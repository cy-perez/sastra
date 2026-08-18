#!/usr/bin/env python3
"""Verifica el contraste del kit en modo claro Y en modo oscuro.

    cd generador
    python3 verificar.py

Por que existe si kit_ui.py ya escribe contraste.md:

kit_ui.py solo comprueba los pares del MODO CLARO. El modo oscuro lo deriva
parcialmente —fondo, superficie, texto, borde y primario— y deja sin tocar los
colores semanticos, el anillo de foco y el borde de campo, que se quedan con su
valor de modo claro. Sobre fondo oscuro eso daba entre 2.2:1 y 2.5:1, ilegible.
Esas correcciones viven en fuentes/marca.css y NADIE las estaba comprobando.

Este script lee los colores reales de los dos archivos ya construidos, arma la
paleta de cada modo y comprueba los pares que de verdad se usan en pantalla.

No necesita nada instalado: solo Python 3.
Devuelve 0 si todo cumple y 1 si algo falla, para poder encadenarlo.
"""
import re
import sys
from pathlib import Path

AQUI = Path(__file__).resolve().parent
KIT = AQUI.parent

AA_TEXTO = 4.5   # texto normal
AA_GRANDE = 3.0  # texto grande (>=24px, o >=18.66px en negrita) e iconos
NO_APLICA = 0.0  # deshabilitado: exento por norma


# ---------------------------------------------------------------- color
def a_rgb(h):
    h = h.strip().lstrip("#")
    if len(h) == 3:
        h = "".join(c * 2 for c in h)
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def luminancia(rgb):
    def canal(v):
        v /= 255
        return v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4
    r, g, b = (canal(c) for c in rgb)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contraste(a, b):
    la, lb = luminancia(a_rgb(a)), luminancia(a_rgb(b))
    alto, bajo = max(la, lb), min(la, lb)
    return round((alto + 0.05) / (bajo + 0.05), 2)


# ------------------------------------------------------- lectura del CSS
BLOQUE = re.compile(r"([^{}]+)\{([^{}]*)\}", re.S)
VARIABLE = re.compile(r"(--[\w-]+)\s*:\s*(#[0-9A-Fa-f]{3,8})\s*(?:;|$)")


def leer_variables(ruta):
    """Devuelve (claro, oscuro): las variables de color de :root y las que
    sobrescribe [data-tema="oscuro"]."""
    if not ruta.exists():
        sys.exit("No existe " + str(ruta) + ". Corre primero construir.py.")
    texto = ruta.read_text(encoding="utf-8")
    texto = re.sub(r"/\*.*?\*/", "", texto, flags=re.S)   # fuera comentarios
    claro, oscuro = {}, {}
    for selector, cuerpo in BLOQUE.findall(texto):
        sel = selector.strip()
        destino = None
        if sel == ":root":
            destino = claro
        elif "data-tema" in sel and "oscuro" in sel:
            destino = oscuro
        if destino is None:
            continue
        for nombre, valor in VARIABLE.findall(cuerpo):
            destino[nombre] = valor
    return claro, oscuro


def paletas():
    """Arma la paleta final de cada modo respetando el orden de carga:
    tokens.css -> tipografia.css -> marca.css."""
    claro, oscuro = {}, {}
    for archivo in ["tokens.css", "tipografia.css", "marca.css"]:
        c, o = leer_variables(KIT / archivo)
        claro.update(c)
        oscuro.update(o)
    # el modo oscuro parte del claro y solo sobrescribe lo que redefine
    final_oscuro = dict(claro)
    final_oscuro.update(oscuro)
    return claro, final_oscuro


# ----------------------------------------------------------------- pares
# (descripcion, frente, fondo, umbral)
# Cada par corresponde a algo que de verdad se ve en pantalla. Si se anade un
# componente con una combinacion nueva, se anade aqui.
PARES = [
    ("Texto principal sobre el fondo",        "--color-texto",         "--color-fondo",            AA_TEXTO),
    ("Texto principal sobre tarjeta",         "--color-texto",         "--color-superficie",       AA_TEXTO),
    ("Texto secundario sobre el fondo",       "--color-texto-suave",   "--color-fondo",            AA_TEXTO),
    ("Texto secundario sobre tarjeta",        "--color-texto-suave",   "--color-superficie",       AA_TEXTO),
    ("Enlace y estructura sobre el fondo",    "--color-primario",      "--color-fondo",            AA_TEXTO),
    ("Texto del boton primario",              "--color-sobre-primario", "--color-primario",        AA_TEXTO),
    ("Texto del CTA sobre el ocre",           "--color-sobre-cta",     "--color-acento",           AA_TEXTO),
    ("Etiqueta sobre fondo de etiqueta",      "--color-texto",         "--color-primario-suave",   AA_TEXTO),
    ("Mensaje de exito sobre tarjeta",        "--color-exito",         "--color-superficie",       AA_TEXTO),
    ("Mensaje de aviso sobre tarjeta",        "--color-aviso",         "--color-superficie",       AA_TEXTO),
    ("Mensaje de error sobre tarjeta",        "--color-error",         "--color-superficie",       AA_TEXTO),
    ("Anillo de foco sobre el fondo",         "--color-foco",          "--color-fondo",            AA_GRANDE),
    ("Anillo de foco sobre tarjeta",          "--color-foco",          "--color-superficie",       AA_GRANDE),
    # El anillo se dibuja con outline-offset:2px, o sea SEPARADO del boton por
    # un hueco que deja ver el fondo. Por eso se compara contra el fondo de
    # alrededor y no contra el relleno del boton: contra el relleno daria un
    # falso negativo en oscuro, donde el anillo y el CTA son el mismo ocre.
    ("Anillo de foco sobre la franja oscura", "--color-foco-franja",   "--fondo-oscuro-marca",     AA_GRANDE),
    ("Borde de campo de formulario",          "--color-borde-control", "--color-superficie",       AA_GRANDE),
    # Las cajas de aviso y de error llevan fondo de tarjeta pero se apoyan sobre
    # el fondo de pagina: su borde linda con los dos y es informacion no textual.
    ("Borde de aviso sobre el fondo",         "--color-aviso",         "--color-fondo",            AA_GRANDE),
    ("Borde de error sobre el fondo",         "--color-error",         "--color-fondo",            AA_GRANDE),
    ("Texto sobre la franja oscura",          "--sobre-fondo-oscuro",  "--fondo-oscuro-marca",     AA_TEXTO),
    ("CTA sobre la franja oscura",            "--color-acento",        "--fondo-oscuro-marca",     AA_GRANDE),
    ("Texto del CTA en la franja oscura",     "--color-sobre-cta",     "--color-acento",           AA_TEXTO),
]


def revisar(modo, paleta):
    print("\n" + "=" * 74)
    print("MODO " + modo.upper())
    print("=" * 74)
    fallas, ausentes = [], []
    for desc, fg, bg, umbral in PARES:
        if fg not in paleta or bg not in paleta:
            ausentes.append((desc, fg if fg not in paleta else bg))
            continue
        r = contraste(paleta[fg], paleta[bg])
        cumple = r >= umbral
        marca = "ok   " if cumple else "FALLA"
        print("  {} {:>6}:1  (min {:.1f})  {:<38} {} sobre {}".format(
            marca, r, umbral, desc[:38], paleta[fg], paleta[bg]))
        if not cumple:
            fallas.append((desc, r, umbral, paleta[fg], paleta[bg]))
    for desc, falta in ausentes:
        print("  ?????            variable no encontrada: {}  ({})".format(falta, desc))
    return fallas, ausentes


def main():
    claro, oscuro = paletas()
    f1, a1 = revisar("claro", claro)
    f2, a2 = revisar("oscuro", oscuro)
    fallas, ausentes = f1 + f2, a1 + a2

    print("\n" + "=" * 74)
    if not fallas and not ausentes:
        print("Todo cumple: {} pares en modo claro y {} en oscuro.".format(
            len(PARES), len(PARES)))
        print("\nRecuerda que esto comprueba los COLORES, no la maqueta. Abre\n"
              "index.html, reduce la ventana a 360px, prueba el modo oscuro y\n"
              "amplia el texto al 200%: eso se ve, no se deduce.")
        return 0

    for desc, r, umbral, fg, bg in fallas:
        print("FALLA  {}: {}:1, necesita {}:1  ({} sobre {})".format(desc, r, umbral, fg, bg))
        print("       Se corrige en tokens.json si es un color de rol, o en\n"
              "       fuentes/marca.css si es una correccion de modo oscuro.")
    for desc, falta in ausentes:
        print("FALTA  la variable {} ({})".format(falta, desc))
    return 1


if __name__ == "__main__":
    sys.exit(main())
