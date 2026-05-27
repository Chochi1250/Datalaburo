# Estrategia de compatibilidad vector-first

Este documento registra la decision arquitectonica para el motor de compatibilidad profesional de Datalaburo.

Frase central:

```text
Datalaburo no busca replicar un ATS por keywords. El objetivo es construir un sistema de asesoramiento profesional basado en similitud semantica, evidencia del perfil, brechas realistas y transferibilidad de habilidades.
```

## Decision

Datalaburo debe evolucionar hacia un sistema de compatibilidad profesional vector-first.

Esto significa:

- `BAAI/bge-m3` + PostgreSQL + pgvector son el corazon del sistema.
- La busqueda vectorial recupera ofertas semanticamente cercanas al perfil/CV.
- El matching por reglas actual no debe ser fuente de verdad final.
- Las reglas actuales pueden servir como baseline historico, inspiracion o capa auxiliar de explicacion.
- Las senales estructuradas deben complementar al vector search, no reemplazarlo en la primera etapa.
- El sistema debe preparar una evolucion futura hacia reranking vector-first trazable.

## Motivo conceptual

Un ATS tradicional tiende a privilegiar coincidencias literales de keywords. Ese enfoque falla cuando:

- una skill aparece con otro nombre;
- el perfil sabe una tecnologia relacionada;
- la experiencia viene de proyectos y no de trabajo formal;
- una oferta usa lenguaje generico;
- un perfil es transferible aunque no coincida palabra por palabra;
- una brecha es secundaria y no el nucleo del rol;
- una cercania textual no implica compatibilidad real.

Datalaburo debe asesorar mejor: no solo decir si "matchea", sino explicar por que, donde estan las brechas y si la oportunidad es fuerte, razonable, transferible, aspiracional o poco conveniente para postulacion inmediata.

## Arquitectura objetivo

```text
Candidate Profile / CV
        |
        v
Normalized source text
        |
        v
BAAI/bge-m3 embedding
        |
        v
pgvector candidate retrieval
        |
        v
Structured compatibility analysis
        |
        v
Evidence, gaps, transferability, explanation
        |
        v
User-facing professional guidance
```

## Capas principales

### 1. Candidate retrieval

Usar PostgreSQL + pgvector con embeddings `BAAI/bge-m3` para recuperar Top N ofertas semanticamente cercanas a un perfil.

Esta capa debe:

- filtrar por `embedding_model = 'BAAI/bge-m3'`;
- filtrar por `embedding_dimensions = 1024`;
- exigir `status = READY`;
- exigir `embedding is not null`;
- comparar `PROFILE` contra `JOB`;
- devolver `vectorRank`, `distance` y `similarity`.

### 2. Structured signals

Extraer o representar senales estructuradas para interpretar mejor el resultado vectorial:

- rol: backend, frontend, fullstack, data, AI/ML, DevOps, cloud, soporte, QA, etc.;
- seniority: trainee, junior, mid, senior, lead;
- anos de experiencia;
- experiencia laboral formal;
- experiencia academica;
- proyectos personales;
- pasantias;
- certificaciones;
- tecnologias usadas;
- modalidad;
- idioma;
- industria o dominio.

Las reglas actuales pueden inspirar esta capa, pero conviene separarla conceptualmente del scorer historico.

### 3. Compatibility analysis

Comparar perfil y oferta desde una perspectiva profesional:

- cercania semantica;
- compatibilidad de rol;
- compatibilidad de seniority;
- coincidencia de skills nucleares;
- brechas criticas;
- brechas secundarias;
- evidencia disponible;
- senales de riesgo.

Esta capa no debe reducirse a "porcentaje de keywords".

### 4. Transferability analysis

Detectar relaciones de transferencia entre skills, areas y trayectorias.

Ejemplos:

- `C` puede ayudar a entender `C++`.
- `Java` puede facilitar `C#`.
- `Spring Boot` puede facilitar otros frameworks backend.
- `SQL` transfiere parcialmente entre PostgreSQL, MySQL y SQL Server.
- `Docker` puede facilitar Kubernetes como siguiente paso.
- Experiencia backend puede transferirse parcialmente a DevOps o cloud.
- Soporte tecnico puede transferirse a technical support, IT analyst o cloud support.

La transferibilidad debe ser explicita y trazable. No debe inflar artificialmente un match fuerte si faltan skills nucleares.

### 5. Gap analysis

Identificar brechas concretas y clasificarlas por gravedad:

- faltante critico: skill o experiencia central para el rol;
- faltante secundario: herramienta deseable o complementaria;
- faltante aspiracional: brecha grande que sirve para roadmap pero no para postulacion inmediata;
- faltante transferible: no tiene la skill exacta, pero tiene una base relacionada;
- riesgo de keyword: hay coincidencias superficiales, pero el rol/contexto no parece alineado.

Ejemplo conceptual:

- No es lo mismo "no tiene experiencia" que "no tiene experiencia laboral pero tiene proyectos".
- No es lo mismo "no sabe Kubernetes" que "uso Docker y entiende contenedores".
- No es lo mismo "le falta una skill secundaria" que "le falta el nucleo del rol".

### 6. Evidence-aware assessment

Diferenciar el tipo de evidencia para cada skill o area:

- experiencia laboral;
- proyecto real;
- repositorio;
- certificacion;
- formacion academica;
- curso;
- mencion sin evidencia;
- skill transferible.

Esto permite que el sistema asesore con mas precision. Por ejemplo: "tenes base tecnica, pero convendria mostrar evidencia concreta en proyectos" es mas util que bajar el score sin explicacion.

### 7. Explanation layer

Generar feedback entendible para el usuario:

- por que aparece esta oferta;
- que coincide;
- que falta;
- que es transferible;
- que evidencia del perfil respalda el match;
- si conviene postular ahora o usar la oferta como roadmap.

La explicacion debe estar conectada con las senales usadas. No debe inventar evidencia.

### 8. Evaluation layer

Permitir validar si el enfoque funciona:

- evaluacion manual de Top N;
- etiquetas humanas;
- falsos positivos;
- falsos negativos;
- calidad de explicaciones;
- comparacion contra baselines.

Con mas datos, esta capa puede evolucionar hacia metricas como precision@k, nDCG y aprendizaje de ranking.

## Estrategias consideradas

### A. VECTOR_ONLY

Ranking puramente vectorial con `BAAI/bge-m3` + pgvector.

Ventajas:

- simple;
- defendible como baseline semantico;
- evita pesos arbitrarios;
- aprovecha directamente el modelo real.

Desventajas:

- explica poco;
- no distingue evidencia fuerte de mencion superficial;
- puede traer ofertas semanticamente cercanas pero poco postulables;
- no clasifica brechas.

Riesgos:

- confundir cercania semantica con compatibilidad profesional completa.

Dificultad:

- baja.

Valor para tesis:

- alto como baseline.

Momento recomendado:

- ahora, como punto de comparacion.

### B. VECTOR_FIRST_WITH_EXPLANATION

Ranking vectorial como principal, enriquecido con senales estructuradas, gaps, evidencia, transferibilidad y explicacion.

Ventajas:

- mantiene a BGE-M3 + pgvector como nucleo;
- agrega valor de asesoramiento;
- evita vender un score arbitrario;
- es implementable de forma incremental;
- es muy defendible para tesis.

Desventajas:

- no corrige todavia todos los falsos positivos del ranking;
- requiere disenar DTOs y analisis explicable.

Riesgos:

- que las explicaciones parezcan mas firmes que la evidencia disponible si no se controla bien la confianza.

Dificultad:

- media.

Valor para tesis:

- muy alto.

Momento recomendado:

- primera etapa.

### C. VECTOR_FIRST_WITH_RERANKING

Recuperacion vectorial inicial y reranking posterior usando seniority, skills, brechas, evidencia y transferibilidad.

Ventajas:

- mejora el orden final;
- permite bajar ofertas con brechas criticas;
- permite subir oportunidades transferibles razonables;
- conserva retrieval semantico como base.

Desventajas:

- exige justificar cada ajuste;
- necesita evaluacion para calibrar;
- puede introducir sesgos si se disena apresuradamente.

Riesgos:

- convertirse en un score por reglas disfrazado si las senales estructuradas dominan sin validacion.

Dificultad:

- media/alta.

Valor para tesis:

- muy alto como arquitectura objetivo posterior.

Momento recomendado:

- despues de validar `VECTOR_FIRST_WITH_EXPLANATION`.

### D. HYBRID_WEIGHTED

Combinacion ponderada entre score vectorial y senales estructuradas.

Ventajas:

- facil de presentar como numero unico;
- familiar para demos;
- implementacion relativamente directa.

Desventajas:

- pesos potencialmente arbitrarios;
- dificil de defender sin dataset etiquetado;
- puede ocultar por que una oferta subio o bajo;
- riesgo de volver al matching por reglas como centro.

Riesgos:

- vender una precision que el sistema no puede justificar.

Dificultad:

- media.

Valor para tesis:

- limitado si no hay evaluacion fuerte.

Momento recomendado:

- no como solucion inicial/final. Solo considerar despues de evaluar datos reales.

### E. LEARNING_TO_RANK_FUTURE

Modelo futuro que aprende de evaluaciones humanas o feedback de usuario.

Ventajas:

- puede optimizar ranking con datos reales;
- permite personalizacion;
- reduce arbitrariedad si hay buen dataset.

Desventajas:

- requiere muchas etiquetas;
- necesita diseno de feedback;
- agrega complejidad tecnica y metodologica.

Riesgos:

- sobreajuste si el dataset es chico;
- sesgos de etiquetas;
- baja explicabilidad si no se disena con cuidado.

Dificultad:

- alta.

Valor para tesis:

- excelente como evolucion futura.

Momento recomendado:

- mas adelante, cuando existan datos etiquetados suficientes.

## Estrategia recomendada

La estrategia central recomendada es:

```text
VECTOR_FIRST_WITH_EXPLANATION -> VECTOR_FIRST_WITH_RERANKING
```

Primero:

- usar pgvector + `BAAI/bge-m3` para Top N;
- mantener `vectorRank` como ranking principal;
- enriquecer resultados con senales estructuradas;
- exponer explicaciones, brechas y evidencia;
- evaluar manualmente.

Despues:

- introducir reranking trazable;
- documentar cada senal que ajusta el orden;
- validar con etiquetas humanas.

## Primera implementacion sugerida

Endpoint interno:

```text
GET /internal/analysis/profiles/{profileId}/vector-first-compatibility?limit=20
```

Respuesta conceptual:

```json
{
  "profileId": 1,
  "embeddingModel": "BAAI/bge-m3",
  "embeddingDimensions": 1024,
  "strategy": "VECTOR_FIRST_WITH_EXPLANATION",
  "results": [
    {
      "jobId": 14,
      "vectorRank": 1,
      "vectorSimilarity": 0.6806,
      "detectedRole": "BACKEND",
      "detectedSeniority": "MID",
      "compatibilityCategory": "GOOD_MATCH_WITH_MINOR_GAPS",
      "evidenceLevel": "PROJECT_OR_WORK",
      "matchedSkills": ["Java", "Spring Boot", "PostgreSQL"],
      "missingCriticalSkills": [],
      "missingSecondarySkills": ["Kubernetes"],
      "transferableSkills": [
        {
          "from": "Docker",
          "to": "Kubernetes",
          "strength": "PARTIAL",
          "reason": "base de contenedores transferible"
        }
      ],
      "roadmapSuggestions": ["Profundizar Kubernetes basico"],
      "explanation": "La oferta esta cerca semanticamente y comparte nucleo backend.",
      "confidence": "MEDIUM"
    }
  ]
}
```

## Categorias conceptuales

- `STRONG_MATCH`: buena cercania vectorial y evidencia tecnica fuerte.
- `GOOD_MATCH_WITH_MINOR_GAPS`: buena cercania y pocas brechas.
- `TRANSFERABLE_OPPORTUNITY`: no coincide perfecto, pero hay skills o experiencia transferible.
- `ASPIRATIONAL_MATCH`: cercana semanticamente, pero con brechas importantes.
- `KEYWORD_MATCH_RISK`: parece coincidir por terminos, pero no necesariamente por rol/contexto.
- `LOW_FIT`: baja compatibilidad.
- `LEARNING_ROADMAP_ONLY`: util para aprender, no para postulacion inmediata.

## Evaluacion inicial

Con el dataset actual chico:

1. Tomar Top 10 vectoriales para el perfil disponible.
2. Etiquetar manualmente cada oferta:
   - postulable ahora;
   - postulable con gaps menores;
   - transferible;
   - aspiracional;
   - baja compatibilidad.
3. Detectar falsos positivos.
4. Detectar falsos negativos.
5. Revisar si la explicacion generada tiene sentido.
6. Comparar `VECTOR_ONLY` contra `VECTOR_FIRST_WITH_EXPLANATION`.

Con mas ofertas:

- aumentar perfiles de prueba;
- crear etiquetas humanas;
- medir precision@k;
- medir nDCG;
- registrar feedback de usuario;
- evaluar learning-to-rank futuro.

## Restricciones

- No tocar scraping para esta arquitectura.
- No tocar extension.
- No tocar captura.
- No tocar migraciones existentes.
- No reintroducir H2 como fallback.
- No mezclar `fake-deterministic-1024` con `BAAI/bge-m3`.
- No convertir las reglas actuales en fuente de verdad final.
- No vender un score hibrido arbitrario como solucion final.
- No implementar UI grande antes de validar el endpoint interno.
