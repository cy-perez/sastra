# ADR-0024 — El sitio se hospeda en Cloud Run; GoDaddy es el registrador y el DNS

**Fecha:** 2026-08-26
**Estado:** aceptada
**Cierra la decisión que dejó abierta:** ADR-0019

## Contexto

ADR-0019 decidió una sola etapa de hospedaje y dejó **una cosa sin decidir a
propósito**: dónde se sirve el frontend con renderizado en servidor. Su regla era
explícita —«se elige al contratar el dominio»— y dejó escrita la lista de lo que
ese hospedaje tendría que cumplir, para que la elección se pudiera hacer después
sin volver a deducirla.

El 26 de agosto de 2026 se contrató **`sendik.co` en GoDaddy**, junto con un plan
de **alojamiento compartido con cPanel**. Con eso, la condición de ADR-0019 se
cumple y toca elegir.

Lo que ya estaba decidido y no se toca: el backend en Cloud Run, las imágenes en
Cloud Storage (ADR-0018), los secretos en Secret Manager y PostgreSQL gestionado.

## Opciones

**A. El frontend en el alojamiento compartido de GoDaddy.** Aprovecha lo
contratado. Se comprobó contra los seis requisitos de ADR-0019 y falla en cinco:

| Requisito de ADR-0019 | Alojamiento compartido de GoDaddy |
|---|---|
| Ejecución de Node 22 | **No de forma nativa.** Su cPanel no trae «Setup Node.js App»; se consigue con SSH, `nvm` y un proxy por `.htaccess`, y muchos planes compartidos no dan terminal |
| Latencia hacia Colombia comparable a `us-east1` | Sin control: la máquina la asigna el proveedor |
| Configuración por variable de entorno | Por `.htaccess` y el panel, no por entorno |
| Despliegue desde la integración continua | Por FTP o SSH, escrito a mano |
| Que no altere las cabeceras de la aplicación | El proxy de `.htaccess` es justamente una capa que las toca |

**B. El frontend en Cloud Run, junto al backend.** GoDaddy queda como registrador
y servidor de DNS. Cumple los seis requisitos sin excepciones y sin trabajo de
adaptación: es un contenedor de Node 22 en la misma región que el backend,
configurado por variables de entorno, desplegado por el mismo flujo que ya publica
el backend, y nada se interpone entre la aplicación y la respuesta.

**C. Un sitio estático en GoDaddy, sin renderizado en servidor.** Encajaría con el
plan contratado, pero exige renunciar al SSR. No es una opción menor: `server.ts`
resuelve el idioma y el tema antes de pintar, manda las cuatro cabeceras de
seguridad y decide la caché de `/fuentes/` y de `/legal/`. Y `frontend/CLAUDE.md`
pide que la ficha de producto y el listado se rendericen en servidor con
metadatos completos, «de esto vive el posicionamiento del marketplace».

## Decisión

**El frontend se hospeda en Cloud Run**, como un segundo servicio junto al del
backend. **GoDaddy es el registrador de `sendik.co` y el servidor de DNS**, y no
ejecuta nada.

## Motivo

Porque el alojamiento compartido no puede ejecutar lo que este frontend es. La
lista de ADR-0019 no era una preferencia: era la descripción de lo que hace falta
para servir este sitio, y el primer punto —«un hospedaje de archivos no sirve»— ya
resolvía la pregunta antes de contratar nada.

Elegir Cloud Run además **no agrega un proveedor**: quita el que sobraba. Los
servicios ya están en GCP, el flujo de despliegue ya publica ahí, la región ya está
elegida y el certificado lo gestiona Google sin costo. El frontend pasa a ser un
segundo `gcloud run deploy` en el mismo archivo, con el mismo commit verificado y
la misma forma de configurarse.

**Lo que GoDaddy hace bien es lo que se le deja.** Registrar el dominio y servir
el DNS son exactamente su trabajo, y para eso el plan compartido no estorba.

## Consecuencias

**Se gana** un solo proveedor de ejecución, un solo flujo de despliegue, una sola
forma de configurar, escalado a cero en las dos piezas y certificados gestionados.
Y se gana que el sitio de `dev` sea el mismo artefacto que se probó, cosa que por
FTP no se puede prometer.

**Se acepta perder** el uso del alojamiento contratado, que queda pagado y sin
usar. Es dinero ya comprometido y no se recupera montando encima algo que no
encaja: sostener el SSR ahí costaría más —en configuración frágil y en tiempo de
operación— que el propio plan. Si se quiere aprovechar, sirve para algo que sí sea
suyo: una página de mantenimiento o un dominio de correo.

**Se acepta también** el arranque en frío. Cloud Run escala a cero y la primera
petición tras un rato inactivo tarda unos segundos. Para `dev` es aceptable y es
justo lo que lo mantiene en cero pesos; al lanzar se configura una instancia mínima
siempre activa, que es una decisión que ya estaba tomada en `entornos.md`.

**El DNS pasa a ser parte del despliegue.** Antes no había registros que mantener;
ahora `docs/operacion/despliegue.md` lleva los de GoDaddy y su verificación.

## Cuándo revisar

Si el arranque en frío deja de ser tolerable en `dev` antes de tener instancia
mínima, o si aparece una razón para servir el sitio desde fuera de GCP —una CDN
propia, un requisito de residencia de datos—. También si algún día el frontend deja
de necesitar renderizado en servidor, que hoy es lo que descarta el alojamiento
compartido: esa decisión tendría que venir antes, con su propia ADR, y entonces
esta se puede reabrir.
