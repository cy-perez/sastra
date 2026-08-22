import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { ImagenEnGrises } from '../domain/blur';
import { CameraService, type Fotograma } from '../infrastructure/camera.service';
import { CaptureField } from './capture-field';

/**
 * El campo de captura. Criterios 2 y 3 de HU-002.
 *
 * <p>La cámara se sustituye por un doble: `getUserMedia` y `canvas` no existen en jsdom,
 * y lo que hay que comprobar aquí no es que el navegador dibuje, sino qué hace este
 * componente con lo que le entregan. Que el servicio de cámara dibuje bien no es una
 * decisión nuestra.
 */
describe('CaptureField', () => {
  /** Franjas de un píxel: el borde más marcado que existe. Pasa el umbral. */
  const nitida = (): ImagenEnGrises => {
    const ancho = 20;
    const alto = 20;
    const pixeles = new Uint8Array(ancho * alto);
    for (let i = 0; i < pixeles.length; i++) {
      pixeles[i] = i % 2 === 0 ? 0 : 255;
    }
    return { ancho, alto, pixeles };
  };

  /** Un tono plano: sin bordes, no pasa el umbral. */
  const borrosa = (): ImagenEnGrises => ({
    ancho: 20,
    alto: 20,
    pixeles: new Uint8Array(400).fill(128),
  });

  class CamaraFalsa {
    readonly pistas = [{ stop: vi.fn() }];
    readonly flujo = { getTracks: () => this.pistas } as unknown as MediaStream;

    disponible = true;
    concede = true;
    grises: ImagenEnGrises = nitida();
    aperturas = 0;

    soportada(): boolean {
      return this.disponible;
    }

    async abrir(): Promise<MediaStream> {
      this.aperturas++;
      if (!this.concede) {
        throw new Error('NotAllowedError');
      }
      return this.flujo;
    }

    cerrar(flujo: MediaStream | null): void {
      flujo?.getTracks().forEach((pista) => pista.stop());
    }

    async capturar(): Promise<Fotograma> {
      return { imagen: new Blob(['unos bytes'], { type: 'image/jpeg' }), grises: this.grises };
    }
  }

  /** Anfitrión para poder leer lo que el campo emite. */
  @Component({
    imports: [CaptureField],
    changeDetection: ChangeDetectionStrategy.OnPush,
    template: `<sastra-capture-field
      [encuadre]="'documento'"
      labelKey="sellerVerification.documentForm.front"
      (capturada)="recibida.set($event)"
    />`,
  })
  class Anfitrion {
    readonly recibida = signal<Blob | null>(null);
  }

  let camara: CamaraFalsa;

  const boton = (fixture: { nativeElement: HTMLElement }, texto: string) =>
    [...fixture.nativeElement.querySelectorAll('button')].find((candidato) =>
      candidato.textContent?.includes(texto),
    ) as HTMLButtonElement | undefined;

  const asentar = async (fixture: {
    whenStable: () => Promise<unknown>;
    detectChanges: () => void;
  }) => {
    for (let vuelta = 0; vuelta < 5; vuelta++) {
      await new Promise((listo) => setTimeout(listo, 0));
      fixture.detectChanges();
      await fixture.whenStable();
    }
  };

  beforeEach(() => {
    camara = new CamaraFalsa();

    // jsdom no trae URL de objeto: se suple porque el componente la usa para la vista
    // previa, y lo que se prueba no es eso.
    URL.createObjectURL = vi.fn(() => 'blob:una-vista-previa');
    URL.revokeObjectURL = vi.fn();

    TestBed.configureTestingModule({
      providers: [{ provide: CameraService, useValue: camara }],
    });
  });

  const montar = async () => {
    const fixture = TestBed.createComponent(Anfitrion);
    await asentar(fixture);
    return fixture;
  };

  /**
   * El criterio 3 es esta ausencia: no se ofrece subir desde la galería. Una prueba que
   * solo mirara el botón de cámara daría verde el día que alguien agregue un input de
   * archivo «para facilitarlo».
   */
  it('no ofrece ningún selector de archivos', async () => {
    const fixture = await montar();

    expect(fixture.nativeElement.querySelector('input[type="file"]')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('se toma en el momento');
  });

  it('ofrece abrir la cámara y muestra la guía de encuadre al abrirla', async () => {
    const fixture = await montar();

    boton(fixture, 'Abrir la cámara')?.click();
    await asentar(fixture);

    expect(fixture.nativeElement.textContent).toContain('Encuadra el documento dentro del marco');
    expect(boton(fixture, 'Tomar la foto')).toBeDefined();
    expect(camara.aperturas).toBe(1);
  });

  /**
   * La regresión que dejó la cámara encendida y el visor en negro.
   *
   * <p>El visor vive dentro de un `@if`, así que al volver de `getUserMedia` el elemento
   * todavía no está en el documento y la consulta de vista devuelve `undefined`. Asignando
   * `srcObject` en ese momento no se asignaba a nada: la cámara quedaba concedida, el
   * botón de tomar la foto ofrecido, y `capturar()` fallaba por un fotograma de cero por
   * cero. Se veía en cualquier navegador y no lo vio ninguna prueba, porque el doble de la
   * cámara no necesita un elemento de verdad para devolver un fotograma.
   *
   * <p>Se comprueba el enganche y no una llamada, que es lo único que distingue un visor
   * con imagen de uno sin ella.
   */
  it('engancha el flujo al visor cuando el visor ya existe', async () => {
    const fixture = await montar();

    boton(fixture, 'Abrir la cámara')?.click();
    await asentar(fixture);

    const visor = fixture.nativeElement.querySelector('video') as HTMLVideoElement | null;

    expect(visor).not.toBeNull();
    expect(visor?.srcObject).toBe(camara.flujo);
  });

  it('emite la foto cuando sale nítida y apaga la cámara', async () => {
    const fixture = await montar();

    boton(fixture, 'Abrir la cámara')?.click();
    await asentar(fixture);
    boton(fixture, 'Tomar la foto')?.click();
    await asentar(fixture);

    expect(fixture.componentInstance.recibida()).toBeInstanceOf(Blob);
    expect(camara.pistas[0]?.stop).toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('img')).not.toBeNull();
  });

  /**
   * Lo que pide el criterio 2: la borrosa se rechaza **antes** de subirla. Si se emitiera,
   * el formulario la mandaría y la persona esperaría una subida para nada.
   */
  it('no emite una foto borrosa y pide otra sin cerrar la cámara', async () => {
    camara.grises = borrosa();
    const fixture = await montar();

    boton(fixture, 'Abrir la cámara')?.click();
    await asentar(fixture);
    boton(fixture, 'Tomar la foto')?.click();
    await asentar(fixture);

    expect(fixture.componentInstance.recibida()).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('salió borrosa');
    // La cámara sigue abierta: repetir es un botón, no volver a empezar.
    expect(boton(fixture, 'Tomar la foto')).toBeDefined();
  });

  it('deja tomar otra después de una buena', async () => {
    const fixture = await montar();

    boton(fixture, 'Abrir la cámara')?.click();
    await asentar(fixture);
    boton(fixture, 'Tomar la foto')?.click();
    await asentar(fixture);

    boton(fixture, 'Tomar otra')?.click();
    await asentar(fixture);

    expect(camara.aperturas).toBe(2);
    expect(URL.revokeObjectURL).toHaveBeenCalled();
  });

  /** Caso borde de HU-002: se explica cómo habilitarla, no se reintenta en bucle. */
  it('explica cómo habilitar la cámara cuando se deniega', async () => {
    camara.concede = false;
    const fixture = await montar();

    boton(fixture, 'Abrir la cámara')?.click();
    await asentar(fixture);

    const aviso = fixture.nativeElement.querySelector('[role="alert"]');

    expect(aviso?.textContent).toContain('Habilítalo en los ajustes del navegador');
    expect(boton(fixture, 'Tomar la foto')).toBeUndefined();
  });

  it('avisa cuando el navegador no da acceso a la cámara', async () => {
    camara.disponible = false;
    const fixture = await montar();

    boton(fixture, 'Abrir la cámara')?.click();
    await asentar(fixture);

    expect(fixture.nativeElement.textContent).toContain('no da acceso a la cámara');
  });

  /**
   * Sin esto, el indicador de cámara del dispositivo se queda encendido al salir de la
   * pantalla, justo después de que alguien haya fotografiado su cédula.
   */
  it('apaga la cámara al destruir el componente', async () => {
    const fixture = await montar();

    boton(fixture, 'Abrir la cámara')?.click();
    await asentar(fixture);

    fixture.destroy();

    expect(camara.pistas[0]?.stop).toHaveBeenCalled();
  });
});
