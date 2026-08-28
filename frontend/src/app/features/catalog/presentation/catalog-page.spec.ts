import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { BehaviorSubject } from 'rxjs';

import {
  apiUrlInterceptor,
  errorInterceptor,
  languageInterceptor,
} from '../../../core/http/interceptors';
import { CatalogPage } from './catalog-page';

/**
 * El catálogo público. HU-009, criterios 1 a 10.
 *
 * <p>Se prueba lo que ve quien entra: que la rejilla pinta lo que llega, que los tres
 * estados existen, que una categoría retirada del árbol se dice y no se pinta vacía, y que
 * «ver más» pide el tramo siguiente con el cursor que dio el anterior.
 *
 * <p><strong>Ninguna prueba pone sesión</strong>, y eso es parte de lo que se prueba: el
 * catálogo sirve igual a quien no tiene cuenta.
 */
describe('CatalogPage', () => {
  const API = 'https://api.pruebas.sendik.co/api/v1';
  const CAMISAS = 'id-camisas';

  const arbol = [
    {
      id: 'id-tops',
      slug: 'tops',
      nameEs: 'Parte superior',
      nameEn: 'Tops',
      familySlug: null,
      sizeSystems: [],
      requiredMeasurements: [],
      allowsUsed: true,
      children: [
        {
          id: CAMISAS,
          slug: 'camisas-y-blusas',
          nameEs: 'Camisas y blusas',
          nameEn: 'Shirts and blouses',
          familySlug: 'tops',
          sizeSystems: ['ALPHA'],
          requiredMeasurements: ['CHEST'],
          allowsUsed: true,
          children: [],
        },
      ],
    },
  ];

  const publicacion = (id: string, titulo: string) => ({
    id,
    sellerId: 'vendedor',
    publishedAt: '2026-08-27T15:00:00Z',
    images: [
      { id: `${id}-0`, kind: 'SELLER_SHOT', position: 0, angleDegrees: 0, url: `/${id}.jpg` },
    ],
    product: {
      categoryId: CAMISAS,
      title: titulo,
      description: 'Usada dos veces.',
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
  });

  let parametros: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

  beforeEach(() => {
    parametros = new BehaviorSubject(convertToParamMap({}));

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

  const montar = async () => {
    const fixture = TestBed.createComponent(CatalogPage);
    const backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    await bombear(fixture);
    return { fixture, backend };
  };

  /**
   * Deja que TanStack asiente sus consultas.
   *
   * <p>Con `Promise.resolve()` no basta: la consulta infinita encadena varias tareas de
   * macrocola antes de publicar el tramo, y la pantalla se quedaba en «cargando». Es el
   * mismo ayudante que usa `publish-page.spec.ts`.
   */
  const bombear = async (fixture: ComponentFixture<CatalogPage>) => {
    for (let vuelta = 0; vuelta < 5; vuelta++) {
      await new Promise((listo) => setTimeout(listo, 0));
      fixture.detectChanges();
    }
  };

  const responder = async (
    fixture: ComponentFixture<CatalogPage>,
    backend: HttpTestingController,
    tramo: object,
  ) => {
    backend.expectOne((llamada) => llamada.url === `${API}/categories`).flush(arbol);
    backend.expectOne((llamada) => llamada.url === `${API}/listings`).flush(tramo);
    await bombear(fixture);
  };

  /** Criterio 1: lo publicado, en una rejilla. */
  it('pinta lo que llega del catálogo', async () => {
    const { fixture, backend } = await montar();

    await responder(fixture, backend, {
      items: [publicacion('uno', 'Camisa de lino'), publicacion('dos', 'Jean recto')],
      nextCursor: null,
      hasMore: false,
    });

    expect(fixture.nativeElement.textContent).toContain('Camisa de lino');
    expect(fixture.nativeElement.textContent).toContain('Jean recto');
  });

  /** Criterio 5: sin nada publicado se dice, no se deja la página en blanco. */
  it('dice que no hay nada cuando el catálogo está vacío, criterio 5', async () => {
    const { fixture, backend } = await montar();

    await responder(fixture, backend, { items: [], nextCursor: null, hasMore: false });

    expect(fixture.nativeElement.textContent).toContain('Todavía no hay nada publicado');
  });

  it('avisa y ofrece reintentar cuando el catálogo falla', async () => {
    const { fixture, backend } = await montar();

    backend.expectOne((llamada) => llamada.url === `${API}/categories`).flush(arbol);
    backend
      .expectOne((llamada) => llamada.url === `${API}/listings`)
      .flush({ code: 'COMMON_UNEXPECTED' }, { status: 500, statusText: 'Server Error' });
    await bombear(fixture);

    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
  });

  /**
   * Criterio 9. Una categoría que no está en el árbol se dice, no se pinta vacía.
   *
   * <p>Un listado vacío se leería como «esta categoría existe y no tiene nada», que es
   * otra cosa y además mentira.
   */
  it('dice que una categoría retirada no existe, criterio 9', async () => {
    parametros.next(convertToParamMap({ familia: 'tops', categoria: 'inventada' }));

    const { fixture, backend } = await montar();
    backend.expectOne((llamada) => llamada.url === `${API}/categories`).flush(arbol);
    await bombear(fixture);

    expect(fixture.nativeElement.textContent).toContain('Esta categoría no existe');
  });

  /** Criterio 8: la categoría de la dirección llega a la petición. */
  it('pide solo la categoría abierta', async () => {
    parametros.next(convertToParamMap({ familia: 'tops', categoria: 'camisas-y-blusas' }));

    const { fixture, backend } = await montar();
    backend.expectOne((llamada) => llamada.url === `${API}/categories`).flush(arbol);
    await bombear(fixture);

    const peticion = backend.expectOne((llamada) => llamada.url === `${API}/listings`);
    expect(peticion.request.params.get('category')).toBe(CAMISAS);
  });

  /** Criterio 3: «ver más» sigue por el cursor que dio el tramo anterior. */
  it('pide el tramo siguiente con el cursor del anterior', async () => {
    const { fixture, backend } = await montar();

    await responder(fixture, backend, {
      items: [publicacion('uno', 'Camisa de lino')],
      nextCursor: 'el-cursor',
      hasMore: true,
    });

    const boton = [...fixture.nativeElement.querySelectorAll('button')].find(
      (candidato: HTMLButtonElement) => candidato.textContent?.includes('Ver más'),
    ) as HTMLButtonElement;
    boton.click();
    await bombear(fixture);

    const siguiente = backend.expectOne((llamada) => llamada.url === `${API}/listings`);
    expect(siguiente.request.params.get('cursor')).toBe('el-cursor');
  });

  /** El árbol puede caerse sin llevarse el listado por delante. */
  it('sigue sirviendo el listado aunque el árbol de categorías falle', async () => {
    const { fixture, backend } = await montar();

    backend
      .expectOne((llamada) => llamada.url === `${API}/categories`)
      .flush({ code: 'COMMON_UNEXPECTED' }, { status: 500, statusText: 'Server Error' });
    backend
      .expectOne((llamada) => llamada.url === `${API}/listings`)
      .flush({
        items: [publicacion('uno', 'Camisa de lino')],
        nextCursor: null,
        hasMore: false,
      });
    await bombear(fixture);

    expect(fixture.nativeElement.textContent).toContain('Camisa de lino');
    expect(fixture.nativeElement.textContent).toContain('No pudimos cargar las categorías');
  });
});
