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
| `APP_BASE_URL` | `https://sastra.co` | sí |
| `APP_API_BASE_URL` | `https://api.sastra.co` | sí |
| `CORS_ALLOWED_ORIGINS` | lista separada por comas | sí |
| `COMMISSION_RATE` | `0.05` | sí |
| `MAIL_PROVIDER_API_KEY` | | sí |
| `MAIL_FROM` | `hola@sastra.co` | sí |
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
