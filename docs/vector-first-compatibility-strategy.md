# Estrategia de compatibilidad vector-first

Este documento registra la decisión arquitectónica para el motor de compatibilidad profesional de Datalaburo.

Frase central:

```text
Datalaburo no busca replicar un ATS por keywords. El objetivo es construir un sistema de asesoramiento profesional basado en similitud semántica, evidencia del perfil, brechas realistas y transferibilidad de habilidades.
```

## Decisión

Datalaburo debe evolucionar hacia un sistema de compatibilidad profesional vector-first.

Esto significa:

- `BAAI/bge-m3` + PostgreSQL + pgvector son el corazón del sistema.
- La búsqueda vectorial recupera ofertas semánticamente cercanas al perfil/CV.
- El matching por reglas actual no debe ser fuente de verdad final.
- Las reglas actuales pueden servir como baseline histórico, inspiración o capa auxiliar de explicación.
- Las señales estructuradas deben complementar al vector search, no reemplazarlo en la primera etapa.
- El sistema debe preparar una evolución futura hacia reranking vector-first trazable.

## Motivo conceptual

Un ATS tradicional tiende a privilegiar coincidencias literales de keywords. Ese enfoque falla cuando:

- una skill aparece con otro nombre;
- el perfil sabe una tecnología relacionada;
- la experiencia viene de proyectos y no de trabajo formal;
- una oferta usa lenguaje genérico;
- un perfil es transferible aunque no coincida palabra por palabra;
- una brecha es secundaria y no el núcleo del rol;
- una cercanía textual no implica compatibilidad real.

Datalaburo debe asesorar mejor: no solo decir si "matchea", sino explicar por qué, dónde están las brechas y si la oportunidad es fuerte, razonable, transferible, aspiracional o poco conveniente para postulación inmediata.

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

Usar PostgreSQL + pgvector con embeddings `BAAI/bge-m3` para recuperar Top N ofertas semánticamente cercanas a un perfil.

Esta capa debe:

- filtrar por `embedding_model = 'BAAI/bge-m3'`;
- filtrar por `embedding_dimensions = 1024`;
- exigir `status = READY`;
- exigir `embedding is not null`;
- comparar `PROFILE` contra `JOB`;
- devolver `vectorRank`, `distance` y `similarity`.

### 2. Structured signals

Extraer o representar señales estructuradas para interpretar mejor el resultado vectorial:

- rol: backend, frontend, fullstack, data, AI/ML, DevOps, cloud, soporte, QA, etc.;
- seniority: trainee, junior, mid, senior, lead;
- años de experiencia;
- experiencia laboral formal;
- experiencia académica;
- proyectos personales;
- pasantías;
- certificaciones;
- tecnologías usadas;
- modalidad;
- idioma;
- industria o dominio.

Las reglas actuales pueden inspirar esta capa, pero conviene separarla conceptualmente del scorer histórico.

### 3. Compatibility analysis

Comparar perfil y oferta desde una perspectiva profesional:

- cercanía semántica;
- compatibilidad de rol;
- compatibilidad de seniority;
- coincidencia de skills nucleares;
- brechas críticas;
- brechas secundarias;
- evidencia disponible;
- señales de riesgo.

Esta capa no debe reducirse a "porcentaje de keywords".

### 4. Transferability analysis

Detectar relaciones de transferencia entre skills, áreas y trayectorias.

Ejemplos:

- `C` puede ayudar a entender `C++`.
- `Java` puede facilitar `C#`.
- `Spring Boot` puede facilitar otros frameworks backend.
- `SQL` transfiere parcialmente entre PostgreSQL, MySQL y SQL Server.
- `Docker` puede facilitar Kubernetes como siguiente paso.
- Experiencia backend puede transferirse parcialmente a DevOps o cloud.
- Soporte técnico puede transferirse a technical support, IT analyst o cloud support.

La transferibilidad debe ser explícita y trazable. No debe inflar artificialmente un match fuerte si faltan skills nucleares.

### 5. Gap analysis

Identificar brechas concretas y clasificarlas por gravedad:

- faltante crítico: skill o experiencia central para el rol;
- faltante secundario: herramienta deseable o complementaria;
- faltante aspiracional: brecha grande que sirve para roadmap pero no para postulación inmediata;
- faltante transferible: no tiene la skill exacta, pero tiene una base relacionada;
- riesgo de keyword: hay coincidencias superficiales, pero el rol/contexto no parece alineado.

Ejemplo conceptual:

- No es lo mismo "no tiene experiencia" que "no tiene experiencia laboral pero tiene proyectos".
- No es lo mismo "no sabe Kubernetes" que "uso Docker y entiende contenedores".
- No es lo mismo "le falta una skill secundaria" que "le falta el núcleo del rol".

### 6. Evidence-aware assessment

Diferenciar el tipo de evidencia para cada skill o área:

- experiencia laboral;
- proyecto real;
- repositorio;
- certificación;
- formación académica;
- curso;
- mención sin evidencia;
- skill transferible.

Esto permite que el sistema asesore con más precisión. Por ejemplo: "tenés base técnica, pero convendría mostrar evidencia concreta en proyectos" es más útil que bajar el score sin explicación.

### 7. Explanation layer

Generar feedback entendible para el usuario:

- por qué aparece esta oferta;
- qué coincide;
- qué falta;
- qué es transferible;
- qué evidencia del perfil respalda el match;
- si conviene postular ahora o usar la oferta como roadmap.

La explicación debe estar conectada con las señales usadas. No debe inventar evidencia.

### 8. Evaluation layer

Permitir validar si el enfoque funciona:

- evaluación manual de Top N;
- etiquetas humanas;
- falsos positivos;
- falsos negativos;
- calidad de explicaciones;
- comparación contra baselines.

Con más datos, esta capa puede evolucionar hacia métricas como precisión@k, nDCG y aprendizaje de ranking.

## Estrategias consideradas

### A. VECTOR_ONLY

Ranking puramente vectorial con `BAAI/bge-m3` + pgvector.

Ventajas:

- simple;
- defendible como baseline semántico;
- evita pesos arbitrarios;
- aprovecha directamente el modelo real.

Desventajas:

- explica poco;
- no distingue evidencia fuerte de mención superficial;
- puede traer ofertas semánticamente cercanas pero poco postulables;
- no clasifica brechas.

Riesgos:

- confundir cercanía semántica con compatibilidad profesional completa.

Dificultad:

- baja.

Valor para tesis:

- alto como baseline.

Momento recomendado:

- ahora, como punto de comparación.

### B. VECTOR_FIRST_WITH_EXPLANATION

Ranking vectorial como principal, enriquecido con señales estructuradas, gaps, evidencia, transferibilidad y explicación.

Ventajas:

- mantiene a BGE-M3 + pgvector como núcleo;
- agrega valor de asesoramiento;
- evita vender un score arbitrario;
- es implementable de forma incremental;
- es muy defendible para tesis.

Desventajas:

- no corrige todavía todos los falsos positivos del ranking;
- requiere diseñar DTOs y análisis explicable.

Riesgos:

- que las explicaciones parezcan más firmes que la evidencia disponible si no se controla bien la confianza.

Dificultad:

- media.

Valor para tesis:

- muy alto.

Momento recomendado:

- primera etapa.

### C. VECTOR_FIRST_WITH_RERANKING

Recuperación vectorial inicial y reranking posterior usando seniority, skills, brechas, evidencia y transferibilidad.

Ventajas:

- mejora el orden final;
- permite bajar ofertas con brechas críticas;
- permite subir oportunidades transferibles razonables;
- conserva retrieval semántico como base.

Desventajas:

- exige justificar cada ajuste;
- necesita evaluación para calibrar;
- puede introducir sesgos si se diseña apresuradamente.

Riesgos:

- convertirse en un score por reglas disfrazado si las señales estructuradas dominan sin validación.

Dificultad:

- media/alta.

Valor para tesis:

- muy alto como arquitectura objetivo posterior.

Momento recomendado:

- después de validar `VECTOR_FIRST_WITH_EXPLANATION`.

### D. HYBRID_WEIGHTED

Combinación ponderada entre score vectorial y señales estructuradas.

Ventajas:

- fácil de presentar como número único;
- familiar para demos;
- implementación relativamente directa.

Desventajas:

- pesos potencialmente arbitrarios;
- difícil de defender sin dataset etiquetado;
- puede ocultar por qué una oferta subió o bajó;
- riesgo de volver al matching por reglas como centro.

Riesgos:

- vender una precisión que el sistema no puede justificar.

Dificultad:

- media.

Valor para tesis:

- limitado si no hay evaluación fuerte.

Momento recomendado:

- no como solución inicial/final. Solo considerar después de evaluar datos reales.

### E. LEARNING_TO_RANK_FUTURE

Modelo futuro que aprende de evaluaciones humanas o feedback de usuario.

Ventajas:

- puede optimizar ranking con datos reales;
- permite personalización;
- reduce arbitrariedad si hay buen dataset.

Desventajas:

- requiere muchas etiquetas;
- necesita diseño de feedback;
- agrega complejidad técnica y metodológica.

Riesgos:

- sobreajuste si el dataset es chico;
- sesgos de etiquetas;
- baja explicabilidad si no se diseña con cuidado.

Dificultad:

- alta.

Valor para tesis:

- excelente como evolución futura.

Momento recomendado:

- más adelante, cuando existan datos etiquetados suficientes.

## Estrategia vigente

La estrategia central del proyecto evolucionó así:

```text
VECTOR_FIRST_WITH_EXPLANATION -> VECTOR_FIRST_WITH_RERANKING_DIAGNOSTIC -> VECTOR_FIRST_WITH_RERANKING
```

Estado actual:

- usar pgvector + `BAAI/bge-m3` para Top N;
- mantener `vectorRank` como auditoría;
- mantener `analysisRank == vectorRank`;
- enriquecer resultados con señales estructuradas;
- exponer explicaciones, brechas y evidencia;
- calcular `suggestedRerankRank` y `suggestedRankDelta` solo como diagnóstico;
- evaluar manualmente sobre perfiles reales y perfiles `DIAG`.

Después:

- introducir reranking trazable;
- documentar cada señal que ajusta el orden;
- validar con etiquetas humanas.

## Endpoint interno actual

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
  "strategy": "VECTOR_FIRST_WITH_RERANKING_DIAGNOSTIC",
  "results": [
    {
      "jobId": 14,
      "vectorRank": 1,
      "analysisRank": 1,
      "suggestedRerankRank": 1,
      "suggestedRankDelta": 0,
      "vectorSimilarity": 0.6806,
      "detectedRole": "BACKEND",
      "detectedSeniority": "MID",
      "compatibilityCategory": "GOOD_MATCH_WITH_MINOR_GAPS",
      "compatibilityBucket": "GOOD_WITH_MINOR_GAPS",
      "evidenceLevel": "PROJECT",
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
      "roadmapSuggestions": ["Profundizar Kubernetes básico"],
      "explanation": "La oferta está cerca semánticamente y comparte núcleo backend.",
      "confidence": "MEDIUM",
      "rerankReasons": ["Subiría o se mantendría por alineación de rol: BACKEND con perfil BACKEND."],
      "rerankWarnings": [],
      "rerankSignals": []
    }
  ]
}
```

Los buckets y ranks sugeridos de esta etapa son diagnósticos internos. Sirven para auditar una posible evolución de ranking, pero no son una verdad absoluta ni un orden final activo.

## Categorías conceptuales

- `STRONG_MATCH`: buena cercanía vectorial y evidencia técnica fuerte.
- `GOOD_MATCH_WITH_MINOR_GAPS`: buena cercanía y pocas brechas.
- `TRANSFERABLE_OPPORTUNITY`: no coincide perfecto, pero hay skills o experiencia transferible.
- `ASPIRATIONAL_MATCH`: cercana semánticamente, pero con brechas importantes.
- `KEYWORD_MATCH_RISK`: parece coincidir por términos, pero no necesariamente por rol/contexto.
- `LOW_FIT`: baja compatibilidad.
- `LEARNING_ROADMAP_ONLY`: útil para aprender, no para postulación inmediata.

## Evaluación inicial

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
5. Revisar si la explicación generada tiene sentido.
6. Revisar si `suggestedRerankRank` sería defendible sin activar reranking real.
7. Comparar `VECTOR_ONLY`, `VECTOR_FIRST_WITH_EXPLANATION` y `VECTOR_FIRST_WITH_RERANKING_DIAGNOSTIC`.

Con más ofertas:

- aumentar perfiles de prueba;
- crear etiquetas humanas;
- medir precisión@k;
- medir nDCG;
- registrar feedback de usuario;
- evaluar learning-to-rank futuro.

## Restricciones

- No tocar scraping para esta arquitectura.
- No tocar extensión.
- No tocar captura.
- No tocar migraciones existentes.
- No reintroducir H2 como fallback.
- No mezclar `fake-deterministic-1024` con `BAAI/bge-m3`.
- No convertir las reglas actuales en fuente de verdad final.
- No vender un score híbrido arbitrario como solución final.
- No implementar UI grande antes de validar el endpoint interno.
