# ADR-0021 — Guard de ruta por rol, y qué se renderiza en el servidor

**Fecha:** 2026-08-22
**Estado:** aceptada

## Contexto

HU-006 trae la primera pantalla del sitio que no es para cualquiera: la bandeja donde
el moderador aprueba o rechaza verificaciones de vendedor. Hasta ahora ninguna ruta
del frontend tenía protección propia. `/mi-cuenta` tampoco la tiene: el componente
pide sus datos, el backend responde según la sesión, y si no hay ninguna la pantalla
muestra su estado vacío. Con una pantalla de cuenta eso basta —quien no tiene sesión
no ve nada de nadie— pero aquí no, por dos motivos:

1. El criterio 2 de HU-006 pide que quien no es moderador **no se entere de que la
   bandeja existe**. Una pantalla que se pinta y luego se llena de errores 403 ya
   contó que hay algo detrás.
2. Y hay un problema anterior: el sitio se renderiza en el servidor (ADR-0006). Si
   esta ruta se renderiza como las demás, sus títulos y etiquetas viajan en el HTML
   que recibe **cualquiera** que pida la dirección, con guard o sin él, porque el
   guard corre en el cliente y para entonces el HTML ya salió.

Y hay una restricción que condiciona todo lo demás: **la sesión no existe cuando la
página nace.** El token de acceso vive en memoria y se pierde al recargar; se
recupera después, con la cookie de refresco, en un inicializador que a propósito no
bloquea el arranque. Es la misma trampa que dejó `/mi-cuenta` sin cargarse nunca y
que `account-page.spec.ts` fija como regresión.

## Opciones

**Como `/mi-cuenta`, sin guard.** Coherente con lo que ya hay y sin mecanismo nuevo.
Pero incumple el criterio 2: la pantalla se pinta antes de saber si corresponde.

**Guard que lee el rol al entrar.** Lo obvio, y está roto. En toda recarga la sesión
todavía es `desconocida` y el guard echaría al moderador de su propia bandeja.

**Guard que espera a que la sesión se resuelva, y la ruta fuera del renderizado del
servidor.** Más piezas, y resuelve los tres problemas a la vez.

## Decisión

Un guard `exigirRol(rol)` en `core/session` que **espera** a que la sesión deje de ser
`desconocida` antes de decidir, y las rutas que lo usan declaradas
`RenderMode.Client` en `app.routes.server.ts`.

## Motivo

**La espera es el guard.** No se lee la señal, se escucha hasta que responde. Que la
respuesta llegue siempre lo garantiza el inicializador del navegador, que marca la
sesión como resuelta también cuando no hay ninguna que recuperar. Sin esto el guard
sería peor que no tenerlo: rechazaría a quien sí tiene permiso, en cada F5.

**El renderizado en cliente no es una optimización, es la mitad de la protección.**
Un guard no puede proteger un HTML que se generó antes de que el guard existiera. Y
en el servidor tampoco habría a quién preguntar: allí la sesión se queda en
`desconocida` para siempre, porque el renderizado no tiene la cookie de nadie, así
que un guard que espera se quedaría esperando.

No se pierde nada al no renderizarlas: son pantallas internas detrás de sesión. Lo
que ADR-0006 protege es el posicionamiento del catálogo y del sitio informativo, y
eso aquí no aplica. Esta es la primera excepción a esa ADR y conviene que se lea como
lo que es: una excepción con motivo, no una grieta.

**Al denegar se manda a la página de «no existe», sin cambiar la dirección.** Un «no
tienes permiso» confirma que detrás hay algo. Es la misma razón por la que el backend
responde 404 —y no 403— a las rutas de verificación con la bandera apagada.
`skipLocationChange` deja la dirección escrita tal cual: una redirección normal
sacudiría la barra, y esa sacudida también delata.

**El guard no es la cerradura, y esto es lo que más importa que quede escrito.** La
cerradura es el backend, que responde 403 a quien no tiene el rol aunque llegue por
otro camino, y por partida doble: la regla por ruta de `SecurityConfig` y un
`@PreAuthorize` en cada método. Este guard se puede saltar editando el JavaScript del
navegador; lo que se llevaría quien lo haga es una pantalla vacía y una tanda de 403.
Ese es el reparto correcto: **el cliente decide qué se pinta, el servidor decide qué
se puede.** Un guard al que se le atribuya más que eso es la forma exacta en que
alguien acaba quitando una comprobación del backend porque «ya lo valida el front».

## Consecuencias

- Toda ruta protegida por rol que venga después —panel del vendedor, moderación de
  publicaciones, disputas de la Fase 4— hereda las dos piezas: el guard y el
  `RenderMode.Client`. Poner el guard y olvidar el modo de renderizado deja el
  contenido en el HTML servido, que es el fallo silencioso de esta ADR.
- La primera pintura de estas pantallas es más lenta: no hay HTML previo y hay que
  esperar a la hidratación y a que la sesión se recupere. Es aceptable para una
  herramienta interna que se usa con sesión abierta.
- `exigirRol` recibe el rol como cadena. No hay un tipo de roles compartido en el
  frontend porque el backend los manda así en la sesión; el día que haya más de dos
  pantallas por rol, conviene un tipo.
- Nada de esto cambia la autorización del backend, que sigue siendo la única real.

## Cuándo revisar

- Si aparece una pantalla protegida que **sí** necesita posicionamiento o que se
  comparte por enlace a alguien sin sesión: ahí el renderizado en cliente estorba y
  hay que resolver la sesión en el servidor, que es una decisión mucho más grande.
- Si el servidor llegara a poder resolver la sesión durante el renderizado —hoy no
  puede, la cookie de refresco es `HttpOnly` y el SSR no la usa—, la mitad de esta
  ADR se queda sin motivo.
- Si alguien propone confiar en el guard para algo que el backend no comprueba.
