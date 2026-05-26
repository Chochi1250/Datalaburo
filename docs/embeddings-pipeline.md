# Pipeline vectorial de Datalaburo

Este documento describe el estado actual del pipeline vectorial de Datalaburo.
La etapa actual prepara la informacion necesaria para generar embeddings en una fase posterior, pero todavia no calcula vectores reales.

## Problema que resuelve

El pipeline vectorial prepara a Datalaburo para comparar semanticamente CVs/perfiles profesionales y ofertas laborales tecnologicas.

El matching actual por reglas sigue siendo el baseline explicable. El pipeline vectorial se esta construyendo como una capa futura para complementar ese baseline con busqueda semantica usando PostgreSQL, pgvector y embeddings.

## Tabla `document_embeddings`

`document_embeddings` es la tabla preparada para almacenar un embedding por documento o seccion vectorizable.

Actualmente guarda metadata como:

- `owner_type`: tipo de documento origen (`JOB` o `PROFILE`).
- `owner_id`: id del registro origen.
- `section_type`: seccion vectorizada. Por ahora se usa `FULL_TEXT`.
- `source_text_hash`: hash SHA-256 del texto normalizado.
- `embedding_model`: modelo previsto. Actualmente `BAAI/bge-m3`.
- `embedding_dimensions`: dimension esperada. Actualmente `1024`.
- `normalizer_version`: version del normalizador usado.
- `status`: estado del registro.
- `embedding`: columna `vector(1024)` de pgvector. Por ahora queda en `NULL`.

La constraint logica evita duplicados para:

```text
owner_type + owner_id + section_type + embedding_model + normalizer_version
```

## Metadata backfill

El metadata backfill recorre los `jobs` y `candidate_profiles` existentes y prepara registros en `document_embeddings`.

No genera embeddings reales. Solo:

1. Construye el texto vectorizable.
2. Normaliza el texto.
3. Calcula el hash SHA-256.
4. Crea o actualiza un registro en `document_embeddings`.
5. Deja el registro en `PENDING` si es nuevo o si el texto cambio.

La operacion es idempotente:

- Si no existe registro, crea uno nuevo en `PENDING`.
- Si existe y el hash no cambio, queda `unchanged`.
- Si existe y el hash cambio, actualiza el hash, limpia errores, resetea `last_embedded_at` y vuelve a `PENDING`.

## Estados

- `PENDING`: el texto fuente ya fue preparado, pero todavia no tiene embedding real.
- `READY`: estado futuro para registros que ya tengan un vector calculado y guardado.
- `FAILED`: estado futuro para registros cuyo calculo de embedding falle.

En la etapa actual, los registros preparados quedan en `PENDING` con `embedding = NULL`.

## Componentes actuales

### `EmbeddingTextBuilder`

Construye el texto vectorizable desde entidades de dominio.

Para `Job` usa campos etiquetados:

- `Title`
- `Company`
- `Location`
- `Description`
- `Requirements`

`visibleText` solo se usa como fallback cuando `description` esta vacio.

Para `CandidateProfile` usa `cvText`. No incluye `name` ni otra identidad personal innecesaria.

### `EmbeddingTextNormalizer`

Normaliza el texto antes de calcular el hash.

Version actual:

```text
embedding-text-v1
```

Reglas principales:

- normaliza saltos de linea a `\n`;
- aplica trim general;
- colapsa espacios repetidos;
- elimina caracteres de control;
- limita lineas vacias consecutivas;
- preserva idioma, acentos y puntuacion razonable.

### `SourceTextHasher`

Calcula SHA-256 sobre el texto normalizado usando UTF-8.

Devuelve un hash hexadecimal de 64 caracteres. Ese hash permite detectar si el texto fuente cambio y evitar recalcular embeddings innecesariamente en fases futuras.

## Endpoints internos

Los endpoints internos actuales estan bajo `/internal/embeddings`.

```text
POST /internal/embeddings/backfill/jobs?limit=100
POST /internal/embeddings/backfill/profiles?limit=100
POST /internal/embeddings/jobs/{id}/prepare
POST /internal/embeddings/profiles/{id}/prepare
GET  /internal/embeddings/status
```

Los backfills devuelven conteos:

- `scanned`
- `created`
- `updated`
- `unchanged`
- `skippedBlank`
- `failed`

## Probar con PowerShell

Con la aplicacion corriendo en `http://localhost:8081`:

```powershell
Invoke-RestMethod -Method Post "http://localhost:8081/internal/embeddings/backfill/jobs?limit=100"
Invoke-RestMethod -Method Post "http://localhost:8081/internal/embeddings/backfill/profiles?limit=100"
Invoke-RestMethod -Method Post "http://localhost:8081/internal/embeddings/jobs/1/prepare"
Invoke-RestMethod -Method Post "http://localhost:8081/internal/embeddings/profiles/1/prepare"
Invoke-RestMethod "http://localhost:8081/internal/embeddings/status"
```

Alternativa con `curl.exe`:

```powershell
curl.exe -X POST "http://localhost:8081/internal/embeddings/backfill/jobs?limit=100"
curl.exe -X POST "http://localhost:8081/internal/embeddings/backfill/profiles?limit=100"
curl.exe -X POST "http://localhost:8081/internal/embeddings/jobs/1/prepare"
curl.exe -X POST "http://localhost:8081/internal/embeddings/profiles/1/prepare"
curl.exe "http://localhost:8081/internal/embeddings/status"
```

## Queries utiles para DBeaver

Ver conteos por tipo y estado:

```sql
select owner_type, status, count(*)
from document_embeddings
group by owner_type, status
order by owner_type, status;
```

Ver registros recientes:

```sql
select id,
       owner_type,
       owner_id,
       section_type,
       embedding_model,
       embedding_dimensions,
       normalizer_version,
       status,
       source_text_hash,
       embedding is null as embedding_is_null,
       created_at,
       updated_at,
       last_embedded_at
from document_embeddings
order by created_at desc
limit 20;
```

Verificar que no haya duplicados logicos:

```sql
select owner_type,
       owner_id,
       section_type,
       embedding_model,
       normalizer_version,
       count(*)
from document_embeddings
group by owner_type, owner_id, section_type, embedding_model, normalizer_version
having count(*) > 1;
```

Verificar que la etapa actual no lleno embeddings reales:

```sql
select status,
       count(*) as total,
       count(*) filter (where embedding is null) as embedding_null,
       count(*) filter (where embedding is not null) as embedding_not_null
from document_embeddings
group by status
order by status;
```

## Que todavia no hace

La etapa actual no:

- genera embeddings reales;
- llama a `BAAI/bge-m3`;
- integra Python ni modelos locales;
- llena la columna `embedding`;
- ejecuta busqueda vectorial;
- modifica el scoring ni el matching actual.

## Proxima fase

La siguiente fase natural es agregar un servicio local de embeddings que:

1. Tome registros `PENDING`.
2. Genere embeddings reales con el modelo local elegido.
3. Guarde el vector en `document_embeddings.embedding`.
4. Cambie el estado a `READY`.
5. Marque `FAILED` y guarde `error_message` si ocurre un error.

Despues de eso, se puede incorporar busqueda semantica con pgvector y combinarla con el baseline explicable actual.
