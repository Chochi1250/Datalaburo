# Datalaburo

Datalaburo es mi proyecto de tesis para analizar la compatibilidad entre CVs y ofertas laborales. La aplicacion permite capturar ofertas desde LinkedIn mediante una extension de navegador, almacenarlas en H2, visualizar los trabajos cargados y calcular un ranking de compatibilidad a partir de un CV pegado en la pantalla de matching.

## Estado actual del MVP

- Backend Java con Spring Boot.
- Vistas server-side con Thymeleaf.
- Persistencia con Spring Data JPA y H2 file-based.
- JOBS es la fuente de verdad de ofertas.
- Matching basado en reglas, catalogo de skills y aliases.
- Extension de navegador para capturar ofertas desde LinkedIn.
- Sin PostgreSQL, pgvector, embeddings ni IA en esta etapa.

## Funcionalidades principales

- Captura de ofertas laborales desde LinkedIn.
- Ingesta y guardado de ofertas en H2.
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
- Maven Wrapper

## Requisitos

- JDK compatible con la version configurada en `pom.xml`.
- Navegador Chromium/Chrome para cargar la extension local si se desea capturar ofertas.

## Ejecucion local

Desde la raiz del proyecto:

```powershell
.\mvnw.cmd spring-boot:run
```

La aplicacion queda disponible en:

```text
http://localhost:8081
```

Consola H2:

```text
http://localhost:8081/h2-console
```

Datos de conexion H2 para desarrollo local:

```text
JDBC URL: jdbc:h2:file:./data/datalaburo
User: sa
Password: vacio
```

## Base local

La base H2 se crea localmente en `data/`. Esa carpeta no debe versionarse porque contiene datos locales de ejecucion:

- `data/datalaburo.mv.db`
- `data/datalaburo.trace.db`

Para arrancar con datos limpios, detener la aplicacion y borrar la carpeta `data/` localmente.

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
- Migracion a PostgreSQL.
- Implementacion de pgvector.
- Embeddings.
- Integracion con IA generativa.
