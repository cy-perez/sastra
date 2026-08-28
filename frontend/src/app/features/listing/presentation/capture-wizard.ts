import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  signal,
  viewChild,
  type ElementRef,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
import { TranslocoPipe } from '@jsverse/transloco';

import { TOMAS_DE_LA_SECUENCIA, type Listing } from '../../../shared/domain/listing';
import { estaNivelado } from '../../../shared/domain/tilt';
import { CameraService } from '../../../shared/infrastructure/camera.service';
import { OrientationService } from '../../../shared/infrastructure/orientation.service';
import { ImagenNoNormalizable } from '../../../shared/infrastructure/photo-normalizer';
import { ListingStore } from '../application/listing.store';
import { admiteAsistente, pasosDeCaptura, primerPasoPendiente } from '../domain/capture-steps';
import { CaptureDraftStore } from '../infrastructure/capture-draft.store';

/** En qué punto está el permiso de sensores. Solo iOS llega a `pendiente`. */
type EstadoDelPermiso = 'pendiente' | 'concedido' | 'negado';

/**
 * El asistente de captura de las ocho tomas. HU-003, criterios 1 a 10.
 *
 * <p>Pantalla propia y no un diálogo sobre el formulario: la cámara ocupa el alto entero en
 * un teléfono, el botón de atrás tiene que cerrarla, y el paso en curso sobrevive a que la
 * pantalla rote, que es uno de los casos borde de la historia.
 *
 * <p>**Nada de lo que hay aquí decide.** Si una lectura del sensor basta para disparar lo
 * dice `shared/domain/tilt.ts`; qué paso toca, `domain/capture-steps.ts`; si la foto sirve,
 * el normalizador y, por encima de él, el servidor sobre los bytes que recibe (ADR-0018).
 * Este componente abre la cámara, pinta y encadena.
 */
@Component({
  selector: 'sendik-capture-wizard',
  imports: [TranslocoPipe],
  templateUrl: './capture-wizard.html',
  styleUrl: './capture-wizard.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CaptureWizard {
  private readonly camara = inject(CameraService);
  private readonly sensores = inject(OrientationService);
  private readonly borrador = inject(CaptureDraftStore);
  private readonly publicaciones = inject(ListingStore);
  private readonly router = inject(Router);

  private readonly id = toSignal(
    inject(ActivatedRoute).paramMap.pipe(map((parametros) => parametros.get('id'))),
    { initialValue: null },
  );

  protected readonly publicacion = computed(() => this.publicaciones.current.data() ?? null);

  /** El paso en curso. Lo fija el primer hueco al abrir y avanza al subir. */
  private readonly paso = signal<number | null>(null);

  private readonly flujo = signal<MediaStream | null>(null);
  private readonly inclinacion = signal<{ beta: number; gamma: number } | null>(null);

  /** La baja del sensor, guardada para poder soltarlo al salir. */
  private bajaDelSensor: (() => void) | null = null;

  protected readonly abierta = signal(false);
  protected readonly ocupada = signal(false);
  protected readonly error = signal<string | null>(null);
  /**
   * En qué punto está el permiso de sensores.
   *
   * <p>Nace en `pendiente` solo donde de verdad hay algo que pedir, que hoy es iOS. En el
   * resto nace concedido: pintar «vamos a pedirte permiso» en un navegador que no lo pide
   * anunciaría un diálogo que nunca aparece.
   */
  protected readonly permiso = signal<EstadoDelPermiso>(
    this.sensores.necesitaPermiso() ? 'pendiente' : 'concedido',
  );

  private readonly video = viewChild<ElementRef<HTMLVideoElement>>('video');

  protected readonly total = TOMAS_DE_LA_SECUENCIA;
  protected readonly soportada = computed(() => this.camara.soportada());

  protected readonly pasos = computed(() => {
    const actual = this.publicacion();
    return actual === null ? [] : pasosDeCaptura(actual);
  });

  protected readonly hechas = computed(() => this.pasos().filter((paso) => paso.hecha).length);

  protected readonly pasoActual = computed(() => {
    const posicion = this.paso();
    return posicion === null ? null : (this.pasos()[posicion] ?? null);
  });

  /**
   * Si el obturador está habilitado. Criterio 3.
   *
   * <p>Sin lectura del sensor responde que sí, y eso es el criterio 4: el asistente sigue
   * sin nivel y **nunca se bloquea la publicación** por un permiso denegado.
   */
  protected readonly nivelado = computed(() => {
    const lectura = this.inclinacion();
    return estaNivelado(lectura?.beta ?? null, lectura?.gamma ?? null);
  });

  /** Se muestra el nivel solo cuando hay algo que mostrar. */
  protected readonly hayNivel = computed(() => this.inclinacion() !== null);

  protected readonly avance = computed(() => this.publicaciones.uploadProgress());

  protected readonly porcentaje = computed(() => {
    const fraccion = this.avance()?.fraccion;
    return fraccion === null || fraccion === undefined ? null : Math.round(fraccion * 100);
  });

  constructor() {
    // La página fija cuál publicación se está viendo; el store pide la que toque.
    effect(() => this.publicaciones.abrir(this.id()));

    /*
     * Coloca el asistente en el primer hueco, una sola vez por publicación.
     *
     * Se hace cuando la publicacion llega y no en el constructor, porque al construir
     * todavia no hay datos. La condicion de `paso() === null` es lo que impide que vuelva
     * a saltar al principio cada vez que el store refresca tras subir una toma: sin ella,
     * subir la tercera devolveria el asistente a la cuarta y luego otra vez a la cuarta,
     * y la persona nunca llegaria al final.
     */
    effect(() => {
      const actual = this.publicacion();
      if (actual !== null && this.paso() === null) {
        this.paso.set(primerPasoPendiente(actual));
      }
    });

    /*
     * Engancha el flujo al elemento cuando el elemento existe, que no es cuando se
     * concede la camara. Es el mismo fallo que HU-002 dejo documentado en capture-field:
     * el visor vive dentro de un `@if`, asi que al volver de `getUserMedia` todavia no
     * esta en el documento y asignar ahi mismo asignaba sobre `undefined`.
     */
    effect(() => {
      const elemento = this.video()?.nativeElement;
      const flujo = this.flujo();

      if (elemento === undefined || flujo === null || elemento.srcObject === flujo) {
        return;
      }
      elemento.srcObject = flujo;
    });

    inject(DestroyRef).onDestroy(() => this.apagar());
  }

  /**
   * Arranca: pide el permiso de sensores si hace falta y abre la cámara.
   *
   * <p>Va detrás de un botón porque **iOS descarta la solicitud de sensores que no venga de
   * un gesto**, y lo hace en silencio. Pedirlo al entrar en la pantalla no fallaría con un
   * error: simplemente no aparecería el diálogo, y el nivel no funcionaría nunca sin que
   * nada lo explicara.
   */
  protected async empezar(): Promise<void> {
    this.error.set(null);

    if (!this.soportada()) {
      this.error.set('listing.capture.entry.unsupported');
      return;
    }

    this.ocupada.set(true);
    try {
      await this.activarNivel();

      this.flujo.set(await this.camara.abrir(false));
      this.abierta.set(true);
    } catch {
      // Denegar la cámara es una decisión de la persona, no un fallo del sistema. Se
      // ofrece la galería, que es lo que el criterio 8 pide para este caso.
      this.error.set('listing.capture.entry.unsupported');
      this.apagar();
    } finally {
      this.ocupada.set(false);
    }
  }

  /**
   * Congela la toma, la guarda en el borrador y la sube.
   *
   * <p>Se guarda **antes** de subir: si la subida falla o la pestaña se cierra, la foto ya
   * está a salvo y no hay que volver a tomarla (criterio 7). Se olvida en cuanto sube,
   * porque desde ese momento la fuente es el servidor.
   */
  protected async tomar(): Promise<void> {
    const elemento = this.video()?.nativeElement;
    const actual = this.publicacion();
    const posicion = this.paso();

    if (!elemento || actual === null || posicion === null) {
      return;
    }

    this.ocupada.set(true);
    this.error.set(null);

    try {
      const fotograma = await this.camara.capturar(elemento);
      await this.borrador.guardar(actual.id, { posicion, imagen: fotograma });

      await this.subir(actual.id, posicion, fotograma, false);
    } catch (fallo: unknown) {
      this.error.set(claveDelFallo(fallo));
    } finally {
      this.ocupada.set(false);
    }
  }

  /**
   * Sube una imagen elegida desde la galería. Criterio 8.
   *
   * <p>Pasa por el mismo recorte forzado que una toma de cámara —lo hace el store— y va
   * marcada como venida de galería, que le suma a la publicación una revisión más atenta.
   */
  protected async alElegirArchivo(evento: Event): Promise<void> {
    const campo = evento.target as HTMLInputElement;
    const archivo = campo.files?.[0];
    const actual = this.publicacion();
    const posicion = this.paso();

    // Se limpia siempre: sin esto, elegir el mismo archivo dos veces seguidas no vuelve a
    // disparar el evento, y quien reintenta tras un fallo no consigue nada.
    campo.value = '';

    if (!archivo || actual === null || posicion === null) {
      return;
    }

    this.ocupada.set(true);
    this.error.set(null);

    try {
      await this.subir(actual.id, posicion, archivo, true);
    } catch (fallo: unknown) {
      this.error.set(claveDelFallo(fallo));
    } finally {
      this.ocupada.set(false);
    }
  }

  /** Repite la toma en curso: se queda en el mismo paso y vuelve a abrir la cámara. */
  protected async repetir(): Promise<void> {
    this.error.set(null);
    if (!this.abierta()) {
      await this.empezar();
    }
  }

  protected irA(posicion: number): void {
    this.paso.set(posicion);
    this.error.set(null);
  }

  /** Sale del asistente y devuelve al formulario, que es donde está la rejilla. */
  protected async salir(): Promise<void> {
    const actual = this.publicacion();
    this.apagar();

    await this.router.navigate(actual === null ? ['/mis-publicaciones'] : ['/publicar', actual.id]);
  }

  protected admite(): boolean {
    const actual = this.publicacion();
    return actual !== null && admiteAsistente(actual);
  }

  private async subir(
    id: string,
    posicion: number,
    imagen: Blob,
    desdeGaleria: boolean,
  ): Promise<void> {
    const actualizada = await this.publicaciones.uploadShot.mutateAsync({
      id,
      posicion,
      imagen,
      desdeGaleria,
    });
    await this.borrador.olvidar(id, posicion);

    this.avanzar(actualizada);
  }

  /**
   * Pasa al siguiente hueco, o termina.
   *
   * <p>Busca el siguiente **pendiente** y no la posición de al lado: quien entró a rellenar
   * los huecos de un borrador a medias no quiere que el asistente se pare en las que ya
   * tenía hechas.
   *
   * <p>Se decide sobre la publicación que **devuelve la subida**, no sobre la señal de la
   * consulta. Las dos acaban diciendo lo mismo, pero la señal lo dice cuando la librería
   * propaga y esto corre justo antes: leerla aquí dejaba el asistente clavado en el paso
   * que se acababa de completar.
   */
  private avanzar(actualizada: Listing): void {
    const pendiente = pasosDeCaptura(actualizada).find((paso) => !paso.hecha);

    if (pendiente === undefined) {
      void this.salir();
      return;
    }
    this.paso.set(pendiente.posicion);
  }

  /**
   * Enciende el nivel, si el aparato lo permite.
   *
   * <p>Que se niegue **no interrumpe nada**: se anota para poder avisar de que las tomas
   * pueden quedar desalineadas, y la captura sigue (criterio 4).
   */
  private async activarNivel(): Promise<void> {
    if (!this.sensores.soportada()) {
      this.permiso.set('negado');
      return;
    }

    const concedido = await this.sensores.pedirPermiso();
    this.permiso.set(concedido ? 'concedido' : 'negado');

    if (!concedido) {
      return;
    }

    this.bajaDelSensor = this.sensores.escuchar((lectura) => this.inclinacion.set(lectura));
  }

  private apagar(): void {
    this.camara.cerrar(this.flujo());
    this.flujo.set(null);
    this.abierta.set(false);

    this.bajaDelSensor?.();
    this.bajaDelSensor = null;
    this.inclinacion.set(null);
  }
}

/**
 * La clave del texto que explica un fallo.
 *
 * <p>El motivo del normalizador se traduce por su código, que es lo que
 * `listing.capture.rejected.*` enumera. Cualquier otra cosa —la red, un 4xx del servidor—
 * ya tiene su mensaje en el interceptor de errores y no se pisa aquí.
 */
function claveDelFallo(fallo: unknown): string {
  return fallo instanceof ImagenNoNormalizable
    ? `listing.capture.rejected.${fallo.motivo}`
    : 'listing.capture.upload.failed';
}
