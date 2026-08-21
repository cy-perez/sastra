# ADR-0019 — Un solo hospedaje, elegido al contratar el dominio

**Fecha:** 2026-08-21 · **Estado:** aceptada · **Sustituye a:** ADR-0009

## Contexto

`ADR-0009` decidió hospedaje escalonado: frontend en Vercel durante el prototipo y
migración a Cloud Run al lanzar. Dos decisiones posteriores la dejaron sin sentido,
y ninguna de las dos la contradecía sola:

- **El despliegue está aplazado** hasta que el proyecto esté lo más completo
  posible (`docs/operacion/entornos.md`). Eso elimina la etapa de prototipo
  hospedada, que era la mitad de ADR-0009 y toda la razón de tener dos etapas.
- **Vercel queda descartado** como proveedor. El hospedaje del sitio se contrata
  junto con el dominio, y el proveedor se elige en ese momento.

Sin etapa de prototipo hospedada, el escalonamiento no tiene qué escalonar: no hay
una primera etapa de la que migrar. Y con Vercel fuera, tampoco hay proveedor
intermedio que justificara la migración.

## Decisión

**Una sola etapa de hospedaje, y su proveedor se decide al contratarlo.**

Hasta el lanzamiento, el frontend se ejecuta en local —`npm start`, o el servidor
de SSR con `npm run serve:ssr`— y se prueba en local, integrado contra los
servicios de GCP en capa gratuita que haga falta. No hay sitio publicado.

Lo que **no** cambia y sigue decidido:

| Pieza | Proveedor | Estado |
|---|---|---|
| Backend | Cloud Run | Decidido, sin cambio |
| Imágenes | Cloud Storage | Decidido (ADR-0018) |
| Secretos | Secret Manager | Decidido |
| Base de datos | PostgreSQL gestionado | Decidido el motor, no el proveedor |
| **Hospedaje del sitio** | **Por definir** | **Se elige al contratar el dominio** |

Es decir: GCP sigue siendo el proveedor de los servicios. Lo que queda abierto es
dónde se sirve el frontend con renderizado en servidor.

## Lo que el hospedaje tenga que ser, tendrá que cumplir esto

Esta lista existe para que la elección se pueda hacer después sin volver a
deducirla, y porque estas decisiones vivían dentro de `frontend/vercel.json`, que
se borra con esta ADR. Son requisitos del sitio, no de un proveedor:

- **Ejecución de Node 22** para el renderizado en servidor. No es un sitio
  estático: `server.mjs` resuelve el idioma y el tema antes de pintar
  (`frontend/CLAUDE.md`), así que un hospedaje de archivos no sirve.
- **Cuatro cabeceras de seguridad** en toda respuesta:
  `X-Content-Type-Options: nosniff`, `Referrer-Policy:
  strict-origin-when-cross-origin`, `X-Frame-Options: DENY` y
  `Strict-Transport-Security: max-age=31536000; includeSubDomains`.
- **Dos políticas de caché**: las fuentes de `/fuentes/` con
  `public, max-age=31536000, immutable`, porque llevan huella en el nombre y no
  cambian nunca; los documentos legales de `/legal/` con `max-age=300`, porque un
  cambio de versión legal tiene que llegar pronto y no puede quedarse en una caché
  por un año.
- **Latencia hacia Colombia comparable a `us-east1`**, que es donde está el
  backend. La configuración anterior fijaba la región `iad1` de Vercel justamente
  por eso: para que la llamada del renderizado en servidor a la API no cruzara el
  continente.
- **Configuración por variable de entorno**, sin valores quemados y sin variables
  propias del proveedor.
- **Despliegue desde la integración continua**, publicando el artefacto que ya
  pasó la verificación, no uno que el proveedor construya por su cuenta más tarde.
  El motivo es el de siempre: lo que se publica tiene que salir del mismo commit
  que se probó, con las versiones exactas del `package-lock.json`.

Se conserva, y ahora es más importante que antes, la condición que ADR-0009 puso:
**nada específico del proveedor**. Sin funciones propias, sin su almacenamiento,
sin sus variables mágicas. Antes protegía una migración prevista; ahora protege una
elección que todavía no se ha hecho.

## Motivo

Porque un proveedor intermedio solo se paga con la migración que evita, y ya no
hay ninguna que evitar. Vercel entró en ADR-0009 por dos ventajas concretas
—despliegue inmediato de Angular con SSR y vistas previas por rama para revisar
avances desde el celular—, y las dos servían a una etapa de prototipo que ya no va
a existir: con el despliegue aplazado, no hay nada que previsualizar que no se vea
mejor en local. Lo que quedaba era su costo: una segunda configuración de
despliegue, una segunda cuenta que administrar y una migración pendiente.

Elegir el hospedaje al contratar el dominio también es cuando se puede elegir bien.
Hoy faltan los datos que deciden —qué tráfico hay, qué cuesta el balanceador
frente al hospedaje administrado, si conviene una sola factura—, y una decisión
tomada sin ellos se toma otra vez más tarde.

Frente a fijarlo ya en Cloud Run: sería la respuesta probable y no cuesta nada
dejarla abierta. Un mes antes del lanzamiento se compara con lo que haya, con
precios reales, y si es Cloud Run se escribe entonces. Comprometerlo hoy no
adelanta ningún trabajo, porque el trabajo que adelanta —escribir el despliegue
del frontend— es el mismo que habría que rehacer si la comparación diera otra cosa.

## Consecuencias

- `frontend/vercel.json` se borra. Sus decisiones —cabeceras, caché, región— viven
  en esta ADR como requisitos del hospedaje, no como configuración de un proveedor
  descartado.
- El trabajo `frontend` de `.github/workflows/despliegue.yml` se quita. El flujo
  publica solo el backend hasta que haya proveedor, y el interruptor de «no hay
  nada configurado» pasa a depender únicamente de `GCP_PROJECT_ID`.
- **El sitio no tiene ruta de despliegue mientras esto siga así**, y es
  deliberado. Escribirla exige saber contra qué, y el día que se sepa se escribe
  con los requisitos de arriba en la mano.
- Las variables `VERCEL_ORG_ID`, `VERCEL_PROJECT_ID` y el secreto `VERCEL_TOKEN`
  desaparecen de la lista de lo que hay que crear.
- ADR-0009 queda como registro de lo que se decidió el 15 de agosto de 2026 y por
  qué cambió. No se edita su contenido.

## Cuándo revisar

Al contratar el dominio y el hospedaje, que es cuando esta ADR deja de decir «por
definir» y se convierte en una decisión con nombre de proveedor. Ese día se escribe
el trabajo de despliegue del frontend y se comprueba la lista de requisitos uno por
uno.
