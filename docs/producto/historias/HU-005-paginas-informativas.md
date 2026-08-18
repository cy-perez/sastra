# HU-005 — Páginas informativas

**Fase:** 1 | **Estado:** pendiente
**Reglas que aplica:** RN-011, RN-015, RN-026, RN-027, RN-032, RN-034

## Objetivo

Quien duda antes de registrarse encuentra explicado, en páginas propias y
enlazables, cómo funciona la compra y la venta, quién está detrás de Sastra, qué
se pregunta todo el mundo y cómo comunicarse con la empresa.

## Alcance

Entra: cuatro páginas nuevas —cómo funciona (con las dos caras, comprador y
vendedor), sobre Sastra, preguntas frecuentes y contacto— con su ruta, su título
y descripción de buscador, y su texto en español e inglés.

No entra: formulario de contacto, chat, centro de ayuda con búsqueda, blog,
página de comisiones detallada. El contacto de esta historia son canales
directos; un formulario implica endpoint, antispam y aviso de privacidad propio,
y se decide aparte si hace falta.

## Criterios de aceptación

**Rutas y renderizado**

1. Dado un visitante, cuando abre `/como-funciona`, `/sobre-sastra`,
   `/preguntas-frecuentes` o `/contacto`, entonces recibe la página
   correspondiente con un `h1` propio y distinto en cada una.
2. Dadas las cuatro páginas, cuando se solicita el HTML al servidor sin ejecutar
   JavaScript, entonces el contenido completo ya viene dentro del documento.
3. Dadas las cuatro páginas, cuando se inspeccionan sus metadatos, entonces cada
   una declara su propio título y su propia descripción como claves de Transloco,
   resueltas durante el renderizado en servidor.
4. Dadas las cuatro páginas, cuando se cambia de español a inglés, entonces
   cambia el texto y no la dirección, igual que en el resto del sitio.

**Cómo funciona**

5. Dada `/como-funciona`, cuando se renderiza, entonces presenta dos recorridos
   separados y claramente rotulados, el del comprador y el del vendedor, y
   ninguno queda oculto tras una interacción que un buscador no pueda seguir.
6. Dado el recorrido del comprador, cuando se lee, entonces explica que paga
   producto más envío y que la comisión no se le suma (RN-027), y que el pago
   queda retenido hasta que confirma la entrega (RN-034).
7. Dado el recorrido del vendedor, cuando se lee, entonces explica que publicar
   es gratis, que la comisión es del 5% sobre el valor del producto y a su
   cargo, que el envío no entra en la base de cálculo (RN-026), y que hay que
   estar verificado para publicar (RN-011).
8. Dada la página, cuando menciona el porcentaje de la comisión, entonces la
   cifra sale de la configuración y no está escrita en la plantilla.
9. Dada la página, cuando describe funcionalidad que aún no existe —publicar,
   comprar, pagar—, entonces lo hace en presente descriptivo del producto sin
   prometer fechas. Ninguna página informativa anuncia un calendario.

**Sobre Sastra**

10. Dada `/sobre-sastra`, cuando se renderiza, entonces explica la promesa de
    respaldo y qué diferencia a la plataforma, y muestra razón social, NIT y
    dirección tomados de la configuración.
11. Dada la página, cuando se lee, entonces ningún texto comunica ganga,
    liquidación ni precio bajo. La promesa es seguridad (`docs/producto/vision.md`).

**Preguntas frecuentes**

12. Dada `/preguntas-frecuentes`, cuando se renderiza, entonces las preguntas
    están agrupadas por tema y cada una es un encabezado con su respuesta
    asociada.
13. Dada una pregunta, cuando el visitante llega con un fragmento en la
    dirección que la identifica, entonces esa pregunta queda visible y con el
    foco puesto en ella. Una respuesta concreta tiene que poder enviarse por
    enlace.
14. Dadas las preguntas plegables, cuando se navega solo con teclado, entonces
    todas se pueden abrir y cerrar, y su estado se anuncia a un lector de
    pantalla.
15. Dado el conjunto de respuestas, cuando se revisan una por una, entonces cada
    afirmación sobre comisión, pago, moderación, verificación o medios de pago
    corresponde a una regla de negocio existente. Ninguna respuesta inventa
    política.
16. Dada la pregunta sobre qué pasa si la prenda llega distinta a la foto,
    cuando se lee la respuesta, entonces explica que el pago queda retenido
    hasta que el comprador confirma la entrega, y que ante una diferencia se
    escribe a soporte **antes** de confirmar. No menciona devoluciones,
    reembolsos ni plazos para pedirlos: esa política no existe todavía.
17. Dada la pregunta sobre cuándo recibe su dinero el vendedor, cuando se lee la
    respuesta, entonces dice que el pago se libera al confirmarse la entrega
    (RN-034) y no da un número de días. El plazo se decide en Fase 3.
18. Dadas las respuestas, cuando se leen sin JavaScript, entonces el texto
    completo está en el documento aunque las preguntas se muestren plegadas.

**Contacto**

19. Dada `/contacto`, cuando se renderiza, entonces muestra el correo de soporte
    tomado de la configuración, como enlace `mailto:` y como texto legible.
20. Dada la página, cuando se lee la sección de derechos del titular, entonces
    indica que por ese mismo canal se ejercen los derechos a conocer,
    actualizar, rectificar y suprimir datos y a revocar la autorización, con los
    plazos de diez días hábiles para una consulta y quince para un reclamo
    (`docs/operacion/datos-personales.md:72-79`).
21. Dada la página, cuando el visitante ya tiene cuenta, entonces se le indica
    que nombre, ciudad, teléfono y correo los edita él mismo desde `/mi-cuenta`,
    con enlace directo. Escribir un correo para cambiar un dato que se cambia
    solo es hacerle perder el tiempo a dos personas.
22. Dada la página, cuando existen canales adicionales configurados, entonces se
    muestran; cuando no, la sección no aparece. Ningún canal está escrito en la
    plantilla.

**Transversales**

23. Dadas las cuatro páginas, cuando se auditan, entonces cada una tiene un solo
    `h1`, sin saltos de nivel, contraste mínimo de 4.5:1 y foco visible de 3px.
24. Dadas las cuatro páginas, cuando se renderizan a 360px de ancho, entonces no
    hay desplazamiento horizontal y los destinos táctiles miden 44px o más.
25. Dadas las cuatro páginas, cuando se recorren sus enlaces internos, entonces
    todos apuntan a rutas que existen. Ninguno lleva a catálogo, publicación ni
    búsqueda.

## Casos borde

- **Dirección con fragmento inexistente** en preguntas frecuentes: la página
  carga normalmente y no falla ni desplaza a ninguna parte.
- **Correo de soporte sin configurar**: la sección no se pinta vacía; el
  servidor lo registra como aviso, porque sin ese canal se incumple una
  obligación legal.
- **Texto largo en inglés** en los títulos de los recorridos: no debe romper la
  rejilla ni recortar palabras.
- **Enlace a una página informativa que todavía no existe** desde el pie o la
  portada: no se renderiza, no se deja roto.
- **Impresión de la página de contacto**: los correos y plazos siguen legibles
  sobre fondo blanco.

## Diseño

- Ancho máximo de contenido 1200px, con la columna de texto por debajo de eso:
  una línea de 200 caracteres no se lee.
- Tipo por clases de rol: `.tipo-h1`, `.tipo-h2`, `.tipo-entradilla`,
  `.tipo-cuerpo`, `.nota`. Ningún `font-size` propio.
- Separación entre secciones con `.regla-puntada`, el único elemento decorativo
  del sistema.
- Estas páginas **no llevan acento ocre**: el acento aparece una vez por
  pantalla y aquí la acción que importa es leer. Si una página termina con una
  llamada a registrarse, esa es la única y va como relleno con texto oscuro
  encima.
- Preguntas frecuentes con `details`/`summary` nativos antes que ARIA: el
  contenido queda en el documento aunque esté plegado, que es lo que resuelve
  los criterios 14 y 16 sin escribir estado.
- Sin datos remotos: ninguna de las cuatro páginas tiene estado de carga, vacío
  ni error.

## Notas técnicas

Sin backend nuevo: ni endpoints, ni tablas, ni migraciones.

Rutas nuevas, en español y con carga diferida como el resto:
`/como-funciona`, `/sobre-sastra`, `/preguntas-frecuentes`, `/contacto`. Sus
direcciones van en un módulo de `core` junto a `core/routes/legal-routes.ts`,
por el mismo motivo: las necesitan la tabla de rutas, el pie y la portada, y
ninguno de esos tres puede importar de una funcionalidad.

El texto va en Transloco, no en archivos versionados como los legales. La
diferencia es intencional: un documento legal se versiona porque hay que poder
demostrar qué texto se le enseñó a alguien; una página informativa no consiente
nada, y meterla en el mismo mecanismo obligaría a publicar un archivo por cada
corrección de una coma. Claves jerárquicas: `howItWorks.*`, `about.*`, `faq.*`,
`contact.*`, más `meta.*` de cada una.

Configuración: reutiliza los cuatro campos de empresa que HU-004 agrega a
`AppConfig` (`companyName`, `companyTaxId`, `companyAddress`, `supportEmail`).
Si se quieren canales adicionales —WhatsApp, Instagram— hacen falta variables
nuevas; hoy no existe ninguna en `docs/operacion/configuracion.md` y no se
inventan aquí.

Nueva funcionalidad `features/content` con las cuatro páginas. No cuelgan de
`features/legal`: comparten aspecto pero no mecanismo, y `legal` tiene resolutor
y versionado que estas no usan.

Dependencia con HU-004: el pie y la navegación del encabezado enlazan estas
páginas. Se puede implementar en cualquier orden gracias al criterio 7 de
HU-004, que no renderiza el enlace si la ruta no existe.

## Pruebas requeridas

- Componente (Vitest, sin red), por página: un solo `h1`; los textos que
  dependen de configuración se pintan desde `AppConfig` y se omiten cuando
  falta el valor; el enlace a `/mi-cuenta` aparece solo con sesión.
- Preguntas frecuentes: abrir y cerrar por teclado; el fragmento de la dirección
  deja visible y enfocada la pregunta correcta; el texto está en el DOM aunque
  esté plegado.
- Preguntas frecuentes, criterios 16 y 17: una prueba sobre el árbol de
  traducción que falle si alguna respuesta contiene «devolución», «reembolso» o
  un plazo en días. Es una guarda contra la corrección bienintencionada de
  dentro de seis meses, cuando nadie recuerde por qué esas palabras no estaban.
- Rutas: las cuatro resuelven, declaran título y descripción, y ninguna cae en
  la ruta comodín.
- Traducción: toda clave usada existe en `es.json` y en `en.json`, comparando
  los dos árboles.
- Servidor: el HTML servido de cada una contiene su contenido completo.
- Extremo a extremo (Playwright): recorrer portada → cómo funciona → registro; y
  un recorrido que verifique que ningún enlace de las cuatro páginas devuelve
  404.
- Accesibilidad automatizada sobre las cuatro páginas, en modo claro y oscuro.

## Qué habría que agregar antes de implementar

No lo agrego todavía; queda para decidir contigo.

**Reglas de negocio.** Ninguna nueva. Las dos preguntas que no tenían regla
detrás quedan resueltas por redacción, no por política nueva, y así se escriben
en los criterios 16 y 17.

**Modelo de datos.** Nada. Ninguna de las cuatro páginas guarda información de
nadie.

**Glosario.** Cerrado. **Respaldo** entra como `Backing`, junto a **retención
del pago** (`PaymentHold`), **liberación del pago** (`PaymentRelease`) y
**moderación** (`Moderation`). Son los términos que estas páginas repiten y que
tienen que decirse igual en todo el sitio.

**Configuración.** Variables para canales de contacto adicionales, si se quieren
mostrar. Y confirmar si `COMMISSION_RATE` se expone al frontend o si el texto se
redacta sin cifra, que es la misma decisión pendiente de HU-004.

**Documentación.** `docs/producto/alcance.md:26-32` describe el sitio informativo
en cinco viñetas; conviene que apunten a HU-004 y HU-005 ahora que existen.
