# Tratamiento de datos personales

Sastra guarda datos que la ley colombiana clasifica como sensibles: numero de
documento, imagen del rostro, cuenta bancaria y direccion de residencia. Aplica
la **Ley 1581 de 2012** y el Decreto 1074 de 2015. Este documento es la regla
operativa; no sustituye asesoria juridica.

Responsable del tratamiento: Sastra, NIT 1054994043-1, Medellin, Colombia.

## Principio de partida

No se pide un dato que no tenga un uso concreto y ya definido. Cada campo nuevo
que almacene informacion de una persona debe poder responder tres preguntas
antes de escribirse en una migracion:

1. Para que se usa, en una frase.
2. Cuanto tiempo se conserva y que lo borra.
3. Quien puede leerlo.

Si alguna no tiene respuesta, el campo no se crea.

## Clasificacion

| Nivel | Datos | Trato |
|---|---|---|
| Publico | Nombre de vendedor, ciudad, publicaciones | Visible en el sitio |
| Interno | Correo, telefono, fecha de nacimiento, historial de pedidos | Solo el titular y la operacion |
| Sensible | Documento de identidad, selfie, cuenta bancaria | Cifrado, acceso restringido y auditado |
| Secreto | Contrasenas, tokens | Nunca legibles, ni por la operacion |

## Reglas tecnicas

- Las contrasenas se guardan con Argon2id. No se guardan, ni cifradas, ni
  reversibles.
- Los documentos de identidad y las selfies van a almacenamiento privado, nunca
  a un bucket publico. Se sirven solo con enlace firmado de caducidad corta.
- El numero de cuenta bancaria se cifra en la base de datos. En pantalla se
  muestran unicamente los ultimos cuatro digitos.
- **Los registros nunca contienen** contrasenas, tokens, numeros de documento,
  cuentas bancarias ni la imagen de una selfie. Tampoco parcialmente, tampoco en
  nivel `debug`, tampoco en el mensaje de una excepcion.
- Las respuestas de la API devuelven solo los campos que la pantalla necesita.
  Un endpoint de perfil publico no incluye correo ni telefono.
- Los datos de verificacion no viajan al frontend una vez aprobada la
  verificacion: basta el estado y la fecha.
- Los entornos de desarrollo nunca reciben datos reales de personas. Si hace
  falta volumen, se generan datos sinteticos.
- **Ningun dato personal se pide sin uso concreto.** La ciudad y el telefono del
  perfil son opcionales, nacen vacios y se quitan dejando el campo en blanco: no
  hacen falta para tener cuenta. La foto de perfil no existe todavia porque
  necesita almacenamiento de archivos, y hasta entonces no se pide.

## Consentimiento

- El registro exige aceptacion expresa y separada de los terminos y de la
  politica de tratamiento de datos. Una sola casilla para las dos cosas no es
  consentimiento valido.
- La casilla no viene marcada por omision.
- **Cada casilla enlaza al documento que acepta.** Un consentimiento tambien
  tiene que ser informado, y no lo es si la persona no puede leer el texto. El
  enlace va fuera de la etiqueta y abre en pestana nueva: dentro de la etiqueta,
  pulsarlo marcaria la casilla ademas de abrir el documento, y se aceptaria sin
  haber leido con un solo gesto.
- La pagina del documento muestra su version en pantalla. Es lo que permite
  comprobar, meses despues, que el texto que alguien acepto es el que se le
  enseño.
- Se guarda la evidencia: version del documento aceptado, fecha, hora y direccion
  IP.
- La finalidad de la verificacion de identidad se explica en el momento de
  pedirla, no solo en la politica.

## Derechos del titular

La persona puede conocer, actualizar, rectificar y suprimir sus datos, y revocar
la autorizacion. Operativamente:

- Existe un canal de contacto visible para ejercerlos.
- El plazo de respuesta a una consulta es de diez dias habiles; el de un reclamo,
  quince habiles, prorrogables una vez.
- La cuenta admite eliminacion. Eliminar no significa borrar todo: las ordenes y
  facturas se conservan por obligacion contable y tributaria, pero se
  desvinculan del perfil y se anonimizan los datos que no sean necesarios.
- **En Fase 1 el cierre anonimiza en el acto**, no a los treinta dias. El plazo
  existe para resolver pedidos en curso y todavia no hay pedidos: no queda nada
  que la ley obligue a conservar, asi que esperar solo dejaria datos vivos. La
  fila se vacia en vez de borrarse (identificador, fecha de creacion y estado
  sobreviven, y ya no apuntan a nadie), el correo se sustituye por uno del
  dominio reservado `.invalid` para que la persona pueda volver a registrarse, y
  se borran contrasena, roles y enlaces pendientes. Cuando existan pedidos habra
  que bifurcar segun RN-009 y revisar si la fecha de nacimiento, que hoy se
  conserva, vuelve a identificar al cruzarse con un historial de compras.
- **Actualizar y rectificar se hace desde la propia cuenta**, sin pedirselo a
  nadie: nombre, ciudad y telefono se editan y se guardan en el acto.
- **El correo se cambia con verificacion previa.** Pedir el cambio no reemplaza
  nada: se manda un enlace al correo nuevo y la direccion de la cuenta solo
  cambia cuando alguien lo abre. Sin ese paso, un error de una letra dejaria a la
  persona fuera de su cuenta sin forma de volver.
- **Al cambiar el correo se avisa al anterior.** Es lo que evita el peor caso:
  quien robe una sesion cambia la direccion y saca al titular de su cuenta en
  silencio. Si la direccion pedida ya tiene cuenta, se avisa a su titular y a
  quien lo pidio se le responde exactamente igual que si estuviera libre: lo
  contrario convertiria el formulario en un detector de cuentas.
- El token de acceso ya emitido sigue siendo valido hasta quince minutos despues
  del cierre: es un JWT y ADR-0003 acepta esa ventana. Las rutas que devuelven o
  tocan datos responden 401 en cuanto la cuenta deja de existir.
- La politica de tratamiento de datos es un enlace visible en el pie de pagina.
  Es obligatorio y es lo primero que revisa una autoridad.

## Conservacion

| Dato | Plazo |
|---|---|
| Cuenta activa | Mientras exista la cuenta |
| Documentos de verificacion | Mientras el vendedor este activo y cinco anos mas |
| Ordenes y facturas | Diez anos, por obligacion contable |
| Registros tecnicos con IP | Seis meses |
| Cuenta eliminada | Anonimizada en el acto en Fase 1; treinta dias cuando existan pedidos |

## Pendiente antes del lanzamiento

- Registro de bases de datos ante la SIC, si se superan los umbrales aplicables.
- Politica de tratamiento de datos y terminos de uso redactados y publicados.
- Aviso de privacidad en el formulario de registro y en el de verificacion.
- Contrato de encargo con cada proveedor que procese datos por cuenta de Sastra:
  pasarela, correo, almacenamiento, buscador.
