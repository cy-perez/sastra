# Modelo de datos

PostgreSQL 17. Migraciones con Flyway en
`backend/bootstrap/src/main/resources/db/migration`.

## Convenciones

- Tablas y columnas en inglés, minúsculas, con guion bajo. Tablas en plural.
- Clave primaria `id` de tipo `uuid`, generada por la aplicación con UUID v7
  (ordenable por tiempo, buen comportamiento en índices).
- `created_at` y `updated_at` de tipo `timestamptz`, siempre en UTC.
- Borrado lógico solo donde el negocio lo exige, con `deleted_at`. En el resto,
  borrado real.
- Dinero: `numeric(12,0)` en pesos colombianos, sin decimales. Jamás `float`.
- Estados en columnas `text` con restricción `check`, no en tipos `enum` de
  PostgreSQL: cambiar un `enum` en producción es doloroso.
- Todo campo con datos personales sensibles se cifra a nivel de aplicación antes
  de guardarse.
- Nada de `ddl-auto`. El esquema lo define exclusivamente Flyway.

## Fase 1

**users**

| Columna | Tipo | Notas |
|---|---|---|
| id | uuid | PK |
| email | citext | único, normalizado |
| email_verified_at | timestamptz | nulo mientras no verifique |
| display_name | text | |
| birth_date | date | RN-008: solo mayores de 18. Se guarda la fecha, no el resultado |
| city | text | opcional. Dato público: sale junto a las publicaciones |
| phone | text | opcional. Dato interno: nunca en un perfil público |
| avatar_url | text | opcional |
| locale | text | `es` o `en` |
| status | text | `ACTIVE`, `BLOCKED`, `CLOSING`, `CLOSED` |
| closed_at | timestamptz | nulo mientras la cuenta siga abierta (V4, criterio 23) |
| created_at, updated_at | timestamptz | |

Una cuenta cerrada conserva la fila anonimizada en vez de borrarse: quedan el
identificador, la fecha de creación y el estado, y el correo se sustituye por uno
del dominio reservado `.invalid` para que RN-001 no impida a esa persona volver a
registrarse con su propia dirección.

**user_roles**: `user_id`, `role` (`BUYER`, `SELLER`, `MODERATOR`, `ADMIN`),
`granted_at`. Clave primaria compuesta.

**user_credentials**: `user_id` (PK y FK), `password_hash` (Argon2id),
`password_updated_at`, `failed_attempts`, `locked_until`.

**refresh_tokens**: `id`, `user_id`, `token_hash` (nunca el token), `family_id`,
`expires_at`, `revoked_at`, `replaced_by`, `user_agent`, `ip_hash`,
`created_at`. El `family_id` permite revocar toda una cadena al detectar
reutilización, y `created_at` es lo que permite mostrarle a alguien desde cuándo
está abierta cada una de sus sesiones.

**verification_tokens**: `id`, `user_id`, `purpose` (`EMAIL_VERIFICATION`,
`PASSWORD_RESET`, `EMAIL_CHANGE`), `token_hash`, `expires_at`, `used_at`,
`created_at`, `new_email`. La fecha de creación es la que permite contar los
reenvíos de la última hora: HU-001 limita a tres y sin ella no hay con qué
contarlos.

`new_email` es la dirección pendiente de confirmar y solo existe en los tokens de
cambio de correo; una restricción `CHECK` obliga a que esté exactamente cuando el
propósito es `EMAIL_CHANGE`. Vive aquí y no en `users` porque el token **es** el
cambio pendiente: caduca con él, se consume con él y desaparece con él. En
`users` habría que limpiarlo cuando el enlace venciera, y nadie limpia lo que
vence solo.

**login_attempts**: `id`, `email_hash`, `ip_hash`, `succeeded`, `created_at`.
Se conserva 90 días.

**consents**: `id`, `user_id`, `document` (`TERMS`, `PRIVACY`), `version`,
`accepted_at`, `ip_hash`. Es la prueba del consentimiento y por eso se guarda la
versión exacta del documento aceptado.

## Fase 2

Las tres primeras ya están aplicadas (`V7` y `V8`, HU-002). El resto de la fase
sigue sin migración y se describe como intención.

**financial_institutions** (`V7`)

| Columna | Tipo | Notas |
|---|---|---|
| code | text | PK. Forma `^[a-z0-9]+(-[a-z0-9]+)*$`, la misma que valida `BankCode` en el dominio |
| name | text | Lo que cambia cuando dos entidades se fusionan. Nunca se guarda en la fila del vendedor |
| kind | text | `BANK` (ahorros y corriente) o `WALLET` (solo depósito electrónico) |
| active | boolean | Una entidad que deja de operar se desactiva, no se borra: hay filas apuntando a ella |
| created_at | timestamptz | |

Es una tabla y no una enumeración del código porque agregar un banco no puede
exigir un despliegue, y porque la Fase 3 necesita el código de cada entidad para
el desembolso.

**seller_verifications** (`V8`)

Una fila por persona (`UNIQUE (user_id)`), no una por intento: los reintentos de
RN-014 se cuentan en `attempts` y sobrescriben la misma fila.

| Columna | Tipo | Notas |
|---|---|---|
| id | uuid | PK |
| user_id | uuid | FK a `users`, único |
| status | text | `NOT_STARTED`, `IN_PROGRESS`, `PENDING_REVIEW`, `VERIFIED`, `REJECTED`, `REVOKED`. Transiciones en RN-059 |
| document_type | text | `CC`, `CE`, `PPT` |
| document_number_cipher | text | AES-256-GCM con nonce y etiqueta (ADR-0020) |
| document_number_key_version | smallint | Con qué versión de clave se cifró |
| document_number_lookup | bytea | HMAC-SHA256 con clave propia, distinta de la de cifrado. Es lo único comparable: el cifrado no se puede indexar |
| document_number_last_four | text | En claro a propósito: es lo único que la pantalla muestra (RN-046) |
| document_holder_name | text | El nombre tal como aparece en el documento |
| document_front_key, document_back_key | text | Claves del cubo privado, no URL (ADR-0018) |
| selfie_key | text | Íd. |
| bank_code | text | FK a `financial_institutions` |
| bank_account_type | text | `SAVINGS`, `CHECKING`, `ELECTRONIC_DEPOSIT` |
| bank_account_cipher | text | Mismo formato de ADR-0020 |
| bank_account_key_version | smallint | |
| bank_account_last_four | text | |
| bank_account_holder_name | text | Debe coincidir con `document_holder_name` (criterio 4) |
| attempts | smallint | RN-014: máximo tres. El cuarto exige que una persona intervenga |
| rejection_reason | text | `ILLEGIBLE_PHOTOS`, `EXPIRED_DOCUMENT`, `HOLDER_MISMATCH`, `DOCUMENT_ALREADY_VERIFIED`, `REQUIREMENTS_NOT_MET` |
| rejection_note | text | Nota libre y opcional del moderador |
| created_at, updated_at | timestamptz | |

No hay `reviewed_by` ni `reviewed_at`: **quién decidió y cuándo vive en
`verification_access_log`**, que es la única fuente de esa información y guarda
además los accesos que no cambian el estado. Duplicarlo en dos sitios dejaría
que se contradijeran.

Tampoco hay `birth_date`: la edad ya está en `users` (RN-008) y no se copia.

**verification_access_log** (`V8`)

| Columna | Tipo | Notas |
|---|---|---|
| id | uuid | PK |
| verification_id | uuid | FK a `seller_verifications` |
| actor_id | uuid | FK a `users`. No admite nulo: un acceso sin actor no es una bitácora |
| action | text | `VIEW_DOCUMENT_FRONT`, `VIEW_DOCUMENT_BACK`, `VIEW_SELFIE`, `VIEW_BANK_ACCOUNT`, `APPROVE`, `REJECT`, `REVOKE` |
| reason | text | Motivo declarado por quien accede. Nunca contiene el dato al que se accedió |
| created_at | timestamptz | |

Es la razón de que la cédula y la selfie no se sirvan por URL firmada: un enlace
que funciona por sí solo no puede registrar quién lo usó (ADR-0018).

**products**: `id`, `seller_id`, `title`, `description`, `category_id`,
`brand`, `condition`, `size_system`, `size_value`, `measurements` (`jsonb`),
`color`, `price`, `weight_grams`, `length_cm`, `width_cm`, `height_cm`,
`created_at`, `updated_at`.

**listings**: `id`, `product_id`, `status`, `published_at`, `sold_at`,
`moderated_by`, `moderated_at`, `rejection_reason`, `version`.
La publicación se separa del producto para que el ciclo de moderación no
contamine los datos de la prenda.

**product_images**: `id`, `product_id`, `object_key`, `position` (0 a 7),
`angle_degrees`, `is_canonical`, `width`, `height`, `bytes`, `content_type`.
Restricción única sobre (`product_id`, `position`).

**categories**: `id`, `parent_id`, `slug`, `name_es`, `name_en`, `size_system`,
`position`.

**moderation_events**: `id`, `listing_id`, `actor_id`, `action`, `reason`,
`notes`, `created_at`.

## Fase 3

**orders**: `id`, `buyer_id`, `seller_id`, `status`, `product_amount`,
`shipping_amount`, `commission_amount`, `total_amount`, `shipping_address`
(`jsonb`, cifrado), `created_at`, `expires_at`.

**order_items**: `id`, `order_id`, `product_id`, `unit_price`, `title_snapshot`,
`image_snapshot`. Los campos con sufijo `snapshot` congelan lo que el comprador
vio: la publicación puede cambiar después, el pedido no.

**payments**: `id`, `order_id`, `provider`, `provider_reference`, `method`,
`status`, `amount`, `raw_response` (`jsonb`), `created_at`, `updated_at`.

**payment_events**: `id`, `payment_id`, `provider_event_id` (único),
`signature_valid`, `payload` (`jsonb`), `processed_at`.
El identificador único del proveedor es lo que garantiza idempotencia.

**payouts**: `id`, `order_id`, `seller_id`, `gross_amount`, `commission_amount`,
`net_amount`, `status`, `released_at`.

**shipping_quotes**: `id`, `order_id`, `carrier`, `service`, `amount`,
`estimated_days`, `quoted_at`, `raw_response` (`jsonb`).

**shipments**: `id`, `order_id`, `carrier`, `tracking_code`, `status`,
`shipped_at`, `delivered_at`.

**order_status_history**: `id`, `order_id`, `from_status`, `to_status`,
`actor_id`, `reason`, `created_at`. Ningún estado cambia sin dejar rastro.

## Índices que no pueden faltar

- `users(email)` único.
- `refresh_tokens(token_hash)` único, y `refresh_tokens(user_id, family_id)`.
- `listings(status, published_at desc)` para el catálogo.
- `products(seller_id)`.
- `product_images(product_id, position)` único.
- `seller_verifications(document_number_lookup)` único **parcial**, solo sobre
  `status = 'VERIFIED'`. Es la lectura literal del criterio 5 de HU-002: dos
  personas pueden tener el mismo documento en revisión —pasa cuando alguien
  intenta usar la cédula de otro— y lo que no puede pasar es que las dos queden
  verificadas. `REVOKED` no bloquea.
- `seller_verifications(updated_at)` parcial sobre `status = 'PENDING_REVIEW'`,
  para la bandeja del moderador.
- `payment_events(provider_event_id)` único.
- `orders(buyer_id, created_at desc)` y `orders(seller_id, created_at desc)`.

## Reglas de integridad

- La suma `product_amount + shipping_amount` debe igualar `total_amount`.
- `commission_amount` debe ser el 5% de `product_amount` redondeado al peso.
  Se guarda calculado, no se recalcula al leer.
- Una publicación en `PUBLISHED` exige exactamente ocho imágenes y cuatro
  canónicas.
- Un pedido no puede referenciar un producto que ya está vendido en otro pedido
  pagado. Se resuelve con bloqueo al confirmar el pago, no con una consulta
  previa optimista.
