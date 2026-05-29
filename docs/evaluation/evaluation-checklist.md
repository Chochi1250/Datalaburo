# Evaluation Evidence Pack Checklist

Checklist de cierre para la fase 1 de evaluacion documental.

## Alcance documental

- [ ] Existe documento principal del pack: `docs/evaluation/evaluation-evidence-pack.md`.
- [ ] README enlaza al pack de evaluacion.
- [ ] La estrategia `VECTOR_FIRST_WITH_RERANKING_DIAGNOSTIC` esta explicada.
- [ ] El endpoint vector-first esta documentado.
- [ ] Los perfiles DIAG estan identificados.
- [ ] Hay ejemplos de requests locales.
- [ ] Las limitaciones estan declaradas.
- [ ] Los proximos pasos estan definidos.

## Baseline tecnico

- [ ] PostgreSQL + pgvector figura como base objetivo.
- [ ] H2 figura como legacy/obsoleto.
- [ ] `BAAI/bge-m3` figura como modelo real.
- [ ] `fake-deterministic-1024` queda limitado a infraestructura/test.
- [ ] `document_embeddings.embedding vector(1024)` esta documentado.
- [ ] `analysisRank == vectorRank` queda explicitado.
- [ ] `suggestedRerankRank` queda documentado como diagnostico.
- [ ] No se presenta score hibrido como productivo.

## Evidencia local

- [ ] Perfiles `DIAG - ...` cargados o actualizados.
- [ ] Embeddings de ofertas preparados.
- [ ] Embeddings de perfiles preparados.
- [ ] Pendientes BGE-M3 procesados.
- [ ] Endpoint ejecutado para cada perfil DIAG.
- [ ] Resultados guardados en `local-evidence/vector-reranking-diagnostic/`.
- [ ] Resultados resumidos sin versionar JSON locales completos.
- [ ] Casos positivos documentados.
- [ ] Casos discutibles documentados.

## Restricciones de cierre

- [ ] No se activo reranking real.
- [ ] No se activo score hibrido.
- [ ] No se cambio `analysisRank`.
- [ ] No se modifico ranking productivo.
- [ ] No se tocaron scraping, extension, captura, UI ni migraciones.
- [ ] No se modifico `CvMatchingService`.

## Decision antes de avanzar

- [ ] Se decidio si hace falta calibracion menor.
- [ ] Se audito o planifico auditar `JOBS` vs `JOB_OFFERS`.
- [ ] Se definio si la siguiente fase es UI minima, mas evaluacion o `rankingMode` experimental.
