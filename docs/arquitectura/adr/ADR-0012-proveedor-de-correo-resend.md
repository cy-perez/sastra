# ADR-0012 — Resend como proveedor de correo transaccional

**Fecha:** 2026-08-17 · **Estado:** aceptada

## Contexto

HU-001 no se puede implementar sin enviar correo: la verificación de la cuenta,
el aviso de intento de registro sobre un correo ya existente, el bloqueo por
intentos fallidos y la recuperación de contraseña dependen de ello.

Las restricciones son las de la etapa de prototipo descrita en ADR-0009: una
sola persona desarrollando, presupuesto cero, y el dominio `sendik.co` todavía
sin comprar. `docs/operacion/entornos.md` dejaba la elección abierta entre
«Resend o Brevo» y esa indefinición ya bloquea trabajo.

## Opciones

**Resend.** Capa gratuita de 3.000 correos al mes y 100 al día. API de un solo
endpoint. Las plantillas se escriben como HTML en el propio repositorio, así que
viven bajo control de versiones y se revisan en la misma pull request que el
código que las usa. Necesita verificar el dominio; mientras no exista, ofrece un
subdominio de pruebas.

**Brevo.** Capa gratuita de 300 correos al día. Trae además automatizaciones y
campañas de marketing, que hoy no se necesitan. La API es más pesada y el
producto está orientado a que las plantillas se editen en su panel, fuera del
repositorio.

**Amazon SES.** El más barato con volumen y el que mejor encaja el día que todo
esté en la nube. A cambio exige salir del entorno de pruebas con una solicitud
manual, configurar identidades y firmar DKIM antes de mandar el primer correo.

**SMTP propio.** Descartado sin discusión: la entregabilidad de un servidor de
correo recién levantado es mala y mantenerlo es un trabajo en sí mismo.

## Decisión

Resend, detrás de un puerto definido en `application`.

## Motivo

En Fase 1 el volumen es despreciable y las tres opciones viables son gratis. Lo
que las diferencia es el tiempo hasta el primer correo enviado y el costo de
mantener las plantillas, y en las dos cosas gana Resend con claridad: SES pide
un trámite de aprobación antes de servir para nada, y Brevo empuja las
plantillas a su panel, donde dejan de estar versionadas y nadie las revisa.

Que las plantillas sean archivos del repositorio importa más de lo que parece:
un correo de verificación es la primera pieza de interfaz que ve un usuario, y
tiene que pasar por el mismo sistema de marca y el mismo Transloco que el resto.

La decisión se toma barata a propósito. El envío entra por un puerto y el
adaptador de Resend es la única clase que conoce al proveedor: cambiarlo es
reescribir un archivo, no una migración.

## Consecuencias

- Una dependencia externa más en el camino del registro. El fallo de envío no
  debe impedir crear la cuenta: se registra y se ofrece reenviar.
- En desarrollo local no se manda correo real. El adaptador de consola imprime
  el enlace de verificación en el registro de la aplicación, y así HU-001 se
  prueba sin credenciales.
- Hasta comprar el dominio se usa el subdominio de pruebas de Resend. Los
  correos llegarán a spam con más facilidad; es aceptable mientras no haya
  usuarios reales.
- `MAIL_PROVIDER_API_KEY` y `MAIL_FROM` ya están declaradas en
  `docs/operacion/configuracion.md`. No hace falta ninguna variable nueva.
- Ninguna prueba sale a la red: el puerto se simula.

## Cuándo revisar

Al comprar el dominio, para verificarlo y salir del subdominio de pruebas. Y si
el volumen se acerca a los 3.000 mensajes mensuales, momento en el que SES pasa
a ser bastante más barato y el trámite de aprobación ya no es un obstáculo.
