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
import { SessionStore } from '../../../core/session/session.store';
import { VerificationImage } from './verification-image';

/** Una imagen de la solicitud, que solo se pide al abrirla. HU-006, criterio 6. */
describe('VerificationImage', () => {
  const API = 'https://api.pruebas.sendik.co/api/v1';
  const ID = 'una-solicitud';

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
    TestBed.inject(SessionStore).set({
      accessToken: 'un-token',
      user: {
        email: 'moderadora@sendik.co',
        displayName: 'Quien Modera',
        emailVerified: true,
        roles: ['MODERATOR'],
      },
    });
  });

  const montar = async () => {
    const fixture = TestBed.createComponent(VerificationImage);
    fixture.componentRef.setInput('solicitud', ID);
    fixture.componentRef.setInput('cual', 'selfie');
    fixture.componentRef.setInput('etiqueta', 'Selfie');
    fixture.componentRef.setInput('descripcion', 'Foto del rostro de quien se verifica');
    await asentar(fixture);

    return { fixture, backend: TestBed.inject(HttpTestingController) };
  };

  const boton = (fixture: { nativeElement: HTMLElement }) =>
    fixture.nativeElement.querySelector('button') as HTMLButtonElement;

  const peticiones = (backend: HttpTestingController) =>
    backend.match((p) => p.url === `${API}/verifications/${ID}/images/selfie`);

  it('no pide nada hasta que alguien la abre', async () => {
    const { backend } = await montar();

    expect(peticiones(backend)).toHaveLength(0);
  });

  it('muestra la imagen al abrirla, con su descripcion', async () => {
    const { fixture, backend } = await montar();

    boton(fixture).click();
    await latir(fixture);
    peticiones(backend)[0]!.flush(new Blob(['x']));
    await asentar(fixture);

    const imagen = fixture.nativeElement.querySelector('img') as HTMLImageElement;
    expect(imagen).not.toBeNull();
    // El alt describe el contenido; el pie ya dice como se llama. Repetirlo lo anuncia
    // dos veces seguidas.
    expect(imagen.getAttribute('alt')).toBe('Foto del rostro de quien se verifica');
  });

  /**
   * <strong>El boton no desaparece.</strong> Si lo sustituyera la imagen, el foco de quien
   * lo acaba de pulsar caeria al cuerpo del documento y con teclado habria que recorrer la
   * pagina entera para volver.
   */
  it('mantiene el boton en la pagina despues de mostrar la imagen', async () => {
    const { fixture, backend } = await montar();

    boton(fixture).click();
    await latir(fixture);
    peticiones(backend)[0]!.flush(new Blob(['x']));
    await asentar(fixture);

    expect(boton(fixture)).not.toBeNull();
    expect(boton(fixture).textContent).toContain('Ocultar');
  });

  /**
   * Volver a pedir los bytes al reabrir dejaria una fila mas en la bitacora por cada vez
   * que alguien esconde y vuelve a mirar, y la bitacora contaria accesos que no lo son
   * (RN-046).
   */
  it('no vuelve a pedirla al ocultar y mostrar otra vez', async () => {
    const { fixture, backend } = await montar();

    boton(fixture).click();
    await latir(fixture);
    peticiones(backend)[0]!.flush(new Blob(['x']));
    await asentar(fixture);

    boton(fixture).click();
    await asentar(fixture);
    boton(fixture).click();
    await asentar(fixture);

    expect(peticiones(backend)).toHaveLength(0);
    expect(fixture.nativeElement.querySelector('img')).not.toBeNull();
  });

  /**
   * Caso borde de la historia: el archivo puede faltar por un fallo de despliegue. Se
   * dice, y la solicitud sigue siendo decidible —rechazar por fotos ilegibles es una
   * respuesta valida—, asi que esto no puede tumbar la pantalla.
   */
  it('dice que la imagen no esta disponible cuando falla', async () => {
    const { fixture, backend } = await montar();

    boton(fixture).click();
    await latir(fixture);
    peticiones(backend)[0]!.flush(null, { status: 404, statusText: 'Not Found' });
    await asentar(fixture);

    expect(fixture.nativeElement.textContent).toContain('no está disponible');
    expect(fixture.nativeElement.querySelector('img')).toBeNull();
  });
});
