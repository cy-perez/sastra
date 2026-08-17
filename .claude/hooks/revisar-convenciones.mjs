#!/usr/bin/env node
// PostToolUse sobre Edit/Write/MultiEdit. El archivo ya se escribio; el hook lo
// relee y devuelve al agente la lista de convenciones incumplidas para que las
// corrija en el mismo turno. Solo entran reglas verificables con certeza: una
// regla con falsos positivos se vuelve ruido y se termina ignorando.

import { readFileSync, existsSync } from 'node:fs';

const entrada = JSON.parse(leerStdin() || '{}');
const ruta = (entrada.tool_input?.file_path ?? '').replace(/\\/g, '/');
if (!ruta || !existsSync(ruta)) process.exit(0);

let texto = '';
try { texto = readFileSync(ruta, 'utf8'); } catch { process.exit(0); }

const hallazgos = [];
const es = (...ext) => ext.some((e) => ruta.endsWith(e));
const enFrontend = ruta.includes('frontend/');
const enBackend = ruta.includes('backend/');

// --- Estilos: ningun valor visual suelto ---------------------------------
const esHojaDelSistema = /(src\/styles|docs\/ui|generador\/fuentes)\/(tokens|tipografia|marca|fuentes)\.css$/.test(ruta);

if (enFrontend && es('.css', '.scss', '.html') && !esHojaDelSistema) {
  const sinComentarios = texto.replace(/\/\*[\s\S]*?\*\//g, '');
  if (/#[0-9a-fA-F]{3,8}\b/.test(sinComentarios)) {
    hallazgos.push('Color literal en HEX. Usa una variable de tokens.css, por ejemplo var(--color-superficie). Si el color no existe, el sistema esta incompleto: se nombra en marca.css y se documenta.');
  }
  if (/\b(rgb|rgba|hsl)\(/.test(sinComentarios)) {
    hallazgos.push('Color literal en rgb/hsl. Misma regla: va por variable.');
  }
  const px = sinComentarios.match(/(?<![\w-])(?:padding|margin|gap|border-radius|font-size)\s*:\s*[^;]*\d+px/g);
  if (px) {
    hallazgos.push('Medida en px fuera del sistema. Espaciados con var(--esp-N), radios con var(--radio-*), tipografia con var(--texto-*). Las unicas medidas fijas permitidas son las del sistema: cabecera 72/60, logo 34, ancho maximo 1200, destino tactil 44.');
  }
  if (/font-size\s*:|font-family\s*:|font-weight\s*:\s*\d/.test(sinComentarios)) {
    hallazgos.push('Tipografia definida fuera del sistema. tipografia.css es la unica fuente de verdad del tipo: se aplica el rol (.tipo-h2, .tipo-cuerpo, .tipo-titulo-tarjeta, .precio, .tipo-secundario), no el tamano ni la familia. Si falta un rol, se agrega alli y se documenta.');
  }
  if (/outline\s*:\s*(none|0)/.test(sinComentarios)) {
    hallazgos.push('Se esta eliminando el indicador de foco. El foco visible de 3px es requisito de accesibilidad y no se retira.');
  }
}

// --- Angular: API vigentes ------------------------------------------------
if (enFrontend && es('.ts', '.html')) {
  const reglas = [
    [/\*ngIf|\*ngFor|\*ngSwitch/, 'Directivas estructurales antiguas. Este proyecto usa el flujo de control de plantilla: @if, @for con track, @switch.'],
    [/@NgModule/, 'NgModule. Todos los componentes son standalone.'],
    [/@Input\(|@Output\(/, 'Decoradores @Input/@Output. Se usan las funciones input(), output() y model().'],
    [/@ViewChild\(|@ContentChild\(/, 'Decorador de consulta antiguo. Se usan viewChild() y contentChild().'],
    [/HttpClientModule/, 'HttpClientModule. Se usa provideHttpClient().'],
    [/localStorage|sessionStorage|window\.|document\./, 'Acceso directo a API del navegador. Rompe el renderizado en servidor: aislalo tras afterNextRender o una comprobacion de plataforma.'],
  ];
  for (const [patron, mensaje] of reglas) if (patron.test(texto)) hallazgos.push(mensaje);

  if (es('.ts') && /@Component\(/.test(texto) && !/OnPush/.test(texto)) {
    hallazgos.push('Componente sin ChangeDetectionStrategy.OnPush.');
  }
  if (es('.html') && /@for\s*\([^)]*\)\s*\{/.test(texto) && !/track\s/.test(texto)) {
    hallazgos.push('@for sin track. Debe seguir un identificador estable.');
  }
}

// --- Spring Boot 4: API que cambiaron ------------------------------------

// La prueba de arquitectura es el catalogo de APIs prohibidas: tiene que
// nombrarlas para poder prohibirlas, y ahi aparecen como cadenas, nunca como
// import. Es el unico archivo exento y se identifica por ruta completa, para
// que crear un ArchitectureTest.java en otra carpeta no sirva de atajo.
const esCatalogoDeApisProhibidas =
  /backend\/bootstrap\/src\/test\/java\/co\/sastra\/ArchitectureTest\.java$/.test(ruta);

if (enBackend && es('.java') && !esCatalogoDeApisProhibidas) {
  const reglas = [
    [/com\.fasterxml\.jackson/, 'Jackson 2. Spring Boot 4 usa Jackson 3: paquete tools.jackson.'],
    [/javax\./, 'Espacio de nombres javax. Se usa jakarta.'],
    [/RestTemplate/, 'RestTemplate. Se usa RestClient o una interfaz @HttpExchange.'],
    [/@MockBean|@SpyBean/, 'Anotacion retirada. Se usa @MockitoBean y @MockitoSpyBean.'],
    [/WebSecurityConfigurerAdapter/, 'Clase eliminada hace varias versiones. La seguridad se configura con un bean SecurityFilterChain.'],
    [/@Autowired\s+(private|protected)/, 'Inyeccion por campo. Se inyecta por constructor, sin anotacion.'],
    [/ddl-auto/, 'Generacion automatica de esquema. El esquema lo gobierna Flyway.'],
    [/\b(double|float)\s+\w*([Pp]rice|[Aa]mount|[Mm]oney|[Tt]otal|[Cc]ommission)/, 'Dinero en coma flotante. Se usa el objeto de valor Money con BigDecimal.'],
  ];
  for (const [patron, mensaje] of reglas) if (patron.test(texto)) hallazgos.push(mensaje);

  if (/\/domain\//.test(ruta) && /import\s+(org\.springframework|jakarta\.persistence|tools\.jackson|org\.hibernate)/.test(texto)) {
    hallazgos.push('El modulo domain esta importando infraestructura. La dependencia va siempre hacia adentro: domain no conoce ningun framework.');
  }
  if (/\/presentation\//.test(ruta) && /@Transactional/.test(texto)) {
    hallazgos.push('@Transactional en presentacion. La transaccion pertenece al caso de uso.');
  }
}

// --- Textos visibles sin traducir ----------------------------------------
if (enFrontend && es('.html')) {
  const conTexto = texto.replace(/<!--[\s\S]*?-->/g, '').match(/>\s*[A-Za-zÁÉÍÓÚÑáéíóúñ][^<>{}]{3,}</g);
  if (conTexto && !/transloco/i.test(texto)) {
    hallazgos.push('Texto visible escrito en la plantilla. Todo texto pasa por una clave de Transloco, en es y en en.');
  }
}

if (hallazgos.length) {
  process.stderr.write(`Convenciones incumplidas en ${ruta}:\n` + hallazgos.map((h) => `- ${h}`).join('\n') + '\nCorrigelo ahora, antes de continuar con la tarea.\n');
  process.exit(2);
}
process.exit(0);

function leerStdin() {
  try { return readFileSync(0, 'utf8'); } catch { return ''; }
}
