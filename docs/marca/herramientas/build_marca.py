#!/usr/bin/env python3
"""
Sastra - generador de identidad.
Todo sale de la misma geometria: arcos de radio 22 sobre lienzo 120,
trazo 15, y la puntada: un corte de 9 unidades a 135 grados.
Sin mascaras, sin <text>, sin filtros: un solo <path> por activo.
"""
import math, os
import pathops
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer
from fontTools.svgLib.path import parse_path
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.boundsPen import ControlBoundsPen
from fontTools.misc.transform import Transform

OUT = "marca"
TINTA, HILO, BLANCO = "#16192A", "#D69A3C", "#FFFFFF"
K = 0.5522847498307933

def d_of(p, prec=2):
    pen = SVGPathPen(None, ntos=lambda v: f"{round(v, prec):g}")
    p.draw(pen); return pen.getCommands()

def bbox(p):
    pen = ControlBoundsPen(None); p.draw(pen); return pen.bounds

def xform(p, sx=1.0, sy=None, dx=0.0, dy=0.0):
    sy = sx if sy is None else sy
    out = pathops.Path()
    p.draw(TransformPen(out.getPen(), Transform(sx, 0, 0, sy, dx, dy)))
    return out

def circle(cx, cy, r):
    p = pathops.Path(); pen = p.getPen()
    pen.moveTo((cx, cy-r))
    pen.curveTo((cx+r*K, cy-r), (cx+r, cy-r*K), (cx+r, cy))
    pen.curveTo((cx+r, cy+r*K), (cx+r*K, cy+r), (cx, cy+r))
    pen.curveTo((cx-r*K, cy+r), (cx-r, cy+r*K), (cx-r, cy))
    pen.curveTo((cx-r, cy-r*K), (cx-r*K, cy-r), (cx, cy-r))
    pen.closePath(); return p

def slot(cx, cy, gap, length, ang):
    a = math.radians(ang); hw, hl = gap/2, length/2
    p = pathops.Path(); pen = p.getPen()
    ro = [(x*math.cos(a)-y*math.sin(a)+cx, x*math.sin(a)+y*math.cos(a)+cy)
          for x, y in [(-hw,-hl),(hw,-hl),(hw,hl),(-hw,hl)]]
    pen.moveTo(ro[0])
    for pt in ro[1:]: pen.lineTo(pt)
    pen.closePath(); return p

def union(ps):
    out = pathops.Path(); pathops.union(list(ps), out.getPen()); return out

def minus(a, b):
    out = pathops.Path(); pathops.difference([a], [b], out.getPen())
    out.simplify(fix_winding=True); return out

# -------------------------------------------------------------- isotipo
def s_solid(cx=60, cy=60, dy=18, r=22, w=15, a0=-40, a1=100):
    c1 = (cx, cy-dy)
    p0 = (c1[0]+r*math.cos(math.radians(a0)), c1[1]+r*math.sin(math.radians(a0)))
    p1 = (c1[0]+r*math.cos(math.radians(a1)), c1[1]+r*math.sin(math.radians(a1)))
    large = 1 if (a0-a1) % 360 > 180 else 0
    halves = []
    for s, e in ((p0, p1), ((2*cx-p0[0], 2*cy-p0[1]), (2*cx-p1[0], 2*cy-p1[1]))):
        ln = pathops.Path()
        parse_path(f"M {s[0]:.4f} {s[1]:.4f} A {r} {r} 0 {large} 0 {e[0]:.4f} {e[1]:.4f}", ln.getPen())
        ln.stroke(w, pathops.LineCap.BUTT_CAP, pathops.LineJoin.MITER_JOIN, 4.0)
        halves.append(ln)
    return union(halves)

def isotipo(w=17):
    """S solida de trazo 17, sin interrupcion.

    Version aprobada. Se probo una variante con la cintura interrumpida por
    un corte diagonal (la puntada); se descarto en favor de esta, que aguanta
    mejor los tamanos pequenos y lee mas limpia. La puntada sobrevive como
    elemento del sistema en la regla divisoria.
    """
    return s_solid(w=w)

def isotipo_simple(w=17):
    return isotipo(w=w)

# ------------------------------------------------------------- logotipo
DISPLAY = "/root/.fonts/Archivo[wdth,wght].ttf"
TEXTO   = "/root/.fonts/InstrumentSans[wdth,wght].ttf"

def texto_path(txt, fuente=DISPLAY, wdth=90, wght=620, size=100.0, tracking=0.0):
    f = TTFont(fuente)
    axes = {a.axisTag for a in f["fvar"].axes}
    loc = {k: v for k, v in (("wdth", wdth), ("wght", wght)) if k in axes}
    instancer.instantiateVariableFont(f, loc, inplace=True)
    upm = f["head"].unitsPerEm
    gs, cmap = f.getGlyphSet(), f.getBestCmap()
    sc, tr = size/upm, tracking*size
    x, partes, cajas = 0.0, [], []
    for ch in txt:
        if ch == " ":
            x += gs[cmap[32]].width*sc + tr; continue
        g = gs[cmap[ord(ch)]]
        sub = pathops.Path()
        g.draw(TransformPen(sub.getPen(), Transform(sc, 0, 0, -sc, x, 0)))
        if bbox(sub):
            partes.append(sub); cajas.append((ch, bbox(sub)))
        x += g.width*sc + tr
    return union(partes), cajas

def logotipo(size=100.0, tracking=0.08):
    """SASTRA en Archivo instanciada a wdth 90 / wght 620, caja alta.

    Decision de critica: se probo repetir la puntada del isotipo dentro de
    las dos S y se descarto. En la geometria pura del isotipo el corte lee
    como costura; sobre el ductus de la tipografia lee como glifo roto, a
    todos los tamanos. La firma vive en el isotipo y en la regla de puntada.
    """
    palabra, _ = texto_path("SASTRA", size=size, tracking=tracking)
    return palabra

# -------------------------------------------------------------- lockups
def lockup_horizontal():
    iso = isotipo(); ix0, iy0, ix1, iy1 = bbox(iso)
    wm = logotipo();  wx0, wy0, wx1, wy1 = bbox(wm)
    capH = wy1-wy0
    esc = (capH*1.30)/(iy1-iy0)
    iw, ih = (ix1-ix0)*esc, (iy1-iy0)*esc
    iso = xform(iso, esc, dx=-ix0*esc, dy=-iy0*esc)
    wm = xform(wm, 1.0, dx=iw+capH*0.46-wx0, dy=(ih-capH)/2-wy0)
    return union([iso, wm])

def lockup_vertical():
    iso = isotipo(); ix0, iy0, ix1, iy1 = bbox(iso)
    wm = logotipo();  wx0, wy0, wx1, wy1 = bbox(wm)
    capH, ww = wy1-wy0, wx1-wx0
    esc = (capH*2.05)/(iy1-iy0)
    iw, ih = (ix1-ix0)*esc, (iy1-iy0)*esc
    iso = xform(iso, esc, dx=(ww-iw)/2-ix0*esc, dy=-iy0*esc)
    wm = xform(wm, 1.0, dx=-wx0, dy=ih+capH*0.52-wy0)
    return union([iso, wm])

# --------------------------------------------------------------- salida
def escribir(nombre, p, color=TINTA, pad=0.0, label="Sastra"):
    x0, y0, x1, y1 = bbox(p)
    vb = f"{round(x0-pad,2):g} {round(y0-pad,2):g} {round(x1-x0+2*pad,2):g} {round(y1-y0+2*pad,2):g}"
    open(os.path.join(OUT, nombre), "w").write(
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{vb}" role="img" '
        f'aria-label="{label}">\n  <title>Sastra</title>\n'
        f'  <path fill="{color}" d="{d_of(p)}"/>\n</svg>\n')
    print(f"  {nombre:30s} {x1-x0:7.1f} x {y1-y0:6.1f}")

def main():
    os.makedirs(OUT, exist_ok=True)
    iso, h, v = isotipo(), lockup_horizontal(), lockup_vertical()
    escribir("isotipo.svg", iso, pad=6)
    escribir("isotipo-negativo.svg", iso, color=BLANCO, pad=6)
    escribir("logotipo.svg", logotipo())
    escribir("logo-principal.svg", h)
    escribir("logo-horizontal.svg", h)
    escribir("logo-vertical.svg", v)
    escribir("logo-mono-positivo.svg", h, color=TINTA)
    escribir("logo-mono-negativo.svg", h, color=BLANCO)

if __name__ == "__main__":
    main()
