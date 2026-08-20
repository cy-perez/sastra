# ADR-0017 — Una suite de extremo a extremo que cruza las dos mitades

**Fecha:** 2026-08-20
**Estado:** aceptada

## Contexto

`docs/arquitectura/pruebas.md` y las pruebas requeridas de HU-001 pedían extremo a
extremo sobre registro, verificación, ingreso, cierre y recuperación. Lo que
existía era otra cosa: `frontend/e2e/` comprueba el HTML que sale del servidor de
renderizado y, por decisión explícita de su propia configuración, **ninguna de sus
pruebas llama a la API**. Eso está bien y es lo que demuestra ADR-0006, pero deja
los caminos de cuentas probados **por mitades**:

- `presentation` los prueba con MockMvc, sin navegador.
- `bootstrap` los prueba con Testcontainers, sin frontend.
- El frontend los prueba con HTTP simulado, sin backend.

Ninguna de las tres puede ver un contrato roto entre las dos mitades: un nombre de
campo que cambia en un DTO, un código de error que el frontend no traduce, una
cookie con un atributo que el navegador rechaza. Las tres pasan verdes y la
pantalla falla.

Dejó de ser hipotético al escribir esta suite. La primera cosa que encontró fue
que el perfil y la lista de sesiones de `/mi-cuenta` **no se cargaban nunca**: sus
consultas leían la señal de sesión dentro de una función que TanStack invoca fuera
del ámbito reactivo, así que nacían deshabilitadas y no se reactivaban. Ninguna
prueba de componente podía verlo, porque todas ponen la sesión antes de crear el
componente. Y la descarga de datos del criterio 22 omitía ciudad y teléfono, que
también pasaba desapercibido porque nadie miraba qué campos llevaba el archivo.

## Opciones

**Un entorno de desarrollo desplegado y las pruebas contra él.** Es lo que decía
`pruebas.md`. Ventaja: prueba exactamente lo que se publica. Costo: no existe ese
entorno, depende de cuentas que no están creadas, y una suite que solo corre
después de desplegar no protege el pull request que la rompe. Además, una base
compartida entre ejecuciones concurrentes hace las pruebas dependientes entre sí.

**Ampliar las pruebas de `bootstrap` con MockMvc hasta cubrir los flujos.** Ventaja:
baratas y ya hay infraestructura. Costo: no hay navegador, así que no se comprueba
lo único que faltaba —que las dos mitades encajan—. El fallo del perfil no se
habría visto: es de hidratación y de orden de creación de componentes.

**Levantar backend, base y renderizado en la propia canalización y probar por la
interfaz.** Ventaja: encuentra los fallos de contrato y de hidratación, sin
depender de que exista un entorno desplegado. Costo: más lenta, necesita Java y
PostgreSQL en el trabajo de CI, y hay que resolver cómo recuperar el token del
correo.

## Decisión

Una segunda suite, `frontend/e2e-completo/` con `playwright.completo.config.ts`,
que arranca el backend empaquetado, PostgreSQL y el servidor de renderizado, y
recorre los caminos críticos de cuentas por la interfaz. Corre en su propio trabajo
de la canalización, en cada pull request.

## Motivo

Es la única de las tres que ve lo que ninguna otra suite puede ver, y no depende
de infraestructura que todavía no existe. Que corra en el pull request —y no
después de desplegar— es la mitad del valor: protege antes de publicar.

Configuración aparte y no un proyecto más dentro de la existente, por dos razones.
La suite rápida no debe empezar a exigir Java y una base de datos. Y cuando algo
falla importa muchísimo saber si se rompió el renderizado del servidor o el
contrato entre las mitades: son dos trabajos, dos resultados.

**El correo se lee del registro del backend.** Con `MAIL_PROVIDER=console` el
adaptador de consola imprime el enlace entero con el token en claro (ADR-0012). Es
la única forma de completar la verificación sin tocar el código de producción: los
tokens se guardan hasheados con SHA-256, así que ni consultando la base se puede
reconstruir el valor que viaja en el enlace. Eso es exactamente lo que se quiere de
un token. La alternativa era un endpoint que entregara tokens para pruebas, y eso
es código de producción que regala credenciales: ninguna comodidad de pruebas paga
eso.

## Consecuencias

Lo que se gana:

- Los fallos de contrato entre las dos mitades se ven antes de publicar.
- Los criterios de HU-001 que hablan del comportamiento observable —que el enlace
  sirve una sola vez, que la respuesta del registro es indistinguible exista o no
  el correo, que la contraseña anterior deja de servir— se comprueban donde de
  verdad ocurren.

Lo que se acepta perder:

- **Es lenta**: unos 50 segundos de pruebas más el arranque. Por eso solo cubre
  caminos críticos y nunca casos borde; esos siguen siendo unitarios.
- **En serie, con un solo trabajador.** Comparten base de datos y el limitador de
  tasa cuenta por origen: en paralelo se estorban y el fallo que producen no es el
  que buscan.
- **La base no se limpia entre pruebas.** Cada prueba usa un correo único. Limpiarla
  obligaría a la suite a conocer el esquema, que es justo lo que no debe conocer.
- **Depende del formato del registro de consola.** Si cambia cómo `ConsoleMailSender`
  imprime el enlace, la suite deja de encontrar el token. Eso está fijado con una
  prueba propia (`ConsoleMailSenderTest`) para que el formato se rompa donde se
  entiende y no en una suite de navegador.
- **Un desarrollador necesita Docker y el artefacto empaquetado** para correrla en
  local. El comando lo dice y explica cómo cuando falta.
- El limitador de tasa se sube, no se apaga: apagarlo dejaría sin ejercitar un
  interceptor que corre en cada petición de producción.

## Cuándo revisar

Cuando exista el entorno `dev` desplegado. Entonces tiene sentido preguntarse si
esta suite se ejecuta además contra él después de cada integración a `main`, como
humo de despliegue. Lo que **no** debe pasar es que se mueva allí y deje de correr
en el pull request: eso devuelve el problema al punto de partida.

También si la suite pasa de dos minutos. A partir de ahí conviene separar el camino
principal, que corre siempre, de los casos que puedan correr solo en `main`.
