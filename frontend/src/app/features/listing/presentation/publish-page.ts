import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  Injector,
  InjectionToken,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { debounceTime } from 'rxjs';

import { APP_CONFIG } from '../../../core/config/app-config';
import { CategoriesStore } from '../application/categories.store';
import { ListingStore } from '../application/listing.store';
import {
  categoriaPorId,
  categoriasHoja,
  type Category,
  type Condition,
  type Listing,
  type Money,
  type Shipping,
} from '../../../shared/domain/listing';
import {
  admiteEdicion,
  COLORES,
  condicionesAdmitidas,
  editarDevuelveARevision,
  esTecnologia,
  puedeIntentarEnviar,
} from '../domain/publish-rules';
import type { DatosDelProducto } from '../infrastructure/listing.api';
import { ShotsField } from './shots-field';

/**
 * Lo que tarda en guardarse solo después de dejar de escribir.
 *
 * <p>Es un token y no una constante para que una prueba pueda ponerlo en cero. Con 1,5 s
 * fijos, el guardado de avance que pide el criterio 5 no se puede comprobar: los relojes
 * falsos de Vitest y el planificador sin zonas de Angular no se llevan bien, y esperar de
 * verdad son segundos por prueba.
 */
export const ESPERA_DE_GUARDADO = new InjectionToken<number>('espera de guardado', {
  providedIn: 'root',
  factory: () => 1500,
});

/**
 * Los cuatro del envío.
 *
 * <p>Están juntos porque el envío se guarda entero o no se guarda: su ruta propia no
 * admite media caja.
 */
const CAMPOS_DE_ENVIO: readonly string[] = ['weightGrams', 'lengthCm', 'widthCm', 'heightCm'];

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
  private readonly inyector = inject(Injector);
  private readonly esperaDeGuardado = inject(ESPERA_DE_GUARDADO);

  /** El plazo que se promete, por configuración y nunca quemado en el texto. */
  protected readonly diasDeRevision = inject(APP_CONFIG).business.listingReviewDays;

  protected readonly arbol = inject(CategoriesStore).categories;
  protected readonly consulta = this.store.current;
  protected readonly creacion = this.store.create;
  protected readonly guardado = this.store.update;
  protected readonly cambioDePrecio = this.store.changePrice;
  protected readonly cambioDeEnvio = this.store.changeShipping;
  protected readonly envio = this.store.submit;
  protected readonly subida = this.store.uploadShot;
  protected readonly borradoDeImagen = this.store.removeImage;
  protected readonly retirada = this.store.withdraw;
  protected readonly retomada = this.store.reopen;

  /**
   * El formulario, para buscar dentro de él y no en el documento entero.
   *
   * <p>Con {@code document.querySelector} el foco podía acabar en un elemento de otra
   * parte de la página que también estuviera marcado como inválido, y además obliga a
   * tocar {@code document}, que en el servidor no existe.
   */
  private readonly formularioRef = viewChild<ElementRef<HTMLFormElement>>('formularioProducto');

  /** Qué posición se está subiendo, para que la casilla lo diga. */
  protected readonly subiendo = signal<number | null>(null);

  private readonly idDeLaRuta = toSignal(this.ruta.paramMap, { requireSync: true });

  protected readonly id = computed<string | null>(() => this.idDeLaRuta().get('id'));

  protected readonly publicacion = computed<Listing | null>(() => this.consulta.data() ?? null);

  /**
   * La categoría elegida antes de que exista el borrador.
   *
   * <p>Va en un {@code FormGroup} y no en un {@code FormControl} suelto porque el
   * formulario usa {@code (ngSubmit)}, y esa salida la aporta {@code FormGroupDirective}.
   * Con un control suelto, Angular compilaba {@code ngSubmit} como un evento del DOM que
   * no dispara nadie: el botón, que es {@code type="submit"}, hacía un envío nativo y
   * recargaba la página sin crear nada.
   */
  protected readonly formularioDeInicio = this.formularios.nonNullable.group({
    categoryId: '',
  });

  /**
   * La categoría elegida, como señal.
   *
   * <p>Leer {@code formularioDeInicio.value} dentro de un {@code computed} no sirve: con
   * detección de cambios sin zonas, el estado de un {@code FormControl} no vuelve a
   * evaluar el cálculo cuando cambia. Es la misma razón por la que {@code TextField}
   * recibe el error ya decidido desde arriba.
   */
  private readonly categoriaDeInicio = toSignal(
    this.formularioDeInicio.controls.categoryId.valueChanges,
    { initialValue: '' },
  );

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

  /**
   * Si se tocó alguna medida desde el último guardado.
   *
   * <p>Las medidas no están en el {@code FormGroup} —cuáles hay depende de la
   * categoría—, así que su estado de «tocado» hay que llevarlo aparte. Importa porque una
   * medida es contenido: cambiarla sí devuelve a moderación.
   */
  private readonly medidasTocadas = signal(false);

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

  /** No hay categoría elegida, o ya se está creando. */
  protected readonly noSePuedeCrear = computed(
    () => this.categoriaDeInicio().length === 0 || this.creacion.isPending(),
  );

  /**
   * Lo que se anuncia a un lector de pantalla.
   *
   * <p>Sale por una región viva permanente y no por un {@code role="status"} que aparece
   * con el texto ya dentro: esa forma no se anuncia de manera fiable, porque la región
   * tiene que existir antes de que el contenido cambie.
   */
  protected readonly anuncio = computed<string | null>(() => {
    if (this.consulta.isPending() || this.arbol.isPending()) {
      return 'listing.a11y.loading';
    }
    if (this.subiendo() !== null) {
      return 'listing.a11y.uploading';
    }
    if (this.guardado.isPending()) {
      return 'listing.form.saving';
    }
    if (this.guardado.isSuccess()) {
      return 'listing.a11y.saved';
    }
    // Nada que anunciar. La region se queda vacia pero sigue en el DOM, que es lo que
    // la hace fiable.
    return null;
  });

  /** Los datos de la clave anunciada. Vacío para las que no llevan ninguno. */
  protected readonly anuncioDatos = computed<Record<string, number>>(() => {
    const posicion = this.subiendo();
    return posicion === null ? {} : ({ posicion: posicion + 1 } as Record<string, number>);
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
      .pipe(debounceTime(this.esperaDeGuardado), takeUntilDestroyed())
      .subscribe(() => this.guardarSiProcede());
  }

  /** Crea el borrador con la categoría elegida y lleva a su formulario. */
  protected async crear(): Promise<void> {
    const categoryId = this.formularioDeInicio.getRawValue().categoryId;
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
      this.envio.mutate(actual.id, { onError: () => this.enfocarElPrimeroQueFalta() });
    }
  }

  /**
   * Lleva el foco al primer campo que el servidor dijo que falta.
   *
   * <p>Sin esto, quien pulsa enviar y recibe el 422 se queda donde estaba: el mensaje
   * aparece al final de la página y hay que ir a buscarlo. Se hace tras pintar, porque
   * antes el atributo de invalidez todavía no está puesto.
   */
  private enfocarElPrimeroQueFalta(): void {
    afterNextRender(
      () => {
        const formulario = this.formularioRef()?.nativeElement;
        formulario?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus();
      },
      { injector: this.inyector },
    );
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
    this.medidasTocadas.set(true);
    this.guardarSiProcede();
  }

  protected claveDeError(fallo: unknown): string {
    return ListingStore.claveDeError(fallo);
  }

  /**
   * La clave con la que se nombra un campo que falta.
   *
   * <p>El servidor manda las medidas como {@code measurements.CHEST}, y componer
   * {@code listing.form.} con eso daba una clave que no existe: en pantalla salía el
   * nombre de la clave en crudo. Las medidas tienen su propio grupo de traducciones.
   */
  protected etiquetaDe(campo: string): string {
    const medida = campo.startsWith('measurements.') ? campo.slice('measurements.'.length) : null;
    return medida === null ? `listing.form.${campo}` : `listing.measurement.${medida}`;
  }

  protected falta(campo: string): boolean {
    return this.camposQueFaltan().includes(campo);
  }

  /**
   * Lo que describe a un campo: su ayuda y, si falta, el mensaje que lo nombra.
   *
   * <p>Sin la segunda parte, quien navega con lector se encuentra un campo marcado como
   * inválido y la explicación al final de la página, sin nada que las una.
   */
  protected describe(ayuda: string, campo: string): string {
    return this.falta(campo) ? `${ayuda} falta-${campo}` : ayuda;
  }

  /**
   * Guarda lo que lleva, por la ruta que corresponde a lo que se tocó.
   *
   * <p><strong>Esto es el criterio 28, y no es un detalle de eficiencia.</strong> Sobre
   * una publicación viva, editar lo que describe el producto la devuelve a moderación
   * (RN-062) y cambiar solo el precio o el envío no. Mandarlo todo por el {@code PATCH}
   * general hacía lo contrario de lo que la historia promete: tocar el precio de algo
   * publicado lo sacaba de circulación hasta que un moderador volviera a mirarlo.
   *
   * <p>Sobre un borrador da igual por dónde vaya —no hay moderación de por medio—, así
   * que ahí se manda todo junto y en una sola petición.
   *
   * <p>Cuando sí va por el {@code PATCH} general se manda el producto entero y no solo lo
   * que cambió: esa ruta reemplaza el producto, y mandar campos sueltos borraría los que
   * no viajen.
   */
  private guardarSiProcede(): void {
    const actual = this.publicacion();
    if (actual === null || !this.editable() || !this.hayCambios()) {
      return;
    }

    if (editarDevuelveARevision(actual.status) && this.soloPrecioOEnvio()) {
      this.guardarSinModeracion(actual.id);
      return;
    }
    this.guardado.mutate({ id: actual.id, datos: this.datosDelFormulario() });
  }

  /** Los nombres de los controles que se tocaron desde el último guardado. */
  private camposTocados(): readonly string[] {
    return Object.entries(this.formulario.controls)
      .filter(([, control]) => control.dirty)
      .map(([nombre]) => nombre);
  }

  private hayCambios(): boolean {
    return !this.formulario.pristine || this.medidasTocadas();
  }

  /**
   * Si lo tocado se puede guardar sin volver a moderación.
   *
   * <p>Una medida cuenta como contenido, así que basta con que se haya tocado una para
   * que la respuesta sea que no.
   */
  private soloPrecioOEnvio(): boolean {
    const tocados = this.camposTocados();

    return (
      !this.medidasTocadas() &&
      tocados.length > 0 &&
      tocados.every((campo) => campo === 'price' || CAMPOS_DE_ENVIO.includes(campo))
    );
  }

  /**
   * Manda el precio y el envío por sus rutas propias.
   *
   * <p>Pueden salir las dos peticiones: son dos cambios distintos y cada uno tiene su
   * ruta. Si el precio quedó vacío, o el envío a medias, no se manda: media caja no es
   * una caja, y esas dos rutas no admiten quitar el dato.
   */
  private guardarSinModeracion(id: string): void {
    const tocados = this.camposTocados();
    const precio = this.precioDelFormulario();
    const envio = this.envioDelFormulario();

    if (tocados.includes('price') && precio !== null) {
      this.cambioDePrecio.mutate({ id, precio });
    }
    if (tocados.some((campo) => CAMPOS_DE_ENVIO.includes(campo)) && envio !== null) {
      this.cambioDeEnvio.mutate({ id, envio });
    }
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

  private precioDelFormulario(): Money | null {
    const pesos = numero(this.formulario.getRawValue().price);
    return pesos === null ? null : { amount: pesos, currency: 'COP' };
  }

  private envioDelFormulario(): Shipping | null {
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
    this.medidasTocadas.set(false);
    this.formulario.markAsPristine();
  }
}

function vacioANulo(valor: string): string | null {
  return valor.trim().length === 0 ? null : valor.trim();
}

/**
 * Un número, venga como venga del control.
 *
 * <p><strong>No siempre es una cadena.</strong> En un {@code input type="number"} quien
 * escribe en el control es {@code NumberValueAccessor}, que pone un {@code number}; el
 * mismo control, volcado desde el servidor, trae la cadena que le pusimos. Asumir texto
 * aquí reventaba con «trim is not a function» en cuanto alguien tecleaba un precio.
 */
function numero(valor: string | number | null): number | null {
  if (valor === null || valor === '') {
    return null;
  }
  if (typeof valor === 'number') {
    return Number.isFinite(valor) ? valor : null;
  }
  if (valor.trim().length === 0) {
    return null;
  }
  const convertido = Number(valor);
  return Number.isFinite(convertido) ? convertido : null;
}
