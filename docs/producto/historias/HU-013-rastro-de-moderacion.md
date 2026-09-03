# HU-013 — El rastro de moderación de una publicación

**Fase:** 2 | **Estado:** pendiente
**Reglas que aplica:** RN-045, RN-022, RN-024, RN-061, y una regla nueva que esta historia
obliga a escribir (al final).

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
- **El envío a revisión, que no está en la bitácora.** Es del vendedor, no del moderador,
  así que nunca se anotó ahí; sale de `listings.submitted_at`. Sin él el rastro empieza a
  media frase.

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
cuando lo hay. **Con texto y no solo con color**, que es RN-012 aplicado a otra pantalla:
un rechazo no puede distinguirse de una aprobación únicamente por el tono.

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
- El envío a revisión no está en `moderation_events`: se compone con `listings.submitted_at`
  en el caso de uso. Hay que decidir si se persiste como evento —lo que arreglaría el
  criterio 4 para los reenvíos— o se deduce; ver lo que queda abierto.
- Claves de traducción nuevas para las acciones y para el estado vacío del criterio 6.
- Sin migración: `moderation_events` ya existe con las columnas que hacen falta.

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

## Las reglas que esta historia obliga a escribir

Ninguna se agrega todavía: se proponen aquí y se deciden antes de implementar.

- **RN-074 (propuesta) — La identidad de quien modera no se le muestra a quien vende.**
  La bitácora la guarda, porque auditar exige saber quién decidió; lo que no se hace es
  devolverla. Una decisión de moderación es de Sendik, y ponerle nombre convierte una
  discrepancia con la plataforma en una discrepancia con una persona. Es el criterio 5 y
  hoy no está escrito en ningún sitio: se está deduciendo de RN-046, que habla de otra
  cosa —de quién puede *leer* la cédula y la selfie, no de a quién se le atribuye una
  decisión.

## Lo que queda abierto

- **¿El envío a revisión se anota como evento?** Hoy se deduce de `submitted_at`, que solo
  guarda el último. Con eso, el criterio 4 —ver las dos vueltas de una publicación
  rechazada y reenviada— se cumple a medias: se ven las dos decisiones, pero solo el
  último envío. Anotarlo cuesta una migración y una línea en el caso de uso, y cierra el
  criterio entero. Es una decisión de producto: si el rastro es «lo que hizo Sendik» o «lo
  que pasó con esta publicación».
- **¿Se acota el rastro?** RN-014 acota a tres los intentos de la verificación de vendedor;
  para las publicaciones no hay tope escrito, así que una publicación puede acumular
  vueltas sin límite y su rastro crecer con ellas. Ninguna de las dos cosas es urgente,
  pero conviene decidir si el rastro se pagina antes de que alguien lo descubra.
- **¿`ARCHIVED` distingue las dos manos?** El vendedor archiva lo suyo y el moderador
  retira por RN-024, y los dos terminan en el mismo estado. El glosario ya los separa como
  conceptos —«Retiro de publicación» frente a archivar— pero si la bitácora los anota con
  la misma acción, el rastro no puede decir cuál fue. Se comprueba al implementar.
