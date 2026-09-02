import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  apiUrlInterceptor,
  errorInterceptor,
  languageInterceptor,
} from '../../../core/http/interceptors';
import type { Session } from '../../../core/session/session';
import { SessionStore } from '../../../core/session/session.store';
import { FavoriteIntent } from '../infrastructure/favorite-intent';
import { FavoriteToggle } from './favorite-toggle';

/**
 * El control de favorito. HU-011, criterios 1 a 10 y 17.
 *
 * <p><strong>La sesión se abre después de crear el componente, nunca antes.</strong> En una
 * carga de página el componente nace primero y la sesión llega luego, por la cookie de
 * refresco; una prueba que la ponga antes no prueba la carga real. Es la regresión que
 * `account-page.spec.ts` fijó y que aquí importa el doble, porque de que la sesión esté
 * resuelta dependen la consulta del estado y la intención pendiente.
 */
describe('FavoriteToggle', () => {
  const API = 'https://api.pruebas.sendik.co/api/v1';
  const ID = '01a04385-47b7-79c7-b3f2-62c03a8d4a88';

  @Component({
    standalone: true,
    imports: [FavoriteToggle],
    template: '<sendik-favorite-toggle [publicacion]="id()" />',
    changeDetection: ChangeDetectionStrategy.OnPush,
  })
  class Anfitrion {
    readonly id = signal(ID);
  }

  const sesion: Session = {
    accessToken: 'un-token',
    user: {
      email: 'ana@correo.co',
      displayName: 'Ana María',
      emailVerified: true,
      roles: ['BUYER'],
    },
  };

  beforeEach(() => {
    sessionStorage.clear();

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(
          withInterceptors([apiUrlInterceptor, languageInterceptor, errorInterceptor]),
        ),
        provideHttpClientTesting(),
      ],
    });
  });

  const bombear = async (fixture: ComponentFixture<Anfitrion>) => {
    for (let vuelta = 0; vuelta < 5; vuelta++) {
      await new Promise((listo) => setTimeout(listo, 0));
      fixture.detectChanges();
    }
  };

  /** El componente nace primero; la sesión, si la hay, llega después. */
  const montar = async (conSesion: boolean) => {
    const fixture = TestBed.createComponent(Anfitrion);
    const backend = TestBed.inject(HttpTestingController);
    const almacen = TestBed.inject(SessionStore);

    fixture.detectChanges();
    await bombear(fixture);

    if (conSesion) {
      almacen.set(sesion);
    } else {
      almacen.clear();
    }
    await bombear(fixture);

    return { fixture, backend };
  };

  const responderEstado = async (
    fixture: ComponentFixture<Anfitrion>,
    backend: HttpTestingController,
    cuerpo: { favorite: boolean; eligible: boolean },
  ) => {
    backend.expectOne((llamada) => llamada.url === `${API}/users/me/favorites/${ID}`).flush(cuerpo);
    await bombear(fixture);
  };

  const boton = (fixture: ComponentFixture<Anfitrion>) =>
    fixture.nativeElement.querySelector('button') as HTMLButtonElement | null;

  /** Criterio 1: el control refleja el estado que llega, no el que se supone. */
  it('se pinta marcado cuando el servidor dice que ya lo estaba', async () => {
    const { fixture, backend } = await montar(true);
    await responderEstado(fixture, backend, { favorite: true, eligible: true });

    expect(boton(fixture)?.getAttribute('aria-pressed')).toBe('true');
    expect(fixture.nativeElement.textContent).toContain('Quitar de favoritos');
  });

  it('se pinta sin marcar cuando el servidor dice que no lo estaba', async () => {
    const { fixture, backend } = await montar(true);
    await responderEstado(fixture, backend, { favorite: false, eligible: true });

    expect(boton(fixture)?.getAttribute('aria-pressed')).toBe('false');
    expect(fixture.nativeElement.textContent).toContain('Guardar');
  });

  /** Criterio 7: sin sesión el control se ofrece igual, y no se pide nada al servidor. */
  it('se ofrece a quien no tiene sesión y no consulta el estado', async () => {
    const { fixture, backend } = await montar(false);

    expect(boton(fixture)).not.toBeNull();
    expect(boton(fixture)?.getAttribute('aria-pressed')).toBe('false');
    backend.expectNone((llamada) => llamada.url.includes('/users/me/favorites'));
  });

  /** Criterio 5: sobre la publicación propia no se ofrece. Lo decide el servidor. */
  it('no se ofrece cuando el servidor dice que no es elegible, criterio 5', async () => {
    const { fixture, backend } = await montar(true);
    await responderEstado(fixture, backend, { favorite: false, eligible: false });

    expect(boton(fixture)).toBeNull();
  });

  /** Criterio 2: se marca, y el control se adelanta a la respuesta. */
  it('marca al pulsar', async () => {
    const { fixture, backend } = await montar(true);
    await responderEstado(fixture, backend, { favorite: false, eligible: true });

    boton(fixture)?.click();
    await bombear(fixture);

    const peticion = backend.expectOne(
      (llamada) => llamada.method === 'PUT' && llamada.url === `${API}/users/me/favorites/${ID}`,
    );
    expect(boton(fixture)?.getAttribute('aria-pressed')).toBe('true');

    peticion.flush(null, { status: 204, statusText: 'No Content' });
    await bombear(fixture);
  });

  /** Criterio 3: y se quita. */
  it('quita al pulsar cuando ya estaba marcado', async () => {
    const { fixture, backend } = await montar(true);
    await responderEstado(fixture, backend, { favorite: true, eligible: true });

    boton(fixture)?.click();
    await bombear(fixture);

    backend
      .expectOne(
        (llamada) =>
          llamada.method === 'DELETE' && llamada.url === `${API}/users/me/favorites/${ID}`,
      )
      .flush(null, { status: 204, statusText: 'No Content' });
    await bombear(fixture);

    // Y sigue sin marcar mientras la respuesta fresca viaja de vuelta. Antes de arreglarlo
    // volvia a pintarse marcado en ese hueco y se desmarcaba al llegar: un parpadeo que
    // decia lo contrario de lo que acababa de pasar.
    expect(boton(fixture)?.getAttribute('aria-pressed')).toBe('false');

    backend
      .match((llamada) => llamada.method === 'GET')
      .forEach((llamada) => llamada.flush({ favorite: false, eligible: true }));
    await bombear(fixture);

    expect(boton(fixture)?.getAttribute('aria-pressed')).toBe('false');
  });

  /**
   * Caso borde: el doble pulsado no manda dos peticiones ni deja el estado invertido.
   *
   * <p>Se comprueba contando las peticiones y no mirando el botón: deshabilitarlo mientras
   * la petición está en curso es una de las formas de conseguirlo, pero no la única, y la
   * prueba tiene que valer si mañana se resuelve de otra manera.
   */
  it('no manda dos peticiones al pulsar dos veces seguidas', async () => {
    const { fixture, backend } = await montar(true);
    await responderEstado(fixture, backend, { favorite: false, eligible: true });

    boton(fixture)?.click();
    await bombear(fixture);
    boton(fixture)?.click();
    await bombear(fixture);

    const peticiones = backend.match(
      (llamada) => llamada.method === 'PUT' && llamada.url === `${API}/users/me/favorites/${ID}`,
    );
    expect(peticiones).toHaveLength(1);
    peticiones.forEach((peticion) =>
      peticion.flush(null, { status: 204, statusText: 'No Content' }),
    );
    await bombear(fixture);
  });

  /**
   * Un fallo devuelve el control a su estado anterior y lo dice. Lo peor que puede hacer
   * un control optimista es quedarse mintiendo.
   */
  it('vuelve al estado anterior cuando la petición falla', async () => {
    const { fixture, backend } = await montar(true);
    await responderEstado(fixture, backend, { favorite: false, eligible: true });

    boton(fixture)?.click();
    await bombear(fixture);

    backend
      .expectOne((llamada) => llamada.method === 'PUT')
      .flush({ code: 'COMMON_UNEXPECTED' }, { status: 500, statusText: 'Server Error' });
    await bombear(fixture);

    expect(boton(fixture)?.getAttribute('aria-pressed')).toBe('false');
    expect(fixture.nativeElement.textContent).toContain('No pudimos guardar el cambio');
  });

  /** Criterio 10: si resulta ser suya, se explica por qué no se guardó. */
  it('explica que la publicación es propia cuando el servidor responde 403', async () => {
    const { fixture, backend } = await montar(true);
    await responderEstado(fixture, backend, { favorite: false, eligible: true });

    boton(fixture)?.click();
    await bombear(fixture);

    backend
      .expectOne((llamada) => llamada.method === 'PUT')
      .flush({ code: 'CATALOG_SELF_FAVORITE_FORBIDDEN' }, { status: 403, statusText: 'Forbidden' });
    await bombear(fixture);

    expect(fixture.nativeElement.textContent).toContain('Esta publicación es tuya');
  });

  /** Criterio 6: la publicación se vendió mientras estaba en pantalla. */
  it('explica que ya no está disponible cuando el servidor responde 404', async () => {
    const { fixture, backend } = await montar(true);
    await responderEstado(fixture, backend, { favorite: false, eligible: true });

    boton(fixture)?.click();
    await bombear(fixture);

    backend
      .expectOne((llamada) => llamada.method === 'PUT')
      .flush({ code: 'COMMON_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    await bombear(fixture);

    expect(fixture.nativeElement.textContent).toContain('ya no está disponible');
  });

  /**
   * Criterio 8, primera mitad: sin sesión se anota la intención y se va a entrar, con la
   * dirección de vuelta puesta.
   */
  it('sin sesión anota la intención y lleva a entrar con la vuelta puesta', async () => {
    const { fixture, backend } = await montar(false);
    const router = TestBed.inject(Router);
    const navegar = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    boton(fixture)?.click();
    await bombear(fixture);

    expect(navegar).toHaveBeenCalledWith(['/ingresar'], {
      queryParams: { redirectTo: `/producto/${ID}` },
    });
    expect(TestBed.inject(FavoriteIntent).consumir(ID)).toBe(true);
    backend.expectNone((llamada) => llamada.method === 'PUT');
  });

  /**
   * Criterio 9: abandonar el ingreso no deja nada guardado.
   *
   * <p>Volver atrás desde el formulario es exactamente esto: la ficha se monta otra vez y
   * la sesión resuelve **anónima**. Si la intención sobreviviera a eso, el favorito
   * aparecería la próxima vez que alguien entrara desde ese navegador, sobre algo que ya
   * no recuerda haber pulsado.
   */
  it('descarta la intención pendiente cuando la sesión resuelve anónima, criterio 9', async () => {
    TestBed.inject(FavoriteIntent).recordar(ID);

    const { fixture, backend } = await montar(false);
    await bombear(fixture);

    expect(TestBed.inject(FavoriteIntent).consumir(ID)).toBe(false);
    backend.expectNone((llamada) => llamada.method === 'PUT');
  });

  /**
   * Criterio 8, segunda mitad: al volver con sesión, la intención se dispara sola y una
   * sola vez.
   *
   * <p>Es el caso que pasa por la recarga: el componente nace sin sesión, la cookie de
   * refresco la trae después, y solo entonces se puede guardar nada.
   */
  it('retoma la intención al resolverse la sesión, y no la repite', async () => {
    TestBed.inject(FavoriteIntent).recordar(ID);

    const { fixture, backend } = await montar(true);
    await bombear(fixture);

    const marcados = backend.match(
      (llamada) => llamada.method === 'PUT' && llamada.url === `${API}/users/me/favorites/${ID}`,
    );
    expect(marcados).toHaveLength(1);
    marcados.forEach((llamada) => llamada.flush(null, { status: 204, statusText: 'No Content' }));
    await bombear(fixture);

    expect(TestBed.inject(FavoriteIntent).consumir(ID)).toBe(false);
  });

  /** El estado marcado se anuncia sin depender del color (criterio 17). */
  it('anuncia el estado con aria-pressed y con texto, no solo con color', async () => {
    const { fixture, backend } = await montar(true);
    await responderEstado(fixture, backend, { favorite: true, eligible: true });

    const estado = fixture.nativeElement.querySelector('[role="status"]') as HTMLElement;
    expect(estado.textContent).toContain('Guardado en tus favoritos');
    expect(boton(fixture)?.getAttribute('aria-pressed')).toBe('true');
  });
});
