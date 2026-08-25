# Frontend

Sitio web de Sendik. Angular 21 con renderizado en servidor e hidratacion,
TypeScript estricto, Transloco, TanStack Query, Vitest y CSS propio sobre los
tokens de marca.

Las convenciones de codigo estan en `CLAUDE.md`. Este archivo es solo para
ponerlo en marcha.

## Arranque local

```
npm install
npm start
```

En `http://localhost:4200`. El backend debe estar arriba en el 8080.

`npm start`, `npm run build` y `npm run serve:ssr` leen el `.env` de la raiz del
repositorio, el mismo que usa `docker-compose`. Si no existe, se arranca igual y
la primera llamada a la API falla con un mensaje explicito. Las variables del
frontend estan en `../docs/operacion/configuracion.md`; la unica que hace falta
para ver el sitio es `API_BASE_URL`.

`NG_ALLOWED_HOSTS` merece un parrafo aparte: es la lista de dominios a los que el
servidor acepta responder. Si falta, Angular **no falla**: entrega la pagina sin
renderizar y la pinta el navegador. El sitio parece funcionar mientras el
buscador recibe un documento vacio. Por eso `npm run serve:ssr` se niega a
arrancar sin ella.

## Comandos

| Accion                       | Comando             |
| ---------------------------- | ------------------- |
| Desarrollo                   | `npm start`         |
| Compilar para produccion     | `npm run build`     |
| Servidor de SSR local        | `npm run serve:ssr` |
| Pruebas                      | `npm test`          |
| Pruebas de extremo a extremo | `npm run e2e`       |
| Verificacion completa        | `npm run verify`    |
| Formato y linter             | `npm run lint`      |
| Aplicar formato              | `npm run format`    |

`npm run serve:ssr` sirve en el 4000. `npm run e2e` construye el sitio y levanta
ese servidor por su cuenta; los navegadores no se descargan solos, la primera vez
hace falta `npx playwright install chromium`.

## Estructura

```
src/app/
  core/         transversal: configuracion, interceptores, i18n, tema
  shared/       componentes sin logica de negocio
  features/<funcionalidad>/
    domain/          modelos y reglas puras, sin Angular
    application/     casos de uso, puertos, estado
    infrastructure/  adaptadores HTTP y mapeadores
    presentation/    componentes y rutas
```

Una funcionalidad no importa de otra. Lo compartido sube a `shared` o a `core`.
No es solo una convencion escrita: `eslint.config.mjs` la comprueba y el build
falla si se cruza una capa o una funcionalidad.

Las traducciones viven en `src/i18n/es.json` y `src/i18n/en.json`. Se sirven como
activo estatico en `/i18n/<idioma>.json` y ademas van incrustadas en el paquete
del servidor, para que el renderizado no dependa de una peticion contra si mismo.

## Estilos

Las cuatro hojas del sistema están en `src/styles/` y las importa
`src/styles.css` en orden fijo: `fuentes.css`, `tokens.css`, `tipografia.css`,
`marca.css`. Alterar el orden rompe el sistema en silencio, porque `marca.css`
carga al final.

**Solo `tokens.css` es generado.** Sale de `docs/ui/generador/`, y para cambiar
un color se corre `python3 publicar.py` desde esa carpeta, que reconstruye,
verifica el contraste en los dos modos y copia la hoja aquí. Las otras tres son
del proyecto y se editan directamente en `src/styles/`.

Las `.woff2` están en `public/fuentes/`, autoalojadas: Inter para todo el texto y
Archivo para los titulares, cada una en subconjunto latino y latino extendido. Al
ser variables, cada archivo cubre el rango de pesos entero. Solo se precargan los
dos latinos.

⚠️ **No son los que trajo el kit**: aquellos eran las variantes itálicas de las
dos familias y el sitio entero se veía inclinado. El detalle, el defecto de
`fuentes.py` que lo causa y la prueba que lo caza están en el LEEME de esa
carpeta.

Ningún HEX, ningún píxel ni ningún tamaño de letra sueltos: todo por variable o
por clase de rol. Un hook bloquea el commit de un
color literal. Si falta un valor, se nombra en `marca.css` y se documenta.

## Idiomas

Espanol por omision, ingles disponible. Ningun texto visible se escribe en una
plantilla: todo pasa por una clave de Transloco jerarquica. El idioma se resuelve
en el servidor durante el renderizado, para que el HTML llegue ya traducido.

## Accesibilidad

Es criterio de aceptacion, no un extra. Contraste minimo 4.5:1, foco visible de
3px, destinos tactiles de 44px, navegacion completa por teclado y sin desbordes a
360px ni con el texto al 200%. El punto de partida esta auditado en
`docs/ui/contraste.md`, y `verificar.py` lo comprueba en modo claro y oscuro cada
vez que se publica: 18 pares por modo. Ese es el listón que se mantiene.
