import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';

import {
  apiUrlInterceptor,
  authInterceptor,
  errorInterceptor,
  languageInterceptor,
} from '../../../core/http/interceptors';
import type { Session } from '../../../core/session/session';
import { SessionStore } from '../../../core/session/session.store';
import { PublishPage } from './publish-page';

/**
 * El formulario de publicar. HU-007.
 *
 * Lo que se comprueba es lo que la persona ve y puede hacer, no los métodos del
 * componente: qué se ofrece según el estado, qué se marca cuando el servidor dice que
 * falta algo, y que el envío no se ofrezca antes de tiempo.
 */
describe('PublishPage', () => {
  const API = 'https://api.pruebas.sendik.co/api/v1';
  const ID = 'af8b9a52-4a3f-4a52-9a1e-8d9a2f1c4b70';

  const SESION: Session = {
    accessToken: 'un-token',
    user: { email: 'ana@correo.co', displayName: 'Ana Maria', emailVerified: true, roles: [] },
  };

  const ARBOL = [
    {
      id: 'familia-tops',
      slug: 'tops',
      nameEs: 'Parte superior',
      nameEn: 'Tops',
      familySlug: null,
      sizeSystems: [],
      requiredMeasurements: [],
      allowsUsed: true,
      children: [
        {
          id: 'hoja-camisas',
          slug: 'camisas-y-blusas',
          nameEs: 'Camisas y blusas',
          nameEn: 'Shirts and blouses',
          familySlug: 'tops',
          sizeSystems: ['ALPHA'],
          requiredMeasurements: ['CHEST', 'LENGTH'],
          allowsUsed: true,
          children: [],
        },
      ],
    },
    {
      id: 'familia-tech',
      slug: 'tech',
      nameEs: 'Tecnología',
      nameEn: 'Tech',
      familySlug: null,
      sizeSystems: [],
      requiredMeasurements: [],
      allowsUsed: false,
      children: [
        {
          id: 'hoja-celulares',
          slug: 'celulares-y-tabletas',
          nameEs: 'Celulares y tabletas',
          nameEn: 'Phones and tablets',
          familySlug: 'tech',
          sizeSystems: ['ONE_SIZE'],
          requiredMeasurements: ['HEIGHT', 'WIDTH', 'DEPTH'],
          allowsUsed: false,
          children: [],
        },
      ],
    },
  ];

  const borrador = (cambios: Record<string, unknown> = {}) => ({
    id: ID,
    sellerId: 'vendedor',
    status: 'DRAFT',
    product: {
      categoryId: 'hoja-camisas',
      title: 'Camisa de lino',
      description: 'Usada dos veces.',
      brand: null,
      condition: 'LIKE_NEW',
      size: { system: 'ALPHA', value: 'M' },
      measurements: { CHEST: 52 },
      color: 'BEIGE',
      price: { amount: 185000, currency: 'COP' },
      shipping: { weightGrams: 600, lengthCm: 30, widthCm: 20, heightCm: 10 },
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

  const conOchoTomas = () =>
    Array.from({ length: 8 }, (_, position) => ({
      id: `toma-${position}`,
      kind: 'SELLER_SHOT',
      position,
      angleDegrees: position * 45,
      url: `https://cdn.sendik.co/productos/${position}.jpg`,
    }));

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

  /**
   * Monta la página con —o sin— identificador en la ruta.
   *
   * <p>La ruta se suple con un {@code ActivatedRoute} de prueba en lugar de navegar de
   * verdad: lo único que la página lee de ella es el `paramMap`, y montar un enrutador
   * completo para eso metería en la prueba una pieza que no se está probando.
   *
   * <p>Va por un sujeto con valor inicial porque la página lee el parámetro de forma
   * síncrona al construirse: un observable sin valor la dejaría sin ruta.
   */
  const montar = async (publicacion: object | null) => {
    if (publicacion !== null) {
      parametros.next(convertToParamMap({ id: ID }));
    }

    const fixture = TestBed.createComponent(PublishPage);
    await fixture.whenStable();

    const backend = TestBed.inject(HttpTestingController);

    backend
      .expectOne((peticion) => peticion.method === 'GET' && peticion.url === `${API}/categories`)
      .flush(ARBOL);

    if (publicacion !== null) {
      backend
        .expectOne(
          (peticion) => peticion.method === 'GET' && peticion.url === `${API}/listings/${ID}`,
        )
        .flush(publicacion);
    }
    await asentar(fixture);

    return { fixture, backend };
  };

  const boton = (fixture: { nativeElement: HTMLElement }, texto: string) =>
    [...fixture.nativeElement.querySelectorAll('button')].find((candidato) =>
      candidato.textContent?.includes(texto),
    ) as HTMLButtonElement | undefined;

  let parametros: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

  beforeEach(() => {
    parametros = new BehaviorSubject(convertToParamMap({}));

    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'publicar/:id', component: PublishPage }]),
        { provide: ActivatedRoute, useValue: { paramMap: parametros.asObservable() } },
        provideHttpClient(
          withInterceptors([
            apiUrlInterceptor,
            authInterceptor,
            languageInterceptor,
            errorInterceptor,
          ]),
        ),
        provideHttpClientTesting(),
      ],
    });
    TestBed.inject(SessionStore).set(SESION);
  });

  /** Sin identificador es el paso previo: elegir categoría y crear el borrador. */
  it('pide la categoría antes de crear el borrador', async () => {
    const { fixture } = await montar(null);

    const texto = fixture.nativeElement.textContent ?? '';
    expect(texto).toContain('Publica tu producto');
    expect(fixture.nativeElement.querySelector('#categoria-nueva')).not.toBeNull();
    expect(boton(fixture, 'Empezar')?.disabled).toBe(true);
  });

  /** Criterio 19: en revisión no se edita, y lo que se ofrece es retirar. */
  it('bloquea el formulario y ofrece retirar mientras está en revisión', async () => {
    const { fixture } = await montar(
      borrador({ status: 'PENDING_REVIEW', images: conOchoTomas() }),
    );

    // Se mira el fieldset y no el input: `input.disabled` refleja solo su propio
    // atributo, no el del fieldset que lo envuelve, aunque el navegador sí lo
    // deshabilite de verdad.
    const bloques = [
      ...fixture.nativeElement.querySelectorAll('.publicar__bloque'),
    ] as HTMLFieldSetElement[];
    expect(bloques.length).toBeGreaterThan(0);
    expect(bloques.every((bloque) => bloque.disabled)).toBe(true);
    expect(boton(fixture, 'Retirar de revisión')).toBeDefined();
    expect(boton(fixture, 'Enviar a revisión')).toBeUndefined();
  });

  /** Criterio 22: el vendedor ve el motivo y la nota, y puede retomar. */
  it('muestra el motivo del rechazo y ofrece corregir', async () => {
    const { fixture } = await montar(
      borrador({
        status: 'REJECTED',
        rejectionReason: 'PHOTOS_UNUSABLE',
        rejectionNote: 'Se ven movidas.',
        images: conOchoTomas(),
      }),
    );

    const texto = fixture.nativeElement.textContent ?? '';
    expect(texto).toContain('No pudimos publicarla');
    expect(texto).toContain('Las fotos no se pueden usar');
    expect(texto).toContain('Se ven movidas.');
    expect(boton(fixture, 'Corregir y volver a enviar')).toBeDefined();
  });

  /** RN-016 y RN-017: sin las ocho tomas no se ofrece enviar. */
  it('no deja enviar sin las tomas completas', async () => {
    const { fixture } = await montar(borrador());

    expect(boton(fixture, 'Enviar a revisión')?.disabled).toBe(true);
  });

  it('deja enviar con las ocho tomas puestas', async () => {
    const { fixture } = await montar(borrador({ images: conOchoTomas() }));

    expect(boton(fixture, 'Enviar a revisión')?.disabled).toBe(false);
  });

  /** El plazo sale de configuración y nunca quemado en el texto. */
  it('promete el plazo de revisión que dice la configuración', async () => {
    const { fixture } = await montar(borrador());

    expect(fixture.nativeElement.textContent).toContain('2 días hábiles');
  });

  /**
   * Criterio 6: el servidor manda los campos que faltan y la pantalla los nombra.
   *
   * Sin esto la persona lee «faltan datos» y tiene que adivinar cuáles.
   */
  it('nombra los campos que el servidor dice que faltan', async () => {
    const { fixture, backend } = await montar(borrador({ images: conOchoTomas() }));

    boton(fixture, 'Enviar a revisión')?.click();
    await fixture.whenStable();

    backend
      .expectOne(
        (peticion) =>
          peticion.method === 'POST' && peticion.url === `${API}/listings/${ID}/submission`,
      )
      .flush(
        {
          code: 'CATALOG_LISTING_INCOMPLETE',
          errors: [
            { field: 'title', code: 'VALIDATION_REQUIRED' },
            { field: 'price', code: 'VALIDATION_REQUIRED' },
          ],
        },
        { status: 422, statusText: 'Unprocessable Content' },
      );
    await asentar(fixture);

    const texto = fixture.nativeElement.textContent ?? '';
    expect(texto).toContain('Faltan datos para enviarla a revisión');
    expect(texto).toContain('Título');
    expect(texto).toContain('Precio');
  });

  /** RN-064: en tecnología solo se ofrece «nuevo», y aparecen sellado y garantía. */
  it('ofrece solo «nuevo», sellado y garantía en tecnología', async () => {
    const { fixture } = await montar(
      borrador({
        product: { ...borrador().product, categoryId: 'hoja-celulares', condition: 'NEW' },
      }),
    );

    const texto = fixture.nativeElement.textContent ?? '';
    expect(texto).toContain('En esta categoría solo se publica lo nuevo');
    expect(texto).toContain('Está sellado, sin abrir');
    expect(texto).toContain('Meses de garantía del fabricante');
    expect(texto).not.toContain('Con detalles');
  });

  /** Criterio 27: editar una viva la devuelve a moderación, y hay que avisarlo antes. */
  it('avisa de que editar una publicada la devuelve a revisión', async () => {
    const { fixture } = await montar(borrador({ status: 'PUBLISHED', images: conOchoTomas() }));

    expect(fixture.nativeElement.textContent).toContain('vuelve a revisión');
  });

  /** Las categorías se ofrecen agrupadas por familia, con su nombre visible. */
  it('ofrece el árbol agrupado por familias', async () => {
    const { fixture } = await montar(null);

    const grupos = [...fixture.nativeElement.querySelectorAll('optgroup')] as HTMLElement[];
    expect(grupos.map((grupo) => grupo.getAttribute('label'))).toEqual([
      'Parte superior',
      'Tecnología',
    ]);
    expect(fixture.nativeElement.textContent).toContain('Camisas y blusas');
  });
});
