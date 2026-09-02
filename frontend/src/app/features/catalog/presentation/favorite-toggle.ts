import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  untracked,
} from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { FavoritesStore } from '../application/favorites.store';

/**
 * El control de favorito de la ficha. HU-011, criterios 1 a 10 y 17.
 *
 * <p><strong>Se ofrece también a quien no tiene sesión</strong> (criterio 7). Pulsarlo
 * lleva a entrar y, al volver, el favorito ya está guardado sin tener que volver a pulsar:
 * la intención queda anotada en el navegador y el destino viaja en la dirección del ingreso
 * (ADR-0029).
 *
 * <p><strong>No se ofrece sobre la publicación propia</strong> (criterio 5), y eso lo dice
 * el servidor: la sesión que guarda el navegador no lleva el identificador de la cuenta,
 * así que la pantalla no puede compararlo con el vendedor. Esconder el control no es la
 * regla —RN-072 se comprueba al marcar— sino evitar que alguien pulse para enterarse.
 *
 * <p><strong>El estado marcado no se comunica solo por color</strong> (criterio 17): el
 * icono cambia de contorno a relleno, el texto accesible cambia con él y
 * `aria-pressed` lo anuncia. Un lector de pantalla dice si está pulsado sin depender de
 * que alguien vea la diferencia.
 *
 * <p><strong>Va en tinta y nunca en bronce.</strong> El acento aparece una vez por pantalla
 * y en la ficha ya lo tiene la insignia de vendedor verificado.
 */
@Component({
  selector: 'sendik-favorite-toggle',
  standalone: true,
  imports: [TranslocoPipe],
  templateUrl: './favorite-toggle.html',
  styleUrl: './favorite-toggle.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FavoriteToggle {
  private readonly store = inject(FavoritesStore);
  private readonly router = inject(Router);

  readonly publicacion = input.required<string>();

  constructor() {
    // Se lo dice al almacen el mismo, no la pantalla que lo contiene. El
    // control recibe la publicación por entrada, así que ya la conoce; dejar que la ficha
    // llamara al almacén repartiría la misma responsabilidad en dos sitios y ataría el
    // control a que la pantalla se acordara. Así funciona donde se ponga.
    effect(() => {
      const id = this.publicacion();
      untracked(() => this.store.abrirFicha(id));
    });

    // La intención que quedó del ingreso (criterios 8, 9 y 10). Depende de que la sesión
    // esté resuelta a propósito: leerla mientras es «desconocida» la descartaría en cada
    // recarga, que es justo el caso que existe para cubrir. El almacén la consume una sola
    // vez y la borra siempre.
    effect(() => {
      const id = this.publicacion();
      const resuelta = this.store.sesionResuelta();

      if (!resuelta) {
        return;
      }
      untracked(() => this.store.retomarIntencion(id));
    });
  }

  protected readonly marcado = computed(() => this.store.control().marcado);

  protected readonly seOfrece = computed(() => this.store.control().seOfrece);

  protected readonly enCurso = computed(() => this.store.control().enCurso);

  protected readonly error = computed(() => this.store.errorDelControl());

  /**
   * El texto del botón, que es también su nombre accesible.
   *
   * <p>Cambia con el estado y no describe el estado sino la acción: quien lo lee sabe qué
   * pasa si lo pulsa. Que ahora mismo esté marcado lo dice `aria-pressed`.
   */
  protected readonly etiqueta = computed(() =>
    this.marcado() ? 'catalog.favorite.remove' : 'catalog.favorite.add',
  );

  protected pulsar(): void {
    if (this.store.alternar(this.publicacion()) === 'hay-que-entrar') {
      // La dirección a la que volver viaja en la URL; la intención, no (ADR-0029).
      void this.router.navigate(['/ingresar'], {
        queryParams: { redirectTo: `/producto/${this.publicacion()}` },
      });
    }
  }
}
