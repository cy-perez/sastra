import {
  AngularNodeAppEngine,
  createNodeRequestHandler,
  isMainModule,
  writeResponseToNodeResponse,
} from '@angular/ssr/node';
import express from 'express';
import { join } from 'node:path';

import { assertRenderingEnvironment, readAppConfig } from './app/core/config/read-app-config';

const browserDistFolder = join(import.meta.dirname, '../browser');

const app = express();
const angularApp = new AngularNodeAppEngine();

/**
 * Accept-CH pide al navegador que, a partir de la siguiente peticion, incluya su
 * preferencia de esquema de color. Con ella el HTML sale ya en modo oscuro sin
 * esperar al JavaScript. En la primera visita no llega todavia y se sirve claro.
 *
 * Vary es obligatorio aqui: la misma direccion devuelve HTML distinto segun el
 * idioma, la cookie y esa preferencia. Sin Vary, una cache intermedia le daria a
 * un visitante la pagina renderizada para otro.
 */
app.use((_request, response, next) => {
  response.setHeader('Accept-CH', 'Sec-CH-Prefers-Color-Scheme');
  response.setHeader('Critical-CH', 'Sec-CH-Prefers-Color-Scheme');
  response.setHeader('Vary', 'Cookie, Accept-Language, Sec-CH-Prefers-Color-Scheme');
  next();
});

/**
 * Archivos estaticos de /browser. Llevan hash en el nombre, asi que se pueden
 * cachear un ano sin riesgo.
 */
app.use(
  express.static(browserDistFolder, {
    maxAge: '1y',
    index: false,
    redirect: false,
  }),
);

/** El resto lo renderiza Angular. */
app.use((request, response, next) => {
  angularApp
    .handle(request)
    .then((rendered) => (rendered ? writeResponseToNodeResponse(rendered, response) : next()))
    .catch(next);
});

if (isMainModule(import.meta.url) || process.env['pm_id']) {
  // Arranque real: aqui si se valida el entorno completo y se cae si falta algo.
  // Es preferible no levantar el servidor a descubrirlo con un visitante dentro.
  readAppConfig(process.env);
  assertRenderingEnvironment(process.env);

  const port = process.env['PORT'] ?? 4000;
  app.listen(port, (error) => {
    if (error) {
      throw error;
    }
    console.log(`Servidor de Sastra escuchando en http://localhost:${port}`);
  });
}

/** Lo usa el CLI durante el desarrollo y la construccion. */
export const reqHandler = createNodeRequestHandler(app);
