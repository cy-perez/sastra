# Contrato de la API

Base: `/api/v1`. JSON en UTF-8. Fechas en ISO 8601 con zona UTC.

## Rutas

- Sustantivos en inglés, plural, minúsculas y con guion: `/product-images`.
- Jerarquía solo cuando el recurso hijo no existe sin el padre:
  `/sellers/{sellerId}/products`.
- Sin verbos en la ruta. La única excepción son acciones que no son un recurso:
  `/auth/login` y `/auth/refresh`. Cuando la acción se puede nombrar como
  sustantivo, se nombra así y el método hace el resto: `POST` y `DELETE` sobre
  `/verifications/{id}/approval`, `/listings/{id}/submission` o
  `/listings/{id}/pause`. Aquí figuraba `/listings/{id}/submit-for-review` como
  ejemplo y se corrigió el 24 de agosto de 2026: esa ruta nunca existió y el
  código no usa verbos en ninguna.
- La versión va en la ruta desde el primer día. Un cambio incompatible crea
  `/api/v2`; agregar un campo opcional no lo es.

## Métodos y códigos

| Acción | Método | Éxito |
|---|---|---|
| Listar | GET | 200 |
| Obtener uno | GET | 200 |
| Crear | POST | 201 con cabecera `Location` |
| Reemplazar | PUT | 200 |
| Modificar parcial | PATCH | 200 |
| Eliminar | DELETE | 204 |
| Acción sin cuerpo de respuesta | POST | 202 o 204 |

Excepción a la fila de creación: un endpoint que no puede revelar si el recurso
ya existía responde **202 sin cuerpo y sin `Location`**. La cabecera apuntando al
recurso creado diría que no existía, y su ausencia diría lo contrario, así que los
dos caminos tienen que responder igual. Es el caso de `POST /auth/register`
(criterio 2 de HU-001) y de `POST /auth/forgot-password`.

Errores: 400 formato inválido, 401 sin autenticar, 403 sin permiso, 404 no
existe, 409 conflicto de estado, 422 regla de negocio incumplida, 429 demasiadas
peticiones, 500 error inesperado.

La distinción entre 400 y 422 importa: 400 es "no entiendo lo que enviaste",
422 es "lo entiendo pero el negocio lo rechaza".

## Formato de error

Siempre `ProblemDetail` según RFC 9457, con `Content-Type:
application/problem+json`:

```json
{
  "type": "/errors/auth_email_taken",
  "title": "AUTH_EMAIL_TAKEN",
  "status": 409,
  "instance": "/api/v1/auth/confirm-email-change",
  "code": "AUTH_EMAIL_TAKEN",
  "traceId": "0af7651916cd43dd8448eb211c80319c"
}
```

El `instance` de ese ejemplo no es casual: `AUTH_EMAIL_TAKEN` solo se emite al
**confirmar** un cambio de correo, nunca al registrarse ni al pedir el cambio.
Quien abre el enlace ya demostró que el buzón es suyo, así que no queda nada por
revelar; en el registro, en cambio, ese mismo código convertiría el formulario en
un detector de cuentas y por eso no existe (`ErrorCode`, HU-001 criterio 2).

En un error de validación se agrega `errors`, con una entrada por campo:

```json
{
  "type": "/errors/common_validation_failed",
  "title": "COMMON_VALIDATION_FAILED",
  "status": 400,
  "instance": "/api/v1/auth/register",
  "code": "COMMON_VALIDATION_FAILED",
  "traceId": "0af7651916cd43dd8448eb211c80319c",
  "errors": [
    { "field": "email", "code": "VALIDATION_EMAIL" }
  ]
}
```

El código de cada campo es `VALIDATION_` más el nombre de la restricción que
falló, en mayúsculas: `VALIDATION_EMAIL`, `VALIDATION_NOTBLANK`,
`VALIDATION_SIZE`. Se deriva de la restricción y no se escribe a mano, así que
agregar una anotación de validación agrega su código sin tocar el manejador.

Reglas:

- `code` es un identificador estable. El frontend lo traduce con Transloco. El
  backend nunca envía texto para mostrar al usuario final.
- `errors` solo aparece en errores de validación, con una entrada por campo.
- `traceId` siempre presente y correlacionado con los registros del servidor.
- Un error nunca revela si un correo existe, ni rutas internas, ni trazas de
  excepción.

## Catálogo de códigos de error

Se mantiene en un enum del backend y en el archivo de traducciones del frontend.
Agregar un código exige actualizar ambos en el mismo commit. Prefijos por
contexto: `AUTH_`, `USER_`, `SELLER_`, `CATALOG_`, `ORDER_`, `PAYMENT_`,
`SHIPPING_`, `FILE_`, `COMMON_`.

`FILE_` es de los archivos que sube alguien y no pertenece a un contexto: lo usan
la foto de perfil, el documento de identidad y las tomas de producto por igual
(ADR-0018). Sus tres códigos y el estado con el que salen:

| Código | Estado | Cuándo |
|---|---|---|
| `FILE_TYPE_UNSUPPORTED` | 415 | El contenido no es una imagen aceptada, decidido por los bytes de cabecera y no por la extensión ni el `Content-Type` |
| `FILE_TOO_LARGE` | 413 | Pasa del tope de tamaño |
| `FILE_DIMENSIONS_TOO_SMALL` | 422 | Es una imagen válida pero no llega al mínimo de píxeles |

El 413 tiene su propio estado a propósito: con un 400 genérico el cliente no puede
distinguir «recorta la imagen» de «revisa el formulario».

## Nombres de campos

`camelCase` en el JSON, `snake_case` en la base de datos. La traducción ocurre en
`infrastructure`, no en el cliente.

Dinero: siempre objeto explícito, nunca un número suelto.

```json
{ "price": { "amount": 185000, "currency": "COP" } }
```

`amount` es un entero en la unidad menor sin decimales, porque el peso
colombiano no usa centavos en precios de venta. Esto evita cualquier ambigüedad
sobre si 185000 son pesos o centavos.

## Paginación

Listados de catálogo por cursor, no por número de página. Con contenido que se
inserta constantemente, la paginación por desplazamiento repite y salta
elementos.

```
GET /api/v1/listings?limit=24&cursor=eyJpZCI6...
```

```json
{
  "items": [],
  "nextCursor": "eyJpZCI6...",
  "hasMore": true
}
```

Listados administrativos y de tamaño acotado pueden usar página y tamaño:

```
GET /api/v1/moderation/listings?page=0&size=20
```

```json
{ "items": [], "page": 0, "size": 20, "hasMore": false }
```

Las dos colas del moderador van así, con los mismos nombres: la de publicaciones
en `/moderation/listings` y la de verificaciones de vendedor en `/verifications`.

```
GET /api/v1/verifications?page=0&size=20
```

La segunda no siempre fue así. Pedía `?limite=` —en español, que es lo único del
contrato que no lo estaba— y devolvía una lista pelada, sin desplazamiento: quien
la consumía tenía que aprender una excepción, y no había forma de llegar a una
solicitud que no estuviera entre las primeras. Se alineó al arreglar eso. **El
nombre viejo no se admite**: llega como parámetro desconocido y se ignora, así que
un cliente que no se haya actualizado recibe la primera página en vez de un error.

El campo `hasMore` existe porque **«hay más» no se puede deducir de que la página
venga llena**. La deducción falla justo cuando el total es múltiplo exacto del
tamaño: con veinte pendientes y veinte por página, la última viene llena y la
pantalla ofrecía un «Siguiente» hacia una página vacía. Quien revisa pulsaba, no encontraba nada, y no
podía saber si la cola se acabó o si algo se rompió. El servidor lo resuelve
preguntando si queda alguna después de esta página, que no es lo mismo que contar:
no dice cuántas quedan —eso obligaría a recorrerlas todas— sino si queda alguna.

El tamaño va acotado a 50 y **por encima se rechaza con 400**, no se recorta en
silencio: un cliente que pide 500 y recibe 50 sin que nadie se lo diga cree que ya
tiene todo. Un `page` o un `size` que no sean números también dan 400.

**Y por eso viven en `/moderation` y no en `/listings`.** La cola del moderador y
el catálogo público son dos listas del mismo recurso con paginación distinta, y
juntarlas en una ruta obligaría a que la autorización dependiera de un parámetro
de consulta. El nombre tampoco puede colgar de `/listings/{algo}`: ahí un segmento
literal —`/queue`, `/pending`— compite con la regla que hace pública la lectura de
una publicación por identificador.

## La carrera del refresco

`POST /api/v1/auth/refresh` responde **401 con dos códigos distintos**, y la diferencia
importa:

- `AUTH_SESSION_INVALID` — la sesión no vale. Hay que pedir credenciales otra vez.
- `AUTH_SESSION_RACE` — otra petición del mismo cliente acaba de rotar el token. No hay
  nada roto: **quien reciba esto no debe cerrar la sesión**. La petición falla y la
  siguiente vuelve a renovar.

  **No conviene reintentar al recibirlo.** En esta rama el servidor no manda `Set-Cookie`,
  así que la cookie sigue siendo la que ya se consumió; reenviarla puede caer fuera de la
  ventana y disparar la revocación de la familia y el aviso de seguridad. ADR-0030.

El estado es 401 en los dos casos porque en los dos la petición no fue autorizada, que es
lo que 401 significa. Lo que cambia es qué hacer después.

Sin esa distinción, dos pestañas abiertas a la vez cerraban la sesión de quien no tenía
ningún problema: el servidor ya sabía que era una carrera —por eso no revocaba la familia
ni mandaba el aviso de seguridad— pero respondía lo mismo que ante una sesión muerta.
ADR-0030 lo explica entero.

**El código no dice «reintenta», dice qué pasó.** Reintentar es la reacción, y la reacción
es del cliente.

## Filtros y orden

- Filtros como parámetros planos: `?category=camisas&condition=NEW&minPrice=50000`
- Orden: `?sort=publishedAt,desc`. Solo se permiten campos de una lista blanca.
- Los parámetros no reconocidos se rechazan con 400 en vez de ignorarse en
  silencio: un filtro mal escrito que no filtra es peor que un error.

## Autenticación

- `Authorization: Bearer <token de acceso>` en toda ruta protegida.
- El token de refresco viaja solo en cookie `HttpOnly`, `Secure`,
  `SameSite=Strict`, con ruta limitada a `/api/v1/auth`.
- El token de acceso no se guarda en `localStorage`. Vive en memoria del cliente.
- 401 significa "el token no sirve, refresca". 403 significa "no insistas".

## Idioma

- Cabecera `Accept-Language` con `es` o `en`.
- Solo afecta contenido que el sistema traduce, como nombres de categoría. Los
  mensajes de error se traducen en el cliente a partir del `code`.

## Idempotencia

Toda operación que mueva dinero o cree un recurso costoso acepta la cabecera
`Idempotency-Key`. La misma clave con el mismo cuerpo devuelve el resultado
original en lugar de ejecutar de nuevo.

## Límites de tasa

Se aplican por dirección IP y por usuario. Respuesta 429 con cabecera
`Retry-After`. Los endpoints de autenticación tienen límites más estrictos que
el resto.

## Documentación

OpenAPI generado desde el código con springdoc, servido en `/v3/api-docs` y
`/swagger-ui.html`. En `prod` los dos están apagados
(`application-prod.yaml`): describen la superficie completa del sistema y no
aportan nada a quien compra.

**Estado a agosto de 2026.** La especificación solo existe en tiempo de
ejecución. No se versiona ningún `openapi.json`, los tipos del frontend están
escritos a mano —`features/auth/infrastructure/auth.api.ts`— y la verificación
continua no comprueba el contrato.

Las tres piezas que faltan, si se decide cerrarlas:

1. Una tarea de Gradle que exporte la especificación a
   `docs/arquitectura/openapi.json`.
2. Un paso en `verificacion.yml` que la regenere y falle si el archivo
   versionado no coincide.
3. Generación de los tipos del frontend a partir de ese archivo, que es lo que
   convertiría un cambio de contrato en un error de compilación y no en un fallo
   en tiempo de ejecución.

Mientras no existan, quien cambie un DTO del backend tiene que cambiar a mano su
pareja en el frontend. Es la fuente de desincronización más probable de las dos
mitades, y conviene tenerlo presente antes de que la superficie crezca.
