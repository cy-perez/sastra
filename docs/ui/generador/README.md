# Generador del kit de Sastra

Esta carpeta es lo que faltaba en la entrega anterior. Sin ella, `tokens.css`
decía que era un archivo generado pero no había con qué regenerarlo, así que
cambiar un color obligaba a editar a mano justo el archivo que no se debe tocar.

**No necesita nada instalado. Solo Python 3.**

---

## Cambiar un color

```bash
cd generador
# edita tokens.json
python3 construir.py     # reconstruye el kit en la carpeta de arriba
python3 verificar.py     # comprueba el contraste en modo claro y oscuro
```

Eso es todo. Los estados —hover, pressed, deshabilitado, anillo de foco, borde
de campo y el color de texto que va sobre cada fondo— **se recalculan solos**
desde el color base. No se eligen a mano y no hay que tocarlos.

---

## Qué hay aquí

| Archivo | Qué es | ¿Se edita? |
|---|---|---|
| `tokens.json` | Colores, escala y medidas | **Sí. Es el origen de todo** |
| `fuentes/marca.css` | Componentes de Sastra y correcciones de modo oscuro | Sí |
| `fuentes/tipografia.css` | Roles de texto, escala fluida, carga de fuentes | Sí |
| `fuentes/LEEME.md` | La nota de entrega | Sí |
| `marca/*.svg` | Los logos | Se reemplazan |
| `construir.py` | Arma el kit | Solo si cambia la plantilla |
| `verificar.py` | Comprueba contraste en los dos modos | Al añadir componentes |
| `kit_ui.py` | Deriva los estados y escribe `tokens.css` | **No** |
| `_plantilla_kit.html` | Esqueleto de la guía | Rara vez |

`kit_ui.py` y `_plantilla_kit.html` vienen de la caja de herramientas de diseño
de interfaz. Se incluyen aquí a propósito, para que este kit **no dependa de
nada externo** y se pueda reconstruir dentro de cinco años.

---

## El bucle, y por qué importa

```
tokens.json  ──construir.py──▶  tokens.css     (generado, NO se edita)
                                index.html      (la guía visual)
                                contraste.md    (informe del modo claro)
                                      │
                                verificar.py ──▶ claro y oscuro, 18 pares cada uno
```

**Si se edita `tokens.css` a mano, el siguiente `construir.py` borra el cambio.**
No es un capricho: el punto del sistema es que un color viva en un solo sitio.

---

## Por qué hay dos verificadores y no uno

`kit_ui.py` escribe `contraste.md`, pero **solo comprueba el modo claro**. El
modo oscuro lo deriva a medias: calcula fondo, superficie, texto, borde y
primario, y deja intactos los colores semánticos, el anillo de foco y el borde
de campo, que se quedan con su valor de modo claro. Sobre fondo oscuro eso daba
entre 2.2:1 y 2.5:1 — ilegible. Esas correcciones viven en `fuentes/marca.css` y
**nadie las estaba comprobando**.

`verificar.py` cubre ese hueco: lee los colores reales de los tres CSS ya
construidos, arma la paleta de cada modo y comprueba 18 pares en cada uno.

Al escribirlo encontró un defecto que ninguna revisión anterior había visto: en
modo claro el anillo de foco es tinta **y la franja del hero también es tinta**,
así que el foco del CTA principal era invisible. De ahí salió la clase
`.franja-oscura`, que redefine `--color-foco` dentro del bloque para que
cualquier control que se meta ahí herede el anillo correcto sin acordarse.

---

## Al añadir un componente nuevo

Si el componente usa una combinación de colores que no existía, **añádela a la
lista `PARES` de `verificar.py`**. Un componente que no está en esa lista no se
está comprobando, y ese es justo el hueco por el que se cuela un texto ilegible.

---

## Lo que esto no comprueba

`verificar.py` mira **colores, no maqueta**. No sabe si un botón mide menos de
44px, si una tabla se desborda en un celular o si el titular se sale de la caja
al ampliar el texto. Eso hay que verlo:

1. Abre `index.html` en el navegador.
2. Pulsa «Cambiar modo».
3. Reduce la ventana por debajo de 640px.
4. Amplía el texto al 200% (`Ctrl` o `Cmd` con `+`).
5. Recorre la página entera con el tabulador y comprueba que **siempre se ve
   dónde está el foco**.

Los problemas de una maqueta se ven, no se deducen.
