# ADR-0013 — Contraseñas filtradas con Have I Been Pwned y k-anonimato

**Fecha:** 2026-08-17 · **Estado:** aceptada

## Contexto

RN-005 exige rechazar una contraseña «si aparece en la lista de contraseñas
filtradas conocidas», y deliberadamente no exige símbolos ni mayúsculas: la
longitud protege más que la complejidad artificial. Esa regla solo tiene sentido
si existe una lista contra la que comparar, y hasta ahora no se había decidido
cuál.

La comprobación ocurre en el registro y en el cambio de contraseña, es decir en
un camino donde una persona está esperando respuesta.

## Opciones

**Have I Been Pwned con k-anonimato.** Se calcula el SHA-1 de la contraseña, se
envían **solo los cinco primeros caracteres** del hash y el servicio devuelve
todos los sufijos que empiezan así, unos cientos. La comparación final ocurre en
nuestro servidor. Más de mil millones de credenciales, sin clave de API y sin
costo.

**Lista local empaquetada.** Las cien mil peores contraseñas en un archivo dentro
del artefacto, comprobación en memoria. Sin latencia y sin terceros, pero con una
cobertura cuatro órdenes de magnitud menor y que envejece salvo que alguien se
acuerde de actualizarla.

**No comprobar.** Incumple RN-005.

## Decisión

Have I Been Pwned mediante el protocolo de k-anonimato, detrás de un puerto en
`application`, con tiempo de espera corto y **fallo abierto**.

## Motivo

La objeción evidente es la privacidad, y el k-anonimato la responde: nunca sale
la contraseña, y tampoco su hash completo. Salen cinco caracteres hexadecimales,
que corresponden a decenas de miles de contraseñas distintas. El servicio no
puede saber cuál se estaba comprobando ni de qué usuario.

Conviene dejar escrito que el uso de SHA-1 aquí no es un descuido: es el
protocolo que define ese servicio y solo sirve para consultar. El
almacenamiento de contraseñas es Argon2id, según `docs/arquitectura/modelo-datos.md`,
y eso no cambia.

Frente a la lista local, la diferencia de cobertura no es un matiz. Una lista de
cien mil entradas atrapa `123456789` y poco más; el problema real son las
contraseñas razonables que ya se filtraron en alguna brecha ajena y que su dueño
sigue reutilizando. Esas solo aparecen en un corpus grande.

**El fallo es abierto y es una decisión consciente.** Si el servicio no responde
dentro del tiempo de espera, la contraseña se acepta y el evento se registra.
Bloquear un registro legítimo porque un tercero se cayó convierte la
disponibilidad de otro en la nuestra, y el mínimo de diez caracteres de RN-005
sigue aplicándose siempre porque se comprueba en el dominio, sin salir a la red.

## Consecuencias

- Una llamada externa en el registro y en el cambio de contraseña. Con tiempo de
  espera corto y sin reintentos: si no responde rápido, no responde.
- Una contraseña filtrada puede colarse durante una caída del servicio. Es el
  precio explícito del fallo abierto.
- El comportamiento es parametrizable: `PASSWORD_BREACH_CHECK_ENABLED` permite
  apagarlo, y así las pruebas de extremo a extremo y el desarrollo local no
  dependen de la red. Hay que agregarla a `docs/operacion/configuracion.md` y a
  `.env.example`.
- Ninguna prueba sale a la red: el puerto se simula, incluido el caso de tiempo
  agotado.
- El mensaje al usuario distingue cuál de las dos reglas falló, como pide el
  criterio 3 de HU-001, así que hacen falta dos códigos de error distintos.

## Cuándo revisar

Si el servicio empieza a exigir clave de API o pasa a ser de pago, o si aparece
evidencia de que el fallo abierto se está explotando, momento en el que la salida
es una lista local de respaldo para cuando el servicio no conteste, y no cerrar
el registro.
