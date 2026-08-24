# HU-003 — Captura asistida y visor 360º

**Fase:** 2 | **Estado:** pendiente
**Reglas:** RN-016 a RN-019

## Objetivo

El vendedor fotografía su prenda con un asistente que garantiza encuadre,
nivelación y proporción uniformes; con esas mismas tomas la ficha de producto
muestra un visor que simula el giro de la prenda.

Es el diferenciador principal del catálogo. Todo el procesamiento ocurre en el
cliente: el servidor solo recibe imágenes ya normalizadas.

## Dos decisiones que hay que respetar

**Ocho tomas, no cuatro.** Cuatro fotogramas producen un giro a saltos que se ve
peor que un carrusel normal. Se capturan **ocho tomas a 45 grados**; las cuatro
canónicas del carrusel (frontal, lateral derecha, posterior, lateral izquierda)
son las de 0, 90, 180 y 270 grados, extraídas de esa misma secuencia. Doce tomas
darían un giro más fluido, pero ocho es el punto donde la fricción para el
vendedor sigue siendo aceptable. Si más adelante se mide abandono alto en este
paso, se baja a seis antes que a cuatro.

**Proporción 3:4, no 1:1.** La guía visual fija 3:4 para toda foto de producto
porque de eso depende que la rejilla del catálogo tenga tarjetas de la misma
altura. El visor 360 usa exactamente los mismos archivos que el carrusel: un solo
canal de imágenes, una sola proporción, la mitad de almacenamiento y ningún
desajuste visual al pasar del carrusel al visor.

## Alcance

Entra: asistente de captura en web móvil, recorte y normalización en cliente,
carga de la secuencia, carrusel y visor giratorio en la ficha.

No entra: eliminación de fondo, corrección de color, reconocimiento del tipo de
prenda, captura desde aplicación nativa.

## Criterios de aceptación — captura

1. El asistente guía ocho pasos obligatorios con barra de progreso y nombre de
   cada toma.
2. La vista de cámara superpone una silueta translúcida y una cuadrícula para
   alinear la prenda.
3. Con el acelerómetro se muestra un nivel; si la inclinación supera 5 grados en
   cualquier eje, el obturador queda deshabilitado y se explica por qué.
4. En iOS el acceso a los sensores exige permiso explícito: se solicita con un
   gesto del usuario y, si se niega, el asistente continúa sin nivel pero avisa
   que la calidad puede variar. Nunca se bloquea la publicación por esto.
5. Cada toma se recorta en el cliente al contenido útil y se ajusta a 3:4 con el
   producto centrado, a 900 x 1200 px como mínimo.
6. Se puede repetir cualquier toma antes de enviar, sin perder las demás.
7. El avance se guarda localmente: cerrar el navegador por accidente no obliga a
   empezar de nuevo.
8. Si el dispositivo no tiene cámara o se deniega el permiso, se ofrece carga
   desde galería con el mismo recorte forzado, y la publicación se marca para
   revisión más atenta.
9. Las imágenes se comprimen antes de subir. Ninguna toma sale del dispositivo
   por encima de 500 KB.
10. La carga muestra progreso real por toma y se puede reintentar solo la que
    falló.

## Criterios de aceptación — visor

11. El componente recibe un arreglo ordenado de URL y muestra de inmediato el
    fotograma frontal.
12. Arrastrar con el ratón o deslizar con el dedo en el eje X cambia el
    fotograma. El sentido del giro coincide con el del movimiento.
13. El resto de fotogramas se precargan en segundo plano sin bloquear el hilo
    principal ni retrasar el contenido visible.
14. Mientras no estén todos cargados, el visor funciona con los disponibles y
    muestra un indicador discreto.
15. Con teclado: flechas izquierda y derecha giran un fotograma; el componente
    es enfocable y anuncia su función a los lectores de pantalla.
16. Con `prefers-reduced-motion` activo no hay giro automático ni inercia.
17. El deslizamiento vertical de la página nunca queda atrapado por el visor.
18. En SSR se entrega el fotograma frontal como imagen normal, con `alt`
    descriptivo, para que la ficha posicione en buscadores aunque el visor no
    llegue a activarse.
19. El visor no depende de ninguna librería de terceros.

## Casos borde

- Conexión lenta: el visor no se activa hasta tener al menos cuatro fotogramas.
- Publicación antigua con menos de ocho tomas: el visor no se ofrece y se muestra
  solo el carrusel.
- Dispositivo de gama baja: el recorte en canvas se hace en un worker para no
  congelar la interfaz.
- Rotación de pantalla a mitad de la captura: se mantiene el paso actual.

## Notas técnicas

- Captura con `getUserMedia`, procesamiento en `OffscreenCanvas` dentro de un
  worker, sensores con `DeviceOrientationEvent` y su solicitud de permiso en iOS.
- Requiere HTTPS también en desarrollo; el entorno local debe servirse con
  certificado.
- **Subida por el backend, no por URL firmada.** Esta línea decía lo contrario y
  quedó desactualizada: ADR-0018 es posterior y descartó la URL firmada
  precisamente porque obliga a validar sobre un archivo que ya existe en el
  almacén. El backend recibe los bytes, decide el tipo real por la cabecera,
  quita el EXIF y comprueba proporción y dimensiones antes de guardar, y nunca
  confía en lo que declara el cliente. El contrato concreto de subida está en
  HU-007. La URL firmada se reconsidera solo si esta historia mide que ocho tomas
  por el backend salen lentas o caras, que es la condición de revisión que el
  propio ADR-0018 anota.
- Se genera derivada en formato moderno para el catálogo y se conserva el
  original.

## Pruebas requeridas

- Unitarias del recorte y del cálculo de proporción con imágenes de prueba.
- Unitarias de la lógica de mapeo de desplazamiento a índice de fotograma.
- Componente: teclado, movimiento reducido, carga parcial.
- Extremo a extremo con cámara simulada por Playwright.
- Verificación de accesibilidad del visor con lector de pantalla.
