import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';

import { SiteFooter } from './site-footer';

describe('SiteFooter', () => {
  const render = async () => {
    const fixture = TestBed.createComponent(SiteFooter);
    await fixture.whenStable();
    return fixture;
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
});
