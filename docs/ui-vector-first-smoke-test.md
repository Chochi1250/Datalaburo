# Smoke test UI vector-first

## Objetivo

Validar la pantalla MVC `GET /profiles/{profileId}/vector-first-compatibility?limit=20` con perfiles DIAG existentes, sin cambiar ranking, embeddings, servicios, endpoints internos ni flujos legacy.

## Alcance

Validacion realizada contra la app local en:

```text
http://127.0.0.1:8081
```

Base local observada:

```text
datalaburo-postgres: Up
```

La validacion uso:

- carga HTTP de la vista MVC vector-first;
- endpoint interno solo como apoyo de observacion del primer resultado;
- chequeo de `/matching`;
- chequeo de `/jobs/1/match`.

No se ejecuto backfill ni procesamiento de embeddings desde UI.

## Perfiles DIAG detectados

| profileId | Perfil |
| ---: | --- |
| 2 | DIAG - Backend Senior Java Cloud |
| 3 | DIAG - Backend Strong Partial DevOps Transfer |
| 4 | DIAG - IT Support Analyst Junior |
| 5 | DIAG - Data BI SQL Profile |
| 6 | DIAG - Backend Trainee Projects |

## Resultados UI

| Perfil probado | URL usada | Carga UI | Resultados | Primer resultado observado | Observaciones UI |
| --- | --- | --- | --- | --- | --- |
| Backend Senior Java Cloud | `/profiles/2/vector-first-compatibility?limit=20` | Si, HTTP 200 | Si, 20 | `Software Engineer Backend - Platform Security` (`vectorRank=1`, `analysisRank=1`) | Labels amigables visibles; `Ver oferta` aparece como boton; diagnostico compacto disponible. |
| Backend Strong Partial DevOps Transfer | `/profiles/3/vector-first-compatibility?limit=20` | Si, HTTP 200 | Si, 20 | `Software Engineer Backend - Platform Security` (`vectorRank=1`, `analysisRank=1`) | Labels amigables visibles; conviene revisar manualmente si el perfil DIAG esperado coincide con el id actual. |
| IT Support Analyst Junior | `/profiles/4/vector-first-compatibility?limit=20` | Si, HTTP 200 | Si, 20 | `Technical Support Jr.` (`vectorRank=1`, `analysisRank=1`) | Buen cambio de contexto hacia soporte; labels de evidencia/categoria se leen sin exponer solo enums. |
| Data BI SQL Profile | `/profiles/5/vector-first-compatibility?limit=20` | Si, HTTP 200 | Si, 20 | `Desarrollador de base de datos` (`vectorRank=1`, `analysisRank=1`) | Aparece como `Match aspiracional`; `suggestedRerankRank=3` queda en diagnostico, no cambia el orden. |
| Backend Trainee Projects | `/profiles/6/vector-first-compatibility?limit=20` | Si, HTTP 200 | Si, 20 | `Software Engineer Backend - Platform Security` (`vectorRank=1`, `analysisRank=1`) | Evidencia `Proyecto` visible con label amigable; ranking activo conserva `analysisRank == vectorRank`. |

## Observaciones de presentacion

- La UI mantiene visible que el ranking activo es `analysisRank` y que actualmente equivale a `vectorRank`.
- `suggestedRerankRank` queda rotulado como diagnostico interno.
- No se muestra score hibrido productivo.
- Las categorias principales se muestran con labels amigables, manteniendo el enum tecnico como apoyo donde aporta trazabilidad.
- Skills, gaps, roadmap y transferencias se muestran como elementos separados, evitando concatenaciones visuales.
- El bloque `Diagnostico interno` no domina la tarjeta; cuando no hay advertencias, muestra un mensaje breve.

## Flujo viejo

| Flujo | URL | Resultado |
| --- | --- | --- |
| Matching general viejo | `/matching` | HTTP 200, formulario visible. |
| Matching viejo por oferta | `/jobs/1/match` | HTTP 200, formulario visible. |

## Casos raros o discutibles

- El orden real de IDs DIAG en la base local no coincide con el orden historico documentado en `docs/evaluation-vector-first-diagnostic.md`.
- El perfil `DIAG - Backend Strong Partial DevOps Transfer` debe seguir revisandose manualmente antes de usarse como evidencia fina de transferencia backend -> DevOps/cloud.
- El perfil `Data BI SQL Profile` conserva `analysisRank=1` para `Desarrollador de base de datos`, pero el diagnostico sugiere otro orden; esto es correcto para esta fase porque no hay reranking activo.

## Confirmaciones

- No se cambio `analysisRank`.
- No se activo reranking real.
- No se activo score hibrido.
- No se modifico `CvMatchingService`.
- No se modifico `VectorFirstCompatibilityService`.
- No se uso `JOB_OFFERS`.
- No se tocaron `/matching` ni `/jobs/{id}/match`.
