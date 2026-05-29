# Auditoria de fuentes de datos de ofertas laborales

## Objetivo

Esta auditoria busca decidir la fuente de verdad para ofertas laborales antes de adaptar la UI minima y el flujo de compatibilidad vector-first.

La fase es deliberadamente documental: no cambia ranking, embeddings, entidades productivas, migraciones, endpoints, frontend, scraping, extension, captura ni `CvMatchingService`.

## Alcance y limite de la auditoria

Se revisaron entidades, repositorios, servicios, controladores, templates/UI, migraciones SQL y documentacion versionada.

Durante la auditoria inicial no se pudieron validar conteos reales de PostgreSQL local porque Docker no estaba disponible:

```text
failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine
```

Posteriormente se valido en PostgreSQL la distribucion de `document_embeddings` por `owner_type`:

| owner_type | count |
| --- | ---: |
| `JOB` | 22 |
| `PROFILE` | 7 |

Esta evidencia confirma que los embeddings reales estan alineados con `JOB`/`jobs`, no con `JOB_OFFERS`. La clasificacion "real", "legacy", "seed/demo" o "snapshot" sigue basada principalmente en codigo, migraciones y documentacion, pero el conteo runtime refuerza la recomendacion de usar `JOBS` como fuente de verdad.

## Tablas/modelos encontrados

| Nombre conceptual | Entidad Java asociada | Tabla SQL | Proposito actual | Tipo de datos esperado | Participa en embeddings | Participa en UI | Participa en matching viejo | Participa en matching vectorial |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `JOBS` | `com.DataLaburo.web.model.Job` | `jobs` | Registro principal de ofertas capturadas por la extension/plugin. Contiene titulo, empresa, ubicacion, URL fuente, descripcion, texto visible y metadatos de captura. | Ofertas reales capturadas; tambien puede contener datos historicos del MVP si fueron cargados antes. | Si. `document_embeddings.owner_type = 'JOB'` usa `owner_id = jobs.id`. | Si. `/`, `/jobs`, `/jobs/{id}`, `/matching`, `/jobs/{id}/match`, `/api/jobs`, vista estatica `/static/jobs/index.html`. | Si. `CvMatchingService` consulta `JobRepository`. | Si. El endpoint vector-first recupera embeddings `JOB` y luego carga `Job` por id. |
| `JOB_SNAPSHOTS` | `com.DataLaburo.web.model.JobSnapshot` | `job_snapshots` | Historial/snapshot opcional de capturas. Guarda HTML, texto visible y descripcion cruda asociados a un `Job`. | Snapshots de captura; no debe ser lectura principal. | No directamente. Puede alimentar backfill hacia `jobs`, pero no tiene `owner_type` propio. | Solo indirectamente/compatibilidad: `JobService` mantiene un DTO `latestSnapshot`, pero lo mapea desde `JOBS`, no desde `JOB_SNAPSHOTS`. Stats/settings muestran conteo. | No directamente. | No directamente. |
| `JOB_OFFERS` | `com.DataLaburo.web.domain.JobOffer` | `job_offers` | Entidad legacy separada. Tiene endpoint propio `/api/job-offers` y servicio catalogo con seed demo dormido. | Legacy/demo/manual. No es el flujo real del plugin. | No. `document_embeddings` solo acepta `JOB` y `PROFILE`. | No en UI principal. Solo aparece como conteo/observabilidad legacy en stats/settings. | No en el matching actual revisado. | No. |
| `document_embeddings` | `com.DataLaburo.web.embedding.DocumentEmbedding` | `document_embeddings` | Metadata y vectores para perfiles y ofertas. | Embeddings `PENDING`, `READY` o `FAILED` para `PROFILE` y `JOB`. | Es la tabla vectorial. | No es UI directa. | No. | Si. Es la base de recuperacion vectorial. |
| `CandidateProfile` | `com.DataLaburo.web.model.CandidateProfile` | `candidate_profiles` | Fuente de perfiles/CVs guardados. Se incluye porque el matching cruza perfil contra oferta. | Perfiles reales/guardados por usuario local. | Si, con `owner_type = 'PROFILE'`. | Si, perfiles y matching. | Si, como CV de entrada. | Si, como vector origen de la busqueda. |

## Uso actual por capa

### Entidades

- `Job` (`src/main/java/com/DataLaburo/web/model/Job.java`) esta anotada con `@Table(name = "jobs")` y representa la oferta capturada principal.
- `JobSnapshot` (`src/main/java/com/DataLaburo/web/model/JobSnapshot.java`) esta anotada con `@Table(name = "job_snapshots")` y tiene relacion `ManyToOne` obligatoria hacia `Job`.
- `JobOffer` (`src/main/java/com/DataLaburo/web/domain/JobOffer.java`) esta anotada con `@Table(name = "job_offers")`, vive en paquete `domain` y no comparte modelo con `Job`.
- `DocumentEmbedding` (`src/main/java/com/DataLaburo/web/embedding/DocumentEmbedding.java`) representa `document_embeddings` y solo admite `DocumentEmbeddingOwnerType.JOB` o `PROFILE`.

### Repositorios

- `JobRepository` consulta `jobs`. Sus metodos principales son deduplicacion por `source/externalJobId`, deduplicacion por `sourceUrl/title/company`, listado por `createdAt desc`, conteos y busquedas por id.
- `JobSnapshotRepository` consulta `job_snapshots` y expone `findTopByJobIdOrderByCapturedAtDescIdDesc`.
- `JobOfferRepository` consulta `job_offers` con CRUD basico, sin queries de matching ni vector search.
- `DocumentEmbeddingRepository` consulta `document_embeddings` por owner, seccion, modelo y version de normalizacion.
- `PostgresEmbeddingVectorSearchRepository` consulta `document_embeddings` filtrando `owner_type = 'PROFILE'` para el perfil y `owner_type = 'JOB'` para las ofertas.

### Servicios

- `JobIngestService` es el flujo real de captura. Recibe `ScrapeCurrentRequestDto`, deduplica contra `jobs`, guarda/actualiza `Job` y crea un `JobSnapshot` como historial opcional. El comentario interno dice que los detalles limpios se persisten directamente en `JOBS` como fuente unica.
- `JobService` lista y detalla ofertas desde `JobRepository`. Mantiene `latestSnapshot` en el DTO por compatibilidad de frontend, pero lo arma desde columnas de `JOBS`.
- `CvMatchingService` usa `JobRepository.findAllByOrderByCreatedAtDescIdDesc()` y `Job` para el matching historico por reglas. No usa `JobOffer`.
- `VectorFirstCompatibilityService` usa vector search sobre `document_embeddings` y luego carga `Job` desde `JobRepository.findAllById(...)`. El resultado vectorial referencia `jobId`, que corresponde a `jobs.id`.
- `EmbeddingPreparationService` prepara embeddings de ofertas con `DocumentEmbeddingOwnerType.JOB` y `ownerId = job.id`.
- `EmbeddingBackfillService` hace backfill de jobs desde `JobRepository`, no desde `JobOfferRepository`.
- `EmbeddingSourceTextResolver` reconstruye texto fuente de embeddings `JOB` buscando `Job` por `ownerId`.
- `JobOfferCatalogService` contiene 5 ofertas demo en `ensureSeeded()`, pero no se encontro ningun caller. `SeedJobOffers` explicita que el seed de `JOB_OFFERS` esta deshabilitado.
- `BackfillJobsFromSnapshots` copia campos faltantes desde el ultimo snapshot hacia `JOBS`, reforzando que `JOB_SNAPSHOTS` es fuente auxiliar/historica.
- `CleanJobsText` limpia textos en `JOBS`.
- `DashboardService` cuenta `jobs`, `job_snapshots` y `job_offers`; usa `job_offers` solo para observabilidad legacy.

### Controladores/endpoints

- `POST /plugins/scrape-current` (`PluginsController`) recibe capturas de extension y llama a `JobIngestService`. Guarda en `JOBS` y snapshot opcional.
- `GET /api/jobs` y `GET /api/jobs/{jobId}` (`JobController`) exponen datos de `JOBS`.
- `POST /matching`, `GET /jobs/{jobId}/match`, `POST /jobs/{jobId}/match` (`MatchController`) usan `CvMatchingService` y `JobRepository`, por lo tanto `JOBS`.
- `GET /jobs`, `GET /jobs/{jobId}`, home, stats y settings (`MatchController`) leen `JOBS` y conteos.
- `POST /api/job-offers` y `GET /api/job-offers` (`JobOfferApiController`) siguen activos, pero escriben/leen `JOB_OFFERS`, que no alimenta UI principal, embeddings ni vector-first.
- `POST /internal/embeddings/backfill/jobs`, `POST /internal/embeddings/jobs/{id}/prepare`, `POST /internal/embeddings/process/bge-m3/pending` preparan/procesan embeddings de `JOBS`.
- `GET /internal/embeddings/vector-search/profiles/{profileId}/jobs` busca embeddings `JOB`.
- `GET /internal/analysis/profiles/{profileId}/vector-first-compatibility` usa `VECTOR_FIRST_WITH_RERANKING_DIAGNOSTIC`, conserva `vectorRank` y `analysisRank == vectorRank`, y devuelve diagnosticos de reranking sin reranking real activo.

### Templates/UI

- `home.html` muestra stats y recientes desde `JOBS`. La inconsistencia visible que presentaba H2 como base de captura fue corregida en la fase 2.1 para presentar `JOBS` como tabla principal en PostgreSQL.
- `jobs.html` declara que lista ofertas guardadas en `JOBS` capturadas por plugin. Muestra titulo, empresa, ubicacion, fecha, logo, descripcion y acciones.
- `job-detail.html` muestra un `Job` por id desde `JOBS`.
- `matching.html` dice que compara contra ofertas guardadas en `JOBS` y usa matching por reglas.
- `job-match.html` evalua una sola oferta desde `JOBS` con matching por reglas.
- `stats.html` se declara basado en `JOBS` y muestra `JOB_SNAPSHOTS`/`JOB_OFFERS` como tablas legacy.
- `settings.html` explica que `/plugins/scrape-current` guarda en `JOBS` y que `JOB_OFFERS` es legacy. En la fase 2.1 se reemplazo la seccion visible de H2 como flujo principal por una seccion PostgreSQL + pgvector.
- `src/main/resources/static/jobs/index.html` + `static/JS/main.js` consumen `/api/jobs`.
- No se encontro una UI actual que consuma `GET /internal/analysis/profiles/{profileId}/vector-first-compatibility`.

### Migraciones

- `V1__baseline_schema.sql` crea `candidate_profiles`, `job_offers`, `jobs`, `job_snapshots`, `skills` y `skill_aliases`.
- `V1__baseline_schema.sql` no inserta datos seed para `jobs` ni `job_offers`.
- `V3__create_document_embeddings.sql` crea `document_embeddings` con `embedding vector(1024)`.
- `V3__create_document_embeddings.sql` restringe `owner_type` a `JOB` o `PROFILE`; no existe `JOB_OFFER`.
- La configuracion default activa `postgres`. `application-h2.properties` existe, pero H2 esta documentado como legacy.

### Documentacion

- `README.md`, `Context.md`, `docs/embeddings-pipeline.md`, `docs/vector-first-compatibility-strategy.md` y el evidence pack sostienen la direccion PostgreSQL + pgvector + `BAAI/bge-m3`.
- `docs/evaluation-vector-first-diagnostic.md` y `docs/evaluation/evaluation-evidence-pack.md` ya pedian auditar `JOBS` vs `JOB_OFFERS` antes de UI minima.
- La documentacion central no promueve `JOB_OFFERS` como fuente de verdad.
- Inconsistencias detectadas y tratadas:
  - `home.html` y `settings.html` hablaban de H2 como flujo de uso; corregido en fase 2.1.
  - `fragments/footer.html` conservaba un link visible a H2; eliminado en fase 2.1.
  - `application-h2.properties` sigue disponible como perfil legacy.
  - `JobOfferApiController` mantiene un endpoint capaz de crear datos en una tabla que el flujo real no usa.

## Flujo actual detectado

Una oferta entra al sistema principalmente desde la extension del navegador:

1. La extension extrae datos de LinkedIn u otra pagina.
2. `popup.js` envia el payload a `POST /plugins/scrape-current`.
3. `PluginsController` llama a `JobIngestService`.
4. `JobIngestService` deduplica por `source + externalJobId` o por `sourceUrl + title + company`.
5. Si la oferta es nueva, crea un registro en `jobs`.
6. Tambien crea un registro en `job_snapshots` como historial opcional.
7. Si la oferta ya existia, solo completa campos faltantes en `jobs`.

El embedding de una oferta se genera en otro paso:

1. `POST /internal/embeddings/backfill/jobs?limit=100` o `POST /internal/embeddings/jobs/{id}/prepare`.
2. `EmbeddingBackfillService` lee `Job` desde `jobs`.
3. `EmbeddingPreparationService` crea/actualiza `document_embeddings` con `owner_type = 'JOB'`, `owner_id = jobs.id`, `section_type = 'FULL_TEXT'` y `embedding_model = 'BAAI/bge-m3'`.
4. `POST /internal/embeddings/process/bge-m3/pending` procesa registros `PENDING`.
5. `BgeM3EmbeddingProcessingService` reconstruye el texto desde `Job`, llama a `embedding-service`, escribe el vector y marca `READY`.

La consulta vectorial funciona asi:

1. El perfil debe tener embedding `READY` con `owner_type = 'PROFILE'`.
2. La busqueda toma ofertas con embedding `READY`, `owner_type = 'JOB'`, `embedding_model = 'BAAI/bge-m3'` y dimension `1024`.
3. Ordena por distancia vectorial en pgvector.
4. `VectorFirstCompatibilityService` carga los `Job` correspondientes desde `jobs`.
5. Agrega analisis explicable y diagnostico de reranking, sin alterar el ranking productivo.

La UI actual funciona asi:

1. `/jobs` lista `jobs`.
2. `/jobs/{id}` muestra detalle de `jobs`.
3. `/matching` ejecuta `CvMatchingService` contra todos los `jobs`.
4. `/jobs/{id}/match` ejecuta `CvMatchingService` contra un `Job`.
5. No hay UI conectada al endpoint vector-first todavia.

## Riesgos encontrados

- Duplicacion conceptual entre `JOBS` y `JOB_OFFERS`: ambas parecen representar ofertas, pero solo `JOBS` alimenta captura, UI principal, matching actual y embeddings.
- `JOB_OFFERS` sigue teniendo endpoint de escritura (`POST /api/job-offers`), lo que permite cargar ofertas que no apareceran en la UI principal ni en vector-first.
- Si una futura UI consume `JOB_OFFERS`, quedaria desconectada de embeddings `JOB` y del endpoint vectorial.
- Si una captura externa usa `/api/job-offers` en vez de `/plugins/scrape-current`, esos datos quedan fuera del pipeline real.
- `document_embeddings.owner_type` no tiene `JOB_OFFER`; cualquier embedding real de ofertas hoy apunta semanticamente a `JOBS`.
- `JOB_SNAPSHOTS` contiene datos historicos/crudos y podria confundirse con fuente primaria, pero el codigo actual indica que no debe ser lectura principal.
- `JobOfferCatalogService.ensureSeeded()` conserva seed demo dormido; si se invoca en el futuro, reintroduciria datos demo en `JOB_OFFERS`.
- Si reaparecen textos visibles que presenten H2 como flujo principal, pueden inducir a validar capturas en la base equivocada. En fase 2.1 se corrigieron las menciones visibles detectadas en home, settings y footer.
- La UI actual usa matching por reglas y puede dar una experiencia distinta al endpoint vector-first. Conectar parcialmente la UI sin aclarar modo de ranking generaria resultados inconsistentes.
- Los campos que la UI historica muestra (`matchPercent`, breakdown tecnico/rol/experiencia) no existen igual en `VectorFirstCompatibilityResult`; adaptar UI requiere una decision de DTO/vista.
- No se validaron conteos reales de base en esta auditoria; antes de ampliar dataset conviene verificar que los embeddings `JOB` correspondan a filas actuales de `jobs`.

## Recomendacion de fuente de verdad

La fuente de verdad recomendada para ofertas laborales es `JOBS` / entidad `Job` / tabla `jobs`.

Motivos:

- Es la tabla alimentada por la extension mediante `/plugins/scrape-current`.
- Es la tabla que usa la UI actual.
- Es la tabla que usa el matching historico actual.
- Es la tabla que alimenta embeddings (`owner_type = 'JOB'`).
- Es la tabla que resuelve el endpoint vector-first despues de la busqueda pgvector.
- `job_snapshots` depende de `jobs`, no al reves.
- `job_offers` no participa del pipeline vectorial ni de la UI principal.

`JOB_OFFERS` debe quedar como legacy/no productivo. No conviene borrarla en esta fase, pero si conviene evitar nuevas integraciones contra ella.

`JOB_SNAPSHOTS` conviene mantenerla como historial/snapshot de captura, util para auditoria o recuperacion de texto crudo, pero no como fuente de verdad para matching ni UI.

## Impacto sobre UI minima vector-first

En la siguiente fase, la UI minima deberia consumir:

```text
GET /internal/analysis/profiles/{profileId}/vector-first-compatibility?limit=20
```

Campos minimos recomendados para mostrar:

- `jobId`
- `title`
- `company`
- `vectorRank`
- `analysisRank`
- `vectorSimilarity`
- `detectedRole`
- `detectedSeniority`
- `compatibilityCategory`
- `evidenceLevel`
- `matchedSkills`
- `missingCriticalSkills`
- `missingSecondarySkills`
- `transferableSkills`
- `roadmapSuggestions`
- `explanation`
- `confidence`
- diagnosticos (`compatibilityBucket`, `suggestedRerankRank`, `suggestedRankDelta`) solo como informacion interna o vista tecnica, no como ranking productivo.

La respuesta debe seguir alimentandose desde `jobs` y `document_embeddings(owner_type = 'JOB')`.

No conviene tocar todavia:

- `CvMatchingService`;
- pesos/reglas;
- `analysisRank`;
- reranking real;
- score hibrido;
- migraciones destructivas;
- borrado de `JOB_OFFERS`;
- conversion de `JOB_SNAPSHOTS` en fuente primaria.

## Impacto sobre embeddings

Los embeddings actuales de ofertas estan asociados a:

```text
owner_type = 'JOB'
owner_id = jobs.id
section_type = 'FULL_TEXT'
embedding_model = 'BAAI/bge-m3'
embedding_dimensions = 1024
```

Esto coincide con la fuente de verdad recomendada: `JOBS`.

Evidencia PostgreSQL validada despues de la auditoria inicial:

```sql
select owner_type, count(*)
from document_embeddings
group by owner_type;
```

Resultado observado:

| owner_type | count |
| --- | ---: |
| `JOB` | 22 |
| `PROFILE` | 7 |

La existencia de 22 embeddings `JOB` y ningun `JOB_OFFER` confirma que la base vectorial esta alineada con `jobs` como fuente de ofertas.

No hay soporte actual para:

```text
owner_type = 'JOB_OFFER'
```

Riesgos antes de ampliar dataset:

- embeddings `JOB` pueden quedar apuntando a ofertas con texto obsoleto si se edita `jobs` sin re-preparar embeddings;
- embeddings `PENDING` o `FAILED` pueden hacer que la UI vector-first no muestre ofertas capturadas recientemente;
- datos cargados por `/api/job-offers` no tendran embedding vectorial;
- si se restaura una base o se migra desde H2/PostgreSQL, hay que validar integridad entre `document_embeddings.owner_id` y `jobs.id`;
- antes de evaluar resultados, validar conteos por `owner_type`, `embedding_model`, `status` y dimensiones.

Checklist operativo recomendado antes de ampliar dataset:

```sql
select owner_type,
       embedding_model,
       status,
       count(*) as total,
       count(*) filter (where embedding is not null) as with_embedding
from document_embeddings
group by owner_type, embedding_model, status
order by owner_type, embedding_model, status;

select count(*) as orphan_job_embeddings
from document_embeddings de
left join jobs j on j.id = de.owner_id
where de.owner_type = 'JOB'
  and j.id is null;
```

## Decision recomendada

- Usar `JOBS` / `Job` / `jobs` como fuente de verdad para ofertas laborales.
- Mantener `JOB_OFFERS` / `JobOffer` / `job_offers` como legacy/no productivo.
- Mantener `JOB_SNAPSHOTS` / `JobSnapshot` / `job_snapshots` como historial de captura, no como tabla principal.
- No conectar la UI a `JOB_OFFERS` si el endpoint vectorial trabaja con `JOBS`.
- Antes de implementar cambios, validar con una muestra de datos reales en PostgreSQL que `jobs`, `job_snapshots` y `document_embeddings` esten alineadas.

## Proximos pasos

1. Mantener documentacion e UI visibles alineadas con PostgreSQL + pgvector como base principal.
2. Confirmar formalmente `JOBS` como fuente de verdad.
3. Desalentar o proteger `/api/job-offers` en una fase posterior para evitar ingestas legacy accidentales.
4. Validar periodicamente conteos reales en PostgreSQL y detectar embeddings huerfanos.
5. Adaptar UI minima para consumir el endpoint vector-first.
6. Ampliar dataset desde `/plugins/scrape-current`.
7. Preparar `rankingMode` experimental detras de flag, parametro o endpoint separado.

## Cambios no realizados en esta fase

- No se modifico logica de ranking.
- No se modifico logica de embeddings.
- No se modificaron entidades productivas.
- No se agregaron migraciones.
- No se borraron tablas.
- No se modificaron endpoints productivos.
- No se modifico frontend.
- No se modifico scraping ni extension.
- No se modifico `CvMatchingService`.
- No se activo reranking real.
- No se activo score hibrido.
- No se cambio `analysisRank`.
