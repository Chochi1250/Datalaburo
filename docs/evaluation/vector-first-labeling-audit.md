# Auditoria de etiquetado vector-first

## Objetivo

Auditar la primera pasada humana del dataset vector-first, sintetizar hallazgos metodologicos y recomendar una segunda pasada de etiquetado antes de usar las metricas como conclusion.

Esta auditoria no modifica ranking, embeddings, servicios, endpoints ni UI funcional. El objetivo es revisar el dataset y convertir las notas humanas en criterios mas consistentes.

## Estado del dataset

Fuente versionable auditada:

```text
docs/evaluation/vector-first-evaluation-dataset.csv
```

La copia local en Excel:

```text
C:\Users\lurie\Downloads\vector-first-evaluation-dataset.xlsx
```

contenia la primera pasada humana. Se sincronizaron al CSV solo las columnas manuales (`human_label`, `human_notes`, `is_false_positive`, `is_false_negative_candidate`, `needs_review`) usando `evaluation_id` como clave. No se reemplazaron campos del sistema desde el Excel.

| Metrica | Valor |
| --- | ---: |
| Total filas | 50 |
| Filas etiquetadas | 50 |
| Filas sin label | 0 |
| Perfiles evaluados | 5 |
| `needs_review=true` | 13 |
| `is_false_positive=true` | 2 |
| `is_false_negative_candidate=true` | 4 |
| `UNCLEAR` | 5 |
| Labels fuera de la guia | 0 |

Nota de normalizacion:

```text
GOOD_MATCH_WITH_MINOR_GAPS -> GOOD_MATCH_WITH_GAPS
```

Se normalizaron 17 filas para alinear el dataset con la guia de etiquetado humano. En una pasada posterior, las filas que estaban sin label fueron marcadas como `UNCLEAR` cuando no tenian informacion suficiente para una etiqueta limpia.

## Labels por perfil

| profileId | Perfil | Filas | Etiquetadas | Labels | needs_review | false positives | false negative candidates |
| ---: | --- | ---: | ---: | --- | ---: | ---: | ---: |
| 2 | DIAG - Backend Senior Java Cloud | 10 | 10 | `STRONG_MATCH=2`; `GOOD_MATCH_WITH_GAPS=4`; `TRANSFERABLE_OPPORTUNITY=1`; `LOW_FIT=3` | 1 | 1 | 0 |
| 3 | DIAG - Backend Strong Partial DevOps Transfer | 10 | 10 | `STRONG_MATCH=3`; `GOOD_MATCH_WITH_GAPS=4`; `TRANSFERABLE_OPPORTUNITY=1`; `LOW_FIT=1`; `UNCLEAR=1` | 2 | 0 | 0 |
| 4 | DIAG - IT Support Analyst Junior | 10 | 10 | `STRONG_MATCH=4`; `GOOD_MATCH_WITH_GAPS=1`; `TRANSFERABLE_OPPORTUNITY=2`; `ASPIRATIONAL_MATCH=2`; `UNCLEAR=1` | 4 | 0 | 2 |
| 5 | DIAG - Data BI SQL Profile | 10 | 10 | `STRONG_MATCH=2`; `GOOD_MATCH_WITH_GAPS=3`; `TRANSFERABLE_OPPORTUNITY=1`; `ASPIRATIONAL_MATCH=2`; `LOW_FIT=1`; `UNCLEAR=1` | 2 | 0 | 1 |
| 6 | DIAG - Backend Trainee Projects | 10 | 10 | `STRONG_MATCH=1`; `GOOD_MATCH_WITH_GAPS=5`; `ASPIRATIONAL_MATCH=1`; `LOW_FIT=1`; `UNCLEAR=2` | 4 | 1 | 1 |

## Hallazgos principales

### 1. Equivalencias de skills aparecen como tema central

Las notas repiten que el sistema trata algunas skills como faltantes cuando humanamente parecen equivalentes o transferibles:

- PostgreSQL deberia cubrir SQL core en muchos casos.
- AWS podria reducir la severidad de una brecha Azure.
- ITIL/ITSM podrian estar mas cerca en soporte/app support.
- PowerShell podria inferirse como probable en ciertos perfiles senior o stacks de infraestructura.
- PL/SQL y SQL/scripting requieren una lectura mas fina segun contexto.

Categoria propuesta: `SKILL_EQUIVALENCE`.

### 2. El contexto de la skill importa

Spring Boot aparece como ambiguo: puede representar backend API, web MVC o microservicios. En ofertas fullstack, Angular/Node.js/Frontend generan dudas sobre si el perfil backend cubre suficiente del rol.

Categorias propuestas:

- `SKILL_CONTEXT_AMBIGUITY`
- `BACKEND_API_MATCH_WITH_FRONTEND_GAP`

### 3. Hay muchos casos donde el perfil puede cumplir, pero el rol no es su objetivo

Las notas distinguen capacidad tecnica de interes profesional. Varios resultados se describen como "podria cumplir", pero no necesariamente como oportunidad prioritaria por area objetivo.

Esto aparece especialmente en:

- perfiles backend frente a soporte/app support/security;
- perfil data frente a soporte/app support;
- perfiles senior frente a roles mas operativos.

Categoria propuesta: `ROLE_OBJECTIVE_MISMATCH`.

### 4. Falta representar sobrecalificacion

En varios casos la nota humana dice que el perfil "cumple de sobra" o excede el rol. Eso no es lo mismo que baja compatibilidad.

Categoria propuesta: `OVERQUALIFIED_MATCH`.

Este caso puede requerir una categoria metodologica aparte: una oferta puede ser compatible tecnicamente pero no recomendable como prioridad profesional.

### 5. Existen oportunidades adyacentes de dominio

Las notas marcan reconversion o readaptacion posible hacia seguridad, soporte, app support o data. El sistema a veces baja esos casos como `LOW_FIT`, mientras el humano los ve como oportunidad transferible.

Categoria propuesta: `DOMAIN_ADJACENT_OPPORTUNITY`.

### 6. El sistema parece conservador en algunos casos

Varias notas indican que el sistema "tira abajo" oportunidades razonables o no refleja suficientemente el potencial del perfil.

Ejemplos:

- `P2-J14-R1`: humano `STRONG_MATCH`, sistema `GOOD_MATCH_WITH_MINOR_GAPS`.
- `P2-J13-R2`: humano `STRONG_MATCH`, sistema `TRANSFERABLE_OPPORTUNITY`.
- `P4-J20-R5`: humano `TRANSFERABLE_OPPORTUNITY`, sistema `LOW_FIT`.
- `P5-J13-R1`: humano `GOOD_MATCH_WITH_GAPS`, sistema `ASPIRATIONAL_MATCH`.

Categoria propuesta: `SYSTEM_TOO_CONSERVATIVE`.

### 7. La suficiencia de evidencia depende del seniority

Las notas sobre perfiles trainee/junior y roles senior muestran que la misma evidencia puede ser suficiente para trainee/junior pero insuficiente para roles senior o especializados.

Categorias propuestas:

- `PROJECT_EVIDENCE_LIMIT`
- `EVIDENCE_SUFFICIENCY`

### 8. Hay problemas de encoding/texto que necesitan tratamiento separado

Se detectaron titulos o notas con mojibake. Estos casos no necesariamente son baja compatibilidad; pueden ser datos dudosos.

Categoria propuesta: `ENCODING_OR_TEXT_QUALITY`.

## Inconsistencias a revisar

### Labels normalizados

La guia define:

```text
GOOD_MATCH_WITH_GAPS
```

El dataset usaba:

```text
GOOD_MATCH_WITH_MINOR_GAPS
```

en 17 filas. Esas filas fueron normalizadas a `GOOD_MATCH_WITH_GAPS`. No quedan labels fuera de guia.

### Filas inicialmente sin label

| evaluation_id | profileId | rank | Oferta |
| --- | ---: | ---: | --- |
| `P3-J21-R10` | 3 | 10 | AI Chatbot Tester |
| `P4-J5-R10` | 4 | 10 | Junior Support & Training - Customer Success |
| `P5-J5-R8` | 5 | 8 | Junior Support & Training - Customer Success |
| `P6-J8-R5` | 6 | 5 | Technical Support Jr. |
| `P6-J5-R6` | 6 | 6 | Junior Support & Training - Customer Success |

Estas filas ya no estan vacias: quedaron como `UNCLEAR` porque no tenian informacion suficiente para una etiqueta positiva/negativa limpia.

### `is_false_positive=true` con label positivo

No quedan filas con `is_false_positive=true` y label positivo. En una pasada posterior, esos flags fueron limpiados porque eran desacuerdos metodologicos, no falsos positivos reales.

Recomendacion: reservar `is_false_positive=true` para resultados que el humano considere `LOW_FIT` o, excepcionalmente, `UNCLEAR` por mala evidencia.

### `is_false_negative_candidate=true` usado para categoria demasiado conservadora

Quedan casos con buen ranking marcados como false negative candidate:

- `P4-J8-R1`
- `P4-J7-R2`
- `P5-J13-R1`
- `P6-J4-R4`

Esto no es un falso negativo de recuperacion, porque las ofertas si fueron recuperadas arriba. Parece mas bien `SYSTEM_TOO_CONSERVATIVE` o desacuerdo con `system_category`.

Recomendacion: separar en segunda pasada:

- `is_false_negative_candidate=true`: oferta relevante que no aparece o aparece demasiado abajo.
- `system_too_conservative`: oferta recuperada, pero evaluada demasiado bajo por la capa diagnostica.

### `needs_review=true` refinado

El dataset tenia 34 filas con `needs_review=true` antes de la depuracion de flags. Luego de aplicar el criterio de metricas limpias, quedaron 13:

- `UNCLEAR` por informacion insuficiente o texto dudoso;
- dudas explicitas de label;
- seniority/rol a revisar;
- inconsistencias entre flags y lectura humana;
- encoding/text quality cuando afecta la interpretacion.

Se quitaron flags de review cuando la nota era metodologica pero la fila podia entrar limpia a metricas: equivalencia SQL/PostgreSQL, Spring Boot ambiguo, sobrecalificacion, dominio adyacente, sistema conservador o evidencia por proyectos.

### Encoding roto luego de depurar `needs_review`

Filas con mojibake o caracteres rotos detectados durante la revision:

| evaluation_id | Oferta | Label | `needs_review` |
| --- | --- | --- | --- |
| `P2-J6-R10` | Analista Soporte Tecnico (Turno Noche) con mojibake | `LOW_FIT` | no |
| `P4-J2-R8` | ANALISTA FUNCIONAL TIC - Bioter S.A. con mojibake | `ASPIRATIONAL_MATCH` | no |
| `P4-J5-R10` | Junior Support & Training - Customer Success con mojibake | `UNCLEAR` | si |
| `P5-J5-R8` | Junior Support & Training - Customer Success con mojibake | `UNCLEAR` | si |
| `P6-J5-R6` | Junior Support & Training - Customer Success con mojibake | `UNCLEAR` | si |

Recomendacion: mantener `needs_review=true` solo cuando el encoding afecta la interpretacion de la fila; si el label sigue siendo claro, documentarlo como calidad de texto sin excluirlo de metricas limpias.

## Nuevas categorias de analisis propuestas

Estas categorias no son necesariamente labels finales. Sirven para codificar notas y guiar calibracion futura:

| Categoria | Uso |
| --- | --- |
| `SKILL_EQUIVALENCE` | Equivalencias o proximidad entre skills: PostgreSQL/SQL, AWS/Azure, ITIL/ITSM. |
| `SKILL_CONTEXT_AMBIGUITY` | Skills cuyo significado depende del contexto: Spring Boot web/API/microservicios. |
| `BACKEND_API_MATCH_WITH_FRONTEND_GAP` | Backend/API compatible, pero faltan Angular/Node/frontend. |
| `ROLE_OBJECTIVE_MISMATCH` | El perfil podria cumplir, pero el rol no coincide con su objetivo principal. |
| `OVERQUALIFIED_MATCH` | El perfil excede el seniority o alcance del rol. |
| `DOMAIN_ADJACENT_OPPORTUNITY` | Dominio cercano o reconversion plausible: soporte, seguridad, app support, data. |
| `SYSTEM_TOO_CONSERVATIVE` | El sistema baja demasiado una oportunidad que el humano ve razonable. |
| `PROJECT_EVIDENCE_LIMIT` | Proyectos alcanzan para trainee/junior, pero no necesariamente para senior. |
| `EVIDENCE_SUFFICIENCY` | La evidencia es suficiente o insuficiente segun seniority/rol. |
| `ENCODING_OR_TEXT_QUALITY` | Texto roto, incompleto o insuficiente para etiquetar. |

## Recomendaciones para segunda pasada

### Mantener labels normalizados

Usar solo:

- `STRONG_MATCH`
- `GOOD_MATCH_WITH_GAPS`
- `TRANSFERABLE_OPPORTUNITY`
- `ASPIRATIONAL_MATCH`
- `LOW_FIT`
- `UNCLEAR`

`GOOD_MATCH_WITH_MINOR_GAPS` ya fue convertido a `GOOD_MATCH_WITH_GAPS`.

### Cuando usar `STRONG_MATCH`

Usarlo cuando:

- el rol esta claramente alineado;
- el seniority es compatible;
- las skills nucleares estan cubiertas;
- los gaps son inexistentes o realmente secundarios;
- la evidencia alcanza para defender postulacion.

Evitarlo si la nota dice "puede cumplir, pero no es su objetivo".

### Cuando usar `GOOD_MATCH_WITH_GAPS`

Usarlo cuando:

- el rol es razonable;
- faltan herramientas entrenables;
- hay equivalencias defendibles;
- el perfil podria postularse con preparacion menor.

### Cuando usar `TRANSFERABLE_OPPORTUNITY`

Usarlo cuando:

- el rol no es directo;
- hay transferencia profesional defendible;
- el caso representa reconversion o dominio adyacente;
- no corresponde tratarlo como match fuerte.

### Cuando usar `ASPIRATIONAL_MATCH`

Usarlo cuando:

- la oportunidad sirve como roadmap;
- falta experiencia o seniority relevante;
- hay varias herramientas o dominios por aprender;
- la postulacion inmediata seria discutible.

### Cuando usar `LOW_FIT`

Usarlo cuando:

- el rol esta lejos del objetivo;
- faltan skills nucleares;
- hay poca evidencia;
- no conviene priorizar la oferta.

### Cuando usar `UNCLEAR`

Usarlo cuando:

- la informacion no alcanza;
- el texto esta roto;
- el seniority o rol no se puede interpretar;
- la fila no deberia afectar metricas sin revision.

### Cuando usar `is_false_positive`

Usar `true` solo cuando:

- el resultado aparece alto;
- el humano lo considera `LOW_FIT` o inutil para priorizar;
- no se trata solo de una categoria diagnostica demasiado conservadora.

### Cuando usar `needs_review`

Usar `true` solo cuando la fila necesita revision antes de entrar a metricas limpias:

- encoding roto;
- label dudoso;
- notas contradictorias;
- evidencia insuficiente;
- inconsistencia de profile/job;
- `UNCLEAR`.

No usarlo como marcador general de "caso interesante"; para eso conviene agregar una columna futura de codigos metodologicos.

## Metricas preliminares

Se ejecuto:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\evaluation\compute-vector-first-metrics.ps1
```

El script reporta `Non-guide labels: 0` despues de la normalizacion.

| Perfil | Precision@5 | Precision@10 | Nota |
| --- | ---: | ---: | --- |
| 2 - Backend Senior Java Cloud | 0.600 | 0.700 | 1 `needs_review`. |
| 3 - Backend Strong Partial DevOps Transfer | 1.000 | 0.800 | 1 `UNCLEAR`, 2 `needs_review`. |
| 4 - IT Support Analyst Junior | 1.000 | 0.700 | 1 `UNCLEAR`, 4 `needs_review`. |
| 5 - Data BI SQL Profile | 0.800 | 0.600 | 1 `UNCLEAR`, 2 `needs_review`. |
| 6 - Backend Trainee Projects | 0.600 | 0.600 | 2 `UNCLEAR`, 4 `needs_review`. |

Estas metricas son preliminares. No deben usarse como conclusion final hasta:

- revisar flags inconsistentes;
- seguir separando `needs_review` de codigos metodologicos.

## Decision recomendada

No pasar todavia a refinamiento algoritmico.

Orden recomendado:

1. Hacer una segunda pasada de labels.
2. Revisar los 4 false negative candidates y separar recuperacion pobre de sistema demasiado conservador.
3. Agregar una columna futura `analysis_codes` o documentar codigos fuera del CSV para capturar patrones metodologicos.
4. Recalcular metricas despues de resolver `UNCLEAR` si se busca una lectura final.
5. Recién despues decidir si corresponde calibrar roles, seniority, buckets o equivalencias de skills.

## Restricciones respetadas

No se toco:

- ranking;
- embeddings;
- entidades;
- migraciones;
- servicios productivos;
- endpoints;
- UI funcional;
- `CvMatchingService`;
- `VectorFirstCompatibilityService`;
- `ProfileVectorCompatibilityController`;
- templates funcionales.

No se activo:

- reranking real;
- score hibrido;
- ranking experimental.

No se cambio:

- `analysisRank`;
- `vectorRank`;
- `suggestedRerankRank`;
- `suggestedRankDelta`.

## Commit message sugerido

```text
docs: audit vector-first labeling results
```
