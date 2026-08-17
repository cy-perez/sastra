import { EnvironmentProviders, InjectionToken, makeEnvironmentProviders } from '@angular/core';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';

/**
 * Se entrega un token con fabrica en vez de una instancia suelta a proposito:
 * en el servidor cada peticion crea su propio inyector y, por tanto, su propia
 * cache. Una instancia compartida a nivel de modulo serviria a un visitante los
 * datos consultados por otro.
 */
const QUERY_CLIENT = new InjectionToken<QueryClient>('sastra.query-client', {
  providedIn: 'root',
  factory: () =>
    new QueryClient({
      defaultOptions: {
        queries: {
          // El catalogo cambia constantemente: un minuto es suficiente para
          // ahorrar peticiones sin mostrar precios viejos.
          staleTime: 60_000,
          retry: 1,
          refetchOnWindowFocus: false,
        },
      },
    }),
});

export function provideQuery(): EnvironmentProviders {
  return makeEnvironmentProviders([provideTanStackQuery(QUERY_CLIENT)]);
}
