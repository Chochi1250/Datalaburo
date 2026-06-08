# Roadmap post-paper para demo de exposicion

## Objetivo

Este documento actualiza el roadmap tecnico de Datalaburo despues de la entrega del paper/tesina.

La demo base ya esta aprobada como prueba de concepto. El objetivo ahora es mejorar la aplicacion para la exposicion, mostrando un producto mas completo sin romper el nucleo vector-first existente.

Este documento no reemplaza los documentos historicos de evaluacion y scope gate del paper. Los deja como contexto de fases anteriores y define el roadmap vigente post-entrega.

## Estado que se debe preservar

- PostgreSQL + pgvector es la base objetivo.
- H2 es legacy/obsoleto; no debe orientar nuevas decisiones.
- `JOBS` / `jobs` es la fuente de verdad de ofertas.
- `JOB_OFFERS` / `job_offers` es legacy/no productivo.
- `document_embeddings` usa `owner_type = JOB` o `PROFILE`.
- Modelo real: `BAAI/bge-m3`.
- Dimension esperada: `1024`.
- Servicio local de embeddings: Python/FastAPI en `embedding-service/`.
- Estrategia vigente: `VECTOR_FIRST_WITH_RERANKING_DIAGNOSTIC`.
- `vectorRank` se conserva como auditoria.
- `analysisRank == vectorRank`.
- `suggestedRerankRank` y `suggestedRankDelta` son diagnosticos.
- No hay reranking real activo.
- No hay score hibrido productivo.
- Matching viejo sigue como legacy operativo en `/matching` y `/jobs/{id}/match`.
- UI vector-first ya existe desde perfiles.
- Proyectos manuales como evidencia estructurada ya existen.
- Los proyectos siguen fuera de embeddings productivos por ahora.

## Proxima fase recomendada

```text
Fase 8.0 - CV textual editable y refresco de embedding de perfil
```

Esta fase es la mas conveniente para la exposicion porque mejora el flujo visible del perfil sin cambiar ranking, algoritmo ni arquitectura vectorial.

### Objetivo

Permitir que el usuario edite el texto fuente del CV/perfil y que el sistema refleje de forma clara el estado del embedding `PROFILE` asociado.

### Alcance

- Permitir editar/actualizar `cvText`.
- Permitir pegar texto de CV.
- Guardar el texto asociado al perfil.
- Cuando cambie el texto fuente del perfil, preparar o marcar el embedding como `PENDING` segun corresponda.
- Mostrar estado del embedding del perfil.
- Mantener el analisis y ranking actuales.
- Mantener proyectos manuales fuera de embeddings, salvo decision explicita posterior.

### Que no se debe tocar en Fase 8.0

- No cambiar `analysisRank`.
- No cambiar `vectorRank`.
- No activar reranking real.
- No activar score hibrido.
- No modificar `VectorFirstCompatibilityService`.
- No modificar `RerankingDiagnosticService`.
- No modificar `CvMatchingService`.
- No modificar scraping, extension ni captura.
- No tocar embeddings de `JOB`.
- No incluir proyectos manuales en embeddings.
- No regenerar embeddings masivamente.
- No tocar `JOB_OFFERS`.
- No tratar H2 como arquitectura objetivo.
- No agregar upload PDF/DOCX como prioridad inmediata.

### Criterios de aceptacion

- Desde `/profiles/{id}` se puede editar y guardar el texto del CV.
- Si el texto no cambia, no se fuerza reprocesamiento innecesario.
- Si el texto cambia, el embedding `PROFILE` queda preparado o marcado como pendiente para `BAAI/bge-m3`.
- La UI muestra estado del embedding del perfil: por ejemplo `READY`, `PENDING`, `FAILED` o "sin embedding".
- La compatibilidad vectorial sigue usando el embedding `PROFILE` disponible y no cambia el orden por esta fase.
- La UI aclara cuando el perfil necesita reprocesar embeddings antes de confiar en una nueva corrida.
- Proyectos manuales siguen visibles como evidencia estructurada, pero no entran al texto vectorizable.
- `/matching` y `/jobs/{id}/match` siguen funcionando como legacy operativo.
- No hay cambios de migraciones salvo necesidad minima justificada.
- No se presenta ningun score hibrido ni reranking real.

## Roadmap post-entrega

### Fase 8.0 - CV textual editable y refresco de embedding de perfil

Prioridad inmediata.

Motivo:

- reduce friccion de demo;
- permite corregir o enriquecer perfiles sin recrearlos;
- conecta naturalmente con el pipeline existente de `document_embeddings`;
- mantiene la arquitectura vector-first estable.

El texto editable del CV debe seguir siendo la fuente canonica productiva para el embedding `PROFILE` en esta etapa.

### Fase 8.1 - Perfil enriquecido minimo

Objetivo:

- mejorar la representacion visible del perfil sin hacer un clon completo de LinkedIn.

Alcance sugerido:

- headline/resumen;
- rol objetivo;
- seniority objetivo;
- modo de busqueda;
- skills declaradas;
- links opcionales;
- proyectos visibles como evidencia.

Decisiones a tomar:

- que campos entran al texto vectorizable y cuales quedan solo como contexto visible;
- como evitar que preferencias de busqueda contaminen el embedding de evidencia/capacidad;
- si skills declaradas se guardan como texto simple o estructura normalizada.

### Fase 8.2 - Checklist preliminar de requisitos

Objetivo:

- mostrar diagnostico de requisitos presentes/faltantes para una oferta o resultado.

Restricciones:

- no llamarlo ATS real;
- no prometer que simula un ATS real;
- no usarlo como filtro duro;
- no usarlo para ranking;
- no convertirlo en decision automatica de postulacion.

Lenguaje recomendado:

```text
Checklist preliminar de requisitos
```

Evitar:

```text
ATS score
Simulador ATS
Filtro ATS
```

### Fase 8.3 - Sugerencias de mejora de CV/perfil

Objetivo:

- sugerir que destacar, completar o mejorar para una oportunidad o rol objetivo.

Restricciones:

- no inventar experiencia;
- no inventar certificaciones;
- no afirmar evidencia que no existe;
- usar solo evidencia real del perfil, CV y proyectos visibles;
- separar "mejora de presentacion" de "brecha real de experiencia".

Ejemplos de salida esperada:

- "Destacar el proyecto donde usaste Spring Boot y PostgreSQL."
- "Agregar evidencia concreta si tenes experiencia con Docker."
- "La oferta pide Kubernetes; hoy aparece como brecha, podria ir al roadmap."

### Fase 8.4 - Roadmaps personalizados por brechas

Objetivo:

- detectar brechas repetidas y sugerir pasos de aprendizaje relacionados con el rol objetivo.

Alcance sugerido:

- agrupar brechas recurrentes por tecnologia o area;
- priorizar brechas criticas sobre secundarias;
- relacionar recomendaciones con `targetRole` y `targetSeniority`;
- mantener recomendaciones como asesoramiento, no como ranking.

Ejemplo:

```text
Para backend Java junior, las brechas repetidas son Docker y testing.
Siguiente paso sugerido: agregar un proyecto chico con Docker Compose y tests de integracion.
```

### PDF/DOCX upload - posterior u opcional

No es prioridad inmediata si el texto editable de CV resuelve la demo.

Si se implementa, el flujo seguro debe ser:

```text
archivo -> extraccion de texto -> vista previa editable -> guardar en perfil
```

Restricciones:

- no guardar texto extraido sin vista previa editable;
- no regenerar embeddings sin que el usuario confirme el texto final;
- manejar errores de extraccion y encoding;
- no convertir upload de archivos en requisito para usar la app.

## Riesgos registrados

- Confundir `suggestedRerankRank` con ranking real.
- Presentar el checklist como ATS real.
- Incluir proyectos en embeddings sin decision explicita y romper comparabilidad.
- Regenerar embeddings masivamente durante una fase de UI.
- Mezclar preferencias de busqueda con evidencia del perfil en el embedding.
- Activar score hibrido por presion de demo sin evidencia.
- Priorizar PDF/DOCX antes de resolver texto editable y estado de embeddings.
- Reabrir `JOB_OFFERS` como fuente accidental de ofertas.
- Introducir cambios sobre H2 como si fuera arquitectura objetivo.

## Decisiones pendientes

- Si Fase 8.0 debe invocar automaticamente prepare de embedding al guardar `cvText`, o solo marcar estado y ofrecer accion explicita.
- Como mostrar el estado de embedding en UI sin exponer demasiado detalle interno.
- Si Fase 8.1 agrega `skillsText` estructurado al perfil o mantiene texto simple.
- En que fase, si alguna, proyectos manuales pasan a formar parte del texto vectorizable.
- Si el checklist preliminar se muestra por oferta puntual, por resultado vectorial o ambos.
- Como versionar cambios de texto fuente para auditoria futura.

## Documentos relacionados

- [roadmap-scope-gate.md](roadmap-scope-gate.md): roadmap y scope gate previo del paper; queda como historico metodologico.
- [embeddings-pipeline.md](embeddings-pipeline.md): pipeline real de embeddings y estados `PENDING`/`READY`/`FAILED`.
- [vector-first-compatibility-strategy.md](vector-first-compatibility-strategy.md): estrategia vector-first y restricciones de ranking.
- [ui-vector-first-integration-audit.md](ui-vector-first-integration-audit.md): auditoria historica previa a la UI vector-first.
- [ui-vector-first-smoke-test.md](ui-vector-first-smoke-test.md): evidencia de la UI vector-first ya existente.

## Confirmacion de alcance

Esta mini fase es documental.

No modifica:

- codigo funcional;
- tests;
- migraciones;
- ranking;
- embeddings productivos;
- `VectorFirstCompatibilityService`;
- `RerankingDiagnosticService`;
- `CvMatchingService`;
- scraping;
- extension;
- captura;
- README.

## Commit sugerido

```text
docs: update post-paper demo roadmap
```
