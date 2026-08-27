# ADR-0025 — Datos remotos en el renderizado en servidor

**Fecha:** 2026-08-27
**Estado:** aceptada

## Contexto

Hasta HU-009, **ninguna pantalla del proyecto renderizaba datos remotos en el
servidor**. Las informativas y las legales son texto; la portada es estática; las
de cuenta y las de moderación exigen sesión y por eso se sirven vacías a
propósito (ADR-0021). El renderizado en servidor existía, pero nunca había tenido
que esperar a la API.

El catálogo público rompe eso, y no de forma incidental: es la primera pantalla
que sirve a alguien sin cuenta y **existe para que un buscador la lea**.
`frontend/CLAUDE.md` lo dice sin rodeos —«la ficha de producto y el listado deben
renderizarse en servidor con metadatos completos; de esto vive el posicionamiento
del marketplace»— y ADR-0006 puso el renderizado en servidor justamente ahí.

Al escribir la prueba de extremo a extremo de HU-009 se descubrió que **no
funcionaba**. Angular serializa el HTML en cuanto la aplicación se queda quieta, y
una consulta de TanStack Query en vuelo no la mantiene ocupada por sí sola: el
catálogo, la ficha y el perfil llegaban con su esqueleto de carga y nada más. El
título de la pestaña era el genérico de la ruta, no el del producto.

La restricción de fondo: `@tanstack/angular-query-experimental` sigue siendo
experimental y **no ofrece soporte de renderizado en servidor de primera clase**.
No hay una forma de preguntarle «avísame cuando esta página no tenga nada más que
pedir», que es exactamente lo que hace falta.

## Opciones

**1. Resolvers de ruta con `HttpClient`.** Cargar los datos en un `resolve` de la
ruta. Angular ya espera a los resolvers antes de activar la ruta y ya transfiere
la caché de `HttpClient` al navegador, así que funciona sin tocar nada más.

- A favor: determinista. El router **espera** la promesa; no hay heurística.
- En contra: deja dos formas de cargar datos conviviendo —resolver en unas
  pantallas, store con TanStack en otras— y obliga a que cada pantalla lea su
  estado inicial de la ruta y el resto del store. La duplicidad es justo lo que
  `frontend/CLAUDE.md` evita al exigir que los componentes no vean la librería.

**2. Esperar a las consultas desde `core/query`.** Retener una tarea pendiente de
Angular mientras queden consultas por resolver, y volcar la caché al estado
transferido antes de soltarla.

- A favor: una sola forma de cargar datos en todo el proyecto. Las pantallas no
  cambian y cualquiera que se escriba después hereda el comportamiento correcto
  sin acordarse de nada.
- En contra: **la condición de parada es heurística**. La librería no dice cuándo
  ha terminado, así que hay que deducirlo.

**3. Prerenderizar el catálogo al construir.** Descartada de entrada: el catálogo
cambia cada vez que un moderador aprueba algo, y una versión generada al
construir sería la misma para todo el mundo hasta el despliegue siguiente. Es el
mismo motivo por el que `app.routes.server.ts` no prerenderiza nada.

## Decisión

La opción 2: **el servidor espera a las consultas desde `core/query`**, y lo
consultado viaja al navegador en el estado transferido.

## Motivo

Porque el costo de la opción 1 es permanente y el de la 2 es temporal.

Los resolvers son deterministas, sí, pero parten el proyecto en dos maneras de
cargar datos, y esa división no se va nunca: cada pantalla nueva obliga a elegir,
y la elección se hace mal tarde o temprano. La heurística de la opción 2, en
cambio, vive en un solo archivo de treinta líneas y se retira entera el día que
la librería ofrezca lo que le falta.

Pesó también que la opción 2 arregla las tres pantallas del catálogo a la vez y
deja bien las que vengan, mientras que la 1 hay que aplicarla pantalla por
pantalla —y la que se olvide se sirve vacía sin que nada avise.

## Consecuencias

**Lo que se gana.** El catálogo, la ficha y el perfil se sirven con sus datos y
sus metadatos. El navegador no vuelve a pedir lo que el servidor ya consultó, así
que no hay parpadeo ni una segunda vuelta a la API. Y cualquier pantalla futura
con datos remotos funciona sin hacer nada.

**Lo que se acepta perder.**

- **La condición de parada es heurística y hay que decirlo.** Se espera una vuelta
  para que los componentes se construyan, una gracia corta para que las consultas
  que nacen deshabilitadas se habiliten, y una racha de vueltas sin trabajo para
  cubrir las encadenadas —la ficha pide el vendedor con el identificador que venía
  dentro de la publicación—. Está comentado línea por línea en
  `core/query/query.providers.ts`.
- **Un tope de cinco segundos.** Si la API no responde, el servidor entrega la
  página sin esos datos en vez de quedarse colgado: una petición de renderizado que
  no termina se lleva por delante a quien espera detrás.
- **Una consulta que arranque muy tarde puede no llegar al HTML.** Si una pantalla
  encadena más consultas de las que cubre la racha, las últimas se piden en el
  navegador. Se sigue viendo bien; lo que se pierde es que salgan indexadas.
- **Lo consultado en el servidor viaja en el HTML.** Es lo que hace que el
  navegador no repita, y obliga a lo que ADR-0021 ya exigía: por el estado
  transferido no puede pasar nada privado. Las pantallas de cuenta y de moderación
  se sirven vacías por diseño, así que hoy solo viaja el catálogo, que es público.

## Cuándo revisar

Dos señales, y cualquiera de las dos basta:

- **`@tanstack/angular-query-experimental` deja de ser experimental o publica
  soporte de renderizado en servidor.** Ese día la heurística sobra y se sustituye
  por lo que ofrezca la librería, que sabrá de verdad cuándo ha terminado.
- **Una pantalla necesita encadenar más consultas de las que la racha cubre.** Si
  eso pasa, la respuesta no es subir el número: es que esa pantalla pida lo que
  necesita de una vez, o que se replantee esta decisión.
