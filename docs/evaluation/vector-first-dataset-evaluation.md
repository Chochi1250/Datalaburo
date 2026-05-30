# Evaluacion de dataset vector-first

## Objetivo

Convertir la evidencia actual del sistema vector-first en una base evaluable con labels humanos, criterios de etiquetado y metricas simples.

La fase busca responder:

1. Si el ranking vector-first recupera ofertas razonables para cada perfil.
2. Si los primeros resultados son realmente compatibles.
3. Si hay falsos positivos.
4. Si hay falsos negativos visibles.
5. Si los buckets y categorias diagnosticas son razonables.
6. Si `suggestedRerankRank` parece util como diagnostico, sin activarlo.
7. Que casos deberian guiar una futura calibracion.
8. Que limitaciones tiene el dataset actual.

## Alcance

Esta fase evalua el baseline actual. No evalua un ranking experimental y no modifica comportamiento productivo.

No se toca:

- ranking;
- embeddings;
- entidades;
- migraciones;
- servicios productivos;
- endpoints productivos;
- UI funcional;
- scraping;
- extension;
- captura;
- `CvMatchingService`;
- `VectorFirstCompatibilityService`;
- `ProfileVectorCompatibilityController`;
- templates funcionales.

No se activa:

- reranking real;
- score hibrido;
- ranking experimental.

## Baseline evaluado

| Elemento | Estado |
| --- | --- |
| Base objetivo | PostgreSQL + pgvector |
| Base legacy | H2 historico/test, no arquitectura objetivo |
| Modelo real | `BAAI/bge-m3` |
| Dimension vectorial | `document_embeddings.embedding vector(1024)` |
| Fuente de ofertas | `JOBS` / `jobs` |
| Fuente legacy | `JOB_OFFERS` / `job_offers`, no productivo |
| Endpoint interno | `GET /internal/analysis/profiles/{profileId}/vector-first-compatibility?limit=20` |
| UI minima | `GET /profiles/{profileId}/vector-first-compatibility?limit=20` |
| Estrategia | `VECTOR_FIRST_WITH_RERANKING_DIAGNOSTIC` |
| Ranking activo | `analysisRank == vectorRank` |
| Reranking real | No activo |
| Score hibrido | No activo |
| Buckets | Diagnosticos internos |

Interpretacion obligatoria:

- `vectorRank` conserva el ranking semantico original.
- `analysisRank` debe coincidir con `vectorRank`.
- `suggestedRerankRank` y `suggestedRankDelta` son diagnosticos.
- Los buckets no son ranking final ni decision automatica.
- `vectorSimilarity` no es porcentaje absoluto de compatibilidad.

## Perfiles incluidos

Los perfiles DIAG estan definidos en `docs/vector-reranking-diagnostic-test-profiles.sql`.

El smoke test mas reciente documento estos IDs locales:

| profileId | Nombre | Objetivo del perfil | Comportamiento esperado | Estado de evidencia |
| ---: | --- | --- | --- | --- |
| 2 | `DIAG - Backend Senior Java Cloud` | Backend senior con Java, microservicios, cloud y experiencia profesional. | Priorizar backend/platform/cloud backend; degradar soporte, data pura e IAM si no hay alineacion. | Evidencia UI/local disponible, 20 resultados. |
| 3 | `DIAG - Backend Strong Partial DevOps Transfer` | Backend fuerte con transferencia parcial hacia DevOps/cloud. | Mantener backend arriba; reconocer DevOps/cloud como transferible sin inflar DevOps senior. | Evidencia UI/local disponible, pero requiere revision manual por inconsistencias historicas. |
| 4 | `DIAG - IT Support Analyst Junior` | Soporte IT junior, help desk y application support. | Priorizar soporte tecnico, mesa de ayuda, app support e IT analyst. | Evidencia UI/local disponible, 20 resultados. |
| 5 | `DIAG - Data BI SQL Profile` | Data/BI/SQL, reporting y dashboards. | Priorizar database/data/BI/SQL; degradar roles sin nucleo de datos. | Evidencia UI/local disponible, 20 resultados. |
| 6 | `DIAG - Backend Trainee Projects` | Backend trainee con proyectos y sin seniority laboral fuerte. | Priorizar backend junior/trainee; degradar senior alto, soporte puro, data senior y roles alejados. | Evidencia UI/local disponible, 20 resultados. |

Nota de consistencia:

- `docs/evaluation-vector-first-diagnostic.md` contiene una lectura historica donde los IDs esperados no coinciden con el smoke test mas reciente.
- Para Fase 4.1, los IDs deben validarse de nuevo contra PostgreSQL antes de congelar dataset.
- No usar el caso `Backend Strong Partial DevOps Transfer` como evidencia fina de transferencia sin verificar que `profileId`, `cv_text` y JSON local correspondan al mismo perfil.

## Muestra de ofertas

La muestra se debe congelar por perfil:

- Top 10 como minimo;
- Top 20 recomendado si hay tiempo;
- resultados obtenidos desde el endpoint interno o desde la UI minima;
- solo ofertas provenientes de `JOBS`;
- no mezclar `JOB_OFFERS`;
- no evaluar ranking con ofertas sin embedding `READY`;
- guardar JSON completo en `local-evidence/`, que no se versiona.

Ruta local recomendada:

```text
local-evidence/vector-first-dataset/profile-2-top20.json
```

El dataset versionado debe contener filas resumidas o una plantilla. No debe copiar JSON completo de `local-evidence/`.

## Taxonomia de labels

La guia completa esta en:

```text
docs/evaluation/compatibility-labeling-guide.md
```

Labels humanos:

| Label | Significado |
| --- | --- |
| `STRONG_MATCH` | Oferta altamente compatible; el perfil podria postularse con buena defensa. |
| `GOOD_MATCH_WITH_GAPS` | Compatible, con brechas menores o entrenables. |
| `TRANSFERABLE_OPPORTUNITY` | No es match directo, pero hay habilidades transferibles defendibles. |
| `ASPIRATIONAL_MATCH` | Oportunidad aspiracional; requiere aprendizaje relevante antes de postular. |
| `LOW_FIT` | Baja compatibilidad; no deberia priorizarse. |
| `UNCLEAR` | No se puede decidir por falta de informacion, texto roto o evidencia insuficiente. |

## Template de dataset

Plantilla creada:

```text
docs/evaluation/vector-first-evaluation-dataset-template.csv
```

Columnas:

- `evaluation_id`
- `profile_id`
- `profile_name`
- `job_id`
- `job_title`
- `company`
- `analysis_rank`
- `vector_rank`
- `vector_similarity`
- `system_category`
- `evidence_level`
- `confidence`
- `detected_role`
- `detected_seniority`
- `matched_skills`
- `missing_critical_skills`
- `missing_secondary_skills`
- `transferable_skills`
- `suggested_rerank_rank`
- `suggested_rank_delta`
- `rerank_warnings`
- `human_label`
- `human_notes`
- `is_false_positive`
- `is_false_negative_candidate`
- `needs_review`

Convenciones sugeridas:

- listas separadas por `;`;
- booleanos como `true` / `false`;
- `human_label` vacio hasta que haya revision humana;
- `needs_review=true` para casos con encoding roto, rol dudoso o evidencia inconsistente;
- no completar campos inventados si no aparecen en el JSON o en la UI.

## Metricas simples

### Precision@5

Proporcion de los primeros 5 resultados que son utiles.

Labels positivos recomendados:

- `STRONG_MATCH`
- `GOOD_MATCH_WITH_GAPS`
- `TRANSFERABLE_OPPORTUNITY`

Formula:

```text
Precision@5 = positivos en Top 5 / 5
```

### Precision@10

Igual que Precision@5, pero sobre Top 10.

```text
Precision@10 = positivos en Top 10 / 10
```

### Tasa de resultados revisables

Mide cuanta muestra no se puede usar limpiamente.

```text
tasa_revisables = filas con needs_review=true o human_label=UNCLEAR / total de filas
```

### Falsos positivos por perfil

Conteo de filas en Top N donde:

```text
is_false_positive=true
```

Debe revisarse junto con `analysis_rank`, `vector_rank`, `compatibilityBucket`, `rerankWarnings` y `human_notes`.

### Distribucion de labels por perfil

Conteo por label:

- `STRONG_MATCH`
- `GOOD_MATCH_WITH_GAPS`
- `TRANSFERABLE_OPPORTUNITY`
- `ASPIRATIONAL_MATCH`
- `LOW_FIT`
- `UNCLEAR`

Sirve para detectar si un perfil trae muchas oportunidades fuertes, mucho ruido o demasiados casos ambiguos.

### nDCG@10 opcional

Usar solo si se define una escala ordinal y hay labels suficientes.

Escala propuesta:

| Label | Relevancia |
| --- | ---: |
| `STRONG_MATCH` | 4 |
| `GOOD_MATCH_WITH_GAPS` | 3 |
| `TRANSFERABLE_OPPORTUNITY` | 2 |
| `ASPIRATIONAL_MATCH` | 1 |
| `LOW_FIT` | 0 |
| `UNCLEAR` | Excluir o revisar manualmente |

Con dataset chico, nDCG@10 es orientativo y no estadisticamente concluyente.

## Procedimiento de evaluacion

1. Levantar PostgreSQL + pgvector.
2. Levantar `embedding-service/`.
3. Levantar Spring Boot con perfil PostgreSQL.
4. Validar que los perfiles DIAG existen y anotar IDs reales.
5. Preparar embeddings pendientes de `JOBS` y `PROFILE`.
6. Procesar embeddings pendientes con `BAAI/bge-m3`.
7. Ejecutar endpoint vector-first para cada perfil con `limit=20`.
8. Guardar JSON local en `local-evidence/vector-first-dataset/`.
9. Completar `docs/evaluation/vector-first-evaluation-dataset-template.csv` en una copia de trabajo o dataset versionado posterior.
10. Etiquetar manualmente cada fila con la guia.
11. Calcular Precision@5, Precision@10, revisables, falsos positivos y distribucion de labels.
12. Documentar falsos positivos, falsos negativos candidatos y buckets discutibles.
13. Decidir si corresponde mantener baseline, calibrar reglas o preparar `rankingMode` experimental.

## Comandos utiles

### Listar perfiles DIAG

```powershell
docker exec datalaburo-postgres psql -U datalaburo -d datalaburo -c "select id, name from candidate_profiles where name like 'DIAG - %' order by id;"
```

### Validar estado de embeddings

```powershell
Invoke-RestMethod "http://localhost:8081/internal/embeddings/status"
```

### Revisar distribucion en `document_embeddings`

```powershell
docker exec datalaburo-postgres psql -U datalaburo -d datalaburo -c "select owner_type, embedding_model, status, count(*) as total, count(*) filter (where embedding is not null) as with_embedding from document_embeddings group by owner_type, embedding_model, status order by owner_type, embedding_model, status;"
```

### Verificar cantidades `JOB` y `PROFILE`

```powershell
docker exec datalaburo-postgres psql -U datalaburo -d datalaburo -c "select owner_type, count(*) from document_embeddings group by owner_type order by owner_type;"
```

### Preparar embeddings

```powershell
Invoke-RestMethod -Method Post "http://localhost:8081/internal/embeddings/backfill/jobs?limit=100"
Invoke-RestMethod -Method Post "http://localhost:8081/internal/embeddings/backfill/profiles?limit=100"
```

### Preparar embeddings de perfiles DIAG puntuales

```powershell
$profileIds = docker exec datalaburo-postgres psql -U datalaburo -d datalaburo -t -A -c "select id from candidate_profiles where name like 'DIAG - %' order by id;"
$profileIds = $profileIds | Where-Object { $_ -match '^\d+$' }

$profileIds | ForEach-Object {
  Invoke-RestMethod -Method Post "http://localhost:8081/internal/embeddings/profiles/$_/prepare"
}
```

### Procesar pendientes con BGE-M3

```powershell
1..10 | ForEach-Object {
  Invoke-RestMethod -Method Post "http://localhost:8081/internal/embeddings/process/bge-m3/pending?limit=5"
}
```

### Ejecutar endpoint para un perfil

```powershell
$profileId = 2
$response = Invoke-RestMethod "http://localhost:8081/internal/analysis/profiles/$profileId/vector-first-compatibility?limit=20"
$response.results |
  Select-Object vectorRank,analysisRank,suggestedRerankRank,suggestedRankDelta,jobId,title,company,detectedRole,detectedSeniority,compatibilityCategory,evidenceLevel,compatibilityBucket,matchedSkills,rerankWarnings |
  Format-Table -AutoSize
```

### Guardar evidencia local por perfil

```powershell
New-Item -ItemType Directory -Force ".\local-evidence\vector-first-dataset" | Out-Null

$profileId = 2
$response = Invoke-RestMethod "http://localhost:8081/internal/analysis/profiles/$profileId/vector-first-compatibility?limit=20"
$response |
  ConvertTo-Json -Depth 12 |
  Set-Content -Encoding UTF8 ".\local-evidence\vector-first-dataset\profile-$profileId-top20.json"
```

### Repetir para perfiles DIAG

```powershell
New-Item -ItemType Directory -Force ".\local-evidence\vector-first-dataset" | Out-Null

$profileIds = @(2, 3, 4, 5, 6)
$profileIds | ForEach-Object {
  $response = Invoke-RestMethod "http://localhost:8081/internal/analysis/profiles/$_/vector-first-compatibility?limit=20"
  $response |
    ConvertTo-Json -Depth 12 |
    Set-Content -Encoding UTF8 ".\local-evidence\vector-first-dataset\profile-$_-top20.json"
}
```

### Revisar evidencia local guardada

```powershell
Get-ChildItem ".\local-evidence\vector-first-dataset\profile-*-top20.json" |
  Sort-Object Name |
  ForEach-Object {
    $j = Get-Content -Raw -Path $_.FullName | ConvertFrom-Json
    "FILE $($_.Name) profileId=$($j.profileId) strategy=$($j.retrieval.strategy) model=$($j.embeddingModel) dims=$($j.embeddingDimensions) count=$($j.results.Count)"
    $j.results |
      Select-Object -First 10 vectorRank,analysisRank,suggestedRerankRank,suggestedRankDelta,jobId,title,detectedRole,detectedSeniority,compatibilityCategory,evidenceLevel,compatibilityBucket,rerankWarnings |
      Format-Table -AutoSize
  }
```

## Requests HTTP

Archivo de requests:

```text
docs/evaluation/vector-first-evaluation-requests.http
```

Incluye:

- health/model-info de `embedding-service`;
- estado de embeddings;
- backfill/preparacion/procesamiento;
- endpoint interno por perfiles conocidos;
- limit 10 y limit 20;
- notas para guardar evidencia local.

## Resultados iniciales

Existe evidencia local no versionada en:

```text
local-evidence/vector-reranking-diagnostic/
```

Archivos observados:

| Archivo | profileId | Resultados | Estado |
| --- | ---: | ---: | --- |
| `profile-2.json` | 2 | 20 | Disponible |
| `profile-3.json` | 3 | 20 | Disponible |
| `profile-4.json` | 4 | 20 | Disponible |
| `profile-5.json` | 5 | 20 | Disponible |
| `profile-6.json` | 6 | 20 | Disponible |

Resumen inicial, sin labels humanos:

| profileId | Referencia mas reciente | Primer resultado local | Lectura inicial |
| ---: | --- | --- | --- |
| 2 | `DIAG - Backend Senior Java Cloud` | `Software Engineer Backend - Platform Security` | Backend aparece arriba con bucket `READY_NOW`; requiere etiquetado humano. |
| 3 | `DIAG - Backend Strong Partial DevOps Transfer` | `Software Engineer Backend - Platform Security` | Backend aparece arriba; caso marcado para revision por inconsistencias historicas de evidencia. |
| 4 | `DIAG - IT Support Analyst Junior` | `Technical Support Jr.` | Soporte aparece arriba; buen candidato para validar sensibilidad multi-perfil. |
| 5 | `DIAG - Data BI SQL Profile` | `Desarrollador de base de datos` | Database/data aparece arriba, pero el diagnostico sugiere revisar brechas/seniority. |
| 6 | `DIAG - Backend Trainee Projects` | `Software Engineer Backend - Platform Security` | Backend aparece arriba, pero bucket local `LOW_FIT` en el primer resultado requiere revision humana. |

No se asignan labels humanos en este documento. La evidencia local permite preparar el dataset, pero debe congelarse nuevamente con IDs validados antes de calcular metricas.

## Criterios de aceptacion

Fase 4.1 se considera cerrada cuando:

- existe guia de labels;
- existe template de dataset;
- existe plan de evaluacion;
- existe archivo de requests;
- hay al menos 5 perfiles evaluables;
- hay Top 10 o Top 20 por perfil;
- hay criterios de metricas definidos;
- se documentan limitaciones;
- se documentan inconsistencias de evidencia;
- no se activa reranking real;
- no se activa score hibrido;
- no se modifica ranking productivo.

## Decision posterior

Despues de esta fase se podra decidir:

- mantener baseline `VECTOR_FIRST_WITH_RERANKING_DIAGNOSTIC`;
- calibrar roles, seniority, buckets o warnings;
- preparar `rankingMode` experimental detras de flag/parametro;
- ampliar dataset;
- mejorar perfil realista;
- agregar proyectos manuales como evidencia estructurada.

La decision debe estar guiada por labels humanos y metricas, no por intuicion ni por una sola demo.

## Limitaciones

- Dataset local chico.
- Labels humanos pueden ser subjetivos.
- La evidencia local no debe versionarse.
- Algunas ofertas tienen encoding roto.
- IDs DIAG historicos pueden no coincidir con la base local actual.
- `Backend Strong Partial DevOps Transfer` requiere verificacion antes de usarlo como evidencia de transferencia backend -> DevOps/cloud.
- `UNCLEAR` y `needs_review` deben excluirse o tratarse aparte en metricas.
- Precision@k y nDCG@10 son orientativas con esta muestra.

## Confirmacion de alcance

Este plan no implementa calculo automatico de metricas. Un script futuro podria leer CSV + JSON local y calcular Precision@k/nDCG, pero debe agregarse en una fase posterior si la evaluacion manual lo justifica.
