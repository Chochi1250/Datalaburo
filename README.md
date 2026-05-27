# Datalaburo

Datalaburo es mi proyecto de tesis para analizar la compatibilidad entre CVs y ofertas laborales tecnologicas. La aplicacion permite capturar ofertas desde LinkedIn mediante una extension de navegador, almacenarlas en PostgreSQL, visualizar los trabajos cargados y analizar compatibilidad entre perfiles profesionales y ofertas.

La direccion estrategica del proyecto es vector-first: PostgreSQL + pgvector y `BAAI/bge-m3` son el corazon del sistema. El objetivo no es replicar un ATS por keywords, sino construir un sistema de asesoramiento profesional basado en similitud semantica, evidencia del perfil, brechas realistas y transferibilidad de habilidades.

## Estado actual del proyecto

- Backend Java con Spring Boot.
- Vistas server-side con Thymeleaf.
- Persistencia con Spring Data JPA.
- PostgreSQL + pgvector como base objetivo.
- Flyway gestiona el esquema PostgreSQL.
- `jobs` es la fuente de verdad de ofertas capturadas.
- `candidate_profiles` guarda perfiles/CVs.
- `document_embeddings` guarda embeddings de perfiles y ofertas con `embedding vector(1024)`.
- El modelo real elegido e integrado es `BAAI/bge-m3`.
- Existe un servicio local Python/FastAPI en `embedding-service`.
- Spring Boot llama al servicio local para generar embeddings reales.
- Ya existen embeddings reales `BAAI/bge-m3` en estado `READY` para ofertas y perfiles.
- Existe busqueda vectorial interna con pgvector usando `embeddingModel=BAAI/bge-m3`.
- `fake-deterministic-1024` existe solo como infraestructura/test y no debe interpretarse como compatibilidad profesional real.
- El matching por reglas sigue existiendo, pero debe entenderse como baseline historico/demo y fuente auxiliar de senales, no como arquitectura final.

## Enfoque vector-first

La busqueda vectorial debe recuperar ofertas semanticamente cercanas al perfil/CV usando `BAAI/bge-m3` y pgvector. Encima de esa recuperacion se agregaran senales estructuradas para explicar mejor los resultados:

- rol detectado;
- seniority estimado;
- skills coincidentes;
- skills faltantes;
- evidencia del perfil;
- brechas criticas y secundarias;
- transferibilidad de habilidades;
- explicacion entendible para el usuario.

La primera etapa recomendada es `VECTOR_FIRST_WITH_EXPLANATION`: mantener el ranking vectorial como orden principal y enriquecer cada resultado con analisis, gaps, evidencia y recomendaciones. Una etapa posterior puede evolucionar hacia `VECTOR_FIRST_WITH_RERANKING`, con reranking trazable por senales estructuradas.

## Stack

- Java 25
- Spring Boot
- Spring Data JPA
- Thymeleaf
- PostgreSQL
- pgvector
- Flyway
- Maven Wrapper
- Python/FastAPI para el servicio local de embeddings
- `BAAI/bge-m3` como modelo real de embeddings

## Requisitos

- JDK compatible con la version configurada en `pom.xml`.
- Docker para PostgreSQL local con pgvector.
- Python 3.11 o superior recomendado para `embedding-service`.
- Navegador Chromium/Chrome para cargar la extension local si se desea capturar ofertas.

## Ejecucion local

PostgreSQL es el perfil por defecto. Antes de correr la app normalmente, levantar la base local:

```powershell
docker compose up -d
```

Luego iniciar la aplicacion desde la raiz del proyecto:

```powershell
.\mvnw.cmd spring-boot:run
```

Para correr explicitamente con PostgreSQL:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=postgres"
```

La aplicacion queda disponible en:

```text
http://localhost:8081
```

## Servicio local de embeddings

El servicio local esta en `embedding-service/` y expone embeddings densos de dimension `1024` para `BAAI/bge-m3`.

Instalacion inicial:

```powershell
cd .\embedding-service
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
pip install -r requirements.txt
```

Levantar el servicio:

```powershell
uvicorn app:app --host 127.0.0.1 --port 8001
```

Verificar estado:

```powershell
Invoke-RestMethod "http://127.0.0.1:8001/health"
Invoke-RestMethod "http://127.0.0.1:8001/model-info"
```

## PostgreSQL local

La guia de PostgreSQL, Flyway, pgvector, backup y restore esta en:

- [docs/postgres-setup.md](docs/postgres-setup.md)

Resumen rapido:

```powershell
docker compose up -d
docker compose stop
docker compose down
```

No usar `docker compose down -v` si queres conservar datos: borra el volumen local de PostgreSQL y elimina ofertas/perfiles cargados.

Datos para DBeaver:

```text
Host: localhost
Port: 5433
Database: datalaburo
User: datalaburo
Password: datalaburo
```

Verificaciones opcionales:

```powershell
docker exec datalaburo-postgres psql -U datalaburo -d datalaburo -c "select installed_rank, version, description, success from flyway_schema_history order by installed_rank;"
docker exec datalaburo-postgres psql -U datalaburo -d datalaburo -c "select extname, extversion from pg_extension where extname = 'vector';"
docker exec datalaburo-postgres psql -U datalaburo -d datalaburo -c "select table_name from information_schema.tables where table_schema = 'public' order by table_name;"
```

## Pipeline vectorial

El pipeline vectorial real funciona sobre PostgreSQL + pgvector:

```text
perfil/oferta -> texto normalizado -> embedding-service -> BAAI/bge-m3 -> vector 1024 -> document_embeddings -> pgvector search
```

Endpoints internos principales:

```text
POST /internal/embeddings/backfill/jobs?limit=100
POST /internal/embeddings/backfill/profiles?limit=100
POST /internal/embeddings/process/bge-m3/pending?limit=1
GET  /internal/embeddings/status
GET  /internal/embeddings/vector-search/profiles/{profileId}/jobs?limit=20&embeddingModel=BAAI/bge-m3
```

Detalle tecnico:

- [docs/embeddings-pipeline.md](docs/embeddings-pipeline.md)

Arquitectura objetivo vector-first:

- [docs/vector-first-compatibility-strategy.md](docs/vector-first-compatibility-strategy.md)

## H2 legacy

H2 fue usado en una etapa inicial del MVP y hoy debe considerarse legacy/obsoleto. No es fallback vigente, no es base local objetivo y no debe guiar la arquitectura del motor de compatibilidad.

Pueden existir archivos o configuraciones historicas relacionadas con H2 para compatibilidad local o tests antiguos, pero la direccion defendible del proyecto es PostgreSQL + pgvector.

## Extension de navegador

La extension se encuentra en `browser-extension/`. Para probarla en Chrome/Chromium:

1. Abrir `chrome://extensions`.
2. Activar modo desarrollador.
3. Elegir "Cargar extension sin empaquetar".
4. Seleccionar la carpeta `browser-extension`.

## Rutas utiles

- `/`: inicio.
- `/jobs`: trabajos cargados.
- `/matching`: matching historico por reglas entre CV y ofertas.
- `/profiles`: perfiles guardados.

## Alcance pendiente

- Implementar endpoint interno vector-first con explicacion.
- Extraer senales estructuradas mas ricas que las reglas actuales.
- Diferenciar evidencia laboral, proyectos, formacion, certificaciones y menciones.
- Agregar analisis de transferibilidad de skills.
- Agregar gap analysis con brechas criticas y secundarias.
- Evaluar manualmente resultados sobre el dataset actual.
- Evolucionar hacia reranking vector-first trazable.

Fuera del alcance inmediato:

- Tocar scraping.
- Tocar extension.
- Tocar captura.
- Reintroducir H2 como fallback.
- Mezclar `fake-deterministic-1024` con `BAAI/bge-m3`.
- Reemplazar la arquitectura por un score hibrido ponderado sin evaluacion.
