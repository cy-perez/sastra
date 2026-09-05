import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import {
  apiUrlInterceptor,
  authInterceptor,
  errorInterceptor,
  languageInterceptor,
} from '../../../core/http/interceptors';
import type { Session } from '../../../core/session/session';
import { SessionStore } from '../../../core/session/session.store';
import { ModerationTrail } from './moderation-trail';

/**
 * El rastro de moderación. HU-013.
 *
 * <p>Lo que se prueba es lo que ve quien vende: que cada paso se entienda **con palabras**
 * y no por el color, que una acción que esta versión no conoce no rompa la lista ni
 * desaparezca de ella, que el vacío se diga, y que el error se quede dentro de este bloque.
 */
describe('ModerationTrail', () => {
  const API = 'https://api.pruebas.sendik.co/api/v1';
  const ID = 'af8b9a52-4a3f-4a52-9a1e-8d9a2f1c4b70';

  const SESION: Session = {
    accessToken: 'un-token',
    user: { email: 'ana@correo.co', displayName: 'Ana Maria', emailVerified: true, roles: [] },
  };

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

  const bombear = async (fixture: { detectChanges: () => void }) => {
    for (let vuelta = 0; vuelta < 5; vuelta++) {
      await new Promise((listo) => setTimeout(listo, 0));
      fixture.detectChanges();
    }
  };

  /** Monta el bloque plegado, que es como nace. */
  const montar = async () => {
    TestBed.inject(SessionStore).set(SESION);

    const fixture = TestBed.createComponent(ModerationTrail);
    fixture.componentRef.setInput('publicacion', ID);
    fixture.detectChanges();
    await fixture.whenStable();

    return { fixture, backend: TestBed.inject(HttpTestingController) };
  };

  /**
   * Por nombre accesible y no por `querySelector('button')`: es lo que pide
   * frontend/CLAUDE.md, y de paso distingue el interruptor del botón de reintentar.
   */
  const boton = (fixture: { nativeElement: HTMLElement }, texto: string) =>
    [...fixture.nativeElement.querySelectorAll('button')].find((candidato) =>
      candidato.textContent?.includes(texto),
    ) as HTMLButtonElement;

  /** El interruptor, se llame como se llame según esté plegado o desplegado. */
  const interruptor = (fixture: { nativeElement: HTMLElement }) =>
    boton(fixture, 'Ver qué ha pasado') ?? boton(fixture, 'Ocultar lo que ha pasado');

  /**
   * Lo despliega y responde la consulta.
   *
   * <p>Con `eventos` en nulo la deja en vuelo, que es lo que necesita la prueba del estado
   * de carga; en ese caso no se puede usar `asentar`, porque `whenStable` no vuelve mientras
   * quede una petición sin responder.
   */
  const desplegar = async (
    contexto: Awaited<ReturnType<typeof montar>>,
    eventos: object[] | null,
  ) => {
    interruptor(contexto.fixture).click();
    await bombear(contexto.fixture);

    const peticion = contexto.backend.expectOne(
      (llamada) =>
        llamada.method === 'GET' && llamada.url === `${API}/listings/${ID}/moderation-history`,
    );

    if (eventos === null) {
      await bombear(contexto.fixture);
      return peticion;
    }

    peticion.flush({ events: eventos });
    await asentar(contexto.fixture);
    return peticion;
  };

  /** El texto de cada paso, por rol: el marcado ya es un `<ol><li>`. */
  const pasos = (fixture: { nativeElement: HTMLElement }) =>
    [...fixture.nativeElement.querySelectorAll('ol li')].map((paso) =>
      (paso.textContent ?? '').replace(/\s+/g, ' ').trim(),
    );

  const texto = (fixture: { nativeElement: HTMLElement }) =>
    (fixture.nativeElement.textContent ?? '').replace(/\s+/g, ' ');

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
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
  });

  // --- Plegado, y no pide nada hasta que se abre ---------------------------

  /**
   * `/mis-publicaciones` pinta hasta veinte filas. Si cada rastro pidiera al nacer, abrir
   * esa pantalla serían veinte peticiones que nadie pidió.
   */
  it('no deberia pedir nada mientras esta plegado', async () => {
    const { fixture, backend } = await montar();

    backend.expectNone((llamada) => llamada.url === `${API}/listings/${ID}/moderation-history`);
    expect(interruptor(fixture).getAttribute('aria-expanded')).toBe('false');
  });

  it('deberia anunciar que esta desplegado al abrirlo', async () => {
    const contexto = await montar();
    await desplegar(contexto, []);

    expect(interruptor(contexto.fixture).getAttribute('aria-expanded')).toBe('true');

    // Lo que el boton dice controlar existe de verdad. Sin esto, `aria-controls` apunta a un
    // identificador que no esta en el documento y el anuncio no lleva a ninguna parte.
    const controlado = interruptor(contexto.fixture).getAttribute('aria-controls');
    expect(controlado).not.toBeNull();
    expect(contexto.fixture.nativeElement.querySelector(`#${controlado}`)).not.toBeNull();
  });

  /**
   * Plegado no controla nada, y por eso no lo declara: `aria-controls` apuntaria a un
   * identificador que no esta en el documento, que es una referencia rota. axe lo devuelve
   * como incompleto y no como violacion, asi que la auditoria no lo caza.
   */
  it('no deberia declarar aria-controls mientras esta plegado', async () => {
    const { fixture } = await montar();

    expect(interruptor(fixture).getAttribute('aria-controls')).toBeNull();
  });

  // --- Criterios 1 a 3: las acciones, con texto ----------------------------

  /**
   * WCAG 1.4.1: un rechazo no puede distinguirse de una aprobación por el color. Cada paso
   * lo dice con palabras, y por eso se afirma sobre el texto y no sobre una clase.
   */
  it('deberia contar cada accion con palabras y no solo con color', async () => {
    const contexto = await montar();
    await desplegar(contexto, [
      { action: 'APPROVED', reason: null, occurredAt: '2026-09-04T18:12:03Z' },
      { action: 'REJECTED', reason: 'PHOTOS_UNUSABLE', occurredAt: '2026-09-03T09:05:22Z' },
      { action: 'SUBMITTED', reason: null, occurredAt: '2026-09-02T21:33:47Z' },
    ]);

    const pintados = pasos(contexto.fixture);
    expect(pintados).toHaveLength(3);
    expect(pintados[0]).toContain('Se aprobó y quedó publicada');
    expect(pintados[1]).toContain('Se rechazó');
    expect(pintados[2]).toContain('La enviaste a revisión');
  });

  /** Criterio 1: el motivo del rechazo, traducido con la lista de RN-022. */
  it('deberia traducir el motivo de un rechazo', async () => {
    const contexto = await montar();
    await desplegar(contexto, [
      { action: 'REJECTED', reason: 'PHOTOS_UNUSABLE', occurredAt: '2026-09-03T09:05:22Z' },
    ]);

    expect(pasos(contexto.fixture)[0]).toContain('Motivo');
    expect(contexto.fixture.nativeElement.textContent).not.toContain('PHOTOS_UNUSABLE');
  });

  /** Criterio 2: aprobar no lleva motivo, y no se le inventa uno. */
  it('no deberia pintar motivo donde no lo hay', async () => {
    const contexto = await montar();
    await desplegar(contexto, [
      { action: 'APPROVED', reason: null, occurredAt: '2026-09-04T18:12:03Z' },
    ]);

    expect(texto(contexto.fixture)).not.toContain('Motivo');
  });

  /**
   * El caso borde: una fila sin motivo donde debería haberlo se pinta igual. Es lo que
   * evita que una fila vieja o torcida deje al vendedor sin ver el resto de su rastro.
   */
  it('deberia pintar un rechazo sin motivo en vez de esconderlo', async () => {
    const contexto = await montar();
    await desplegar(contexto, [
      { action: 'REJECTED', reason: null, occurredAt: '2026-09-03T09:05:22Z' },
    ]);

    expect(pasos(contexto.fixture)).toHaveLength(1);
    expect(pasos(contexto.fixture)[0]).toContain('Se rechazó');
  });

  /**
   * El caso borde de la historia. Una acción que esta versión no conoce **se pinta**, con
   * su fecha y una descripción genérica: omitir la fila escondería que algo pasó, que es lo
   * único que este rastro existe para no hacer.
   *
   * <p>Es lo contrario de lo que hacen las cifras del panel, que descartan un estado
   * desconocido porque una cifra sin nombre no se puede explicar.
   */
  it('deberia pintar una accion desconocida sin romper la lista', async () => {
    const contexto = await montar();
    await desplegar(contexto, [
      { action: 'ESCALATED', reason: null, occurredAt: '2026-09-04T18:12:03Z' },
      { action: 'SUBMITTED', reason: null, occurredAt: '2026-09-02T21:33:47Z' },
    ]);

    const pintados = pasos(contexto.fixture);
    expect(pintados).toHaveLength(2);
    expect(pintados[0]).toContain('Hubo un cambio en esta publicación');
    expect(pintados[0]).not.toContain('ESCALATED');
    expect(pintados[1]).toContain('La enviaste a revisión');
  });

  // --- Criterio 4: las dos vueltas ----------------------------------------

  /**
   * Rechazada, corregida y reenviada: se ven las dos vueltas y en orden, no solo la última.
   * Es la razón por la que el envío se anota como evento.
   */
  it('deberia ensenar las dos vueltas en orden y no solo la ultima', async () => {
    const contexto = await montar();
    await desplegar(contexto, [
      { action: 'APPROVED', reason: null, occurredAt: '2026-09-04T18:12:03Z' },
      { action: 'SUBMITTED', reason: null, occurredAt: '2026-09-04T17:40:11Z' },
      { action: 'REJECTED', reason: 'PHOTOS_UNUSABLE', occurredAt: '2026-09-03T09:05:22Z' },
      { action: 'SUBMITTED', reason: null, occurredAt: '2026-09-02T21:33:47Z' },
    ]);

    const pintados = pasos(contexto.fixture);
    expect(pintados).toHaveLength(4);
    expect(pintados[0]).toContain('Se aprobó');
    expect(pintados[3]).toContain('La enviaste');
  });

  // --- Criterio 9: la fecha -----------------------------------------------

  /**
   * En la zona y el formato de quien mira, no en UTC crudo: «2026-09-04T18:12:03Z» no le
   * dice a nadie qué día pasó eso. El instante original se conserva en `datetime`, que es
   * lo que una máquina interpreta sin ambigüedad.
   */
  it('deberia fechar cada paso en la configuracion regional activa', async () => {
    const contexto = await montar();
    await desplegar(contexto, [
      { action: 'APPROVED', reason: null, occurredAt: '2026-09-04T18:12:03Z' },
    ]);

    const fecha = contexto.fixture.nativeElement.querySelector('time') as HTMLTimeElement;

    // El instante original, sin ambigüedad, para quien lo lea con una máquina.
    expect(fecha.getAttribute('datetime')).toBe('2026-09-04T18:12:03Z');

    // Y el texto, con el formato y el idioma activos. **Se afirma el valor exacto**: la
    // versión anterior solo comprobaba que no fuera la cadena ISO y que no estuviera vacía,
    // y eso lo cumple igual `new Date(x).toString()`, o UTC crudo formateado, o la
    // configuración regional equivocada. Con el valor puesto, cambiar cualquiera de las tres
    // cosas rompe la prueba.
    const esperada = new Intl.DateTimeFormat('es', {
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(new Date('2026-09-04T18:12:03Z'));

    expect(fecha.textContent?.trim()).toBe(esperada);

    // Y no es la cadena cruda ni el `toString()` del navegador, que es lo que sale si alguien
    // quita el `Intl` de en medio.
    expect(fecha.textContent).not.toContain('2026-09-04T18:12:03Z');
    expect(fecha.textContent).not.toContain('GMT');
  });

  // --- Criterio 5: nunca quien decidio -------------------------------------

  /**
   * No hay campo que pintar, así que esto no puede fallar por la plantilla; lo que fija es
   * que nadie agregue uno el día que el servidor empiece a mandarlo por error.
   */
  it('no deberia pintar nunca quien decidio ni la nota interna', async () => {
    const contexto = await montar();
    await desplegar(contexto, [
      {
        action: 'REJECTED',
        reason: 'PHOTOS_UNUSABLE',
        occurredAt: '2026-09-03T09:05:22Z',
        // El servidor no manda esto. Si algun dia lo hiciera, la pantalla lo ignora.
        actorId: '9d20fb6b-ab91-485a-8fcf-423b93cf68fa',
        notes: 'sospecha de replica, avisar a soporte',
      },
    ]);

    const texto = contexto.fixture.nativeElement.textContent ?? '';
    expect(texto).not.toContain('9d20fb6b');
    expect(texto).not.toContain('sospecha de replica');
  });

  // --- Los cuatro estados del bloque ---------------------------------------

  /** Criterio 6: se dice que no ha pasado nada; no se pinta una lista vacía. */
  it('deberia decir que todavia no ha pasado nada en vez de pintar una lista vacia', async () => {
    const contexto = await montar();
    await desplegar(contexto, []);

    expect(contexto.fixture.nativeElement.querySelector('ol')).toBeNull();
    expect(contexto.fixture.nativeElement.textContent).toContain('Todavía no ha pasado nada');
  });

  /**
   * La región viva es **permanente** y vive fuera del desplegable, no un `role="status"` que
   * nace con el texto ya dentro: eso no se anuncia de forma fiable, que es la regla que ya
   * estaba escrita en las dos pantallas donde se monta esto.
   */
  it('deberia anunciar la carga por la region viva permanente', async () => {
    const contexto = await montar();

    // Existe desde antes de desplegar, que es lo que la hace funcionar.
    const viva = contexto.fixture.nativeElement.querySelector('[role="status"]');
    expect(viva).not.toBeNull();
    expect(viva?.textContent?.trim()).toBe('');

    await desplegar(contexto, null);

    expect(viva?.textContent).toContain('Cargando el rastro');

    // Y lo visual es el esqueleto del sistema, escondido a quien no ve la pantalla.
    const esqueleto = contexto.fixture.nativeElement.querySelector('.esqueleto');
    expect(esqueleto).not.toBeNull();
    expect(esqueleto?.closest('[aria-hidden="true"]')).not.toBeNull();
  });

  /** Y al terminar lo dice, que es lo que faltaba: vaciar la región no anuncia nada. */
  it('deberia anunciar tambien que termino de cargar', async () => {
    const contexto = await montar();
    await desplegar(contexto, [
      { action: 'SUBMITTED', reason: null, occurredAt: '2026-09-02T21:33:47Z' },
    ]);

    expect(contexto.fixture.nativeElement.querySelector('[role="status"]')?.textContent).toContain(
      'Rastro al día',
    );
  });

  /**
   * El error se queda dentro de este bloque. Es lo mismo que decidió HU-012 para las
   * cifras: una publicación que no se ve porque su rastro falló es una publicación que el
   * vendedor no puede retomar.
   */
  it('deberia acotar el error a este bloque y ofrecer reintentar', async () => {
    const contexto = await montar();
    const peticion = await desplegar(contexto, null);

    peticion.flush({ code: 'COMMON_UNEXPECTED' }, { status: 500, statusText: 'Server Error' });
    await asentar(contexto.fixture);

    // El mensaje, no solo que exista un hueco donde ponerlo.
    expect(texto(contexto.fixture)).toContain('Algo salió mal de nuestro lado');
    expect(boton(contexto.fixture, 'Reintentar')).toBeDefined();

    // **Sin `role="alert"`**: el error viaja por la región viva permanente, y con los dos se
    // anunciaba dos veces. Es la misma corrección que la fila de cifras de HU-012.
    expect(contexto.fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
  });

  /**
   * Y el reintento vuelve a pedir. Sin esto, el botón está pero no hace nada, que es peor
   * que no ofrecerlo.
   */
  it('deberia volver a pedir el rastro al reintentar', async () => {
    const contexto = await montar();
    const peticion = await desplegar(contexto, null);
    peticion.flush({ code: 'COMMON_UNEXPECTED' }, { status: 500, statusText: 'Server Error' });
    await asentar(contexto.fixture);

    boton(contexto.fixture, 'Reintentar').click();
    await bombear(contexto.fixture);

    contexto.backend
      .expectOne(
        (llamada) =>
          llamada.method === 'GET' && llamada.url === `${API}/listings/${ID}/moderation-history`,
      )
      .flush({
        events: [{ action: 'SUBMITTED', reason: null, occurredAt: '2026-09-02T21:33:47Z' }],
      });
    await asentar(contexto.fixture);

    expect(pasos(contexto.fixture)[0]).toContain('La enviaste a revisión');
  });

  /**
   * Criterio 3: el retiro de RN-024, que es el evento que más le duele a quien vende y el
   * único cuya clave de traducción no ejercitaba nada. Si `listing.trail.action.ARCHIVED`
   * faltara o estuviera mal escrita, la pantalla pintaba la clave cruda y la suite seguía
   * verde.
   */
  it('deberia contar el retiro del catalogo con su motivo', async () => {
    const contexto = await montar();
    await desplegar(contexto, [
      { action: 'ARCHIVED', reason: 'PROHIBITED_ITEM', occurredAt: '2026-09-04T18:12:03Z' },
    ]);

    expect(pasos(contexto.fixture)[0]).toContain('Se retiró del catálogo');
    expect(pasos(contexto.fixture)[0]).toContain('Motivo');
    expect(texto(contexto.fixture)).not.toContain('PROHIBITED_ITEM');
  });

  /**
   * El hermano del caso de la acción desconocida, por el lado del motivo, y se resuelve al
   * revés: un motivo que no se reconoce **se descarta**, porque se pinta traducido y sin
   * traducción saldría el nombre crudo de la enumeración. La fila se pinta igual.
   */
  it('deberia descartar un motivo que no reconoce en vez de pintarlo crudo', async () => {
    const contexto = await montar();
    await desplegar(contexto, [
      { action: 'REJECTED', reason: 'MOTIVO_QUE_NO_EXISTE', occurredAt: '2026-09-03T09:05:22Z' },
    ]);

    expect(pasos(contexto.fixture)).toHaveLength(1);
    expect(pasos(contexto.fixture)[0]).toContain('Se rechazó');
    expect(texto(contexto.fixture)).not.toContain('MOTIVO_QUE_NO_EXISTE');
    expect(texto(contexto.fixture)).not.toContain('Motivo');
  });

  /**
   * Criterio 8 por el lado del navegador. **La sesión se pone después de crear el
   * componente**, que es como ocurre en una carga real: el token de acceso vive en memoria y
   * se pierde al recargar, así que la sesión llega después por la cookie de refresco.
   *
   * <p>Ponerla antes es exactamente el error que `frontend/CLAUDE.md` documenta como la
   * causa de que `/mi-cuenta` no cargara nunca sin que ninguna prueba lo viera: con la sesión
   * ya puesta, la cláusula `sesion.isAuthenticated()` de la consulta se puede borrar entera y
   * todo sigue verde.
   */
  it('no deberia pedir el rastro hasta que llegue la sesion', async () => {
    const fixture = TestBed.createComponent(ModerationTrail);
    fixture.componentRef.setInput('publicacion', ID);
    await bombear(fixture);

    // La sesión se resuelve en anónima, que es lo que hace el inicializador cuando no hay
    // cookie de refresco que recuperar. Es la misma forma que usa `my-listings-page.spec.ts`.
    TestBed.inject(SessionStore).clear();
    await bombear(fixture);

    const backend = TestBed.inject(HttpTestingController);

    interruptor(fixture).click();
    await bombear(fixture);
    backend.expectNone((llamada) => llamada.url.includes('moderation-history'));

    // Y en cuanto llega, sale sola: quien recarga con el rastro abierto lo vuelve a ver.
    TestBed.inject(SessionStore).set(SESION);
    await bombear(fixture);

    backend
      .expectOne((llamada) => llamada.url === `${API}/listings/${ID}/moderation-history`)
      .flush({ events: [] });
    await bombear(fixture);

    expect(texto(fixture)).toContain('Todavía no ha pasado nada');
  });

  /** Al plegarlo desaparece la región, y el botón vuelve a decir que abre. */
  it('deberia poder plegarse otra vez', async () => {
    const contexto = await montar();
    await desplegar(contexto, [
      { action: 'SUBMITTED', reason: null, occurredAt: '2026-09-02T21:33:47Z' },
    ]);

    interruptor(contexto.fixture).click();
    await asentar(contexto.fixture);

    expect(contexto.fixture.nativeElement.querySelector('ol')).toBeNull();
    expect(interruptor(contexto.fixture).getAttribute('aria-expanded')).toBe('false');
  });
});
