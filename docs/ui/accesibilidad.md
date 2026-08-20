# Accesibilidad: qué hay y cómo se comprueba

`docs/producto/alcance.md` pedía «controles de accesibilidad» sin decir cuáles.
Una línea así no se puede dar por cumplida ni por incumplida, así que aquí está la
lista concreta. Cada punto es comprobable, y casi todos los comprueba una prueba.

Las reglas de escritura de componentes están en `frontend/CLAUDE.md`. Este
documento no las repite: dice qué existe hoy y dónde se verifica.

## Los cinco controles

Cinco cosas que la persona usa o que el sitio respeta por ella.

| Control | Dónde | Se comprueba en |
|---|---|---|
| Enlace de salto al contenido | Primer elemento enfocable de cada página (`site-header.html`) | `e2e/shell.spec.ts` |
| Foco visible de 3px | `tokens.css`, con anillo propio dentro de `.franja-oscura` | `e2e/portada.spec.ts` |
| Conmutador de tema claro y oscuro | Cabecera, con `aria-pressed` | `e2e/shell.spec.ts`, `core/theme/theme.spec.ts` |
| Selector de idioma ES/EN | Cabecera, con etiqueta para lector de pantalla | `e2e/shell.spec.ts` |
| Respeto de las preferencias del sistema | `prefers-reduced-motion` en `tokens.css` y `marca.css`; tema preferido si no hay elección guardada | `core/theme/theme.spec.ts` |

**Lo que no hay: control de tamaño de texto.** No está y no se añade por iniciativa
propia: sería funcionalidad de producto que nadie ha decidido. El navegador ya
permite ampliar, y el sistema tipográfico está en unidades relativas para que
ampliar funcione. Si algún día se decide, entra como criterio de una historia.

## Las garantías del sistema

No son controles: son propiedades que todo componente cumple.

- **Un solo `h1` por página** y sin saltos de nivel. Lo comprueban
  `e2e/shell.spec.ts` y `e2e/registro.spec.ts`.
- **Contraste mínimo** 4.5:1 en texto normal y 3:1 en texto grande, iconos y
  bordes de control. El informe es `contraste.md` y lo regenera
  `generador/verificar.py` en los dos modos.
- **Formularios** con `label` asociado, errores con `aria-describedby` y
  `aria-invalid`. Cada formulario de cuentas tiene su prueba de componente.
- **Navegación completa por teclado**, menú móvil incluido, con `Escape` para
  cerrarlo.
- **Destinos táctiles de 44px** como mínimo.
- **Imágenes** con `alt` real o `alt=""` si son decorativas.

## La auditoría automática

`frontend/e2e/accesibilidad.spec.ts` pasa axe-core sobre WCAG 2.2 AA en todas las
páginas públicas y en los dos modos. Lo decidió ADR-0016. Un fallo de contraste o
de foco rompe la construcción igual que un error de compilación, y ninguna regla
de axe se desactiva para que la suite pase.

Dos límites que conviene no olvidar:

1. **Un motor automático encuentra una parte de los problemas, no todos.** La
   suite en verde no significa que el sitio sea accesible. La revisión de teclado
   a mano sigue haciendo falta en cada componente nuevo.
2. **Una página que no está en la lista de rutas auditadas no se audita**, y nadie
   se entera. La lista sale de `content-routes.ts` y `legal-routes.ts`, así que una
   página informativa o legal nueva entra sola. Una que no viva en esas constantes
   —la portada, el registro, cualquier pantalla de cuenta— hay que agregarla a
   mano.

## Cuando se agrega un componente

1. Teclado a mano: llegar, activar y salir sin ratón.
2. Si usa una combinación de colores que no existía, agregar ese par a `PARES` en
   `generador/verificar.py`. Un par que no está en la lista no se comprueba.
3. Si es una página pública nueva, agregarla a la lista de
   `e2e/accesibilidad.spec.ts`.
