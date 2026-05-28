# Evaluación diagnóstica vector-first

Este documento convierte la validacion multi-perfil de Datalaburo en evidencia defendible para tesis. La fase es solo de evaluacion y documentacion: no activa reranking real, no modifica el pipeline funcional y no cambia scraping, extension, captura, UI, migraciones ni `CvMatchingService`.

## Objetivo

Validar si el sistema cambia su analisis segun el perfil profesional usado como consulta y si `compatibilityBucket`, `suggestedRerankRank`, `suggestedRankDelta`, `matchedSkills` y `rerankWarnings` son razonables antes de activar cualquier reranking real.

La evaluacion busca responder:

- si PostgreSQL + pgvector + `BAAI/bge-m3` recupera candidatos semanticamente cercanos;
- si la capa de analisis distingue perfiles backend, soporte y data;
- si los buckets diagnosticos son coherentes con rol, seniority, skills, gaps y evidencia;
- si `suggestedRerankRank` aporta una hipotesis auditable sin reemplazar el ranking vectorial;
- si aparecen falsos positivos o falsos negativos que obliguen a calibrar antes de exponer una UI.

## Estado evaluado

| Elemento | Estado |
| --- | --- |
| Base objetivo | PostgreSQL + pgvector |
| Modelo real | `BAAI/bge-m3` |
| Dimension vectorial | `document_embeddings.embedding vector(1024)` |
| Endpoint evaluado | `GET /internal/analysis/profiles/{profileId}/vector-first-compatibility?limit=20` |
| Estrategia | `VECTOR_FIRST_WITH_RERANKING_DIAGNOSTIC` |
| Ranking semantico original | `vectorRank` conservado |
| Ranking activo | `analysisRank == vectorRank` |
| Ranking sugerido | `suggestedRerankRank` solo diagnostico |
| Delta sugerido | `suggestedRankDelta` solo diagnostico |
| Reranking real | No activo |
| Score hibrido | No activo |

La interpretacion vigente es:

1. pgvector + `BAAI/bge-m3` recupera Top N ofertas.
2. `vectorRank` conserva el orden semantico original.
3. `analysisRank` sigue igual a `vectorRank`.
4. `suggestedRerankRank` y `suggestedRankDelta` son diagnosticos para auditar una posible evolucion.
5. Los buckets son senales internas de compatibilidad, no una verdad absoluta ni una decision automatica.

## Perfiles usados

| Perfil | Objetivo | Skills principales | Comportamiento esperado |
| --- | --- | --- | --- |
| Backend Trainee Projects | Validar perfil backend inicial con evidencia de proyectos y sin seniority laboral fuerte. | Java, Spring Boot, REST APIs, Maven, JUnit, Mockito, PostgreSQL, MySQL, Docker, Git. | Mantener arriba roles backend junior/trainee o backend general; degradar seniorities altos, soporte puro, seguridad/IAM y roles alejados. |
| Backend Senior Java Cloud | Validar perfil backend senior con experiencia profesional y cloud. | Java, Spring Boot, microservices, REST APIs, PostgreSQL, MySQL, Kafka, Docker, Kubernetes, AWS, CI/CD, Git, Linux, observability. | Mantener arriba backend/platform/cloud backend; aceptar DevOps/cloud si hay transferencia fuerte; degradar soporte, data pura e IAM si no hay alineacion de rol. |
| IT Support Analyst Junior | Validar cambio de analisis hacia soporte y application support. | Help desk, service desk, Windows Server, Linux basics, Active Directory, Office 365, networking, SQL basics, ITIL, customer support. | Priorizar soporte tecnico, mesa de ayuda, application support e IT analyst; degradar backend, data senior, seguridad/IAM y roles de desarrollo alejados. |
| Data BI SQL Profile | Validar orientacion data/BI/SQL. | SQL, PostgreSQL, SQL Server, Power BI, Excel, dashboards, reporting, ETL basics, data quality, Python para limpieza/visualizacion. | Priorizar data analyst, BI, reporting, SQL/database; degradar soporte, backend no-data, seguridad/IAM y roles sin nucleo de datos. |
| Backend Strong Partial DevOps Transfer | Validar transferencia backend fuerte hacia DevOps/cloud sin convertirla automaticamente en match fuerte. | Java, Spring Boot, REST APIs, PostgreSQL, Docker, Git, testing, Docker Compose, Kubernetes basico, Linux, CI/CD, AWS fundamentals. | Mantener backend arriba; reconocer DevOps/cloud como transferible parcial; degradar DevOps senior/cloud avanzado si faltan skills nucleares o evidencia suficiente. |

## Metodología

1. Cargar o actualizar los perfiles `DIAG - ...`.
2. Preparar embeddings para ofertas y perfiles.
3. Procesar pendientes con `BAAI/bge-m3`.
4. Ejecutar el endpoint vector-first para cada perfil con `limit=20`.
5. Guardar cada respuesta completa en `local-evidence/vector-reranking-diagnostic/`.
6. Comparar Top N por perfil.
7. Revisar `vectorRank`, `analysisRank`, `compatibilityBucket`, `suggestedRerankRank`, `suggestedRankDelta`, `matchedSkills` y `rerankWarnings`.
8. Interpretar manualmente falsos positivos y falsos negativos.

Lectura recomendada de cada resultado:

- `vectorRank`: posicion original de pgvector; es la auditoria semantica.
- `analysisRank`: posicion activa actual; debe ser igual a `vectorRank`.
- `compatibilityBucket`: clasificacion diagnostica interna.
- `suggestedRerankRank`: posicion hipotetica si se activara reranking, no debe usarse como orden real.
- `suggestedRankDelta`: diferencia diagnostica entre ranking vectorial y ranking sugerido.
- `matchedSkills`: evidencia directa de coincidencia.
- `rerankWarnings`: senales de riesgo o deteccion dudosa.

## Resultados esperados por perfil

| Perfil | Que deberia subir o mantenerse | Que deberia bajar | Senales esperadas |
| --- | --- | --- | --- |
| Backend Trainee Projects | Backend junior/trainee, backend general, ofertas con Java/Spring/REST y gaps menores. | Senior backend exigente, IAM/security, soporte puro, data senior, DevOps avanzado. | `READY_NOW`, `GOOD_WITH_MINOR_GAPS` o `TRANSFERABLE` para backend razonable; warnings o buckets bajos para roles alejados. |
| Backend Senior Java Cloud | Backend senior, platform backend, cloud backend, microservices, Java/Spring, Docker/Kubernetes/AWS. | Soporte junior, data/reporting sin backend, security/IAM si el rol no es backend/cloud. | Evidencia fuerte, pocas brechas criticas, transferibilidad backend-cloud razonable. |
| IT Support Analyst Junior | Technical support, help desk, service desk, app support, IT analyst junior. | Backend, data senior, security/IAM, roles de desarrollo que solo comparten SQL/Linux superficialmente. | Alineacion de rol IT_SUPPORT/APP_SUPPORT, buckets moderados si la evidencia es debil, warnings en clasificaciones dudosas. |
| Data BI SQL Profile | Database, data analyst, BI, SQL reporting, ETL basico, Power BI/Excel/Python. | Soporte no-data, backend general, seguridad/IAM, roles senior con brechas criticas. | SQL/PostgreSQL/SQL Server como matches, gaps explicitos cuando falten Oracle/Azure u otros requisitos centrales. |
| Backend Strong Partial DevOps Transfer | Backend fuerte y roles backend con crecimiento cloud. | DevOps/cloud senior si faltan Kubernetes/AWS/CI-CD profundos; soporte, data y security sin relacion clara. | Transferibilidad parcial Docker -> Kubernetes/cloud, bucket no inflado si faltan skills nucleares. |

## Resultados observados

Existe evidencia local en:

```text
local-evidence/vector-reranking-diagnostic/
```

Archivos encontrados:

| Archivo | Perfil esperado por orden de carga DIAG | Estado de evidencia |
| --- | --- | --- |
| `profile-2.json` | Backend Trainee Projects | Disponible |
| `profile-3.json` | Backend Senior Java Cloud | Disponible |
| `profile-4.json` | IT Support Analyst Junior | Disponible |
| `profile-5.json` | Data BI SQL Profile | Disponible |
| `profile-6.json` | Backend Strong Partial DevOps Transfer | Disponible, pero requiere verificacion porque la salida observada parece inconsistente con el objetivo esperado del perfil |

Resumen de evidencia local disponible, sin inventar resultados fuera de esos JSON:

| Perfil / archivo | Observacion local |
| --- | --- |
| `profile-2.json` | `Software Engineer Backend - Platform Security` aparece en `vectorRank=1`, `analysisRank=1`, `compatibilityBucket=READY_NOW`, con matches `Java` y `REST`. Roles de soporte, security ops y data aparecen mas abajo o con buckets bajos/aspiracionales. |
| `profile-3.json` | `Software Engineer Backend - Platform Security` tambien aparece primero y se mantiene en `suggestedRerankRank=1`. El Top 8 incluye database, app support, security ops y dotnet fullstack con buckets mas bajos, lo que permite revisar falsos positivos por cercania semantica. |
| `profile-4.json` | El perfil de soporte prioriza `Technical Support Jr.`, `Analista de Soporte`, `Soporte a aplicaciones` y `Soporte de APPs Mesa de ayuda APPS SSR Pilar`. Aparecen matches como `Windows Server`, `Linux`, `SQL` e `ITIL`. |
| `profile-5.json` | El perfil data/BI prioriza `Desarrollador de base de datos`, `ANALISTA FUNCIONAL TIC - Bioter S.A.` y resultados con `SQL`, `PostgreSQL` y `SQL Server`. El primer resultado queda como `ASPIRATIONAL` por brechas como `Oracle` y `Azure`, lo cual es una senal util para no venderlo como match fuerte. |
| `profile-6.json` | La salida conserva `Software Engineer Backend - Platform Security` en primer lugar, pero el bucket observado es `LOW_FIT` y las razones mencionan un objetivo de perfil `QA`. Esta evidencia no debe usarse como confirmacion de Backend Strong Partial DevOps Transfer sin regenerarla o verificar el id/perfil consultado. |

La evidencia actual permite sostener que el endpoint cambia su comportamiento entre backend, soporte y data, especialmente en los perfiles `profile-2.json` a `profile-5.json`. Tambien muestra por que esta fase debe seguir siendo diagnostica: hay casos que requieren revision manual antes de usar `suggestedRerankRank` como orden real.

## Casos positivos

- Los perfiles backend mantienen arriba `Software Engineer Backend - Platform Security`.
- El perfil IT Support prioriza soporte tecnico, app support y mesa de ayuda.
- El perfil Data BI SQL prioriza database/data/SQL y expone brechas cuando la oferta pide tecnologias no cubiertas.
- Roles alejados como IAM, security ops, soporte o data aparecen con buckets bajos cuando no corresponden al objetivo del perfil.
- `rerankWarnings` aparece en roles dudosos, por ejemplo clasificaciones de Customer Success/Training como security ops.
- `suggestedRerankRank` ayuda a auditar cambios hipoteticos sin alterar `analysisRank`.

## Casos discutibles o limitaciones

- El dataset local es chico y no permite conclusiones estadisticas fuertes.
- Algunos roles pueden detectarse mal y requieren revision manual.
- Hay encoding roto en algunas ofertas capturadas; por ejemplo titulos con mojibake en evidencia local.
- Los buckets son diagnosticos internos, no ranking final ni verdad absoluta.
- `suggestedRerankRank` todavia no debe usarse como orden real.
- `profile-6.json` parece inconsistente con el perfil esperado y debe regenerarse o auditarse antes de citarlo como evidencia de transferencia backend -> DevOps.
- La cercania vectorial puede traer candidatos semanticamente relacionados pero profesionalmente discutibles.
- La deteccion de seniority puede quedar en `UNKNOWN` o clasificar ofertas de forma discutible.
- Se necesita revision manual antes de activar reranking real.

## Decisión sobre reranking real

No activar reranking real todavia.

La decision defendible para esta fase es mantener:

```text
VECTOR_FIRST_WITH_RERANKING_DIAGNOSTIC
```

Motivos:

- `vectorRank` sigue siendo la auditoria semantica mas limpia.
- `analysisRank == vectorRank` evita cambiar comportamiento productivo sin evidencia suficiente.
- `suggestedRerankRank` ya produce informacion util para auditar, pero todavia muestra casos que necesitan calibracion.
- El dataset es chico y debe ampliarse con mas perfiles, ofertas y revision humana.
- Una futura activacion deberia hacerse detras de un flag, parametro o endpoint experimental.

## Qué falta antes de frontend

Antes de construir una UI minima de compatibilidad vector-first, falta:

1. Completar la evaluacion multi-perfil documentada.
2. Regenerar o verificar evidencia dudosa, especialmente el caso `profile-6.json`.
3. Calibrar reglas menores si aparecen falsos positivos o falsos negativos claros.
4. Hacer una auditoria minima de `JOBS` vs `JOB_OFFERS` para confirmar cual es la fuente vigente de ofertas.
5. Recien despues, exponer una UI minima que muestre compatibilidad vector-first como evidencia explicable y no como score absoluto.

## Comandos útiles

Listar perfiles DIAG:

```powershell
docker exec datalaburo-postgres psql -U datalaburo -d datalaburo -c "select id, name from candidate_profiles where name like 'DIAG - %' order by id;"
```

Cargar o actualizar perfiles DIAG:

```powershell
Get-Content .\docs\vector-reranking-diagnostic-test-profiles.sql |
  docker exec -i datalaburo-postgres psql -U datalaburo -d datalaburo
```

Preparar embeddings de perfiles DIAG:

```powershell
$profileIds = docker exec datalaburo-postgres psql -U datalaburo -d datalaburo -t -A -c "select id from candidate_profiles where name like 'DIAG - %' order by id;"
$profileIds = $profileIds | Where-Object { $_ -match '^\d+$' }

$profileIds | ForEach-Object {
  Invoke-RestMethod -Method Post "http://localhost:8081/internal/embeddings/profiles/$_/prepare"
}
```

Preparar metadata de ofertas y perfiles:

```powershell
Invoke-RestMethod -Method Post "http://localhost:8081/internal/embeddings/backfill/jobs?limit=100"
Invoke-RestMethod -Method Post "http://localhost:8081/internal/embeddings/backfill/profiles?limit=100"
```

Procesar pendientes con `BAAI/bge-m3`:

```powershell
1..10 | ForEach-Object {
  Invoke-RestMethod -Method Post "http://localhost:8081/internal/embeddings/process/bge-m3/pending?limit=5"
}

Invoke-RestMethod "http://localhost:8081/internal/embeddings/status"
```

Ejecutar endpoint por perfil y ver resumen:

```powershell
$profileIds | ForEach-Object {
  $r = Invoke-RestMethod "http://localhost:8081/internal/analysis/profiles/$_/vector-first-compatibility?limit=20"
  $r.results |
    Select-Object vectorRank,analysisRank,suggestedRerankRank,suggestedRankDelta,title,detectedRole,detectedSeniority,compatibilityBucket,matchedSkills,rerankWarnings |
    Format-Table -AutoSize
}
```

Guardar evidencia completa en `local-evidence/`:

```powershell
New-Item -ItemType Directory -Force ".\local-evidence\vector-reranking-diagnostic" | Out-Null

$profileIds | ForEach-Object {
  $response = Invoke-RestMethod "http://localhost:8081/internal/analysis/profiles/$_/vector-first-compatibility?limit=20"
  $response |
    ConvertTo-Json -Depth 12 |
    Set-Content -Encoding UTF8 ".\local-evidence\vector-reranking-diagnostic\profile-$_.json"
}
```

Revisar evidencia guardada:

```powershell
Get-ChildItem ".\local-evidence\vector-reranking-diagnostic\profile-*.json" |
  Sort-Object Name |
  ForEach-Object {
    $j = Get-Content -Raw -Path $_.FullName | ConvertFrom-Json
    "FILE $($_.Name) profileId=$($j.profileId) strategy=$($j.retrieval.strategy) model=$($j.embeddingModel) dims=$($j.embeddingDimensions) count=$($j.results.Count)"
    $j.results |
      Select-Object -First 10 vectorRank,analysisRank,suggestedRerankRank,suggestedRankDelta,title,detectedRole,detectedSeniority,compatibilityBucket,matchedSkills,rerankWarnings |
      Format-Table -AutoSize
  }
```

## Criterio de cierre de esta fase

La fase Evaluation Evidence Pack queda completa cuando:

- existe evidencia local por cada perfil DIAG;
- los resultados observados estan resumidos sin inventar datos;
- los casos positivos y discutibles estan documentados;
- queda explicita la decision de no activar reranking real;
- queda claro que frontend debe esperar a una validacion minima adicional.
