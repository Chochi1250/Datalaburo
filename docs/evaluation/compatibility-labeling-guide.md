# Guia de etiquetado humano de compatibilidad

## Objetivo

Definir una taxonomia estable para etiquetar manualmente resultados de compatibilidad vector-first entre perfiles profesionales/CVs y ofertas laborales tecnologicas.

La guia evalua cada par `perfil + oferta` como juicio humano. No cambia el ranking, no activa reranking real y no convierte buckets diagnosticos en verdad final.

## Unidad de evaluacion

Cada fila del dataset representa:

```text
un perfil evaluado contra una oferta recuperada por el endpoint vector-first
```

La etiqueta humana debe responder:

```text
Esta oferta seria una oportunidad razonable para este perfil?
```

La decision debe considerar:

- rol buscado por el perfil;
- seniority del perfil y de la oferta;
- skills directas;
- gaps criticos;
- gaps secundarios;
- skills transferibles;
- evidencia del perfil;
- calidad del texto de la oferta;
- explicacion y warnings generados por el sistema.

## Labels

### `STRONG_MATCH`

Oferta altamente compatible. El perfil podria postularse con buena defensa profesional.

Criterios orientativos:

- rol de la oferta alineado con el objetivo del perfil;
- seniority compatible o apenas superior;
- varias skills nucleares coinciden;
- no hay gaps criticos claros;
- la evidencia del perfil es fuerte (`WORK_EXPERIENCE`, `PROJECT` o `CERTIFICATION`);
- la explicacion del sistema es coherente;
- los warnings no afectan el nucleo del match.

Ejemplo:

- perfil backend senior con Java/Spring/cloud frente a oferta backend Java/Spring con gaps menores.
- perfil soporte junior frente a oferta help desk/app support junior con skills de soporte visibles.

### `GOOD_MATCH_WITH_GAPS`

Oferta compatible, pero con brechas menores o entrenables.

Criterios orientativos:

- rol alineado;
- seniority razonable, aunque puede requerir aprendizaje;
- skills principales parcialmente cubiertas;
- gaps secundarios o pocos gaps criticos de baja severidad;
- evidencia suficiente para defender la postulacion;
- la oferta podria ser postulable con preparacion breve o ajuste de CV.

Ejemplo:

- perfil backend junior con Java/Spring frente a oferta backend que pide Docker/Kubernetes basico.
- perfil data/BI con SQL y Power BI frente a oferta BI que pide una herramienta adicional.

### `TRANSFERABLE_OPPORTUNITY`

No es un match directo, pero hay habilidades transferibles defendibles.

Criterios orientativos:

- el rol no coincide exactamente, pero esta cerca profesionalmente;
- existen skills base que pueden transferirse;
- la brecha no invalida por completo la oportunidad;
- conviene explicar la postulacion desde crecimiento o cambio de foco;
- no debe marcarse como fuerte si faltan skills nucleares.

Ejemplo:

- backend con Docker/Linux/CI basico frente a una oferta cloud/devops junior.
- soporte tecnico con SQL y troubleshooting frente a application support.

### `ASPIRATIONAL_MATCH`

Oportunidad aspiracional. Puede servir como roadmap de aprendizaje, pero requiere preparacion relevante antes de postular.

Criterios orientativos:

- cercania semantica real, pero brechas importantes;
- seniority superior o requisitos centrales ausentes;
- perfil podria crecer hacia esa oferta, pero no esta listo para priorizarla;
- la oferta es util para detectar skills a aprender.

Ejemplo:

- perfil trainee frente a oferta senior.
- perfil data inicial frente a oferta de database developer senior con Oracle avanzado.

### `LOW_FIT`

Baja compatibilidad. No deberia priorizarse para este perfil.

Criterios orientativos:

- rol alejado del objetivo profesional;
- ausencia de skills nucleares;
- gaps criticos multiples;
- seniority muy superior sin evidencia suficiente;
- coincidencias solo genericas como `SQL`, `REST` o `Git`;
- warning de rol o texto que vuelve dudoso el resultado.

Ejemplo:

- perfil backend frente a oferta de soporte puro sin trayectoria transferible relevante.
- perfil data/BI frente a oferta IAM/security sin nucleo de datos.

### `UNCLEAR`

No se puede decidir con confianza.

Usar cuando:

- el texto de la oferta esta incompleto;
- hay encoding roto o mojibake que impide entender requisitos;
- la descripcion no incluye seniority ni tareas suficientes;
- el sistema detecta rol dudoso y el humano tampoco puede resolverlo;
- faltan datos del perfil;
- la evidencia local parece inconsistente con el `profileId`.

`UNCLEAR` no debe contarse como positivo ni negativo sin revision posterior.

## Como tratar senales especificas

### Skills directas

Las skills directas pesan mas cuando son nucleares para el rol.

- Java/Spring para backend Java pesa mucho.
- SQL/Power BI para data/BI pesa mucho.
- Help desk/ITIL/Active Directory para soporte pesa mucho.
- Git o REST aislados suelen ser senales genericas.

No etiquetar como `STRONG_MATCH` solo por compartir skills genericas.

### Gaps criticos

Un gap critico es una ausencia que afecta el nucleo del rol.

Ejemplos:

- oferta DevOps que exige Kubernetes/AWS/CI-CD y perfil sin evidencia cloud;
- oferta backend senior que exige microservicios y experiencia productiva, perfil trainee;
- oferta data engineering que exige pipelines/ETL avanzado, perfil solo reporting basico.

Muchos gaps criticos empujan hacia `ASPIRATIONAL_MATCH` o `LOW_FIT`.

### Gaps secundarios

Un gap secundario es una herramienta o requisito entrenable que no define todo el rol.

Ejemplos:

- una base de datos alternativa;
- una herramienta de observabilidad;
- una libreria o framework complementario;
- una certificacion deseable.

Gaps secundarios pueden mantener el label en `GOOD_MATCH_WITH_GAPS`.

### Transferibilidad

La transferencia debe ser concreta y explicable.

Ejemplos defendibles:

- Docker ayuda parcialmente hacia Kubernetes;
- Java/Spring ayuda hacia backend con frameworks similares;
- SQL transfiere entre PostgreSQL, SQL Server y MySQL;
- soporte tecnico transfiere hacia application support;
- experiencia backend puede ayudar en cloud/backend platform.

No usar transferibilidad para esconder un rol claramente lejano o gaps criticos centrales.

### Seniority

El seniority debe compararse con la evidencia del perfil.

- perfil trainee/junior frente a oferta senior: normalmente `ASPIRATIONAL_MATCH` o `LOW_FIT`.
- perfil senior frente a oferta junior: puede ser compatible, pero revisar si tiene sentido profesional.
- seniority `UNKNOWN`: no penalizar automaticamente, pero marcar `needs_review` si el texto no permite decidir.

### Rol detectado

El rol detectado por el sistema es una senal, no una verdad final.

- Si el rol detectado coincide con titulo, descripcion y skills, usarlo como apoyo.
- Si hay `rerankWarnings`, revisar manualmente.
- Si el rol detectado contradice el titulo o la descripcion, marcar `needs_review` y considerar `UNCLEAR` o `LOW_FIT`.

### `evidenceLevel`

`evidenceLevel` ayuda a distinguir menciones superficiales de experiencia defendible.

Orden aproximado:

```text
WORK_EXPERIENCE / PROJECT / CERTIFICATION > ACADEMIC > MENTIONED_ONLY > NO_EVIDENCE
```

Un perfil con `PROJECT` puede ser defendible para junior/trainee. Para seniority alto, proyectos pueden no alcanzar si faltan experiencia productiva o escala.

### Texto incompleto o encoding roto

Si el texto de la oferta tiene mojibake, truncamiento o datos insuficientes:

- no inventar requisitos;
- usar `UNCLEAR` si no se puede decidir;
- marcar `needs_review=true`;
- registrar el problema en `human_notes`;
- no usar ese caso para calibrar reglas sin revisar la fuente.

## Falsos positivos y falsos negativos

### Falso positivo

Marcar `is_false_positive=true` cuando el ranking trae una oferta arriba, pero el humano la considera `LOW_FIT` o `UNCLEAR` por mala evidencia.

Ejemplos:

- Top 5 con rol alejado;
- coincidencia por keywords genericas;
- oferta senior para perfil trainee sin transferencia razonable;
- bucket optimista con gaps criticos.

### Falso negativo candidato

Marcar `is_false_negative_candidate=true` cuando una oferta parece buena para el perfil, pero:

- aparece muy abajo;
- el diagnostico la bajaria de forma dudosa;
- el bucket parece demasiado pesimista;
- una oferta conocida relevante no aparece en Top N aunque tiene embedding READY.

Esta marca es candidata: requiere revisar si la oferta estaba en la muestra y si tenia embedding `READY`.

## Relacion con categorias del sistema

Las categorias y buckets del sistema ayudan a auditar, pero no reemplazan el label humano.

| Sistema | Interpretacion humana sugerida |
| --- | --- |
| `STRONG_MATCH` | Puede corresponder a `STRONG_MATCH` si la evidencia lo respalda. |
| `GOOD_MATCH_WITH_MINOR_GAPS` | Puede corresponder a `GOOD_MATCH_WITH_GAPS`. |
| `TRANSFERABLE_OPPORTUNITY` | Puede corresponder a `TRANSFERABLE_OPPORTUNITY`. |
| `ASPIRATIONAL_MATCH` | Puede corresponder a `ASPIRATIONAL_MATCH`. |
| `LEARNING_ROADMAP_ONLY` | Suele ser `ASPIRATIONAL_MATCH` o `LOW_FIT`. |
| `LOW_FIT` | Suele ser `LOW_FIT`, salvo deteccion erronea. |

Los buckets diagnosticos (`READY_NOW`, `GOOD_WITH_MINOR_GAPS`, `TRANSFERABLE`, `ASPIRATIONAL`, `WEAK_MATCH`, `LOW_FIT`) deben usarse como senales internas.

## Checklist de etiquetado

Para cada fila:

1. Confirmar perfil y objetivo profesional.
2. Leer titulo, empresa y descripcion disponible de la oferta.
3. Revisar `vectorRank` y `analysisRank` sin tratarlos como juicio humano.
4. Revisar rol, seniority, categoria, evidenceLevel y confidence.
5. Revisar skills matcheadas y si son nucleares o genericas.
6. Revisar gaps criticos y secundarios.
7. Revisar transferencias.
8. Revisar warnings y problemas de texto.
9. Asignar un unico `human_label`.
10. Completar `human_notes`, `is_false_positive`, `is_false_negative_candidate` y `needs_review`.

## Criterio de consistencia

Si dos etiquetas parecen posibles, elegir la mas conservadora y explicar la duda en `human_notes`.

Ejemplo:

- entre `GOOD_MATCH_WITH_GAPS` y `TRANSFERABLE_OPPORTUNITY`, usar `TRANSFERABLE_OPPORTUNITY` si el rol no es directo.
- entre `ASPIRATIONAL_MATCH` y `LOW_FIT`, usar `LOW_FIT` si la oferta no deberia priorizarse.
- usar `UNCLEAR` si el problema es informacion insuficiente, no baja compatibilidad real.
