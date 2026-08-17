# HU-002 — Verificación de vendedor

**Fase:** 2 | **Estado:** pendiente
**Reglas:** RN-010 a RN-014, RN-046

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
3. La selfie se toma en el momento, no se puede cargar desde la galería.
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

- Las imágenes se guardan en un bucket privado, cifradas, con acceso solo por URL
  firmada de corta duración y solo para el rol de moderación.
- El número de documento y el de cuenta se guardan cifrados a nivel de columna.
- Todo acceso a estos datos queda registrado en bitácora con actor y motivo.
- Retención: se define en `docs/operacion/datos-personales.md`. No se conservan
  indefinidamente.

## Pruebas requeridas

- Unitarias: coincidencia de titular, conteo de intentos, transiciones de estado.
- Integración: unicidad de documento bajo concurrencia, cifrado efectivo en base
  de datos, ausencia de datos sensibles en las respuestas.
- Extremo a extremo: recorrido completo hasta obtener el sello.
