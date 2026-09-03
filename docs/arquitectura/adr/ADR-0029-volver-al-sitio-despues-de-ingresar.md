# ADR-0029 — Volver al sitio después de ingresar

**Fecha:** 2026-09-02
**Estado:** aceptada

## Contexto

El criterio 8 de HU-011 dice que quien no tiene sesión puede pulsar el control de favorito
en la ficha, se le lleva a entrar, y **cuando termina vuelve a la ficha de esa publicación
con el favorito ya guardado**, sin tener que volver a pulsar.

Hasta hoy el proyecto no sabe hacer ninguna de las dos mitades:

- **No sabe volver.** `LoginPage` navega a `/` siempre, con un comentario que ya anticipaba
  esto: «quien decide a dónde ir después de entrar es la pantalla, y mañana puede ser a la
  dirección que la persona intentaba abrir».
- **No sabe retomar una intención.** `exigirRol` (ADR-0021) redirige a «no encontrado» sin
  cambiar la dirección y no vuelve de ninguna parte, porque su caso es esconder una
  pantalla, no aplazar un gesto.

Y hay una trampa propia de este proyecto: **el token de acceso vive en memoria y se pierde
al recargar** (ADR-0003). La sesión se recupera después, con la cookie de refresco, en el
`provideAppInitializer` que describe `role.guard.ts`. Así que la intención tiene que
sobrevivir a esa recarga y, además, esperar a que la sesión esté resuelta antes de
dispararse: leerla antes encontraría `desconocida` y la descartaría en cada recarga.

No es un problema de los favoritos. Es el primero de una familia —guardar, seguir a un
vendedor, cualquier gesto de Fase 3 que exija cuenta— y la decisión se toma una vez.

## Opciones

**Guardar la intención en la URL del ingreso.** `/ingresar?redirectTo=/producto/x&accion=favorito`.
Ventaja: no toca almacenamiento y sobrevive a la recarga solo. Costo: la acción a ejecutar
queda escrita en una dirección que se comparte, se guarda en el historial y se puede
fabricar. Un enlace que hace que alguien marque algo al entrar es un gesto ejecutado desde
fuera, y aunque hoy marcar un favorito sea inocuo, el mecanismo se reutilizaría para lo que
no lo es.

**Hacerlo todo en memoria, sin almacenamiento.** Es lo más limpio y no funciona: el ingreso
es una navegación entre rutas de la misma aplicación, sí, pero la sesión se recupera con una
recarga de por medio en cuanto alguien llega por su dirección o refresca. El estado en
memoria no sobrevive y el criterio 8 se rompe justo en el caso que más ocurre.

**Guardar la intención en el cliente, y solo el destino en la URL.** La dirección lleva
`redirectTo`, que es a dónde volver; la intención —qué gesto había pendiente— vive en el
navegador y no viaja en ningún enlace.

**IndexedDB, como ADR-0027.** Aquella decisión existe porque se guardan varios megabytes de
datos binarios en un teléfono. Aquí es un identificador y una fecha: usar IndexedDB para eso
es traerse una API asíncrona y su ceremonia para guardar sesenta bytes.

## Decisión

La intención pendiente se guarda en **`sessionStorage`**, y el destino al que volver viaja
como parámetro `redirectTo` en la dirección de `/ingresar`.

## Motivo

**Separadas porque son cosas distintas.** «A dónde vuelvo» es navegación, es inocuo, y tiene
que estar en la URL para que funcione con el botón de atrás y con el historial. «Qué gesto
había pendiente» es una acción, y una acción no se pone en un enlace que se puede compartir
o fabricar.

**`sessionStorage` y no `localStorage`**, que es lo que el proyecto ya usa para el tema y el
idioma. Aquellas son preferencias que deben durar entre visitas; esta es una intención que
debe morir con la pestaña. Con `localStorage`, quien abandona el ingreso y vuelve al sitio
tres días después se encontraría marcando algo que ya no recuerda haber querido.

**Se consume una sola vez y se borra siempre.** Se borra al consumirla, al resolverse la
sesión como anónima —que es lo que significa volver atrás sin entrar, el criterio 9— y por
vencimiento. Lo que la historia advierte es exactamente lo contrario de esto: «hay que
borrarla siempre, o el favorito reaparecerá la próxima vez que alguien entre desde ese
navegador».

**El `redirectTo` se valida y no se obedece.** Solo se admite una ruta relativa que empiece
por `/` y no por `//`. Sin esa comprobación el formulario de ingreso queda convertido en un
redirector abierto: `/ingresar?redirectTo=https://otro-sitio` mandaría a alguien recién
autenticado a un sitio ajeno, con la confianza puesta y con el nombre de Sendik en la barra
de la que viene.

**La regla sigue siendo del servidor.** La intención solo aplaza un gesto; cuando se
dispara, la petición pasa por la misma autorización que si se hubiera pulsado con sesión.
Que el criterio 10 —quien entra resulta ser el dueño de esa publicación— lo resuelva el
servidor con RN-072 y no la pantalla es lo que impide que aplazar un gesto sea una forma de
saltarse una regla.

## Consecuencias

- El ingreso deja de llevar siempre a la portada, y esa es una capacidad nueva del sitio, no
  un detalle de los favoritos. Cualquier pantalla puede pedir la vuelta a la suya.
- Se acepta que una intención pueda perderse: en una pestaña de incógnito que se cierra, o
  si el navegador tiene el almacenamiento bloqueado. El coste es que la persona pulse otra
  vez, y por eso todo acceso a `sessionStorage` se hace tolerante a fallo en vez de asumir
  que responde.
- Se acepta que el mecanismo no sirva entre dispositivos: quien empieza en el teléfono y
  entra en el computador no encuentra nada pendiente. Guardarlo en el servidor sería
  escribir en nombre de alguien que todavía no ha demostrado ser nadie.
- Nada de esto puede tocarse desde el renderizado en servidor. `sessionStorage` no existe
  allí, así que la lectura y la escritura van tras una comprobación de plataforma, como el
  resto del proyecto.

## Cuándo revisar

- **Si aparece una segunda intención aplazada** —seguir a un vendedor, avisar de una bajada
  de precio—, hay que sacar el mecanismo de `features/catalog` a `shared`, porque
  `features/x` no importa de `features/y` y la segunda funcionalidad no podrá reutilizarlo
  donde está hoy.
- **Si alguna intención llega a mover dinero o a cambiar algo irreversible**, esta decisión
  se reabre entera: aplazar un gesto que la persona pulsó antes de autenticarse deja de ser
  aceptable en cuanto el gesto no se puede deshacer con otro clic.
- **Si el ingreso pasa a hacerse con un proveedor externo**, la vuelta ya no es una
  navegación interna y `redirectTo` tendrá que viajar por donde ese proveedor admita, con su
  propia validación.
