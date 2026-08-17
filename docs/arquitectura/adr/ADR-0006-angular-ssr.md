# ADR-0006 — Renderizado en servidor desde el inicio

**Fecha:** 2026-08-15 · **Estado:** aceptada

## Contexto

Un marketplace vive del tráfico de búsqueda hacia sus fichas de producto.

## Decisión

Angular con renderizado en servidor e hidratación desde el primer día, aunque la
Fase 1 solo tenga páginas informativas y autenticación.

## Motivo

Sin renderizado en servidor, cada ficha de producto llega al buscador como un
documento vacío. Se puede argumentar que hoy los buscadores ejecutan JavaScript,
pero lo hacen tarde, de forma parcial e impredecible, y las vistas previas de
WhatsApp y las redes ni siquiera lo intentan. Para un producto cuyo canal
principal de descubrimiento será compartir un enlace, eso es fatal.

Se activa desde el inicio y no después porque agregarlo tarde obliga a auditar
todo el código en busca de accesos a `window`, `document` y `localStorage`, y a
rehacer la configuración de traducciones y de tema. Hacerlo desde cero cuesta un
día; hacerlo con la aplicación ya construida cuesta semanas.

## Consecuencias

- El código no puede tocar API del navegador sin protección explícita.
- El idioma y el tema se resuelven en el servidor para evitar el parpadeo al
  cargar.
- El despliegue necesita un entorno con Node, no un simple servidor de archivos.
- Cada ficha entrega metadatos completos y datos estructurados de producto.

## Cuándo revisar

No se prevé. Si el costo de ejecución se volviera un problema, la salida es
generación estática incremental para el catálogo, no eliminar el renderizado.
