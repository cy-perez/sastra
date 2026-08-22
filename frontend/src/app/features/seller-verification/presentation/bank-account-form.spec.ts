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
import { BankAccountForm } from './bank-account-form';

/** El formulario de la cuenta bancaria. Criterio 4 de HU-002. */
describe('BankAccountForm', () => {
  const API = 'https://api.pruebas.sastra.co/api/v1';

  const SESION: Session = {
    accessToken: 'un-token',
    user: { email: 'ana@correo.co', displayName: 'Ana Maria', emailVerified: true, roles: [] },
  };

  const ENTIDADES = [
    { code: 'bancolombia', name: 'Bancolombia', wallet: false },
    { code: 'nequi', name: 'Nequi', wallet: true },
  ];

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

  const montar = async () => {
    const fixture = TestBed.createComponent(BankAccountForm);
    await fixture.whenStable();

    const backend = TestBed.inject(HttpTestingController);
    backend
      .expectOne(
        (peticion) => peticion.method === 'GET' && peticion.url === `${API}/financial-institutions`,
      )
      .flush(ENTIDADES);
    await asentar(fixture);

    return { fixture, backend };
  };

  const select = (fixture: { nativeElement: HTMLElement }, id: string) =>
    fixture.nativeElement.querySelector(`#${id}`) as HTMLSelectElement;

  const escribir = async (
    fixture: { nativeElement: HTMLElement; whenStable: () => Promise<unknown> },
    id: string,
    valor: string,
  ) => {
    const campo = fixture.nativeElement.querySelector(`#${id}`) as HTMLInputElement;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  };

  const elegir = async (
    fixture: { nativeElement: HTMLElement; whenStable: () => Promise<unknown> },
    id: string,
    valor: string,
  ) => {
    const campo = select(fixture, id);
    campo.value = valor;
    campo.dispatchEvent(new Event('change'));
    await fixture.whenStable();
  };

  const enviar = (fixture: { nativeElement: HTMLElement }) => {
    const formulario = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    formulario.dispatchEvent(new Event('submit'));
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

  it('ofrece las entidades del catálogo por su nombre', async () => {
    const { fixture } = await montar();

    expect(fixture.nativeElement.textContent).toContain('Bancolombia');
    expect(fixture.nativeElement.textContent).toContain('Nequi');
  });

  /** Un banco recibe en ahorros o corriente. */
  it('ofrece los dos tipos bancarios al elegir un banco', async () => {
    const { fixture } = await montar();

    await elegir(fixture, 'entidad', 'bancolombia');
    await asentar(fixture);

    const opciones = [...select(fixture, 'tipo-de-cuenta').options].map((una) => una.value);

    expect(opciones).toContain('SAVINGS');
    expect(opciones).toContain('CHECKING');
    expect(opciones).not.toContain('ELECTRONIC_DEPOSIT');
  });

  /**
   * Una billetera no tiene cuenta de ahorros aunque se use igual. Ofrecer «ahorros» en
   * Nequi sería ofrecer algo que no existe, y en la Fase 3 el desembolso fallaría.
   */
  it('ofrece solo depósito electrónico al elegir una billetera, y lo explica', async () => {
    const { fixture } = await montar();

    await elegir(fixture, 'entidad', 'nequi');
    await asentar(fixture);

    const opciones = [...select(fixture, 'tipo-de-cuenta').options].map((una) => una.value);

    expect(opciones).toEqual(['ELECTRONIC_DEPOSIT']);
    expect(fixture.nativeElement.textContent).toContain(
      'Las billeteras solo reciben en depósito electrónico',
    );
  });

  /**
   * El caso que justifica el `effect`: sin limpiar el tipo al cambiar de entidad, el
   * formulario se vería bien y mandaría algo imposible.
   */
  it('no deja un tipo de cuenta que la entidad nueva no admite', async () => {
    const { fixture } = await montar();

    await elegir(fixture, 'entidad', 'bancolombia');
    await asentar(fixture);
    await elegir(fixture, 'tipo-de-cuenta', 'CHECKING');
    await asentar(fixture);

    await elegir(fixture, 'entidad', 'nequi');
    await asentar(fixture);

    expect(select(fixture, 'tipo-de-cuenta').value).toBe('ELECTRONIC_DEPOSIT');
  });

  it('guarda la cuenta con los cuatro datos', async () => {
    const { fixture, backend } = await montar();

    await elegir(fixture, 'entidad', 'bancolombia');
    await asentar(fixture);
    await elegir(fixture, 'tipo-de-cuenta', 'SAVINGS');
    await escribir(fixture, 'numero-de-cuenta', '915 001 234 56');
    await escribir(fixture, 'titular', 'Ana Maria Garcia');
    await asentar(fixture);

    enviar(fixture);
    await fixture.whenStable();

    const peticion = backend.expectOne(
      (enviada) =>
        enviada.method === 'PUT' && enviada.url === `${API}/users/me/verification/bank-account`,
    );

    expect(peticion.request.body).toEqual({
      bank: 'bancolombia',
      accountType: 'SAVINGS',
      accountNumber: '915 001 234 56',
      holderName: 'Ana Maria Garcia',
    });
  });

  it('no envía nada y señala el campo cuando falta la entidad', async () => {
    const { fixture, backend } = await montar();

    await escribir(fixture, 'numero-de-cuenta', '91500123456');
    await escribir(fixture, 'titular', 'Ana Maria Garcia');
    await asentar(fixture);

    enviar(fixture);
    await asentar(fixture);

    backend.expectNone((enviada) => enviada.url === `${API}/users/me/verification/bank-account`);
    expect(fixture.nativeElement.textContent).toContain('Elige una entidad de la lista');
  });

  it('señala un número de cuenta con letras sin llamar al servidor', async () => {
    const { fixture, backend } = await montar();

    await elegir(fixture, 'entidad', 'bancolombia');
    await asentar(fixture);
    await elegir(fixture, 'tipo-de-cuenta', 'SAVINGS');
    await escribir(fixture, 'numero-de-cuenta', '9150ABC3456');
    await escribir(fixture, 'titular', 'Ana Maria Garcia');
    await asentar(fixture);

    enviar(fixture);
    await asentar(fixture);

    backend.expectNone((enviada) => enviada.url === `${API}/users/me/verification/bank-account`);
    expect(fixture.nativeElement.textContent).toContain('solo dígitos');
  });

  /** No se regaña a nadie mientras escribe: los errores salen al intentar enviar. */
  it('no muestra errores antes del primer intento', async () => {
    const { fixture } = await montar();

    expect(fixture.nativeElement.textContent).not.toContain('Elige una entidad de la lista');
  });

  it('traduce el rechazo del servidor por titular distinto', async () => {
    const { fixture, backend } = await montar();

    await elegir(fixture, 'entidad', 'bancolombia');
    await asentar(fixture);
    await elegir(fixture, 'tipo-de-cuenta', 'SAVINGS');
    await escribir(fixture, 'numero-de-cuenta', '91500123456');
    await escribir(fixture, 'titular', 'Pedro Ramirez');
    await asentar(fixture);

    enviar(fixture);
    await fixture.whenStable();

    backend
      .expectOne(
        (enviada) =>
          enviada.method === 'PUT' && enviada.url === `${API}/users/me/verification/bank-account`,
      )
      .flush(
        { code: 'SELLER_ACCOUNT_HOLDER_MISMATCH' },
        { status: 422, statusText: 'Unprocessable Content' },
      );
    await asentar(fixture);

    const aviso = fixture.nativeElement.querySelector('[role="alert"]');

    expect(aviso?.textContent).toContain('El titular de la cuenta tiene que ser el mismo nombre');
  });
});
