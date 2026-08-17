#!/usr/bin/env node
// PreToolUse sobre Read/Edit/Write/MultiEdit.
// Impide leer secretos y modificar archivos que son fuente de verdad generada
// o historial inmutable. Salir con codigo 2 bloquea la accion y devuelve el
// mensaje de stderr al agente.

import { readFileSync } from 'node:fs';

const entrada = JSON.parse(leerStdin() || '{}');
const herramienta = entrada.tool_name ?? '';
const ruta = (entrada.tool_input?.file_path ?? entrada.tool_input?.notebook_path ?? '').replace(/\\/g, '/');

if (!ruta) process.exit(0);

const SECRETOS = [
  // .env y sus variantes locales, pero no .env.example: ese si se versiona y
  // debe listar todas las claves con valores de ejemplo
  // (docs/operacion/configuracion.md). Bloquearlo impedia mantenerlo al dia.
  /(^|\/)\.env($|\.(?!example))/,
  /\.pem$/, /\.p12$/, /\.jks$/,
  /(^|\/)id_rsa/,
  /service-account.*\.json$/,
  /(^|\/)secrets?\.(ya?ml|json)$/,
];

const SOLO_LECTURA = [
  { patron: /(frontend\/src\/styles|docs\/ui)\/tokens\.css$/, motivo: 'tokens.css es generado. El origen es docs/ui/generador/tokens.json: cambia el token y corre "python3 publicar.py". Los estados (hover, pressed, foco, borde de campo) se recalculan solos.' },
  { patron: /(frontend\/src\/styles|docs\/ui)\/(marca|tipografia)\.css$/, motivo: 'Es una copia publicada, no el original. Edita docs/ui/generador/fuentes/ y corre "python3 publicar.py": el siguiente build sobrescribiria cualquier cambio hecho aqui.' },
  { patron: /docs\/ui\/(index\.html|LEEME\.md|contraste\.md)$/, motivo: 'Salida del generador del kit. Se regenera con construir.py, no se edita.' },
  { patron: /docs\/ui\/generador\/(kit_ui|construir)\.py$/, motivo: 'Script entregado por diseno. Modificarlo bifurca el kit y la proxima entrega se pierde. Si hace falta otro paso, va en publicar.py, que si es del proyecto.' },
  { patron: /docs\/ui\/generador\/_plantilla_kit\.html$/, motivo: 'Plantilla del generador. Los cambios de la guia van como parche en construir.py.' },
  { patron: /docs\/marca\//, motivo: 'Los activos de marca son entregables cerrados. Si algo falta, se solicita a diseno.' },
  { patron: /gradle\/wrapper\//, motivo: 'El wrapper de Gradle se cambia con el comando gradlew wrapper, no editando archivos.' },
  { patron: /package-lock\.json$/, motivo: 'package-lock.json lo escribe npm. Usa npm install o npm ci.' },
];

const esLectura = herramienta === 'Read';

if (esLectura && SECRETOS.some((p) => p.test(ruta))) {
  bloquear(`Lectura bloqueada: ${ruta} contiene secretos. Las claves llegan por variable de entorno; la lista de nombres esta en docs/operacion/configuracion.md y en .env.example.`);
}

if (!esLectura) {
  if (SECRETOS.some((p) => p.test(ruta))) {
    bloquear(`Escritura bloqueada: ${ruta} contiene secretos y no se versiona. Si hace falta una variable nueva, agregala a .env.example y a docs/operacion/configuracion.md.`);
  }
  for (const { patron, motivo } of SOLO_LECTURA) {
    if (patron.test(ruta)) bloquear(`Archivo protegido: ${ruta}. ${motivo}`);
  }
  if (/db\/migration\/V\d+__/.test(ruta) && herramienta !== 'Write') {
    bloquear(`Migracion ya existente: ${ruta}. Una migracion aplicada nunca se edita. Crea la siguiente version con un nombre nuevo.`);
  }
}

process.exit(0);

function bloquear(mensaje) {
  process.stderr.write(mensaje + '\n');
  process.exit(2);
}

function leerStdin() {
  try { return readFileSync(0, 'utf8'); } catch { return ''; }
}
