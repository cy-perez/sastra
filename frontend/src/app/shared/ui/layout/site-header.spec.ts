import { DOCUMENT } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';

import { SiteHeader } from './site-header';
import { ThemeService } from '../../../core/theme/theme.service';

/**
 * Se consulta por rol y por texto accesible, que es como lo encuentra quien usa
 * el sitio: una prueba atada al selector CSS se rompe al maquetar y no dice
 * nada sobre si el control se puede usar.
 */
describe('SiteHeader', () => {
  let document: Document;

  const render = async () => {
    const fixture = TestBed.createComponent(SiteHeader);
    await fixture.whenStable();
    return fixture;
  };

  const byLabel = (label: string): HTMLElement | null =>
    document.querySelector(`[aria-label="${label}"]`);

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    document = TestBed.inject(DOCUMENT);
    document.documentElement.setAttribute('data-tema', 'claro');
  });

  it('ofrece un enlace para saltar al contenido como primer elemento enfocable', async () => {
    const fixture = await render();
    const link = fixture.nativeElement.querySelector('a');

    expect(link?.getAttribute('href')).toBe('#contenido');
    expect(link?.textContent?.trim()).toBe('Saltar al contenido');
  });

  it('nombra el logo una sola vez, en el enlace y no en las imagenes', async () => {
    const fixture = await render();
    const home = byLabel('Sastra, ir al inicio');
    const images = fixture.nativeElement.querySelectorAll('img');

    expect(home).not.toBeNull();
    expect(images).toHaveLength(2);
    for (const image of images) {
      expect((image as HTMLImageElement).getAttribute('alt')).toBe('');
    }
  });

  it('presenta los idiomas disponibles con su etiqueta asociada', async () => {
    const fixture = await render();
    const select = fixture.nativeElement.querySelector('select') as HTMLSelectElement;
    const label = fixture.nativeElement.querySelector(`label[for="${select.id}"]`);

    expect(label?.textContent?.trim()).toBe('Idioma');
    expect([...select.options].map((option) => option.value)).toEqual(['es', 'en']);
    expect([...select.options].map((option) => option.textContent?.trim())).toEqual([
      'Español',
      'English',
    ]);
  });

  // La etiqueta anuncia a donde lleva el boton, no en que estado esta: es lo
  // que espera oir quien no ve el icono.
  it('el conmutador de tema anuncia la accion y refleja el estado', async () => {
    const fixture = await render();
    const button = byLabel('Cambiar a modo oscuro') as HTMLButtonElement;

    expect(button).not.toBeNull();
    expect(button.getAttribute('aria-pressed')).toBe('false');

    button.click();
    await fixture.whenStable();

    expect(byLabel('Cambiar a modo claro')).not.toBeNull();
    expect(TestBed.inject(ThemeService).current()).toBe('dark');
    expect(document.documentElement.getAttribute('data-tema')).toBe('oscuro');
  });
});
