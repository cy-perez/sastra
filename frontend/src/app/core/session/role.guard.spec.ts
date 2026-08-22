import { EnvironmentInjector, runInInjectionContext } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, RedirectCommand } from '@angular/router';
import { firstValueFrom, type Observable } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';

import { exigirRol } from './role.guard';
import type { Session } from './session';
import { SessionStore } from './session.store';

/**
 * El guard por rol (HU-006, ADR-0021).
 *
 * <p>Lo que de verdad se prueba aqui es la espera. Que un rol correcto pase y uno
 * incorrecto no es lo facil; lo que rompio a `/mi-cuenta` en su dia, y lo que este guard
 * puede repetir, es decidir <strong>antes</strong> de que la sesion se haya resuelto.
 */
describe('exigirRol', () => {
  const sesionCon = (roles: readonly string[]): Session => ({
    accessToken: 'un-token',
    user: { email: 'quien@sastra.co', displayName: 'Quien Modera', emailVerified: true, roles },
  });

  let almacen: SessionStore;
  let inyector: EnvironmentInjector;

  /** Ejecuta el guard como lo haria el router, dentro de un contexto de inyeccion. */
  const decidir = (): Observable<boolean | RedirectCommand> =>
    runInInjectionContext(
      inyector,
      () => exigirRol('MODERATOR')(null!, null!) as Observable<boolean | RedirectCommand>,
    );

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    almacen = TestBed.inject(SessionStore);
    inyector = TestBed.inject(EnvironmentInjector);
  });

  it('deja pasar a quien tiene el rol', async () => {
    almacen.set(sesionCon(['BUYER', 'MODERATOR']));

    await expect(firstValueFrom(decidir())).resolves.toBe(true);
  });

  it('no deja pasar a quien tiene sesion pero no el rol', async () => {
    almacen.set(sesionCon(['BUYER']));

    await expect(firstValueFrom(decidir())).resolves.toBeInstanceOf(RedirectCommand);
  });

  it('no deja pasar a quien no tiene sesion', async () => {
    almacen.clear();

    await expect(firstValueFrom(decidir())).resolves.toBeInstanceOf(RedirectCommand);
  });

  /**
   * Criterio 2: quien no es moderador no puede enterarse de que la bandeja existe. Un
   * "no tienes permiso" confirma que detras hay algo, asi que se manda a la pagina de
   * "no existe" y **sin tocar la direccion**.
   */
  it('manda a la pagina de no encontrado sin cambiar la direccion', async () => {
    almacen.set(sesionCon(['BUYER']));

    const decision = (await firstValueFrom(decidir())) as RedirectCommand;

    expect(decision.redirectTo.toString()).toBe('/no-encontrado');
    expect(decision.navigationBehaviorOptions?.skipLocationChange).toBe(true);
  });

  /**
   * <strong>La prueba que justifica el guard entero.</strong>
   *
   * <p>En una recarga el componente nace antes que la sesion: el token vive en memoria y
   * se recupera despues con la cookie de refresco. Un guard que leyera el rol de
   * inmediato encontraria `desconocida` y echaria al moderador de su propia bandeja en
   * cada F5.
   *
   * <p>Se comprueba que no responde nada mientras no se sepa, y que responde en cuanto se
   * sabe. Es la trampa que ya dejo `/mi-cuenta` sin cargarse nunca.
   */
  it('espera a que la sesion se resuelva antes de decidir', () => {
    let decidido: boolean | RedirectCommand | undefined;
    decidir().subscribe((valor) => (decidido = valor));

    // La sesion todavia es `desconocida`: nadie ha respondido.
    expect(almacen.status()).toBe('desconocida');
    expect(decidido).toBeUndefined();

    almacen.set(sesionCon(['MODERATOR']));
    // `toObservable` propaga con un efecto: sin vaciar la cola de efectos la senal ya
    // cambio y el observable todavia no lo sabe.
    TestBed.tick();

    expect(decidido).toBe(true);
  });

  /** La misma espera cuando la respuesta es que no hay sesion. */
  it('espera tambien cuando la sesion resulta ser anonima', () => {
    let decidido: boolean | RedirectCommand | undefined;
    decidir().subscribe((valor) => (decidido = valor));

    expect(decidido).toBeUndefined();

    almacen.clear();
    TestBed.tick();

    expect(decidido).toBeInstanceOf(RedirectCommand);
  });

  /** El rol pedido es el que se comprueba, no cualquiera. */
  it('no confunde un rol con otro', async () => {
    almacen.set(sesionCon(['SELLER', 'ADMIN']));

    await expect(firstValueFrom(decidir())).resolves.toBeInstanceOf(RedirectCommand);
  });
});
