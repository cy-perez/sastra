#!/usr/bin/env node
// SessionStart. Inyecta el estado real del repositorio al abrir la sesion, para
// que el agente no arranque suponiendo en que punto quedo el trabajo.

import { execSync } from 'node:child_process';
import { readFileSync, existsSync } from 'node:fs';

const partes = [];

try {
  const rama = execSync('git rev-parse --abbrev-ref HEAD', { encoding: 'utf8' }).trim();
  const ultimos = execSync('git log -3 --pretty=format:%s', { encoding: 'utf8' }).trim();
  const sucio = execSync('git status --porcelain', { encoding: 'utf8' }).trim();
  partes.push(`Rama actual: ${rama}`);
  if (ultimos) partes.push(`Ultimos commits:\n${ultimos}`);
  if (sucio) partes.push(`Hay cambios sin confirmar en ${sucio.split('\n').length} archivo(s).`);
} catch { /* repositorio aun no inicializado */ }

if (existsSync('docs/producto/alcance.md')) {
  const fase = readFileSync('docs/producto/alcance.md', 'utf8').match(/^## (Fase 1[^\n]*)/m);
  if (fase) partes.push(`Fase vigente segun docs/producto/alcance.md: ${fase[1]}. No implementes funcionalidad de fases posteriores.`);
}

if (partes.length) {
  process.stdout.write(JSON.stringify({
    hookSpecificOutput: {
      hookEventName: 'SessionStart',
      additionalContext: partes.join('\n'),
    },
  }));
}
process.exit(0);
