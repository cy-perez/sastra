import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { BehaviorSubject } from 'rxjs';

import {
  apiUrlInterceptor,
  errorInterceptor,
  languageInterceptor,
} from '../../../core/http/interceptors';
import { ProductPage } from './product-page';

/**
 * La ficha de producto. HU-009, criterios 11 a 17 y 21.
 *
 * <p>Ninguna prueba pone sesión: la ficha sirve igual a quien no tiene cuenta.
 */
describe('ProductPage', () => {
  const API = 'https://api.pruebas.sendik.co/api/v1';
  const ID = '01a04385-47b7-79c7-b3f2-62c03a8d4a88';
  const VENDEDOR = '01a04385-47b7-79c7-b3f2-62c03a8d4a99';

  const toma = (position: number) => ({
    id: `toma-${position}`,
    kind: 'SELLER_SHOT',
    position,
    angleDegrees: position * 45,
    url: `/toma-${position}.jpg`,
  });

  const publicacion = (cambios: Record<string, unknown> = {}) => ({
    id: ID,
    sellerId: VENDEDOR,
    publishedAt: '2026-08-27T15:00:00Z',
    images: [toma(2), toma(0), toma(4)],
    product: {
      categoryId: 'camisas',
      title: 'Camisa de lino color hueso',
      description: 'Usada dos veces, sin manchas.',
      brand: 'Zara',
      condition: 'LIKE_NEW',
      size: { system: 'ALPHA', value: 'M' },
      measurements: { CHEST: 52, SHOULDERS: 41 },
      color: 'BEIGE',
      price: { amount: 185000, currency: 'COP' },
      shipping: null,
      isSealed: null,
      warrantyMonths: null,
      ...cambios,
    },
  });

  const vendedor = (verified = true) => ({
    id: VENDEDOR,
    name: 'Ana María',
    avatarUrl: null,
    verified,
  });

  let parametros: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

  beforeEach(() => {
    parametros = new BehaviorSubject(convertToParamMap({ id: ID }));

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: parametros.asObservable() } },
        provideHttpClient(
          withInterceptors([apiUrlInterceptor, languageInterceptor, errorInterceptor]),
        ),
        provideHttpClientTesting(),
      ],
    });
  });

  const bombear = async (fixture: ComponentFixture<ProductPage>) => {
    for (let vuelta = 0; vuelta < 5; vuelta++) {
      await new Promise((listo) => setTimeout(listo, 0));
      fixture.detectChanges();
    }
  };

  const montar = async () => {
    const fixture = TestBed.createComponent(ProductPage);
    const backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    await bombear(fixture);
    return { fixture, backend };
  };

  const responder = async (
    fixture: ComponentFixture<ProductPage>,
    backend: HttpTestingController,
    cuerpo: object,
    quien: object | null = vendedor(),
  ) => {
    backend.expectOne((llamada) => llamada.url === `${API}/listings/${ID}`).flush(cuerpo);
    await bombear(fixture);

    if (quien !== null) {
      backend.expectOne((llamada) => llamada.url === `${API}/sellers/${VENDEDOR}`).flush(quien);
      await bombear(fixture);
    }
  };

  /** Criterio 12: lo que el vendedor declaró. */
  it('muestra los datos declarados del producto', async () => {
    const { fixture, backend } = await montar();
    await responder(fixture, backend, publicacion());

    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('Camisa de lino color hueso');
    expect(texto).toContain('Usada dos veces, sin manchas.');
    expect(texto).toContain('Zara');
    expect(texto).toContain('Como nuevo');
    expect(texto).toContain('Beige');
    expect(texto).toContain('52');
  });

  /** Criterio 11: la frontal primero, aunque llegue en otro orden. */
  it('abre el carrusel por la toma frontal', async () => {
    const { fixture, backend } = await montar();
    await responder(fixture, backend, publicacion());

    const primera = fixture.nativeElement.querySelector('.ficha__toma img') as HTMLImageElement;
    expect(primera.getAttribute('src')).toBe('/toma-0.jpg');
  });

  /**
   * Criterio 14, RN-066. El rótulo va en el carrusel, no solo debajo.
   *
   * <p>Sin él, una foto del fabricante junto a fotos reales lleva a creer que el producto
   * se ve así, que es exactamente lo que la regla existe para impedir.
   */
  it('rotula la imagen de referencia dentro del carrusel, RN-066', async () => {
    const { fixture, backend } = await montar();

    await responder(fixture, backend, {
      ...publicacion({ isSealed: true }),
      images: [
        toma(0),
        { id: 'ref', kind: 'REFERENCE', position: 0, angleDegrees: null, url: '/caja.jpg' },
      ],
    });

    const rotulo = fixture.nativeElement.querySelector('.ficha__referencia');
    expect(rotulo).not.toBeNull();
    expect(rotulo.textContent).toContain('Imagen de referencia');
    expect(fixture.nativeElement.textContent).toContain('No la tomó el vendedor');
  });

  /** Sin imágenes de referencia no se rotula nada: es de tecnología sellada y nada más. */
  it('no rotula nada cuando todas las fotos son del vendedor', async () => {
    const { fixture, backend } = await montar();
    await responder(fixture, backend, publicacion());

    expect(fixture.nativeElement.querySelector('.ficha__referencia')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Imagen de referencia');
  });

  /** Criterio 15 y 21: quién vende, con su sello, y el enlace a su perfil. */
  it('dice quién vende, muestra la insignia y enlaza a su perfil', async () => {
    const { fixture, backend } = await montar();
    await responder(fixture, backend, publicacion());

    expect(fixture.nativeElement.textContent).toContain('Ana María');
    expect(fixture.nativeElement.querySelector('.insignia-verificado')).not.toBeNull();

    const enlace = fixture.nativeElement.querySelector(
      `a[href="/vendedor/${VENDEDOR}"]`,
    ) as HTMLAnchorElement;
    expect(enlace).not.toBeNull();
  });

  /** Sin sello no hay insignia: el acento bronce solo aparece donde algo lo respalda. */
  it('no muestra insignia si el vendedor no está verificado', async () => {
    const { fixture, backend } = await montar();
    await responder(fixture, backend, publicacion(), vendedor(false));

    expect(fixture.nativeElement.textContent).toContain('Ana María');
    expect(fixture.nativeElement.querySelector('.insignia-verificado')).toBeNull();
  });

  /** Criterio 13: no publicada y no existe dicen lo mismo. */
  it('dice que ya no está disponible cuando la API responde 404, criterio 13', async () => {
    const { fixture, backend } = await montar();

    backend
      .expectOne((llamada) => llamada.url === `${API}/listings/${ID}`)
      .flush({ code: 'COMMON_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    await bombear(fixture);

    expect(fixture.nativeElement.textContent).toContain('ya no está disponible');
  });

  /**
   * Criterio 16: el título describe este producto, no la plantilla.
   *
   * <p>Es lo que un buscador indexa y lo que se ve al compartir el enlace, así que un
   * título genérico deja a todas las fichas compitiendo por el mismo resultado.
   */
  it('pone el título de la publicación, criterio 16', async () => {
    const { fixture, backend } = await montar();
    await responder(fixture, backend, publicacion());

    expect(TestBed.inject(Title).getTitle()).toBe('Camisa de lino color hueso');
  });

  /**
   * La garantía del fabricante **no** se pinta.
   *
   * <p>Está aplazada a la tanda legal y `textos-web.md` dice que bloquea esta pantalla.
   * La prueba existe para que nadie la agregue sin darse cuenta de que falta el texto.
   */
  it('no pinta la garantía del fabricante mientras su texto no exista, RN-067', async () => {
    const { fixture, backend } = await montar();
    await responder(fixture, backend, publicacion({ isSealed: true, warrantyMonths: 12 }));

    expect(fixture.nativeElement.textContent).not.toContain('12');
  });
});
