#!/usr/bin/env python3
"""Reconstruye el kit de interfaz de Sastra desde cero.

    cd generador
    python3 construir.py

Todo sale de esta carpeta: no depende de nada instalado ni de ninguna skill.

    tokens.json        los colores, la escala y las medidas. AQUI se edita.
    kit_ui.py          deriva los estados y escribe tokens.css y contraste.md
    _plantilla_kit.html  el esqueleto de la guia
    fuentes/           tipografia.css, marca.css y LEEME.md, escritos a mano
    marca/             los SVG del logo

El resultado se escribe en la carpeta de arriba, sobrescribiendo el kit.

Para cambiar un color: se edita tokens.json y se vuelve a correr esto. Los
estados (hover, pressed, foco, borde de campo, color de texto sobre cada fondo)
se recalculan solos. Editar tokens.css a mano es perder el trabajo en el
siguiente build.
"""
import subprocess, sys, shutil
from pathlib import Path

AQUI = Path(__file__).resolve().parent
KIT = AQUI.parent

for necesario in ["kit_ui.py", "_plantilla_kit.html", "tokens.json",
                  "fuentes/tipografia.css", "fuentes/marca.css", "fuentes/LEEME.md",
                  "marca/logo-horizontal.svg"]:
    if not (AQUI / necesario).exists():
        sys.exit("Falta " + necesario + " en la carpeta generador/. "
                 "El kit no se puede reconstruir sin ese archivo.")

subprocess.run([sys.executable, str(AQUI / "kit_ui.py"), str(AQUI / "tokens.json"),
                "--out", str(KIT), "--marca", "Sastra",
                "--logo", str(AQUI / "marca" / "logo-horizontal.svg")],
               cwd=AQUI, check=True)

for f in (AQUI / "marca").glob("*.svg"):
    shutil.copy(f, KIT / f.name)
for f in (AQUI / "fuentes").iterdir():
    shutil.copy(f, KIT / f.name)

h = (KIT / "index.html").read_text(encoding="utf-8")


# El generador inserta el lockup en tinta. Sobre fondo oscuro desaparece, asi que
# se cambia por el par de logos con alternancia por CSS: es lo que debe copiar
# quien programa, no un <img> suelto.
LOGO_GENERADO = '<img src="logo-horizontal.svg" alt="Logo de Sastra" style="height:34px">'
LOGO_ADAPTABLE = (
    '<span class="logo-sitio" role="img" aria-label="Sastra">'
    '<img class="logo-claro" src="logo-horizontal.svg" alt="" style="height:34px">'
    '<img class="logo-oscuro" src="logo-mono-negativo.svg" alt="" style="height:34px">'
    '</span>')
if LOGO_GENERADO not in h:
    sys.exit("La plantilla ya no inserta el logo como se esperaba. "
             "Revisa LOGO_GENERADO en construir.py.")
h = h.replace(LOGO_GENERADO, LOGO_ADAPTABLE)

# Las tablas no caben en 360px. Se envuelven en un contenedor con desplazamiento
# horizontal, enfocable con teclado: una region que se desplaza y no se puede
# alcanzar con Tab deja fuera a quien no usa raton.
h = h.replace('<table>',
              '<div class="tabla-envoltura" tabindex="0" role="region" '
              'aria-label="Tabla con desplazamiento horizontal"><table>')
h = h.replace('</table>', '</table></div>')


def cambiar(viejo, nuevo, etiqueta):
    """Sustituye un trozo de la guia generada por la version de Sastra.

    Si falla, es porque _plantilla_kit.html cambio y ya no contiene ese trozo.
    Se corrige actualizando el texto de 'viejo' para que coincida con la
    plantilla nueva. Falla ruidosamente a proposito: un parche que se aplica a
    medias deja una guia con partes genericas y nadie lo nota.
    """
    global h
    if viejo not in h:
        sys.exit("No se pudo aplicar el parche '" + etiqueta + "'.\n"
                 "La plantilla _plantilla_kit.html cambio y ya no contiene el\n"
                 "fragmento que este parche esperaba. Revisa construir.py,\n"
                 "busca la etiqueta '" + etiqueta + "' y actualiza el texto.")
    h = h.replace(viejo, nuevo, 1)


# ---------- 1. Fuentes de marca y capa de marca ----------
cambiar(
    '<link rel="stylesheet" href="tokens.css">',
    '<link rel="preconnect" href="https://fonts.googleapis.com">\n'
    '<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>\n'
    '<link href="https://fonts.googleapis.com/css2?family=Archivo:wght@400;500;700'
    '&family=Instrument+Sans:wght@400;500;700&family=IBM+Plex+Mono:wght@400;700'
    '&display=swap" rel="stylesheet">\n'
    '<link rel="stylesheet" href="tokens.css">\n'
    '<link rel="stylesheet" href="tipografia.css">\n'
    '<link rel="stylesheet" href="marca.css">',
    "fuentes")

# ---------- 2. Estilos solo de la guia (marco de telefono, rejilla de producto) ----------
# La guia usa su propia escala fluida: si predica clamp() y sus encabezados
# son fijos, no se sostiene.
cambiar("h1{font-size:var(--texto-3xl)} h2{font-size:var(--texto-xl)} h3{font-size:var(--texto-lg)}",
        "h1{font-size:var(--tipo-h1)} h2{font-size:var(--tipo-h3)} h3{font-size:var(--texto-lg)}",
        "encabezados fluidos de la guia")
cambiar("  h1{font-size:var(--texto-2xl)}\n", "", "override movil sobrante")

cambiar("</style>\n</head>", """
/* --- solo para esta guia, no van al proyecto --- */
.telefonos{display:flex;gap:var(--esp-24);flex-wrap:wrap;margin-top:var(--esp-16)}
.telefono{width:320px;border:1px solid var(--color-borde);border-radius:var(--radio-lg);
  overflow:hidden;background:var(--color-fondo);box-shadow:var(--sombra-sm)}
.telefono .rotulo{font-size:var(--texto-xs);font-weight:700;padding:6px var(--esp-12);
  background:var(--color-primario-suave);color:var(--color-texto)}
.rejilla-prod{display:grid;gap:var(--esp-16);
  grid-template-columns:repeat(auto-fill,minmax(11.875rem,1fr))}
.mk-header .buscador{margin-left:var(--esp-16)}
/* Hero y footer usan la franja oscura de marca, no el primario: asi no se
   invierten a claro en modo oscuro y el logo blanco del footer se sigue viendo. */
/* La plantilla pinta hero y footer con --color-texto, que en modo oscuro se
   invierte a claro. Su <style> carga despues de marca.css, asi que hay que
   volver a fijarlo aqui. En el proyecto real basta la clase .franja-oscura. */
.mk-hero,.mk-footer{background:var(--fondo-oscuro-marca);color:var(--sobre-fondo-oscuro)}
.mk-footer a,.mk-footer b{color:var(--sobre-fondo-oscuro)}
.mk-footer b{display:block;margin-bottom:var(--esp-8)}
.mk-footer b{display:block;margin-bottom:var(--esp-8)}
/* Destinos tactiles de 44px. Los enlaces de navegacion y de pie de pagina son
   donde mas se incumple: quedan como una lista de enlaces pegados donde nadie atina. */
.mk-nav a{min-height:44px;display:flex;align-items:center}
.mk-footer a{min-height:44px;display:flex;align-items:center;padding:0}
.barra .env>button{min-height:44px!important}
/* Muestras del logo: el fondo es fijo en los dos modos. Una muestra existe para
   enseñar el logo sobre su fondo previsto, no para seguir el tema de la pagina. */
.espec{text-align:center;border-radius:var(--radio-lg);padding:var(--esp-24);
  border:1px solid var(--color-borde)}
.espec-claro{background:#F7F5F1;color:#16192A}
.espec-oscuro{background:#16192A;color:#F2F1EE}
.espec .nota{color:inherit;opacity:.75}
.tabla-envoltura{overflow-x:auto}
.tabla-envoltura table{min-width:420px}
.tabla-envoltura:focus-visible{outline:3px solid var(--color-foco);outline-offset:2px}
@media (max-width:640px){
  .telefono{width:100%}
  .barra .env{flex-wrap:wrap;gap:var(--esp-8)}
  .barra .env>strong{display:none}
  .rejilla-prod{grid-template-columns:repeat(auto-fill,minmax(9.375rem,1fr));gap:var(--esp-12)}
}
</style>
</head>""", "estilos de guia")

# ---------- 3. Intro ----------
cambiar("""  <p class="nota">Guia visual del sitio. Todo lo que se ve aqui sale de
  <code>tokens.css</code>: si cambia un token, cambia el sitio entero. Prueba el
  modo oscuro y reduce el ancho de la ventana para ver el comportamiento en movil.</p>""",
        """  <p class="nota">Guia visual de la interfaz web. Parte de la identidad ya
  aprobada: <b>ningun color de aqui es nuevo</b>, cada uno es un color del manual
  puesto en su rol de interfaz. Todo sale de <code>tokens.css</code> mas
  <code>marca.css</code>: si cambia un token, cambia el sitio entero.</p>
  <p class="nota"><b>Pruebalo:</b> pulsa &laquo;Cambiar modo&raquo; arriba y reduce
  el ancho de la ventana por debajo de 640px. Los problemas de una maqueta se ven,
  no se deducen.</p>
  <hr class="regla-puntada" style="margin:var(--esp-24) 0 0">""", "intro")

# ---------- 4. Botones ----------
cambiar("""  <p>
    <button class="btn btn-primario">Cotizar ahora</button>
    <button class="btn btn-secundario">Ver servicios</button>
    <button class="btn btn-texto">Saber mas</button>
    <button class="btn btn-primario" disabled>Enviando...</button>
  </p>""",
        """  <p>
    <button class="btn btn-cta">Publicar prenda</button>
    <button class="btn btn-primario">Comprar ahora</button>
    <button class="btn btn-secundario">Guardar</button>
    <button class="btn btn-texto">Ver medidas</button>
    <button class="btn btn-primario" disabled>Publicando...</button>
  </p>
  <p class="nota"><b>Cuatro jerarquias y en este orden.</b> El ocre
  (<code>btn-cta</code>) es el unico color de acento de la marca y aparece
  <b>una sola vez por pantalla</b>: si esta en cinco sitios, no destaca ninguno.
  El tinta (<code>btn-primario</code>) es para la accion principal de cada
  bloque. El ocre nunca se usa como color de texto: sobre fondo claro da 2.3:1
  y es ilegible.</p>""", "botones")

# ---------- 5. Formularios ----------
cambiar("""  <div class="campo">
    <label for="n">Nombre</label>
    <input id="n" placeholder="Como te llamas">
  </div>
  <div class="campo error">
    <label for="c">Correo</label>
    <input id="c" value="correo-invalido" aria-invalid="true" aria-describedby="ec">
    <div class="ayuda" id="ec">Falta el correo para poder responderte</div>
  </div>
  <p class="nota">El error dice que pasa y como arreglarlo, y no depende solo del
  color: lleva texto y borde mas grueso, para quien no distingue rojo y verde.</p>""",
        """  <p class="nota">Formulario de publicar una prenda. La etiqueta va siempre
  visible encima del campo: el texto de ejemplo dentro desaparece al escribir y
  la gente olvida que iba ahi.</p>
  <div class="campo">
    <label for="t">Titulo de la prenda</label>
    <input id="t" placeholder="Chaqueta de cuero negra talla M">
    <div class="ayuda">Marca, prenda, color y talla. Es lo que la gente busca.</div>
  </div>
  <div class="campo">
    <label for="e">Estado</label>
    <select id="e">
      <option>Nueva con etiqueta</option>
      <option>Como nueva</option>
      <option>Buen estado</option>
      <option>Con senales de uso</option>
    </select>
  </div>
  <div class="campo error">
    <label for="p">Precio</label>
    <input id="p" value="ochenta mil" aria-invalid="true" aria-describedby="ep">
    <div class="ayuda" id="ep">
      &#9888; Escribe el precio en numeros, sin puntos ni signos. Ejemplo: 89000
    </div>
  </div>
  <div class="campo">
    <label for="m">Medidas <span style="font-weight:400;color:var(--color-texto-suave)">(opcional)</span></label>
    <input id="m" placeholder="Largo 62 cm, pecho 48 cm">
  </div>
  <p class="nota">Tres cosas que este formulario resuelve y casi ninguno resuelve:
  el error <b>no depende solo del color</b> (lleva simbolo, texto y borde mas
  grueso, para quien no distingue rojo y verde); se marca <b>lo opcional</b>, no
  lo obligatorio, porque intimida menos; y el boton de envio <b>no se
  deshabilita</b> mientras se escribe, se valida al enviar y se explica que falta.</p>""",
        "formularios")

# ---------- 6. Tarjetas de producto y avisos ----------
foto = ('<div class="foto">Foto 3:4</div>')


def producto(titulo, precio, antes, estado, vendedor, verificado=True):
    sello = ('<span class="sello sello-verificado">'
             '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" '
             'stroke-linecap="butt" stroke-linejoin="miter"><path d="M20 6 9 17l-5-5"/></svg>'
             'Verificado</span>') if verificado else \
            '<span style="color:var(--color-texto-suave)">Sin verificar</span>'
    ant = '<span class="precio-antes">$ {}</span>'.format(antes) if antes else ""
    return """<article class="tarjeta-producto">
      {foto}
      <div class="cuerpo">
        <span class="chip">{estado}</span>
        <h4><a href="#">{titulo}</a></h4>
        <div><span class="precio">$ {precio}</span>{ant}</div>
        <div class="vendedor">{sello} <span>&middot; {vendedor}</span></div>
      </div>
    </article>""".format(foto=foto, estado=estado, titulo=titulo, precio=precio,
                         ant=ant, sello=sello, vendedor=vendedor)


cambiar("""  <div class="rejilla" style="grid-template-columns:repeat(auto-fit,minmax(240px,1fr))">
    <div class="tarjeta"><span class="etiqueta">Servicio</span>
      <h3 style="margin-top:12px">Mantenimiento</h3>
      <p class="nota">Revision completa con diagnostico incluido.</p>
      <button class="btn btn-secundario">Cotizar</button></div>
    <div>
      <div class="aviso aviso-exito"><b>Listo.</b> Recibimos tu mensaje.</div>
      <div class="aviso aviso-aviso"><b>Atencion.</b> Quedan 3 cupos.</div>
      <div class="aviso aviso-error"><b>Error.</b> No pudimos enviar el formulario.</div>
    </div>
  </div>""",
        """  <p class="nota">La tarjeta de producto es el componente que mas veces se
  repite en el sitio, asi que es donde mas se nota una decision floja. Cuatro
  reglas: <b>foto siempre en 3:4</b> (si cada una trae su proporcion, la rejilla
  queda descuadrada), <b>precio en monoespaciada</b> con cifras tabulares para
  que las columnas cuadren y la lista se recorra rapido, <b>el estado de la
  prenda visible</b> porque en ropa usada es la primera pregunta, y <b>toda la
  tarjeta es clicable</b>, no solo el titulo.</p>
  <h3>Tarjeta de producto</h3>
  <div class="rejilla-prod">
    """ + producto("Chaqueta de cuero negra talla M", "189.000", "240.000",
                   "Como nueva", "Ana M.") + """
    """ + producto("Vestido midi estampado talla S", "72.000", None,
                   "Nueva con etiqueta", "Taller Luz") + """
    """ + producto("Jeans rectos talla 30", "58.000", None,
                   "Buen estado", "Carlos R.", verificado=False) + """
    """ + producto("Blazer de lana beige talla L", "134.000", "160.000",
                   "Como nueva", "Sara G.") + """
  </div>

  <h3 style="margin-top:var(--esp-32)">Avisos</h3>
  <div class="rejilla" style="grid-template-columns:repeat(auto-fit,minmax(280px,1fr))">
    <div>
      <div class="aviso aviso-exito"><b>&#10003; Pago liberado.</b>
        Confirmaste que recibiste la prenda. El dinero ya salio hacia Ana M.</div>
      <div class="aviso aviso-aviso"><b>&#9888; Falta una foto.</b>
        Las publicaciones con tres o mas fotos se venden mucho mas.</div>
      <div class="aviso aviso-error"><b>&#10005; Publicacion rechazada.</b>
        No se permiten replicas de marca. Puedes editarla y volver a enviarla.</div>
    </div>
    <div>
      <p class="nota">Cada aviso empieza por un simbolo y dice <b>que paso y que
      hacer</b>, no solo que algo fallo. Ninguno depende del color para
      entenderse: quitale el color a los tres y siguen leyendose distinto.</p>
      <p class="nota">El verde es el color de <b>respaldo</b> de Sastra: verificado,
      pago liberado, envio entregado. Es la promesa de la marca hecha interfaz, asi
      que no se usa para nada decorativo.</p>
    </div>
  </div>""", "tarjetas")

# ---------- 7. Maqueta ----------
cambiar("""  <div class="maqueta">
    <header class="mk-header">""", """  <p class="nota">Asi se compone el sistema en las tres regiones. Los textos
  marcados <b>[muestra]</b> son de relleno y los reemplaza el copy definitivo.</p>
  <div class="maqueta">
    <header class="mk-header">""", "intro maqueta")

viejo_maqueta = h[h.index('      <nav class="mk-nav">'):h.index('</footer>') + len('</footer>')]
cambiar(viejo_maqueta, """      <div class="buscador">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="1.75" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>
        <input placeholder="Buscar prendas, marcas o tallas" aria-label="Buscar">
      </div>
      <nav class="mk-nav">
        <a href="#" aria-current="page">Explorar</a><a href="#">Mujer</a>
        <a href="#">Hombre</a><a href="#">Como funciona</a>
      </nav>
      <button class="btn btn-cta" style="padding:10px 16px">Publicar prenda</button>
    </header>
    <div class="mk-hero franja-oscura">
      <h2>Compra y vende moda con respaldo</h2>
      <p>Guardamos tu pago hasta que confirmes que la prenda llego como la viste.
      [muestra]</p>
      <button class="btn btn-cta">Publicar prenda</button>
      <p class="tipo-secundario" style="color:inherit;margin:var(--esp-16) 0 0">
        Publicar es gratis &middot; Cobramos solo cuando vendes</p>
    </div>
    <div class="mk-body">
      <h3 style="margin-bottom:var(--esp-16)">Recien publicado</h3>
      <div class="rejilla-prod">
      """ + producto("Chaqueta de cuero negra talla M", "189.000", "240.000",
                     "Como nueva", "Ana M.") + """
      """ + producto("Vestido midi estampado talla S", "72.000", None,
                     "Nueva con etiqueta", "Taller Luz") + """
      """ + producto("Blazer de lana beige talla L", "134.000", "160.000",
                     "Como nueva", "Sara G.") + """
      </div>
      <hr class="regla-puntada">
      <div class="rejilla" style="grid-template-columns:repeat(auto-fit,minmax(200px,1fr))">
        <div class="tarjeta"><h3>El pago queda retenido</h3>
          <p class="nota">Sastra guarda el dinero y lo libera cuando confirmas que
          recibiste la prenda. [muestra]</p></div>
        <div class="tarjeta"><h3>Vendedores verificados</h3>
          <p class="nota">Cedula y celular validados antes de la primera venta.
          [muestra]</p></div>
        <div class="tarjeta"><h3>Devolucion en 3 dias</h3>
          <p class="nota">Si la prenda no es como se describio, se devuelve.
          [muestra]</p></div>
      </div>
    </div>
    <footer class="mk-footer franja-oscura">
      <div>
        <img src="logo-mono-negativo.svg" alt="Sastra" style="height:28px;display:block;margin-bottom:var(--esp-12)">
        <span style="opacity:.85">Sastra S.A.S.<br>NIT 900.000.000-0<br>
        Calle 00 #00-00, Medellin, Colombia [muestra]</span>
      </div>
      <div><b>Comprar</b><a href="#">Explorar</a><a href="#">Mujer</a><a href="#">Hombre</a></div>
      <div><b>Vender</b><a href="#">Publicar prenda</a><a href="#">Como funciona</a><a href="#">Comisiones</a></div>
      <div><b>Legal</b><a href="#">Politica de tratamiento de datos</a>
        <a href="#">Terminos y condiciones</a><a href="#">Politica de devoluciones</a></div>
      <div><b>Contacto</b><a href="#">WhatsApp</a><a href="#">Instagram</a><a href="#">Ayuda</a>
        <div style="margin-top:var(--esp-12);opacity:.85;font-size:var(--texto-xs)">
          Pagos: PSE &middot; Nequi &middot; Tarjetas &middot; Contraentrega</div>
      </div>
    </footer>""", "maqueta")

cambiar("""  <p class="nota" style="margin-top:16px">Reduce la ventana por debajo de 640px:
  la navegacion se oculta y el hero baja de tamano. En el sitio real, ese menu
  pasa a un boton de menu con destino tactil de 44px.</p>""",
        """  <p class="nota" style="margin-top:16px">Reduce la ventana por debajo de 640px:
  la navegacion se oculta y el hero baja de tamano. Abajo esta lo que la
  sustituye, resuelto en el diseno y no dejado al criterio de quien programa.</p>
  <h3 style="margin-top:var(--esp-32)">El logo en el header</h3>
  <p class="nota">En escritorio va el <b>lockup horizontal a 34px</b> de alto.
  En movil, el <b>isotipo solo</b>: el manual fija 24px como minimo del lockup y
  en una barra de 60px con buscador al lado no cabe legible. En el footer y en
  modo oscuro va la <b>version monocroma negativa</b>; el logo en tinta sobre
  fondo tinta desaparece, y ese es el error clasico de esta pantalla.</p>
  <div style="display:flex;gap:var(--esp-24);align-items:center;flex-wrap:wrap;margin-top:var(--esp-16)">
    <div class="espec espec-claro">
      <img src="logo-horizontal.svg" alt="Lockup horizontal de Sastra" style="height:34px">
      <div class="nota" style="margin-top:8px">Escritorio &middot; 34px</div></div>
    <div class="espec espec-claro">
      <img src="isotipo.svg" alt="Isotipo de Sastra" style="height:32px">
      <div class="nota" style="margin-top:8px">Movil &middot; 32px</div></div>
    <div class="espec espec-oscuro">
      <img src="logo-mono-negativo.svg" alt="Logo monocromo negativo de Sastra" style="height:28px">
      <div class="nota" style="margin-top:8px">Footer y modo oscuro</div></div>
  </div>""", "nota maqueta")

# ---------- 8. Secciones nuevas ----------
cambiar("""<section class="doc">
  <h2>Como se usa</h2>""", """<section class="doc">
  <h2>Movil: lo que hay que dejar decidido</h2>
  <p class="nota">Si esto no se decide en el diseno, lo improvisa quien programa
  y cada pantalla sale distinta.</p>
  <div class="telefonos">
    <div class="telefono">
      <div class="rotulo">Header en movil</div>
      <div style="display:flex;align-items:center;gap:8px;padding:var(--esp-12);
                  background:var(--color-superficie);border-bottom:1px solid var(--color-borde)">
        <img src="isotipo.svg" alt="Sastra" style="height:28px">
        <div class="buscador" style="min-height:44px;padding:0 12px">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor"
               stroke-width="1.75" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>
          <input placeholder="Buscar" aria-label="Buscar" style="min-height:44px;font-size:16px">
        </div>
        <button class="btn btn-texto" aria-label="Abrir menu"
                style="min-width:44px;min-height:44px;padding:0;justify-content:center">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor"
               stroke-width="1.75" aria-hidden="true"><path d="M3 6h18M3 12h18M3 18h18"/></svg>
        </button>
      </div>
      <div style="padding:var(--esp-16);font-size:var(--texto-sm)" class="nota">
        Entra el isotipo, no el lockup. El buscador se queda: en un catalogo es la
        navegacion de verdad. Las categorias pasan al menu.
      </div>
    </div>
    <div class="telefono">
      <div class="rotulo">Menu abierto</div>
      <div style="background:var(--color-superficie);padding:var(--esp-16);min-height:220px">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:var(--esp-16)">
          <img src="isotipo.svg" alt="Sastra" style="height:26px">
          <button class="btn btn-texto" aria-label="Cerrar menu"
                  style="min-width:44px;min-height:44px;padding:0;justify-content:center">&#10005;</button>
        </div>
        <a href="#" style="display:block;padding:12px 0;min-height:44px;font-size:var(--texto-lg);
           color:var(--color-texto);text-decoration:none;border-bottom:1px solid var(--color-borde)">Explorar</a>
        <a href="#" style="display:block;padding:12px 0;min-height:44px;font-size:var(--texto-lg);
           color:var(--color-texto);text-decoration:none;border-bottom:1px solid var(--color-borde)">Mujer</a>
        <a href="#" style="display:block;padding:12px 0;min-height:44px;font-size:var(--texto-lg);
           color:var(--color-texto);text-decoration:none;border-bottom:1px solid var(--color-borde)">Hombre</a>
        <a href="#" style="display:block;padding:12px 0;min-height:44px;font-size:var(--texto-lg);
           color:var(--color-texto);text-decoration:none">Como funciona</a>
      </div>
    </div>
    <div class="telefono">
      <div class="rotulo">Ficha de producto</div>
      <div style="background:var(--color-superficie)">
        <div style="aspect-ratio:3/4;background:var(--color-primario-suave);display:grid;
                    place-items:center;color:var(--color-texto-suave);font-size:var(--texto-xs)">Foto 3:4</div>
        <div style="padding:var(--esp-16)">
          <span class="chip">Como nueva</span>
          <p style="margin:8px 0 4px;font-size:var(--texto-base);font-weight:500;line-height:1.35">Chaqueta de cuero negra talla M</p>
          <div><span class="precio">$ 189.000</span><span class="precio-antes">$ 240.000</span></div>
        </div>
        <div class="barra-inferior">
          <button class="btn btn-secundario" aria-label="Guardar"
                  style="flex:0 0 auto;min-width:44px;padding:0 14px">&#9825;</button>
          <button class="btn btn-cta">Comprar</button>
        </div>
      </div>
    </div>
  </div>
  <p class="nota" style="margin-top:var(--esp-16)"><b>Decidido y por escrito:</b>
  el header en movil se queda fijo con isotipo, buscador y menu; el menu abre a
  pantalla completa con enlaces de 44px y boton de cierre de 44px; en la ficha de
  producto el boton de comprar se vuelve <b>barra fija abajo</b>, porque es la
  unica accion que importa en esa pantalla y las esquinas superiores son
  incomodas con una mano. En el resto del sitio no hay barra fija: ocupa espacio
  permanente y no se justifica.</p>
</section>

<section class="doc">
  <h2>Carga, vacio y error</h2>
  <p class="nota">Los tres estados que casi nunca se disenan y siempre aparecen.
  Un catalogo vacio sin mensaje parece un sitio roto.</p>
  <div class="rejilla" style="grid-template-columns:repeat(auto-fit,minmax(260px,1fr))">
    <div>
      <h3>Cargando</h3>
      <div class="rejilla-prod" style="grid-template-columns:1fr 1fr">
        <div class="tarjeta-producto"><div class="esqueleto" style="aspect-ratio:3/4"></div>
          <div class="cuerpo"><div class="esqueleto" style="height:12px;width:70%;margin-bottom:8px"></div>
          <div class="esqueleto" style="height:16px;width:45%"></div></div></div>
        <div class="tarjeta-producto"><div class="esqueleto" style="aspect-ratio:3/4"></div>
          <div class="cuerpo"><div class="esqueleto" style="height:12px;width:60%;margin-bottom:8px"></div>
          <div class="esqueleto" style="height:16px;width:50%"></div></div></div>
      </div>
      <p class="nota">Esqueleto con la forma del contenido, no un circulo girando:
      se percibe mas rapido y la maqueta no salta al llegar los datos.</p>
    </div>
    <div>
      <h3>Sin resultados</h3>
      <div class="vacio">
        <p style="margin:0 0 var(--esp-12);color:var(--color-texto)">
          <b>No hay chaquetas talla M por menos de $50.000</b></p>
        <p style="margin:0 0 var(--esp-16)">Prueba a subir el precio o quitar la talla.</p>
        <button class="btn btn-secundario">Quitar filtros</button>
      </div>
      <p class="nota">Dice <b>por que</b> no hay nada y ofrece el siguiente paso.</p>
      <h3 style="margin-top:var(--esp-24)">No cargo</h3>
      <div class="aviso aviso-error"><b>&#10005; No pudimos cargar el catalogo.</b>
        Revisa tu conexion. <button class="btn btn-texto" style="padding:4px 0">Reintentar</button></div>
    </div>
  </div>
</section>

<section class="doc">
  <h2>Como se usa</h2>""", "secciones nuevas")

cambiar("""    <li>Copia <code>tokens.css</code> al proyecto y enlazalo antes de tus estilos.</li>
    <li>Usa siempre las variables, nunca un HEX suelto en el codigo.</li>
    <li>Para modo oscuro basta con <code>data-tema="oscuro"</code> en el html.</li>
    <li>Si cambia un color de marca, edita el token y vuelve a generar el kit:
        los estados se recalculan solos.</li>""",
        """    <li>Copia <code>tokens.css</code> y <code>marca.css</code> al proyecto y
        enlazalos en ese orden, antes de tus estilos.</li>
    <li><code>tokens.css</code> es generado: <b>no lo edites a mano</b>. Si hay que
        cambiar un color, se cambia en <code>tokens.json</code> y se vuelve a generar,
        porque los estados (hover, pressed, foco, borde de campo) se recalculan solos.</li>
    <li><code>marca.css</code> si se edita: ahi viven los componentes propios de
        Sastra y las correcciones de modo oscuro.</li>
    <li>Usa siempre las variables, <b>nunca un HEX suelto</b> en el codigo. Si
        necesitas un color que no esta, el sistema esta incompleto: anadelo con nombre.</li>
    <li>Carga las tres tipografias desde Google Fonts o autoalojadas. Las tres son
        SIL OFL: no cuestan nada ahora ni cuando el negocio crezca.</li>
    <li>Modo oscuro: <code>data-tema="oscuro"</code> en el elemento
        <code>&lt;html&gt;</code>. Para respetar la preferencia del sistema:
        <code>document.documentElement.dataset.tema =
        matchMedia('(prefers-color-scheme: dark)').matches ? 'oscuro' : 'claro'</code>.</li>""",
        "como se usa")



# ---------- 9. Especimen tipografico completo ----------
# El generador solo emitia una tabla de tamanos con una muestra "Ag". Eso no
# le sirve a quien programa: no dice que rol tiene cada tamano, ni como se
# comporta en movil, ni como se cargan las fuentes.

def fila_rol(rol, clase, esc, mov, familia, peso, inter, muestra):
    return ("<tr><td><code>.{c}</code><br><span class='nota'>{r}</span></td>"
            "<td>{e}</td><td>{m}</td><td>{f}<br><span class='nota'>{p} · int. {i}</span></td>"
            "<td class='{c}' style='max-width:none'>{s}</td></tr>").format(
        c=clase, r=rol, e=esc, m=mov, f=familia, p=peso, i=inter, s=muestra)


ROLES = [
    ("Titular de portada", "tipo-display", "62px", "34px", "Archivo", "700", "1.05", "Compra y vende moda"),
    ("Titular de página", "tipo-h1", "46px", "30px", "Archivo", "700", "1.1", "Chaquetas de mujer"),
    ("Título de sección", "tipo-h2", "34px", "26px", "Archivo", "700", "1.15", "Recién publicado"),
    ("Subtítulo", "tipo-h3", "26px", "22px", "Archivo", "500", "1.2", "Cómo funciona el pago"),
    ("Título de tarjeta", "tipo-titulo-tarjeta", "16px", "16px", "Instrument Sans", "500", "1.35", "Chaqueta de cuero negra talla M"),
    ("Entradilla", "tipo-entradilla", "20px", "18px", "Instrument Sans", "400", "1.5", "Guardamos tu pago hasta que confirmes."),
    ("Cuerpo", "tipo-cuerpo", "16px", "16px", "Instrument Sans", "400", "1.55", "El vendedor recibe el dinero cuando confirmas."),
    ("Secundario", "tipo-secundario", "14px", "14px", "Instrument Sans", "400", "1.5", "Publicado hace 2 días · Medellín"),
    ("Leyenda y legal", "tipo-leyenda", "12px", "12px", "Instrument Sans", "400", "1.45", "Precios en pesos colombianos, IVA incluido."),
    ("Etiqueta de botón", "tipo-etiqueta-boton", "16px", "16px", "Instrument Sans", "700", "1", "Publicar prenda"),
    ("Precio", "precio", "20px", "20px", "IBM Plex Mono", "700", "1.2", "$ 189.000"),
    ("Precio anterior", "precio-antes", "14px", "14px", "IBM Plex Mono", "400", "1.2", "$ 240.000"),
    ("Código de pedido", "codigo-pedido", "14px", "14px", "IBM Plex Mono", "400", "1.4", "SAS-2K48-9R"),
]

especimen = """
<section class="doc">
  <h2>Tipografía: roles y uso</h2>
  <p class="nota"><b>Se aplica el rol, no el tamaño.</b> El nivel de encabezado lo
  decide la estructura del documento (un H1 por página, sin saltarse niveles); el
  tamaño lo decide la clase. Por eso un <code>&lt;h2&gt;</code> puede llevar
  <code>.tipo-h3</code> sin romper nada para un lector de pantalla, y nunca hay
  que bajar de nivel de encabezado solo para que algo se vea más pequeño.</p>
  <div class="tabla-envoltura" tabindex="0" role="region"
       aria-label="Tabla de roles tipográficos, con desplazamiento horizontal">
  <table style="min-width:720px">
    <thead><tr><th>Clase y rol</th><th>Escritorio</th><th>Móvil</th>
    <th>Familia</th><th>Muestra</th></tr></thead>
    <tbody>""" + "".join(fila_rol(*r) for r in ROLES) + """</tbody>
  </table></div>

  <h3>Los titulares son fluidos, el cuerpo no</h3>
  <p class="nota">Los titulares interpolan entre 360px y 1280px de ancho con
  <code>clamp()</code>: un titular de 62px ocupa media pantalla en un celular, y
  uno de 34px se ve flojo en escritorio. <b>Estira y encoge la ventana</b> y mira
  el titular de aquí abajo.</p>
  <div class="tarjeta" style="margin-top:var(--esp-16)">
    <p class="tipo-display" style="margin:0 0 var(--esp-12)">Compra y vende moda con respaldo</p>
    <p class="tipo-entradilla" style="margin:0">Guardamos tu pago hasta que confirmes que
    la prenda llegó como la viste. [muestra]</p>
  </div>
  <p class="nota"><b>El cuerpo se queda fijo en 16px.</b> Por debajo, el navegador
  móvil hace zoom solo al enfocar un campo y descuadra la maqueta. Y la escala usa
  <code>rem + vw</code>, nunca <code>vw</code> solo: con <code>vw</code> solo el
  titular deja de responder al zoom del navegador.</p>

  <h3>Por qué tres familias y no dos</h3>
  <div class="rejilla" style="grid-template-columns:repeat(auto-fit,minmax(240px,1fr))">
    <div class="tarjeta">
      <p style="font-family:var(--fuente-display);font-size:var(--texto-2xl);font-weight:700;margin:0 0 8px">Archivo</p>
      <p class="nota" style="margin:0">Titulares. Condensada y de asta firme: sostiene
      los tamaños grandes sin volverse decorativa. Es la del logotipo, así que el
      titular y la marca riman.</p>
    </div>
    <div class="tarjeta">
      <p style="font-family:var(--fuente-texto);font-size:var(--texto-2xl);font-weight:500;margin:0 0 8px">Instrument Sans</p>
      <p class="nota" style="margin:0">Interfaz y cuerpo. Neutra y de altura de x
      generosa, que es lo que se lee cómodo a 16px en un celular.</p>
    </div>
    <div class="tarjeta">
      <p style="font-family:var(--fuente-mono);font-size:var(--texto-xl);font-weight:700;margin:0 0 8px">IBM Plex Mono</p>
      <p class="nota" style="margin:0">Cifras. La tercera familia se justifica solo
      por esto: numerales tabulares para que los precios se alineen en columna.</p>
    </div>
  </div>
  <p class="nota">La regla general es dos familias como máximo. La tercera entra
  porque hay cifras que alinear, y ese es el único motivo que la justifica.</p>

  <h3>Numerales tabulares: para qué sirven de verdad</h3>
  <p class="nota">Con cifras tabulares todos los dígitos ocupan lo mismo y las
  columnas cuadran. Sin ellas, una lista de precios se lee en zigzag. Compara:</p>
  <p class="nota">Las dos columnas usan <b>la misma familia y el mismo tamaño</b>;
  lo único que cambia es <code>font-variant-numeric</code>. Mira dónde caen los
  puntos de millar.</p>
  <div class="rejilla" style="grid-template-columns:repeat(auto-fit,minmax(240px,1fr))">
    <div class="tarjeta">
      <p class="tipo-secundario" style="margin:0 0 8px"><b>tabular-nums</b> — así se entrega</p>
      <div style="font-family:var(--fuente-texto);font-variant-numeric:tabular-nums;
                  text-align:right;max-width:170px;line-height:1.9;font-size:var(--texto-lg)">
        $ 1.189.000<br>$ 189.000<br>$ 111.000<br>$ 58.000</div>
    </div>
    <div class="tarjeta">
      <p class="tipo-secundario" style="margin:0 0 8px"><b>proportional-nums</b> — el defecto</p>
      <div style="font-family:var(--fuente-texto);font-variant-numeric:proportional-nums;
                  text-align:right;max-width:170px;line-height:1.9;font-size:var(--texto-lg)">
        $ 1.189.000<br>$ 189.000<br>$ 111.000<br>$ 58.000</div>
    </div>
  </div>
  <p class="nota">Con numerales proporcionales el <b>1</b> es más angosto que los
  demás dígitos, así que las columnas se desalinean y la lista se lee en zigzag.
  <b>IBM Plex Mono ya es tabular por construcción</b>, que es exactamente por lo
  que entra como tercera familia: los precios del catálogo salen alineados sin
  tener que pedirlo.</p>

  <h3>Ancho de lectura</h3>
  <p class="nota">Entre 60 y 75 caracteres por línea. Más largo y el ojo pierde el
  renglón al saltar; más corto y se lee a tirones. El token
  <code>--medida</code> vale <code>68ch</code> y lo aplican
  <code>.tipo-cuerpo</code>, <code>.tipo-entradilla</code> y <code>.medida</code>.</p>
  <div class="tarjeta">
    <p class="tipo-cuerpo" style="margin:0">Sastra es el mercado donde cualquiera
    compra y vende moda, nueva y usada, con la plataforma como respaldo de la
    transacción. El pago queda retenido hasta que quien compra confirma que
    recibió la prenda, y solo entonces se libera hacia quien vende. [muestra]</p>
  </div>
  <p class="nota">Esta caja llega al límite y no lo pasa, por ancha que se ponga
  la ventana. Sin <code>max-width</code>, en un monitor grande esta línea mediría
  más de 150 caracteres.</p>

  <h3>Cómo se cargan las fuentes</h3>
  <p class="nota"><b>Para empezar</b>, con Google Fonts basta y es lo que ya trae
  esta guía en el <code>&lt;head&gt;</code>. Pesa poco y no hay que administrar
  archivos.</p>
  <p class="nota"><b>Para producción conviene autoalojarlas</b>: quita una
  conexión a un tercero, mejora el tiempo de carga y evita depender de un
  servicio externo. Descarga los <code>.woff2</code>, súbelos junto al sitio y
  precarga solo los dos que se ven de entrada:</p>
  <pre style="background:var(--color-primario-suave);padding:var(--esp-16);
       border-radius:var(--radio-md);overflow-x:auto;font-size:var(--texto-sm)"><code>&lt;link rel="preload" as="font" type="font/woff2" crossorigin
      href="/fuentes/instrument-sans-400.woff2"&gt;
&lt;link rel="preload" as="font" type="font/woff2" crossorigin
      href="/fuentes/archivo-700.woff2"&gt;</code></pre>
  <p class="nota"><b>Precarga solo esos dos.</b> Precargarlo todo compite por el
  ancho de banda con las fotos de producto y termina retrasando la página, que es
  justo lo contrario de lo que se busca.</p>
  <p class="nota"><b>Pesos: tres y no más.</b> 400, 500 y 700. Cada peso extra es
  una descarga más, y en celular con datos móviles eso se nota.</p>
  <div class="aviso aviso-aviso" style="margin-top:var(--esp-16);max-width:68ch">
    <b>&#9888; Un pendiente honesto.</b> <code>tipografia.css</code> trae un bloque
    de respaldo con <code>size-adjust</code> para que la página no salte cuando la
    fuente real reemplaza a la del sistema. <b>Esos tres valores son un punto de
    partida y no están medidos</b> contra los archivos reales. Mídelos antes de
    publicar o borra el bloque: un ajuste inventado empeora el salto en vez de
    arreglarlo.
  </div>
</section>
"""

ancla = '<section class="doc">\n  <h2>Botones y estados</h2>'
if ancla not in h:
    sys.exit("No se encontro donde insertar el especimen tipografico. "
             "Revisa la variable 'ancla' al final de construir.py.")
h = h.replace(ancla, especimen + "\n" + ancla, 1)
(KIT / "index.html").write_text(h, encoding="utf-8")
print("Especimen tipografico insertado")

(KIT / "index.html").write_text(h, encoding="utf-8")
print("Kit de Sastra reconstruido en " + str(KIT))
print("Ahora corre:  python3 verificar.py")
