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
import { ReviewDetailPage } from './review-detail-page';

/** El detalle de una solicitud, donde se decide. HU-006. */
describe('ReviewDetailPage', () => {
  const API = 'https://api.pruebas.sendik.co/api/v1';
  const ID = 'una-solicitud';

  const SESION: Session = {
    accessToken: 'un-token',
    user: {
      email: 'moderadora@sendik.co',
      displayName: 'Quien Modera',
      emailVerified: true,
      roles: ['MODERATOR'],
    },
  };

  const solicitud = (cambios: Record<string, unknown> = {}) => ({
    id: ID,
    attempts: 1,
    documentType: 'CC',
    documentNumberLastFour: '2947',
    // Los completos NO viajan (criterio 11). Se dejan aqui como campos ajenos al tipo
    // para que la asercion de mas abajo pueda fallar: sin ellos, `not.toContain` era
    // cierto por construccion y no probaba nada.
    documentNumberFull: '1053812947',
    bankAccountNumberFull: '91500123456',
    documentHolderName: 'Ana Maria Garcia',
    documentSubmitted: true,
    selfieSubmitted: true,
    bank: 'bancolombia',
    bankAccountType: 'SAVINGS',
    bankAccountLastFour: '3456',
    bankAccountHolderName: 'Ana Maria Garcia',
    waitingSince: '2026-08-20T10:00:00Z',
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
   * Un latido, sin esperar estabilidad.
   *
   * <p>`whenStable()` espera a que no queden peticiones en vuelo, asi que con una
   * mutacion pendiente no vuelve nunca: hay que dejar que la peticion se emita, servirla
   * y solo despues asentar. Es la diferencia entre una prueba que pasa y una que se
   * cuelga cinco segundos.
   */
  const latir = async (fixture: { detectChanges: () => void }) => {
    await new Promise((listo) => setTimeout(listo, 0));
    fixture.detectChanges();
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

  const montar = async (respuesta: unknown[]) => {
    const fixture = TestBed.createComponent(ReviewDetailPage);
    fixture.componentRef.setInput('id', ID);
    await fixture.whenStable();

    const backend = TestBed.inject(HttpTestingController);
    backend
      .expectOne((p) => p.method === 'GET' && p.url === `${API}/verifications`)
      // La bandeja responde una página, no una lista pelada: es un listado administrativo.
      .flush({ items: respuesta, page: 0, size: 20 });
    await asentar(fixture);

    return { fixture, backend };
  };

  const boton = (fixture: { nativeElement: HTMLElement }, texto: string) =>
    [...fixture.nativeElement.querySelectorAll('button')].find((b) =>
      b.textContent?.includes(texto),
    ) as HTMLButtonElement | undefined;

  const peticionesDeImagen = (backend: HttpTestingController) =>
    backend.match((p) => p.url.includes('/images/'));

  /**
   * Envia el formulario de decision.
   *
   * <p>Se despacha el evento en vez de pulsar el boton porque jsdom no implementa
   * `requestSubmit()`, asi que un clic sobre un `type="submit"` no dispara nada. El
   * camino que se prueba sigue siendo el real: `ngSubmit`.
   */
  /**
   * Deja pasar el tiempo atendiendo lo que vaya llegando.
   *
   * <p>No sirve `asentar` despues de un conflicto: la bandeja se refresca sola, y
   * `whenStable()` no vuelve mientras haya una peticion en vuelo. Aqui se late y se sirve
   * lo que aparezca, que es lo que hace el navegador de verdad.
   */
  const latirYServir = async (
    fixture: { detectChanges: () => void },
    backend: HttpTestingController,
    respuesta: unknown[],
  ) => {
    for (let vuelta = 0; vuelta < 5; vuelta++) {
      await new Promise((listo) => setTimeout(listo, 0));
      backend
        .match((p) => p.url === `${API}/verifications`)
        .forEach((p) => p.flush({ items: respuesta, page: 0, size: 20 }));
      fixture.detectChanges();
    }
  };

  const enviarFormulario = (fixture: { nativeElement: HTMLElement }) =>
    fixture.nativeElement
      .querySelector('form')
      ?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

  /**
   * <strong>La prueba que justifica el componente de imagen.</strong>
   *
   * <p>Cada lectura de una imagen deja una fila en la bitacora con actor y motivo
   * (RN-046, criterio 6). Si la ficha pidiera las tres al cargarse, quedarian
   * registradas tres lecturas que nadie hizo y la bitacora dejaria de contar lo que
   * paso. Es una diferencia que no se ve en pantalla: sin esta prueba, cambiar el
   * componente por un `img [src]` pasaria desapercibido.
   */
  it('no pide ninguna imagen al abrir el detalle', async () => {
    const { backend } = await montar([solicitud()]);

    expect(peticionesDeImagen(backend)).toHaveLength(0);
  });

  it('pide una sola imagen, y solo la que se abre', async () => {
    const { fixture, backend } = await montar([solicitud()]);

    boton(fixture, 'Ver')?.click();
    await asentar(fixture);

    const peticiones = peticionesDeImagen(backend);
    expect(peticiones).toHaveLength(1);

    const primera = peticiones[0]!;
    expect(primera.request.url).toContain('/images/document-front');
    primera.flush(new Blob(['x']));
  });

  /** Criterio 6: la lectura queda anotada con un motivo que la interfaz manda sola. */
  it('manda el motivo de la lectura para la bitacora', async () => {
    const { fixture, backend } = await montar([solicitud()]);

    boton(fixture, 'Ver')?.click();
    await asentar(fixture);

    const peticion = peticionesDeImagen(backend)[0]!;
    expect(peticion.request.params.get('motivo')).toBeTruthy();
    peticion.flush(new Blob(['x']));
  });

  /** Y se dice, siempre: quien revisa tiene derecho a saber que se le registra. */
  it('avisa de que abrir una imagen queda registrado', async () => {
    const { fixture } = await montar([solicitud()]);

    expect(fixture.nativeElement.textContent).toContain('queda registrado');
  });

  /**
   * Criterio 9. El boton NO se deshabilita: uno deshabilitado sale del orden de
   * tabulacion y no dice por que. Se intenta, se senala el campo y no se llama a nadie.
   */
  it('al rechazar sin motivo lo dice y no llama al servidor', async () => {
    const { fixture, backend } = await montar([solicitud()]);

    enviarFormulario(fixture);
    await asentar(fixture);

    expect(fixture.nativeElement.textContent).toContain('Elige un motivo');
    expect(fixture.nativeElement.querySelector('#motivo')?.getAttribute('aria-invalid')).toBe(
      'true',
    );
    expect(backend.match((p) => p.url.includes('/rejection'))).toHaveLength(0);
  });

  /** Y con motivo elegido si se puede: sin esto, un `false` fijo pasaria la anterior. */
  it('rechaza con el motivo y la nota elegidos', async () => {
    const { fixture, backend } = await montar([solicitud()]);

    const motivo = fixture.nativeElement.querySelector('#motivo') as HTMLSelectElement;
    motivo.value = 'ILLEGIBLE_PHOTOS';
    motivo.dispatchEvent(new Event('change'));

    const nota = fixture.nativeElement.querySelector('#nota') as HTMLTextAreaElement;
    nota.value = 'El reverso sale oscuro';
    nota.dispatchEvent(new Event('input'));
    await asentar(fixture);

    enviarFormulario(fixture);
    await asentar(fixture);
    boton(fixture, 'Confirmar')?.click();
    await latir(fixture);

    const peticion = backend.expectOne(`${API}/verifications/${ID}/rejection`);
    expect(peticion.request.body).toEqual({
      reason: 'ILLEGIBLE_PHOTOS',
      note: 'El reverso sale oscuro',
    });
    peticion.flush(null);
  });

  /** El tope de la nota es el mismo que valida el backend. */
  it('no deja escribir una nota mas larga de 500', async () => {
    const { fixture } = await montar([solicitud()]);

    const nota = fixture.nativeElement.querySelector('#nota') as HTMLTextAreaElement;
    expect(nota.getAttribute('maxlength')).toBe('500');
  });

  /** Criterio 10: aprobar se confirma una vez. Notifica por correo y no se deshace. */
  it('pide confirmacion antes de aprobar y no llama hasta confirmar', async () => {
    const { fixture, backend } = await montar([solicitud()]);

    boton(fixture, 'Aprobar')?.click();
    await asentar(fixture);

    expect(fixture.nativeElement.textContent).toContain('Aprobar esta');
    expect(backend.match((p) => p.url.includes('/approval'))).toHaveLength(0);
  });

  it('aprueba al confirmar', async () => {
    const { fixture, backend } = await montar([solicitud()]);

    boton(fixture, 'Aprobar')?.click();
    await asentar(fixture);
    boton(fixture, 'Confirmar')?.click();
    await latir(fixture);

    const peticion = backend.expectOne(`${API}/verifications/${ID}/approval`);
    expect(peticion.request.method).toBe('POST');
    peticion.flush(null);
  });

  it('cancelar deja la solicitud sin decidir', async () => {
    const { fixture, backend } = await montar([solicitud()]);

    boton(fixture, 'Aprobar')?.click();
    await asentar(fixture);
    boton(fixture, 'Cancelar')?.click();
    await asentar(fixture);

    expect(backend.match((p) => p.url.includes('/approval'))).toHaveLength(0);
    expect(boton(fixture, 'Aprobar')).toBeDefined();
  });

  /**
   * Criterio 12 y RN-060: sobre lo propio no se decide, y se avisa antes de intentarlo.
   *
   * <p>El servidor lo rechaza igual —esconder el boton no es la regla— pero enterarse
   * despues de pulsar, con un correo ya prometido, no hace falta.
   */
  it('no ofrece decidir sobre la solicitud propia', async () => {
    const { fixture } = await montar([solicitud({ own: true })]);

    expect(fixture.nativeElement.textContent).toContain('Esta solicitud es tuya');
    expect(boton(fixture, 'Aprobar')).toBeUndefined();
    expect(boton(fixture, 'Rechazar')).toBeUndefined();
  });

  /** Criterio 7, en el detalle. Con texto, no solo con color. */
  it('senala con texto la discrepancia de titular', async () => {
    const { fixture } = await montar([solicitud({ bankAccountHolderName: 'Carlos Perez' })]);

    expect(fixture.nativeElement.textContent).toContain('no coincide con el del documento');
  });

  /**
   * Criterio 13: ni el numero de documento ni el de cuenta completos, ni ninguna
   * direccion de imagen. Solo cuatro digitos, tambien para el moderador.
   */
  it('no pinta numeros completos ni direcciones de imagen', async () => {
    const { fixture } = await montar([solicitud()]);

    const texto = fixture.nativeElement.textContent ?? '';
    expect(texto).toContain('2947');
    expect(texto).not.toContain('1053812947');
    expect(texto).not.toContain('91500123456');
    expect(fixture.nativeElement.querySelectorAll('img')).toHaveLength(0);
  });

  /** Criterio 11: se dice que paso, no un error generico. */
  it('dice que otra persona ya la resolvio', async () => {
    const { fixture, backend } = await montar([solicitud()]);

    boton(fixture, 'Aprobar')?.click();
    await asentar(fixture);
    boton(fixture, 'Confirmar')?.click();
    await latir(fixture);

    backend
      .expectOne(`${API}/verifications/${ID}/approval`)
      .flush(
        { code: 'SELLER_VERIFICATION_INVALID_STATE' },
        { status: 409, statusText: 'Conflict' },
      );

    // Criterio 11, la otra mitad: la bandeja se refresca sola, porque la que se esta
    // mirando ya no es la que hay.
    await latirYServir(fixture, backend, []);

    expect(fixture.nativeElement.textContent).toContain('Otra persona ya');
  });

  /** No se navega tras un conflicto: quien revisa tiene que leerlo aqui. */
  it('se queda en el detalle cuando la solicitud ya no esta pendiente', async () => {
    const { fixture, backend } = await montar([solicitud()]);

    boton(fixture, 'Aprobar')?.click();
    await asentar(fixture);
    boton(fixture, 'Confirmar')?.click();
    await latir(fixture);

    backend
      .expectOne(`${API}/verifications/${ID}/approval`)
      .flush(
        { code: 'SELLER_VERIFICATION_INVALID_STATE' },
        { status: 409, statusText: 'Conflict' },
      );

    await latirYServir(fixture, backend, [solicitud()]);

    expect(boton(fixture, 'Aprobar')).toBeDefined();
  });

  /** Recargar con la direccion de una que ya no esta: se dice, no se deja en blanco. */
  it('explica que la solicitud ya no esta en la bandeja', async () => {
    const { fixture } = await montar([]);

    expect(fixture.nativeElement.textContent).toContain('ya no');
  });
});
