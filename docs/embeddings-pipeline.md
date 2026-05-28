# Pipeline vectorial de Datalaburo

Este documento describe el pipeline real de embeddings y búsqueda vectorial de Datalaburo.

PostgreSQL + pgvector es la base objetivo del proyecto. H2 fue usado en una etapa inicial del MVP y hoy debe considerarse legacy/obsoleto: no es fallback, no es arquitectura objetivo y no debe guiar decisiones nuevas.

## Objetivo

El pipeline vectorial permite comparar semánticamente perfiles profesionales/CVs y ofertas laborales tecnológicas.

El modelo real elegido e integrado es:

```text
BAAI/bge-m3
```

Dimensión esperada:

```text
1024
```

El flujo conceptual es:

```text
perfil/oferta -> texto normalizado -> embedding-service -> BAAI/bge-m3 -> vector 1024 -> document_embeddings -> pgvector search
```

## Tabla `document_embeddings`

Flyway crea `document_embeddings` con una columna:

```sql
embedding vector(1024)
```

La tabla guarda un documento vectorizable por origen, sección, modelo y versión de normalización.

Campos principales:

- `owner_type`: origen (`JOB` o `PROFILE`).
- `owner_id`: id del registro origen.
- `section_type`: sección vectorizada. Actualmente `FULL_TEXT`.
- `source_text_hash`: SHA-256 del texto normalizado.
- `embedding_model`: modelo o generador usado.
- `embedding_dimensions`: dimensión esperada. Actualmente `1024`.
- `normalizer_version`: versión del normalizador.
- `embedding`: vector pgvector `vector(1024)`.
- `status`: `PENDING`, `READY` o `FAILED`.
- `error_message`: error operativo si falla el procesamiento.
- `last_embedded_at`: momento en que se guardó el vector.

La constraint lógica evita duplicados para:

```text
owner_type + owner_id + section_type + embedding_model + normalizer_version
```

Validaciones relevantes:

- `owner_type` debe ser `JOB` o `PROFILE`.
- `section_type` debe identificar la sección vectorizada; hoy se usa `FULL_TEXT`.
- `embedding_model` separa estrictamente `BAAI/bge-m3` de `fake-deterministic-1024`.
- `embedding_dimensions` debe ser `1024`.
- Para búsqueda real, `status` debe ser `READY`.
- Para búsqueda real, `embedding` no debe ser `null`.
- `vector_dims(embedding)` debe ser `1024`.

## Construcción del texto fuente

`EmbeddingTextBuilder` arma el texto vectorizable.

Para `Job` incluye:

- título;
- empresa;
- ubicación;
- descripción;
- requisitos si existen.

Para `CandidateProfile` incluye:

- texto completo del CV/perfil.

Luego `EmbeddingTextNormalizer` normaliza el texto y `SourceTextHasher` calcula `source_text_hash`.

## Preparación de metadata

El metadata backfill prepara registros en `document_embeddings` a partir de `jobs` y `candidate_profiles`.

No genera el vector por sí mismo. Solo:

1. Construye el texto vectorizable.
2. Normaliza el texto.
3. Calcula `source_text_hash`.
4. Crea o actualiza metadata.
5. Deja el registro en `PENDING` si es nuevo o si el texto cambió.

La operación es idempotente:

- Si no existe registro, crea uno nuevo en `PENDING`.
- Si existe y el hash no cambió, devuelve `unchanged`.
- Si existe y el hash cambió, actualiza el hash, limpia errores, resetea `last_embedded_at` y vuelve a `PENDING`.

Endpoints:

```text
POST /internal/embeddings/backfill/jobs?limit=100
POST /internal/embeddings/backfill/profiles?limit=100
POST /internal/embeddings/jobs/{id}/prepare
POST /internal/embeddings/profiles/{id}/prepare
```

## Servicio local `embedding-service`

El servicio local Python/FastAPI vive en:

```text
embedding-service/
```

Expone:

```text
GET  /health
GET  /model-info
POST /v1/embeddings
```

El servicio:

- carga `BAAI/bge-m3`;
- genera embeddings densos;
- devuelve vectores de dimensión `1024`;
- puede normalizar el vector;
- rechaza modelos distintos;
- rechaza inputs vacíos;
- valida que no haya `NaN` ni infinitos;
- se ejecuta localmente, sin APIs externas de embeddings.

Ejemplo:

```powershell
$body = @{
  model = "BAAI/bge-m3"
  input = "Desarrollador Java con Spring Boot, PostgreSQL y experiencia en APIs."
  normalize = $true
} | ConvertTo-Json

Invoke-RestMethod `
  -Method Post `
  -Uri "http://127.0.0.1:8001/v1/embeddings" `
  -ContentType "application/json" `
  -Body $body
```

## Procesamiento real con BGE-M3

Spring Boot llama al servicio local mediante:

- `BgeM3EmbeddingClient`
- `BgeM3EmbeddingGenerator`
- `BgeM3EmbeddingProcessingService`

El worker real:

- procesa solo registros `PENDING`;
- procesa solo `embedding_model = 'BAAI/bge-m3'`;
- valida `embedding_dimensions = 1024`;
- reconstruye el texto fuente;
- valida que el `source_text_hash` siga coincidiendo;
- llama a `embedding-service`;
- valida modelo, dimensión y valores numéricos;
- escribe el vector en PostgreSQL usando `PostgresDocumentEmbeddingVectorWriter`;
- marca el registro como `READY`;
- completa `last_embedded_at`;
- marca `FAILED` con `error_message` si algo falla.

Endpoints:

```text
POST /internal/embeddings/process/bge-m3/pending?limit=1
POST /internal/embeddings/{id}/process-bge-m3
POST /internal/embeddings/{id}/reset-bge-m3-failed
```

## Modelo fake de infraestructura

También existe:

```text
fake-deterministic-1024
```

Este modelo sirve solo para infraestructura y tests:

- validar escritura de `vector(1024)`;
- validar transiciones `PENDING -> READY`;
- validar búsqueda pgvector sin depender del modelo real;
- tests deterministas.

No tiene significado semántico real y no debe usarse para:

- compatibilidad profesional;
- ranking de ofertas;
- conclusiones de tesis;
- explicaciones al usuario.

Endpoints fake separados:

```text
POST /internal/embeddings/backfill/fake/jobs?limit=100
POST /internal/embeddings/backfill/fake/profiles?limit=100
POST /internal/embeddings/jobs/{id}/prepare-fake
POST /internal/embeddings/profiles/{id}/prepare-fake
POST /internal/embeddings/process/pending?limit=100
POST /internal/embeddings/{id}/process
```

## Búsqueda vectorial interna

Endpoint actual:

```text
GET /internal/embeddings/vector-search/profiles/{profileId}/jobs?limit=20&embeddingModel=BAAI/bge-m3
```

La búsqueda:

- toma el embedding `READY` de un `PROFILE`;
- busca `JOBs` con embeddings `READY`;
- filtra por `owner_type`;
- filtra por `section_type = FULL_TEXT`;
- filtra por `embedding_model`;
- filtra por `embedding_dimensions = 1024`;
- exige `embedding is not null`;
- usa distancia coseno con el operador `<=>` de pgvector;
- ordena de menor distancia a mayor distancia;
- devuelve `distance`, `similarity`, `jobId`, `jobEmbeddingId`, `embeddingModel` y `semanticMeaning`.

Para compatibilidad profesional real se debe llamar con:

```text
embeddingModel=BAAI/bge-m3
```

No usar `fake-deterministic-1024` para resultados semánticamente interpretables.

## SQL base de búsqueda

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

## Estado operativo confirmado

Estado confirmado al momento de actualizar este documento:

```text
BAAI/bge-m3:
  JOB READY: 21
  PROFILE READY: 6
  vector_dims(embedding): 1024

fake-deterministic-1024:
  disponible solo para infraestructura/test
```

Query útil:

```sql
select owner_type,
       embedding_model,
       status,
       count(*) as total,
       count(*) filter (where embedding is not null) as with_embedding
from document_embeddings
group by owner_type, embedding_model, status
order by owner_type, embedding_model, status;
```

Validar dimensiones:

```sql
select embedding_model,
       min(vector_dims(embedding)) as min_dims,
       max(vector_dims(embedding)) as max_dims,
       count(*) filter (where status = 'READY' and embedding is not null) as ready_with_embedding
from document_embeddings
group by embedding_model
order by embedding_model;
```

## Probar con PowerShell

Con PostgreSQL, `embedding-service` y la aplicación corriendo:

```powershell
Invoke-RestMethod "http://localhost:8081/internal/embeddings/status"

Invoke-RestMethod "http://localhost:8081/internal/embeddings/vector-search/profiles/1/jobs?limit=20&embeddingModel=BAAI/bge-m3" |
  ConvertTo-Json -Depth 5
```

Ver Top 10 directamente desde PostgreSQL:

```powershell
docker exec datalaburo-postgres psql -U datalaburo -d datalaburo -c "with profile_embedding as (select owner_id, embedding from document_embeddings where owner_type='PROFILE' and embedding_model='BAAI/bge-m3' and status='READY' and embedding is not null limit 1) select p.owner_id as profile_id, j.id as job_id, left(coalesce(j.title,'Untitled'),80) as title, round((de.embedding <=> p.embedding)::numeric, 4) as distance, round((1 - (de.embedding <=> p.embedding))::numeric, 4) as similarity from document_embeddings de cross join profile_embedding p join jobs j on j.id=de.owner_id where de.owner_type='JOB' and de.embedding_model='BAAI/bge-m3' and de.status='READY' and de.embedding is not null order by de.embedding <=> p.embedding limit 10;"
```

## Relación con el motor de compatibilidad

La búsqueda vectorial actual es la base del endpoint de compatibilidad vector-first.

La estrategia activa no mezcla reglas viejas con vectores mediante un score arbitrario. La etapa actual es:

```text
VECTOR_FIRST_WITH_RERANKING_DIAGNOSTIC
```

Esto significa:

1. pgvector + `BAAI/bge-m3` recuperan ofertas cercanas semánticamente.
2. Una capa de análisis agrega señales estructuradas.
3. `vectorRank` se conserva como auditoría y `analysisRank` sigue igual a `vectorRank`.
4. Cada resultado se enriquece con rol, seniority, skills, gaps, evidencia, transferibilidad, explicación y confianza.
5. `suggestedRerankRank` y `suggestedRankDelta` se calculan solo como diagnóstico.
6. Una etapa posterior puede activar `VECTOR_FIRST_WITH_RERANKING` real después de validar los diagnósticos.

Ver:

- [vector-first-compatibility-strategy.md](vector-first-compatibility-strategy.md)
