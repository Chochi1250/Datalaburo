# Datalaburo local embedding service

Servicio local minimo para validar `BAAI/bge-m3` antes de integrarlo con Spring
Boot. Expone embeddings densos de dimension `1024` para pruebas locales.

Este servicio no implementa autenticacion, no esta optimizado para produccion y
debe escucharse solo en `127.0.0.1`.

## Requisitos

- Python 3.11 o superior recomendado.
- RAM suficiente para cargar `BAAI/bge-m3`.
- Conexion a internet solo para instalar dependencias y descargar el modelo la
  primera vez, salvo que ya este cacheado localmente.

El texto enviado a `/v1/embeddings` se procesa localmente. No se llama a APIs de
embeddings externas.

## Instalacion

Desde la raiz del repo:

```powershell
cd .\embedding-service
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
pip install -r requirements.txt
```

## Levantar el servicio

```powershell
uvicorn app:app --host 127.0.0.1 --port 8001
```

El primer arranque puede tardar bastante porque descarga y carga
`BAAI/bge-m3`. Si la carga falla, el servicio queda levantado y `/model-info`
muestra `loaded=false` junto con el error.

## Endpoints

### Health

```powershell
Invoke-RestMethod "http://127.0.0.1:8001/health"
```

### Model info

```powershell
Invoke-RestMethod "http://127.0.0.1:8001/model-info"
```

### Embedding

```powershell
$body = @{
  model = "BAAI/bge-m3"
  input = "Desarrollador Java con Spring Boot, PostgreSQL y experiencia en APIs."
  normalize = $true
} | ConvertTo-Json

Invoke-RestMethod `
  -Method Post `
  -Uri "http://127.0.0.1:8001/v1/embeddings" `
  -ContentType "application/json" `
  -Body $body
```

Alternativa con `curl.exe`:

```powershell
curl.exe -X POST "http://127.0.0.1:8001/v1/embeddings" `
  -H "Content-Type: application/json" `
  -d "{\"model\":\"BAAI/bge-m3\",\"input\":\"texto de prueba\",\"normalize\":true}"
```

## Respuesta esperada

```json
{
  "model": "BAAI/bge-m3",
  "dimensions": 1024,
  "embedding": [0.0123, -0.0456],
  "elapsedMs": 1234
}
```

El array real contiene `1024` valores. El servicio valida que no haya `NaN` ni
infinitos.

## Notas

- Usar `BAAI/bge-m3` solamente. Otros modelos se rechazan con `400`.
- Inputs vacios se rechazan con `400`.
- Si el modelo no cargo, `/v1/embeddings` responde `503`.
- Si falla la inferencia o la dimension no es `1024`, responde `500`.
- En CPU la inferencia puede ser lenta; probar primero con textos cortos.
