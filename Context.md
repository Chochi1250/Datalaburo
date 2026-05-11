# Datalaburo — Contexto del Proyecto

## Descripción general

Datalaburo es un MVP de tesis orientado al análisis de compatibilidad entre perfiles profesionales y ofertas laborales tecnológicas.

El sistema permite capturar ofertas laborales, almacenarlas, visualizarlas y compararlas contra el CV de un usuario mediante un sistema de matching basado en skills, aliases, reglas de afinidad, roles, áreas y seniority.

## Stack tecnológico

- Java
- Spring Boot
- Thymeleaf
- Spring Data JPA
- H2 Database
- Maven
- Extensión de navegador para captura de ofertas

## Estado actual del MVP

Actualmente el sistema cuenta con:

- Captura individual de ofertas desde LinkedIn mediante extensión de navegador.
- Endpoint de captura: POST /plugins/scrape-current.
- Persistencia de ofertas en la tabla JOBS.
- JOBS como fuente de verdad.
- Visualización de trabajos cargados.
- Limpieza y normalización básica de textos.
- Página /jobs con lista y detalle.
- Página /matching para pegar CV y calcular compatibilidad.
- Matching basado en:
  - catálogo de skills;
  - skill aliases;
  - reglas de inferencia;
  - roles;
  - áreas;
  - seniority;
  - scoring explicable.
- No se guarda el CV en la versión actual del matching.
- No se usa IA.
- No se usa vector DB.
- No se usa PostgreSQL todavía.

## Decisiones importantes

- H2 se mantiene para cerrar el MVP.
- PostgreSQL queda como migración futura.
- pgvector/embeddings quedan para una fase posterior.
- El CV se procesa en memoria.
- La extracción automática de requisitos se despriorizó por inestabilidad.
- El matching debe ser explicable y defendible para demo.

## Componentes principales

### Extensión de navegador

Captura datos visibles de ofertas laborales desde LinkedIn y los envía al backend.

### Backend Spring Boot

Gestiona endpoints, lógica de negocio, persistencia y matching.

### Thymeleaf

Renderiza las vistas web del MVP.

### H2

Base de datos local del MVP.

### Matching

Compara el CV ingresado por el usuario contra ofertas guardadas en JOBS.

## Tablas principales

- JOBS: fuente de verdad de ofertas laborales.
- SKILLS: catálogo de habilidades.
- SKILL_ALIASES: aliases o variantes de skills.
- JOB_SNAPSHOTS: historial opcional / legacy.
- JOB_OFFERS: tabla legacy que no debería formar parte del flujo principal.

## Funcionalidades futuras

- Perfiles básicos persistentes.
- Carga de CV por archivo.
- Migración a PostgreSQL.
- Incorporación de pgvector.
- Embeddings para similitud semántica.
- IA para mejorar extracción y explicación del matching.

## Restricciones actuales

- No romper el flujo de captura individual.
- No tocar scraping salvo necesidad puntual.
- No migrar a PostgreSQL todavía.
- No agregar pgvector todavía.
- No agregar IA todavía.
- Priorizar estabilidad del MVP.