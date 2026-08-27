# Imagen del frontend con renderizado en servidor, para Cloud Run
# (docs/operacion/entornos.md, ADR-0024).
#
# Dos etapas. La primera construye con el Node completo y las dependencias de
# desarrollo; la segunda solo lleva el artefacto y las dependencias de ejecucion,
# sin el codigo fuente, sin Angular CLI y sin el cache de npm.
#
# No es un sitio estatico y por eso no basta con servir `browser/`: `server.mjs`
# resuelve el idioma y el tema antes de pintar, manda las cuatro cabeceras de
# seguridad y decide la cache de `/fuentes/` y de `/legal/` (ADR-0019). Un
# hospedaje de archivos serviria el HTML sin nada de eso.

# ---------- Construccion ----------
FROM node:22-bookworm-slim AS construccion

WORKDIR /origen

# Primero solo lo que define las dependencias. Mientras esto no cambie, Docker
# reutiliza la instalacion entera aunque el codigo cambie en cada commit.
COPY package.json package-lock.json ./

# `npm ci` y no `npm install`: instala exactamente lo que fija el lock, que es lo
# mismo que verifico la canalizacion. Con `install`, una version nueva compatible
# puede colarse entre la verificacion y la publicacion.
RUN npm ci

COPY angular.json tsconfig.json tsconfig.app.json ./
COPY public/ public/
COPY src/ src/

# Aqui no se ejecutan las pruebas: son un paso propio de la canalizacion y ya
# corrieron antes de llegar a construir la imagen. Repetirlas dentro las pagaria
# dos veces sin que nadie viera el resultado.
RUN npm run build

# Las dependencias de ejecucion, aparte y sin las de desarrollo. El artefacto de
# Angular deja las de node fuera del paquete, asi que `server.mjs` las necesita en
# tiempo de ejecucion.
RUN npm ci --omit=dev

# ---------- Ejecucion ----------
FROM node:22-bookworm-slim

# Sin privilegios. Cloud Run no lo exige, y una aplicacion que no necesita ser root
# no debe serlo: si alguien consigue ejecutar algo dentro, la diferencia importa.
# La imagen de node ya trae el usuario `node`, sin necesidad de crear otro.
WORKDIR /aplicacion

COPY --from=construccion --chown=node:node /origen/node_modules/ ./node_modules/
COPY --from=construccion --chown=node:node /origen/dist/ ./dist/

USER node

ENV NODE_ENV=production

# Documenta el puerto por omision. El de verdad lo decide Cloud Run con PORT, que
# `src/server.ts` ya lee.
EXPOSE 4000

# Sin capa de shell: asi el proceso de Node es el PID 1 y recibe directamente la
# senal de apagado que manda Cloud Run al retirar una revision.
CMD ["node", "dist/sendik/server/server.mjs"]
