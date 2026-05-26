# Pipeline vectorial de Datalaburo

Este documento describe el estado actual del pipeline vectorial de Datalaburo.
La base objetivo del proyecto es PostgreSQL con pgvector. H2 fue usado en una
etapa inicial del MVP y hoy debe considerarse legacy/obsoleto, no fallback ni
base local objetivo.

## Objetivo

El pipeline vectorial prepara a Datalaburo para comparar semanticamente
perfiles profesionales/CVs y ofertas laborales tecnologicas en una fase futura.

El matching por reglas sigue siendo el baseline explicable y defendible. La
capa vectorial todavia no reemplaza ese baseline, no participa del scoring y no
se usa en la UI.

## Tabla `document_embeddings`

Flyway crea `document_embeddings` con una columna `embedding vector(1024)`.
La tabla guarda un documento vectorizable por origen, seccion, modelo y version
de normalizacion.

Campos principales:

- `owner_type`: origen (`JOB` o `PROFILE`).
- `owner_id`: id del registro origen.
- `section_type`: seccion vectorizada. Actualmente `FULL_TEXT`.
- `source_text_hash`: SHA-256 del texto normalizado.
- `embedding_model`: modelo o generador usado.
- `embedding_dimensions`: dimension esperada. Actualmente `1024`.
- `normalizer_version`: version del normalizador.
- `embedding`: vector pgvector `vector(1024)`.
- `status`: `PENDING`, `READY` o `FAILED`.
- `error_message`: error operativo si falla el procesamiento.
- `last_embedded_at`: momento en que se guardo el vector.

La constraint logica evita duplicados para:

```text
owner_type + owner_id + section_type + embedding_model + normalizer_version
```

## Metadata backfill

El metadata backfill prepara registros en `document_embeddings` a partir de
`jobs` y `candidate_profiles`.

No genera embeddings semanticos reales. Solo:

1. Construye el texto vectorizable.
2. Normaliza el texto.
3. Calcula `source_text_hash`.
4. Crea o actualiza metadata.
5. Deja el registro en `PENDING` si es nuevo o si el texto cambio.

La operacion es idempotente:

- Si no existe registro, crea uno nuevo en `PENDING`.
- Si existe y el hash no cambio, devuelve `unchanged`.
- Si existe y el hash cambio, actualiza el hash, limpia errores, resetea
  `last_embedded_at` y vuelve a `PENDING`.

## Componentes existentes

- `EmbeddingTextBuilder`: arma texto vectorizable desde `Job` y
  `CandidateProfile`.
- `EmbeddingTextNormalizer`: normaliza texto antes del hash.
- `SourceTextHasher`: calcula SHA-256 sobre UTF-8.
- `DocumentEmbedding`: mapea metadata, no mapea directamente el campo
  `embedding`.
- `DocumentEmbeddingRepository`: acceso JPA a metadata.
- `EmbeddingPreparationService`: prepara metadata por registro.
- `EmbeddingBackfillService`: prepara metadata por lote.
- `EmbeddingAdminController`: expone endpoints internos.
- `EmbeddingProcessingService`: procesa embeddings fake `PENDING`.
- `PostgresDocumentEmbeddingVectorWriter`: escribe `vector(1024)` con JDBC.
- `EmbeddingVectorSearchService`: ejecuta busqueda vectorial interna.
- `PostgresEmbeddingVectorSearchRepository`: usa pgvector para ranking.

## Modelos

### Modelo real futuro

El modelo real objetivo sigue siendo:

```text
BAAI/bge-m3
```

Dimension esperada:

```text
1024
```

Todavia no existe integracion real con `BAAI/bge-m3`, no hay servicio local de
embeddings reales y no se generan embeddings semanticos reales.

### Modelo fake de infraestructura

El worker fake usa:

```text
fake-deterministic-1024
```

Ese identificador separa los vectores fake de los futuros vectores reales de
`BAAI/bge-m3`.

El worker fake/deterministico:

- procesa solo registros `PENDING` con
  `embedding_model = 'fake-deterministic-1024'`;
- genera siempre el mismo vector para el mismo input;
- genera vectores de dimension `1024`;
- evita `NaN` e infinitos;
- escribe en `document_embeddings.embedding` usando pgvector;
- marca registros como `READY`;
- completa `last_embedded_at`;
- limpia `error_message` si fue exitoso;
- marca `FAILED` y guarda `error_message` ante error.

Importante: los vectores `fake-deterministic-1024` no tienen significado
semantico real. Solo validan infraestructura: generacion controlada,
persistencia `vector(1024)`, transicion `PENDING -> READY`, trazabilidad e
idempotencia.

## Busqueda vectorial interna

Ya existe una busqueda vectorial interna con pgvector para validar que
PostgreSQL puede comparar embeddings `READY`.

La busqueda:

- toma el embedding `READY` de un `PROFILE`;
- busca `JOBs` con embeddings `READY`;
- filtra siempre por `embedding_model` y `embedding_dimensions = 1024`;
- usa distancia coseno con el operador `<=>` de pgvector;
- ordena de menor distancia a mayor distancia;
- devuelve `distance`, `similarity`, `jobId`, `jobEmbeddingId`,
  `embeddingModel` y `semanticMeaning=false`.

Endpoint:

```text
GET /internal/embeddings/vector-search/profiles/{profileId}/jobs?limit=20
```

Parametro opcional:

```text
embeddingModel=fake-deterministic-1024
```

Default:

```text
fake-deterministic-1024
```

Respuesta conceptual:

- `semanticMeaning=false` indica que el resultado no debe interpretarse como
  compatibilidad profesional real.
- Si `embeddingModel = fake-deterministic-1024`, el ranking es una prueba
  interna de infraestructura, no una medicion semantica.

## SQL base de busqueda

La consulta interna usa la forma:

```sql
with profile_embedding as (
    select embedding
    from document_embeddings
    where owner_type = 'PROFILE'
      and owner_id = :profileId
      and section_type = 'FULL_TEXT'
      and embedding_model = :embeddingModel
      and embedding_dimensions = 1024
      and status = 'READY'
      and embedding is not null
    limit 1
)
select job.owner_id as job_id,
       job.id as job_embedding_id,
       job.embedding_model,
       job.embedding <=> profile_embedding.embedding as distance,
       1 - (job.embedding <=> profile_embedding.embedding) as similarity
from document_embeddings job
cross join profile_embedding
where job.owner_type = 'JOB'
  and job.section_type = 'FULL_TEXT'
  and job.embedding_model = :embeddingModel
  and job.embedding_dimensions = 1024
  and job.status = 'READY'
  and job.embedding is not null
order by job.embedding <=> profile_embedding.embedding asc
limit :limit;
```

## Endpoints internos

Preparacion de metadata:

```text
POST /internal/embeddings/backfill/jobs?limit=100
POST /internal/embeddings/backfill/profiles?limit=100
POST /internal/embeddings/jobs/{id}/prepare
POST /internal/embeddings/profiles/{id}/prepare
```

Preparacion fake separada:

```text
POST /internal/embeddings/backfill/fake/jobs?limit=100
POST /internal/embeddings/backfill/fake/profiles?limit=100
POST /internal/embeddings/jobs/{id}/prepare-fake
POST /internal/embeddings/profiles/{id}/prepare-fake
```

Procesamiento fake:

```text
POST /internal/embeddings/process/pending?limit=100
POST /internal/embeddings/{id}/process
```

Estado y busqueda:

```text
GET /internal/embeddings/status
GET /internal/embeddings/vector-search/profiles/{profileId}/jobs?limit=20
```

## Probar con PowerShell

Con PostgreSQL y la aplicacion corriendo en `http://localhost:8081`:

```powershell
Invoke-RestMethod -Method Post "http://localhost:8081/internal/embeddings/backfill/fake/jobs?limit=100"
Invoke-RestMethod -Method Post "http://localhost:8081/internal/embeddings/backfill/fake/profiles?limit=100"
Invoke-RestMethod -Method Post "http://localhost:8081/internal/embeddings/process/pending?limit=100"
Invoke-RestMethod "http://localhost:8081/internal/embeddings/status"
```

Buscar jobs desde un perfil fake `READY`:

```powershell
$profileId = docker exec datalaburo-postgres psql -U datalaburo -d datalaburo -t -A -c "select owner_id from document_embeddings where owner_type='PROFILE' and embedding_model='fake-deterministic-1024' and status='READY' and embedding is not null limit 1;"
Invoke-RestMethod "http://localhost:8081/internal/embeddings/vector-search/profiles/$profileId/jobs?limit=20" | ConvertTo-Json -Depth 5
```

## Queries utiles

Conteos por modelo y estado:

```sql
select owner_type, embedding_model, status, count(*)
from document_embeddings
group by owner_type, embedding_model, status
order by owner_type, embedding_model, status;
```

Verificar embeddings fake `READY`:

```sql
select id,
       owner_type,
       owner_id,
       embedding_model,
       status,
       embedding is not null as has_embedding,
       vector_dims(embedding) as dimensions,
       last_embedded_at,
       error_message
from document_embeddings
where embedding_model = 'fake-deterministic-1024'
order by owner_type, owner_id;
```

Verificar que no se mezclan modelos:

```sql
select embedding_model,
       count(*) as total,
       count(*) filter (where status = 'READY') as ready,
       count(*) filter (where embedding is not null) as with_embedding
from document_embeddings
group by embedding_model
order by embedding_model;
```

## Que todavia falta

Todavia no existe:

- integracion real con `BAAI/bge-m3`;
- generacion de embeddings semanticos reales;
- dataset mas representativo de ofertas;
- busqueda vectorial semanticamente valida;
- comparacion formal contra el baseline por reglas;
- feedback semantico real para el usuario;
- matching hibrido.

Tampoco existen indices vectoriales HNSW/IVFFlat. No son necesarios para esta
fase de validacion interna.

## Proximos pasos recomendados

1. Capturar 20-50 ofertas variadas.
2. Crear 2-3 perfiles de prueba.
3. Preparar registros `BAAI/bge-m3` en `PENDING`.
4. Integrar un servicio local de embeddings reales.
5. Pasar registros `BAAI/bge-m3` de `PENDING` a `READY`.
6. Usar busqueda vectorial real como base para asesoramiento.
7. Comparar resultados contra el baseline por reglas.
8. Recien despues evaluar matching hibrido.
