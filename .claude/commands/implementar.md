---
description: Implementa una historia de usuario completa, en fases y con revision
argument-hint: [numero de historia, por ejemplo HU-001]
---

Implementa $ARGUMENTS siguiendo este orden. No te saltes pasos.

**1. Contexto.** Lee la historia en `docs/producto/historias/`, mas
`docs/arquitectura/vision-tecnica.md`, `docs/arquitectura/contrato-api.md`,
`docs/arquitectura/modelo-datos.md` y el `CLAUDE.md` del lado que vayas a tocar.

**2. Plan.** Antes de escribir una sola linea, presenta:

- Los archivos que vas a crear o modificar, por capa.
- El modelo de dominio: entidades, objetos de valor, estados y transiciones.
- Los endpoints con su contrato de entrada y salida.
- La migracion de Flyway si el esquema cambia.
- Las claves de Transloco nuevas, en es y en en.
- Las decisiones que requieran una ADR.

Detente y espera aprobacion.

**3. Implementacion, de adentro hacia afuera.** Dominio primero con sus pruebas,
luego casos de uso, luego infraestructura, luego presentacion. Ejecuta las
pruebas al terminar cada capa, no al final. Cada capa completa es un commit.

**4. Revision.** Lanza los subagentes `arquitecto` y `revisor-pruebas`. Si el
cambio toca cuentas, pagos o datos personales, tambien `revisor-seguridad`. Si
produce interfaz, tambien `revisor-accesibilidad`. Corrige lo que reporten.

**5. Cierre.** Verificacion completa, resumen de lo hecho y lista explicita de lo
que quedo fuera del alcance con su motivo.

Si en cualquier punto la historia resulta ambigua, pregunta en vez de decidir por
tu cuenta.
