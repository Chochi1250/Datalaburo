# Roadmap tecnico y scope gate

## Objetivo

Definir un roadmap tecnico priorizado para las proximas fases de Datalaburo, separando:

- funcionalidades necesarias para un MVP defendible de tesis;
- funcionalidades utiles para demo/producto;
- funcionalidades futuras que conviene postergar.

Esta fase es solo de analisis, documentacion y recomendacion. No cambia codigo funcional, ranking, embeddings, migraciones, UI, scraping, extension ni endpoints productivos.

## Estado actual resumido

El estado actual del repositorio ya permite sostener una arquitectura vector-first real:

- PostgreSQL + pgvector es la base objetivo.
- H2 queda como legacy/historico/test, no como fallback productivo.
- El modelo real es `BAAI/bge-m3`.
- `document_embeddings.embedding` usa `vector(1024)`.
- El servicio local `embedding-service/` genera embeddings reales con Python/FastAPI.
- Spring Boot prepara y procesa embeddings reales.
- `JOBS` / `jobs` es la fuente de verdad de ofertas.
- `JOB_OFFERS` / `job_offers` queda legacy/no productivo.
- El endpoint interno vector-first existe:

```http
GET /internal/analysis/profiles/{profileId}/vector-first-compatibility?limit=20
```

- La estrategia vigente es:

```text
VECTOR_FIRST_WITH_RERANKING_DIAGNOSTIC
```

Interpretacion obligatoria:

- `vectorRank` conserva el ranking semantico original de pgvector.
- `analysisRank == vectorRank`.
- `suggestedRerankRank` y `suggestedRankDelta` son diagnosticos.
- No hay reranking real activo.
- No hay score hibrido productivo.

Tambien existe evidencia documental importante:

- `docs/evaluation/evaluation-evidence-pack.md`
- `docs/evaluation-vector-first-diagnostic.md`
- `docs/data-source-audit.md`
- `docs/ui-vector-first-integration-audit.md`
- `docs/ui-vector-first-smoke-test.md`
- `docs/vector-first-compatibility-strategy.md`
- `docs/embeddings-pipeline.md`

La UI minima vector-first ya existe desde perfiles mediante:

```http
GET /profiles/{profileId}/vector-first-compatibility?limit=20
```

El matching historico queda preservado en `/matching` y `/jobs/{id}/match`.

## Features candidatas

### 1. Perfiles mas realistas, similares a LinkedIn

Valor para tesis: alto. Mejora la representacion del candidato, permite diferenciar experiencia laboral, educacion, proyectos, skills y resumen profesional.

Valor demo/producto: alto. La experiencia se vuelve mas creible que un textarea de CV plano.

Complejidad tecnica: media/alta si se modelan secciones estructuradas; baja/media si primero se agregan campos simples.

Riesgo de romper lo actual: medio. `CandidateProfile` hoy tiene `name` y `cv_text`; cambiar el modelo sin compatibilidad podria afectar matching viejo, embeddings y UI.

Dependencias previas: dataset/evaluacion para saber que senales realmente importan; decision de modelo de perfil.

Migraciones: si. Campos nuevos o tablas nuevas para experiencia, educacion, skills y proyectos.

Nuevos endpoints: probablemente si, para crear/editar secciones de perfil.

Cambios de UI: si, formularios y detalle de perfil.

Cambios en embeddings: si. `EmbeddingTextBuilder` deberia construir texto fuente por secciones, manteniendo trazabilidad.

Recomendacion: implementar despues de cerrar dataset/evaluacion. Conviene hacer una version minima, no un clon completo de LinkedIn.

### 2. Subida/carga de proyectos al perfil

Valor para tesis: alto. La arquitectura ya distingue `EvidenceLevel.PROJECT`; proyectos manuales harian esa evidencia mas defendible que detectarla solo por texto libre.

Valor demo/producto: alto. Es especialmente util para perfiles trainee/junior.

Complejidad tecnica: media.

Riesgo de romper lo actual: medio si se cambia el embedding de perfil; bajo si se agrega como seccion compatible y se preserva `cv_text`.

Dependencias previas: modelo de perfil mas realista; reglas claras para incorporar proyectos al texto vectorizable.

Migraciones: si, idealmente una tabla `profile_projects` o una seccion estructurada equivalente.

Nuevos endpoints: si, CRUD minimo de proyectos.

Cambios de UI: si.

Cambios en embeddings: si. Al agregar o editar proyectos, el embedding del perfil debe pasar a `PENDING` o requerir reprocesamiento.

Recomendacion: corto/mediano plazo, despues del modelo de perfil minimo. Es mas valioso para tesis que GitHub automatico.

### 3. Vinculacion con GitHub

Valor para tesis: medio. Aporta evidencia externa, pero no es necesaria para demostrar el enfoque vector-first.

Valor demo/producto: alto si funciona bien.

Complejidad tecnica: alta. Requiere OAuth o tokens, llamadas a API, rate limits, privacidad, seleccion de repositorios y parsing de tecnologias.

Riesgo de romper lo actual: alto si se mezcla con el pipeline de perfil y embeddings antes de estabilizar perfiles/proyectos.

Dependencias previas: perfiles/proyectos manuales, politica de privacidad, modelo de evidencia, dataset para validar si mejora resultados.

Migraciones: si, para cuentas, repos, snapshots o metadatos.

Nuevos endpoints: si, autenticacion/callbacks/sync.

Cambios de UI: si.

Cambios en embeddings: probablemente si, si repos/proyectos pasan al texto del perfil.

Recomendacion: futuro/trabajo posterior. Primero resolver proyectos manuales; despues GitHub puede ser una fuente opcional de evidencia.

### 4. Procesamiento minimo de documentos o PDFs

Valor para tesis: medio/alto. Mejora entrada de datos y realismo, aunque no cambia por si mismo la hipotesis de compatibilidad.

Valor demo/producto: alto. Reduce friccion: subir CV/PDF es mas natural que pegar texto.

Complejidad tecnica: media. El `pom.xml` no muestra dependencias de parsing PDF/documentos; habria que elegir una libreria, validar encoding y manejar errores.

Riesgo de romper lo actual: medio si se reemplaza `cv_text`; bajo si el PDF solo extrae texto hacia el flujo actual.

Dependencias previas: definir si el perfil sigue teniendo `cv_text` como texto canonico o si se guardan archivos/metadatos.

Migraciones: no necesariamente para una prueba minima que extrae texto y lo guarda en `cv_text`; si para almacenar archivos, nombre original, hash o auditoria.

Nuevos endpoints: si, upload multipart.

Cambios de UI: si.

Cambios en embeddings: si indirectamente. Cambia el texto fuente del perfil y exige preparar/procesar embedding.

Recomendacion: mediano plazo. Puede entrar antes que GitHub, pero no antes de dataset/evaluacion.

### 5. Pulido visual tipo LinkedIn

Valor para tesis: bajo/medio. Ayuda a presentar, pero no valida el metodo.

Valor demo/producto: alto.

Complejidad tecnica: media.

Riesgo de romper lo actual: medio si se reestructura navegacion y vistas existentes.

Dependencias previas: claridad de resultados y modelo de perfil. Sin eso, el pulido visual puede maquillar incertidumbre metodologica.

Migraciones: no.

Nuevos endpoints: no necesariamente.

Cambios de UI: si.

Cambios en embeddings: no.

Recomendacion: postergar el pulido avanzado. Hacer solo ajustes puntuales de claridad cuando ayuden a explicar resultados.

### 6. Claridad visual de resultados de compatibilidad

Valor para tesis: alto. La tesis necesita mostrar por que un resultado es compatible, transferible, aspiracional o debil.

Valor demo/producto: alto.

Complejidad tecnica: baja/media. La UI vector-first ya existe; el trabajo seria ordenar, rotular y explicar mejor.

Riesgo de romper lo actual: bajo si se mantiene separada de `/matching` y `/jobs/{id}/match`.

Dependencias previas: dataset/evaluacion para saber que campos conviene destacar.

Migraciones: no.

Nuevos endpoints: no necesariamente.

Cambios de UI: si.

Cambios en embeddings: no.

Recomendacion: corto plazo, despues de cerrar la primera tabla de evaluacion. Es mas importante que una estetica avanzada.

### 7. Pulido del algoritmo

Valor para tesis: alto, pero solo si esta guiado por evidencia.

Valor demo/producto: medio/alto.

Complejidad tecnica: media/alta.

Riesgo de romper lo actual: alto si se toca ranking, buckets o heuristicas sin dataset.

Dependencias previas: dataset etiquetado, falsos positivos/falsos negativos documentados, criterio de aceptacion.

Migraciones: no necesariamente.

Nuevos endpoints: no necesariamente.

Cambios de UI: no necesariamente.

Cambios en embeddings: no, salvo que se cambie texto fuente o modelo.

Recomendacion: no ahora. Primero medir. Despues calibrar reglas puntuales y documentar impacto.

### 8. Dataset y metricas de evaluacion

Valor para tesis: muy alto. Es el principal faltante metodologico para defender calidad y decisiones futuras.

Valor demo/producto: medio. No luce tanto como UI, pero evita mostrar resultados no validados.

Complejidad tecnica: media. Puede empezar como documentos/tablas; luego puede volverse automatizable.

Riesgo de romper lo actual: bajo si se mantiene documental y/o como fixtures no productivos.

Dependencias previas: `JOBS` confirmado como fuente de verdad, perfiles DIAG, endpoint vector-first estable.

Migraciones: no.

Nuevos endpoints: no.

Cambios de UI: no.

Cambios en embeddings: no, salvo regenerar perfiles/ofertas para evidencia.

Recomendacion: implementar ahora. Debe ser la proxima fase.

### 9. `rankingMode` experimental detras de flag/parametro

Valor para tesis: medio/alto si se usa para comparar variantes.

Valor demo/producto: medio.

Complejidad tecnica: media.

Riesgo de romper lo actual: medio/alto si se confunde con ranking productivo.

Dependencias previas: dataset etiquetado, metricas base, contrato claro de respuesta.

Migraciones: no.

Nuevos endpoints: tal vez no; podria ser parametro. Es preferible endpoint/parametro claramente experimental.

Cambios de UI: solo si se expone en una vista tecnica.

Cambios en embeddings: no.

Recomendacion: despues de dataset/evaluacion. No activar antes de tener baseline.

### 10. Mejoras adicionales detectadas

Mejoras prioritarias desde el estado actual:

- Consolidar dataset de evaluacion versionado o semi-versionado: etiquetas manuales, resultados esperados y resumen de metricas.
- Regenerar/auditar evidencia dudosa del perfil `Backend Strong Partial DevOps Transfer`, porque docs previos marcan inconsistencias de `profileId`.
- Definir una taxonomia de labels humanos: `postulable_ahora`, `postulable_con_gaps`, `transferible`, `aspiracional`, `bajo_fit`.
- Crear una decision explicita de "no activar reranking real" hasta superar un gate de evaluacion.
- Revisar calidad de textos/encoding de ofertas antes de ampliar dataset.
- Desalentar `/api/job-offers` en documentacion visible o protegerlo en una fase posterior para evitar ingesta legacy accidental.
- Documentar como se refrescan embeddings cuando cambia un perfil.

## Priorizacion

| Feature | Prioridad | Valor tesis | Valor demo/producto | Complejidad | Riesgo | Dependencias | Recomendacion |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Dataset y metricas de evaluacion | P0 | Muy alto | Medio | Media | Bajo | Endpoint vector-first, perfiles DIAG, `JOBS` estable | Hacer ahora como Fase 4.1 |
| Claridad visual de resultados | P1 | Alto | Alto | Baja/media | Bajo | Dataset inicial y campos a destacar | Corto plazo, despues de evaluar |
| Perfiles mas realistas | P1 | Alto | Alto | Media/alta | Medio | Dataset, diseno de secciones | Corto/mediano plazo, version minima |
| Proyectos manuales | P1 | Alto | Alto | Media | Medio | Modelo de perfil realista | Mediano plazo; priorizar sobre GitHub |
| Procesamiento minimo PDF/documentos | P2 | Medio/alto | Alto | Media | Medio | Decision sobre texto canonico del perfil | Mediano plazo |
| Pulido del algoritmo | P2 | Alto | Medio/alto | Media/alta | Alto | Dataset etiquetado y metricas | Solo despues de medir |
| `rankingMode` experimental | P2 | Medio/alto | Medio | Media | Medio/alto | Dataset, baseline, contrato experimental | Despues de evaluacion |
| Pulido visual tipo LinkedIn | P3 | Bajo/medio | Alto | Media | Medio | Resultados claros y perfil mas realista | Postergar salvo ajustes puntuales |
| Vinculacion con GitHub | P4 | Medio | Alto | Alta | Alto | Proyectos manuales, privacidad, evidencia | Futuro/trabajo posterior |
| Score hibrido productivo | P4 | Bajo sin dataset | Medio | Media | Alto | Dataset robusto y justificacion | No implementar todavia |
| Learning-to-rank | P4 | Alto futuro | Alto futuro | Alta | Alto | Muchas etiquetas humanas | Futuro, fuera del MVP |

## MVP defendible para tesis

Un MVP defendible no necesita parecer una red social ni integrar GitHub. Deberia ser suficiente si demuestra, de forma reproducible:

- base objetivo PostgreSQL + pgvector;
- embeddings reales `BAAI/bge-m3` de dimension 1024;
- ofertas desde `JOBS` como fuente de verdad;
- perfiles guardados con embeddings `PROFILE`;
- recuperacion vectorial Top N por perfil;
- explicacion de compatibilidad con rol, seniority, skills, gaps, evidencia y transferibilidad;
- UI minima separada que muestre resultados vector-first sin confundirlos con matching viejo;
- evidence pack con perfiles representativos;
- dataset manual etiquetado sobre Top N;
- metricas simples como precision@k y, si alcanza el tiempo, nDCG;
- analisis de falsos positivos, falsos negativos y casos discutibles;
- decision documentada de no activar reranking real ni score hibrido sin evidencia suficiente.

Para tesis, el eje defendible es:

```text
retrieval semantico real + explicacion profesional + evaluacion manual reproducible
```

No es necesario para el MVP:

- login real;
- clon visual de LinkedIn;
- integracion GitHub;
- parsing avanzado de PDFs;
- ranking aprendido;
- score unico final.

## Roadmap recomendado

### Corto plazo

#### Fase 4.1 - Dataset y evaluacion

Objetivo:

- convertir la evidencia actual en un dataset evaluable;
- cerrar una tabla manual de labels por perfil/oferta;
- medir baseline vector-first;
- dejar un gate claro antes de ranking experimental.

Entregables recomendados:

- tabla de perfiles evaluados;
- tabla de ofertas Top 10/20 por perfil;
- label humano por resultado;
- columna de falsos positivos/falsos negativos;
- resumen de metricas por perfil;
- decision explicita sobre si `suggestedRerankRank` parece razonable;
- lista de ajustes candidatos, sin implementarlos todavia.

#### Fase 4.2 - Perfil realista minimo

Objetivo:

- pasar de `cv_text` plano a un perfil mas expresivo, conservando compatibilidad.

Scope recomendado:

- headline/resumen;
- experiencia;
- educacion;
- skills declaradas;
- links opcionales;
- `cv_text` como fallback o texto canonico legacy.

No conviene intentar un clon completo de LinkedIn.

#### Fase 4.3 - Claridad de resultados vector-first

Objetivo:

- mejorar la vista actual para defender resultados en demo/tesis.

Scope recomendado:

- distinguir ranking activo vs diagnostico;
- destacar evidencia, gaps criticos y transferibilidad;
- mejorar estados de error de embeddings;
- evitar porcentajes absolutos o score hibrido.

### Mediano plazo

#### Fase 4.4 - Proyectos manuales

Agregar proyectos como evidencia estructurada del perfil.

Debe incluir:

- titulo;
- descripcion;
- tecnologias;
- rol del candidato;
- link opcional;
- inclusion controlada en el texto de embedding.

#### Fase 4.5 - Procesamiento basico de CV/PDF

Agregar carga de archivo solo si mantiene el flujo actual:

```text
archivo -> texto extraido -> cv_text / secciones -> embedding PENDING
```

La prioridad es extraccion robusta y mensajes claros, no almacenamiento documental complejo.

#### Fase 4.6 - Calibracion algoritmica guiada por evidencia

Usar el dataset de Fase 4.1 para decidir ajustes puntuales:

- roles mal detectados;
- seniority dudoso;
- buckets demasiado optimistas o pesimistas;
- gaps criticos mal clasificados;
- transferencias exageradas.

Cada cambio deberia tener antes/despues documentado.

#### Fase 4.7 - `rankingMode` experimental

Agregar un modo experimental solo despues de tener baseline:

```text
rankingMode=vector
rankingMode=diagnostic-rerank
```

Debe conservar `vectorRank` como auditoria y no reemplazar el ranking productivo por defecto.

### Futuro/trabajo posterior

- Vinculacion con GitHub.
- Pulido visual avanzado tipo LinkedIn.
- Feedback humano en la UI para construir dataset.
- Score hibrido, si hay evidencia suficiente para justificar pesos.
- Learning-to-rank.
- Personalizacion por objetivo profesional.
- Proteccion/desactivacion formal de endpoints legacy como `/api/job-offers`.
- Pipeline avanzado de documentos, multiples archivos y versiones.

## Proxima fase recomendada

La siguiente fase concreta deberia ser:

```text
Fase 4.1 - Dataset y evaluacion
```

Justificacion:

- Es el mayor faltante para defender la tesis.
- Tiene bajo riesgo de romper lo actual.
- No requiere migraciones ni endpoints nuevos.
- Evita activar ranking experimental por intuicion.
- Da criterio para decidir que pulir del algoritmo.
- Permite separar mejoras metodologicas de mejoras cosmeticas.
- Convierte la UI minima vector-first en evidencia defendible, no solo en demo.

Scope sugerido para Fase 4.1:

1. Seleccionar perfiles DIAG y, si hace falta, crear 1 o 2 perfiles mas realistas.
2. Regenerar/auditar evidencia dudosa, especialmente `Backend Strong Partial DevOps Transfer`.
3. Congelar una muestra de ofertas desde `JOBS`.
4. Ejecutar Top 10 o Top 20 por perfil.
5. Etiquetar manualmente cada resultado.
6. Documentar falsos positivos, falsos negativos y buckets discutibles.
7. Calcular metricas simples.
8. Emitir decision: mantener diagnostico, calibrar reglas, o preparar ranking experimental.

## Que no conviene hacer todavia

- No activar reranking experimental sin dataset.
- No introducir score hibrido sin metricas.
- No hacer `rankingMode` visible como producto antes de validarlo.
- No integrar GitHub antes de estabilizar perfiles y proyectos manuales.
- No pulir UI avanzada antes de validar resultados.
- No convertir `compatibilityBucket` en categoria final de producto.
- No presentar `vectorSimilarity` como porcentaje absoluto de compatibilidad.
- No reemplazar `/matching` ni `/jobs/{id}/match` todavia.
- No conectar nada nuevo a `JOB_OFFERS`.
- No reintroducir H2 como arquitectura objetivo.
- No cambiar dimensiones, modelo ni normalizacion de embeddings sin una migracion/evaluacion especifica.
- No borrar tablas legacy en esta fase.

## Riesgos principales

- Dataset chico: permite evidencia cualitativa, pero no conclusiones estadisticas fuertes.
- Etiquetas humanas subjetivas: hay que definir una taxonomia estable y documentar criterios.
- Encoding/textos capturados: ofertas con texto roto pueden contaminar evaluacion.
- Perfil DIAG inconsistente: evidencia previa marca dudas sobre `Backend Strong Partial DevOps Transfer`.
- Confusion de ranking: `suggestedRerankRank` puede interpretarse como ranking real si no se rotula bien.
- Sobreajuste: ajustar reglas para 5 perfiles puede empeorar casos nuevos.
- Complejidad de perfil: un modelo demasiado ambicioso puede desviar la tesis hacia producto.
- GitHub/PDF/UI avanzada pueden consumir tiempo sin mejorar la defensa metodologica.

## Confirmacion de alcance

Esta fase no implementa codigo funcional.

No se modifico:

- ranking;
- embeddings;
- entidades;
- migraciones;
- endpoints productivos;
- scraping;
- extension;
- captura;
- `CvMatchingService`;
- `VectorFirstCompatibilityService`;
- UI funcional.

El entregable esperado de esta fase es solo este documento.

## Commit message sugerido

```text
docs: define roadmap scope gate
```
