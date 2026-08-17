# Trabajo con el agente

Este proyecto lo desarrolla una sola persona con Claude Code. Sin revision de
pares, la disciplina del proceso es lo unico que sustituye al segundo par de
ojos. De ahi que la configuracion sea estricta.

## Como esta organizado el contexto

| Archivo | Cuando lo lee el agente | Que contiene |
|---|---|---|
| `CLAUDE.md` | Siempre | Reglas que no se negocian, corto a proposito |
| `backend/CLAUDE.md` | Al tocar backend | Convenciones de Java y Spring |
| `frontend/CLAUDE.md` | Al tocar frontend | Convenciones de Angular y estilos |
| `docs/**` | Bajo demanda | El detalle, referenciado desde los anteriores |

Regla de oro: si algo debe cumplirse **siempre**, va en un `CLAUDE.md` o, mejor,
en un hook. Si es informacion que basta conocer cuando el tema aparece, va en
`docs/` y se referencia. Un `CLAUDE.md` largo se diluye y deja de leerse.

## Los tres mecanismos y cuando usar cada uno

- **Documentacion.** Lo que el agente debe saber. Si la ignora una vez, el costo
  es una molestia.
- **Hook.** Lo que debe ocurrir siempre, sin depender de que el modelo lo
  recuerde. Si lo ignora una vez, el costo es un incidente: un secreto en el
  repositorio, una migracion editada, un color quemado que rompe el modo oscuro.
- **Subagente.** Una revision especializada que necesita su propio contexto y no
  debe contaminar el de la tarea principal.

## Ciclo de una funcionalidad

```
/historia   redactar y acordar la historia y sus criterios
/implementar  planear, aprobar el plan, construir de adentro hacia afuera
/revisar    subagentes + verificacion completa
            corregir, confirmar, repetir
```

Puntos donde conviene detenerse siempre:

1. **Despues del plan y antes del codigo.** Es el momento mas barato para
   corregir el rumbo. Un plan equivocado aprobado cuesta una tarde.
2. **Al terminar cada capa.** Dominio con sus pruebas verdes antes de seguir.
3. **Antes de confirmar.** Nada entra sin pasar por `/revisar`.

## Comandos disponibles

| Comando | Para que |
|---|---|
| `/historia` | Redactar una historia de usuario nueva |
| `/implementar` | Construir una historia completa, con plan previo |
| `/revisar` | Revision con subagentes y verificacion antes de confirmar |
| `/adr` | Registrar una decision tecnica |
| `/sincronizar-docs` | Detectar desviaciones entre documentacion y codigo |

## Subagentes

| Subagente | Cuando |
|---|---|
| `arquitecto` | Siempre que se agreguen clases o archivos |
| `revisor-seguridad` | Cuentas, sesiones, pagos, archivos subidos, identidad |
| `revisor-accesibilidad` | Cualquier interfaz visible |
| `revisor-pruebas` | Cualquier logica de negocio |

Se invocan con el subagente correspondiente o en bloque con `/revisar`. Corren
con su propio contexto, asi que revisan sin el sesgo de haber escrito el codigo.

## Hooks activos

| Hook | Evento | Que hace |
|---|---|---|
| `proteger-archivos.mjs` | Antes de leer o escribir | Bloquea secretos, `tokens.css`, activos de marca y migraciones ya aplicadas |
| `proteger-comandos.mjs` | Antes de ejecutar un comando | Bloquea borrados destructivos, `push --force`, saltarse pruebas y `flyway clean` |
| `revisar-convenciones.mjs` | Despues de editar | Devuelve al agente los HEX y px sueltos, las API viejas de Angular y de Spring Boot 3, y los textos sin traducir |
| `contexto-de-sesion.mjs` | Al iniciar sesion | Inyecta rama, ultimos commits, cambios pendientes y fase vigente |

Un hook que bloquea devuelve el motivo al agente, que suele corregir solo. Si un
hook estorba de forma legitima, se ajusta el hook: no se desactiva.

Ya hay dos ajustes de ese tipo, los dos por el mismo motivo: la regla era
correcta pero atrapaba al archivo equivocado.

| Excepcion | Por que |
|---|---|
| `.env.example` se puede leer y escribir | Es el catalogo de variables y si se versiona. El patron de secretos lo bloqueaba junto con `.env`, y por eso llevaba tiempo desactualizado |
| `ArchitectureTest.java` no pasa por `revisar-convenciones.mjs` | Tiene que nombrar las API prohibidas para poder prohibirlas. La exencion va por ruta completa: el mismo nombre en otra carpeta si se revisa |

## Habitos que marcan la diferencia

- **Sesiones cortas y de un solo tema.** El contexto largo degrada la calidad
  mucho antes de agotarse. Al cambiar de funcionalidad, se limpia.
- **Planear en un turno, ejecutar en otro.** Separar decision de escritura evita
  la mayoria de los desastres.
- **Nunca aceptar "ya funciona" sin ejecucion.** Si no se corrio, no funciona.
- **Un commit por unidad logica**, con el trabajo del agente ya revisado. El
  historial es el unico registro de por que el codigo es como es.
- **Cuando la documentacion y el codigo se contradigan, gana el codigo** y se
  corrige la documentacion el mismo dia. Documentacion falsa es peor que
  ninguna.
- **Lo que se repite tres veces se automatiza**: en un comando si es un flujo, en
  un hook si es una regla.

## Cuando el agente se equivoca

- Si insiste en una API antigua, el problema no es el modelo: es que falta la
  prohibicion explicita en el `CLAUDE.md` correspondiente. Agregala.
- Si inventa una regla de negocio, falta la regla en
  `docs/producto/reglas-negocio.md`. Agregala.
- Si rompe una capa, revisa si el grafo de Gradle o la prueba de ArchUnit
  deberian haberlo impedido.

Cada correccion que se escribe una vez en la documentacion deja de repetirse en
cada sesion.
