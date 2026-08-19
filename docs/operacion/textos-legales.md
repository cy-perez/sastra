# Textos legales

Cada archivo se llama `<documento>.<version>.<idioma>.html`, donde `<documento>`
es `terms`, `privacy` o `cookies`.

La versión del nombre **tiene que coincidir** con la variable de entorno
correspondiente: `LEGAL_TERMS_VERSION`, `LEGAL_PRIVACY_VERSION` y
`LEGAL_COOKIES_VERSION`. Es lo que ata el texto que se muestra al que quedó
guardado como evidencia del consentimiento. Ver `docs/operacion/datos-personales.md`.

## Publicar un texto nuevo

1. Agrega el archivo con la versión nueva, en español y en inglés. **No edites ni
   borres el anterior:** hay personas que aceptaron esa versión y su evidencia
   apunta a ese archivo.
2. Cambia la variable de entorno a la versión nueva, en el backend y en el
   frontend, con el mismo valor en los dos.

No hace falta desplegar código.

## Dónde va la política de devoluciones

Dentro de `terms`, no en un documento propio. Un documento nuevo exige su
variable de versión, su ruta y su entrada en `RUTAS_LEGALES`, y hoy no hay nada
que lo justifique: el derecho de retracto y el reintegro por producto no conforme
(RN-050 a RN-058) son dos secciones de los términos.

Si más adelante se separa, se agrega `returns` con su `LEGAL_RETURNS_VERSION`
siguiendo el mismo mecanismo. La decisión está anotada en
`docs/producto/alcance.md`.

Lo que sí es obligatorio: **la redacción del retracto la revisa un abogado antes
de publicarse**, y ninguna página informativa enuncia sus plazos por su cuenta.
Los plazos concretos viven aquí, en un solo sitio versionado, y las páginas
enlazan (RN-057).

## Estado actual

Los archivos `borrador-local` son **relleno sin valor legal**, puestos para que
la estructura funcione. No sirven para lanzar: los textos reales de términos,
tratamiento de datos y cookies los tiene que redactar y revisar quien
corresponda, con la razón social y el NIT reales.

Mientras la versión vigente sea `borrador-local`, la página muestra un aviso
visible diciéndolo.

## Formato

HTML suelto, sin `<html>` ni `<body>`: se inserta dentro de la página, que ya
pone el título y la versión. Usa `h2`, `h3`, `p`, `ul`, `ol` y `a`; la
maquetación la aporta `legal-page.css`.

El contenido pasa por el desinfectante de Angular, así que cualquier `script` o
atributo ejecutable se descarta al mostrarlo.
