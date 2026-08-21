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

// La fase vigente se busca, no se supone. Antes estaba escrito `Fase 1` en el
// patron, asi que al cerrarse esa fase el hook habria seguido anunciandola en cada
// sesion y habria bloqueado el trabajo de la fase nueva. Lo que marca cual esta en
// curso es la frase "es la fase en curso" dentro de su seccion.
if (existsSync('docs/producto/alcance.md')) {
  const alcance = readFileSync('docs/producto/alcance.md', 'utf8');
  const secciones = alcance.split(/^## /m).filter((seccion) => seccion.startsWith('Fase '));
  const enCurso = secciones.find((seccion) => /es la fase en curso/i.test(seccion));

  if (enCurso) {
    const titulo = enCurso.split('\n', 1)[0].trim();
    partes.push(`Fase vigente segun docs/producto/alcance.md: ${titulo}. No implementes funcionalidad de fases posteriores.`);
  } else {
    // Ninguna seccion se declara en curso: es un error del documento, y decirlo es
    // mejor que callar y dejar que el agente suponga.
    partes.push('Ninguna fase se declara en curso en docs/producto/alcance.md: revisa el documento antes de implementar.');
  }
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
