import { describe, expect, it } from 'vitest';

import {
  categoriaPorId,
  ESTADOS,
  esEstadoConocido,
  posicionesAPintar,
  precioFormateado,
  tomaEn,
  tomasDelVendedor,
  type Category,
  type Listing,
  type ListingImage,
} from './listing';

/**
 * El vocabulario que comparten el formulario del vendedor y la bandeja del moderador.
 *
 * <p>Las reglas del formulario —qué condiciones ofrece una categoría, cuántas tomas faltan
 * para enviar— se prueban en `features/listing/domain/publish-rules.spec.ts`, que es donde
 * viven. Aquí solo lo que las dos mitades usan.
 */
describe('el vocabulario de la publicación', () => {
  const toma = (position: number): ListingImage => ({
    id: `toma-${position}`,
    kind: 'SELLER_SHOT',
    position,
    angleDegrees: position * 45,
    url: `https://cdn.sendik.co/productos/${position}.jpg`,
  });

  const referencia: ListingImage = {
    id: 'referencia-0',
    kind: 'REFERENCE',
    position: 0,
    angleDegrees: null,
    url: 'https://cdn.sendik.co/productos/ref-0.jpg',
  };

  // Moldes parciales a proposito: estas funciones solo miran las tomas y el numero de
  // tomas exigidas, y rellenar el resto de la publicacion no probaria nada mas.
  const publicacion = (images: readonly ListingImage[], requiredShots = 8): Listing =>
    ({ images, requiredShots }) as unknown as Listing;

  describe('las tomas', () => {
    /** RN-066: una imagen de referencia nunca cuenta como toma del vendedor. */
    it('separa las tomas de las imágenes de referencia', () => {
      expect(tomasDelVendedor(publicacion([toma(0), toma(1), referencia]))).toHaveLength(2);
    });

    it('encuentra la toma de una posición', () => {
      const conDos = publicacion([toma(0), toma(3)]);

      expect(tomaEn(conDos, 3)?.position).toBe(3);
      expect(tomaEn(conDos, 5)).toBeNull();
    });

    /** Una de referencia en la misma posición no puede hacerse pasar por la toma. */
    it('no devuelve una imagen de referencia al buscar una toma', () => {
      expect(tomaEn(publicacion([referencia]), 0)).toBeNull();
    });

    /** RN-065: la tecnología sellada pide cuatro, y son las canónicas del empaque. */
    it('pinta cuatro casillas en la sellada y ocho en el resto', () => {
      expect(posicionesAPintar(publicacion([], 4))).toEqual([0, 2, 4, 6]);
      expect(posicionesAPintar(publicacion([]))).toEqual([0, 1, 2, 3, 4, 5, 6, 7]);
    });
  });

  describe('el precio formateado', () => {
    it('escribe el peso colombiano sin decimales', () => {
      const formateado = precioFormateado({ amount: 185000, currency: 'COP' }, 'es');

      // Se comprueban las partes y no la cadena entera: el separador de miles lo decide la
      // configuracion regional del entorno, y fijarlo aqui haria la prueba fragil.
      expect(formateado).toContain('185');
      expect(formateado).toContain('000');
      expect(formateado).not.toMatch(/[.,]\d{2}$/);
    });

    it('lleva el símbolo de la moneda', () => {
      expect(precioFormateado({ amount: 1000, currency: 'COP' }, 'es')).toMatch(/\$|COP/);
    });

    /** El importe es un entero de pesos: redondear aquí sería inventar centavos. */
    it('no inventa decimales', () => {
      expect(precioFormateado({ amount: 1, currency: 'COP' }, 'en')).not.toContain('.0');
    });
  });

  describe('categorías', () => {
    const camisas = { id: 'cat-camisas', slug: 'camisas-y-blusas' } as unknown as Category;
    const arbol = [
      { id: 'familia-tops', slug: 'tops', children: [camisas] } as unknown as Category,
    ];

    it('encuentra una hoja por su identificador', () => {
      expect(categoriaPorId(arbol, camisas.id)?.slug).toBe('camisas-y-blusas');
      expect(categoriaPorId(arbol, 'no-existe')).toBeNull();
    });

    /** Una familia no es publicable: no puede salir de la búsqueda por identificador. */
    it('no devuelve una familia', () => {
      expect(categoriaPorId(arbol, 'familia-tops')).toBeNull();
    });
  });

  /**
   * Los siete estados de RN-061, y el guardia que decide si un texto es uno de ellos.
   *
   * <p>Se prueba aquí, sin TestBed, porque es dominio puro. Lo usa el adaptador para
   * descartar en la frontera un estado que el servidor añada antes de que esta pantalla lo
   * conozca (HU-012), y probarlo solo de rebote desde una prueba de componente con HTTP
   * simulado lo dejaba en el nivel equivocado.
   */
  describe('los estados de la publicación', () => {
    it('son los siete del glosario, en el orden del ciclo de vida', () => {
      expect(ESTADOS).toEqual([
        'DRAFT',
        'PENDING_REVIEW',
        'PUBLISHED',
        'REJECTED',
        'PAUSED',
        'SOLD',
        'ARCHIVED',
      ]);
    });

    it('reconoce cada uno de los siete', () => {
      expect(ESTADOS.every((estado) => esEstadoConocido(estado))).toBe(true);
    });

    it('no reconoce lo que no está en RN-061', () => {
      expect(esEstadoConocido('EN_LA_LUNA')).toBe(false);
      // Ni una variante de uno que sí existe: se compara entero, no por prefijo.
      expect(esEstadoConocido('draft')).toBe(false);
      expect(esEstadoConocido('DRAFT_2')).toBe(false);
      expect(esEstadoConocido('')).toBe(false);
    });
  });
});
