# Manual de marca — Sastra

## Idea

Sastra es el mercado colombiano donde cualquiera compra y vende moda, nueva y
usada, con la plataforma como respaldo de la transaccion.

El nombre suena a **sastre**, y de ahi sale todo el sistema. Una **puntada** es
lo que une dos piezas separadas: exactamente lo que Sastra hace entre quien
vende y quien compra. Y ancla la marca en el mundo de la ropa y el arreglo, no
en el de la ganga. Es la diferencia que importa: la promesa de Sastra es
**seguridad**, no *barato*.

## La firma: la puntada

El isotipo es una **S geometrica construida con dos arcos identicos girados 180
grados**, de trazo constante 17 sobre un lienzo de 120. Las dos mitades
simetricas son las dos partes de la transaccion, unidas en la cintura: eso es
la costura.

La marca es **solida, sin interrupciones**. Se probaron dos variantes con la
cintura cortada por un tajo diagonal —una en el isotipo y otra dentro de las S
del logotipo— y ambas se descartaron: el corte penalizaba la legibilidad en
pequeno y sobre la tipografia leia como glifo roto. La version aprobada es la
solida.

La puntada sigue viva como **elemento del sistema**: la **regla divisoria** de
guion 16 y hueco 9 que separa secciones en la web, en los banners y en las
piezas de redes. Ese ritmo es lo que hace que todas las piezas se reconozcan
como de la misma familia. Es el unico elemento decorativo de la marca y no
conviene sustituirlo por una linea continua.

## Logo: variantes y cuando usar cada una

| Archivo | Uso |
|---|---|
| `logo-principal.svg` | La composicion preferida. Igual al horizontal. |
| `logo-horizontal.svg` | Barra de navegacion, cabeceras, firmas de correo. **Es el que mas vas a usar.** |
| `logo-vertical.svg` | Espacios cuadrados: fichas, sellos, pie de pagina centrado. |
| `isotipo.svg` | Solo el simbolo. Avatar de redes, icono de app, favicon, marca de agua. |
| `isotipo-negativo.svg` | El simbolo en blanco, para fondos oscuros. |
| `isotipo-app.svg` | El simbolo con el aire que exige Apple (arte al 80% del lienzo). Solo para generar iconos de app. |
| `logo-mono-positivo.svg` | Un solo color oscuro. Sello, factura, grabado laser, serigrafia a un color. |
| `logo-mono-negativo.svg` | Un solo color claro. Fondos oscuros, fotos, modo oscuro, vinilo de vehiculo. |

### Area de respeto

Deja libre, en los cuatro lados, **la mitad del ancho del isotipo**. Nada entra
ahi: ni texto, ni fotos, ni bordes de caja.

### Tamano minimo

Probado, no inventado:

| | Pantalla | Impreso |
|---|---|---|
| Lockup horizontal | 24 px de alto | 14 mm de ancho |
| Isotipo solo | 16 px | 6 mm |

Por debajo de 24 px de alto, **no uses el lockup: usa el isotipo solo**. El
isotipo es el mismo archivo a cualquier tamano: al ser solido no necesita una
version simplificada aparte, y se sostiene hasta los 16 px del favicon.

## Usos prohibidos

- No deformar ni estirar. Escala siempre proporcional.
- No rotar. La S vive en vertical.
- No recolorear fuera de la paleta. Nunca degradados dentro del logo.
- No anadir sombras, contornos, biseles ni brillos.
- No poner sobre fotos ocupadas sin una capa de tinta al 70% detras.
- No reencuadrar el lockup ni cambiar la distancia entre simbolo y palabra.
- No abrir cortes, huecos ni ranuras dentro de la S.
- No escribir el nombre en minusculas ni con otra tipografia.

## Color

| Rol | HEX | RGB | Sobre que fondo |
|---|---|---|---|
| Tinta (primario) | `#16192A` | 22, 25, 42 | Sobre hueso 17.0:1 · sobre hilo 7.3:1 |
| Tinta oscuro | `#0C0F1B` | 12, 15, 27 | Hover y fondo en modo oscuro |
| Tinta suave | `#2B3145` | 43, 49, 69 | Tarjetas en modo oscuro |
| Hilo (acento) | `#D69A3C` | 214, 154, 60 | **Solo como relleno**, con texto tinta encima |
| Hilo oscuro | `#8A5A12` | 138, 90, 18 | Texto ocre sobre claro: 5.9:1 |
| Hueso (fondo) | `#F7F5F1` | 247, 245, 241 | Fondo de pagina |
| Superficie | `#FFFFFF` | 255, 255, 255 | Tarjetas de producto |
| Texto suave | `#5B6072` | 91, 96, 114 | Sobre hueso 5.7:1 |
| Borde | `#E3DFD7` | 227, 223, 215 | Divisores de 1px |
| Verificado | `#1F7A55` | 31, 122, 85 | Sobre blanco 5.3:1 |
| Alerta | `#B3402A` | 179, 64, 42 | Sobre blanco 5.7:1 |

**La regla del acento:** el hilo aparece **una vez por pantalla**, en el boton
que quieres que la persona toque. Si esta en cinco sitios, no destaca ninguno.

**El error a evitar:** `#D69A3C` como texto sobre fondo claro da 2.5:1 y es
ilegible para mucha gente. Para texto ocre sobre claro, `#8A5A12`.

## Tipografia

| Uso | Familia | Licencia | Donde bajarla |
|---|---|---|---|
| Display y logotipo | **Archivo** | SIL OFL | fonts.google.com/specimen/Archivo |
| Interfaz y cuerpo | **Instrument Sans** | SIL OFL | fonts.google.com/specimen/Instrument+Sans |
| Precios y codigos | **IBM Plex Mono** | SIL OFL | fonts.google.com/specimen/IBM+Plex+Mono |

Las tres son de licencia libre: **no le cuestan nada al negocio, ni ahora ni
cuando crezca**, y se pueden empaquetar dentro de la app sin permiso de nadie.

El logotipo usa Archivo instanciada en **ancho 90 y peso 620**, en caja alta con
tracking de 0.08 em. Ese corte no existe descargable: ya viene convertido a
curvas en los SVG, asi que no necesitas la fuente para usar el logo.

Usa IBM Plex Mono para los precios: las cifras se alinean en columna y una lista
de productos se lee mucho mas rapido.

## Aplicacion: donde va cada archivo

### Web

Sube a la **raiz del sitio** (junto al `index.html`) todo lo que hay en
`dist/web/`: `favicon.ico`, `favicon.svg`, `apple-touch-icon.png`, los
`favicon-*.png`, `icon-192.png`, `icon-512.png`, `icon-512-maskable.png` y
`site.webmanifest`.

Luego pega en el `<head>` de tu HTML el contenido de
`dist/web/head-snippet.html`.

Para el logo del sitio usa `marca/logo-horizontal.svg` directamente con
`<img src="/logo-horizontal.svg" alt="Sastra">`. Es vectorial: pesa 4 KB y se ve
nitido en cualquier pantalla.

Para la regla divisoria de la firma, la clase `.regla-puntada` ya viene escrita
en `tokens.css`.

### App movil

**iOS:** arrastra la carpeta `dist/app/ios/` al Asset Catalog de Xcode
(`Assets.xcassets` → AppIcon). Ninguno tiene transparencia, que es lo que exige
App Store.

**Android:** copia cada carpeta `dist/app/android/mipmap-*` dentro de
`app/src/main/res/`. Despues crea `res/mipmap-anydpi-v26/ic_launcher.xml` con:

```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:mipmap="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>
```

y en `res/values/colors.xml` anade
`<color name="ic_launcher_background">#16192A</color>`.

`dist/app/android/play-store-512.png` es el que sube a la ficha de Play Store.

**React Native:** los SVG no funcionan de forma nativa. Instala
`react-native-svg` y convierte los archivos con `svgr`, o usa los PNG de
`dist/raster/`.

### Redes sociales

En `dist/social/` esta cada pieza compuesta para su lienzo, a sangre:

| Archivo | Donde |
|---|---|
| `og-image.png` | Etiqueta `og:image`: la imagen que aparece cuando comparten un link en WhatsApp o Facebook |
| `twitter-card.png` | Etiqueta `twitter:image` |
| `instagram-post.png` | Publicacion cuadrada |
| `instagram-story.png` | Historia y reel de portada |
| `linkedin-banner.png` | Portada de la pagina de empresa |
| `facebook-cover.png` | Portada de la pagina |

Para el **avatar** de Instagram, TikTok, Facebook y WhatsApp Business usa
`dist/app/android/play-store-512.png`: es el isotipo blanco sobre tinta, cuadrado
y sin transparencia, que es lo que piden todas esas plataformas.

### Impreso, empaque y letrero

Manda a la imprenta los **SVG**, nunca los PNG. Para bordado, vinilo de corte,
sello o serigrafia a un color usa `logo-mono-positivo.svg` o
`logo-mono-negativo.svg` segun el fondo.

## Sistema de iconos de interfaz

Si mas adelante dibujas iconos para la app, respeta estas cinco reglas o se vera
amateur:

- Reticula de **24 × 24**, area viva de 20 × 20.
- Trazo de **1.75 px, el mismo en todos**.
- Terminaciones rectas (`butt`) y uniones en angulo (`miter`), como los
  extremos del isotipo.
- `stroke="currentColor"` para que hereden el color y funcionen solos en modo
  claro, oscuro y deshabilitado.
- Radio de esquina de 4 px, el mismo del sistema.

## Descriptor

El descriptor que acompana al logo en las piezas es
**"Compra y vende moda con respaldo"**. Es una propuesta de trabajo, no una
marca registrada: dice la promesa (respaldo) y la categoria (moda) sin prometer
precio. Cambialo si encuentras uno mejor, pero manten la estructura: **verbo +
categoria + promesa**.
