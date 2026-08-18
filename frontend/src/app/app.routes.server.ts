import { RenderMode, type ServerRoute } from '@angular/ssr';

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
  { path: 'recuperar-contrasena', renderMode: RenderMode.Server },
  { path: 'restablecer-contrasena', renderMode: RenderMode.Server },
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
