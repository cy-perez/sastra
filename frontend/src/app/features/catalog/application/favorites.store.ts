import { computed, inject, Injectable, signal } from '@angular/core';
import {
  injectInfiniteQuery,
  injectMutation,
  injectQuery,
  QueryClient,
} from '@tanstack/angular-query-experimental';

import { SessionStore } from '../../../core/session/session.store';
import type { CatalogPage, PublicListing } from '../domain/public-listing';
import { FavoriteIntent } from '../infrastructure/favorite-intent';
import { FavoritesApi, type FavoriteState } from '../infrastructure/favorites.api';
import { queryKeys } from './query-keys';

/** Lo que la pantalla necesita saber del control, sin ver TanStack. */
export interface EstadoDelControl {
  readonly marcado: boolean;
  readonly seOfrece: boolean;
  readonly enCurso: boolean;
}

/**
 * El estado de los favoritos. HU-011.
 *
 * <p>Envuelve TanStack Query para que los componentes no vean la librería
 * (frontend/CLAUDE.md).
 *
 * <p><strong>Nada de aquí sale en el HTML del servidor.</strong> Es la propiedad delicada
 * de esta historia: `GET /listings/{id}` responde hoy lo mismo para cualquiera y se
 * renderiza en el servidor, y añadirle «esto es favorito tuyo» la volvería distinta por
 * persona. El estado se pide **aparte y después de hidratar**, cuando la sesión ya está
 * resuelta, y la ficha se queda como está. Que la consulta esté deshabilitada mientras la
 * sesión no sea `abierta` es lo que lo garantiza: en el servidor no lo es nunca.
 *
 * <p>Ninguna consulta reintenta. Un 404 o un 403 son respuestas normales aquí y
 * reintentarlas tres veces solo retrasa lo que la pantalla ya sabe decir.
 */
@Injectable({ providedIn: 'root' })
export class FavoritesStore {
  private readonly api = inject(FavoritesApi);
  private readonly sesion = inject(SessionStore);
  private readonly intencion = inject(FavoriteIntent);
  private readonly consultas = inject(QueryClient);

  /** Cuál publicación está abierta en la ficha. La fija la propia ficha al resolver la ruta. */
  private readonly ficha = signal<string | null>(null);

  /** Si hay alguien dentro. Falso también mientras la sesión es `desconocida`. */
  readonly haySesion = computed(() => this.sesion.status() === 'abierta');

  /** Si ya se sabe la respuesta, sea cual sea. Es lo que espera la intención pendiente. */
  readonly sesionResuelta = computed(() => this.sesion.status() !== 'desconocida');

  /**
   * El estado del control para la publicación abierta.
   *
   * <p>Solo sale con sesión: sin ella la ruta responde 401 y pedirla sería fabricar un
   * error en cada visita de cualquiera que no tenga cuenta, que son casi todas.
   */
  readonly estado = injectQuery(() => ({
    queryKey: queryKeys.favorite(this.ficha() ?? 'ninguna'),
    queryFn: () => this.api.estado(this.ficha() ?? ''),
    enabled: this.ficha() !== null && this.haySesion(),
    // Sin frescura, al contrario que el catalogo. El minuto de `provideQuery` esta pensado
    // para datos publicos que cambian solos; esto es de una persona y cambia porque ella lo
    // cambia, a veces en otra pestana. Y es dato privado: cuanto menos viva en la cache,
    // mejor (docs/operacion/datos-personales.md).
    staleTime: 0,
    retry: false,
  }));

  /**
   * Lo que la ficha pinta.
   *
   * <p><strong>Sin sesión el control se ofrece igual</strong> (criterio 7), sin marcar:
   * pulsarlo lleva a entrar. Con sesión, se ofrece salvo que el servidor diga que no
   * —la publicación es suya, o ya no está publicada— y hasta que responda se ofrece
   * también, porque el estado neutro es el que se sirvió desde el servidor y cambiarlo dos
   * veces parpadea.
   */
  readonly control = computed<EstadoDelControl>(() => {
    const respuesta: FavoriteState | undefined = this.estado.data();

    if (!this.haySesion()) {
      return { marcado: false, seOfrece: true, enCurso: false };
    }

    return {
      marcado: this.optimista() ?? respuesta?.favorite ?? false,
      seOfrece: respuesta?.eligible ?? true,
      enCurso: this.marcar.isPending() || this.quitar.isPending(),
    };
  });

  /**
   * Lo que el control enseña mientras el servidor confirma.
   *
   * <p>El control puede adelantarse: pulsar y esperar medio segundo a que se pinte se
   * siente roto. Nulo significa «lo que diga el servidor». Si la petición falla se vuelve
   * a poner en nulo, y con eso el control regresa a su estado anterior en vez de quedarse
   * mintiendo.
   */
  private readonly optimista = signal<boolean | null>(null);

  readonly marcar = injectMutation(() => ({
    mutationFn: (listingId: string) => this.api.marcar(listingId),
    onMutate: () => this.optimista.set(true),
    onError: () => this.optimista.set(null),
    onSuccess: () => this.refrescar(),
  }));

  readonly quitar = injectMutation(() => ({
    mutationFn: (listingId: string) => this.api.quitar(listingId),
    onMutate: () => this.optimista.set(false),
    onError: () => this.optimista.set(null),
    onSuccess: () => this.refrescar(),
  }));

  /**
   * Qué decirle a quien acabó con un error.
   *
   * <p>Los dos códigos tienen texto propio porque no son fallos genéricos: uno dice que la
   * publicación es suya (criterios 5 y 10) y el otro que ya no está disponible (criterio
   * 6). Con un mensaje único, quien vuelve del ingreso y no ve su favorito no sabría por
   * qué.
   */
  readonly errorDelControl = computed<string | null>(() => {
    const fallo = (this.marcar.error() ?? this.quitar.error()) as { status?: number } | null;

    if (fallo == null) {
      return null;
    }
    if (fallo.status === 403) {
      return 'catalog.favorite.errors.own';
    }
    return fallo.status === 404
      ? 'catalog.favorite.errors.unavailable'
      : 'catalog.favorite.errors.failed';
  });

  // --- La lista propia -----------------------------------------------------

  /**
   * Si la pantalla de la lista está abierta.
   *
   * <p><strong>Sin esto, la lista se pedía al abrir cualquier ficha.</strong> Este almacén
   * es de raíz y lo inyecta también el control de favorito, así que en cuanto había sesión
   * la consulta se habilitaba y `GET /users/me/favorites` salía en cada producto que
   * alguien mirara. Es el mismo criterio que `ficha`: una consulta no se habilita porque
   * haya sesión, sino porque alguien está mirando lo que devuelve.
   */
  private readonly mirandoLaLista = signal(false);

  /**
   * La lista es una consulta infinita, igual que el catálogo: el contrato la pagina por
   * cursor, así que no hay número de página que pedir sino un «por dónde seguir».
   */
  readonly listado = injectInfiniteQuery(() => ({
    queryKey: queryKeys.favorites,
    queryFn: ({ pageParam }: { pageParam: string | null }) => this.api.lista(pageParam),
    enabled: this.haySesion() && this.mirandoLaLista(),
    // Sin frescura, por lo mismo que el estado del control.
    staleTime: 0,
    initialPageParam: null as string | null,
    getNextPageParam: (ultimo: CatalogPage) => ultimo.nextCursor,
    retry: false,
  }));

  readonly publicaciones = computed<readonly PublicListing[]>(
    () => this.listado.data()?.pages.flatMap((tramo) => tramo.items) ?? [],
  );

  readonly hayMas = computed(() => this.listado.hasNextPage());

  readonly trayendoMas = computed(() => this.listado.isFetchingNextPage());

  /** La pantalla de la lista dice cuándo se está mirando, y cuándo se dejó de mirar. */
  abrirLista(abierta: boolean): void {
    this.mirandoLaLista.set(abierta);
  }

  siguienteTramo(): void {
    if (this.listado.hasNextPage() && !this.listado.isFetchingNextPage()) {
      void this.listado.fetchNextPage();
    }
  }

  reintentar(): void {
    void this.listado.refetch();
  }

  // --- Lo que hace la ficha -------------------------------------------------

  /** La ficha fija cuál publicación se está mirando al resolver la ruta. */
  abrirFicha(id: string | null): void {
    this.ficha.set(id);
    this.optimista.set(null);
    this.marcar.reset();
    this.quitar.reset();
  }

  /**
   * Pulsar el control. Criterios 2, 3 y 8.
   *
   * <p><strong>Sin sesión no llama a la API</strong>: anota la intención y devuelve a dónde
   * hay que ir. Quien decide navegar es la pantalla, igual que en el ingreso, porque el
   * almacén no sabe de rutas.
   *
   * <p>El doble pulsado no manda dos peticiones: mientras una está en curso, esto no hace
   * nada. Es el caso borde de la historia, y se resuelve aquí y no en la plantilla para
   * que valga también si el control se lleva algún día a otra pantalla.
   */
  alternar(listingId: string): 'hecho' | 'hay-que-entrar' {
    if (!this.haySesion()) {
      this.intencion.recordar(listingId);
      return 'hay-que-entrar';
    }

    if (this.marcar.isPending() || this.quitar.isPending()) {
      return 'hecho';
    }

    if (this.control().marcado) {
      this.quitar.mutate(listingId);
    } else {
      this.marcar.mutate(listingId);
    }
    return 'hecho';
  }

  /**
   * Retoma la intención que quedó del ingreso, una sola vez. Criterios 8, 9 y 10.
   *
   * <p>Se llama cuando la sesión ya está resuelta, no antes: leerla mientras es
   * `desconocida` la descartaría en cada recarga, que es justo el caso que la intención
   * existe para cubrir.
   *
   * <p>Si resolvió anónima —volver atrás, cerrar el ingreso— la intención se descarta y no
   * se guarda nada (criterio 9). Si resolvió abierta se consume y se marca; que la
   * publicación resulte ser suya lo rechaza el servidor con RN-072, y el mensaje del
   * criterio 10 sale del error como cualquier otro. Pasar por el ingreso no salta ninguna
   * regla.
   */
  retomarIntencion(listingId: string): void {
    if (!this.sesionResuelta()) {
      return;
    }

    if (!this.haySesion()) {
      this.intencion.descartar();
      return;
    }

    if (this.intencion.consumir(listingId)) {
      this.marcar.mutate(listingId);
    }
  }

  /**
   * Tras marcar o quitar, la lista y el estado dejan de ser ciertos.
   *
   * <p>Se invalida la lista entera y no se toca a mano: quitar un favorito lo saca de un
   * tramo y corre los demás, y remendar eso en memoria es reimplementar la paginación por
   * cursor en el cliente.
   *
   * <p>La clave `favorites` es prefijo de `favorite(id)`, así que esta única invalidación
   * alcanza también al estado del control de cada ficha. Es deliberado y por eso las dos
   * claves comparten raíz: después de marcar, las dos cosas dejaron de ser ciertas a la
   * vez.
   */
  private refrescar(): void {
    // El valor optimista se suelta **cuando la respuesta fresca ya esta**, no al pedirla.
    // Soltarlo antes hacia esto: se quitaba un favorito, el control volvia a pintarse
    // marcado durante el viaje de vuelta y se desmarcaba otra vez al llegar. Un parpadeo
    // que dice justo lo contrario de lo que acaba de pasar.
    void this.consultas
      .invalidateQueries({ queryKey: queryKeys.favorites })
      .then(() => this.optimista.set(null));
  }
}
