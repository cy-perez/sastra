import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  apiUrlInterceptor,
  authInterceptor,
  errorInterceptor,
  languageInterceptor,
} from '../../../core/http/interceptors';
import type { Session } from '../../../core/session/session';
import { SessionStore } from '../../../core/session/session.store';
import type { Listing, ListingImage } from '../../../shared/domain/listing';
import { CameraService } from '../../../shared/infrastructure/camera.service';
import type { Inclinacion } from '../../../shared/infrastructure/orientation.service';
import { OrientationService } from '../../../shared/infrastructure/orientation.service';
import {
  ImagenNoNormalizable,
  PhotoNormalizer,
} from '../../../shared/infrastructure/photo-normalizer';
import { CaptureDraftStore } from '../infrastructure/capture-draft.store';
import { CaptureWizard } from './capture-wizard';

/**
 * El asistente de captura. HU-003, criterios 1 a 10.
 *
 * <p>La cámara, el acelerómetro, el normalizador y el borrador se doblan: los cuatro
 * hablan con el navegador y ninguno existe en jsdom. Lo que se comprueba aquí es **qué
 * hace este componente con lo que le entregan** —cuándo deja disparar, en qué paso abre,
 * qué explica cuando algo falla—, no que el navegador dibuje.
 */
describe('CaptureWizard', () => {
  const API = 'https://api.pruebas.sendik.co/api/v1';
  const ID = 'af8b9a52-4a3f-4a52-9a1e-8d9a2f1c4b70';

  const SESION: Session = {
    accessToken: 'un-token',
    user: { email: 'ana@correo.co', displayName: 'Ana Maria', emailVerified: true, roles: [] },
  };

  const toma = (position: number): ListingImage => ({
    id: `toma-${position}`,
    kind: 'SELLER_SHOT',
    position,
    angleDegrees: position * 45,
    url: `https://cdn.sendik.co/productos/${position}.jpg`,
  });

  const publicacion = (images: readonly ListingImage[], requiredShots = 8): Listing => ({
    id: ID,
    sellerId: 'b1c2d3e4-0000-4000-8000-000000000001',
    status: 'DRAFT',
    product: {
      categoryId: 'c1c2d3e4-0000-4000-8000-000000000002',
      title: 'Camisa de lino color hueso',
      description: null,
      brand: null,
      condition: 'LIKE_NEW',
      size: { system: 'ALPHA', value: 'M' },
      measurements: {},
      color: 'BEIGE',
      price: { amount: 185000, currency: 'COP' },
      shipping: null,
      isSealed: null,
      warrantyMonths: null,
    },
    images,
    requiredShots,
    requiresAttention: false,
    attentionReasons: [],
    rejectionReason: null,
    rejectionNote: null,
    publishedAt: null,
    createdAt: '2026-08-28T10:00:00Z',
    updatedAt: '2026-08-28T10:00:00Z',
    version: 1,
  });

  class CamaraFalsa {
    disponible = true;
    concede = true;
    apagadas = 0;

    soportada(): boolean {
      return this.disponible;
    }
    async abrir(): Promise<MediaStream> {
      if (!this.concede) {
        throw new Error('NotAllowedError');
      }
      return { getTracks: () => [{ stop: vi.fn() }] } as unknown as MediaStream;
    }
    cerrar(): void {
      this.apagadas++;
    }
    async capturar(): Promise<Blob> {
      return new Blob(['unos bytes'], { type: 'image/jpeg' });
    }
  }

  class SensoresFalsos {
    hacenFalta = false;
    concede = true;
    bajas = 0;
    private emitir: ((lectura: Inclinacion) => void) | null = null;

    soportada(): boolean {
      return true;
    }
    necesitaPermiso(): boolean {
      return this.hacenFalta;
    }
    async pedirPermiso(): Promise<boolean> {
      return this.concede;
    }
    escuchar(alLeer: (lectura: Inclinacion) => void): () => void {
      this.emitir = alLeer;
      return () => {
        this.bajas++;
        this.emitir = null;
      };
    }

    /** Simula una lectura del acelerómetro. */
    inclinar(beta: number, gamma: number): void {
      this.emitir?.({ beta, gamma });
    }
  }

  class NormalizadorFalso {
    rechazaCon: ImagenNoNormalizable | null = null;

    soportado(): boolean {
      return true;
    }
    async normalizar(imagen: Blob): Promise<Blob> {
      if (this.rechazaCon !== null) {
        throw this.rechazaCon;
      }
      return imagen;
    }
  }

  class BorradorFalso {
    readonly guardadas: { posicion: number; imagen: Blob }[] = [];
    readonly olvidadas: number[] = [];

    soportado(): boolean {
      return true;
    }
    async guardar(_id: string, toma: { posicion: number; imagen: Blob }): Promise<void> {
      this.guardadas.push(toma);
    }
    async olvidar(_id: string, posicion: number): Promise<void> {
      this.olvidadas.push(posicion);
    }
    async recuperar(): Promise<readonly { posicion: number; imagen: Blob }[]> {
      return [];
    }
    async limpiar(): Promise<void> {
      // Nada que limpiar en un doble.
    }
  }

  let camara: CamaraFalsa;
  let sensores: SensoresFalsos;
  let normalizador: NormalizadorFalso;
  let borrador: BorradorFalso;
  let parametros: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

  /**
   * Da vueltas al bucle de eventos y revisa la vista.
   *
   * <p>Sin `whenStable`, y es la misma razón que dejó anotada `publish-page.spec.ts`:
   * cuando queda una petición sin responder Angular la cuenta como tarea pendiente, así
   * que la espera no termina hasta que la prueba conteste. Y aquí se necesita revisar la
   * vista **con la subida todavía en vuelo**, que es justo cuando `whenStable` se cuelga.
   */
  const asentar = async (fixture: { detectChanges: () => void }) => {
    for (let vuelta = 0; vuelta < 6; vuelta++) {
      await new Promise((listo) => setTimeout(listo, 0));
      fixture.detectChanges();
    }
  };

  const boton = (fixture: { nativeElement: HTMLElement }, texto: string) =>
    [...fixture.nativeElement.querySelectorAll('button')].find((candidato) =>
      candidato.textContent?.includes(texto),
    ) as HTMLButtonElement | undefined;

  beforeEach(() => {
    camara = new CamaraFalsa();
    sensores = new SensoresFalsos();
    normalizador = new NormalizadorFalso();
    borrador = new BorradorFalso();
    parametros = new BehaviorSubject(convertToParamMap({ id: ID }));

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(
          withInterceptors([
            apiUrlInterceptor,
            authInterceptor,
            languageInterceptor,
            errorInterceptor,
          ]),
        ),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { paramMap: parametros.asObservable() } },
        { provide: CameraService, useValue: camara },
        { provide: OrientationService, useValue: sensores },
        { provide: PhotoNormalizer, useValue: normalizador },
        { provide: CaptureDraftStore, useValue: borrador },
      ],
    });

    TestBed.inject(SessionStore).set(SESION);
  });

  /** Monta el asistente con la publicación que devuelva el servidor. */
  const montar = async (respuesta: Listing) => {
    const fixture = TestBed.createComponent(CaptureWizard);
    const backend = TestBed.inject(HttpTestingController);

    await asentar(fixture);
    backend.expectOne(`${API}/listings/${ID}`).flush(respuesta);
    await asentar(fixture);

    return { fixture, backend };
  };

  describe('a quién se le abre', () => {
    /** RN-065: la tecnología sellada son cuatro tomas del empaque, sin giro que guiar. */
    it('no ofrece el asistente a la tecnología declarada sellada', async () => {
      const { fixture } = await montar(publicacion([], 4));

      expect(boton(fixture, 'Tomar la foto')).toBeUndefined();
      expect(fixture.nativeElement.querySelector('progress')).toBeNull();
    });

    it('abre en el primer paso cuando no hay ninguna toma', async () => {
      const { fixture } = await montar(publicacion([]));

      expect(fixture.nativeElement.textContent).toContain('Frente');
      expect(fixture.nativeElement.textContent).toContain('Toma 1 de 8');
    });

    /** Criterio 6 y la decisión de retomar: lo ya subido se da por bueno. */
    it('abre en la primera que falte cuando el borrador viene a medias', async () => {
      const { fixture } = await montar(publicacion([toma(0), toma(1), toma(2)]));

      expect(fixture.nativeElement.textContent).toContain('Toma 4 de 8');
      expect(fixture.nativeElement.textContent).toContain('Espalda y lado derecho');
    });
  });

  describe('el progreso', () => {
    it('cuenta las tomas que ya están, en la barra y en el anuncio', async () => {
      const { fixture } = await montar(publicacion([toma(0), toma(2)]));

      const barra = fixture.nativeElement.querySelector('progress') as HTMLProgressElement;

      expect(barra.value).toBe(2);
      expect(barra.max).toBe(8);
      expect(fixture.nativeElement.textContent).toContain('2 de 8 tomas listas');
    });

    it('avisa de cuáles no pueden faltar', async () => {
      const { fixture } = await montar(publicacion([]));

      expect(fixture.nativeElement.textContent).toContain('Esta no puede faltar');
    });
  });

  describe('el nivel', () => {
    /** Criterio 3: pasados los 5 grados el obturador se deshabilita y se explica. */
    it('deshabilita el obturador con el teléfono inclinado y dice por qué', async () => {
      const { fixture } = await montar(publicacion([]));

      boton(fixture, 'Tomar las fotos con la cámara')?.click();
      await asentar(fixture);

      sensores.inclinar(90, 20);
      await asentar(fixture);

      expect(boton(fixture, 'Tomar la foto')?.disabled).toBe(true);
      expect(fixture.nativeElement.textContent).toContain('Endereza el teléfono');
      expect(fixture.nativeElement.textContent).toContain('más de 5 grados');
    });

    it('habilita el obturador con el teléfono nivelado', async () => {
      const { fixture } = await montar(publicacion([]));

      boton(fixture, 'Tomar las fotos con la cámara')?.click();
      await asentar(fixture);

      sensores.inclinar(90, 0);
      await asentar(fixture);

      expect(boton(fixture, 'Tomar la foto')?.disabled).toBe(false);
      expect(fixture.nativeElement.textContent).toContain('Nivelado');
    });

    /**
     * Criterio 4, y es el que más importa de los tres: negar el permiso **nunca bloquea la
     * publicación**. Un obturador muerto sin forma de salir sería justo eso.
     */
    it('sigue dejando disparar cuando se niega el permiso de sensores, y avisa', async () => {
      sensores.hacenFalta = true;
      sensores.concede = false;

      const { fixture } = await montar(publicacion([]));

      boton(fixture, 'Tomar las fotos con la cámara')?.click();
      await asentar(fixture);

      expect(boton(fixture, 'Tomar la foto')?.disabled).toBe(false);
      expect(fixture.nativeElement.textContent).toContain('Seguimos sin el nivel');
    });
  });

  describe('tomar una foto', () => {
    it('la guarda en el borrador antes de subirla, y la olvida cuando sube', async () => {
      const { fixture, backend } = await montar(publicacion([]));

      boton(fixture, 'Tomar las fotos con la cámara')?.click();
      await asentar(fixture);

      boton(fixture, 'Tomar la foto')?.click();
      await asentar(fixture);

      // Guardada antes de que la subida conteste: es lo que salva la foto si la pestaña
      // se cierra a mitad (criterio 7).
      expect(borrador.guardadas).toEqual([
        expect.objectContaining({ posicion: 0 }) as unknown as { posicion: number; imagen: Blob },
      ]);

      const subida = backend.expectOne(
        (llamada) => llamada.method === 'POST' && llamada.url === `${API}/listings/${ID}/images`,
      );
      subida.flush(publicacion([toma(0)]));
      await asentar(fixture);

      expect(borrador.olvidadas).toEqual([0]);
    });

    /** Lo capturado con la cámara **no** es carga desde galería (criterio 18 de HU-007). */
    it('declara que la toma no viene de la galería', async () => {
      const { fixture, backend } = await montar(publicacion([]));

      boton(fixture, 'Tomar las fotos con la cámara')?.click();
      await asentar(fixture);
      boton(fixture, 'Tomar la foto')?.click();
      await asentar(fixture);

      const subida = backend.expectOne(
        (llamada) => llamada.method === 'POST' && llamada.url === `${API}/listings/${ID}/images`,
      );

      expect(subida.request.params.get('fromGallery')).toBe('false');
      expect(subida.request.params.get('position')).toBe('0');
      subida.flush(publicacion([toma(0)]));
    });

    it('avanza al siguiente paso pendiente cuando la toma sube', async () => {
      const { fixture, backend } = await montar(publicacion([]));

      boton(fixture, 'Tomar las fotos con la cámara')?.click();
      await asentar(fixture);
      boton(fixture, 'Tomar la foto')?.click();
      await asentar(fixture);

      backend
        .expectOne(
          (llamada) => llamada.method === 'POST' && llamada.url === `${API}/listings/${ID}/images`,
        )
        .flush(publicacion([toma(0)]));
      await asentar(fixture);

      expect(fixture.nativeElement.textContent).toContain('Toma 2 de 8');
    });
  });

  describe('cuando la foto no sirve', () => {
    /**
     * RN-019 con el número que la persona necesita: el recorte se decide en el
     * dispositivo, así que esto se explica **sin gastar la subida**.
     */
    it('explica que la foto es muy pequeña y no la sube', async () => {
      normalizador.rechazaCon = new ImagenNoNormalizable('RESOLUCION_INSUFICIENTE');

      const { fixture, backend } = await montar(publicacion([]));

      boton(fixture, 'Tomar las fotos con la cámara')?.click();
      await asentar(fixture);
      boton(fixture, 'Tomar la foto')?.click();
      await asentar(fixture);

      expect(fixture.nativeElement.textContent).toContain('900 × 1200');
      backend.expectNone(
        (llamada) => llamada.method === 'POST' && llamada.url === `${API}/listings/${ID}/images`,
      );
    });

    it('explica un archivo ilegible con su propio texto', async () => {
      normalizador.rechazaCon = new ImagenNoNormalizable('IMAGEN_ILEGIBLE');

      const { fixture } = await montar(publicacion([]));

      boton(fixture, 'Tomar las fotos con la cámara')?.click();
      await asentar(fixture);
      boton(fixture, 'Tomar la foto')?.click();
      await asentar(fixture);

      expect(fixture.nativeElement.textContent).toContain('No pudimos leer este archivo');
    });
  });

  describe('sin cámara', () => {
    /** Criterio 8: se ofrece la galería, y se ofrece desde el principio. */
    it('ofrece la galería siempre, con su aviso de revisión más atenta', async () => {
      const { fixture } = await montar(publicacion([]));

      expect(fixture.nativeElement.textContent).toContain('Subir desde la galería');
      expect(fixture.nativeElement.textContent).toContain('revisión más atenta');
    });

    it('explica que no hay cámara cuando el navegador no la da', async () => {
      camara.disponible = false;

      const { fixture } = await montar(publicacion([]));

      boton(fixture, 'Tomar las fotos con la cámara')?.click();
      await asentar(fixture);

      expect(fixture.nativeElement.textContent).toContain('no tiene cámara disponible');
    });

    it('explica lo mismo cuando se deniega el permiso de cámara', async () => {
      camara.concede = false;

      const { fixture } = await montar(publicacion([]));

      boton(fixture, 'Tomar las fotos con la cámara')?.click();
      await asentar(fixture);

      expect(fixture.nativeElement.textContent).toContain('no tiene cámara disponible');
      expect(fixture.nativeElement.querySelector('video')).toBeNull();
    });
  });

  describe('al salir', () => {
    /**
     * Sin esto el indicador de cámara del dispositivo se queda encendido después de salir
     * de la pantalla. Es la misma razón que en HU-002, y aquí pesa igual.
     */
    it('apaga la cámara al destruir el componente', async () => {
      const { fixture } = await montar(publicacion([]));

      boton(fixture, 'Tomar las fotos con la cámara')?.click();
      await asentar(fixture);

      fixture.destroy();

      expect(camara.apagadas).toBeGreaterThan(0);
    });

    it('suelta el sensor al destruir el componente', async () => {
      const { fixture } = await montar(publicacion([]));

      boton(fixture, 'Tomar las fotos con la cámara')?.click();
      await asentar(fixture);

      fixture.destroy();

      expect(sensores.bajas).toBe(1);
    });
  });
});
