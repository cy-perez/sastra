import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';

import {
  apiUrlInterceptor,
  authInterceptor,
  errorInterceptor,
  languageInterceptor,
} from '../../../core/http/interceptors';
import type { Session } from '../../../core/session/session';
import { SessionStore } from '../../../core/session/session.store';
import { ReviewListingPage } from './review-listing-page';

/** El detalle donde se decide. HU-008, criterios 6, 7, 9, 10, 11, 12 y 13. */
describe('ReviewListingPage', () => {
  const API = 'https://api.pruebas.sendik.co/api/v1';
  const ID = 'una-publicacion';

  const SESION: Session = {
    accessToken: 'un-token',
    user: {
      email: 'moderadora@sendik.co',
      displayName: 'Quien Modera',
      emailVerified: true,
      roles: ['MODERATOR'],
    },
  };

  const toma = (posicion: number) => ({
    id: `toma-${posicion}`,
    kind: 'SELLER_SHOT',
    position: posicion,
    angleDegrees: posicion * 45,
    url: `https://cdn.sendik.co/${posicion}.jpg`,
  });

  const publicacion = (cambios: Record<string, unknown> = {}) => ({
    id: ID,
    sellerId: '0198f2aa-0000-7000-8000-000000000001',
    status: 'PENDING_REVIEW',
    product: {
      categoryId: 'camisas',
      title: 'Camisa de lino color hueso',
      description: 'Usada dos veces.',
      brand: 'Zara',
      condition: 'LIKE_NEW',
      size: { system: 'ALPHA', value: 'M' },
      measurements: { CHEST: 52, LENGTH: 70 },
      color: 'WHITE',
      price: { amount: 185000, currency: 'COP' },
      shipping: { weightGrams: 600, lengthCm: 30, widthCm: 20, heightCm: 10 },
      isSealed: false,
      warrantyMonths: null,
    },
    images: [0, 1, 2, 3, 4, 5, 6, 7].map(toma),
    requiredShots: 8,
    requiresAttention: false,
    attentionReasons: [],
    rejectionReason: null,
    rejectionNote: null,
    publishedAt: null,
    createdAt: '2026-08-20T09:00:00Z',
    updatedAt: '2026-08-20T10:00:00Z',
    version: 3,
    own: false,
    ...cambios,
  });

  /** El árbol de categorías, para el nombre que pide el criterio 7. */
  const ARBOL = [
    {
      id: 'moda',
      slug: 'moda',
      nameEs: 'Moda',
      nameEn: 'Fashion',
      familySlug: null,
      sizeSystems: [],
      requiredMeasurements: [],
      allowsUsed: true,
      children: [
        {
          id: 'camisas',
          slug: 'camisas-y-blusas',
          nameEs: 'Camisas y blusas',
          nameEn: 'Shirts and blouses',
          familySlug: 'moda',
          sizeSystems: ['ALPHA'],
          requiredMeasurements: ['CHEST', 'LENGTH'],
          allowsUsed: true,
          children: [],
        },
      ],
    },
  ];

  const fila = (cambios: Record<string, unknown> = {}) => ({
    id: ID,
    title: 'Camisa de lino color hueso',
    price: { amount: 185000, currency: 'COP' },
    waitingSince: '2026-08-20T10:00:00Z',
    requiresAttention: false,
    attentionReasons: [],
    coverUrl: 'https://cdn.sendik.co/0.jpg',
    own: false,
    ...cambios,
  });

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
   * Deja pasar unos turnos y vuelve a pintar, sin esperar la estabilidad.
   *
   * <p>`whenStable` no sirve cuando queda alguna peticion sin responder: Angular la
   * cuenta como tarea pendiente y la espera no termina hasta que la prueba conteste. Es
   * justo lo que pasa despues de pulsar «Confirmar», que dispara el POST de la decision.
   */
  const bombear = async (fixture: { detectChanges: () => void }) => {
    for (let vuelta = 0; vuelta < 5; vuelta++) {
      await new Promise((listo) => setTimeout(listo, 0));
      fixture.detectChanges();
    }
  };

  beforeEach(() => {
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
      ],
    });
    TestBed.inject(SessionStore).set(SESION);
  });

  /**
   * Monta el detalle.
   *
   * <p>La cola se responde solo si la pide alguien: el detalle **no** la necesita para
   * pintarse —esa es la diferencia con HU-006— pero sí para saber si la publicación es
   * propia. Se atiende cuando aparece, sin exigirla.
   */
  const montar = async (opciones: { detalle?: unknown; propia?: boolean } = {}) => {
    const fixture = TestBed.createComponent(ReviewListingPage);
    fixture.componentRef.setInput('id', ID);
    await fixture.whenStable();

    const backend = TestBed.inject(HttpTestingController);

    // La cola y el árbol de categorías se atienden si alguien los pide, sin exigirlos: el
    // detalle no depende de ninguno para pintarse.
    backend
      .match((p) => p.method === 'GET' && p.url === `${API}/moderation/listings`)
      .forEach((p) => p.flush({ items: [fila()], page: 0, size: 20 }));

    backend
      .match((p) => p.method === 'GET' && p.url === `${API}/categories`)
      .forEach((p) => p.flush(ARBOL));

    backend
      .expectOne((p) => p.method === 'GET' && p.url === `${API}/listings/${ID}`)
      .flush(opciones.detalle ?? publicacion({ own: opciones.propia === true }));

    await asentar(fixture);
    return fixture;
  };

  const botonQueDice = (fixture: { nativeElement: HTMLElement }, texto: string) =>
    [...fixture.nativeElement.querySelectorAll('button')].find((b: Element) =>
      b.textContent?.includes(texto),
    ) as HTMLButtonElement | undefined;

  /** Criterio 7: lo que hace falta para decidir está a la vista. */
  it('muestra los datos del producto y sus ocho tomas', async () => {
    const fixture = await montar();

    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('Camisa de lino color hueso');
    expect(texto).toContain('Usada dos veces.');
    expect(texto).toContain('Zara');
    expect(fixture.nativeElement.querySelectorAll('.toma')).toHaveLength(8);
  });

  it('muestra las medidas declaradas', async () => {
    const fixture = await montar();

    expect(
      fixture.nativeElement.querySelectorAll('.datos-de-la-publicacion dd').length,
    ).toBeGreaterThan(2);
    expect(fixture.nativeElement.textContent).toContain('52');
  });

  /**
   * Criterio 6: al moderador sí se le dice el motivo.
   *
   * <p>Es lo contrario que en la lista del vendedor, que solo dice que la publicación
   * necesita atención: anunciarle el motivo antes de que nadie la mire lo invita a
   * cambiar el precio para esquivar la revisión.
   */
  it('dice por qué la publicación necesita atención', async () => {
    const fixture = await montar({
      detalle: publicacion({
        requiresAttention: true,
        attentionReasons: ['PRICE_OUT_OF_RANGE', 'GALLERY_UPLOAD'],
      }),
    });

    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('El precio está fuera del rango habitual');
    expect(texto).toContain('se cargó desde la galería');
  });

  /** Caso borde: el archivo puede faltar, y la publicación sigue siendo decidible. */
  it('dice qué toma no está disponible sin descolocar la rejilla', async () => {
    const sinLaTercera = publicacion({
      images: [0, 1, 3, 4, 5, 6, 7].map(toma),
    });
    const fixture = await montar({ detalle: sinLaTercera });

    expect(fixture.nativeElement.querySelectorAll('.toma')).toHaveLength(8);
    expect(fixture.nativeElement.textContent).toContain('Esta toma no está disponible');
  });

  /** Criterio 9: sin motivo elegido, la acción no se envía. */
  it('no rechaza sin motivo y lo dice junto al campo', async () => {
    const fixture = await montar();

    botonQueDice(fixture, 'Rechazar')?.click();
    await asentar(fixture);

    expect(fixture.nativeElement.textContent).toContain('Elige un motivo para rechazar');
    TestBed.inject(HttpTestingController).expectNone(
      (p) => p.url === `${API}/listings/${ID}/rejection`,
    );
  });

  const elegirMotivo = async (
    fixture: {
      nativeElement: HTMLElement;
      detectChanges: () => void;
      whenStable: () => Promise<unknown>;
    },
    valor: string,
  ) => {
    const selector = fixture.nativeElement.querySelector('#motivo') as HTMLSelectElement;
    selector.value = valor;
    selector.dispatchEvent(new Event('change'));
    await asentar(fixture);
  };

  /** Criterio 10: aprobar y rechazar se confirman una vez. */
  it('pide confirmación antes de aprobar', async () => {
    const fixture = await montar();

    botonQueDice(fixture, 'Aprobar')?.click();
    await asentar(fixture);

    expect(fixture.nativeElement.textContent).toContain('¿Aprobar esta publicación?');
    TestBed.inject(HttpTestingController).expectNone(
      (p) => p.url === `${API}/listings/${ID}/approval`,
    );
  });

  it('aprueba solo al confirmar', async () => {
    const fixture = await montar();

    botonQueDice(fixture, 'Aprobar')?.click();
    await asentar(fixture);
    botonQueDice(fixture, 'Confirmar')?.click();
    await bombear(fixture);

    TestBed.inject(HttpTestingController)
      .expectOne((p) => p.method === 'POST' && p.url === `${API}/listings/${ID}/approval`)
      .flush({});
  });

  it('rechaza con el motivo elegido y la nota', async () => {
    const fixture = await montar();

    await elegirMotivo(fixture, 'PHOTOS_UNUSABLE');

    const nota = fixture.nativeElement.querySelector('#nota') as HTMLTextAreaElement;
    nota.value = '  La frontal está borrosa.  ';
    nota.dispatchEvent(new Event('input'));
    await asentar(fixture);

    botonQueDice(fixture, 'Rechazar')?.click();
    await asentar(fixture);
    botonQueDice(fixture, 'Confirmar')?.click();
    await bombear(fixture);

    const peticion = TestBed.inject(HttpTestingController).expectOne(
      (p) => p.method === 'POST' && p.url === `${API}/listings/${ID}/rejection`,
    );
    expect(peticion.request.body).toEqual({
      reason: 'PHOTOS_UNUSABLE',
      note: 'La frontal está borrosa.',
    });
    peticion.flush({});
  });

  /** La nota vacía viaja como nula, no como cadena en blanco. */
  it('manda la nota nula cuando no se escribió ninguna', async () => {
    const fixture = await montar();

    await elegirMotivo(fixture, 'PROHIBITED_ITEM');
    botonQueDice(fixture, 'Rechazar')?.click();
    await asentar(fixture);
    botonQueDice(fixture, 'Confirmar')?.click();
    await bombear(fixture);

    const peticion = TestBed.inject(HttpTestingController).expectOne(
      (p) => p.method === 'POST' && p.url === `${API}/listings/${ID}/rejection`,
    );
    expect(peticion.request.body).toEqual({ reason: 'PROHIBITED_ITEM', note: null });
    peticion.flush({});
  });

  /**
   * Criterios 11 y 13: se dice qué pasó, no «error inesperado».
   *
   * <p>El mismo texto cubre que otra persona decidiera antes y que el vendedor la
   * retirara: al moderador le pasa lo mismo en los dos casos, ya no le toca.
   */
  it('dice que ya no está en revisión cuando otra persona decidió antes', async () => {
    const fixture = await montar();

    botonQueDice(fixture, 'Aprobar')?.click();
    await asentar(fixture);
    botonQueDice(fixture, 'Confirmar')?.click();
    await bombear(fixture);

    const backend = TestBed.inject(HttpTestingController);
    backend
      .expectOne((p) => p.url === `${API}/listings/${ID}/approval`)
      .flush({ code: 'CATALOG_LISTING_INVALID_STATE' }, { status: 409, statusText: 'Conflict' });
    await bombear(fixture);

    expect(fixture.nativeElement.textContent).toContain('ya no está en revisión');
  });

  /**
   * Criterio 11, la mitad que se olvida: «…y la bandeja se refresca».
   *
   * <p>Se comprueba que sale una petición nueva de la cola, no solo que aparezca el
   * mensaje: sin esto, `refrescarSiYaNoEstaPendiente` era borrable con la suite en verde y
   * quien revisa volvía a abrir la siguiente fila fantasma.
   */
  it('vuelve a pedir la cola cuando la publicación ya no está pendiente', async () => {
    const fixture = await montar();
    const backend = TestBed.inject(HttpTestingController);

    // Se atiende la cola que pidió el montaje, para que la de después sea la nueva.
    backend
      .match((p) => p.method === 'GET' && p.url === `${API}/moderation/listings`)
      .forEach((p) => p.flush({ items: [fila()], page: 0, size: 20 }));

    botonQueDice(fixture, 'Aprobar')?.click();
    await asentar(fixture);
    botonQueDice(fixture, 'Confirmar')?.click();
    await bombear(fixture);

    backend
      .expectOne((p) => p.url === `${API}/listings/${ID}/approval`)
      .flush({ code: 'CATALOG_LISTING_INVALID_STATE' }, { status: 409, statusText: 'Conflict' });
    await bombear(fixture);

    expect(
      backend.match((p) => p.method === 'GET' && p.url === `${API}/moderation/listings`).length,
    ).toBeGreaterThan(0);
  });

  /** El tope de la nota es el mismo que valida el servidor. */
  it('no deja escribir más de 500 caracteres en la nota', async () => {
    const fixture = await montar();

    const nota = fixture.nativeElement.querySelector('#nota') as HTMLTextAreaElement;
    expect(nota.getAttribute('maxlength')).toBe('500');
  });

  /**
   * Criterio 12 y RN-063: sobre lo propio no se decide, y se dice antes de intentarlo.
   *
   * <p>El servidor lo rechaza igual; esconder los botones no es la regla, pero enterarse
   * después de pulsar, con un correo ya prometido, no hace falta.
   */
  it('no ofrece decidir sobre una publicación propia', async () => {
    const fixture = await montar({ propia: true });

    expect(fixture.nativeElement.textContent).toContain('Esta publicación es tuya');
    expect(botonQueDice(fixture, 'Aprobar')).toBeUndefined();
    expect(botonQueDice(fixture, 'Rechazar')).toBeUndefined();
  });

  /** Cancelar no decide nada y devuelve a la pantalla como estaba. */
  it('vuelve atrás al cancelar la confirmación', async () => {
    const fixture = await montar();

    botonQueDice(fixture, 'Aprobar')?.click();
    await asentar(fixture);
    botonQueDice(fixture, 'Cancelar')?.click();
    await asentar(fixture);

    expect(fixture.nativeElement.textContent).not.toContain('¿Aprobar esta publicación?');
    expect(botonQueDice(fixture, 'Aprobar')).toBeDefined();
    TestBed.inject(HttpTestingController).expectNone(
      (p) => p.url === `${API}/listings/${ID}/approval`,
    );
  });

  /**
   * Recargar con la dirección directa tiene que funcionar sin pasar por la lista.
   *
   * <p>Es la diferencia con HU-006: allí el detalle salía de la bandeja ya cargada. Aquí
   * la publicación se pide por su identificador, así que la pantalla se pinta aunque la
   * cola no haya llegado nunca.
   */
  it('se pinta aunque la cola no esté cargada', async () => {
    const fixture = TestBed.createComponent(ReviewListingPage);
    fixture.componentRef.setInput('id', ID);
    await fixture.whenStable();

    const backend = TestBed.inject(HttpTestingController);
    backend
      .expectOne((p) => p.method === 'GET' && p.url === `${API}/listings/${ID}`)
      .flush(publicacion());
    await asentar(fixture);

    expect(fixture.nativeElement.textContent).toContain('Camisa de lino color hueso');
    expect(botonQueDice(fixture, 'Aprobar')).toBeDefined();
  });

  /**
   * Criterio 12 por la dirección directa. **Es el defecto que encontraron tres
   * revisiones**: `own` salía de la fila de la cola, y sin cola cargada quedaba en falso,
   * así que sobre su propia publicación el moderador veía los dos botones.
   *
   * <p>Ahora `own` viaja en la publicación, así que la cola no hace falta para saberlo.
   */
  it('no ofrece decidir sobre lo propio aunque se entre por la dirección directa', async () => {
    const fixture = TestBed.createComponent(ReviewListingPage);
    fixture.componentRef.setInput('id', ID);
    await fixture.whenStable();

    TestBed.inject(HttpTestingController)
      .expectOne((p) => p.method === 'GET' && p.url === `${API}/listings/${ID}`)
      .flush(publicacion({ own: true }));
    await asentar(fixture);

    expect(fixture.nativeElement.textContent).toContain('Esta publicación es tuya');
    expect(botonQueDice(fixture, 'Aprobar')).toBeUndefined();
    expect(botonQueDice(fixture, 'Rechazar')).toBeUndefined();
  });

  /** Criterio 7: la categoría, resuelta contra el árbol. */
  it('muestra el nombre de la categoría y no su identificador', async () => {
    const fixture = await montar();

    expect(fixture.nativeElement.textContent).toContain('Camisas y blusas');
  });

  /**
   * La sesión llega **después** de crear el componente, como en una carga real.
   *
   * <p>Es la trampa que dejó `/mi-cuenta` sin cargarse nunca: `enabled` se evalúa fuera del
   * ámbito reactivo, así que una señal leída en el sitio equivocado nace deshabilitada y no
   * se reactiva. `frontend/CLAUDE.md` la exige y HU-006 la tiene; aquí faltaba.
   */
  it('pide la publicación aunque la sesión llegue después de crear el componente', async () => {
    TestBed.inject(SessionStore).clear();

    const fixture = TestBed.createComponent(ReviewListingPage);
    fixture.componentRef.setInput('id', ID);
    await fixture.whenStable();

    TestBed.inject(SessionStore).set(SESION);
    // Un turno, sin esperar estabilidad: la peticion que acaba de nacer sigue viva y
    // Angular la cuenta como tarea pendiente.
    await new Promise((listo) => setTimeout(listo, 0));
    fixture.detectChanges();

    TestBed.inject(HttpTestingController)
      .expectOne((p) => p.method === 'GET' && p.url === `${API}/listings/${ID}`)
      .flush(publicacion());
    for (let vuelta = 0; vuelta < 5; vuelta++) {
      await new Promise((listo) => setTimeout(listo, 0));
      fixture.detectChanges();
    }

    expect(fixture.nativeElement.textContent).toContain('Camisa de lino color hueso');
  });

  /** El error del detalle también ofrece reintentar: quien llegó directo no tiene la fila. */
  it('ofrece reintentar cuando la publicación no carga', async () => {
    const fixture = TestBed.createComponent(ReviewListingPage);
    fixture.componentRef.setInput('id', ID);
    await fixture.whenStable();

    TestBed.inject(HttpTestingController)
      .expectOne((p) => p.method === 'GET' && p.url === `${API}/listings/${ID}`)
      .flush({ code: 'COMMON_UNEXPECTED' }, { status: 500, statusText: 'Error' });
    await asentar(fixture);

    expect(fixture.nativeElement.textContent).toContain('Reintentar');
    // Y con encabezado: la página tenía h1 solo en uno de sus tres estados.
    expect(fixture.nativeElement.querySelector('h1')).not.toBeNull();
  });

  /** El foco vuelve al botón que abrió la confirmación, también cuando la decisión falla. */
  it('devuelve el foco al cancelar en vez de dejarlo en el cuerpo del documento', async () => {
    const fixture = await montar();

    botonQueDice(fixture, 'Aprobar')?.click();
    await asentar(fixture);
    botonQueDice(fixture, 'Cancelar')?.click();
    await asentar(fixture);

    expect(document.activeElement).toBe(botonQueDice(fixture, 'Aprobar'));
  });
});
