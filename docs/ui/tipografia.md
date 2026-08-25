# Tipografia del sitio — Sendik

## Familias

| Rol | Familia | Pesos | Licencia | Uso |
|---|---|---|---|---|
| display | **Archivo** | 600 | SIL OFL 1.1 | Logotipo, titulares y precios grandes |
| texto | **Inter** | 400, 500, 600 | SIL OFL 1.1 | Cuerpo, interfaz, formularios, botones, eslogan |

## Como se instala

En el `<head>`, **antes** de la hoja de estilos del sitio:

```html
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600&family=Archivo:wght@600&display=swap">
```

**Para entregar el kit sin dependencias externas**, autoaloja las
tipografias: cargan antes y el sitio no se rompe si el servicio falla.

```bash
python3 generador/fuentes.py tokens.json --out fuentes
```

Eso descarga los `.woff2`, sus licencias y genera `fuentes.css`.

## Escala y jerarquia

| Token | px | Donde se usa | Familia | Interlineado | Peso |
|---|---|---|---|---|---|
| `--texto-3xl` | 46 | H1 — titular de portada | Archivo | 1.1 | 700 |
| `--texto-2xl` | 34 | H2 — titulo de seccion | Archivo | 1.1 | 700 |
| `--texto-xl` | 26 | H3 — subtitulo | Archivo | 1.1 | 700 |
| `--texto-lg` | 20 | Entradilla | Inter | 1.55 | 400 |
| `--texto-base` | 16 | Cuerpo de texto | Inter | 1.55 | 400 |
| `--texto-sm` | 14 | Texto secundario, etiquetas | Inter | 1.55 | 400 |
| `--texto-xs` | 12 | Leyendas, avisos legales | Inter | 1.55 | 400 |

## Reglas

- El cuerpo **nunca baja de 16px**. Por debajo, los navegadores moviles
  hacen zoom automatico al enfocar un campo y descuadran la maqueta.
- Ancho de linea de 60 a 75 caracteres en texto corrido.
- Interlineado de 1.55 en texto y 1.1 en titulares.
- Maximo tres pesos en todo el sitio.
- Usa siempre `var(--fuente-texto)` y `var(--fuente-display)`,
  nunca el nombre de la familia escrito a mano en el CSS.
