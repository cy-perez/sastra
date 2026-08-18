import { describe, expect, it } from 'vitest';

import {
  comoDatoOpcional,
  elNombreEsValido,
  elTelefonoEsValido,
  esElMismoCorreo,
  laCiudadEsValida,
} from './profile';

/** Criterio 21 de HU-001. Reglas puras: sin TestBed y sin HTTP. */
describe('perfil', () => {
  describe('comoDatoOpcional', () => {
    /**
     * Vacio y ausente son lo mismo. Sin esta equivalencia, borrar la ciudad desde
     * un formulario seria imposible: el campo vaciado llegaria como cadena vacia
     * y el servidor la guardaria tal cual.
     */
    it('convierte lo vacio en ausencia', () => {
      expect(comoDatoOpcional('')).toBeNull();
      expect(comoDatoOpcional('   ')).toBeNull();
    });

    it('quita los espacios de los bordes', () => {
      expect(comoDatoOpcional('  Medellin  ')).toBe('Medellin');
    });
  });

  describe('el nombre', () => {
    it('acepta un nombre normal', () => {
      expect(elNombreEsValido('Ana Maria')).toBe(true);
    });

    it('rechaza el vacio y lo demasiado corto', () => {
      expect(elNombreEsValido('')).toBe(false);
      expect(elNombreEsValido('   ')).toBe(false);
      expect(elNombreEsValido('A')).toBe(false);
    });

    it('rechaza lo desmedido', () => {
      expect(elNombreEsValido('a'.repeat(81))).toBe(false);
      expect(elNombreEsValido('a'.repeat(80))).toBe(true);
    });
  });

  describe('la ciudad', () => {
    // Es opcional: quitarla tiene que ser valido, o no habria forma de quitarla.
    it('acepta que no este', () => {
      expect(laCiudadEsValida('')).toBe(true);
    });

    it('rechaza un texto desmedido', () => {
      expect(laCiudadEsValida('a'.repeat(81))).toBe(false);
    });
  });

  describe('el telefono', () => {
    it('acepta que no este', () => {
      expect(elTelefonoEsValido('  ')).toBe(true);
    });

    // La gente escribe el numero con parentesis, espacios y guiones.
    it('acepta los separadores de siempre', () => {
      expect(elTelefonoEsValido('+57 (300) 123-4567')).toBe(true);
      expect(elTelefonoEsValido('300 123 4567')).toBe(true);
    });

    /**
     * No se valida contra el plan de numeracion colombiano: un vendedor puede
     * tener un numero de otro pais y este dato no enruta llamadas.
     */
    it('acepta un numero de otro pais', () => {
      expect(elTelefonoEsValido('+34 612 345 678')).toBe(true);
    });

    it('rechaza lo que no es un numero', () => {
      expect(elTelefonoEsValido('no-es-numero')).toBe(false);
      expect(elTelefonoEsValido('300 123 456 ext 7')).toBe(false);
    });

    it('exige entre siete y quince digitos', () => {
      expect(elTelefonoEsValido('123456')).toBe(false);
      expect(elTelefonoEsValido('1234567')).toBe(true);
      expect(elTelefonoEsValido('1234567890123456')).toBe(false);
    });
  });

  describe('esElMismoCorreo', () => {
    // Quien lo escribe con mayusculas no se esta equivocando de direccion.
    it('compara sin distinguir mayusculas ni espacios', () => {
      expect(esElMismoCorreo('  Ana@Correo.co ', 'ana@correo.co')).toBe(true);
    });

    it('distingue direcciones distintas', () => {
      expect(esElMismoCorreo('otra@correo.co', 'ana@correo.co')).toBe(false);
    });
  });
});
