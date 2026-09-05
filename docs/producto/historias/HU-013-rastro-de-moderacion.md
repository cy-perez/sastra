# HU-013 — El rastro de moderación de una publicación

**Fase:** 2 | **Estado:** hecha el 5 de septiembre de 2026
**Reglas que aplica:** RN-045, RN-022, RN-024, RN-061, RN-062, RN-074 —la regla nueva que
esta historia obligó a escribir, ya en `reglas-negocio.md`.

> **La otra mitad del punto que `alcance.md` marca «hecho a medias»**, separada de HU-012
> porque no se parecen: las cifras son lectura de algo que ya está en `listings`; esto
> necesita abrir un puerto que hoy solo escribe.
>
> `ModerationLog` registra cada decisión desde HU-008 —RN-045 dice que ninguna transición
> se pierde— y nadie puede leerla. La tabla `moderation_events` guarda acción, motivo,
> nota y fecha, y hasta hoy solo sirve para auditar por consulta directa.

## Objetivo

Quien vende entiende qué le pasó a una publicación suya y por qué, sin tener que buscar el
correo que se lo avisó.

## Alcance

Entra:

- El rastro de una publicación propia: qué pasó, cuándo y con qué motivo.
- La lectura nueva en el puerto `ModerationLog` y su endpoint.
- Los eventos que ya se registran: aprobación, rechazo y retiro.
- **El envío a revisión, que no estaba en la bitácora.** Es del vendedor, no del moderador,
  así que nunca se anotó ahí. Sin él el rastro empieza a media frase. **Se decidió
  anotarlo como evento** en vez de deducirlo de `listings.submitted_at`; el porqué está más
  abajo, en lo que quedaba abierto.

No entra:

- **Quién decidió.** El rastro guarda `actor_id` y no sale: el moderador es un rol, no una
  persona, y ponerle nombre a la decisión convierte una discrepancia con Sendik en una
  discrepancia con alguien. Ver la regla nueva al final.
- **La nota interna.** `moderation_events.notes` se escribió para Sendik. Enseñarla exige
  decidir antes que se escribe pensando en que la lea el vendedor, y hoy no es así.
- El rastro de una publicación ajena, y el de las verificaciones de vendedor, que tiene su
  propia bitácora con reglas más duras (RN-046, ADR-0018).
- Cifras y filtros. Son HU-012 y lo que quedó abierto allí.

## Criterios de aceptación

1. Dado que una publicación mía fue rechazada, cuando abro su rastro, entonces veo que se
   rechazó, cuándo, y el motivo traducido de RN-022.
2. Dado que una publicación mía fue aprobada, cuando abro su rastro, entonces veo que se
   aprobó y cuándo; sin motivo, porque aprobar no lo pide.
3. Dado que una publicación mía fue retirada estando visible, cuando abro su rastro,
   entonces veo el retiro con su motivo, que RN-024 hace obligatorio.
4. Dado que una publicación mía pasó por revisión más de una vez —rechazada, corregida y
   reenviada— cuando abro su rastro, entonces **veo las dos vueltas**, en orden, y no solo
   la última.
5. Dado que abro el rastro, cuando lo leo, entonces **en ningún sitio aparece quién
   decidió**: ni nombre, ni correo, ni identificador.
6. Dado que una publicación mía nunca salió de borrador, cuando abro su rastro, entonces
   se me dice que todavía no ha pasado nada, y no se pinta una lista vacía.
7. Dado que pido el rastro de una publicación que no es mía, cuando el servidor responde,
   entonces es 404 y no 403: un 403 confirma que esa publicación existe.
8. Dado que pido el rastro sin sesión, cuando la ruta resuelve, entonces se me lleva a
   entrar.
9. Dado que las fechas se muestran, cuando las leo, entonces están en la zona horaria de
   la configuración y con el formato de la configuración regional, no en UTC crudo.

## Casos borde

- **Publicación con muchas vueltas**: RN-014 acota los intentos de la verificación, no los
  de una publicación. Hay que decidir si el rastro se acota; ver la nota al final.
- **Un evento con una acción que la pantalla no conoce** —porque se agregó después—: se
  muestra la fecha y una descripción genérica; no se rompe la lista ni se omite la fila,
  que sería esconder que algo pasó.
- **Motivo nulo donde se esperaba**: aprobar no lleva motivo; rechazar y retirar sí. Una
  fila sin motivo donde debería haberlo se pinta igual, sin inventar texto.
- **Publicación archivada por el propio vendedor**: no es una decisión de moderación y no
  tiene por qué estar en la bitácora. Confirmar al implementar si `ARCHIVED` distingue las
  dos manos; si no lo hace, es un defecto que esta historia destapa.
- **Orden con fechas iguales**: dos eventos en el mismo instante se desempatan por `id`,
  que es la lección ya pagada en las dos colas.

## Diseño

Dentro de la publicación, no como pantalla aparte: se llega desde `/mis-publicaciones` y
desde el borrador rechazado, que es donde hoy ya se ve el motivo del último rechazo.

Una línea de tiempo, lo más reciente arriba. Cada entrada: qué pasó, cuándo, y el motivo
cuando lo hay. **Con texto y no solo con color** (WCAG 1.4.1): un rechazo no puede
distinguirse de una aprobación únicamente por el tono. No hay regla de negocio detrás; la
historia citaba RN-012, que es la de la cuenta bancaria, y era un error.

Estados de carga, vacío y error propios, acotados al bloque del rastro. Como en la
bandeja: el error no puede tumbar la publicación entera.

## Notas técnicas

- `ModerationLog` gana una lectura. Es un puerto de salida de `application`, así que el
  método devuelve tipos de dominio y la traducción a JSON vive en `presentation`.
- Endpoint nuevo bajo la publicación: `GET /api/v1/listings/{id}/moderation-history`. El
  identificador del vendedor sale del token, no del parámetro; el 404 del criterio 7 es
  del caso de uso, no del controlador.
- **La respuesta no incluye `actor_id` ni `notes`.** No se filtran en la pantalla: no
  salen del servidor. Filtrar en el cliente es enviarlos.
- ~~El envío a revisión se compone con `listings.submitted_at` en el caso de uso.~~
  **Se persiste como evento**, así que el rastro sale entero de `moderation_events` y el
  caso de uso no compone nada. Es la decisión de más abajo, y hace que el criterio 4 se
  cumpla entero en vez de a medias.
- Claves de traducción nuevas para las acciones y para el estado vacío del criterio 6.
- ~~Sin migración.~~ **`V17`**: la restricción de `action` no admitía `SUBMITTED`, y hay que
  reconstruir desde `submitted_at` los envíos anteriores a ella o todo rastro existente
  estrena sin su primera línea.

## Pruebas requeridas

- **Aplicación**: que el rastro de otra persona no se entrega (criterio 7); que el envío se
  compone con las decisiones en el orden correcto; que las varias vueltas salen todas.
- **Persistencia**: la lectura contra PostgreSQL, con dos publicaciones y dos vendedores;
  el desempate por `id` con fechas iguales.
- **Controlador**: 404 y no 403 sobre una publicación ajena; 401 sin sesión; y **una
  prueba que afirme que `actor_id` y `notes` no aparecen en el JSON**, que es el criterio 5
  y es lo único que impide que vuelvan por descuido.
- **Componente**: las tres acciones pintadas con su texto, la acción desconocida que no
  rompe, el vacío del criterio 6, el error acotado.
- **Extremo a extremo**: rechazar, corregir, reenviar y aprobar, y comprobar que el
  vendedor ve las dos vueltas. El recorrido ya existe en
  `moderacion-de-publicaciones.spec.ts`; le falta el final.

## La regla que esta historia obligó a escribir

**RN-074 — La identidad de quien modera no se le muestra a quien vende.** Se aprobó tal
como se proponía y está en `reglas-negocio.md`, al final de la sección de publicación. La
nota interna entra en la misma regla: se escribió para Sendik.

Se cumple **no trayendo el dato**: la consulta no selecciona `actor_id` ni `notes`, el tipo
de dominio no los lleva y el DTO no tiene campo para ellos. La única prueba que puede verlo
volver es la que mira el JSON crudo, y está.

## Lo que quedaba abierto, y cómo se cerró

- **El envío a revisión se anota como evento.** Decidido el 4 de septiembre de 2026: el
  rastro cuenta «lo que le pasó a esta publicación» y no «lo que hizo Sendik». Sin eso, el
  criterio 4 se cumplía a medias —dos decisiones y un solo envío— porque `submitted_at` se
  sobrescribe en cada entrada a `PENDING_REVIEW`.

  **Y no hay un camino de entrada, hay dos.** Escribir la historia solo contemplaba el
  envío explícito; el código enseñó que RN-062 devuelve a la cola una publicación viva
  cuando se le edita el contenido. Anotar solo el primero dejaba sin rastro justo la vuelta
  que el vendedor no recuerda haber dado, porque la vivió como «cambié la descripción».
  Los dos casos de uso escriben el evento.

  `V17` reconstruye desde `submitted_at` los envíos anteriores a ella: uno por publicación,
  el último, que es todo lo que esa columna sabe. Es incompleto a propósito y está dicho en
  la migración; inventar los anteriores exigiría datos que nadie guardó.

- **El rastro no se pagina.** Decidido el 4 de septiembre de 2026: no hay tope de vueltas
  escrito, pero tampoco volumen que justifique paginar una línea de tiempo corta. La
  respuesta es un objeto con la lista dentro, así que el día que haga falta admite un cursor
  sin romper a ningún cliente.

- **`ARCHIVED` sí distingue las dos manos, y no había defecto.** Se comprobó al implementar:
  solo `TakeDownListingUseCase` escribe en la bitácora. `ArchiveListingUseCase` —el archivar
  del vendedor— no la toca, así que un `ARCHIVED` en el rastro es siempre un retiro del
  moderador. Queda fijado con una prueba para que esa propiedad no se pierda el día que
  alguien decida anotar también lo que hace el vendedor.

## Lo que la implementación destapó

**Dos relojes escribiendo en un mismo registro ordenado.** El envío sellaba la hora con el
reloj de la aplicación y las tres decisiones del moderador la tomaban del `now()` de la
tabla. Era inofensivo mientras la bitácora solo se auditaba por consulta directa; al
leerla en orden dejó de serlo. Lo vio `ListingJourneyTest`, que se encontró una aprobación
fechada **antes** del envío que la había provocado.

`ModerationLog.registrar` recibe ahora el instante con el que el caso de uso sella la
publicación, así que el rastro y `listings.moderated_at` cuentan el mismo momento. Ninguna
fila de esa tabla se fecha ya con el reloj del motor.

## Lo que quedó fuera

- **Quién decidió y la nota interna.** RN-074, por decisión y no por olvido.
- **El rastro de una publicación ajena** y el de las verificaciones de vendedor, que tiene
  su propia bitácora con reglas más duras (RN-046, ADR-0018).
- **Cifras y filtros sobre el rastro.** Las cifras son HU-012 y ya están.
- **Paginar**, por la decisión de arriba.
