import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';

import { APP_CONFIG, type AppConfig, type CompanyInfo } from '../../../core/config/app-config';
import { SiteFooter } from './site-footer';

describe('SiteFooter', () => {
  const render = async () => {
    const fixture = TestBed.createComponent(SiteFooter);
    await fixture.whenStable();
    return fixture;
  };

  /** Sustituye solo los datos de empresa; el resto de la configuracion de prueba sigue igual. */
  const conEmpresa = (empresa: CompanyInfo) => {
    const base = TestBed.inject(APP_CONFIG);
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: APP_CONFIG, useValue: { ...base, company: empresa } satisfies AppConfig },
      ],
    });
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });

  /**
   * La politica de tratamiento tiene que ser un enlace visible en el pie: es
   * obligatorio y es lo primero que revisa una autoridad
   * (docs/operacion/datos-personales.md).
   */
  it('enlaza los tres documentos legales', async () => {
    const fixture = await render();
    const destinos = Array.from(fixture.nativeElement.querySelectorAll('a[href]')).map((enlace) =>
      (enlace as HTMLAnchorElement).getAttribute('href'),
    );

    expect(destinos).toEqual(
      expect.arrayContaining(['/terminos', '/tratamiento-de-datos', '/politica-de-cookies']),
    );
  });

  // Van en una lista dentro de su propia region, para que quien navegue por
  // landmarks los encuentre sin recorrer el pie entero.
  it('agrupa los enlaces en una region con nombre', async () => {
    const fixture = await render();
    const nav = fixture.nativeElement.querySelector('nav') as HTMLElement;

    expect(nav.getAttribute('aria-label')).toBe('Documentos legales');
    expect(nav.querySelectorAll('li')).toHaveLength(3);
  });

  /** Criterio 12: salen de la configuracion, nunca escritos en la plantilla. */
  it('muestra razon social, NIT y direccion de la configuracion', async () => {
    const fixture = await render();
    const datos = fixture.nativeElement.querySelector('address') as HTMLElement;

    expect(datos.textContent).toContain('Sastra S.A.S.');
    expect(datos.textContent).toContain('1054994043-1');
    expect(datos.textContent).toContain('Medellin, Colombia');
  });

  /**
   * Caso borde: un despliegue con la configuracion a medias. Se omite el dato
   * que falta y se conservan los demas; nunca se pinta el hueco.
   */
  it('omite cada dato de empresa que falte sin perder los demas', async () => {
    conEmpresa({
      name: 'Sastra S.A.S.',
      taxId: null,
      address: null,
      supportEmail: 'hola@sastra.co',
    });
    const fixture = await render();
    const datos = fixture.nativeElement.querySelector('address') as HTMLElement;

    expect(datos.textContent).toContain('Sastra S.A.S.');
    expect(datos.textContent).not.toContain('NIT');
    expect(datos.textContent).not.toContain('null');
  });

  /**
   * Criterio 14. Es ademas la via por la que se ejercen los derechos del titular
   * de los datos, asi que un pie sin el incumple la Ley 1581
   * (docs/operacion/datos-personales.md).
   */
  it('ofrece el correo de soporte como canal de contacto', async () => {
    const fixture = await render();
    const correo = fixture.nativeElement.querySelector(
      'a[href^="mailto:"]',
    ) as HTMLAnchorElement | null;

    expect(correo?.getAttribute('href')).toBe('mailto:hola@sastra.co');
    expect(correo?.textContent?.trim()).toBe('hola@sastra.co');
  });

  // Sin correo configurado no se pinta una columna de contacto vacia: se calla.
  it('no pinta la columna de contacto si no hay correo configurado', async () => {
    conEmpresa({ name: 'Sastra S.A.S.', taxId: null, address: null, supportEmail: null });
    const fixture = await render();

    expect(fixture.nativeElement.querySelector('a[href^="mailto:"]')).toBeNull();
  });

  /**
   * Criterio 15: el logo en tinta sobre la franja oscura desaparece, y la franja
   * es tinta en los dos modos. Va siempre la version monocroma negativa.
   */
  it('usa el logo monocromo negativo dentro de la franja oscura', async () => {
    const fixture = await render();
    const pie = fixture.nativeElement.querySelector('footer') as HTMLElement;
    const logo = pie.querySelector('img') as HTMLImageElement;

    expect(pie.classList).toContain('franja-oscura');
    expect(logo.getAttribute('src')).toBe('/logo-mono-negativo.svg');
    expect(logo.getAttribute('alt')).toBe('Sastra');
  });
});
