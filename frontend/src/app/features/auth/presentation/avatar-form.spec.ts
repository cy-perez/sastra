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
import { AvatarForm } from './avatar-form';

/** Criterio 21 de HU-001: la foto de perfil. */
describe('AvatarForm', () => {
  const API = 'https://api.pruebas.sendik.co/api/v1';

  const SESION: Session = {
    accessToken: 'un-token',
    user: { email: 'ana@correo.co', displayName: 'Ana Maria', emailVerified: true, roles: [] },
  };

  const SIN_FOTO = {
    email: 'ana@correo.co',
    emailVerified: true,
    displayName: 'Ana Maria',
    city: null,
    phone: null,
    avatarUrl: null,
  };

  const CON_FOTO = { ...SIN_FOTO, avatarUrl: 'https://archivos.sendik.co/avatares/la-foto.png' };

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

  const montar = async (perfil: object = SIN_FOTO) => {
    const fixture = TestBed.createComponent(AvatarForm);
    await fixture.whenStable();

    const backend = TestBed.inject(HttpTestingController);
    backend
      .expectOne((peticion) => peticion.method === 'GET' && peticion.url === `${API}/users/me`)
      .flush(perfil);
    await asentar(fixture);

    return { fixture, backend };
  };

  const campoDeArchivo = (fixture: { nativeElement: HTMLElement }) =>
    fixture.nativeElement.querySelector('input[type="file"]') as HTMLInputElement;

  /**
   * Elegir un archivo de verdad no se puede simular poniendo `files`, que es de solo
   * lectura. Se define la propiedad sobre el elemento y se emite `change`, que es
   * exactamente lo que hace el navegador.
   */
  const elegir = (fixture: { nativeElement: HTMLElement }, archivo: File) => {
    const campo = campoDeArchivo(fixture);
    Object.defineProperty(campo, 'files', { value: [archivo], configurable: true });
    campo.dispatchEvent(new Event('change'));
  };

  const boton = (fixture: { nativeElement: HTMLElement }, texto: string) =>
    [...fixture.nativeElement.querySelectorAll('button')].find((candidato) =>
      candidato.textContent?.includes(texto),
    ) as HTMLButtonElement | undefined;

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

  it('ofrece elegir una foto cuando no hay ninguna', async () => {
    const { fixture } = await montar();

    expect(fixture.nativeElement.textContent).toContain('Elegir una foto');
    expect(fixture.nativeElement.querySelector('img')).toBeNull();
  });

  it('muestra la foto que ya hay y ofrece cambiarla', async () => {
    const { fixture } = await montar(CON_FOTO);

    const imagen = fixture.nativeElement.querySelector('img') as HTMLImageElement;

    expect(imagen.getAttribute('src')).toBe(CON_FOTO.avatarUrl);
    expect(fixture.nativeElement.textContent).toContain('Cambiar la foto');
  });

  /** La imagen es contenido, no decoracion: lleva texto alternativo de verdad. */
  it('la foto tiene texto alternativo', async () => {
    const { fixture } = await montar(CON_FOTO);

    expect((fixture.nativeElement.querySelector('img') as HTMLImageElement).alt).toBe(
      'Tu foto de perfil',
    );
  });

  /** Solo los dos tipos que el servidor puede recodificar para quitarles el EXIF. */
  it('solo acepta los tipos que el servidor acepta', async () => {
    const { fixture } = await montar();

    expect(campoDeArchivo(fixture).accept).toBe('image/jpeg,image/png');
  });

  it('sube la foto elegida como multipart', async () => {
    const { fixture, backend } = await montar();

    elegir(fixture, new File(['unos bytes'], 'foto.png', { type: 'image/png' }));
    await fixture.whenStable();

    const peticion = backend.expectOne(
      (enviada) => enviada.method === 'PUT' && enviada.url === `${API}/users/me/avatar`,
    );

    expect(peticion.request.body).toBeInstanceOf(FormData);
    expect((peticion.request.body as FormData).get('archivo')).toBeInstanceOf(File);
    // El Content-Type lo pone el navegador con su separador: fijarlo a mano rompe
    // el multipart y el servidor recibe un cuerpo que no puede leer.
    expect(peticion.request.headers.get('Content-Type')).toBeNull();
  });

  /**
   * La foto nueva se pinta con lo que devolvio el PUT, sin esperar otra respuesta.
   *
   * <p>Lo que se afirma es que aparece <strong>ya</strong>, no que no haya ninguna
   * peticion mas: la consulta del perfil tiene {@code staleTime: 0} —para que un
   * cambio hecho en otro dispositivo se vea al mirar— y eso hace que se refresque de
   * fondo despues. Ese refresco no se contesta aqui a proposito: si la pantalla
   * dependiera de el para mostrar la foto, esta prueba fallaria, y eso es justo lo
   * que se quiere saber.
   */
  it('muestra la foto nueva sin esperar otra respuesta', async () => {
    const { fixture, backend } = await montar();

    elegir(fixture, new File(['unos bytes'], 'foto.png', { type: 'image/png' }));
    await fixture.whenStable();

    backend
      .expectOne((enviada) => enviada.method === 'PUT' && enviada.url === `${API}/users/me/avatar`)
      .flush(CON_FOTO);
    await asentar(fixture);

    expect(
      (fixture.nativeElement.querySelector('img') as HTMLImageElement).getAttribute('src'),
    ).toBe(CON_FOTO.avatarUrl);
  });

  /**
   * Un archivo que el navegador reconoce como otra cosa se rechaza sin gastar la
   * subida. Es una cortesia y no una defensa: quien decide es el servidor mirando
   * los bytes, y un archivo renombrado pasa por aqui y se rechaza alli.
   */
  it('rechaza un tipo que no se acepta sin llamar al servidor', async () => {
    const { fixture, backend } = await montar();

    elegir(fixture, new File(['<svg>'], 'dibujo.svg', { type: 'image/svg+xml' }));
    await asentar(fixture);

    backend.expectNone((enviada) => enviada.url === `${API}/users/me/avatar`);
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'no es una imagen JPG o PNG',
    );
  });

  /** El servidor manda un codigo y la pantalla lo traduce; nunca texto del servidor. */
  it('traduce el rechazo del servidor', async () => {
    const { fixture, backend } = await montar();

    elegir(fixture, new File(['unos bytes'], 'chica.png', { type: 'image/png' }));
    await fixture.whenStable();

    backend
      .expectOne((enviada) => enviada.method === 'PUT' && enviada.url === `${API}/users/me/avatar`)
      .flush(
        { code: 'FILE_DIMENSIONS_TOO_SMALL', title: 'da igual', traceId: 'x' },
        { status: 422, statusText: 'Unprocessable Content' },
      );
    await asentar(fixture);

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'muy pequeña',
    );
  });

  /**
   * Quitar la foto va en dos pasos y subir en uno. No es simetrico a proposito:
   * subir se corrige subiendo otra, y borrar no se corrige con nada si el archivo
   * original ya no esta en el telefono.
   */
  it('pide confirmacion antes de quitar la foto', async () => {
    const { fixture, backend } = await montar(CON_FOTO);

    boton(fixture, 'Quitar la foto')?.click();
    await asentar(fixture);

    // Todavia no se llamo a nadie: solo se abrio la confirmacion.
    backend.expectNone((enviada) => enviada.method === 'DELETE');
    expect(fixture.nativeElement.textContent).toContain('Sí, quitarla');

    boton(fixture, 'Sí, quitarla')?.click();
    await fixture.whenStable();

    backend
      .expectOne(
        (enviada) => enviada.method === 'DELETE' && enviada.url === `${API}/users/me/avatar`,
      )
      .flush(SIN_FOTO);
    await asentar(fixture);

    expect(fixture.nativeElement.querySelector('img')).toBeNull();
  });

  it('se puede desistir de quitar la foto', async () => {
    const { fixture, backend } = await montar(CON_FOTO);

    boton(fixture, 'Quitar la foto')?.click();
    await asentar(fixture);

    boton(fixture, 'Dejarla como está')?.click();
    await asentar(fixture);

    backend.expectNone((enviada) => enviada.method === 'DELETE');
    expect(fixture.nativeElement.querySelector('img')).not.toBeNull();
  });

  /**
   * El campo se limpia despues de cada eleccion. Sin esto, elegir el mismo archivo
   * dos veces seguidas no emite `change` y un reintento tras un fallo no haria nada.
   */
  it('permite reintentar con el mismo archivo', async () => {
    const { fixture, backend } = await montar();
    const archivo = new File(['unos bytes'], 'foto.png', { type: 'image/png' });

    elegir(fixture, archivo);
    await fixture.whenStable();
    backend
      .expectOne((enviada) => enviada.method === 'PUT' && enviada.url === `${API}/users/me/avatar`)
      .flush({ code: 'COMMON_UNEXPECTED' }, { status: 500, statusText: 'Server Error' });
    await asentar(fixture);

    expect(campoDeArchivo(fixture).value).toBe('');

    elegir(fixture, archivo);
    await fixture.whenStable();

    backend.expectOne(
      (enviada) => enviada.method === 'PUT' && enviada.url === `${API}/users/me/avatar`,
    );
  });
});
