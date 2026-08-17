#!/usr/bin/env python3
"""Reconstruye el kit y lo publica en el frontend, en un solo comando.

    cd docs/ui/generador
    python3 publicar.py

Encadena los tres pasos que si se hacen por separado terminan olvidados:

    construir.py   regenera tokens.css, index.html y contraste.md en docs/ui/
    verificar.py   comprueba el contraste en modo claro y oscuro
    (este script)  copia las tres hojas a frontend/src/styles/

Sin el tercer paso, el sitio sigue sirviendo la version anterior del sistema y
nadie se entera hasta que un color no cuadra en pantalla.

Este script NO es del kit: es del proyecto. construir.py, verificar.py y
kit_ui.py llegan tal como los entrego diseno y no se modifican, para que la
proxima entrega del kit se pueda reemplazar sin perder cambios.

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

HOJAS = ["tokens.css", "tipografia.css", "marca.css"]


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
        "\nListo. Recuerda que fuentes.css NO se toca aqui: es del proyecto, no\n"
        "del kit, y declara los .woff2 de frontend/public/fuentes/."
    )


if __name__ == "__main__":
    main()
