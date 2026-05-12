# PostgreSQL local

Datalaburo puede correr con PostgreSQL local mediante Docker Compose. H2 sigue disponible como fallback para desarrollo rapido.

## Levantar PostgreSQL

Desde la raiz del proyecto:

```powershell
docker compose up -d
```

El servicio usa:

- Imagen: `pgvector/pgvector:pg16`
- Contenedor: `datalaburo-postgres`
- Host: `localhost`
- Puerto host: `5433`
- Base: `datalaburo`
- Usuario: `datalaburo`
- Password: `datalaburo`

## Detener sin perder datos

Para detener la base y conservar el volumen:

```powershell
docker compose stop
```

Tambien se puede remover el contenedor sin borrar el volumen:

```powershell
docker compose down
```

Advertencia: no uses esto si queres conservar datos locales:

```powershell
docker compose down -v
```

`docker compose down -v` borra el volumen `datalaburo-postgres-data` y elimina ofertas, perfiles y cualquier dato local cargado en PostgreSQL.

## Correr la app

Con PostgreSQL:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=postgres"
```

Con H2 fallback:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=h2"
```

La app queda disponible en:

```text
http://localhost:8081
```

## Conexion desde DBeaver

Usar una conexion PostgreSQL con estos datos:

```text
Host: localhost
Port: 5433
Database: datalaburo
User: datalaburo
Password: datalaburo
```

## Verificar Flyway

Con la app corriendo en perfil `postgres`, Flyway valida y aplica las migraciones desde `src/main/resources/db/migration/postgres`.

Para revisar el historial:

```powershell
docker exec datalaburo-postgres psql -U datalaburo -d datalaburo -c "select installed_rank, version, description, success from flyway_schema_history order by installed_rank;"
```

Migraciones esperadas:

- `V1__baseline_schema.sql`
- `V2__enable_pgvector.sql`
- `V3__create_document_embeddings.sql`

## Verificar pgvector

Extension:

```powershell
docker exec datalaburo-postgres psql -U datalaburo -d datalaburo -c "select extname, extversion from pg_extension where extname = 'vector';"
```

Tabla de embeddings:

```powershell
docker exec datalaburo-postgres psql -U datalaburo -d datalaburo -c "select column_name, data_type, udt_name from information_schema.columns where table_name = 'document_embeddings' order by ordinal_position;"
```

La columna de embedding debe estar creada como `vector(1024)`.

## Backup

El script crea `backups/` si no existe y guarda un dump con timestamp.

```powershell
.\scripts\backup-postgres.ps1
```

Ejemplo de salida:

```text
backups\datalaburo-20260512-134500.sql
```

La carpeta `backups/` esta ignorada por Git.

## Restore

Restaurar un backup:

```powershell
.\scripts\restore-postgres.ps1 -File .\backups\datalaburo-20260512-134500.sql
```

Conviene restaurar sobre una base limpia o entender que puede haber conflictos si ya existen datos. Si necesitas limpiar todo antes de restaurar, hacelo con cuidado y solo despues de tener un backup verificado.
