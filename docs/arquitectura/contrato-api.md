# Contrato de la API

Base: `/api/v1`. JSON en UTF-8. Fechas en ISO 8601 con zona UTC.

## Rutas

- Sustantivos en inglés, plural, minúsculas y con guion: `/product-images`.
- Jerarquía solo cuando el recurso hijo no existe sin el padre:
  `/sellers/{sellerId}/products`.
- Sin verbos en la ruta. La única excepción son acciones que no son un recurso:
  `/auth/login`, `/auth/refresh`, `/listings/{id}/submit-for-review`.
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
  "type": "https://sastra.co/errors/email-already-registered",
  "title": "email-already-registered",
  "status": 409,
  "detail": "Codigo interno para trazabilidad, no para mostrar al usuario",
  "instance": "/api/v1/auth/register",
  "code": "AUTH_EMAIL_TAKEN",
  "traceId": "0af7651916cd43dd8448eb211c80319c",
  "errors": [
    { "field": "email", "code": "VALIDATION_ALREADY_EXISTS" }
  ]
}
```

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
`SHIPPING_`, `COMMON_`.

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

Listados administrativos y de tamaño acotado pueden usar página y tamaño.

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

OpenAPI generado desde el código, disponible en `/v3/api-docs` y
`/swagger-ui.html`. El archivo generado se versiona en
`docs/arquitectura/openapi.json` y de él se derivan los tipos del frontend. Si el
contrato cambia y el archivo no se regenera, la verificación continua falla.
