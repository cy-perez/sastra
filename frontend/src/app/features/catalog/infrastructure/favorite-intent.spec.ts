import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { FavoriteIntent } from './favorite-intent';

/**
 * La intención pendiente del ingreso. HU-011, criterios 8, 9 y 10. ADR-0029.
 *
 * <p>Es donde es más fácil dejar un fantasma: una intención que no se borra hace que el
 * favorito reaparezca la próxima vez que alguien entre desde ese navegador, sobre una
 * publicación que ya no recuerda haber pulsado.
 */
describe('FavoriteIntent', () => {
  const ID = '01a04385-47b7-79c7-b3f2-62c03a8d4a88';
  const OTRA = '01a04385-47b7-79c7-b3f2-62c03a8d4a99';

  let intencion: FavoriteIntent;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({});
    intencion = TestBed.inject(FavoriteIntent);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    sessionStorage.clear();
  });

  it('guarda la intención y la devuelve una vez', () => {
    const pase = intencion.recordar(ID);

    expect(intencion.consumir(ID, pase)).toBe(true);
  });

  /**
   * El pase es lo que ata la intención a su propio recorrido.
   *
   * <p>Sin él había un fallo de verdad: una persona pulsa «Guardar» sin sesión y no entra;
   * otra entra después en esa misma pestaña, abre esa ficha por su cuenta, y se llevaba el
   * favorito que pidió la primera.
   */
  it('no la consume sin el pase, ni con uno que no es el suyo', () => {
    intencion.recordar(ID);

    expect(intencion.consumir(ID, null)).toBe(false);
    expect(intencion.consumir(ID, 'un-pase-inventado')).toBe(false);
  });

  /** Y sigue ahí: un intento fallido no la gasta. */
  it('sobrevive a un intento con el pase equivocado', () => {
    const pase = intencion.recordar(ID);

    intencion.consumir(ID, 'otro');

    expect(intencion.consumir(ID, pase)).toBe(true);
  });

  /** Se consume una sola vez: es lo que impide que el favorito reaparezca. */
  it('no la devuelve dos veces', () => {
    const pase = intencion.recordar(ID);

    expect(intencion.consumir(ID, pase)).toBe(true);
    expect(intencion.consumir(ID, pase)).toBe(false);
  });

  /** Y se borra de verdad, no solo se deja de devolver. */
  it('la borra del almacenamiento al consumirla', () => {
    const pase = intencion.recordar(ID);
    intencion.consumir(ID, pase);

    expect(sessionStorage.length).toBe(0);
  });

  /** Una intención sobre otra ficha no se consume aquí, y tampoco se pierde. */
  it('no consume la intención de otra publicación', () => {
    const pase = intencion.recordar(ID);

    expect(intencion.consumir(OTRA, pase)).toBe(false);
    expect(intencion.consumir(ID, pase)).toBe(true);
  });

  /** Criterio 9: abandonar el ingreso no deja nada guardado. */
  it('descarta lo que hubiera', () => {
    const pase = intencion.recordar(ID);

    intencion.descartar();

    expect(intencion.consumir(ID, pase)).toBe(false);
    expect(sessionStorage.length).toBe(0);
  });

  it('no falla al descartar cuando no hay nada', () => {
    expect(() => intencion.descartar()).not.toThrow();
  });

  /**
   * Vence sola. Sin esto, una pestaña abierta toda la mañana guardaría al entrar algo que
   * se pulsó horas antes.
   */
  it('descarta una intención vencida', () => {
    const pase = intencion.recordar(ID);

    vi.useFakeTimers();
    vi.setSystemTime(Date.now() + 11 * 60 * 1000);

    expect(intencion.consumir(ID, pase)).toBe(false);
  });

  /**
   * Y justo por debajo del vencimiento sigue valiendo. Sin este lado, poner la vigencia en
   * cero dejaría la suite en verde y el criterio 8 roto entero.
   */
  it('conserva una intención que todavía no ha vencido', () => {
    const pase = intencion.recordar(ID);

    vi.useFakeTimers();
    vi.setSystemTime(Date.now() + 9 * 60 * 1000);

    expect(intencion.consumir(ID, pase)).toBe(true);
  });

  /** Un valor corrupto no es una intención, y se limpia para no volver a tropezar. */
  it('descarta un valor que no se puede leer', () => {
    sessionStorage.setItem('sendik.favorito.pendiente', 'esto-no-es-json');

    expect(intencion.consumir(ID, 'da-igual')).toBe(false);
    expect(sessionStorage.length).toBe(0);
  });

  /**
   * Sin almacenamiento —incógnito, o el navegador configurado para bloquearlo— la
   * intención se pierde y la persona pulsa otra vez. Lo que no puede pasar es que la
   * excepción salga de aquí y la ficha no se pinte.
   */
  it('no lanza cuando el almacenamiento no está disponible', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('almacenamiento bloqueado');
    });
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('almacenamiento bloqueado');
    });

    expect(() => intencion.recordar(ID)).not.toThrow();
    expect(intencion.consumir(ID, 'da-igual')).toBe(false);
  });
});
