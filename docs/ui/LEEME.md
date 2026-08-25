# Kit de interfaz — Sendik

Todo lo que necesita quien programa el sitio. **El kit se regenera solo**
si cambia una decision de diseno; no edites a mano los archivos generados.

## Que hay aqui

```
kit/
├── index.html       guia visual — abrela en el navegador
├── tokens.css       variables para el proyecto (GENERADO, no editar)
├── tokens.json      las decisiones. ESTE es el archivo que se edita
├── tipografia.md    familias, pesos, licencia e instalacion
├── contraste.md     informe WCAG
│                 (las tipografias se cargan desde Google Fonts)
│
└── generador/       kit_ui.py y sus plantillas
```

## Como cambiar algo

1. Edita `tokens.json` — por ejemplo el color primario o un tamano de texto.
2. Vuelve a generar:

```bash
python3 generador/kit_ui.py tokens.json --out . --logo logo-horizontal.svg --logo-oscuro logo-mono-negativo.svg
```

Los estados (hover, pressed, foco, texto sobre cada fondo) y el modo oscuro
se recalculan solos, y el informe de contraste se rehace. Por eso no se
editan a mano: el proximo regenerado borraria el cambio.

## Como se enlaza en el sitio

```html
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600&family=Archivo:wght@600&display=swap">
<link rel="stylesheet" href="tokens.css">
```

Para autoalojar las tipografias y no depender de Google:

```bash
python3 generador/fuentes.py tokens.json --out fuentes
```

## Reglas

- Usa siempre las variables de `tokens.css`, nunca un HEX o un px suelto.
- Modo oscuro: `data-tema="oscuro"` en el `<html>`.
- Los textos del kit son de muestra: reemplazalos por los definitivos.
