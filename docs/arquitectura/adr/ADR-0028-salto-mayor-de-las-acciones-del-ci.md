# ADR-0028 — Las acciones del CI suben de versión mayor en bloque

**Fecha:** 2026-09-01
**Estado:** aceptada

## Contexto

Dependabot abrió el PR #19 con siete acciones de GitHub, **todas con salto de versión
mayor**:

| Acción | De | A | Dónde se usa |
|---|---|---|---|
| `actions/checkout` | 4 | 7 | los dos flujos, siete veces |
| `actions/setup-node` | 4 | 7 | `verificacion.yml` |
| `actions/upload-artifact` | 4 | 7 | `verificacion.yml`, tres informes |
| `actions/setup-java` | 4 | 6 | `verificacion.yml` |
| `gradle/actions/setup-gradle` | 4 | 6 | `verificacion.yml` |
| `google-github-actions/auth` | 2 | 3 | `despliegue.yml` |
| `google-github-actions/setup-gcloud` | 2 | 3 | `despliegue.yml` |

`CLAUDE.md` pone «subir dependencias de versión mayor sin una ADR» en la lista de lo que
nunca se hace, y son siete de golpe. De ahí este archivo.

Lo que obliga a decidir ahora y no dentro de tres meses es que **el CI ya avisa**. Cada
ejecución termina con:

```
Node.js 20 is deprecated. The following actions target Node.js 20 but are being forced to
run on Node.js 24: actions/checkout@v4, actions/setup-node@v4, actions/upload-artifact@v4
```

Es decir: el runner ya **está** ejecutando esas tres acciones en Node 24, a la fuerza y
contra lo que ellas declaran. No estamos eligiendo entre Node 20 y Node 24; estamos
eligiendo entre correr en Node 24 con acciones que dicen soportarlo y correr en Node 24 con
acciones que dicen que no. La mayoría de estos saltos mayores son exactamente eso: el
cambio de `runs.using` a `node24` y la versión mínima de runner que trae.

Tres cosas acotan el riesgo, y se comprobaron contra el repositorio antes de decidir:

- **`pull_request_target` no se usa.** `verificacion.yml` dispara con `pull_request`,
  `push` a `main` y `workflow_call`. El cambio rompiente de `checkout@v6` —valores por
  omisión más seguros en `pull_request_target`— no toca nada de aquí.
- **Los runners son de GitHub.** Los ocho `runs-on` del repositorio son `ubuntu-latest`, así
  que la versión mínima de runner que piden estas versiones se cumple sola. Con runners
  auto-hospedados habría que actualizarlos primero.
- **No hay acciones de contenedor Docker.** `despliegue.yml` construye imágenes con `docker
  build` dentro de un `run:`, que no es lo mismo. El cambio de `checkout@v6`, que pasa a
  persistir las credenciales en un archivo aparte en vez de en el `git config` local, no
  afecta a ese uso.
- **`upload-artifact@v7` conserva todas las entradas que usamos**, y en particular
  `include-hidden-files`, con el mismo valor por omisión. Se comprobó a propósito: HU-008
  perdió una tarde por culpa de esa opción —`.registro-backend.log` empieza por punto y no
  llegaba al artefacto, así que se diagnosticó sobre el registro de otra corrida—. Si el
  salto la hubiera quitado, habríamos reabierto ese mismo agujero sin enterarnos.

Y una que **no** queda acotada, que es lo que de verdad hay que decidir: de los siete, el
CI solo ejercita cinco. Los dos de `google-github-actions` viven únicamente en
`despliegue.yml`, que corre al empujar a `main`. El job «Ensayo del despliegue» **no los
usa**: hace `checkout` y `docker build` en local, sin autenticar contra GCP. Su verde en el
PR #19 no dice nada sobre `auth@v3` ni sobre `setup-gcloud@v3`.

## Opciones

**Subir los siete en bloque.** Un PR, una ADR, y el CI vuelve a estar alineado con el
runtime que ya lo ejecuta. El costo es que dos de las siete llegan a `main` sin que ninguna
prueba las haya tocado, y su primer ejercicio es un despliegue de verdad.

**Partir el PR en dos: los cinco probados ahora, los dos de GCP después.** Nada llega a
producción sin haber pasado por alguna prueba. El costo es doble y no es solo de trabajo:
obliga a editar a mano un PR de Dependabot o a cerrarlo y rehacer el bump por partes —lo
que rompe el agrupamiento `acciones` que el propio `dependabot.yml` configura, y en la
siguiente ronda vuelve a proponer lo mismo—, y sobre todo **no elimina el riesgo, lo
aplaza**: los dos de GCP se seguirán estrenando en un despliegue real el día que entren,
porque no existe forma de probarlos en este repositorio sin credenciales de GCP en un PR.

**Fijar las acciones y no subirlas.** Congelar en v4 y silenciar el aviso. Es gratis hoy y
carísimo el día que GitHub deje de forzar Node 24 y las acciones dejen de arrancar. Además
convierte cada ronda de Dependabot en ruido que se cierra sin leer, que es la manera de que
un día se cierre sin leer la que sí importaba.

## Decisión

Se suben las siete en bloque, en un solo PR, y se acepta por escrito que
`google-github-actions/auth@v3` y `google-github-actions/setup-gcloud@v3` se estrenan en el
primer despliegue a `main` posterior al merge.

## Motivo

La opción de partir el PR compra menos de lo que parece. El riesgo que quita no es «los dos
de GCP pueden fallar», sino «pueden fallar hoy en vez de dentro de dos semanas»: como no
hay manera de ejercitarlos sin credenciales, entren cuando entren, su primera ejecución
real será un despliegue. Pagar por aplazar eso el precio de romper el agrupamiento de
Dependabot no compensa.

Y el riesgo es barato de revertir, que es lo que inclina la decisión. Si el despliegue
falla, el síntoma es inmediato y ruidoso —el job se cae en el paso de autenticación, antes
de tocar nada— y la vuelta atrás es cambiar `@v3` por `@v2` en cuatro líneas. **Cloud Run
sigue sirviendo la revisión anterior mientras tanto**: un despliegue que no llega a
desplegar no tumba lo que ya está en línea. No es el caso de una migración de base de
datos, donde revertir es el problema.

Fijar las versiones se descartó porque el aviso del CI no es una preferencia de estilo: las
tres acciones ya corren forzadas en un runtime que no declaran soportar. Eso es la
definición de deuda que solo crece.

## Consecuencias

- El CI deja de emitir el aviso de deprecación de Node 20 y las acciones pasan a declarar
  el runtime en el que ya se ejecutan.
- **El primer despliegue a `main` después del merge es el que hay que mirar.** Si falla en
  «Autenticar contra GCP» o en `setup-gcloud`, la causa más probable son estos dos saltos y
  no el código que se estaba desplegando. Vale la pena leer eso antes de buscar en otro
  lado.
- La reversión está definida de antemano: `@v3` a `@v2` en `despliegue.yml`, cuatro líneas,
  sin tocar los otros cinco.
- Se acepta que este bloque entra sin probar dos de sus siete piezas. Es una deuda asumida
  a propósito, no un descuido.
- El agrupamiento `acciones` de `dependabot.yml` se mantiene: la próxima ronda seguirá
  llegando en un solo PR.

## Cuándo revisar

- **Si el despliegue siguiente falla en autenticación o en `setup-gcloud`.** Entonces esta
  decisión se ejecuta al revés: se vuelve a v2 y se escribe la ADR que sustituya a esta,
  con lo que se aprendió del fallo.
- **Si aparecen runners auto-hospedados.** La versión mínima de runner deja de estar
  garantizada y hay que comprobarla antes de cualquier salto mayor futuro.
- **Si algún flujo pasa a usar `pull_request_target`** o acciones de contenedor Docker. Las
  dos razones por las que los cambios rompientes de `checkout` no afectan aquí desaparecen,
  y hay que releer sus notas de versión.
- **Si el «Ensayo del despliegue» llega a autenticar contra un proyecto de GCP de prueba.**
  Entonces el argumento central de esta ADR —que no hay forma de probar esas dos acciones—
  deja de ser cierto, y los saltos mayores de `google-github-actions` deberían pasar por ahí
  antes de llegar a `main`.
