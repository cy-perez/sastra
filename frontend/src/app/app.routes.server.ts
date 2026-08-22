import { RenderMode, type ServerRoute } from '@angular/ssr';

import { PAGINAS_DE_CONTENIDO, RUTAS_CONTENIDO } from './core/routes/content-routes';

/**
 * Todo se renderiza en cada peticion, no se prerenderiza: el idioma, el tema y
 * la configuracion salen de las cabeceras y las cookies de quien pide la
 * pagina, y una version generada al construir seria la misma para todo el mundo.
 *
 * La ruta comodin devuelve 404 de verdad. Un "no existe" servido con 200 es un
 * soft 404: el buscador lo indexa como pagina buena y termina ofreciendo
 * direcciones rotas.
 *
 * <p><strong>Toda ruta que exista tiene que estar en esta lista.</strong> La que
 * falte cae en el comodin y se sirve con estado 404 aunque la pagina se pinte
 * entera: el visitante no nota nada y el buscador la descarta. Es lo que le
 * pasaba a /ingresar.
 */
export const serverRoutes: ServerRoute[] = [
  { path: '', renderMode: RenderMode.Server },
  { path: 'registro', renderMode: RenderMode.Server },
  { path: 'ingresar', renderMode: RenderMode.Server },
  { path: 'verificar-correo', renderMode: RenderMode.Server },
  { path: 'mi-cuenta', renderMode: RenderMode.Server },
  // HU-002. Faltaba, y el sintoma es el que este archivo describe arriba: la pagina
  // se pintaba entera y se servia con 404. No se nota mirandola, solo midiendo el
  // estado, que es lo que hace ahora `rutas.spec.ts`.
  { path: 'verificacion-de-vendedor', renderMode: RenderMode.Server },
  { path: 'recuperar-contrasena', renderMode: RenderMode.Server },
  { path: 'restablecer-contrasena', renderMode: RenderMode.Server },
  { path: 'confirmar-correo-nuevo', renderMode: RenderMode.Server },
  // Las cuatro informativas de HU-005. Son las que mas dependen de esto: existen
  // para que alguien que duda las encuentre y las lea, asi que servirlas con 404
  // las dejaria fuera del buscador aunque se pintaran enteras.
  //
  // Derivadas de la constante y no escritas otra vez: el modulo de core existe
  // precisamente para que la direccion se declare en un solo sitio, y una lista
  // repetida a mano es la forma exacta en que /ingresar se quedo fuera.
  ...PAGINAS_DE_CONTENIDO.map((pagina): ServerRoute => ({
    path: RUTAS_CONTENIDO[pagina].slice(1),
    renderMode: RenderMode.Server,
  })),
  // Los legales importan aqui mas que ninguna: son las que una autoridad revisa
  // y las que un buscador tiene que poder indexar.
  { path: 'terminos', renderMode: RenderMode.Server },
  { path: 'tratamiento-de-datos', renderMode: RenderMode.Server },
  { path: 'politica-de-cookies', renderMode: RenderMode.Server },
  {
    path: '**',
    renderMode: RenderMode.Server,
    status: 404,
  },
];
