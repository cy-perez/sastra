import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';

import { HomePage } from './home-page';

describe('HomePage', () => {
  const render = async () => {
    const fixture = TestBed.createComponent(HomePage);
    await fixture.whenStable();
    return fixture;
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });

  /** Criterio 1 y criterio 20: un solo h1, y es la propuesta de valor. */
  /** Texto de cada elemento que case, en orden de documento. */
  const textos = (raiz: HTMLElement, selector: string): string[] =>
    Array.from(raiz.querySelectorAll(selector) as NodeListOf<HTMLElement>).map(
      (elemento) => elemento.textContent?.trim() ?? '',
    );

  it('tiene un unico h1 con la propuesta de valor', async () => {
    const fixture = await render();

    expect(textos(fixture.nativeElement, 'h1')).toEqual(['Compra y vende moda con respaldo']);
  });

  /**
   * Criterio 2. El acento ocre aparece una sola vez por pantalla, siempre como
   * relleno. Se cuenta en el DOM porque es la unica forma de que la regla no se
   * erosione: el segundo boton ocre siempre parece justificado por si solo.
   */
  it('pinta exactamente un elemento con el acento ocre', async () => {
    const fixture = await render();

    expect(fixture.nativeElement.querySelectorAll('.btn-cta')).toHaveLength(1);
  });

  /**
   * Criterio 3. Es un enlace y no un boton: lo que hace es navegar, asi que
   * funciona sin JavaScript y se puede abrir en otra pestana.
   *
   * <p>Lleva siempre a /registro, tambien con sesion abierta: el servidor no
   * puede saber si la hay, y un destino condicional dejaria el hero sin boton en
   * el HTML servido (criterio 18) o lo cambiaria al hidratar.
   */
  it('el boton principal es un enlace al registro', async () => {
    const fixture = await render();
    const cta = fixture.nativeElement.querySelector('.btn-cta') as HTMLAnchorElement;

    expect(cta.tagName).toBe('A');
    expect(cta.getAttribute('href')).toBe('/registro');
    expect(cta.textContent?.trim()).toBe('Crear cuenta');
  });

  /** Criterio 4: publicar es gratis y solo se cobra al vender, sin cifra. */
  it('dice que publicar es gratis sin escribir el porcentaje', async () => {
    const fixture = await render();
    const nota = fixture.nativeElement.querySelector('.hero-nota') as HTMLElement;

    expect(nota.textContent).toContain('Publicar es gratis');
    expect(nota.textContent).not.toContain('%');
  });

  /** Criterio 6: publicar, vender y cobrar, en ese orden. */
  it('muestra los tres pasos en orden dentro de una lista ordenada', async () => {
    const fixture = await render();

    expect(textos(fixture.nativeElement, 'ol.pasos li h3')).toEqual([
      'Publicas tu prenda',
      'Alguien la compra',
      'Cobras al confirmarse la entrega',
    ]);
  });

  /**
   * Criterio 7. La ruta /como-funciona llega con HU-005; hasta entonces no se
   * pinta el enlace. Un enlace roto en portada es peor que no tenerlo.
   */
  it('no enlaza paginas informativas que todavia no existen', async () => {
    const fixture = await render();
    const destinos = Array.from(
      fixture.nativeElement.querySelectorAll('a[href]') as NodeListOf<HTMLAnchorElement>,
    ).map((enlace) => enlace.getAttribute('href'));

    expect(destinos).toEqual(['/registro']);
  });

  /** Criterio 8: el unico elemento decorativo del sistema, y no una linea continua. */
  it('separa los bloques con la regla de puntada', async () => {
    const fixture = await render();

    expect(fixture.nativeElement.querySelector('hr.regla-puntada')).not.toBeNull();
  });

  /** Criterio 9: retencion del pago, vendedores verificados y publicaciones moderadas. */
  it('muestra las tres tarjetas de confianza', async () => {
    const fixture = await render();

    expect(textos(fixture.nativeElement, 'ul.tarjetas li h3')).toEqual([
      'El pago queda retenido',
      'Vendedores verificados',
      'Publicaciones revisadas',
    ]);
  });

  /**
   * Criterio 10. La maqueta original prometia "Devolucion en 3 dias" y esa
   * politica no existe: devoluciones es Fase 4 y no hay documento legal que la
   * respalde. La prueba existe para que no vuelva por la puerta de atras.
   */
  it('no promete devoluciones, reembolsos ni plazos', async () => {
    const fixture = await render();
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(texto).not.toMatch(/devoluci[oó]n|reembolso|d[ií]as h[aá]biles/i);
  });

  /** Criterio 20: sin saltos de nivel. Del h1 se pasa a h2 y de ahi a h3. */
  it('no salta ningun nivel de encabezado', async () => {
    const fixture = await render();
    const niveles = Array.from(
      fixture.nativeElement.querySelectorAll('h1, h2, h3, h4') as NodeListOf<HTMLElement>,
    ).map((titulo) => Number(titulo.tagName.slice(1)));

    // Empieza en 1 y nunca baja mas de un nivel de golpe. Subir varios de vuelta
    // si es valido: cerrar dos secciones anidadas y abrir otra de primer nivel.
    const saltos = niveles.slice(1).map((nivel, indice) => nivel - (niveles[indice] ?? nivel));

    expect(niveles.at(0)).toBe(1);
    expect(saltos.every((salto) => salto <= 1)).toBe(true);
  });

  /** El hero pide el carril a sangre y trae la franja que rescata el foco. */
  it('el hero sangra a ancho completo dentro de la franja oscura', async () => {
    const fixture = await render();
    const hero = fixture.nativeElement.querySelector('.hero') as HTMLElement;

    expect(hero.classList).toContain('a-sangre');
    expect(hero.classList).toContain('franja-oscura');
  });
});
