#!/usr/bin/env python3
"""Genera el kit de interfaz a partir de tokens.json.

Uso:
    python3 kit_ui.py tokens.json --out kit --marca "Mi Marca" --logo logo.svg

Produce:
    kit/tokens.css   variables listas para el proyecto, con modo oscuro
    kit/index.html   guia visual completa: paleta, tipografia, componentes,
                     y maqueta de header, body y footer
    kit/contraste.md informe WCAG de los pares que se usan de verdad

Los estados (hover, pressed, disabled, foco) y el color de texto sobre cada
fondo se calculan aqui: son derivados, no decisiones sueltas, y calcularlos
evita que en la maqueta aparezca un boton con texto ilegible.
"""
import argparse, json, shutil
from pathlib import Path

# ---------- color ----------

def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))

def hexa(t):
    return "#{:02X}{:02X}{:02X}".format(*[max(0, min(255, int(round(c)))) for c in t])

def mezclar(a, b, p):
    """Mezcla a con b en proporcion p (0 = a, 1 = b)."""
    ra, rb = rgb(a), rgb(b)
    return hexa(tuple(ra[i] + (rb[i] - ra[i]) * p for i in range(3)))

def luminancia(h):
    f = lambda c: c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = [c / 255 for c in rgb(h)]
    return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b)

def contraste(a, b):
    la, lb = luminancia(a), luminancia(b)
    hi, lo = max(la, lb), min(la, lb)
    return round((hi + 0.05) / (lo + 0.05), 2)

def texto_sobre(fondo, claro="#FFFFFF", oscuro="#111111"):
    """Elige el color de texto legible sobre un fondo dado."""
    return claro if contraste(fondo, claro) >= contraste(fondo, oscuro) else oscuro

def nivel(r, grande=False):
    if grande:
        return "AAA" if r >= 4.5 else "AA" if r >= 3 else "FALLA"
    return "AAA" if r >= 7 else "AA" if r >= 4.5 else "FALLA"

# ---------- tokens ----------

BASE = {
    "primario": "#0F3D2E", "acento": "#8CE0B0", "fondo": "#FBFAF7",
    "superficie": "#FFFFFF", "texto": "#161A18", "texto_suave": "#5C6560",
    "borde": "#E4E7E5", "exito": "#1E7B4D", "aviso": "#B4690E", "error": "#B3261E",
}

def derivar(t):
    c = dict(BASE)
    c.update({k.replace("-", "_"): v for k, v in (t.get("color") or {}).items()
              if isinstance(v, str)})
    for k, v in (t.get("color") or {}).items():
        if isinstance(v, dict) and "hex" in v:
            c[k.replace("-", "_")] = v["hex"]
    d = dict(c)
    d["primario_hover"]    = mezclar(c["primario"], "#000000", 0.12)
    d["primario_pressed"]  = mezclar(c["primario"], "#000000", 0.22)
    d["primario_suave"]    = mezclar(c["primario"], "#FFFFFF", 0.90)
    d["sobre_primario"]    = texto_sobre(c["primario"])
    d["sobre_acento"]      = texto_sobre(c["acento"])
    d["deshabilitado"]     = mezclar(c["texto_suave"], c["fondo"], 0.55)
    d["foco"]              = c["acento"] if contraste(c["acento"], c["fondo"]) >= 3 else c["primario"]
    # El divisor decorativo puede ser sutil; el borde de un campo de formulario es
    # el unico indicador visual del control y necesita 3:1. Se oscurece hasta lograrlo.
    bc = c["borde"]
    while contraste(bc, c["superficie"]) < 3 and luminancia(bc) > 0.02:
        bc = mezclar(bc, "#000000", 0.10)
    d["borde_control"] = bc
    # modo oscuro derivado
    d["o_fondo"]      = mezclar(c["texto"], "#000000", 0.35)
    d["o_superficie"] = mezclar(c["texto"], "#FFFFFF", 0.10)
    d["o_texto"]      = mezclar(c["fondo"], "#FFFFFF", 0.30)
    d["o_texto_suave"]= mezclar(c["fondo"], "#000000", 0.35)
    d["o_borde"]      = mezclar(c["texto"], "#FFFFFF", 0.22)
    op = c["primario"]
    while contraste(op, d["o_fondo"]) < 4.5 and luminancia(op) < 0.75:
        op = mezclar(op, "#FFFFFF", 0.12)
    d["o_primario"]   = op
    d["o_sobre_primario"] = texto_sobre(op)
    return d

def esc(t):
    esc_ = {"escala_px": {"xs":12,"sm":14,"base":16,"lg":20,"xl":26,"2xl":34,"3xl":46,"4xl":62}}
    tip = t.get("tipografia") or {}
    return {**esc_, **tip}

# ---------- salidas ----------

def css(d, tip, esp, rad):
    L = ["/* Generado por kit_ui.py. Los estados son derivados: no los edites a mano,",
         "   cambia el color base y vuelve a generar. */", ":root {"]
    for k in ["primario","primario_hover","primario_pressed","primario_suave","sobre_primario",
              "acento","sobre_acento","fondo","superficie","texto","texto_suave","borde",
              "exito","aviso","error","deshabilitado","foco","borde_control"]:
        L.append("  --color-{}: {};".format(k.replace("_","-"), d[k]))
    fam_t = tip.get("texto",{}).get("familia","system-ui")
    fam_d = tip.get("display",{}).get("familia", fam_t)
    L.append('  --fuente-texto: "{}", system-ui, sans-serif;'.format(fam_t))
    L.append('  --fuente-display: "{}", var(--fuente-texto);'.format(fam_d))
    for k, v in esc(tip)["escala_px"].items():
        L.append("  --texto-{}: {}px;".format(k, v))
    for v in esp:
        L.append("  --esp-{}: {}px;".format(v, v))
    for k, v in rad.items():
        L.append("  --radio-{}: {}px;".format(k, v))
    L += ["  --sombra-sm: 0 1px 2px rgba(0,0,0,.06);",
          "  --sombra-md: 0 4px 12px rgba(0,0,0,.08);",
          "  --sombra-lg: 0 12px 32px rgba(0,0,0,.12);",
          "  --ancho-max: 1140px;", "}", "",
          '[data-tema="oscuro"] {']
    for k, v in [("fondo","o_fondo"),("superficie","o_superficie"),("texto","o_texto"),
                 ("texto-suave","o_texto_suave"),("borde","o_borde"),("primario","o_primario"),
                 ("sobre-primario","o_sobre_primario")]:
        L.append("  --color-{}: {};".format(k, d[v]))
    L += ["}", "", "/* Respeta a quien pide menos movimiento */",
          "@media (prefers-reduced-motion: reduce) {",
          "  * { animation-duration: .01ms !important; transition-duration: .01ms !important; }", "}"]
    return "\n".join(L) + "\n"

PARES = [("texto","fondo","Texto principal sobre fondo",False),
         ("texto","superficie","Texto sobre tarjeta",False),
         ("texto_suave","fondo","Texto secundario sobre fondo",False),
         ("sobre_primario","primario","Texto del boton principal",False),
         ("primario","fondo","Primario como texto o icono sobre fondo",True),
         ("sobre_acento","acento","Texto sobre acento",False),
         ("error","fondo","Mensaje de error sobre fondo",False),
         ("borde_control","superficie","Borde de campo de formulario",True)]

def informe(d):
    fil, fallas = [], 0
    for a, b, desc, grande in PARES:
        r = contraste(d[a], d[b])
        n = nivel(r, grande)
        if n == "FALLA":
            fallas += 1
        fil.append((desc, d[a], d[b], r, n, grande))
    L = ["# Informe de contraste", "",
         "Umbrales WCAG 2.1: 4.5:1 para texto normal, 3:1 para texto grande (24px o",
         "19px en negrita), iconos y bordes de control.", "",
         "| Par | Frente | Fondo | Ratio | Nivel |", "|---|---|---|---|---|"]
    for desc, fa, fb, r, n, g in fil:
        L.append("| {} | `{}` | `{}` | {}:1 | {}{} |".format(
            desc, fa, fb, r, n, " (texto grande)" if g else ""))
    L.append("")
    if fallas:
        L += ["## {} par(es) no pasan".format(fallas), "",
              "No lo entregues asi. Opciones, de menos a mas invasiva:", "",
              "1. Oscurece el color de texto, no aclares el fondo: preserva mejor la marca.",
              "2. Usa la variante oscura del primario para texto y deja el primario",
              "   original solo como fondo de boton.",
              "3. Si el acento de marca no alcanza, no lo uses para texto: reservalo",
              "   para fondos, subrayados y detalles graficos.", "",
              "El color de marca no es excusa: un texto que no se lee no comunica la marca."]
    else:
        L.append("Todos los pares en uso cumplen el umbral que les corresponde.")
    return "\n".join(L) + "\n", fallas

def html(d, tip, marca, logo_rel, informe_md):
    e = esc(tip)["escala_px"]
    fam_t = tip.get("texto",{}).get("familia","system-ui")
    fam_d = tip.get("display",{}).get("familia", fam_t)
    filas_pal = "".join(
        '<div class="ficha"><div class="muestra" style="background:{h}"></div>'
        '<b>{k}</b><code>{h}</code></div>'.format(k=k.replace("_","-"), h=d[k])
        for k in ["primario","primario_hover","acento","fondo","superficie","texto",
                  "texto_suave","borde","exito","aviso","error"])
    filas_tipo = "".join(
        '<tr><td><code>--texto-{k}</code></td><td>{v}px</td>'
        '<td style="font-size:{v}px;line-height:1.25;font-family:var(--fuente-display)">Ag</td></tr>'.format(k=k, v=v)
        for k, v in e.items())
    marca_logo = ('<img src="{}" alt="Logo de {}" style="height:34px">'.format(logo_rel, marca)
                  if logo_rel else '<span class="logotexto">{}</span>'.format(marca))
    tabla_contraste = "\n".join(l for l in informe_md.splitlines() if l.startswith("|"))
    filas = []
    for l in tabla_contraste.splitlines()[2:]:
        c = [x.strip() for x in l.strip("|").split("|")]
        if len(c) >= 5:
            estado = "falla" if "FALLA" in c[4] else "ok"
            filas.append("<tr class='{}'><td>{}</td><td><code>{}</code></td><td><code>{}</code></td>"
                         "<td>{}</td><td>{}</td></tr>".format(estado, c[0], c[1].strip("`"),
                                                              c[2].strip("`"), c[3], c[4]))
    plantilla = Path(__file__).with_name("_plantilla_kit.html").read_text(encoding="utf-8")
    for k, v in {
        "__MARCA__": marca, "__LOGO__": marca_logo, "__PALETA__": filas_pal,
        "__TIPO__": filas_tipo, "__CONTRASTE__": "".join(filas),
        "__FAM_TEXTO__": fam_t, "__FAM_DISPLAY__": fam_d,
    }.items():
        plantilla = plantilla.replace(k, v)
    return plantilla

def main():
    p = argparse.ArgumentParser(description="Genera el kit de interfaz desde tokens.json")
    p.add_argument("tokens")
    p.add_argument("--out", default="kit")
    p.add_argument("--marca", default=None)
    p.add_argument("--logo", default=None, help="SVG o PNG del logo, se copia al kit")
    args = p.parse_args()

    t = json.loads(Path(args.tokens).read_text(encoding="utf-8"))
    marca = args.marca or t.get("marca", "Marca")
    d = derivar(t)
    tip = t.get("tipografia") or {}
    esp = t.get("espaciado_px") or [4, 8, 12, 16, 24, 32, 48, 64, 96]
    rad = t.get("radio_px") or {"sm": 6, "md": 10, "lg": 16, "completo": 9999}

    out = Path(args.out); out.mkdir(parents=True, exist_ok=True)
    (out / "tokens.css").write_text(css(d, tip, esp, rad), encoding="utf-8")
    inf, fallas = informe(d)
    (out / "contraste.md").write_text(inf, encoding="utf-8")

    logo_rel = None
    if args.logo and Path(args.logo).exists():
        destino = out / Path(args.logo).name
        shutil.copy(args.logo, destino)
        logo_rel = destino.name

    (out / "index.html").write_text(html(d, tip, marca, logo_rel, inf), encoding="utf-8")

    print("Kit generado en " + str(out))
    for f in ["index.html", "tokens.css", "contraste.md"]:
        print("  - " + f)
    if fallas:
        print("\n  ATENCION: {} par(es) de color no pasan contraste.".format(fallas))
        print("  Revisa contraste.md y corrige antes de entregar.")
    else:
        print("\n  Contraste: todos los pares en uso cumplen.")

if __name__ == "__main__":
    main()
