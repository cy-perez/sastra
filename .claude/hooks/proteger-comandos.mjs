#!/usr/bin/env node
// PreToolUse sobre Bash. Bloquea comandos destructivos o que saltan controles
// de calidad. La lista es corta a proposito: si el agente ignora una regla de
// CLAUDE.md el costo es una molestia; si ejecuta uno de estos, es un incidente.

import { readFileSync } from 'node:fs';

const entrada = JSON.parse(leerStdin() || '{}');
const comando = entrada.tool_input?.command ?? '';
if (!comando) process.exit(0);

const PROHIBIDOS = [
  { patron: /git\s+push\s+.*(--force|-f)\b/, motivo: 'Reescribir historia publicada. Usa --force-with-lease solo tras confirmarlo con la persona.' },
  { patron: /git\s+(commit|push)\s+.*--no-verify/, motivo: 'Saltarse las verificaciones previas al commit.' },
  { patron: /git\s+reset\s+--hard/, motivo: 'Descarta trabajo sin posibilidad de recuperarlo. Propon git stash.' },
  { patron: /git\s+checkout\s+\.\s*$/, motivo: 'Descarta todos los cambios locales.' },
  { patron: /rm\s+-rf?\s+[\/~]/, motivo: 'Borrado recursivo fuera del proyecto.' },
  { patron: /(gradlew|gradle).*-x\s*test/, motivo: 'Excluir las pruebas del build.' },
  { patron: /(gradlew|gradle).*--continue.*check/, motivo: 'Enmascara fallos de verificacion.' },
  { patron: /npm\s+(install|i)\s+(?!.*--save-dev)(?!$)/, motivo: 'Agregar dependencias sin proponerlo. Toda dependencia nueva se justifica antes de instalarse.' },
  { patron: /flyway.*(clean|repair)/, motivo: 'flyway clean borra el esquema completo; repair reescribe el historial de migraciones.' },
  { patron: /DROP\s+(TABLE|DATABASE|SCHEMA)/i, motivo: 'Instruccion destructiva de base de datos fuera de una migracion.' },
  { patron: /(^|\s)(psql|docker\s+compose)\b.*prod/i, motivo: 'Operacion apuntando a produccion desde la sesion de desarrollo.' },
];

for (const { patron, motivo } of PROHIBIDOS) {
  if (patron.test(comando)) {
    process.stderr.write(`Comando bloqueado: ${motivo}\nComando: ${comando}\nSi es realmente necesario, explicalo y deja que la persona lo ejecute.\n`);
    process.exit(2);
  }
}
process.exit(0);

function leerStdin() {
  try { return readFileSync(0, 'utf8'); } catch { return ''; }
}
