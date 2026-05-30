# Workflow de etiquetado vector-first

## Objetivo

Explicar como completar manualmente `docs/evaluation/vector-first-evaluation-dataset.csv` para calcular metricas simples sin modificar ranking, embeddings ni logica funcional.

El CSV representa pares:

```text
perfil + oferta recuperada por vector-first
```

La evaluacion respeta:

- `analysisRank == vectorRank`;
- `suggestedRerankRank` y `suggestedRankDelta` son diagnosticos;
- no hay reranking real activo;
- no hay score hibrido productivo;
- `JOBS` / `jobs` es la fuente de verdad;
- `JOB_OFFERS` es legacy/no productivo.

## Como abrir el CSV

Archivo:

```text
docs/evaluation/vector-first-evaluation-dataset.csv
```

Opciones recomendadas:

- Excel o LibreOffice, cuidando que `vector_similarity` quede como numero decimal con punto o como texto sin modificar.
- VS Code, para editar como CSV plano.
- PowerShell `Import-Csv`, para revisar conteos sin editar manualmente.

No abrirlo con una herramienta que cambie encoding, separadores o comillas sin revisar el diff.

## Columnas que se completan manualmente

Completar solo estas columnas:

- `human_label`
- `human_notes`
- `is_false_positive`
- `is_false_negative_candidate`
- `needs_review`

No modificar manualmente salvo correccion justificada:

- `analysis_rank`
- `vector_rank`
- `vector_similarity`
- `system_category`
- `suggested_rerank_rank`
- `suggested_rank_delta`
- `matched_skills`
- `missing_critical_skills`
- `missing_secondary_skills`
- `transferable_skills`

## Labels permitidos

Usar exactamente uno por fila:

- `STRONG_MATCH`
- `GOOD_MATCH_WITH_GAPS`
- `TRANSFERABLE_OPPORTUNITY`
- `ASPIRATIONAL_MATCH`
- `LOW_FIT`
- `UNCLEAR`

La guia completa esta en:

```text
docs/evaluation/compatibility-labeling-guide.md
```

## Como decidir el label

Leer la fila como una decision profesional:

```text
Esta oferta seria una oportunidad razonable para este perfil?
```

Revisar:

- objetivo del perfil;
- titulo y empresa;
- rol y seniority detectados;
- skills directas;
- gaps criticos;
- gaps secundarios;
- transferencias;
- `evidence_level`;
- `confidence`;
- warnings;
- texto incompleto o encoding roto.

Si dos labels parecen posibles, usar el mas conservador y explicar la duda en `human_notes`.

## Falsos positivos

Marcar:

```text
is_false_positive=true
```

cuando una oferta aparece arriba en el ranking, pero el juicio humano indica que no deberia priorizarse.

Casos tipicos:

- Top 5 o Top 10 con label `LOW_FIT`;
- coincidencias solo genericas como `SQL`, `REST` o `Git`;
- rol alejado del objetivo del perfil;
- seniority demasiado alto;
- bucket/categoria demasiado optimista;
- texto roto que vuelve el resultado inutil para decision.

Si no aplica, dejar vacio o usar `false`. Mantener un criterio consistente en todo el archivo.

## Falsos negativos candidatos

Marcar:

```text
is_false_negative_candidate=true
```

cuando una oferta parece buena para el perfil, pero:

- aparece muy abajo;
- el diagnostico la baja de forma dudosa;
- el bucket parece demasiado pesimista;
- una oferta conocida relevante no aparece en Top 10 aunque tiene embedding `READY`.

Esta marca es candidata y requiere revision posterior. No implica automaticamente que el algoritmo este mal.

## `needs_review`

Marcar:

```text
needs_review=true
```

cuando la fila no deberia usarse sin revision adicional.

Casos tipicos:

- encoding roto o mojibake;
- titulo o descripcion incompleta;
- rol detectado contradictorio;
- seniority ambiguo;
- evidencia local inconsistente;
- dudas sobre `profile_id`;
- campos clave vacios;
- `human_label=UNCLEAR`.

## Encoding roto

Si una oferta tiene texto con mojibake o caracteres corruptos:

1. No inventar contenido.
2. Completar `human_label=UNCLEAR` si no se puede decidir.
3. Marcar `needs_review=true`.
4. Explicar el problema en `human_notes`.
5. No usar el caso para calibrar reglas hasta revisar la fuente.

## Como tratar `UNCLEAR`

Usar `UNCLEAR` cuando falta informacion suficiente para decidir, no cuando la oferta es simplemente mala.

Para metricas:

- excluir `UNCLEAR` de conclusiones fuertes;
- reportar cantidad de `UNCLEAR`;
- revisar manualmente antes de calibrar algoritmo;
- no contarlo como positivo para Precision@5 o Precision@10.

## Positivos para Precision@5 y Precision@10

Contar como positivos:

- `STRONG_MATCH`
- `GOOD_MATCH_WITH_GAPS`
- `TRANSFERABLE_OPPORTUNITY`

No contar como positivos:

- `ASPIRATIONAL_MATCH`
- `LOW_FIT`
- `UNCLEAR`

Formula:

```text
Precision@5 = positivos en Top 5 / 5
Precision@10 = positivos en Top 10 / 10
```

El script de metricas debe reportar Precision@k solo cuando las primeras k filas de cada perfil tengan `human_label` completo.

## Orden recomendado de trabajo

1. Abrir el CSV.
2. Filtrar por `profile_id`.
3. Etiquetar Top 10 de un perfil completo antes de pasar al siguiente.
4. Completar `human_notes` en casos dudosos.
5. Marcar falsos positivos y `needs_review`.
6. Guardar el CSV.
7. Ejecutar el script de metricas.
8. Revisar si hay labels vacios antes de sacar conclusiones.

## Comando de revision rapida

```powershell
$rows = Import-Csv .\docs\evaluation\vector-first-evaluation-dataset.csv
$rows.Count
$rows | Group-Object profile_id | Sort-Object Name | Select-Object Name, Count
$rows | Where-Object { [string]::IsNullOrWhiteSpace($_.human_label) } | Measure-Object
```

## Script de metricas

Script:

```text
scripts/evaluation/compute-vector-first-metrics.ps1
```

Uso:

```powershell
.\scripts\evaluation\compute-vector-first-metrics.ps1
```

Si Windows bloquea la ejecucion por Execution Policy:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\evaluation\compute-vector-first-metrics.ps1
```

Con path explicito:

```powershell
.\scripts\evaluation\compute-vector-first-metrics.ps1 -CsvPath .\docs\evaluation\vector-first-evaluation-dataset.csv
```

Si el CSV todavia no tiene labels humanos, el script reportara filas etiquetadas en cero y Precision@k como no disponible.
