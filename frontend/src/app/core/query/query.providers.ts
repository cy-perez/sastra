import { isPlatformServer } from '@angular/common';
import {
  EnvironmentProviders,
  InjectionToken,
  makeEnvironmentProviders,
  makeStateKey,
  PendingTasks,
  PLATFORM_ID,
  provideEnvironmentInitializer,
  TransferState,
  inject,
} from '@angular/core';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { dehydrate, hydrate, type DehydratedState } from '@tanstack/query-core';

/**
 * Se entrega un token con fabrica en vez de una instancia suelta a proposito:
 * en el servidor cada peticion crea su propio inyector y, por tanto, su propia
 * cache. Una instancia compartida a nivel de modulo serviria a un visitante los
 * datos consultados por otro.
 */
const QUERY_CLIENT = new InjectionToken<QueryClient>('sendik.query-client', {
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

/** Lo que el servidor ya consulto, para que el navegador no lo vuelva a pedir. */
const ESTADO_DE_CONSULTAS = makeStateKey<DehydratedState>('sendik.consultas');

/**
 * Cuanto se espera, como maximo, a que las consultas de una pagina terminen.
 *
 * <p>Es un tope de seguridad, no un plazo esperado: si la API no responde, el servidor
 * entrega la pagina sin esos datos en vez de quedarse colgado. Una peticion de
 * renderizado que no termina es peor que una pagina incompleta, porque se lleva por
 * delante a quien esta esperando detras.
 */
const ESPERA_MAXIMA_MS = 5_000;

/**
 * Margen para que las consultas de la pagina se habiliten antes de darlas por terminadas.
 *
 * <p>Solo lo pagan las paginas que consultan algo, y en la practica no lo pagan: la
 * llamada a la API tarda mas que esto, asi que la espera la manda ella.
 */
const GRACIA_MS = 150;

/** Vueltas seguidas sin trabajo antes de dar la pagina por lista. Cubre las encadenadas. */
const VUELTAS_QUIETAS = 3;

/**
 * TanStack Query, y lo que hace falta para que sus datos salgan en el HTML del servidor.
 *
 * <p><strong>Sin esto, toda pantalla con datos remotos se sirve vacia.</strong> Angular
 * serializa el HTML en cuanto la aplicacion se queda quieta, y una consulta en vuelo no
 * la mantiene ocupada por si sola: el catalogo, la ficha y el perfil llegaban al buscador
 * con su esqueleto de carga y nada mas. No lo vio nadie antes porque hasta HU-009 ninguna
 * pantalla del proyecto renderizaba datos remotos en servidor —las informativas son
 * estaticas y las de cuenta exigen sesion—, y es justo lo que `frontend/CLAUDE.md` pide
 * para la ficha y el listado: de ese trafico vive el marketplace.
 *
 * <p>Se resuelve en dos mitades:
 *
 * <ol>
 *   <li><strong>El servidor espera.</strong> Se retiene una tarea pendiente de Angular
 *       desde el arranque y no se suelta hasta que ninguna consulta esta en vuelo. Angular
 *       no da por terminada la pagina mientras haya una tarea retenida.
 *   <li><strong>El navegador no repite.</strong> Lo consultado viaja en el estado
 *       transferido y se rehidrata al arrancar, asi que la pantalla se pinta con lo que ya
 *       venia y no parpadea ni gasta una segunda vuelta a la API.
 * </ol>
 *
 * <p>Va aqui y no en cada pantalla porque es una decision del proyecto y no de una
 * funcionalidad: cualquier pantalla que se escriba a partir de ahora hereda el
 * comportamiento correcto sin acordarse de nada.
 */
export function provideQuery(): EnvironmentProviders {
  return makeEnvironmentProviders([
    provideTanStackQuery(QUERY_CLIENT),
    provideEnvironmentInitializer(() => {
      const cliente = inject(QUERY_CLIENT);
      const estado = inject(TransferState);

      if (isPlatformServer(inject(PLATFORM_ID))) {
        esperarYVolcar(cliente, estado, inject(PendingTasks));
        return;
      }

      rehidratar(cliente, estado);
    }),
  ]);
}

/**
 * En el servidor: no terminar la pagina hasta que las consultas respondan, y dejar lo
 * consultado en el estado transferido.
 *
 * <p>La tarea se retiene **desde el arranque** y no cuando empieza la primera consulta.
 * Es la diferencia que hace que esto funcione: las consultas nacen al construirse los
 * componentes, y para entonces Angular ya podria haber decidido que no queda nada por
 * hacer. Reteniendola antes, la ventana no existe.
 *
 * <p>El volcado se hace **antes** de soltar la tarea. Angular serializa el estado
 * transferido junto con el HTML, y hacerlo despues llegaria tarde.
 */
function esperarYVolcar(cliente: QueryClient, estado: TransferState, tareas: PendingTasks): void {
  const soltar = tareas.add();

  void (async () => {
    try {
      await esperarAlReposo(cliente);
      estado.set(ESTADO_DE_CONSULTAS, dehydrate(cliente));
    } finally {
      // En un `finally`: si el volcado fallara, una tarea retenida para siempre dejaria
      // la peticion colgada, que es peor que servir la pagina sin datos.
      soltar();
    }
  })();
}

/**
 * Espera a que las consultas de la pagina terminen.
 *
 * <p><strong>Es una heuristica, y conviene saberlo.</strong> La libreria todavia no ofrece
 * soporte de renderizado en servidor de primera clase —sigue marcada como experimental— y
 * no hay forma de preguntarle «avisame cuando esta pagina no tenga nada mas que pedir».
 * Lo que hay aqui es lo mas cerca que se puede estar sin reescribir como cargan datos
 * todas las pantallas. Ver ADR-0025.
 *
 * <p>Tres cosas que parecen de mas y ninguna lo es:
 *
 * <ol>
 *   <li><strong>La primera vuelta.</strong> Al llegar aqui no hay ni una consulta
 *       registrada: los componentes se construyen despues. Sin ella, la espera terminaria
 *       antes de empezar.
 *   <li><strong>La gracia inicial.</strong> Una consulta nace deshabilitada mientras no se
 *       sabe que pedir —la ficha no conoce su identificador hasta que el enrutador
 *       resuelve— y se habilita en el efecto siguiente. Sin la gracia, se la encuentra
 *       apagada y se da por terminada. **Solo se paga en paginas que consultan algo**: si
 *       la cache esta vacia tras la primera vuelta, esta pagina no depende de datos y se
 *       sale de inmediato.
 *   <li><strong>La racha de quietud.</strong> Hay consultas encadenadas: la ficha pide el
 *       vendedor con el identificador que venia dentro de la publicacion. Entre que una
 *       termina y la siguiente arranca hay un instante sin trabajo, y soltar ahi dejaria
 *       la ficha sin el nombre de quien vende.
 * </ol>
 */
async function esperarAlReposo(cliente: QueryClient): Promise<void> {
  const limite = Date.now() + ESPERA_MAXIMA_MS;

  await siguienteVuelta();

  if (cliente.getQueryCache().getAll().length === 0) {
    return;
  }

  const finDeGracia = Date.now() + GRACIA_MS;
  let quietas = 0;

  while (Date.now() < limite) {
    if (quedaTrabajo(cliente)) {
      quietas = 0;
    } else if (Date.now() >= finDeGracia && ++quietas >= VUELTAS_QUIETAS) {
      return;
    }

    await siguienteVuelta();
  }
}

/**
 * Alguna consulta habilitada que todavia no tiene respuesta.
 *
 * <p>Las deshabilitadas no cuentan y sin esa salvedad esto esperaria hasta el tope en cada
 * pagina: una consulta con `enabled: false` se queda en `pending` para siempre, que es
 * exactamente lo que significa estar apagada.
 */
function quedaTrabajo(cliente: QueryClient): boolean {
  return cliente
    .getQueryCache()
    .getAll()
    .some((consulta) => !consulta.isDisabled() && consulta.state.status === 'pending');
}

function siguienteVuelta(): Promise<void> {
  return new Promise((listo) => setTimeout(listo, 0));
}

/**
 * En el navegador: partir de lo que el servidor ya consulto.
 *
 * <p>Sin esto, la pagina se pinta con los datos del servidor y acto seguido los vuelve a
 * pedir, con su parpadeo y su peticion de mas. Con esto, la cache arranca con lo que
 * venia en el HTML y la primera pantalla no llama a la API.
 */
function rehidratar(cliente: QueryClient, estado: TransferState): void {
  const volcado = estado.get(ESTADO_DE_CONSULTAS, null);

  if (volcado !== null) {
    hydrate(cliente, volcado);
  }
}
