import { RenderMode, type ServerRoute } from '@angular/ssr';

/**
 * Todo se renderiza en cada peticion, no se prerenderiza: el idioma, el tema y
 * la configuracion salen de las cabeceras y las cookies de quien pide la
 * pagina, y una version generada al construir seria la misma para todo el mundo.
 *
 * La ruta comodin devuelve 404 de verdad. Un "no existe" servido con 200 es un
 * soft 404: el buscador lo indexa como pagina buena y termina ofreciendo
 * direcciones rotas.
 */
export const serverRoutes: ServerRoute[] = [
  { path: '', renderMode: RenderMode.Server },
  { path: 'registro', renderMode: RenderMode.Server },
  { path: 'verificar-correo', renderMode: RenderMode.Server },
  {
    path: '**',
    renderMode: RenderMode.Server,
    status: 404,
  },
];
