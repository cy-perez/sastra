# Configuracion de Claude Code

Lo que hay aqui no es opcional: es el sustituto de la revision de pares en un
proyecto de una sola persona.

```
settings.json   permisos y registro de hooks
commands/       comandos de barra del flujo de trabajo
agents/         subagentes revisores, con su propio contexto
hooks/          scripts que se ejecutan siempre, sin depender del modelo
```

El detalle de cuando usar cada cosa esta en `docs/ia/flujo-de-trabajo.md`.

## Hooks

Node sin dependencias, para que funcionen igual en Windows y en la nube. Se
ejecutan desde la raiz del repositorio. Codigo de salida 2 bloquea la accion y
devuelve el motivo al agente; el codigo 1 no bloquea nada.

Para probar uno a mano:

```
echo {"tool_name":"Edit","tool_input":{"file_path":"frontend/src/styles/tokens.css"}} | node .claude/hooks/proteger-archivos.mjs
```

## Ajustes personales

`settings.local.json` no se versiona y sirve para preferencias de la maquina.
Todo lo que deba aplicarse siempre va en `settings.json`.
