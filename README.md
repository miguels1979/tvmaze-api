# TVMaze API

API REST construida con Spring Boot para consultar series desde TVMaze y resolver su detalle mediante Cache-Aside en MongoDB Atlas.
Permite almacenar comentarios con calificaciones e incluirlos en Search y Show.

## Tecnologías

Java 17 · Spring Boot 3.5.16 · Maven · Spring Web · Spring Data MongoDB · MongoDB Atlas · JUnit 5 · Mockito · MockMvc.

## Requisitos y configuración

- JDK 17 o compatible y Maven instalado.
- MongoDB Atlas accesible, con un usuario `readWrite` sobre la base `tvmaze`.
- Acceso de red permitido desde el equipo evaluador y conectividad HTTPS a TVMaze.

`MONGODB_URI` es la única variable obligatoria propia de la aplicación.
Configúrala en el entorno de ejecución (terminal o IntelliJ IDEA) antes de arrancar. La URI debe seleccionar `tvmaze`; plantilla ficticia:

```text
mongodb+srv://<usuario>:<contraseña_codificada>@<host-del-cluster>/tvmaze?retryWrites=true&w=majority&authSource=admin
```

Codifica porcentualmente los caracteres reservados de la contraseña. La aplicación no carga archivos `.env` automáticamente.
Define la variable en la terminal de arranque; sustituye los marcadores únicamente en tu entorno local:

```powershell
$env:MONGODB_URI = 'mongodb+srv://<usuario>:<contraseña_codificada>@<host-del-cluster>/tvmaze?authSource=admin'
```

```bash
# Linux/macOS
export MONGODB_URI='mongodb+srv://<usuario>:<contraseña_codificada>@<host-del-cluster>/tvmaze?authSource=admin'
```

La configuración pública está en `src/main/resources/application.properties`: TVMaze utiliza HTTPS, timeout de conexión de 3 s y de lectura de 5 s.

La regla Atlas `0.0.0.0/0` solicitada por el ejercicio permite conexiones desde cualquier IP y amplía la exposición; sigue requiriendo autenticación.

## Ejecución

Desde la raíz del proyecto:

```bash
mvn -B clean verify
mvn spring-boot:run
```

La aplicación escucha en `http://localhost:8080`. Define `MONGODB_URI` antes del arranque; la suite habitual no requiere Atlas ni credenciales.

## Endpoints

| Método y ruta | Respuesta |
|---|---|
| `GET /api/shows/search?search_query=...` | 200: arreglo con `id`, `name`, `channel`, `summary`, `genres` y `comments`. |
| `GET /api/shows/{show_id}` | 200: JSON completo de TVMaze más `comments`. |
| `POST /api/shows/{show_id}/comments` | 201: objeto con `id` (UUID), `show_id` y `status: "created"`. |

`search_query` es obligatorio y no puede estar en blanco; `show_id` debe ser un entero positivo.
Sin comentarios se devuelve `comments: []`; cada comentario publica exclusivamente `comment` y `rating`.

Cuerpo del POST (`Content-Type: application/json`):

```json
{
  "comment": "Excelente serie",
  "rating": 5
}
```

`comment` es obligatorio, no puede estar en blanco y admite hasta 2000 unidades UTF-16.
`rating` es un número JSON entero de 0 a 5; se rechazan decimales y cadenas.
Se permiten varios comentarios por serie sin verificar su existencia en TVMaze.
Los errores utilizan ProblemDetail: 400, 404, 500, 502, 503 o 504 según la causa.

## MongoDB Atlas

Base `tvmaze`: `shows` almacena el ID original como `_id` y el JSON del proveedor como texto en `showJson`; `comments` almacena UUID, `showId`, `comment` y `rating`.
Cache-Aside consulta MongoDB primero; ante un miss consulta TVMaze e inserta.
El detalle se enriquece sobre una copia sin modificar el `showJson` almacenado.

En **mongosh**, selecciona `tvmaze` y crea o verifica el índice de comentarios:

```javascript
use tvmaze
db.comments.createIndex({ showId: 1 })
db.comments.getIndexes()
```

Debe ser ascendente y **no único**: varios comentarios pueden compartir `showId`.
También puede crearse desde Atlas Data Explorer → comments → Indexes.
Después de consumir los endpoints, verifica la persistencia desde Atlas Data Explorer inspeccionando las colecciones `shows` y `comments`.

## Pruebas

`mvn -B clean verify` ejecuta la suite habitual; la última verificación registró **325 pruebas aprobadas**.
Las clases terminadas en `IT` son integraciones reales con MongoDB y quedan fuera de esa suite.
Selecciona una clase explícitamente en JUnit/IntelliJ, configura `MONGODB_URI` y habilita `-Datlas.it.enabled=true`.
Las integraciones de persistencia crean colecciones temporales; pueden permanecer vacías o conservar datos de prueba si la ejecución o limpieza se interrumpe.

## Seguridad

No versiones credenciales ni publiques `MONGODB_URI` con usuario y contraseña reales. Utiliza variables de entorno y comparte accesos temporales de evaluación únicamente por un canal privado.
