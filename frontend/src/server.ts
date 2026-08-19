import {
  AngularNodeAppEngine,
  createNodeRequestHandler,
  isMainModule,
  writeResponseToNodeResponse,
} from '@angular/ssr/node';
import express from 'express';
import { join } from 'node:path';

import {
  assertRenderingEnvironment,
  avisosDeConfiguracion,
  readAppConfig,
} from './app/core/config/read-app-config';

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

/**
 * Comprobacion del entorno, una sola vez y sin lanzar.
 *
 * <p>No puede lanzar aqui: este modulo tambien se carga al construir, y ahi no
 * hay ni debe haber configuracion de ejecucion. Devuelve el motivo en vez de
 * tumbar el proceso, y quien lo consume decide que hacer con el.
 */
function errorDeEntorno(): string | null {
  try {
    readAppConfig(process.env);
    return null;
  } catch (fallo) {
    return fallo instanceof Error ? fallo.message : String(fallo);
  }
}

const entornoInvalido = errorDeEntorno();
let yaAvisado = false;

/**
 * Sin configuracion valida no se renderiza: se responde 500 diciendo que falta.
 *
 * <p>El arranque de mas abajo ya se cae por esto, pero **solo cuando este archivo
 * es el modulo principal**. Bajo `ng serve` no lo es: el CLI importa reqHandler y
 * ese bloque no se ejecuta nunca. La aplicacion arrancaba entonces con la
 * configuracion de relleno de readAppConfigForBootstrap, con apiBaseUrl vacia;
 * la primera peticion a la API lanzaba dentro del renderizado, la consulta no
 * llegaba a resolverse y la respuesta se quedaba colgada para siempre, sin una
 * sola linea en el registro.
 *
 * <p>Colgarse en silencio es la peor forma de fallar que hay: parece que compila,
 * el navegador gira y no hay nada que leer. La guarda existe para que el unico
 * entorno donde alguien programa no sea justo el que no valida nada.
 *
 * <p>Solo comprueba lo que impide renderizar, que es lo que exige readAppConfig.
 * NG_ALLOWED_HOSTS no entra: sin ella la pagina sale sin renderizar pero sale, y
 * el servidor de desarrollo del CLI funciona sin declararla. Exigirla aqui
 * romperia el caso que hoy va bien; el arranque real si la exige, que es donde
 * importa.
 */
app.use((_request, response, next) => {
  if (entornoInvalido === null) {
    next();
    return;
  }

  // Se avisa en la consola la primera vez: quien arranco el servidor mira ahi,
  // no el cuerpo de la respuesta.
  if (!yaAvisado) {
    yaAvisado = true;
    console.error(`[configuracion] ${entornoInvalido}`);
  }

  response
    .status(500)
    .type('text/plain; charset=utf-8')
    .send(
      `No se puede renderizar: la configuracion del servidor esta incompleta.\n\n${entornoInvalido}\n\n` +
        'En local suele ser que falta el archivo .env de la raiz del repositorio.\n' +
        'Se crea copiando .env.example y completando los valores (ver README.md).\n',
    );
});

/** El resto lo renderiza Angular. */
app.use((request, response, next) => {
  angularApp
    .handle(request)
    .then((rendered) => (rendered ? writeResponseToNodeResponse(rendered, response) : next()))
    .catch(next);
});

if (isMainModule(import.meta.url) || process.env['pm_id']) {
  // Arranque real: aqui se vuelve a validar, pero ahora si se lanza. La guarda de
  // mas arriba responde 500 a cada peticion, que es lo mejor que se puede hacer
  // cuando el proceso lo gobierna el CLI; cuando el proceso es nuestro, lo
  // correcto es no levantarlo. Es preferible no arrancar a descubrirlo con un
  // visitante dentro.
  const config = readAppConfig(process.env);
  assertRenderingEnvironment(process.env);

  // Lo que falta pero no impide servir. Se dice al arrancar: un despliegue a
  // medias no debe descubrirse mirando el pie de la pagina en produccion.
  for (const aviso of avisosDeConfiguracion(config)) {
    console.warn(`[configuracion] ${aviso}`);
  }

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
