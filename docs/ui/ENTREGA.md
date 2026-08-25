# Nota de entrega — Kit de interfaz Sendik

Lo marcado con ⚠️ lo debe confirmar el cliente antes de programar.

---

## Resumen

**Marca:** Sendik · **Negocio:** marketplace de moda (calzado, ropa, bolsos —
nuevo y usado) y tecnología (solo nueva), donde cualquier persona compra y
vende.
**Acción principal del sitio:** comprar.
**Personalidad:** término medio cercano↔serio · sobrio↔vibrante (hacia sobrio)
· tradicional↔tecnológico (término medio, sin modismos).
**Plataforma:** a medida ⚠️ — no se especificó; si el desarrollo termina siendo
sobre Shopify, WooCommerce u otro, el sistema de tokens funciona igual, pero
los nombres de las variables del tema de esa plataforma no son estos: hay que
mapearlos uno a uno.

**Archivos de marca recibidos:** el paquete completo de `diseno-de-marca`
(`sendik-marca.zip`), con las 13 variantes de logo en SVG, `manual.md`,
`tokens.json` y `tokens.css` ya verificados con contraste WCAG. No hubo
limitaciones: no hizo falta reconstruir nada ni aproximar colores desde un
raster.

---

## Decisiones de diseño

**Paleta** — origen: `tokens.json` y `manual.md` que entregó `diseno-de-marca`.
No se modificó ningún color de marca; se agregó el semántico `aviso`, que no
existía.

| Rol | HEX (claro) | HEX (oscuro) | Por qué |
|---|---|---|---|
| primario | `#14162B` | se aclara automático a `#83858E` | Tinta de marca. Botón principal, texto, header |
| acento (bronce) | `#8A6428` | `#B4884A` | El bronce tiene dos tonos porque ninguno de los dos pasa contraste en ambos fondos — ver "Ajuste especifico de marca Sendik" en `generador/kit_ui.py`. Un solo elemento por pantalla: la insignia de vendedor verificado |
| fondo | `#F6F6F8` | `#0D0E1C` | Fondo de página |
| superficie | `#FFFFFF` | `#2C2D40` | Tarjetas de producto |
| exito | `#1E7A5A` | `#4FBF92` | Pago aprobado, envío entregado |
| aviso | `#9C4A12` | `#E08A3C` | **Nuevo, no venía de marca.** Publicación en revisión, stock bajo. Matiz distinto al bronce (24° vs 35°) para que "aviso" y "verificado" no se confundan |
| error | `#B3261E` | `#F2837C` | Pago rechazado, publicación bloqueada |

**Tinta fija:** se agregó un token `--color-tinta` (`#14162B`, no cambia con
el modo) para el header oscuro del footer y el hero, porque el `primario`
normal se aclara en modo oscuro para seguir siendo legible como texto — y sin
un token aparte, el footer se hubiera visto casi blanco en modo oscuro,
invertido respecto al resto del sitio. Este ajuste solo existe en la copia de
`kit_ui.py` que va dentro de este kit.

**Tipografía**

| Rol | Familia | Pesos | Licencia | Origen |
|---|---|---|---|---|
| Titulares y precios grandes | Archivo | 600 | SIL OFL 1.1 | Google Fonts, autoalojada en `fuentes/` |
| Texto e interfaz | Inter | 400, 500, 600 | SIL OFL 1.1 | Google Fonts, autoalojada en `fuentes/` |

Instalación: `<link rel="stylesheet" href="fuentes.css">` antes de
`tokens.css`. Ficha completa en `kit/tipografia.md`. Ambas son gratuitas de
uso comercial — no hay licencia que comprar.

**Radio:** 4px en botones y campos, 8px en tarjetas, deliberadamente bajo —
"la marca necesita leer fiable, no amable" (manual de marca).

**Rasgo propio:** la insignia de vendedor verificado (línea de bronce + ícono,
nunca relleno) es la única pieza de interfaz nueva que no sale de un patrón
genérico de e-commerce: es la traducción directa de "confianza" — la promesa
central del negocio — a un componente visual. El corte del isotipo se repite
una sola vez, como `.regla-corte`, entre la sección de diferenciadores y el
footer.

---

## Entregables

```
kit/
├── index.html       guía visual — ábrela en el navegador
├── tokens.css        variables de INTERFAZ (GENERADO, no editar)
├── tokens.json        las decisiones de interfaz — este es el que se edita
├── tipografia.md      familias, pesos, licencia e instalación
├── contraste.md        informe WCAG
├── fuentes.css / fuentes/     tipografías autoalojadas + licencias OFL
├── generador/          kit_ui.py (con el ajuste de Sendik), fuentes.py, plantilla
├── marca/              paquete completo de diseno-de-marca: los 13 SVG del
│                        logo, manual.md, favicon, banner, íconos de app,
│                        y tokens-marca.json/.css originales
└── ENTREGA.md           este archivo
```

**Para cambiar cualquier decisión:** edita `tokens.json` y ejecuta el comando
que aparece en `LEEME.md` (incluye `--logo` y `--logo-oscuro`, para que la
guía conserve el logo correcto en cada modo). Los estados, el modo oscuro y el
informe de contraste se recalculan solos. No edites `tokens.css` a mano.

**Resultado del informe de contraste:** sin fallas. Los ocho pares que se usan
de verdad en la interfaz cumplen AA o AAA — ver `contraste.md`.

---

## Para quien programa

1. Copia `tokens.css` al proyecto y enlázalo **antes** de tus estilos.
2. Usa siempre variables; ningún HEX ni píxel suelto en el código.
3. Carga la tipografía con `fuentes.css` **antes** de `tokens.css` (o el
   enlace de Google Fonts que también deja `tipografia.md`, si prefieres no
   autoalojar).
4. Modo oscuro: `data-tema="oscuro"` en el `<html>`. El logo debe cambiar de
   variante con el modo — la lógica exacta está en `index.html`, sección
   `.logo-claro` / `.logo-oscuro`.
5. **Header:** 72px de alto en escritorio (56px en móvil), fijo al hacer
   scroll, logo horizontal a 34px de alto. En móvil el logo se reduce al
   isotipo (32px) y la búsqueda pasa a una segunda fila a ancho completo — es
   la acción más usada del header en un marketplace, así que se le da el
   espacio principal en vez de dejarla como ícono.
6. **Ancho máximo de contenido:** 1140px.
7. **Puntos de quiebre:** móvil < 640px · tableta 640–1024px · escritorio >
   1024px.
8. **Imágenes que debe entregar cada vendedor:** fotos de producto en
   proporción 1:1, mínimo 800×800px, fondo neutro o el real del producto —
   nunca texto incrustado en la imagen. Esto no es una decisión de diseño
   pendiente: es una regla que hay que hacer cumplir en el formulario de
   publicación, o la rejilla de producto se descuadra. ⚠️
9. **Condición del producto** (nuevo/usado) es un campo obligatorio en el
   formulario de publicación, y tecnología solo admite "nuevo" — esa
   restricción de negocio hay que validarla en el backend, no solo ocultar la
   opción en el frontend. ⚠️
10. **Insignia de vendedor verificado:** aparece solo cuando el backend marca
    al vendedor como verificado. No es un dato de presentación, depende del
    proceso de verificación que la plataforma tiene que definir aparte. ⚠️

---

## Pendientes antes de publicar

- [x] Tipografía confirmada — venía definida en el manual de marca.
- [x] Verificar el paquete: se copió aparte, se cambió el primario en
      `tokens.json`, se regeneró y funcionó — ver el propio historial de este
      encargo.
- [ ] Reemplazar los textos de muestra (títulos de producto, precios, nombres
      de vendedor, "María G.", "TecnoDirecto", el titular del hero) por los
      definitivos — usar `copywriter-web`. **Todo lo que dice "de muestra" en
      `index.html` hay que reemplazarlo.** ⚠️
- [ ] Confirmar la plataforma de desarrollo (a medida / Shopify / WooCommerce)
      para saber si los tokens se pegan directo o hay que mapearlos al tema.⚠️
- [ ] Definir el proceso real de "vendedor verificado" (qué lo activa, quién
      lo aprueba) — el diseño ya tiene el componente, falta la regla de
      negocio detrás. ⚠️
- [ ] Confirmar los textos legales del footer: NIT y razón social reales (se
      usó un NIT de muestra), política de datos y términos. ⚠️
- [ ] Verificar el logo sobre el header en modo oscuro — ya resuelto en el kit
      (variante mono-negativa), pero confírmalo también en el sitio real una
      vez integrado.
- [ ] Recorrer la lista de `accesibilidad-y-verificacion.md` de la skill antes
      de publicar (foco visible, destinos táctiles de 44px, texto al 200%).
