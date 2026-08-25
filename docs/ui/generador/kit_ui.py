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

--- Copia especifica de Sendik ---
Esta copia trae un agregado sobre la version original del generador: el rol
"acento" cambia de tono entre modo claro y oscuro (ver derivar(), bloque
"Ajuste especifico de marca Sendik"), porque el bronce de Sendik necesita dos
tonos distintos segun el fondo y el generador generico solo maneja uno. Los
semanticos "exito", "aviso" y "error" tambien llevan variante propia en modo
oscuro en vez de reusar el valor de modo claro. Si se reemplaza este archivo
por una version mas nueva del generador de la skill, hay que volver a aplicar
este ajuste o el modo oscuro pierde esas variantes.
"""
import argparse, json, shutil, sys
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

    # --- Ajuste especifico de marca Sendik: el bronce tiene DOS tonos porque
    # ninguno de los dos pasa contraste en ambos fondos (ver manual.md). El rol
    # "acento" de este generador es unico, asi que aqui se resuelve cual tono va
    # en cada modo, en vez de dejar que un solo valor falle en uno de los dos:
    #   - claro (fondo F6F6F8): usa el bronce oscuro '#8A6428' (acento-oscuro)
    #   - oscuro (fondo casi negro): usa el bronce claro '#B4884A' (acento),
    #     que es justo el valor original de marca.
    # Si 'acento-oscuro' o los '..._modo_oscuro' no vienen en tokens.json, cae
    # al comportamiento generico de kit_ui.py (un solo acento en ambos modos).
    if "acento_oscuro" in c:
        d["acento"] = c["acento_oscuro"]
        d["sobre_acento"] = texto_sobre(d["acento"])
    d["o_acento"] = c.get("acento_modo_oscuro", d["acento"])
    d["o_sobre_acento"] = texto_sobre(d["o_acento"])
    d["o_exito"] = c.get("exito_modo_oscuro", c["exito"])
    d["o_aviso"] = c.get("aviso_modo_oscuro", c.get("aviso", BASE["aviso"]))
    d["o_error"] = c.get("error_modo_oscuro", c["error"])

    # Tinta fija: el primario "normal" se aclara en modo oscuro para poder
    # seguir usandolo como texto/borde (ver o_primario mas arriba). Eso deja
    # sin token un caso distinto: superficies que la marca quiere SIEMPRE en
    # tinta oscura, sin importar el modo — el pie de pagina y el hero, segun
    # header-body-footer.md ("el footer suele ir sobre fondo oscuro"). Sin
    # este token esas piezas heredarian el primario aclarado y en modo oscuro
    # el pie de pagina se veria casi blanco, invertido respecto al resto del
    # sitio. "tinta" no cambia con el modo a proposito.
    d["tinta"] = c["primario"]
    d["sobre_tinta"] = texto_sobre(c["primario"])
    return d

def escala(t):
    base = {"escala_px": {"xs":12,"sm":14,"base":16,"lg":20,"xl":26,"2xl":34,"3xl":46,"4xl":62}}
    tip = t.get("tipografia") or {}
    return {**base, **tip}

def escapar(s):
    """Escapa para HTML. Ojo: esc() en este archivo es la escala tipografica."""
    return (str(s).replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace('"', "&quot;"))

PLACEHOLDERS = ("NOMBRE", "XXX", "TODO", "PENDIENTE", "TU_", "FAMILIA")

def sin_definir(nombre):
    if not nombre or not str(nombre).strip():
        return True
    n = str(nombre).upper()
    return any(pl in n for pl in PLACEHOLDERS)

def pila(familia, tipo):
    """Pila de respaldo. Si la fuente no carga, que caiga en algo del mismo genero."""
    if tipo == "mono":
        return '"{}", ui-monospace, SFMono-Regular, Menlo, monospace'.format(familia)
    if tipo == "display":
        return '"{}", Georgia, "Times New Roman", serif'.format(familia)
    return '"{}", system-ui, -apple-system, "Segoe UI", Roboto, sans-serif'.format(familia)

def url_google(familias_pesos):
    """URL de Google Fonts para las familias pedidas."""
    partes = []
    for fam, pesos in familias_pesos:
        ps = ";".join(str(x) for x in sorted(set(pesos)))
        partes.append("family=" + fam.replace(" ", "+") + ":wght@" + ps)
    return "https://fonts.googleapis.com/css2?" + "&".join(partes) + "&display=swap"

def resolver_tipografia(t):
    """Devuelve la especificacion tipografica y avisa de lo que falte.

    El fallo que esto evita: que el kit se entregue con NOMBRE_DISPLAY escrito
    literalmente, o con la fuente correcta en el CSS pero sin cargarla, de modo
    que el cliente abre el entregable y no ve la tipografia por ningun lado.
    """
    tip = t.get("tipografia") or {}
    pesos = list((tip.get("pesos") or {"regular": 400, "medio": 500, "fuerte": 700}).values())
    spec = {"avisos": [], "familias": [], "pendiente": False}
    for rol, tipo in (("texto", "texto"), ("display", "display"), ("mono", "mono")):
        d = tip.get(rol) or {}
        fam = d.get("familia")
        if rol == "mono" and not fam:
            continue
        if sin_definir(fam):
            spec["pendiente"] = True
            spec["avisos"].append(
                "La familia de '{}' no esta definida (aparece como '{}').".format(rol, fam))
            fam = "Inter" if rol != "display" else "Fraunces"
            spec["avisos"].append("  Se uso '{}' de forma provisional. Cambiala antes de entregar.".format(fam))
        spec[rol] = {
            "familia": fam,
            "pila": pila(fam, tipo),
            "licencia": d.get("licencia", "por confirmar"),
            "origen": d.get("origen", "google"),
            "uso": d.get("uso", ""),
            "pesos": d.get("pesos", pesos if rol != "display" else [p for p in pesos if p >= 500] or [700]),
        }
        spec["familias"].append((fam, spec[rol]["pesos"]))
    if tip.get("confirmada") is False:
        spec["pendiente"] = True
        spec["avisos"].append("tokens.json marca la tipografia como no confirmada ('confirmada': false).")
    spec["url_google"] = url_google([(f, p) for f, p in spec["familias"]
                                     if spec.get("texto", {}).get("origen", "google") == "google"])
    spec["pesos"] = pesos
    return spec

# ---------- salidas ----------

def css(d, tip, esp, rad, tipo):
    L = ["/* Generado por kit_ui.py. Los estados son derivados: no los edites a mano,",
         "   cambia el color base y vuelve a generar. */",
         "",
         "/* TIPOGRAFIA — la fuente debe cargarse o el navegador usara la de respaldo.",
         "   Pega esto en el <head>, ANTES de esta hoja de estilos:",
         "",
         '   <link rel="preconnect" href="https://fonts.googleapis.com">',
         '   <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>',
         '   <link rel="stylesheet" href="' + tipo["url_google"] + '">',
         "",
         "   Alternativa autoalojada (mas rapida y sin dependencia externa):",
         "   descarga los .woff2, declara @font-face con font-display: swap y",
         "   apunta las variables de abajo a esas familias. */",
         "",
         ":root {"]
    for k in ["primario","primario_hover","primario_pressed","primario_suave","sobre_primario",
              "acento","sobre_acento","fondo","superficie","texto","texto_suave","borde",
              "exito","aviso","error","deshabilitado","foco","borde_control"]:
        L.append("  --color-{}: {};".format(k.replace("_","-"), d[k]))
    if "tinta" in d:
        L.append("  --color-tinta: {};  /* fija: no cambia en modo oscuro, ver derivar() */".format(d["tinta"]))
        L.append("  --color-sobre-tinta: {};".format(d["sobre_tinta"]))
    L.append("  --fuente-texto: {};".format(tipo["texto"]["pila"]))
    L.append("  --fuente-display: {};".format(tipo["display"]["pila"]))
    if tipo.get("mono"):
        L.append("  --fuente-mono: {};".format(tipo["mono"]["pila"]))
    for k, v in (tip.get("pesos") or {"regular":400,"medio":500,"fuerte":700}).items():
        L.append("  --peso-{}: {};".format(k, v))
    il = tip.get("interlineado") or {"titulares": 1.15, "texto": 1.55}
    L.append("  --interlineado-titulares: {};".format(il.get("titulares", 1.15)))
    L.append("  --interlineado-texto: {};".format(il.get("texto", 1.55)))
    for k, v in escala(tip)["escala_px"].items():
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
                 ("sobre-primario","o_sobre_primario"),("acento","o_acento"),
                 ("sobre-acento","o_sobre_acento"),("exito","o_exito"),("aviso","o_aviso"),
                 ("error","o_error")]:
        if v in d:
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

def ficha_tipografia(tipo, tip, marca, fuentes_ok=False):
    e = escala(tip)["escala_px"]
    mapa = [("3xl", "H1 — titular de portada", "display", "titulares"),
            ("2xl", "H2 — titulo de seccion", "display", "titulares"),
            ("xl",  "H3 — subtitulo", "display", "titulares"),
            ("lg",  "Entradilla", "texto", "texto"),
            ("base","Cuerpo de texto", "texto", "texto"),
            ("sm",  "Texto secundario, etiquetas", "texto", "texto"),
            ("xs",  "Leyendas, avisos legales", "texto", "texto")]
    il = tip.get("interlineado") or {"titulares": 1.15, "texto": 1.55}
    L = ["# Tipografia del sitio — " + marca, ""]
    if tipo["pendiente"]:
        L += ["> **PENDIENTE DE DEFINIR.** " + " ".join(tipo["avisos"]),
              "> No entregues el kit asi.", ""]
    L += ["## Familias", "",
          "| Rol | Familia | Pesos | Licencia | Uso |", "|---|---|---|---|---|"]
    for rol in ("display", "texto", "mono"):
        if rol not in tipo:
            continue
        f = tipo[rol]
        L.append("| {} | **{}** | {} | {} | {} |".format(
            rol, f["familia"], ", ".join(str(p) for p in f["pesos"]),
            f["licencia"], f["uso"] or "-"))
    L += ["", "## Como se instala", ""]
    if fuentes_ok:
        L += ["Las tipografias vienen **autoalojadas en el kit**, en `fuentes/`.",
              "El sitio no depende de ningun servicio externo.", "",
              "```html",
              '<link rel="stylesheet" href="fuentes.css">',
              '<link rel="stylesheet" href="tokens.css">',
              "```", "",
              "Copia la carpeta `fuentes/` completa, con las licencias que trae dentro:",
              "distribuirlas es condicion de la licencia OFL.", ""]
    else:
        L += ["En el `<head>`, **antes** de la hoja de estilos del sitio:", "",
              "```html",
              '<link rel="preconnect" href="https://fonts.googleapis.com">',
              '<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>',
              '<link rel="stylesheet" href="' + tipo["url_google"] + '">',
              "```", "",
              "**Para entregar el kit sin dependencias externas**, autoaloja las",
              "tipografias: cargan antes y el sitio no se rompe si el servicio falla.", "",
              "```bash",
              "python3 generador/fuentes.py tokens.json --out fuentes",
              "```", "",
              "Eso descarga los `.woff2`, sus licencias y genera `fuentes.css`.", ""]
    L += [
          "## Escala y jerarquia", "",
          "| Token | px | Donde se usa | Familia | Interlineado | Peso |",
          "|---|---|---|---|---|---|"]
    for k, desc, fam, iln in mapa:
        if k not in e:
            continue
        L.append("| `--texto-{}` | {} | {} | {} | {} | {} |".format(
            k, e[k], desc, tipo[fam]["familia"], il.get(iln, 1.4),
            700 if fam == "display" else 400))
    L += ["", "## Reglas", "",
          "- El cuerpo **nunca baja de 16px**. Por debajo, los navegadores moviles",
          "  hacen zoom automatico al enfocar un campo y descuadran la maqueta.",
          "- Ancho de linea de 60 a 75 caracteres en texto corrido.",
          "- Interlineado de {} en texto y {} en titulares.".format(
              il.get("texto", 1.55), il.get("titulares", 1.15)),
          "- Maximo tres pesos en todo el sitio.",
          "- Usa siempre `var(--fuente-texto)` y `var(--fuente-display)`,",
          "  nunca el nombre de la familia escrito a mano en el CSS.", ""]
    if tipo["texto"]["licencia"] == "por confirmar":
        L += ["> Confirma la licencia antes de publicar. Si la tipografia es de pago,",
              "> el cliente necesita comprar la licencia web, que se cobra aparte de",
              "> la de escritorio y suele depender de las visitas del sitio.", ""]
    return "\n".join(L)

def html(d, tip, marca, logo_rel, informe_md, tipo, fuentes_ok=False, logo_oscuro_rel=None):
    e = escala(tip)["escala_px"]
    fam_t = tipo["texto"]["familia"]
    fam_d = tipo["display"]["familia"]
    filas_pal = "".join(
        '<div class="ficha"><div class="muestra" style="background:{h}"></div>'
        '<b>{k}</b><code>{h}</code></div>'.format(k=k.replace("_","-"), h=d[k])
        for k in ["primario","primario_hover","acento","fondo","superficie","texto",
                  "texto_suave","borde","exito","aviso","error"])
    usos = {"3xl":("H1 · titular de portada","display"),"2xl":("H2 · titulo de seccion","display"),
            "xl":("H3 · subtitulo","display"),"lg":("Entradilla","texto"),
            "base":("Cuerpo de texto","texto"),"sm":("Secundario, etiquetas","texto"),
            "xs":("Leyendas, legales","texto"),"4xl":("Titular grande","display")}
    filas_tipo = "".join(
        '<tr><td><code>--texto-{k}</code></td><td>{v}px</td><td>{u}</td>'
        '<td style="font-size:{vv}px;line-height:1.2;font-family:var(--fuente-{f});'
        'font-weight:{w}">{m}</td></tr>'.format(
            k=k, v=v, u=usos.get(k, ("-", "texto"))[0], f=usos.get(k, ("-", "texto"))[1],
            w=700 if usos.get(k, ("-", "texto"))[1] == "display" else 400,
            vv=min(v, 40), m="Diseño con carácter" if v >= 20 else "Texto de muestra")
        for k, v in e.items())
    aviso_tipo = ("" if not tipo["pendiente"] else
        '<div style="background:#B3261E;color:#fff;padding:16px 20px;border-radius:10px;'
        'margin-bottom:20px"><b>Tipografia pendiente de definir.</b><br>' +
        "<br>".join(escapar(a) for a in tipo["avisos"]) +
        '<br>No entregues el kit en este estado.</div>')
    filas_fam = "".join(
        '<tr><td>{r}</td><td style="font-family:var(--fuente-{r2});font-size:22px;'
        'font-weight:{w}">{f}</td><td><code>{p}</code></td><td>{l}</td></tr>'.format(
            r=r, r2=r if r != "mono" else "mono", f=tipo[r]["familia"],
            w=700 if r == "display" else 400,
            p=", ".join(str(x) for x in tipo[r]["pesos"]), l=tipo[r]["licencia"])
        for r in ("display", "texto", "mono") if r in tipo)
    if fuentes_ok:
        enlace_fuente = '<link rel="stylesheet" href="fuentes.css">'
        codigo_carga = escapar('<link rel="stylesheet" href="fuentes.css">')
    else:
        enlace_fuente = (
            '<link rel="preconnect" href="https://fonts.googleapis.com">'
            '<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>'
            '<link rel="stylesheet" href="{}">'.format(tipo["url_google"]))
        codigo_carga = escapar('<link rel="stylesheet" href="' + tipo["url_google"] + '">')
    # Si hay variante negativa, el logo cambia con el modo: un logo de un solo
    # color oscuro desaparece sobre fondo oscuro (ver accesibilidad-y-verificacion.md).
    # Las dos imagenes se insertan siempre; el CSS del template decide cual se ve
    # segun [data-tema], para que la guia misma demuestre el problema resuelto.
    if logo_rel and logo_oscuro_rel:
        marca_logo = (
            '<img class="logo-claro" src="{}" alt="Logo de {}" style="height:34px">'
            '<img class="logo-oscuro" src="{}" alt="Logo de {}" style="height:34px">'
        ).format(logo_rel, marca, logo_oscuro_rel, marca)
    elif logo_rel:
        marca_logo = '<img src="{}" alt="Logo de {}" style="height:34px">'.format(logo_rel, marca)
    else:
        marca_logo = '<span class="logotexto">{}</span>'.format(marca)
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
        "__ENLACE_FUENTE__": enlace_fuente, "__AVISO_TIPO__": aviso_tipo,
        "__FAMILIAS__": filas_fam, "__CODIGO_CARGA__": codigo_carga,
    }.items():
        plantilla = plantilla.replace(k, v)
    return plantilla

def main():
    p = argparse.ArgumentParser(description="Genera el kit de interfaz desde tokens.json")
    p.add_argument("tokens")
    p.add_argument("--out", default="kit")
    p.add_argument("--marca", default=None)
    p.add_argument("--logo", default=None, help="SVG o PNG del logo, se copia al kit")
    p.add_argument("--logo-oscuro", default=None,
                   help="Variante del logo para fondo oscuro (mono-negativo), opcional")
    p.add_argument("--fuentes", action="store_true",
                   help="Descarga las tipografias y las autoaloja en el kit (necesita red)")
    p.add_argument("--sin-generador", action="store_true",
                   help="No incluir el generador en el kit (no recomendado)")
    args = p.parse_args()

    t = json.loads(Path(args.tokens).read_text(encoding="utf-8"))
    marca = args.marca or t.get("marca", "Marca")
    d = derivar(t)
    tip = t.get("tipografia") or {}
    esp = t.get("espaciado_px") or [4, 8, 12, 16, 24, 32, 48, 64, 96]
    rad = t.get("radio_px") or {"sm": 6, "md": 10, "lg": 16, "completo": 9999}

    tipo = resolver_tipografia(t)

    out = Path(args.out); out.mkdir(parents=True, exist_ok=True)
    (out / "tokens.css").write_text(css(d, tip, esp, rad, tipo), encoding="utf-8")
    inf, fallas = informe(d)
    (out / "contraste.md").write_text(inf, encoding="utf-8")

    # --- el kit tiene que poder regenerarse solo ---
    # Sin tokens.json y sin el generador, cambiar un color obliga a editar a mano
    # el CSS ya generado, que es justo lo que el sistema de tokens evita.
    aqui = Path(__file__).parent
    def copiar(origen, destino):
        """Copia salvo que sea el mismo archivo.

        Al regenerar el kit dentro de su propia carpeta —que es lo normal para
        quien lo recibe— el generador se copiaria sobre si mismo.
        """
        origen, destino = Path(origen), Path(destino)
        if destino.exists() and origen.resolve() == destino.resolve():
            return
        destino.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy(origen, destino)

    if not args.sin_generador:
        gen = out / "generador"
        gen.mkdir(parents=True, exist_ok=True)
        for f in ("kit_ui.py", "_plantilla_kit.html", "fuentes.py"):
            if (aqui / f).exists():
                copiar(aqui / f, gen / f)
        copiar(args.tokens, out / "tokens.json")

    fuentes_ok = False
    if args.fuentes:
        try:
            sys.path.insert(0, str(aqui))
            import fuentes as MF
            caras = []
            for rol in ("display", "texto", "mono"):
                if rol in tipo and tipo[rol].get("origen", "google") == "google":
                    print("Descargando {}...".format(tipo[rol]["familia"]))
                    caras += MF.procesar(tipo[rol]["familia"], tipo[rol]["pesos"],
                                         out / "fuentes", MF.hay_brotli())
            if caras:
                (out / "fuentes.css").write_text(MF.css_fuentes(caras), encoding="utf-8")
                fuentes_ok = True
        except Exception as e:
            print("  AVISO: no se pudieron autoalojar las fuentes ({}).".format(type(e).__name__))
            print("         El kit queda enlazando a Google Fonts.")

    (out / "tipografia.md").write_text(
        ficha_tipografia(tipo, tip, marca, fuentes_ok), encoding="utf-8")

    logo_rel = None
    if args.logo and Path(args.logo).exists():
        destino = out / Path(args.logo).name
        copiar(args.logo, destino)
        logo_rel = destino.name

    logo_oscuro_rel = None
    if args.logo_oscuro and Path(args.logo_oscuro).exists():
        destino = out / Path(args.logo_oscuro).name
        copiar(args.logo_oscuro, destino)
        logo_oscuro_rel = destino.name

    (out / "index.html").write_text(
        html(d, tip, marca, logo_rel, inf, tipo, fuentes_ok, logo_oscuro_rel), encoding="utf-8")

    leeme = ["# Kit de interfaz — " + marca, "",
        "Todo lo que necesita quien programa el sitio. **El kit se regenera solo**",
        "si cambia una decision de diseno; no edites a mano los archivos generados.", "",
        "## Que hay aqui", "",
        "```",
        "kit/",
        "├── index.html       guia visual — abrela en el navegador",
        "├── tokens.css       variables para el proyecto (GENERADO, no editar)",
        "├── tokens.json      las decisiones. ESTE es el archivo que se edita",
        "├── tipografia.md    familias, pesos, licencia e instalacion",
        "├── contraste.md     informe WCAG",
        ("├── fuentes.css      @font-face de las tipografias autoalojadas" if fuentes_ok
         else "│                 (las tipografias se cargan desde Google Fonts)"),
        ("├── fuentes/         los .woff2 y sus licencias" if fuentes_ok else "│"),
        "└── generador/       kit_ui.py y sus plantillas",
        "```", "",
        "## Como cambiar algo", "",
        "1. Edita `tokens.json` — por ejemplo el color primario o un tamano de texto.",
        "2. Vuelve a generar:", "",
        "```bash",
        "python3 generador/kit_ui.py tokens.json --out ."
        + (" --logo {}".format(logo_rel) if logo_rel else "")
        + (" --logo-oscuro {}".format(logo_oscuro_rel) if logo_oscuro_rel else "")
        + (" --fuentes" if fuentes_ok else ""),
        "```", "",
        "Los estados (hover, pressed, foco, texto sobre cada fondo) y el modo oscuro",
        "se recalculan solos, y el informe de contraste se rehace. Por eso no se",
        "editan a mano: el proximo regenerado borraria el cambio.", "",
        "## Como se enlaza en el sitio", "",
        "```html"]
    if fuentes_ok:
        leeme += ['<link rel="stylesheet" href="fuentes.css">',
                  '<link rel="stylesheet" href="tokens.css">', "```", "",
                  "Copia tambien la carpeta `fuentes/`. Las licencias que van dentro",
                  "deben distribuirse con los archivos: es condicion de la licencia OFL."]
    else:
        leeme += ['<link rel="preconnect" href="https://fonts.googleapis.com">',
                  '<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>',
                  '<link rel="stylesheet" href="' + tipo["url_google"] + '">',
                  '<link rel="stylesheet" href="tokens.css">', "```", "",
                  "Para autoalojar las tipografias y no depender de Google:", "",
                  "```bash", "python3 generador/fuentes.py tokens.json --out fuentes", "```"]
    leeme += ["", "## Reglas", "",
        "- Usa siempre las variables de `tokens.css`, nunca un HEX o un px suelto.",
        "- Modo oscuro: `data-tema=\"oscuro\"` en el `<html>`.",
        "- Los textos del kit son de muestra: reemplazalos por los definitivos.", ""]
    (out / "LEEME.md").write_text("\n".join(leeme), encoding="utf-8")

    print("Kit generado en " + str(out))
    listado = ["index.html", "tokens.css", "tokens.json", "tipografia.md",
               "contraste.md", "LEEME.md"]
    if fuentes_ok:
        listado += ["fuentes.css", "fuentes/"]
    if not args.sin_generador:
        listado += ["generador/"]
    for f in listado:
        print("  - " + f)
    if not fuentes_ok:
        print("\n  Tipografias enlazadas a Google Fonts. Para entregar el kit")
        print("  completo y sin dependencias externas, usa --fuentes.")
    print("\n  Tipografia: {} (titulares) / {} (texto)".format(
        tipo["display"]["familia"], tipo["texto"]["familia"]))
    if tipo["pendiente"]:
        print("\n  ATENCION: la tipografia no esta definida.")
        for a in tipo["avisos"]:
            print("  " + a)
        print("  Revisa tipografia.md y corrigelo antes de entregar.")
    if fallas:
        print("\n  ATENCION: {} par(es) de color no pasan contraste.".format(fallas))
        print("  Revisa contraste.md y corrige antes de entregar.")
    else:
        print("\n  Contraste: todos los pares en uso cumplen.")

if __name__ == "__main__":
    main()
