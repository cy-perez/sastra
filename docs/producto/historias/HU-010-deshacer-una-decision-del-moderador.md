# HU-010 — Deshacer una decisión del moderador

**Fase:** 2 | **Estado:** pendiente
**Reglas que aplica:** RN-013, RN-024, RN-059, RN-060, RN-061, RN-063, RN-068, RN-069

> **Es la mitad que le falta a la moderación.** HU-006 y HU-008 entregaron el camino de
> ida —aprobar un sello, aprobar una publicación— y ninguna de las dos entregó la vuelta.
> Los dos endpoints existen desde HU-002 y HU-007 y hacen ya todo el trabajo; lo que no
> hay es forma de llegar a esos identificadores desde la interfaz.
>
> **Encender `FEATURE_CATALOG` sin esto es abrir el sitio sin freno de mano.** Es la razón
> por la que esta historia va antes que los favoritos y que el panel del vendedor: aquellas
> suman, y de esta depende poder encender las banderas.

## Objetivo

Un moderador puede quitarle el sello a un vendedor ya verificado y bajar una publicación
que ya es visible, con motivo y desde donde el problema se ve.

## Alcance

Entra:

- La acción de bajar, en la ficha pública `/producto/:id`, para quien tiene rol de moderador.
- La acción de revocar, en el perfil público `/vendedor/:id`, para lo mismo.
- Confirmación con motivo obligatorio de lista cerrada y nota opcional, en las dos.
- Una lista de motivos **propia de la revocación**, que hoy no existe.
- Un endpoint de lectura que lleve de un vendedor a su verificación.

No entra:

- Buscadores ni listados por estado en las dos bandejas. Se descartó a propósito: quien
  descubre una réplica la está mirando, no la está buscando por identificador.
- Bajar en bloque las publicaciones de quien pierde el sello. RN-013 dice que siguen
  visibles, y una acción masiva es cambiar la regla, no implementarla.
- Deshacer el deshacer. `ARCHIVED` es terminal (RN-061) y de `REVOKED` solo se sale por
  voluntad de la persona, volviendo a intentarlo (RN-059).
- El reporte de un comprador que denuncia una publicación. Es Fase 4.

## Criterios de aceptación

### Bajar una publicación visible

1. Dado un moderador con la sesión abierta, cuando abre la ficha de una publicación
   `PUBLISHED` o `PAUSED`, entonces ve la acción de bajarla.
2. Dado cualquier otra persona —sin sesión, comprador o vendedor—, cuando abre la misma
   ficha, entonces la acción no existe en el HTML que recibe. No basta con esconderla.
3. Dado que la publicación está en `DRAFT`, `PENDING_REVIEW`, `REJECTED`, `SOLD` o
   `ARCHIVED`, cuando un moderador la abre, entonces la acción no se ofrece. Bajar es para
   lo que ya fue visible; lo que espera revisión se decide en su bandeja.
4. Dado que pulsa la acción, cuando aparece la confirmación, entonces se exige un motivo de
   la lista cerrada de RN-024 y se admite una nota opcional, y no se puede confirmar sin
   motivo.
5. Dado que confirma, entonces la publicación queda `ARCHIVED`, deja de aparecer en el
   catálogo (RN-068) y su ficha pasa a decir que ya no está disponible.
6. Dado que la publicación es del propio moderador, cuando confirma, entonces el servidor
   la rechaza por RN-063 y la pantalla lo explica con esas palabras.
7. Dado que otra persona la bajó mientras esta tenía la confirmación abierta, cuando
   confirma, entonces recibe el conflicto con su motivo y la pantalla se actualiza sola,
   sin pedirle que lo intente otra vez.
8. Dado que se bajó, entonces queda en la bitácora con fecha, actor, motivo y nota, y el
   vendedor recibe el correo con el motivo.

### Revocar un sello otorgado

9. Dado un moderador con la sesión abierta, cuando abre el perfil de alguien `VERIFIED`,
   entonces ve la acción de revocar el sello.
10. Dado cualquier otra persona, cuando abre el mismo perfil, entonces la acción no existe
    en el HTML que recibe.
11. Dado que el vendedor no está verificado —nunca empezó, está en revisión, fue rechazado
    o ya está revocado—, entonces la acción no se ofrece.
12. Dado que pulsa la acción, cuando aparece la confirmación, entonces se exige un motivo de
    los cinco de RN-069 y se admite una nota opcional. Los motivos del rechazo no se
    ofrecen aquí.
13. Dado que la confirmación está abierta, entonces dice, antes de confirmar, que las
    publicaciones activas de esa persona **siguen visibles** y que bajarlas es una a una
    (RN-013). Sin esa frase, quien revoca cree que ya retiró lo que no retiró.
14. Dado que confirma, entonces la verificación queda `REVOKED`, la persona pierde el rol de
    vendedor, y la insignia desaparece de su perfil y de sus fichas.
15. Dado que el sello es el suyo, cuando confirma, entonces el servidor la rechaza por
    RN-060 y la pantalla lo explica.
16. Dado que se revocó, entonces la persona recibe el correo con el motivo, y ese correo
    dice que lo que tenía publicado sigue visible.
17. Dado que perdió el sello, cuando intenta crear una publicación nueva, entonces no puede
    (RN-011 y RN-013); las que ya tenía siguen donde estaban.
18. Dado que perdió el sello, cuando vuelve a `/verificacion-de-vendedor`, entonces puede
    intentarlo de nuevo (RN-059).

### Las dos

19. Las dos confirmaciones se recorren enteras con el teclado: el foco entra al abrirlas y
    vuelve al disparador al cerrarlas, y se cierran con `Escape` sin ejecutar nada.
20. Ningún texto de esta historia vive en una plantilla: todo por clave de Transloco, en
    español y en inglés, motivos incluidos.

## Casos borde

- **La bandera está apagada.** Sin `publishing` la ficha del moderador no ofrece bajar;
  sin `seller-verification`, el perfil no ofrece revocar. La pantalla no promete lo que el
  servidor va a responder con 404.
- **Doble envío.** Pulsar confirmar dos veces no manda dos peticiones. La segunda, si sale,
  cae en el criterio 7.
- **La sesión expiró mientras la confirmación estaba abierta.** Se renueva con la cookie de
  refresco y se reintenta una vez; si tampoco, se dice y no se pierde el motivo escrito.
- **El vendedor nunca empezó la verificación.** El endpoint nuevo responde 404 y el perfil
  no ofrece nada. No es un error de la pantalla.
- **Revocar a alguien con publicaciones `SOLD`.** No las toca: RN-023 y el pago ya ocurrido.
- **Bajar una publicación con una compra en curso.** Fuera de alcance hasta Fase 3, pero se
  anota aquí para que no se descubra entonces: hoy no hay compras.

## Diseño

- Las dos son acciones destructivas y ninguna va en bronce. Botón secundario, y la
  confirmación reutiliza el diálogo que ya existe en las dos bandejas.
- El acento de la pantalla sigue siendo la insignia de verificado, una sola vez. En el
  perfil del vendedor eso significa que el botón de revocar no compite con ella.
- Estados de carga, error y el estado posterior a la acción, en las dos pantallas.
- En móvil la acción va debajo del contenido de la ficha y nunca al lado del precio: es del
  moderador, no del comprador, y la ficha es sobre todo una pantalla de compra.

## Notas técnicas

- **La mitad de publicación no necesita backend.** `POST /listings/{id}/removal` existe y
  `TakeDownListingUseCase` ya archiva, registra en bitácora, avisa por correo, exige que
  haya sido visible, aplica RN-063 y borra las fotos del almacén. `GET /listings/{id}`
  responde la forma completa a un moderador.
- **La mitad de revocación sí.** `POST /verifications/{id}/revocation` existe y hace todo,
  pero no hay forma de llegar a ese identificador: el controlador de revisión solo expone
  la cola de pendientes, y el perfil público entrega un `sellerId`, que no es el de la
  verificación. Hace falta una lectura nueva —`GET /api/v1/users/{id}/verification` o
  equivalente— con rol de moderador. **Es la única pieza de servidor que esta historia
  agrega.**
- **La lista de motivos de revocación la fija RN-069**, escrita el 28 de agosto de 2026 al
  redactar esta historia. Son cinco: `DOCUMENT_NOT_ITS_HOLDER`, `BANK_ACCOUNT_NOT_HOLDER`,
  `REPEATED_PROHIBITED_LISTINGS`, `HOLDER_REQUEST` y `REQUIREMENTS_NO_LONGER_MET`. Hasta
  entonces el endpoint reutilizaba `RejectionReason`, que está escrita para juzgar **una
  solicitud**: cuatro de sus cinco valores hablan de lo que se entregó, y el quinto,
  `REQUIREMENTS_NOT_MET`, es genérico a propósito. Con esa lista, revocar por otra cosa
  manda un correo que dice «fotos ilegibles». Entra `RevocationReason` en `domain/identity`
  y el endpoint pasa a recibirla en vez de la del rechazo.
- Cada acción vive detrás de su bandera: `publishing` la de bajar, `seller-verification`
  la de revocar.

## Pruebas requeridas

- Unitarias de dominio de la lista de motivos nueva: un motivo desconocido no se convierte
  en uno válido y no hay valor por omisión.
- De aplicación: que revocar exija un motivo de la lista nueva, y que la lectura nueva no
  responda a quien no modera.
- De componente: que la acción no se renderice sin el rol, que no se confirme sin motivo,
  que el foco entre y vuelva, y que el aviso de RN-013 esté antes de confirmar.
- Extremo a extremo en `e2e-completo/`, los dos ciclos completos por la interfaz: aprobar
  una publicación, bajarla, y comprobar que deja de verse sin cuenta; verificar a alguien,
  revocarle el sello, y comprobar que la insignia desaparece y que ya no puede publicar.

## Lo que habría que agregar, y no se agrega aquí

**Reglas de negocio y glosario: ya están.** Se escribieron el 28 de agosto de 2026, al
decidir los motivos. RN-069 fija la lista cerrada y explica por qué sus valores describen
hechos y no delitos; el glosario suma Revocación / `Revocation`, Motivo de revocación /
`RevocationReason` y Retiro de publicación / `ListingRemoval`.

**Modelo de datos.** Es lo único que queda pendiente, y llega con la implementación, no
antes: `RevocationReason` como enumeración de dominio y la columna donde se guarda en la
tabla de verificación, que hoy comparte con el motivo de rechazo. Separarlas es una
migración nueva. La bitácora no cambia: guarda el motivo como texto.
