import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { debounceTime } from 'rxjs';

import { APP_CONFIG } from '../../../core/config/app-config';
import { CategoriesStore } from '../application/categories.store';
import { ListingStore } from '../application/listing.store';
import {
  admiteEdicion,
  categoriaPorId,
  categoriasHoja,
  COLORES,
  condicionesAdmitidas,
  editarDevuelveARevision,
  esTecnologia,
  puedeIntentarEnviar,
  type Category,
  type Condition,
  type Listing,
} from '../domain/listing';
import type { DatosDelProducto } from '../infrastructure/listing.api';
import { ShotsField } from './shots-field';

/** Lo que tarda en guardarse solo después de dejar de escribir. */
const ESPERA_DE_GUARDADO = 1500;

/**
 * El formulario de publicar. HU-007.
 *
 * <p>Una sola página con secciones y guardado automático, y no un asistente por pasos: el
 * criterio 5 dice que se guarda a medias y que salir y volver retoma donde iba, así que lo
 * que hace falta es que todo esté a la vista y que nada se pierda, no una secuencia.
 *
 * <p><strong>El borrador se crea antes de poder subir fotos.</strong> Una toma se sube
 * contra una publicación que ya existe, así que `/publicar` solo pide la categoría, crea
 * el borrador y lleva a `/publicar/:id`. La categoría primero y no por comodidad: de ella
 * dependen las condiciones admisibles (RN-064), los sistemas de talla y qué medidas se
 * piden.
 *
 * <p><strong>Aquí no se decide si la publicación está completa.</strong> Eso lo comprueba
 * el servidor con la categoría delante y responde 422 con la lista de campos que faltan;
 * la pantalla los marca. Repetir esa comprobación en el cliente daría dos respuestas a la
 * misma pregunta.
 */
@Component({
  selector: 'sendik-publish-page',
  imports: [ReactiveFormsModule, TranslocoPipe, ShotsField],
  templateUrl: './publish-page.html',
  styleUrl: './publish-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PublishPage {
  private readonly store = inject(ListingStore);
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly formularios = inject(FormBuilder);

  /** El plazo que se promete, por configuración y nunca quemado en el texto. */
  protected readonly diasDeRevision = inject(APP_CONFIG).business.listingReviewDays;

  protected readonly arbol = inject(CategoriesStore).categories;
  protected readonly consulta = this.store.current;
  protected readonly creacion = this.store.create;
  protected readonly guardado = this.store.update;
  protected readonly envio = this.store.submit;
  protected readonly subida = this.store.uploadShot;
  protected readonly borradoDeImagen = this.store.removeImage;
  protected readonly retirada = this.store.withdraw;
  protected readonly retomada = this.store.reopen;

  /** Qué posición se está subiendo, para que la casilla lo diga. */
  protected readonly subiendo = signal<number | null>(null);

  private readonly idDeLaRuta = toSignal(this.ruta.paramMap, { requireSync: true });

  protected readonly id = computed<string | null>(() => this.idDeLaRuta().get('id'));

  protected readonly publicacion = computed<Listing | null>(() => this.consulta.data() ?? null);

  /** La categoría elegida antes de que exista el borrador. */
  protected readonly categoriaNueva = new FormControl<string>('', { nonNullable: true });

  protected readonly formulario = this.formularios.nonNullable.group({
    categoryId: '',
    title: '',
    description: '',
    brand: '',
    condition: '',
    sizeSystem: '',
    sizeValue: '',
    color: '',
    price: '',
    weightGrams: '',
    lengthCm: '',
    widthCm: '',
    heightCm: '',
    isSealed: false,
    warrantyMonths: '',
  });

  /** Las medidas se arman aparte: cuáles hay depende de la categoría. */
  protected readonly medidas = signal<Readonly<Record<string, string>>>({});

  protected readonly hojas = computed<readonly Category[]>(() =>
    categoriasHoja(this.arbol.data() ?? []),
  );

  protected readonly categoria = computed<Category | null>(() => {
    const elegida = this.publicacion()?.product.categoryId ?? null;
    return elegida === null ? null : categoriaPorId(this.arbol.data() ?? [], elegida);
  });

  protected readonly condiciones = computed<readonly Condition[]>(() =>
    condicionesAdmitidas(this.categoria()),
  );

  protected readonly esTecnologia = computed(() => esTecnologia(this.categoria()));

  protected readonly medidasPedidas = computed<readonly string[]>(
    () => this.categoria()?.requiredMeasurements ?? [],
  );

  protected readonly sistemasDeTalla = computed<readonly string[]>(
    () => this.categoria()?.sizeSystems ?? [],
  );

  protected readonly colores = COLORES;

  protected readonly editable = computed(() => {
    const actual = this.publicacion();
    return actual !== null && admiteEdicion(actual.status);
  });

  protected readonly volveraARevision = computed(() => {
    const actual = this.publicacion();
    return actual !== null && editarDevuelveARevision(actual.status);
  });

  protected readonly puedeEnviar = computed(() => {
    const actual = this.publicacion();
    return actual !== null && puedeIntentarEnviar(actual);
  });

  /** Los campos que el servidor dijo que faltan, para marcarlos uno a uno. */
  protected readonly camposQueFaltan = computed<readonly string[]>(() =>
    ListingStore.camposQueFaltan(this.envio.error()),
  );

  constructor() {
    // Sincroniza el formulario con lo que llega del servidor. Es un efecto porque
    // sincroniza con algo externo al marco —el estado de un FormGroup—, que es el
    // único uso que frontend/CLAUDE.md admite.
    effect(() => {
      const actual = this.publicacion();
      if (actual !== null) {
        this.volcarEnElFormulario(actual);
      }
    });

    effect(() => this.store.abrir(this.id()));

    // Guardado automático: el criterio 5 promete que nada se pierde al salir. Se espera
    // a que la persona deje de escribir para no mandar una petición por tecla.
    this.formulario.valueChanges
      .pipe(debounceTime(ESPERA_DE_GUARDADO), takeUntilDestroyed())
      .subscribe(() => this.guardarSiProcede());
  }

  /** Crea el borrador con la categoría elegida y lleva a su formulario. */
  protected async crear(): Promise<void> {
    const categoryId = this.categoriaNueva.value;
    if (categoryId.length === 0) {
      return;
    }

    const creada = await this.creacion.mutateAsync({ categoryId });
    await this.router.navigate(['/publicar', creada.id]);
  }

  protected alSubir(toma: { posicion: number; imagen: File }): void {
    const actual = this.publicacion();
    if (actual === null) {
      return;
    }

    this.subiendo.set(toma.posicion);
    this.subida.mutate(
      { id: actual.id, posicion: toma.posicion, imagen: toma.imagen },
      { onSettled: () => this.subiendo.set(null) },
    );
  }

  protected alQuitar(imagenId: string): void {
    const actual = this.publicacion();
    if (actual !== null) {
      this.borradoDeImagen.mutate({ id: actual.id, imagenId });
    }
  }

  protected enviarARevision(): void {
    const actual = this.publicacion();
    if (actual !== null) {
      this.envio.mutate(actual.id);
    }
  }

  protected retirar(): void {
    const actual = this.publicacion();
    if (actual !== null) {
      this.retirada.mutate(actual.id);
    }
  }

  protected retomar(): void {
    const actual = this.publicacion();
    if (actual !== null) {
      this.retomada.mutate(actual.id);
    }
  }

  protected medidaDe(clase: string): string {
    return this.medidas()[clase] ?? '';
  }

  protected alEscribirMedida(clase: string, evento: Event): void {
    const valor = (evento.target as HTMLInputElement).value;
    this.medidas.update((actuales) => ({ ...actuales, [clase]: valor }));
    this.guardarSiProcede();
  }

  protected claveDeError(fallo: unknown): string {
    return ListingStore.claveDeError(fallo);
  }

  protected falta(campo: string): boolean {
    return this.camposQueFaltan().includes(campo);
  }

  /**
   * Guarda lo que lleva, si hay borrador y se puede editar.
   *
   * <p>Se manda todo el producto y no solo lo que cambió: la API es un `PATCH` del
   * producto entero, y mandar campos sueltos borraría los que no viajen.
   */
  private guardarSiProcede(): void {
    const actual = this.publicacion();
    if (actual === null || !this.editable() || this.formulario.pristine) {
      return;
    }
    this.guardado.mutate({ id: actual.id, datos: this.datosDelFormulario() });
  }

  private datosDelFormulario(): DatosDelProducto {
    const valores = this.formulario.getRawValue();

    return {
      categoryId: valores.categoryId,
      title: vacioANulo(valores.title),
      description: vacioANulo(valores.description),
      brand: vacioANulo(valores.brand),
      condition: (vacioANulo(valores.condition) as Condition | null) ?? null,
      size:
        valores.sizeSystem.length > 0 && valores.sizeValue.length > 0
          ? {
              system: valores.sizeSystem as Category['sizeSystems'][number],
              value: valores.sizeValue,
            }
          : null,
      measurements: this.medidasNumericas(),
      color: (vacioANulo(valores.color) as never) ?? null,
      price:
        numero(valores.price) === null ? null : { amount: numero(valores.price)!, currency: 'COP' },
      shipping: this.envioDelFormulario(),
      isSealed: this.esTecnologia() ? valores.isSealed : null,
      warrantyMonths: this.esTecnologia() ? numero(valores.warrantyMonths) : null,
    };
  }

  private envioDelFormulario(): DatosDelProducto['shipping'] {
    const valores = this.formulario.getRawValue();
    const peso = numero(valores.weightGrams);
    const largo = numero(valores.lengthCm);
    const ancho = numero(valores.widthCm);
    const alto = numero(valores.heightCm);

    // Media caja no es una caja: si falta uno de los cuatro no se manda ninguno, que es
    // lo que el servidor admite en un borrador a medias.
    if (peso === null || largo === null || ancho === null || alto === null) {
      return null;
    }
    return { weightGrams: peso, lengthCm: largo, widthCm: ancho, heightCm: alto };
  }

  private medidasNumericas(): Readonly<Record<string, number>> {
    const convertidas: Record<string, number> = {};

    for (const [clase, valor] of Object.entries(this.medidas())) {
      const medida = numero(valor);
      if (medida !== null) {
        convertidas[clase] = medida;
      }
    }
    return convertidas;
  }

  private volcarEnElFormulario(publicacion: Listing): void {
    const producto = publicacion.product;

    this.formulario.patchValue(
      {
        categoryId: producto.categoryId,
        title: producto.title ?? '',
        description: producto.description ?? '',
        brand: producto.brand ?? '',
        condition: producto.condition ?? '',
        sizeSystem: producto.size?.system ?? '',
        sizeValue: producto.size?.value ?? '',
        color: producto.color ?? '',
        price: producto.price === null ? '' : String(producto.price.amount),
        weightGrams: producto.shipping === null ? '' : String(producto.shipping.weightGrams),
        lengthCm: producto.shipping === null ? '' : String(producto.shipping.lengthCm),
        widthCm: producto.shipping === null ? '' : String(producto.shipping.widthCm),
        heightCm: producto.shipping === null ? '' : String(producto.shipping.heightCm),
        isSealed: producto.isSealed ?? false,
        warrantyMonths: producto.warrantyMonths === null ? '' : String(producto.warrantyMonths),
      },
      // Sin emitir: volcar lo que acaba de llegar del servidor no es un cambio de la
      // persona, y emitirlo dispararía un guardado de lo mismo, en bucle.
      { emitEvent: false },
    );

    const medidas: Record<string, string> = {};
    for (const [clase, valor] of Object.entries(producto.measurements)) {
      medidas[clase] = String(valor);
    }
    this.medidas.set(medidas);
    this.formulario.markAsPristine();
  }
}

function vacioANulo(valor: string): string | null {
  return valor.trim().length === 0 ? null : valor.trim();
}

function numero(valor: string): number | null {
  if (valor.trim().length === 0) {
    return null;
  }
  const convertido = Number(valor);
  return Number.isFinite(convertido) ? convertido : null;
}
