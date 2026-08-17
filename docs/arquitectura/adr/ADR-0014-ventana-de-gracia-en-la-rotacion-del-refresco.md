# ADR-0014 — Ventana de gracia en la detección de reutilización del refresco

**Fecha:** 2026-08-17
**Estado:** aceptada

## Contexto

ADR-0003 fija el refresco rotatorio y RN-007 su consecuencia: si llega un token
de refresco que ya se usó, se revoca la familia completa y se avisa al titular.
La regla es correcta frente a un atacante. El problema es que, tal cual, también
se dispara sin que haya ninguno.

La cookie del refresco no pertenece a una pestaña, pertenece al navegador. Basta
con abrir dos pestañas a la vez, restaurar la sesión del navegador al arrancarlo
o recargar mientras una renovación va por el aire para que dos peticiones salgan
con el mismo token. La primera rota; la segunda llega con uno recién consumido y
el servidor lo lee como reutilización.

El resultado observable no es un inconveniente menor: a la persona se le cierra
la sesión y le llega un correo diciendo que alguien copió su sesión. Ese correo
es el aviso más importante que manda el sistema. Un aviso que se dispara solo
deja de leerse, y deja de leerse justo para el día en que sea verdad.

El candado del cliente no basta por sí solo: no cubre dos navegadores distintos,
ni una recarga que aborta la respuesta antes de que el navegador guarde la cookie
nueva, ni un navegador sin la API de candados.

## Opciones

**Dejarlo como está y arreglarlo solo en el cliente.** Sale gratis en el
servidor, pero la corrección depende de que todos los clientes se porten bien.
Un cliente futuro, o el mismo con la API de candados ausente, vuelve a producir
falsos incidentes. Se descartó: la detección de reutilización es una defensa del
servidor y no puede depender de la cortesía de quien llama.

**Reemitir el mismo token dentro de la ventana.** Es lo más cómodo para el
cliente, que ni se entera de la carrera. Exigiría guardar el valor del token y no
solo su hash, que es exactamente lo que ADR-0003 prohíbe. Descartada sin más.

**Ventana de gracia con doble condición.** Dentro de una ventana corta desde la
rotación, y sólo si el token que salió de esa rotación sigue sin usarse, la
petición se rechaza **sin** revocar la familia ni avisar. Fuera de la ventana, o
si la cadena ya avanzó, se aplica RN-007 sin cambios.

## Decisión

Se adopta la ventana de gracia con doble condición, de diez segundos por
omisión, parametrizable en `JWT_REFRESH_GRACE`. En el cliente, además, el
refresco se serializa entre pestañas con la API de candados del navegador.

## Motivo

Las dos condiciones hacen el trabajo que ninguna hace sola.

El tiempo solo sería un agujero: un ladrón que reprodujera el token en el mismo
segundo entraría sin levantar la alarma. Por eso la ventana se mide en segundos
y no en minutos: cubre una ida y vuelta entre dos peticiones del mismo navegador,
que es lo que dura una carrera de verdad.

El estado del reemplazo solo sería otro agujero, de signo contrario: una sesión
abandonada a medias dejaría su reemplazo sin usar indefinidamente y cualquier
token viejo pasaría por carrera un mes después.

Juntas describen una situación estrecha y reconocible: se rotó hace un instante y
nadie ha seguido la cadena. Eso es la firma de dos peticiones que salieron a la
vez, no la de alguien que ya está dentro con la sesión.

Dentro de la ventana no se emite nada. Se responde 401 igual, sin revocar, y el
cliente reintenta con la cookie que el navegador ya tiene, que es la buena. Es
importante que la gracia no regale sesión: sólo evita el castigo.

## Consecuencias

Se acepta perder diez segundos de detección. Un token robado y reproducido
dentro de ese margen, y sólo si el legítimo aún no ha vuelto a renovar, no
dispara la revocación de familia. Es el precio explícito de la decisión y por eso
el valor es configurable: ponerlo en cero devuelve el comportamiento anterior,
con sus falsos incidentes.

`RefreshTokenRepository` gana un `buscarPorId`, que existe sólo para esta
comprobación. El identificador no viaja a ningún cliente, así que no abre ninguna
forma nueva de pedir un token desde fuera.

Las pruebas del criterio 15 necesitan reutilizar fuera de la ventana. En vez de
dormir la prueba diez segundos, `SessionLifecycleTest` construye una instancia
con la ventana en cero para el camino estricto y usa el bean real para probar la
carrera. Es un caso de uso que se instancia a mano en una prueba, cosa que sólo
se puede hacer porque no lleva anotaciones de framework.

RN-007 se reformula en `docs/producto/reglas-negocio.md` para nombrar la ventana.
La regla de negocio no cambia de intención; cambia de precisión.

## Cuándo revisar

Si aparece un incidente real en el que la ventana haya impedido detectar una
reutilización, se baja a cero y se resuelve la carrera únicamente en el cliente,
asumiendo los falsos positivos.

También si algún día la sesión deja de vivir en una cookie compartida por todas
las pestañas: sin esa cookie compartida desaparece la carrera y la ventana sobra.
