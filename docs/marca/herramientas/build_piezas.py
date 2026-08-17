#!/usr/bin/env python3
"""Piezas derivadas del sistema: favicon con modo oscuro y banner social."""
import build_marca as B

TINTA, HILO, BLANCO, HUESO = "#16192A", "#D69A3C", "#FFFFFF", "#F7F5F1"

# ---- favicon.svg: isotipo simplificado que se invierte en modo oscuro ----
iso = B.isotipo_simple()
x0, y0, x1, y1 = B.bbox(iso)
pad = 8
vb = f"{x0-pad:g} {y0-pad:g} {x1-x0+2*pad:g} {y1-y0+2*pad:g}"
open("marca/favicon.svg", "w").write(
    f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{vb}" role="img" aria-label="Sastra">\n'
    f'  <title>Sastra</title>\n'
    f'  <style>.s{{fill:{TINTA}}}@media (prefers-color-scheme:dark){{.s{{fill:{BLANCO}}}}}</style>\n'
    f'  <path class="s" d="{B.d_of(iso)}"/>\n</svg>\n')

# ---- banner 1200x630 para og:image y redes ----
W, H = 1200, 630
M = 88                      # margen, multiplo de 8

lock = B.lockup_horizontal()
lx0, ly0, lx1, ly1 = B.bbox(lock)
esc = 470 / (lx1 - lx0)
lock = B.xform(lock, esc, dx=M - lx0*esc, dy=214 - ly0*esc)

desc, _ = B.texto_path("Compra y vende moda con respaldo",
                       fuente=B.TEXTO, wdth=100, wght=500, size=34, tracking=0.004)
dx0, dy0, dx1, dy1 = B.bbox(desc)
desc = B.xform(desc, 1.0, dx=M - dx0, dy=384 - dy0)

# regla de puntada: guiones de 16 con hueco de 9, el mismo hueco del isotipo
tramos = []
x = M
while x < W - M:
    tramos.append(B.slot(x + 8, 338, 4, 16, 90))
    x += 25
regla = B.union(tramos)

open("marca/banner.svg", "w").write(
    f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" role="img" '
    f'aria-label="Sastra: compra y vende moda con respaldo">\n'
    f'  <title>Sastra</title>\n'
    f'  <rect width="{W}" height="{H}" fill="{TINTA}"/>\n'
    f'  <path fill="{HILO}" d="{B.d_of(regla)}"/>\n'
    f'  <path fill="{BLANCO}" d="{B.d_of(lock)}"/>\n'
    f'  <path fill="{HILO}" d="{B.d_of(desc)}"/>\n</svg>\n')

print("marca/favicon.svg y marca/banner.svg listos")

# ---- piezas verticales y cuadradas, cada una compuesta para su lienzo ----
def pieza(w, h, archivo, ancho_logo, ts_desc):
    lock = B.lockup_vertical() if h > w * 1.2 else B.lockup_horizontal()
    lx0, ly0, lx1, ly1 = B.bbox(lock)
    e = ancho_logo / (lx1 - lx0)
    lw, lh = (lx1 - lx0) * e, (ly1 - ly0) * e
    desc, _ = B.texto_path("Compra y vende moda con respaldo", fuente=B.TEXTO,
                           wdth=100, wght=500, size=ts_desc, tracking=0.004)
    dx0, dy0, dx1, dy1 = B.bbox(desc)
    dw, dh = dx1 - dx0, dy1 - dy0
    hueco = ts_desc * 1.9
    bloque = lh + hueco + dh
    top = (h - bloque) / 2
    lock = B.xform(lock, e, dx=(w - lw) / 2 - lx0 * e, dy=top - ly0 * e)
    desc = B.xform(desc, 1.0, dx=(w - dw) / 2 - dx0, dy=top + lh + hueco - dy0)
    tramos, x = [], (w - ancho_logo) / 2
    y = top + lh + hueco * 0.46
    while x < w - (w - ancho_logo) / 2:
        tramos.append(B.slot(x + ts_desc * 0.25, y, ts_desc * 0.13, ts_desc * 0.5, 90))
        x += ts_desc * 0.78
    regla = B.union(tramos)
    open(archivo, "w").write(
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" role="img" '
        f'aria-label="Sastra: compra y vende moda con respaldo">\n  <title>Sastra</title>\n'
        f'  <rect width="{w}" height="{h}" fill="{TINTA}"/>\n'
        f'  <path fill="{HILO}" d="{B.d_of(regla)}"/>\n'
        f'  <path fill="{BLANCO}" d="{B.d_of(lock)}"/>\n'
        f'  <path fill="{HILO}" d="{B.d_of(desc)}"/>\n</svg>\n')

pieza(1080, 1080, "marca/pieza-cuadrada.svg", 620, 38)
pieza(1080, 1920, "marca/pieza-story.svg", 560, 40)
print("piezas cuadrada y story listas")

# ---- isotipo con aire para iconos de app: arte al 80% del lienzo ----
iso_app = B.isotipo()
ax0, ay0, ax1, ay1 = B.bbox(iso_app)
alto = ay1 - ay0
p = (alto / 0.80 - alto) / 2
vb2 = f"{ax0-p-((alto-(ax1-ax0))/2):g} {ay0-p:g} {alto+2*p:g} {alto+2*p:g}"
open("marca/isotipo-app.svg", "w").write(
    f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{vb2}" role="img" '
    f'aria-label="Sastra">\n  <title>Sastra</title>\n'
    f'  <path fill="{BLANCO}" d="{B.d_of(iso_app)}"/>\n</svg>\n')

# ---- todas las piezas sociales compuestas a sangre para su lienzo ----
for w, h, nombre, al, ts in [
        (1200, 630, "og-image", 470, 34),
        (1200, 675, "twitter-card", 470, 34),
        (1584, 396, "linkedin-banner", 420, 28),
        (820, 312, "facebook-cover", 340, 24),
        (1080, 1080, "instagram-post", 620, 38),
        (1080, 1920, "instagram-story", 560, 40)]:
    pieza(w, h, f"marca/social-{nombre}.svg", al, ts)
print("piezas sociales a sangre listas")
