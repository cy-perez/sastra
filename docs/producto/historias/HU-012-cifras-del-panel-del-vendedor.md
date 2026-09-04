# HU-012 — Cifras del panel del vendedor

**Fase:** 2 | **Estado:** hecha el 4 de septiembre de 2026
**Reglas que aplica:** RN-061, RN-024

> **Es la mitad que falta de un punto que `alcance.md` marca «hecho a medias».**
> `/mis-publicaciones` llegó con HU-007 y da la lista con el estado de cada una, que es lo
> que hacía falta para retomar un borrador. Lo que no hay es panel: «ni cifras, ni ventas,
> ni el rastro de lo que pasó con cada publicación».
>
> Las ventas son Fase 3 y quedan fuera por alcance. El rastro sale a HU-013, porque
> necesita lectura nueva en el puerto, endpoint y pantalla, y esta historia no necesita
> nada de eso. Aquí van las cifras.

## Objetivo

Quien vende ve de un vistazo cuántas publicaciones tiene en cada estado, sin contar filas
ni recordar cuáles envió.

## Alcance

Entra:

- Una fila de cifras encima de la lista en `/mis-publicaciones`, una por estado de RN-061.
- El cero **se dice**, no se esconde: «0 en revisión» es información, y omitirlo obliga a
  deducir por ausencia.
- El estado de carga, el vacío y el de error de esa fila.

No entra:

- Ventas, ingresos, visitas o cualquier cifra de dinero. Son Fase 3.
- El rastro de lo que pasó con cada publicación. Es HU-013.
- Filtrar la lista por estado y paginarla. Se decidió dejarlas fuera; ver
  «Lo que queda abierto».
- Cifras de otro vendedor, ni agregadas para Sendik. Esto es el panel de quien vende.

## Criterios de aceptación

1. Dado que tengo publicaciones en varios estados, cuando abro `/mis-publicaciones`,
   entonces veo una cifra por cada estado de RN-061 —borrador, en revisión, publicada,
   rechazada, pausada, vendida y archivada— con el número que le corresponde.
2. Dado que no tengo ninguna publicación en un estado, cuando miro su cifra, entonces
   dice cero y sigue estando: no desaparece.
3. Dado que no tengo ninguna publicación, cuando abro la pantalla, entonces las cifras
   están todas en cero y **debajo** aparece el estado vacío que la lista ya tenía; no se
   pintan dos mensajes de vacío distintos.
4. Dado que archivo o pauso una publicación desde la lista, cuando la acción termina,
   entonces las cifras se actualizan sin recargar la página.
5. Dado que las cifras están cargando, cuando miro la pantalla, entonces hay un
   esqueleto y quien usa lector de pantalla oye que están cargando; no aparecen ceros
   provisionales, que se leen como un dato y no como una espera.
6. Dado que la petición de las cifras falla, cuando miro la pantalla, entonces **la lista
   se sigue viendo** y el error se acota a la fila de cifras, con su botón de reintentar.
   Una cifra que no llega no puede tapar las publicaciones.
7. Dado que entro sin sesión a `/mis-publicaciones`, cuando la ruta resuelve, entonces se
   me lleva a entrar, igual que hoy.

## Casos borde

- **Cero absoluto**: cuenta nueva, sin ninguna publicación. Criterio 3.
- **Un estado que no existe en RN-061** llegando del servidor: se ignora, no se pinta una
  cifra sin nombre ni se rompe la fila.
- **La cifra y la lista discrepan**: son dos lecturas y pueden cruzarse con una decisión
  del moderador en medio. Se acepta y se resuelve sola en la siguiente carga; no se
  bloquea la pantalla por cuadrarlas.
- **Números grandes**: se formatean con el separador de la configuración regional, no
  concatenando texto.
- **Doble pulsación** en reintentar: no encadena dos peticiones.

## Diseño

Encima de la lista, antes del `h1` no: después. Se reutiliza la tipografía de rol y los
tokens ya auditados; **ninguna cifra lleva el acento bronce**, que está reservado a la
insignia de vendedor verificado y aparece una vez por pantalla.

Los estados se agrupan visualmente por lo que significan para quien vende —lo que está
en marcha, lo que espera y lo que terminó— pero **cada cifra se lee sola**: el grupo es
disposición, no información, así que no se anuncia como región propia.

En móvil la fila envuelve. Con siete cifras y destino táctil de 44px no caben en una
línea, que es la misma lección de `.paginacion`.

## Notas técnicas

- **Las cifras las cuenta el servidor**, no la pantalla. Contarlas sobre la lista ya
  cargada funcionaría hoy —`api.mias()` trae todo— pero ata la cifra al tamaño de la
  página el día que esa lista se pagine, que es una deuda ya anotada. Endpoint nuevo:
  ~~`GET /api/v1/listings/mine/summary`~~ **`GET /api/v1/users/me/listings/summary`**,
  que responde el conteo por estado.

  **La ruta cambió al implementarla, el 4 de septiembre de 2026.** El prefijo
  `/api/v1/listings/mine` no existe: las publicaciones propias viven bajo
  `/api/v1/users/me/listings` desde HU-007, por lo que argumenta el javadoc de
  `SellerListingsController` —el recurso no es el catálogo sino lo que tiene esta cuenta—
  y de paso heredan la regla de seguridad de `users/**`, que exige token. Colgar la cifra
  de otro sitio habría partido en dos el mismo recurso y la habría dejado fuera de esa
  regla.
- La forma de la respuesta se decide al implementar, pero **no un objeto con una clave por
  estado**: un estado nuevo obligaría a tocar el contrato. Una lista de pares
  `{ status, count }` deja que la pantalla pinte lo que conoce e ignore lo que no.
- Consulta propia y clave propia en TanStack, invalidada por las mismas mutaciones que ya
  invalidan la lista —pausar, reanudar, archivar— que es lo que sostiene el criterio 4.
- Claves de traducción nuevas en `es.json` y `en.json`, bajo `listing.mine.summary`.
- Sin migración: los estados ya están en `listings.status`.

## Pruebas requeridas

- **Dominio o aplicación**: el conteo por estado, incluido el cero, y que solo cuenta las
  del vendedor que pregunta.
- **Persistencia**: el conteo contra PostgreSQL, con publicaciones de dos vendedores
  distintos en la misma tabla; que una no vea las de la otra.
- **Controlador**: la ruta pide sesión; responde 401 sin ella; la forma de la respuesta.
- **Componente**: los siete estados pintados, el cero visible, el esqueleto, el error
  acotado que no tumba la lista (criterio 6), y la actualización tras archivar
  (criterio 4).
- **Extremo a extremo**: no hace falta recorrido nuevo. La cifra es lectura sobre datos
  que las suites existentes ya crean; una aserción en el recorrido de HU-007 basta.
