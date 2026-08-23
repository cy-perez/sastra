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

**Sacar la ruta del renderizado del servidor** (`RenderMode.Client`). Es lo que parece
natural para una pantalla interna, y **no funciona en este proyecto**: `APP_CONFIG` llega
por el estado transferido del SSR, así que una ruta que no se renderiza allí arranca sin
configuración y la aplicación no levanta. Se intentó y se descartó con la prueba delante.

**Guard que espera a que la sesión se resuelva, y que deniega en el servidor.** Más
piezas, y resuelve los tres problemas a la vez.

## Decisión

Un guard `exigirRol(rol)` en `core/session` que **espera** a que la sesión deje de ser
`desconocida` antes de decidir en el navegador, y que **deniega siempre en el servidor**.
Las rutas se renderizan en servidor como todas las demás.

## Motivo

**La espera es el guard.** No se lee la señal, se escucha hasta que responde. Que la
respuesta llegue siempre lo garantiza el inicializador del navegador, que marca la
sesión como resuelta también cuando no hay ninguna que recuperar. Sin esto el guard
sería peor que no tenerlo: rechazaría a quien sí tiene permiso, en cada F5.

**Denegar en el servidor no es una limitación: es la mitad de la protección.** Allí la
sesión se queda en `desconocida` para siempre —el renderizado no tiene la cookie de
nadie— así que esperar colgaría el SSR, y dejar pasar metería el título de la pantalla
en el HTML que recibe cualquiera que pida la dirección. Denegar deja servida la página
de «no existe», que es exactamente lo que debe ver quien no tiene el rol.

Al hidratar, el guard vuelve a correr en el navegador, ahí sí espera a la sesión, y quien
tenga el rol entra. El coste es que la página de «no existe» se ve un instante antes de
la bandeja. Se acepta: es una herramienta interna, y ese instante es justo lo que ve
quien no debería pasar.

Esto conserva ADR-0006 intacta —todo se sigue renderizando en servidor— que es
justamente lo que obligó a descartar `RenderMode.Client`: el proyecto **depende** del
SSR para entregar la configuración, no solo para el posicionamiento.

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
  publicaciones, disputas de la Fase 4— basta con que declare el guard. La protección
  del HTML servido va dentro del propio guard, no en una segunda pieza que se pueda
  olvidar. Fue así a propósito: la primera versión repartía la responsabilidad entre el
  guard y el modo de renderizado, y olvidar la mitad dejaba el contenido servido sin que
  nada avisara.
- Quien tiene el rol ve un instante la página de «no existe» antes de su pantalla. Es el
  precio de que el HTML servido no cuente nada.
- El SSR de estas rutas renderiza siempre la página de «no existe», así que su coste de
  servidor es el de esa página y no el de la bandeja.
- `exigirRol` recibe el rol como cadena. No hay un tipo de roles compartido en el
  frontend porque el backend los manda así en la sesión; el día que haya más de dos
  pantallas por rol, conviene un tipo.
- Nada de esto cambia la autorización del backend, que sigue siendo la única real.

## Cuándo revisar

- Si aparece una pantalla protegida que **sí** necesita posicionamiento o que se
  comparte por enlace a alguien sin sesión: servirla siempre como «no existe» deja de
  valer, y hay que resolver la sesión en el servidor, que es una decisión mucho mayor.
- Si el servidor llegara a poder resolver la sesión durante el renderizado —hoy no
  puede, la cookie de refresco es `HttpOnly` y el SSR no la usa—, la mitad de esta
  ADR se queda sin motivo.
- Si alguien propone confiar en el guard para algo que el backend no comprueba.
