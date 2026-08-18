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
import { ForgotPasswordPage } from './forgot-password-page';

/** Criterio 19 de HU-001. */
describe('ForgotPasswordPage', () => {
  const API = 'https://api.pruebas.sastra.co/api/v1';

  const render = async () => {
    const fixture = TestBed.createComponent(ForgotPasswordPage);
    await fixture.whenStable();
    return fixture;
  };

  const escribir = (fixture: { nativeElement: HTMLElement }, valor: string) => {
    const campo = fixture.nativeElement.querySelector('#correo') as HTMLInputElement;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
  };

  const enviar = (fixture: { nativeElement: HTMLElement }) => {
    (fixture.nativeElement.querySelector('form') as HTMLFormElement).requestSubmit();
  };

  const asentar = async (fixture: {
    whenStable: () => Promise<unknown>;
    detectChanges: () => void;
  }) => {
    await new Promise((listo) => setTimeout(listo, 0));
    fixture.detectChanges();
    await fixture.whenStable();
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
  });

  it('pide el correo con su etiqueta asociada', async () => {
    const fixture = await render();

    expect(fixture.nativeElement.querySelector('label[for="correo"]')).not.toBeNull();
  });

  it('no llama a la API con un correo mal escrito', async () => {
    const fixture = await render();
    escribir(fixture, 'no-es-un-correo');
    enviar(fixture);
    await asentar(fixture);

    TestBed.inject(HttpTestingController).expectNone(`${API}/auth/forgot-password`);
    expect(document.activeElement?.id).toBe('correo');
  });

  it('envia la peticion cuando el correo tiene forma valida', async () => {
    const fixture = await render();
    escribir(fixture, 'ana@correo.co');
    enviar(fixture);
    await fixture.whenStable();

    const peticion = TestBed.inject(HttpTestingController).expectOne(`${API}/auth/forgot-password`);
    expect(peticion.request.body).toEqual({ email: 'ana@correo.co' });
  });

  /**
   * Criterio 19. El servidor responde 202 exista o no la cuenta, y esta pantalla
   * muestra el mismo aviso: si dijera algo distinto en cada caso, escribir correos
   * uno por uno revelaria quien esta registrado en Sastra.
   */
  it('muestra el mismo aviso en condicional, sin decir si el correo existe', async () => {
    const fixture = await render();
    escribir(fixture, 'ana@correo.co');
    enviar(fixture);
    await fixture.whenStable();

    TestBed.inject(HttpTestingController)
      .expectOne(`${API}/auth/forgot-password`)
      .flush(null, { status: 202, statusText: 'Accepted' });
    await asentar(fixture);

    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('Si ese correo tiene una cuenta');
    // Criterio 18: se dice cuanto dura, y son 30 minutos, no 24 horas.
    expect(texto).toContain('30 minutos');
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
  });

  // El enlace de vuelta al ingreso: quien llego aqui por error tiene salida.
  it('ofrece volver a entrar', async () => {
    const fixture = await render();
    const enlace = fixture.nativeElement.querySelector('a[href="/ingresar"]');

    expect(enlace).not.toBeNull();
  });
});
