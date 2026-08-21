# HU-002 — Verificación de vendedor

**Fase:** 2 | **Estado:** planeada, sin implementar
**Reglas:** RN-010 a RN-014, RN-046, RN-059

## Decisiones tomadas el 21 de agosto de 2026

Cuatro cosas que esta historia daba por resueltas y no lo estaban. Se decidieron
antes de escribir código, porque tres de ellas deciden el esquema o el nombre del
actor:

- **Revisa el moderador**, y su definición en el glosario se amplió para decirlo:
  antes era «aprueba o rechaza publicaciones y nada más». Es el único rol que ve la
  cédula y la selfie de alguien.
- **Las imágenes no se sirven por URL firmada.** Las sirve un endpoint autenticado,
  autorizado por rol y que registra cada lectura. El criterio 2 de más abajo pedía
  URL firmada; se cambió porque contradecía ADR-0018 y porque una URL firmada, en
  el tiempo que vive, deja ver la cédula a quien la tenga sin pasar por ninguna
  autorización nuestra y sin dejar rastro en la bitácora que esta misma historia
  exige.
- **El cifrado de columna es ADR-0020**: AES-GCM en la aplicación, con un HMAC de
  clave propia para la unicidad del criterio 5. Un valor cifrado no se puede
  indexar, así que sin ese HMAC el criterio 5 no se puede cumplir.
- **Falta el estado `REVOKED`** y se agregó al glosario. RN-013 pide revocar un
  sello ya otorgado, y eso no es `REJECTED`. Las transiciones completas están en
  RN-059.

## Objetivo

Un usuario se convierte en vendedor verificado entregando documento de identidad,
selfie y cuenta bancaria, para poder publicar y recibir desembolsos.

## Alcance

Entra: captura de documento por ambas caras, selfie con prueba de vida básica,
registro y validación de cuenta bancaria, revisión manual, sello de verificado.

No entra: proveedor automático de verificación de identidad, verificación de
empresas, validación de antecedentes.

## Criterios de aceptación

1. Solo un usuario con correo verificado y mayor de edad puede iniciar el
   proceso.
2. Se captura el documento por ambas caras con guía de encuadre y detección de
   desenfoque; una imagen borrosa se rechaza en el cliente antes de subirla.
3. La selfie se toma en el momento con la cámara, y la interfaz no ofrece cargar
   un archivo. **El límite se dice aquí y no se promete más:** en un navegador esto
   no se puede garantizar. Se usa la cámara y no se ofrece el selector de archivos,
   pero una cámara virtual pasa por ahí. Lo que el criterio exige es que cargar
   desde la galería no esté ofrecido, no que sea imposible.
4. Los datos bancarios exigen banco, tipo de cuenta, número y titular. El titular
   debe coincidir con el nombre del documento; si no coincide, se rechaza con
   motivo explícito.
5. Un mismo número de documento no puede quedar verificado en dos cuentas.
6. Enviada la solicitud, el estado pasa a `PENDING_REVIEW` y se informa el tiempo
   estimado de respuesta.
7. El moderador aprueba o rechaza indicando motivo de una lista cerrada más una
   nota opcional.
8. Aprobado, el usuario obtiene el rol de vendedor, el sello visible y acceso a
   publicar.
9. Rechazado, puede corregir y reenviar hasta tres veces; el cuarto intento
   requiere revisión manual y se avisa así.
10. Cada cambio de estado se notifica por correo.
11. En ninguna respuesta de la API aparecen las imágenes, el número de documento
    completo ni el número de cuenta completo. Solo los cuatro últimos dígitos.

## Casos borde

- Salir a la mitad del proceso: se guarda el avance y se retoma donde iba.
- Documento vencido: se rechaza con motivo específico.
- Cámara denegada en el navegador: se explica cómo habilitarla y no se ofrece
  carga desde galería para la selfie.
- Reintento con los mismos datos rechazados: se detecta y se avisa.

## Seguridad y datos

- Las imágenes se guardan en el almacén reservado —cubo privado, sin lectura
  pública, ADR-0018— y **no se sirven por ninguna URL**. Las entrega un endpoint que
  comprueba el rol de moderación y registra la lectura antes de devolver los bytes.
- El número de documento y el de cuenta se guardan cifrados a nivel de columna
  según ADR-0020, con `last_four` en claro para lo que la pantalla muestra.
- Todo acceso a estos datos queda registrado en bitácora con actor y motivo. La
  bitácora es la razón de que no haya URL firmada: un enlace que funciona por sí
  solo no puede registrar quién lo usó.
- Retención: se define en `docs/operacion/datos-personales.md`. No se conservan
  indefinidamente.

## Datos de referencia

Decididos el 21 de agosto de 2026. Los nombres de código van en inglés; lo visible,
por clave de Transloco.

### Documentos admitidos — `IdentityDocumentType`

| Código | Visible |
|---|---|
| `CC` | Cédula de ciudadanía |
| `CE` | Cédula de extranjería |
| `PPT` | Permiso por Protección Temporal |

Sin pasaporte: no se pidió y no se agrega solo. **El formato del número por tipo no
está decidido**, así que la validación es la mínima defendible —solo dígitos y un
rango de longitud— y queda anotada como regla por endurecer. Inventar el formato de
un PPT sería inventar una regla de negocio.

### Motivos de rechazo — `RejectionReason`

Lista cerrada. Los cuatro primeros los exige la propia historia, no son opcionales:

| Código | Visible | De dónde sale |
|---|---|---|
| `ILLEGIBLE_PHOTOS` | Las fotos no se pueden leer | Decidido |
| `EXPIRED_DOCUMENT` | El documento está vencido | Casos borde |
| `HOLDER_MISMATCH` | El titular de la cuenta no coincide con el documento | Criterio 4, RN-012 |
| `DOCUMENT_ALREADY_VERIFIED` | Ese documento ya está verificado en otra cuenta | Criterio 5, RN-010 |
| `REQUIREMENTS_NOT_MET` | No cumple los requisitos para vender | Decidido |

**`REQUIREMENTS_NOT_MET` es genérico a propósito, y eso tiene un costo que se
asume.** Cubre el caso de antecedentes penales o judiciales sin registrar nada
judicial sobre la persona: el sistema no consulta ninguna fuente judicial —la
historia deja la validación de antecedentes fuera de alcance— y
`docs/operacion/datos-personales.md` no tiene categoría para ese dato. Lo que se
pierde es que la persona rechazada no sabe por qué, y eso es deliberado, no un
olvido.

**La nota opcional del criterio 7 es la puerta de atrás de esta decisión.** Es texto
libre, así que es exactamente donde alguien podría escribir lo que se decidió no
guardar. Regla: la nota viaja a la persona rechazada y **nunca contiene información
judicial ni datos de un tercero**. Si hace falta registrar algo así, no se escribe
en la nota: se decide primero, con su clasificación y su respaldo.

### Tipos de cuenta — `BankAccountType`

| Código | Visible |
|---|---|
| `SAVINGS` | Ahorros |
| `CHECKING` | Corriente |
| `ELECTRONIC_DEPOSIT` | Depósito electrónico |

El tercero es el de Nequi, DaviPlata, Movii, Dale y RappiPay, que no son cuentas de
ahorros aunque se parezcan al usarlas. La distinción no es cosmética: el desembolso
de la Fase 3 necesita el tipo correcto o el dinero no llega.

### Bancos y billeteras

Van en **tabla con migración de Flyway**, no en un enum del código ni en una
variable: son 29 y crecerán, y la Fase 3 necesita el código de cada uno para el
desembolso. Cada fila lleva un código interno estable, el nombre visible y si es
banco o billetera. Guardar el nombre en la fila del vendedor ataría el dato a cómo
se llama hoy la entidad.

Bancos: Bancolombia, Banco Davivienda, Banco de Bogotá, BBVA Colombia, Banco AV
Villas, Banco Caja Social, Scotiabank Colpatria, Banco Popular, Banco de Occidente,
Banco GNB Sudameris, Banco Agrario, Banco Itaú, Citibank, Banco Pichincha, Banco
Santander, Bancoomeva, Banco Falabella, Banco Serfinanza, Bancamía, Banco Mundo
Mujer, Lulo Bank.

Billeteras y depósitos electrónicos: DaviPlata, Dale, RappiPay, Nequi, Movii, Tpaga,
Ualá.

**Scotiabank y Colpatria se listaron por separado y aquí van como una sola entrada**,
Scotiabank Colpatria, porque son la misma entidad: dos opciones que son una obligan
a la persona a adivinar. Si en tu operación son dos cosas distintas, se separan.

### Tiempo de respuesta

`VERIFICATION_REVIEW_DAYS`, dos días hábiles. Texto: «revisamos tu solicitud en
máximo 2 días hábiles». Va en configuración por lo mismo que `CLAIM_WINDOW_DAYS`:
cambiar la promesa no puede exigir un despliegue.

Cada reenvío del criterio 9 vuelve a `PENDING_REVIEW` y promete otra vez el mismo
plazo. El cuarto intento de RN-014, que exige revisión manual, también: no se
prometió nada distinto y no se inventa aquí.

## Lo que todavía falta

- **El texto de las pantallas.** `textos-web.md` cubre el sitio informativo y no
  esto; sin fuente escrita no hay claves de Transloco. Se escribe con la rebanada de
  interfaz.
- **El formato del número por tipo de documento**, según la nota de arriba.

## Pruebas requeridas

- Unitarias: coincidencia de titular, conteo de intentos, transiciones de estado.
- Integración: unicidad de documento bajo concurrencia, cifrado efectivo en base
  de datos, ausencia de datos sensibles en las respuestas.
- Extremo a extremo: recorrido completo hasta obtener el sello.
