# Evaluation Evidence Pack

Este documento define la linea base de evaluacion de Datalaburo antes de avanzar con UI, ranking experimental o un dataset mas grande.

La fase es documental. No cambia servicios productivos, entidades, endpoints, migraciones, scraping, extension, captura, UI ni logica de ranking.

## Objetivo

Documentar de forma reproducible:

- que arquitectura vectorial se evalua;
- que endpoint se usa;
- como interpretar rankings y diagnosticos;
- que perfiles DIAG sirven como base de prueba;
- que limitaciones tiene la evidencia actual;
- que falta antes de incorporar cambios experimentales.

Datalaburo utiliza embeddings para representar perfiles y ofertas laborales. La busqueda vectorial permite recuperar ofertas semanticamente cercanas al perfil evaluado. La capa de compatibilidad agrega explicaciones sobre rol, seniority, skills, brechas, evidencia y transferibilidad.

## Baseline Actual

Estrategia evaluada:

```text
VECTOR_FIRST_WITH_RERANKING_DIAGNOSTIC
```

| Elemento | Estado |
| --- | --- |
| Base objetivo | PostgreSQL + pgvector |
| Base legacy | H2, historico/tests; no es arquitectura objetivo |
| Modelo real | `BAAI/bge-m3` |
| Modelo fake | `fake-deterministic-1024`, solo infraestructura/test |
| Tabla vectorial | `document_embeddings` |
| Dimension | `vector(1024)` |
| Ranking productivo | Vector-first |
| Orden activo | `analysisRank == vectorRank` |
| Reranking real | No activo |
| Score hibrido productivo | No activo |
| Buckets | Diagnosticos internos |

Interpretacion obligatoria:

- `vectorRank` conserva el ranking semantico original de pgvector.
- `analysisRank` conserva el orden vectorial.
- `suggestedRerankRank` y `suggestedRankDelta` son diagnosticos.
- El reranking sugerido no altera el ranking productivo.
- No se debe presentar `suggestedRerankRank` como ranking real.
- No se debe presentar un score hibrido como funcionalidad activa.

## Endpoint Evaluado

```http
GET /internal/analysis/profiles/{profileId}/vector-first-compatibility?limit=20
```

Campos principales:

| Campo | Uso en evaluacion |
| --- | --- |
| `embeddingModel` | Debe ser `BAAI/bge-m3`. |
| `embeddingDimensions` | Debe ser `1024`. |
| `strategy` | Debe indicar `VECTOR_FIRST_WITH_RERANKING_DIAGNOSTIC`. |
| `vectorRank` | Ranking semantico original. |
| `analysisRank` | Ranking activo actual; debe coincidir con `vectorRank`. |
| `vectorSimilarity` | Cercania semantica, no compatibilidad completa. |
| `detectedRole` | Rol inferido de la oferta. |
| `detectedSeniority` | Seniority inferido de la oferta. |
| `compatibilityBucket` | Bucket diagnostico interno. |
| `evidenceLevel` | Fuerza de evidencia del perfil. |
| `matchedSkills` | Skills detectadas en comun. |
| `missingCriticalSkills` | Brechas centrales para el rol. |
| `missingSecondarySkills` | Brechas complementarias. |
| `transferableSkills` | Skills relacionadas o parcialmente transferibles. |
| `suggestedRerankRank` | Orden hipotetico de diagnostico. |
| `suggestedRankDelta` | Movimiento hipotetico respecto de `vectorRank`. |
| `rerankReasons` | Razones del diagnostico. |
| `rerankWarnings` | Riesgos o detecciones dudosas. |

## Diagnosticos Incluidos

El endpoint calcula diagnosticos para auditoria:

- buckets como `READY_NOW`, `GOOD_WITH_MINOR_GAPS`, `TRANSFERABLE`, `ASPIRATIONAL`, `WEAK_MATCH` y `LOW_FIT`;
- razones de posible subida o bajada;
- warnings de rol, seniority o evidencia;
- gaps criticos y secundarios;
- transferibilidad;
- `evidenceLevel`;
- `suggestedRerankRank` y `suggestedRankDelta`.

Estos datos ayudan a revisar falsos positivos, falsos negativos y buckets discutibles. No cambian el orden productivo.

## Perfiles DIAG

Los perfiles de evaluacion estan definidos en:

```text
docs/vector-reranking-diagnostic-test-profiles.sql
```

| Perfil | Proposito |
| --- | --- |
| `DIAG - Backend Trainee Projects` | Backend inicial con proyectos y sin seniority laboral fuerte. |
| `DIAG - Backend Senior Java Cloud` | Backend senior con cloud y experiencia profesional. |
| `DIAG - IT Support Analyst Junior` | Soporte, help desk y application support. |
| `DIAG - Data BI SQL Profile` | Data, BI, SQL y reporting. |
| `DIAG - Backend Strong Partial DevOps Transfer` | Transferencia parcial de backend hacia DevOps/cloud. |

La lectura multi-perfil detallada esta en:

```text
docs/evaluation-vector-first-diagnostic.md
```

## Reproduccion Manual

Prerequisitos:

1. PostgreSQL con pgvector levantado.
2. `embedding-service/` corriendo en `http://127.0.0.1:8001`.
3. Spring Boot corriendo con PostgreSQL.
4. Perfiles DIAG cargados.
5. Embeddings `BAAI/bge-m3` preparados y procesados.

Listar perfiles DIAG:

```powershell
docker exec datalaburo-postgres psql -U datalaburo -d datalaburo -c "select id, name from candidate_profiles where name like 'DIAG - %' order by id;"
```

Cargar o actualizar perfiles DIAG:

```powershell
Get-Content .\docs\vector-reranking-diagnostic-test-profiles.sql |
  docker exec -i datalaburo-postgres psql -U datalaburo -d datalaburo
```

Preparar embeddings:

```powershell
Invoke-RestMethod -Method Post "http://localhost:8081/internal/embeddings/backfill/jobs?limit=100"
Invoke-RestMethod -Method Post "http://localhost:8081/internal/embeddings/backfill/profiles?limit=100"

$profileIds = docker exec datalaburo-postgres psql -U datalaburo -d datalaburo -t -A -c "select id from candidate_profiles where name like 'DIAG - %' order by id;"
$profileIds = $profileIds | Where-Object { $_ -match '^\d+$' }

$profileIds | ForEach-Object {
  Invoke-RestMethod -Method Post "http://localhost:8081/internal/embeddings/profiles/$_/prepare"
}
```

Procesar pendientes con BGE-M3:

```powershell
1..10 | ForEach-Object {
  Invoke-RestMethod -Method Post "http://localhost:8081/internal/embeddings/process/bge-m3/pending?limit=5"
}

Invoke-RestMethod "http://localhost:8081/internal/embeddings/status"
```

Consultar compatibilidad vector-first:

```powershell
$profileIds | ForEach-Object {
  $response = Invoke-RestMethod "http://localhost:8081/internal/analysis/profiles/$_/vector-first-compatibility?limit=20"
  $response.results |
    Select-Object vectorRank,analysisRank,suggestedRerankRank,suggestedRankDelta,title,detectedRole,detectedSeniority,compatibilityBucket,evidenceLevel,matchedSkills,rerankWarnings |
    Format-Table -AutoSize
}
```

Guardar evidencia local no versionada:

```powershell
New-Item -ItemType Directory -Force ".\local-evidence\vector-reranking-diagnostic" | Out-Null

$profileIds | ForEach-Object {
  $response = Invoke-RestMethod "http://localhost:8081/internal/analysis/profiles/$_/vector-first-compatibility?limit=20"
  $response |
    ConvertTo-Json -Depth 12 |
    Set-Content -Encoding UTF8 ".\local-evidence\vector-reranking-diagnostic\profile-$_.json"
}
```

Requests HTTP equivalentes:

```text
docs/evaluation/sample-requests.http
```

## Interpretacion

Para revisar cada respuesta:

1. Confirmar `embeddingModel = BAAI/bge-m3`.
2. Confirmar `embeddingDimensions = 1024`.
3. Confirmar `strategy = VECTOR_FIRST_WITH_RERANKING_DIAGNOSTIC`.
4. Verificar que `analysisRank` conserve `vectorRank`.
5. Revisar rol, seniority, skills, gaps, transferibilidad y `evidenceLevel`.
6. Usar `compatibilityBucket` como diagnostico, no como decision final.
7. Usar `suggestedRerankRank` para auditoria, no para ordenar resultados.
8. Registrar falsos positivos, falsos negativos y buckets discutibles.

## Evidencia Local

La evidencia local debe guardarse en:

```text
local-evidence/
```

Ese directorio no se versiona. Los JSON locales pueden resumirse en documentos, pero no deben copiarse completos al repositorio salvo que se creen fixtures o ejemplos sinteticos.

Resumen disponible:

```text
docs/evaluation-vector-first-diagnostic.md
```

## Limitaciones

- Dataset local chico.
- Algunas ofertas capturadas tienen problemas de encoding.
- La deteccion de rol y seniority puede ser discutible en casos ambiguos.
- Los buckets son diagnosticos internos.
- `suggestedRerankRank` no debe usarse como orden real.
- La evidencia requiere revision manual antes de activar reranking real.
- Puede requerirse calibracion menor ante falsos positivos o falsos negativos claros.
- La relacion `JOBS` vs `JOB_OFFERS` debe auditarse antes de ampliar evaluacion o construir UI.

## Criterios de Aceptacion

La fase puede considerarse cerrada cuando:

- este documento existe;
- el endpoint vector-first esta documentado;
- la estrategia esta explicada;
- queda claro que `analysisRank == vectorRank`;
- queda claro que no hay reranking real ni score hibrido productivo;
- hay ejemplos de requests;
- hay checklist de cierre;
- las limitaciones estan declaradas;
- los proximos pasos estan definidos;
- no hay cambios funcionales en ranking.

Checklist:

```text
docs/evaluation/evaluation-checklist.md
```

## Proximos Pasos

Orden recomendado:

1. Auditar `JOBS` vs `JOB_OFFERS`.
2. Regenerar evidencia DIAG dudosa o incompleta.
3. Completar una tabla manual de falsos positivos, falsos negativos y buckets discutibles.
4. Construir una UI minima solo despues de cerrar la evidencia base.
5. Evaluar `rankingMode` experimental en una fase posterior, detras de flag, parametro o endpoint separado.

La siguiente fase recomendada es auditar datos fuente y cerrar evidencia manual suficiente. No se recomienda activar reranking real todavia.
