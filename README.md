# Datalaburo

Datalaburo es mi proyecto de tesis para analizar la compatibilidad entre CVs y ofertas laborales. La aplicacion permite capturar ofertas desde LinkedIn mediante una extension de navegador, almacenarlas en PostgreSQL, visualizar los trabajos cargados y calcular un ranking de compatibilidad a partir de un CV pegado en la pantalla de matching.

## Estado actual del MVP

- Backend Java con Spring Boot.
- Vistas server-side con Thymeleaf.
- Persistencia con Spring Data JPA. PostgreSQL es la base principal y H2 queda como fallback/demo local explicito.
- JOBS es la fuente de verdad de ofertas.
- Matching basado en reglas, catalogo de skills y aliases.
- Extension de navegador para capturar ofertas desde LinkedIn.
- PostgreSQL local con Flyway y pgvector preparado para la futura capa vectorial. H2 sigue disponible para demo/fallback. Sin embeddings ni IA generativa en esta etapa.

## Funcionalidades principales

- Captura de ofertas laborales desde LinkedIn.
- Ingesta y guardado de ofertas en PostgreSQL por defecto, o en H2 si se activa el perfil fallback.
- Visualizacion de trabajos cargados.
- Pantalla `/matching` para pegar un CV.
- Ranking de compatibilidad entre CV y ofertas.
- Explicacion del match, afinidades y gaps.
- Catalogo de skills y aliases para normalizar coincidencias.

## Stack

- Java 25
- Spring Boot
- Spring Data JPA
- Thymeleaf
- H2
- PostgreSQL
- Flyway
- pgvector
- Maven Wrapper

## Requisitos

- JDK compatible con la version configurada en `pom.xml`.
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

Para correr explicitamente con H2 fallback:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=h2"
```

La aplicacion queda disponible en:

```text
http://localhost:8081
```

Consola H2, solo cuando la app corre con perfil `h2`:

```text
http://localhost:8081/h2-console
```

Datos de conexion H2 para desarrollo local:

```text
JDBC URL: jdbc:h2:file:./data/datalaburo
User: sa
Password: vacio
```

## Base local H2

La base H2 se crea localmente en `data/` cuando se usa el perfil `h2`. Esa carpeta no debe versionarse porque contiene datos locales de ejecucion:

- `data/datalaburo.mv.db`
- `data/datalaburo.trace.db`

Para arrancar con datos limpios, detener la aplicacion y borrar la carpeta `data/` localmente.

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

## Extension de navegador

La extension se encuentra en `browser-extension/`. Para probarla en Chrome/Chromium:

1. Abrir `chrome://extensions`.
2. Activar modo desarrollador.
3. Elegir "Cargar extension sin empaquetar".
4. Seleccionar la carpeta `browser-extension`.

## Rutas utiles

- `/`: inicio.
- `/jobs`: trabajos cargados.
- `/matching`: matching entre CV y ofertas.
- `/h2-console`: consola H2 local.


## Alcance pendiente

Fuera del alcance de este MVP actual:

- Finalizar sistema de creacion de perfiles
- Ingresar informacion por documentos ( CVs en .docx o .pdf )
- Embeddings.
- Integracion con IA generativa.
