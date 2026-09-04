# ADR-0030 — La carrera del refresco se nombra, y no cierra la sesión

**Fecha:** 2026-09-03
**Estado:** aceptada

## Contexto

ADR-0003 fijó la rotación del token de refresco: cada uso emite uno nuevo y consume el
anterior, y si aparece un token ya usado se revoca la familia entera y se avisa al titular.
RN-007 le añadió después una ventana de gracia, porque la cookie es del navegador y no de
una pestaña: dos pestañas que arrancan a la vez mandan el mismo token, la primera lo rota y
la segunda llega con uno recién consumido. Sin la ventana, abrir dos pestañas cerraba la
sesión y disparaba el aviso más importante del sistema sin motivo.

La ventana funciona para lo que se escribió: dentro de ella no se revoca la familia y no se
manda el correo.

**Lo que no se resolvió es qué recibe el cliente.** `RefreshSessionUseCase` lanza
`RefreshTokenInvalidException` en los dos casos —la carrera benigna y el incidente real— y
las dos salen como `AUTH_SESSION_INVALID`. Hacia afuera son el mismo error.

El javadoc del caso de uso asume la conducta que falta: «se rechaza igual, sin revocar, y
el cliente reintenta con la cookie que el navegador ya tiene, que es la buena». El cliente
no reintenta. El `refreshInterceptor` cierra la sesión ante cualquier fallo del refresco, y
no puede hacer otra cosa: con un solo código no tiene con qué decidir.

El efecto es que **una carrera benigna echa a la persona**. Se descubrió persiguiendo una
prueba intermitente —la única de la suite que encadena veintiún registros seguidos, que es
donde más ocasiones hay de que dos peticiones coincidan— pero no es un problema de pruebas:
le pasa igual a quien tenga dos pestañas abiertas.

Dicho de otro modo: la ventana de gracia arregló la mitad del problema que fue a arreglar
—el correo y la revocación— y la otra mitad se quedó fuera porque vive en el cliente y
nadie se la contó.

## Opciones

**A. Un código propio para la carrera, y el cliente deja de cerrar la sesión al verlo.** El
servidor dice cuál de los dos casos es; el cliente falla esa petición pero conserva la
sesión, y la siguiente vuelve a renovar con la cookie ya asentada. Cuesta un código nuevo en
el contrato, su traducción y una rama más en el interceptor.

**B. Que el cliente reintente a ciegas antes de cerrar, sin cambiar el backend.** Más barato
y no toca autenticación. Deja la decisión apoyada en una conjetura del cliente en vez de en
un dato del servidor —el día que alguien lea el interceptor no encontrará por qué
reintenta— y arrastra el riesgo que hizo descartar el reintento también en A: reenviar un
token ya consumido puede caer fuera de la ventana y disparar la revocación de la familia
más el aviso de seguridad. Aquí es peor todavía, porque sin código propio reintentaría
también sobre sesiones muertas.

**C. Que el servidor emita tokens en la carrera en vez de rechazar.** Elimina el problema
de raíz, y también la propiedad que hace útil la rotación: dos secretos válidos a la vez
son exactamente lo que el criterio 15 existe para detectar.

## Decisión

**A.** La carrera benigna responde `AUTH_SESSION_RACE`, distinto de
`AUTH_SESSION_INVALID`, y el cliente **no cierra la sesión** al recibirlo: falla esa
petición y deja que la siguiente renueve.

**Y no reintenta.** Se consideró, y se descartó al revisarlo: en la rama de la carrera el
servidor no manda `Set-Cookie` —lanza antes de emitir nada— así que la cookie del navegador
sigue siendo la que ya se consumió. Reintentar la reenviaría, y si el primer refresco llegó
tarde en la ventana y el reintento tarda un poco, la segunda llegada cae fuera: el servidor
lo lee como reutilización, revoca la familia entera y le manda al titular el correo de «te
copiaron la sesión». Es exactamente el falso incidente que RN-007 existe para evitar,
alcanzable por un camino que antes no existía.

La cookie es `HttpOnly`, así que el cliente no puede comprobar si la que tiene ahora sirve.
Reintentar sería apostar, y se cambia un fallo visible y recuperable por no arriesgar ese
correo.

## Motivo

Porque **quien sabe cuál de los dos casos es, es el servidor**, y ya lo sabe: la distinción
está calculada en `esUnaCarreraDelPropioCliente` y se usa para decidir si revocar. Se está
tirando esa información justo antes de responder.

B funciona y es más barata, pero convierte una regla del contrato en una costumbre del
cliente. Un reintento sin motivo escrito se borra en la primera limpieza que alguien haga
del interceptor, y el defecto vuelve sin que nadie relacione una cosa con la otra —que es
precisamente lo que acaba de pasar con la mitad que faltaba de RN-007.

C se descarta sin dudar: emitir en la carrera pondría dos tokens válidos en circulación y
ADR-0003 está construida sobre que eso no ocurra.

El código nuevo **no dice «reintenta»**: dice qué pasó. Que la respuesta a esa situación
sea reintentar es cosa del cliente, y así el contrato sigue describiendo hechos y no
órdenes.

## Consecuencias

Se gana que una carrera benigna deje de cerrar sesiones, y que el contrato diga lo que
antes solo estaba en un comentario.

Se acepta:

- **Un código más que traducir**, y la regla de `ErrorCode` obliga a agregarlo a los dos
  idiomas en el mismo commit. Aunque en la práctica no debería verse nunca: el cliente lo
  consume y reintenta.
- **Que la petición que perdió la carrera falle.** Quien la hizo ve un error una vez. Es el
  precio de no reintentar, y es barato: la siguiente petición renueva sola.
- **Un oráculo, y conviene llamarlo por su nombre.** `AUTH_SESSION_RACE` solo sale si el
  hash existe en la tabla, si se rotó hace menos de la ventana y si el reemplazo sigue sin
  usarse. No es «información de temporización»: es una **confirmación positiva de que el
  token es auténtico y de que la sesión está viva ahora mismo**, cosa que
  `AUTH_SESSION_INVALID` nunca dio.

  Se acepta igualmente, y por dos razones concretas que conviene tener escritas. **No sirve
  para enumerar**: un token inventado siempre responde `INVALID`, y uno real pero fuera de
  ventana cuesta la revocación de la familia entera más el correo al titular —el oráculo se
  autodestruye al fallar—. Y **no se alcanza sobre una sesión ya revocada**: revocar marca
  también el reemplazo sin usar, así que la condición no se cumple y la respuesta vuelve a
  ser `INVALID`. Una sesión muerta nunca contesta `RACE`.

  Lo que queda expuesto es la ventana en sí, que es de ADR-0014 y no de esta decisión.
- **Que el arranque de la aplicación también tenga que distinguirlo.** Su refresco no pasa
  por el interceptor —este se excluye de las rutas de sesión— así que la comprobación vive
  en dos sitios. Allí tampoco se reintenta: no hay ninguna petición que rehacer.

## Cuándo revisar

Si la ventana de gracia de RN-007 cambia de duración o de condiciones, porque este código
se emite exactamente donde ella decide.

Y si algún día el cliente hace refrescos coordinados entre pestañas —un `BroadcastChannel`,
un bloqueo compartido— la carrera dejaría de ocurrir en el navegador y este código quedaría
solo para los clientes que no coordinen.
