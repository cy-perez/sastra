#!/usr/bin/env python3
"""Reconstruye el kit y lo publica en el frontend, en un solo comando.

    cd docs/ui/generador
    python3 publicar.py

Encadena los tres pasos que si se hacen por separado terminan olvidados:

    construir.py   regenera tokens.css, index.html, contraste.md y tipografia.md
                   en docs/ui/
    verificar.py   comprueba el contraste en modo claro y oscuro, ya con las
                   hojas del proyecto encima
    (este script)  copia tokens.css a frontend/src/styles/

Sin el tercer paso, el sitio sigue sirviendo la version anterior del sistema y
nadie se entera hasta que un color no cuadra en pantalla.

Una sola hoja y no tres
-----------------------
El kit de Sendik entrega `tokens.css` y nada mas. `tipografia.css` y `marca.css`
son del proyecto: viven en `frontend/src/styles/`, se editan ahi y no se
publican desde aqui porque no hay nada de donde publicarlas.

Este script NO es del kit: es del proyecto. construir.py y verificar.py tambien;
kit_ui.py, fuentes.py y _plantilla_kit.html llegan tal como los entrego diseno y
no se modifican, para que la proxima entrega del kit se pueda reemplazar sin
perder cambios.

Con --solo-copiar se salta la reconstruccion y la verificacion. Util cuando ya
se corrieron a mano; no es el camino normal.
"""
import shutil
import subprocess
import sys
from pathlib import Path

AQUI = Path(__file__).resolve().parent
KIT = AQUI.parent                        # docs/ui
RAIZ = KIT.parent.parent                 # raiz del repositorio
DESTINO = RAIZ / "frontend" / "src" / "styles"

HOJAS = ["tokens.css"]


def paso(script: str) -> None:
    print(f"\n--- {script} ---")
    resultado = subprocess.run([sys.executable, str(AQUI / script)], cwd=AQUI)
    if resultado.returncode != 0:
        sys.exit(
            f"\n{script} fallo. No se publica nada: el frontend se queda con la\n"
            "version anterior, que al menos es coherente. Corrige y vuelve a correr."
        )


def main() -> None:
    solo_copiar = "--solo-copiar" in sys.argv

    if not solo_copiar:
        paso("construir.py")
        paso("verificar.py")

    if not DESTINO.is_dir():
        sys.exit(
            f"No existe {DESTINO}.\n"
            "Se esperaba el proyecto de Angular en frontend/. Si aun no esta\n"
            "creado, el kit ya quedo construido en docs/ui/ y se copia despues."
        )

    print(f"\n--- publicando en {DESTINO.relative_to(RAIZ)} ---")
    for hoja in HOJAS:
        origen = KIT / hoja
        if not origen.exists():
            sys.exit(f"Falta {origen}. Corre construir.py primero.")
        shutil.copy(origen, DESTINO / hoja)
        print(f"  {hoja}")

    print(
        "\nListo. Recuerda lo que NO se toca aqui:\n"
        "  fuentes.css     es del proyecto y declara los .woff2 de\n"
        "                  frontend/public/fuentes/\n"
        "  tipografia.css  es del proyecto: la unica fuente de verdad del tipo\n"
        "  marca.css       es del proyecto: componentes propios y ajustes de\n"
        "                  modo oscuro que el generador no cubre"
    )


if __name__ == "__main__":
    main()
