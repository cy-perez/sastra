#!/usr/bin/env python3
"""Reconstruye el kit de interfaz de Sendik desde cero.

    cd docs/ui/generador
    python3 construir.py

Todo sale de esta carpeta: no depende de nada instalado ni de conexion.

    tokens.json          los colores, la escala y las medidas. AQUI se edita.
    kit_ui.py            deriva los estados y escribe tokens.css, contraste.md,
                         tipografia.md, LEEME.md e index.html
    fuentes.py           descarga las tipografias (solo si hace falta rehacerlas)
    _plantilla_kit.html  el esqueleto de la guia
    marca/               los SVG del logo que usa la guia

El resultado se escribe en la carpeta de arriba (docs/ui/), sobrescribiendo el
kit construido.

Para cambiar un color: se edita tokens.json y se vuelve a correr esto. Los
estados (hover, pressed, foco, borde de campo, color de texto sobre cada fondo)
y el modo oscuro se recalculan solos. Editar tokens.css a mano es perder el
trabajo en el siguiente build.

Que NO se genera aqui
---------------------
`tipografia.css` y `marca.css` **no** salen del generador: son hojas del
proyecto y viven en `frontend/src/styles/`, que es donde se editan. El kit de
Sendik entrega una sola hoja, `tokens.css`, y ese es el unico archivo que
`publicar.py` copia al frontend.

Antes era al reves —las tres hojas se generaban aqui y se publicaban juntas—
porque el kit anterior las entregaba acopladas. Mantener esa cadena obligaba a
parchear a mano treinta fragmentos de la plantilla en cada entrega nueva, y era
justo lo que impedia reemplazar el kit cuando diseno mandaba una version nueva.
"""
import shutil
import subprocess
import sys
from pathlib import Path

AQUI = Path(__file__).resolve().parent
KIT = AQUI.parent

MARCA = "Sendik"
LOGO = AQUI / "marca" / "logo-horizontal.svg"
LOGO_OSCURO = AQUI / "marca" / "logo-mono-negativo.svg"

NECESARIOS = ["kit_ui.py", "_plantilla_kit.html", "tokens.json",
              "marca/logo-horizontal.svg", "marca/logo-mono-negativo.svg"]


def main() -> None:
    for necesario in NECESARIOS:
        if not (AQUI / necesario).exists():
            sys.exit("Falta " + necesario + " en la carpeta generador/. "
                     "El kit no se puede reconstruir sin ese archivo.")

    # --sin-generador: el generador ya vive aqui, en docs/ui/generador/. Sin la
    # bandera, kit_ui.py se copiaria a si mismo dentro de docs/ui/generador/
    # otra vez y dejaria una copia anidada que nadie mantiene.
    subprocess.run(
        [sys.executable, str(AQUI / "kit_ui.py"), str(AQUI / "tokens.json"),
         "--out", str(KIT), "--marca", MARCA,
         "--logo", str(LOGO), "--logo-oscuro", str(LOGO_OSCURO),
         "--sin-generador"],
        cwd=AQUI, check=True)

    for f in (AQUI / "marca").glob("*.svg"):
        shutil.copy(f, KIT / f.name)

    enlazar_fuentes_locales()

    print("\nListo. El kit esta en docs/ui/.")
    print("Siguiente paso: python3 verificar.py, o directamente publicar.py,")
    print("que encadena los tres.")


def enlazar_fuentes_locales() -> None:
    """Hace que la guia use los .woff2 del repositorio y no Google Fonts.

    kit_ui.py solo autoaloja las tipografias con --fuentes, y eso descarga de
    la red. Aqui no se descarga nada: los archivos ya estan versionados en
    docs/ui/fuentes/, que es como los entrego diseno. Se cambia el <link> y
    listo.

    Si algun dia hay que rehacerlos:

        python3 fuentes.py tokens.json --out ../fuentes
    """
    hoja = KIT / "fuentes.css"
    carpeta = KIT / "fuentes"
    indice = KIT / "index.html"

    if not hoja.exists() or not carpeta.is_dir():
        print("\n  AVISO: no hay fuentes autoalojadas en docs/ui/fuentes/.")
        print("         La guia queda enlazando a Google Fonts. El sitio no:")
        print("         frontend/src/styles/fuentes.css declara los .woff2 de")
        print("         frontend/public/fuentes/, que son los que se sirven.")
        return

    h = indice.read_text(encoding="utf-8")
    marca_google = '<link rel="preconnect" href="https://fonts.googleapis.com">'
    if marca_google not in h:
        # Ya venia autoalojada: nada que cambiar.
        return

    fin = '&display=swap">'
    corte = h.find(fin, h.find(marca_google))
    if corte == -1:
        sys.exit("La plantilla ya no enlaza Google Fonts como se esperaba.\n"
                 "Revisa enlazar_fuentes_locales() en construir.py.")
    h = (h[:h.find(marca_google)]
         + '<link rel="stylesheet" href="fuentes.css">'
         + h[corte + len(fin):])
    indice.write_text(h, encoding="utf-8")
    print("  index.html enlaza fuentes.css (autoalojadas)")


if __name__ == "__main__":
    main()
