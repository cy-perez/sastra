import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { ShotsField } from './shots-field';
import type { Listing, ListingImage } from '../../../shared/domain/listing';

/**
 * La rejilla de tomas. HU-007, criterios 14 a 18.
 *
 * <p>El dominio ya prueba cuántas casillas van y qué falta; lo que se comprueba aquí es
 * lo que la persona ve y hace: qué se ofrece en cada casilla, qué se emite al elegir un
 * archivo y qué pasa con el campo después.
 */
describe('ShotsField', () => {
  const toma = (position: number): ListingImage => ({
    id: `toma-${position}`,
    kind: 'SELLER_SHOT',
    position,
    angleDegrees: position * 45,
    url: `https://cdn.sendik.co/productos/${position}.jpg`,
  });

  const publicacion = (cambios: Partial<Listing> = {}): Listing => ({
    id: 'af8b9a52-4a3f-4a52-9a1e-8d9a2f1c4b70',
    sellerId: 'vendedor',
    status: 'DRAFT',
    product: {
      categoryId: 'hoja-camisas',
      title: 'Camisa de lino',
      description: null,
      brand: null,
      condition: 'LIKE_NEW',
      size: null,
      measurements: {},
      color: null,
      price: null,
      shipping: null,
      isSealed: null,
      warrantyMonths: null,
    },
    images: [],
    requiredShots: 8,
    requiresAttention: false,
    attentionReasons: [],
    rejectionReason: null,
    rejectionNote: null,
    publishedAt: null,
    createdAt: '2026-08-26T10:00:00Z',
    updatedAt: '2026-08-26T10:00:00Z',
    version: 1,
    ...cambios,
  });

  const montar = (listado: Listing, deshabilitado = false, subiendo: number | null = null) => {
    const fixture = TestBed.createComponent(ShotsField);
    fixture.componentRef.setInput('publicacion', listado);
    fixture.componentRef.setInput('deshabilitado', deshabilitado);
    fixture.componentRef.setInput('subiendo', subiendo);
    fixture.detectChanges();
    return fixture;
  };

  const casillas = (fixture: { nativeElement: HTMLElement }) =>
    [...fixture.nativeElement.querySelectorAll('.tomas__casilla')] as HTMLElement[];

  beforeEach(() => {
    TestBed.configureTestingModule({});
  });

  /** RN-017: ocho, una cada 45 grados. */
  it('pinta ocho casillas rotuladas con sus grados', () => {
    const fixture = montar(publicacion());

    expect(casillas(fixture)).toHaveLength(8);
    const texto = fixture.nativeElement.textContent ?? '';
    expect(texto).toContain('0°');
    expect(texto).toContain('315°');
  });

  /** RN-065: la tecnología sellada baja a cuatro, y son las del empaque. */
  it('pinta cuatro casillas cuando el servidor exige cuatro', () => {
    const fixture = montar(publicacion({ requiredShots: 4 }));

    expect(casillas(fixture)).toHaveLength(4);
    const texto = fixture.nativeElement.textContent ?? '';
    expect(texto).toContain('270°');
    expect(texto).not.toContain('45°');
  });

  /** RN-016: las cuatro canónicas se rotulan aparte porque no pueden faltar. */
  it('rotula las cuatro canónicas', () => {
    const fixture = montar(publicacion());

    const rotuladas = fixture.nativeElement.querySelectorAll('.tomas__obligatoria');
    expect(rotuladas).toHaveLength(4);
  });

  it('ofrece quitar donde ya hay una toma, y agregar donde no', () => {
    const fixture = montar(publicacion({ images: [toma(0)] }));

    expect(fixture.nativeElement.querySelectorAll('img')).toHaveLength(1);
    // Siete huecos y una foto.
    expect(fixture.nativeElement.querySelectorAll('.tomas__vacia')).toHaveLength(7);
  });

  it('emite la posición y el archivo al elegir uno', () => {
    const fixture = montar(publicacion());
    const emitido: { posicion: number; imagen: File }[] = [];
    fixture.componentInstance.subir.subscribe((evento) => emitido.push(evento));

    const campo = fixture.nativeElement.querySelector('#toma-3') as HTMLInputElement;
    const archivo = new File(['x'], 'toma.jpg', { type: 'image/jpeg' });
    Object.defineProperty(campo, 'files', { value: [archivo] });
    campo.dispatchEvent(new Event('change'));

    expect(emitido).toHaveLength(1);
    expect(emitido[0]?.posicion).toBe(3);
    expect(emitido[0]?.imagen.name).toBe('toma.jpg');
  });

  /**
   * El campo se limpia tras emitir.
   *
   * <p>Sin eso, quien sube una foto, ve que falló y elige **el mismo archivo** otra vez
   * no consigue nada: el navegador no dispara `change` si el valor no cambió. Es la clase
   * de detalle que un refactor se lleva por delante sin que nadie lo note.
   */
  it('limpia el campo después de emitir, para poder reintentar con el mismo archivo', () => {
    const fixture = montar(publicacion());

    const campo = fixture.nativeElement.querySelector('#toma-0') as HTMLInputElement;
    Object.defineProperty(campo, 'files', {
      value: [new File(['x'], 'toma.jpg', { type: 'image/jpeg' })],
    });
    campo.dispatchEvent(new Event('change'));

    expect(campo.value).toBe('');
  });

  it('no emite nada si no se eligió archivo', () => {
    const fixture = montar(publicacion());
    let emisiones = 0;
    fixture.componentInstance.subir.subscribe(() => (emisiones += 1));

    const campo = fixture.nativeElement.querySelector('#toma-0') as HTMLInputElement;
    Object.defineProperty(campo, 'files', { value: [] });
    campo.dispatchEvent(new Event('change'));

    expect(emisiones).toBe(0);
  });

  it('emite el identificador de la toma al quitarla', () => {
    const fixture = montar(publicacion({ images: [toma(0)] }));
    const quitados: string[] = [];
    fixture.componentInstance.quitar.subscribe((id) => quitados.push(id));

    const boton = fixture.nativeElement.querySelector('.tomas__quitar') as HTMLButtonElement;
    boton.click();

    expect(quitados).toEqual(['toma-0']);
  });

  /** Con la publicación bloqueada no se toca nada: ni se agrega ni se quita. */
  it('no deja tocar nada cuando está deshabilitado', () => {
    const fixture = montar(publicacion({ images: [toma(0)] }), true);

    const quitar = fixture.nativeElement.querySelector('.tomas__quitar') as HTMLButtonElement;
    const campo = fixture.nativeElement.querySelector('#toma-1') as HTMLInputElement;

    expect(quitar.disabled).toBe(true);
    expect(campo.disabled).toBe(true);
  });

  /**
   * Solo se bloquea la casilla que está subiendo.
   *
   * <p>Bloquearlas todas sacaba las ocho del orden de tabulación mientras una subía, y
   * dejaba a quien navega con teclado sin nada que pulsar.
   */
  it('bloquea solo la casilla que está subiendo', () => {
    const fixture = montar(publicacion(), false, 2);

    expect((fixture.nativeElement.querySelector('#toma-2') as HTMLInputElement).disabled).toBe(
      true,
    );
    expect((fixture.nativeElement.querySelector('#toma-3') as HTMLInputElement).disabled).toBe(
      false,
    );
  });

  it('avisa de cuántas canónicas faltan', () => {
    const fixture = montar(publicacion({ images: [toma(0), toma(2)] }));

    expect(fixture.nativeElement.querySelector('.tomas__aviso')?.textContent).toContain('2');
  });

  /** El progreso se anuncia con su sustantivo: «3 de 8» a secas no dice nada. */
  it('anuncia el progreso con sustantivo', () => {
    const fixture = montar(publicacion({ images: [toma(0), toma(1), toma(2)] }));

    const anuncio = fixture.nativeElement.querySelector('[aria-live="polite"]');
    expect(anuncio?.textContent).toContain('3 de 8 fotos');
  });
});
