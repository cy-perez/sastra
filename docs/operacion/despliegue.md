# Despliegue: qué hay que crear una vez

El flujo ya está escrito: `.github/workflows/despliegue.yml`. Lo que falta no es
código, son las cuentas y los permisos. Este documento es la lista, en orden, de
lo que hay que crear a mano una sola vez.

La arquitectura y los costos están en `entornos.md`. Aquí solo está el
procedimiento.

> **Antes de desplegar a producción de verdad**, falta una cosa que no es
> técnica: los tres textos legales siguen siendo `borrador-local`, que es relleno
> sin valor legal (`textos-legales.md`). Publicar el registro con un consentimiento
> que apunta a un borrador no es aceptable. Lo demás de esta lista se puede hacer
> ya, y `dev` puede quedar en pie con los borradores.

## Lo que hace el flujo

```
main            -> verificación -> dev  (automático)
etiqueta vX.Y.Z -> verificación -> prod (con aprobación de una persona)
```

El despliegue **llama** a `verificacion.yml` en lugar de repetir sus pasos, así
que nada se publica sin compilar, pasar las pruebas de los cinco módulos, la
cobertura agregada, el linter, las pruebas de accesibilidad y las de extremo a
extremo completas.

**Mientras no haya nada configurado, los dos trabajos de despliegue se omiten** y
la ejecución sale en verde con un aviso que apunta a este documento. Es
deliberado: si fallaran, cada integración a `main` dejaría una ejecución roja, y
una canalización que está roja siempre deja de leerse justo antes del día en que
se rompe de verdad. El interruptor es la presencia de `GCP_PROJECT_ID` y de
`VERCEL_PROJECT_ID`; en cuanto existan, el trabajo correspondiente empieza a
desplegar. Van por separado para poder poner en pie una mitad antes que la otra.

Los secretos no pasan por GitHub Actions. Cloud Run los lee de Secret Manager y
el flujo solo dice qué secreto va en qué variable de entorno. Consecuencia
práctica: rotar la clave de Resend no toca el repositorio, y el registro de una
ejecución no puede contener una contraseña.

## 1. Google Cloud

Crea el proyecto y anota su identificador.

```bash
gcloud projects create sastra-prod --name="Sastra"
gcloud config set project sastra-prod

gcloud services enable \
  run.googleapis.com \
  artifactregistry.googleapis.com \
  secretmanager.googleapis.com \
  iamcredentials.googleapis.com
```

El repositorio de imágenes, en `us-east1` (la región la decide `entornos.md`):

```bash
gcloud artifacts repositories create sastra \
  --repository-format=docker \
  --location=us-east1 \
  --description="Imágenes de Sastra"
```

## 2. La base de datos

En la etapa de prototipo es Neon o Supabase en capa gratuita; al lanzar, Cloud
SQL. Lo único que necesita el backend es la cadena de conexión JDBC, el usuario y
la contraseña.

Crea la base y guarda los tres valores como secretos. **Con Cloud SQL hace falta
además el conector**, y entonces el `DB_URL` cambia de forma; con Neon o Supabase
basta la cadena normal con `sslmode=require`.

## 3. El almacén de archivos

Dos cubos con garantías distintas (ADR-0018): el **público** sirve la foto de perfil
y, en Fase 2, las tomas de producto; el **reservado** guarda la cédula y la selfie,
que no se sirven por ninguna dirección pública (RN-046).

> **El adaptador de Cloud Storage todavía no está escrito.** Necesita la dependencia
> `com.google.cloud:google-cloud-storage`, y `CLAUDE.md` exige una decisión explícita
> antes de agregarla, así que ADR-0018 está en estado **propuesta**. Lo que sigue es
> lo que hay que crear en Google; el código del adaptador entra cuando se apruebe la
> ADR. Hasta entonces `STORAGE_PROVIDER=local` es el único que funciona, y sirve para
> desarrollo pero **no en la nube**: el sistema de archivos de Cloud Run es efímero.

### Los dos cubos

```bash
PROYECTO=sastra-prod
REGION=us-east1

# El público. Acceso uniforme a nivel de cubo, no ACL por objeto: con ACL, un solo
# objeto mal marcado queda expuesto o inaccesible y nadie lo nota.
gcloud storage buckets create gs://sastra-publico   --project=$PROYECTO --location=$REGION --uniform-bucket-level-access

# El reservado. Mismo comando y una diferencia que es todo el punto: este nunca
# recibe el permiso de lectura pública del paso siguiente.
gcloud storage buckets create gs://sastra-reservado   --project=$PROYECTO --location=$REGION --uniform-bucket-level-access
```

Misma región que Cloud Run. Un cubo en otra región se paga en latencia en cada
imagen del catálogo y en tráfico entre regiones.

### Lectura pública, solo en uno

```bash
gcloud storage buckets add-iam-policy-binding gs://sastra-publico   --member=allUsers --role=roles/storage.objectViewer
```

`allUsers` da miedo escrito así y es lo correcto **para este cubo**: son las imágenes
de un catálogo, tienen que verse sin credenciales. Lo que protege lo demás es que
este comando no se ejecuta nunca sobre `sastra-reservado`. Que sean dos cubos y no
dos carpetas del mismo es exactamente lo que permite eso.

Conviene comprobarlo después, porque es el error que no avisa:

```bash
# Debe decir allUsers.
gcloud storage buckets get-iam-policy gs://sastra-publico --format=json | grep allUsers

# Y aquí no debe decir nada. Si dice algo, la cédula de alguien es pública.
gcloud storage buckets get-iam-policy gs://sastra-reservado --format=json | grep allUsers
```

### Los permisos de la aplicación

La cuenta que ejecuta (`sastra-backend`, del paso siguiente) necesita distinto
permiso en cada cubo, y ahí está la diferencia que importa:

```bash
CUENTA=serviceAccount:sastra-backend@$PROYECTO.iam.gserviceaccount.com

# Público: crear y borrar objetos.
gcloud storage buckets add-iam-policy-binding gs://sastra-publico   --member=$CUENTA --role=roles/storage.objectAdmin

# Reservado: lo mismo, y nada más. No se le da `admin` sobre el cubo, así que no
# puede cambiar su política de acceso ni hacerlo público por error.
gcloud storage buckets add-iam-policy-binding gs://sastra-reservado   --member=$CUENTA --role=roles/storage.objectAdmin
```

### Borrado y versiones

**Sin versionado de objetos en ninguno de los dos.** Es lo contrario de lo habitual
y es deliberado: con versionado, borrar un objeto guarda una copia anterior, así que
el derecho de eliminación de la Ley 1581 dejaría la selfie de alguien en una versión
retenida. Cerrar la cuenta borra el archivo y tiene que borrarlo de verdad
(`datos-personales.md`).

Una regla de ciclo de vida sí conviene, para limpiar lo que quede huérfano cuando un
borrado falle:

```bash
cat > ciclo.json <<'JSON'
{
  "rule": [
    {
      "action": {"type": "Delete"},
      "condition": {"daysSinceNoncurrentTime": 1, "isLive": false}
    }
  ]
}
JSON
gcloud storage buckets update gs://sastra-publico --lifecycle-file=ciclo.json
```

### CORS: no hace falta

Las imágenes se cargan con `<img src>`, y eso no está sujeto a CORS. Configurarlo
«por si acaso» abre el cubo a lecturas desde JavaScript de cualquier origen sin que
nadie lo necesite. Si algún día el visor 360 lee píxeles con `canvas`, entonces sí,
y solo para el cubo público y solo para el dominio del sitio.

### Las variables

Cuando el adaptador exista, en el entorno de la nube:

| Variable | `dev` | `prod` |
|---|---|---|
| `STORAGE_PROVIDER` | `gcs` | `gcs` |
| `STORAGE_PUBLIC_BASE_URL` | `https://storage.googleapis.com/sastra-publico-dev` | el dominio del CDN |
| `STORAGE_LOCAL_PATH` | no se usa con `gcs` | ídem |

En producción conviene que `STORAGE_PUBLIC_BASE_URL` sea un dominio propio detrás
del CDN y no `storage.googleapis.com`: la dirección de cada imagen queda escrita en
el HTML que sirve el renderizado, y cambiar de proveedor después obliga a que todas
esas direcciones sigan resolviendo.

## 4. Los secretos

Seis secretos, con estos nombres exactos porque son los que nombra el flujo:

```bash
crear_secreto() {
  printf '%s' "$2" | gcloud secrets create "$1" --data-file=- --replication-policy=automatic
}

crear_secreto sastra-db-url      'jdbc:postgresql://HOST:5432/sastra?sslmode=require'
crear_secreto sastra-db-username 'sastra'
crear_secreto sastra-db-password 'LA-CONTRASEÑA'
crear_secreto sastra-jwt-issuer  'https://sastra.co'
crear_secreto sastra-mail-api-key 're_LA-CLAVE-DE-RESEND'

# La clave de firma de los tokens. Se genera, no se elige: 32 bytes de verdad.
crear_secreto sastra-jwt-secret "$(openssl rand -base64 48)"
```

`JWT_SECRET` pide mínimo 32 caracteres y lo valida al arrancar. Cambiarlo
invalida todos los tokens de acceso emitidos: la gente tiene que volver a entrar.
No es motivo para no rotarlo, sí para hacerlo a una hora tranquila.

## 5. Las dos cuentas de servicio

Dos, y no una, porque hacen cosas distintas: una despliega y la otra ejecuta. Si
fueran la misma, la aplicación en marcha tendría permiso para desplegarse a sí
misma.

```bash
PROYECTO=sastra-prod
NUMERO=$(gcloud projects describe $PROYECTO --format='value(projectNumber)')

# La que ejecuta la aplicación. Solo lee secretos.
gcloud iam service-accounts create sastra-backend \
  --display-name="Backend de Sastra en ejecución"

for secreto in sastra-db-url sastra-db-username sastra-db-password \
               sastra-jwt-secret sastra-jwt-issuer sastra-mail-api-key; do
  gcloud secrets add-iam-policy-binding $secreto \
    --member="serviceAccount:sastra-backend@$PROYECTO.iam.gserviceaccount.com" \
    --role="roles/secretmanager.secretAccessor"
done

# La que despliega desde GitHub.
gcloud iam service-accounts create sastra-despliegue \
  --display-name="Despliegue desde GitHub Actions"

for papel in roles/run.admin roles/artifactregistry.writer roles/iam.serviceAccountUser; do
  gcloud projects add-iam-policy-binding $PROYECTO \
    --member="serviceAccount:sastra-despliegue@$PROYECTO.iam.gserviceaccount.com" \
    --role="$papel"
done
```

## 6. Federación de identidades

Es lo que permite que GitHub se autentique **sin ninguna clave JSON guardada en
el repositorio**. Una clave JSON en los secretos de GitHub es una credencial
permanente que no caduca y que cualquiera con acceso al repositorio puede copiar;
la federación entrega un token de minutos, atado a este repositorio.

```bash
gcloud iam workload-identity-pools create github \
  --location=global --display-name="GitHub"

gcloud iam workload-identity-pools providers create-oidc sastra \
  --location=global --workload-identity-pool=github \
  --display-name="Repositorio de Sastra" \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository" \
  --attribute-condition="assertion.repository == 'TU-USUARIO/sastra'"

# Solo este repositorio puede usar la cuenta de despliegue. Sin esta condición,
# cualquier repositorio de GitHub podría pedir el token.
gcloud iam service-accounts add-iam-policy-binding \
  sastra-despliegue@$PROYECTO.iam.gserviceaccount.com \
  --role=roles/iam.workloadIdentityUser \
  --member="principalSet://iam.googleapis.com/projects/$NUMERO/locations/global/workloadIdentityPools/github/attribute.repository/TU-USUARIO/sastra"
```

Cambia `TU-USUARIO/sastra` por el repositorio real en los dos sitios. El
`attribute-condition` es la pieza que importa: es lo que impide que otro
repositorio pida el mismo token.

## 7. Vercel

Crea el proyecto apuntando a la carpeta `frontend/` del repositorio. Anota el
identificador de la organización y el del proyecto, y genera un token de acceso.

Las variables del frontend se configuran **en Vercel**, no aquí: `API_BASE_URL`,
`COMPANY_*`, `SUPPORT_EMAIL`, `LEGAL_*_VERSION`. La lista completa y qué hace
cada una está en `configuracion.md`.

`vercel.json` ya fija la región `iad1`, que es la misma zona que `us-east1` de
Google: así la llamada del renderizado del servidor a la API no cruza el
continente.

## 8. Los entornos de GitHub

En **Settings → Environments**, dos entornos: `dev` y `prod`.

En `prod`, marca **Required reviewers** y añádete. Ahí vive la aprobación manual
que pide `entornos.md`, y vive ahí a propósito y no en un `if` del flujo: un
archivo se cambia en un commit, un revisor obligatorio no.

Conviene también marcar en `prod` que solo puede desplegarse desde etiquetas.

### Secreto (uno)

| Nombre | Dónde se saca |
|---|---|
| `VERCEL_TOKEN` | Vercel → Account Settings → Tokens |

### Variables

Van como *variables*, no como secretos, porque ninguna lo es: son direcciones y
datos públicos de la empresa. Ponerlas como secretos solo conseguiría que
aparezcan tachadas en los registros cuando haga falta leerlas.

| Variable | `dev` | `prod` |
|---|---|---|
| `GCP_PROJECT_ID` | `sastra-dev` | `sastra-prod` |
| `GCP_REGION` | `us-east1` | `us-east1` |
| `GCP_WORKLOAD_IDENTITY_PROVIDER` | ruta completa del proveedor del paso 5 | ídem |
| `GCP_DEPLOY_SERVICE_ACCOUNT` | `sastra-despliegue@…` | ídem |
| `CLOUD_RUN_SERVICE` | `sastra-backend-dev` | `sastra-backend` |
| `CLOUD_RUN_SERVICE_ACCOUNT` | `sastra-backend@…` | ídem |
| `CLOUD_RUN_MIN_INSTANCES` | `0` | `1` |
| `CLOUD_RUN_MAX_INSTANCES` | `2` | `10` |
| `CLOUD_RUN_MEMORY` | `512Mi` | `1Gi` |
| `APP_BASE_URL` | `https://dev.sastra.co` | `https://sastra.co` |
| `APP_API_BASE_URL` | `https://api-dev.sastra.co/api/v1` | `https://api.sastra.co/api/v1` |
| `CORS_ALLOWED_ORIGINS` | `https://dev.sastra.co` | `https://sastra.co` |
| `SUPPORT_EMAIL` | `soporte@sastra.co` | ídem |
| `MAIL_FROM` | `no-responder@sastra.co` | ídem |
| `COMPANY_NAME`, `COMPANY_TAX_ID`, `COMPANY_ADDRESS` | los reales | ídem |
| `COMMISSION_RATE` | `0.05` | `0.05` |
| `LEGAL_TERMS_VERSION`, `LEGAL_PRIVACY_VERSION` | `borrador-local` hasta que existan los textos | la versión real |
| `STORAGE_PROVIDER` | `gcs` | `gcs` |
| `STORAGE_PUBLIC_BASE_URL` | `https://storage.googleapis.com/sastra-publico-dev` | el dominio del CDN |
| `VERCEL_ORG_ID`, `VERCEL_PROJECT_ID` | los de Vercel | ídem |

`min-instances = 0` en `dev` es lo que hace que no cueste nada: Cloud Run escala
a cero y sin tráfico no cobra. En `prod` se pone 1 para no pagar el arranque en
frío en la primera visita del día, como dice `entornos.md`.

`CLOUD_RUN_MIN_INSTANCES = 0` implica arranque en frío de varios segundos. Es lo
aceptable en la etapa de prototipo.

## 9. Comprobarlo

El primer despliegue conviene hacerlo a `dev` y mirándolo:

```bash
git switch main && git pull
git commit --allow-empty -m "chore: primer despliegue a dev"
git push
```

El flujo hace la comprobación de estado por su cuenta y falla si la aplicación no
responde, así que un trabajo verde significa que responde de verdad y no solo que
`gcloud` no dio error.

Para producción:

```bash
git tag v0.1.0 && git push origin v0.1.0
```

Y entonces hay que entrar a aprobarlo en la pestaña de Actions.

## Volver atrás

Está en `entornos.md`: se redirige el tráfico a la revisión previa de Cloud Run y
es inmediato.

```bash
gcloud run services update-traffic sastra-backend \
  --region us-east1 --to-revisions REVISION-ANTERIOR=100
```

La base de datos no se revierte nunca: se corrige hacia adelante con una
migración nueva. Por eso toda migración destructiva va en dos pasos separados por
al menos un despliegue.

## Lo que este flujo todavía no hace

Se anota para no confundir lo que falta con lo que está:

- **Dominio y certificado.** `sastra.co` no está comprado. Hasta entonces, las
  direcciones son las que Cloud Run y Vercel asignan solas, y las variables de
  arriba llevan esas.
- **Registro y métricas.** Cloud Logging recoge la salida sin configurar nada,
  pero no hay ninguna alerta. Sentry para los errores del frontend está en
  `entornos.md` y aún no está conectado.
- **Respaldos.** Los de la capa gratuita de Neon o Supabase, sin verificar. La
  copia diaria con retención de 7 días que pide `entornos.md` hay que
  comprobarla, y una copia que nadie ha restaurado nunca no es una copia.
