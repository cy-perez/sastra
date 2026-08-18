# Configuración y variables

Regla general: **nada quemado en el código**. Ninguna URL, ninguna clave, ningún
correo, ningún NIT, ningún porcentaje de comisión.

## Cómo funciona

- El backend declara la configuración en clases `@ConfigurationProperties`
  tipadas y validadas dentro de `infrastructure`. No se usa `@Value` disperso.
- Si falta una variable obligatoria, la aplicación **no arranca**. Es preferible
  fallar al iniciar que descubrirlo con un usuario adentro.
- El frontend recibe su configuración en tiempo de ejecución desde el servidor,
  no incrustada en el paquete compilado. Así el mismo artefacto sirve para `dev`
  y para `prod`.
- Los secretos nunca están en el repositorio. En local, en `.env` ignorado por
  Git. En la nube, en Secret Manager.
- `.env.example` sí se versiona, con todas las claves y valores de ejemplo, nunca
  reales.

## Backend

| Variable | Ejemplo | Obligatoria |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local`, `dev`, `prod` | sí |
| `DB_URL` | `jdbc:postgresql://localhost:5432/sastra` | sí |
| `DB_USERNAME` | `sastra` | sí |
| `DB_PASSWORD` | | sí |
| `SERVER_PORT` | `8080` | no, 8080 por omisión |
| `JWT_ISSUER` | `https://api.sastra.co` | sí |
| `JWT_SECRET` | clave de 256 bits como mínimo | sí |
| `JWT_ACCESS_TTL` | `PT15M` | sí |
| `JWT_REFRESH_TTL` | `P30D` | sí |
| `JWT_REFRESH_GRACE` | `PT10S` | no, `PT10S` por omisión |
| `RATE_LIMIT_CREDENTIALS_MAX` | `10` | no, `10` por omisión |
| `RATE_LIMIT_CREDENTIALS_WINDOW` | `PT1M` | no, `PT1M` por omisión |
| `RATE_LIMIT_SESSION_MAX` | `60` | no, `60` por omisión |
| `RATE_LIMIT_SESSION_WINDOW` | `PT1M` | no, `PT1M` por omisión |
| `RATE_LIMIT_MAX_KEYS` | `50000` | no, `50000` por omisión |
| `APP_BASE_URL` | `https://sastra.co` | sí |
| `APP_API_BASE_URL` | `https://api.sastra.co` | sí |
| `APP_TIME_ZONE` | `America/Bogota` | no, `America/Bogota` por omisión |
| `CORS_ALLOWED_ORIGINS` | lista separada por comas | sí |
| `COMMISSION_RATE` | `0.05` | sí |
| `MAIL_PROVIDER` | `resend` o `console` | no, `resend` por omisión |
| `MAIL_PROVIDER_API_KEY` | clave de Resend, ver ADR-0012 | sí |
| `MAIL_FROM` | `hola@sastra.co` | sí |
| `MAIL_API_URL` | `https://api.resend.com/emails` | no |
| `MAIL_VERIFICATION_PATH` | `/verificar-correo` | no |
| `MAIL_PASSWORD_RESET_PATH` | `/restablecer-contrasena` | no |
| `LEGAL_TERMS_VERSION` | `2026-08-01` | sí |
| `LEGAL_PRIVACY_VERSION` | `2026-08-01` | sí |
| `LEGAL_COOKIES_VERSION` | `2026-08-01` | no, solo frontend |
| `PASSWORD_BREACH_CHECK_ENABLED` | `true` | no, `true` por omisión |
| `PASSWORD_BREACH_CHECK_TIMEOUT` | `PT2S` | no |
| `PASSWORD_BREACH_API_URL` | `https://api.pwnedpasswords.com/range` | no |
| `STORAGE_BUCKET` | `sastra-media-dev` | sí |
| `STORAGE_SIGNED_URL_TTL` | `PT10M` | sí |
| `WOMPI_PUBLIC_KEY` | | Fase 3 |
| `WOMPI_PRIVATE_KEY` | | Fase 3 |
| `WOMPI_EVENTS_SECRET` | | Fase 3 |
| `WOMPI_INTEGRITY_SECRET` | firma de la transacción | Fase 3 |
| `WOMPI_BASE_URL` | permite apuntar a pruebas | Fase 3 |
| `TYPESENSE_HOST` | | Fase 3 |
| `TYPESENSE_PORT` | `8108` | Fase 3 |
| `TYPESENSE_API_KEY` | | Fase 3 |
| `CARRIER_*_API_KEY` | uno por transportadora | Fase 3 |
| `COMPANY_NAME` | `Sastra` | sí |
| `COMPANY_TAX_ID` | `1054994043-1` | sí |
| `COMPANY_ADDRESS` | | sí |
| `SUPPORT_EMAIL` | | sí |

`APP_TIME_ZONE` no es cosmética: RN-008 compara fechas de calendario, no
instantes. Con UTC, alguien en Colombia cumpliría 18 años cinco horas antes de
que aquí sea su cumpleaños.

`JWT_REFRESH_GRACE` es la ventana de gracia de RN-007 (ADR-0014). Durante ese
tiempo después de rotar un token, y sólo mientras el que salió de la rotación
siga sin usarse, recibirlo de nuevo se trata como una carrera entre dos pestañas
y no como reutilización: se rechaza la petición pero no se revoca la familia ni
se avisa al titular. Se mide en segundos porque cubre una ida y vuelta, no una
sesión. Subirla a minutos le regala ese mismo tiempo a un token robado para
reproducirse sin levantar la alarma; ponerla en `PT0S` devuelve el comportamiento
anterior a ADR-0014, con sus falsos avisos de incidente.

Las variables `RATE_LIMIT_*` limitan cuántas peticiones acepta cada origen en las
rutas de cuenta. RN-006 protege una cuenta; esto protege el endpoint, que es otra
cosa: sin ello, cinco intentos por cuenta no impiden probar una contraseña común
contra todas las cuentas que se quiera, ni usar el registro como emisor de correo
gratuito contra la cuota de Resend, ni mantener bloqueada indefinidamente la
cuenta de alguien cuyo correo se conozca.

Son dos grupos porque las rutas no se parecen. `CREDENTIALS` cubre ingreso,
registro, verificación y recuperación, que son actos humanos y poco frecuentes.
`SESSION` cubre refresco y cierre, que los dispara el navegador solo y con varias
pestañas abiertas se repiten sin que nadie haga nada raro. Un límite único
tendría que ser el más flojo de los dos.

**La cuenta es de cada instancia.** Con dos réplicas detrás de un balanceador,
cada una permite el máximo por separado y el límite real se duplica. Es aceptable
mientras el despliegue sea de una sola instancia (ADR-0009); al escalar
horizontalmente el conteo tiene que mudarse a un almacén compartido o al
balanceador.

`MAIL_PROVIDER=console` sustituye el envío real por un adaptador que imprime el
enlace de verificación en el registro de la aplicación. Es lo que permite
recorrer HU-001 entera sin credenciales, y por eso es el valor del perfil
`local`. En `dev` y `prod` no se usa: imprimiría un token de un solo uso, que es
una credencial.

`LEGAL_TERMS_VERSION` y `LEGAL_PRIVACY_VERSION` identifican el texto que la
persona acepta al registrarse. Se guardan con el consentimiento y son la prueba
de a qué dijo que sí: una versión equivocada invalida esa prueba. Cambian cada
vez que se publica un texto nuevo.

**Las lee también el frontend, y tienen que valer lo mismo en los dos.** El
backend las guarda como evidencia y el frontend las usa para elegir qué archivo
de texto sirve: si no coinciden, se muestra un documento distinto del que quedó
escrito y la prueba deja de valer. El nombre del archivo lleva la versión
(`frontend/public/legal/<documento>.<versión>.<idioma>.html`), así que cambiar la
variable sin subir el texto nuevo da un error visible y no un texto viejo con
etiqueta nueva. El procedimiento completo está en
`docs/operacion/textos-legales.md`.

`LEGAL_COOKIES_VERSION` es solo del frontend: nadie consiente cookies en un
formulario, así que el backend no guarda nada de ella y existe únicamente para
versionar el archivo de ese texto.

Si no se declaran, las tres caen en `borrador-local`, que es la versión de los
textos de relleno. No se exigen para no romper el arranque en la máquina de quien
programa; un despliegue que las olvide sirve el borrador, y el borrador dice en
su primera línea que no tiene valor legal, además de que la página muestra un
aviso.

`PASSWORD_BREACH_CHECK_ENABLED` apaga la consulta a Have I Been Pwned
(ADR-0013). Se apaga en las pruebas de extremo a extremo y en desarrollo sin
red; el mínimo de diez caracteres de RN-005 se sigue comprobando siempre, porque
esa regla vive en el dominio y no depende de nadie.

La tasa de comisión es configurable a propósito: es un número de negocio que
puede cambiar, y no debería exigir un despliegue de código.

Los datos de la empresa también: hoy la operación es como persona natural y más
adelante puede constituirse una sociedad. Ese cambio debe ser una variable, no
una búsqueda de texto por todo el repositorio.

## Solo para desarrollo local

Estas dos no las lee la aplicación: las lee `docker-compose.yml` para crear la
base de datos. Viven en el mismo `.env` por comodidad, y en la nube no existen.

| Variable | Ejemplo |
|---|---|
| `DB_NAME` | `sastra` |
| `DB_PORT` | `5432` |

El backend siempre se conecta por `DB_URL`. Si se cambia `DB_PORT`, hay que
cambiar también el puerto dentro de `DB_URL`: son dos valores distintos a
propósito, porque en la nube el contenedor no existe y solo queda la URL.

## Frontend

Las lee el servidor de renderizado y viajan al navegador dentro del HTML, en el
estado transferido. No se compilan dentro del paquete: el mismo artefacto sirve
para `dev` y para `prod`.

| Variable | Ejemplo | Obligatoria |
|---|---|---|
| `API_BASE_URL` | `https://api.sastra.co/api/v1` | sí |
| `NG_ALLOWED_HOSTS` | `sastra.co,www.sastra.co` | sí |
| `DEFAULT_LOCALE` | `es` | no, `es` por omisión |
| `AVAILABLE_LOCALES` | `es,en` | no, `es,en` por omisión |
| `PORT` | `4000` | no, 4000 por omisión |
| `SENTRY_DSN` | opcional | no |
| `ENABLE_DEVTOOLS` | `false` en producción | no |

`NG_ALLOWED_HOSTS` es la lista de dominios a los que el servidor acepta
responder y protege contra falsificación de peticiones del lado del servidor. La
lee `@angular/ssr`, no la aplicación. Merece atención especial porque **si falta,
Angular no falla**: registra un aviso y entrega la página sin renderizar, para
que la pinte el navegador. El sitio parece funcionar mientras el buscador y la
vista previa de WhatsApp reciben un documento vacío, que es justo lo que
ADR-0006 existe para impedir. Por eso `src/server.ts` la exige al arrancar y no
levanta el servidor sin ella.

En local, `npm start` y `npm run build` leen el `.env` de la raíz del repositorio
con `--env-file-if-exists`. Si no existe, el frontend arranca igual y falla al
llamar a la API con un mensaje explícito.

## Banderas de funcionalidad

Se manejan como configuración, no como ramas de Git de larga vida:

| Bandera | Efecto |
|---|---|
| `FEATURE_SELLER_VERIFICATION` | Habilita el flujo de verificación |
| `FEATURE_PUBLISHING` | Habilita publicar prendas |
| `FEATURE_CHECKOUT` | Habilita el proceso de compra |
| `FEATURE_SEARCH` | Habilita la búsqueda con Typesense |
| `FEATURE_SPIN_VIEWER` | Habilita el visor 360 |

Todas apagadas en Fase 1. Permiten desplegar código incompleto sin exponerlo.

## Rotación de secretos

Toda clave se puede rotar sin cambiar código. La clave de firma de tokens admite
dos valores simultáneos durante la rotación, para no invalidar las sesiones
activas de golpe.
