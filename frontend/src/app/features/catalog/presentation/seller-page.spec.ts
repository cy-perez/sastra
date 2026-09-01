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
import type { Session } from '../../../core/session/session';
import { SessionStore } from '../../../core/session/session.store';
import { SellerPage } from './seller-page';

/**
 * El perfil público del vendedor. HU-009, criterios 18 a 20.
 */
describe('SellerPage', () => {
  const API = 'https://api.pruebas.sendik.co/api/v1';
  const VENDEDOR = '01a04385-47b7-79c7-b3f2-62c03a8d4a99';

  const publicacion = (id: string, titulo: string) => ({
    id,
    sellerId: VENDEDOR,
    publishedAt: '2026-08-27T15:00:00Z',
    images: [
      { id: `${id}-0`, kind: 'SELLER_SHOT', position: 0, angleDegrees: 0, url: `/${id}.jpg` },
    ],
    product: {
      categoryId: 'camisas',
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
    parametros = new BehaviorSubject(convertToParamMap({ id: VENDEDOR }));

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

  const bombear = async (fixture: ComponentFixture<SellerPage>) => {
    for (let vuelta = 0; vuelta < 5; vuelta++) {
      await new Promise((listo) => setTimeout(listo, 0));
      fixture.detectChanges();
    }
  };

  const montar = async () => {
    const fixture = TestBed.createComponent(SellerPage);
    const backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    await bombear(fixture);
    return { fixture, backend };
  };

  /** Criterio 18: quién es y qué vende. */
  it('muestra el nombre, la insignia y sus publicaciones', async () => {
    const { fixture, backend } = await montar();

    backend
      .expectOne((llamada) => llamada.url === `${API}/sellers/${VENDEDOR}`)
      .flush({ id: VENDEDOR, name: 'Ana María', avatarUrl: null, verified: true });
    backend
      .expectOne((llamada) => llamada.url === `${API}/sellers/${VENDEDOR}/listings`)
      .flush({
        items: [publicacion('uno', 'Camisa de lino')],
        nextCursor: null,
        hasMore: false,
      });
    await bombear(fixture);

    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('Ana María');
    expect(texto).toContain('Camisa de lino');
    expect(fixture.nativeElement.querySelector('.insignia-verificado')).not.toBeNull();
    expect(texto).toContain('Sendik confirmó su identidad y su cuenta bancaria');
  });

  /**
   * Criterio 19: no sale nada personal.
   *
   * <p>Se comprueba de la única forma que tiene sentido: la respuesta no trae esos campos,
   * así que aunque el servidor los mandara la pantalla no tendría dónde pintarlos. Esta
   * prueba fija que no se agreguen.
   */
  it('no muestra datos personales del vendedor, criterio 19', async () => {
    const { fixture, backend } = await montar();

    backend
      .expectOne((llamada) => llamada.url === `${API}/sellers/${VENDEDOR}`)
      .flush({
        id: VENDEDOR,
        name: 'Ana María',
        avatarUrl: null,
        verified: true,
        // Lo que el servidor no manda, pero por si algún día lo mandara.
        email: 'ana@example.test',
        documentNumber: '1234567890',
      });
    backend
      .expectOne((llamada) => llamada.url === `${API}/sellers/${VENDEDOR}/listings`)
      .flush({ items: [], nextCursor: null, hasMore: false });
    await bombear(fixture);

    const texto = fixture.nativeElement.textContent;
    expect(texto).not.toContain('ana@example.test');
    expect(texto).not.toContain('1234567890');
  });

  /** Criterio 20: sin nada publicado se dice, y no es un error. */
  it('dice que no tiene nada publicado en vez de fallar, criterio 20', async () => {
    const { fixture, backend } = await montar();

    backend
      .expectOne((llamada) => llamada.url === `${API}/sellers/${VENDEDOR}`)
      .flush({ id: VENDEDOR, name: 'Ana María', avatarUrl: null, verified: false });
    backend
      .expectOne((llamada) => llamada.url === `${API}/sellers/${VENDEDOR}/listings`)
      .flush({ items: [], nextCursor: null, hasMore: false });
    await bombear(fixture);

    expect(fixture.nativeElement.textContent).toContain('no tiene nada publicado');
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
  });

  /** Criterio 19: no existe, no es de nadie y cuenta cerrada responden lo mismo. */
  it('dice que no encontró al vendedor cuando la API responde 404', async () => {
    const { fixture, backend } = await montar();

    backend
      .expectOne((llamada) => llamada.url === `${API}/sellers/${VENDEDOR}`)
      .flush({ code: 'COMMON_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    backend
      .expectOne((llamada) => llamada.url === `${API}/sellers/${VENDEDOR}/listings`)
      .flush({ items: [], nextCursor: null, hasMore: false });
    await bombear(fixture);

    expect(fixture.nativeElement.textContent).toContain('No encontramos a este vendedor');
  });

  // --- HU-010: revocar el sello ----------------------------------------------

  describe('revocar el sello', () => {
    const SESION_DE_MODERADORA: Session = {
      accessToken: 'un-token',
      user: {
        email: 'moderadora@sendik.co',
        displayName: 'Quien Modera',
        emailVerified: true,
        roles: ['MODERATOR'],
      },
    };

    const SESION_CUALQUIERA: Session = {
      ...SESION_DE_MODERADORA,
      user: { ...SESION_DE_MODERADORA.user, roles: ['BUYER'] },
    };

    const VERIFICACION = '01a04385-47b7-79c7-b3f2-62c03a8d4b11';

    const accion = (fixture: ComponentFixture<SellerPage>) =>
      Array.from(fixture.nativeElement.querySelectorAll('button')).find((boton) =>
        (boton as HTMLButtonElement).textContent?.includes('Revocar el sello'),
      ) as HTMLButtonElement | undefined;

    /**
     * Responde el perfil y sus publicaciones, y el sello **solo si alguien lo pidio**.
     *
     * <p>Que se pida o no es la mitad de lo que estas pruebas comprueban: para quien no
     * modera esa peticion no debe salir. Por eso se atiende cuando aparece, en vez de
     * exigirla, y se devuelve cuantas hubo.
     */
    const responder = async (
      fixture: ComponentFixture<SellerPage>,
      backend: HttpTestingController,
      sello: { readonly estado?: string; readonly noExiste?: boolean } = {},
    ) => {
      backend
        .expectOne((llamada) => llamada.url === API + '/sellers/' + VENDEDOR)
        .flush({ id: VENDEDOR, name: 'Ana Maria', avatarUrl: null, verified: true });
      backend
        .expectOne((llamada) => llamada.url === API + '/sellers/' + VENDEDOR + '/listings')
        .flush({ items: [], nextCursor: null, hasMore: false });
      await bombear(fixture);

      const pedidas = backend.match(
        (llamada) => llamada.url === API + '/verifications/by-seller/' + VENDEDOR,
      );

      for (const peticion of pedidas) {
        if (sello.noExiste === true) {
          peticion.flush({ code: 'COMMON_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
        } else {
          peticion.flush({ id: VERIFICACION, status: sello.estado ?? 'VERIFIED' });
        }
      }
      await bombear(fixture);

      return pedidas.length;
    };

    /** Criterio 10, y de paso: sin sesion ni siquiera se pregunta por el sello. */
    it('no existe para quien no tiene sesion, y no pregunta por el sello', async () => {
      const { fixture, backend } = await montar();
      const preguntas = await responder(fixture, backend);

      expect(preguntas).toBe(0);
      expect(accion(fixture)).toBeUndefined();
      expect(fixture.nativeElement.textContent).not.toContain('Revocar el sello');
    });

    it('no existe para una cuenta sin el rol', async () => {
      TestBed.inject(SessionStore).set(SESION_CUALQUIERA);

      const { fixture, backend } = await montar();
      const preguntas = await responder(fixture, backend);

      expect(preguntas).toBe(0);
      expect(accion(fixture)).toBeUndefined();
    });

    it('se ofrece a quien modera sobre alguien verificado', async () => {
      TestBed.inject(SessionStore).set(SESION_DE_MODERADORA);

      const { fixture, backend } = await montar();
      await responder(fixture, backend, { estado: 'VERIFIED' });

      expect(accion(fixture)).toBeDefined();
    });

    /** Criterio 11: sin sello no hay nada que quitar. */
    it('no se ofrece cuando la verificacion ya esta revocada', async () => {
      TestBed.inject(SessionStore).set(SESION_DE_MODERADORA);

      const { fixture, backend } = await montar();
      await responder(fixture, backend, { estado: 'REVOKED' });

      expect(accion(fixture)).toBeUndefined();
    });

    it('no se ofrece cuando esa persona nunca empezo la verificacion', async () => {
      TestBed.inject(SessionStore).set(SESION_DE_MODERADORA);

      const { fixture, backend } = await montar();
      await responder(fixture, backend, { noExiste: true });

      expect(accion(fixture)).toBeUndefined();
    });

    /**
     * Criterio 13: el aviso de RN-013 se lee antes de confirmar.
     *
     * <p>Es el criterio que mas facil se pierde en una refactorizacion, porque el aviso no
     * hace nada: solo se lee. Si desaparece, quien revoca cree que ya retiro lo que no
     * retiro.
     */
    it('avisa de que lo publicado sigue visible antes de confirmar', async () => {
      TestBed.inject(SessionStore).set(SESION_DE_MODERADORA);

      const { fixture, backend } = await montar();
      await responder(fixture, backend, { estado: 'VERIFIED' });

      accion(fixture)?.click();
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).toContain('sigue visible');
    });

    /** Criterio 12: el motivo sale de la lista de RN-069 y viaja a la ruta correcta. */
    it('manda el motivo de revocacion a la verificacion, no al vendedor', async () => {
      TestBed.inject(SessionStore).set(SESION_DE_MODERADORA);

      const { fixture, backend } = await montar();
      await responder(fixture, backend, { estado: 'VERIFIED' });

      accion(fixture)?.click();
      fixture.detectChanges();

      const motivo = fixture.nativeElement.querySelector('select') as HTMLSelectElement;
      motivo.value = 'DOCUMENT_NOT_ITS_HOLDER';
      motivo.dispatchEvent(new Event('change'));
      fixture.detectChanges();

      const confirmar = Array.from(fixture.nativeElement.querySelectorAll('button')).find((boton) =>
        (boton as HTMLButtonElement).textContent?.includes('Confirmar'),
      ) as HTMLButtonElement | undefined;

      confirmar?.click();
      await bombear(fixture);

      const peticion = backend.expectOne(
        (llamada) => llamada.url === API + '/verifications/' + VERIFICACION + '/revocation',
      );
      expect(peticion.request.method).toBe('POST');
      expect(peticion.request.body).toEqual({ reason: 'DOCUMENT_NOT_ITS_HOLDER', note: null });
      peticion.flush({});
    });

    /** Los motivos que se ofrecen son los cinco de RN-069, no los del rechazo. */
    it('no ofrece ningun motivo de la lista del rechazo', async () => {
      TestBed.inject(SessionStore).set(SESION_DE_MODERADORA);

      const { fixture, backend } = await montar();
      await responder(fixture, backend, { estado: 'VERIFIED' });

      accion(fixture)?.click();
      fixture.detectChanges();

      const valores = Array.from(fixture.nativeElement.querySelectorAll('option')).map(
        (opcion) => (opcion as HTMLOptionElement).value,
      );

      expect(valores).toContain('DOCUMENT_NOT_ITS_HOLDER');
      expect(valores).not.toContain('REQUIREMENTS_NOT_MET');
      expect(valores).not.toContain('ILLEGIBLE_PHOTOS');
    });
  });
});
