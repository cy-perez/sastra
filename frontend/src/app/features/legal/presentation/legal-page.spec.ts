import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import type { LegalContent } from '../application/legal-content.resolver';
import { LegalPage } from './legal-page';

describe('LegalPage', () => {
  const render = async (contenido: LegalContent) => {
    const fixture = TestBed.createComponent(LegalPage);
    fixture.componentRef.setInput('contenido', contenido);
    await fixture.whenStable();
    return fixture;
  };

  const conTexto = (html: string | null, version = '2026-08-01'): LegalContent => ({
    documento: { id: 'privacy', version, locale: 'es' },
    html,
  });

  beforeEach(() => {
    TestBed.configureTestingModule({});
  });

  it('muestra el titulo del documento que le toca', async () => {
    const fixture = await render(conTexto('<p>El texto.</p>'));

    expect(fixture.nativeElement.querySelector('h1')?.textContent).toContain(
      'Política de tratamiento de datos',
    );
  });

  /**
   * La version es lo unico que permite comprobar, meses despues, que el texto
   * que alguien acepto es este. Sin ella la evidencia guardada apunta a un
   * documento que no se puede identificar (docs/operacion/datos-personales.md).
   */
  it('muestra en pantalla la version vigente', async () => {
    const fixture = await render(conTexto('<p>El texto.</p>'));

    expect(fixture.nativeElement.textContent).toContain('2026-08-01');
  });

  it('inserta el texto del documento', async () => {
    const fixture = await render(conTexto('<h2>Primera parte</h2><p>El texto.</p>'));

    expect(fixture.nativeElement.querySelector('h2')?.textContent).toBe('Primera parte');
  });

  /**
   * El texto pasa por el desinfectante de Angular. Es un archivo nuestro, pero
   * la proteccion no se apaga por eso: bypassSecurityTrust no se usa aqui.
   */
  it('descarta cualquier guion que venga dentro del texto', async () => {
    const fixture = await render(conTexto('<p>Antes</p><script>alert(1)</script><p>Despues</p>'));

    expect(fixture.nativeElement.querySelector('script')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Despues');
  });

  /**
   * Un borrador enlazado desde una casilla de consentimiento es peor que no
   * tener pagina: la persona creeria haber leido algo que no la obliga a nada.
   */
  it('avisa cuando la version es un borrador', async () => {
    const fixture = await render(conTexto('<p>Relleno.</p>', 'borrador-local'));

    const aviso = fixture.nativeElement.querySelector('aside') as HTMLElement;
    expect(aviso.textContent).toContain('no tiene valor legal');
    // La advertencia se distingue por su etiqueta, no solo por el color del
    // borde: un color no puede ser el unico portador de informacion.
    expect(aviso.querySelector('strong')?.textContent).toBe('Aviso:');
  });

  /**
   * Sin region viva. El aviso ya esta en la pagina al primer renderizado, y una
   * que nace con su contenido no se anuncia: quien llegue por la direccion
   * directa no oiria nada. Al navegar desde el pie si dispararia, interrumpiendo
   * el titulo de la pagina. Dos comportamientos distintos segun como se llegue.
   */
  it('no usa una region viva para un aviso que ya estaba', async () => {
    const fixture = await render(conTexto('<p>Relleno.</p>', 'borrador-local'));

    expect(fixture.nativeElement.querySelectorAll('[role="alert"]')).toHaveLength(0);
    expect(fixture.nativeElement.querySelectorAll('[role="status"]')).toHaveLength(0);
  });

  it('no avisa de borrador con una version publicada', async () => {
    const fixture = await render(conTexto('<p>El texto.</p>'));

    expect(fixture.nativeElement.querySelector('aside')).toBeNull();
  });

  // Falta el archivo de esa version: es un fallo de despliegue, no del visitante.
  // Se dice, en vez de dejar la pagina en blanco.
  it('explica que el documento no se pudo cargar', async () => {
    const fixture = await render(conTexto(null));

    expect(fixture.nativeElement.textContent).toContain('No pudimos cargar este documento');
  });
});
