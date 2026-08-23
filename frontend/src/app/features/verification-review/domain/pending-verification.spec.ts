import { describe, expect, it } from 'vitest';

import {
  esperaEnMilisegundos,
  hayDiscrepanciaDeTitular,
  porAntiguedad,
  type PendingVerification,
} from './pending-verification';

/** Reglas puras de la bandeja del moderador. Sin TestBed: es TypeScript y nada más. */
describe('pending-verification', () => {
  const solicitud = (cambios: Partial<PendingVerification> = {}): PendingVerification => ({
    id: 'una-solicitud',
    attempts: 1,
    documentType: 'CC',
    documentNumberLastFour: '2947',
    documentHolderName: 'Ana Maria Garcia',
    documentSubmitted: true,
    selfieSubmitted: true,
    bank: 'bancolombia',
    bankAccountType: 'SAVINGS',
    bankAccountLastFour: '3456',
    bankAccountHolderName: 'Ana Maria Garcia',
    waitingSince: '2026-08-20T10:00:00Z',
    own: false,
    ...cambios,
  });

  describe('hayDiscrepanciaDeTitular, RN-012', () => {
    it('no señala nada cuando los dos nombres son el mismo', () => {
      expect(hayDiscrepanciaDeTitular(solicitud())).toBe(false);
    });

    it('señala cuando el titular de la cuenta es otra persona', () => {
      expect(hayDiscrepanciaDeTitular(solicitud({ bankAccountHolderName: 'Carlos Perez' }))).toBe(
        true,
      );
    });

    /**
     * Así es como los escribe la gente. Marcar esto como discrepancia haría que quien
     * revisa muchas al día dejara de mirar el aviso, y entonces el aviso no sirve para
     * la vez que sí importa.
     */
    it('no señala por mayúsculas ni por espacios de más', () => {
      expect(
        hayDiscrepanciaDeTitular(solicitud({ bankAccountHolderName: '  ANA   MARIA  garcia ' })),
      ).toBe(false);
    });

    /**
     * Y lo contrario: no se quitan tildes. «Garcia» y «García» son nombres distintos y
     * quien revisa tiene que verlo, aunque muchas veces sea solo un teclado sin tildes.
     */
    it('señala una diferencia de tildes, porque no puede decidirla sola', () => {
      expect(
        hayDiscrepanciaDeTitular(solicitud({ bankAccountHolderName: 'Ana María García' })),
      ).toBe(true);
    });

    /** Un paso sin entregar no es una discrepancia. Decirlo sería ruido. */
    it('no señala nada cuando todavía falta uno de los dos', () => {
      expect(hayDiscrepanciaDeTitular(solicitud({ bankAccountHolderName: null }))).toBe(false);
      expect(hayDiscrepanciaDeTitular(solicitud({ documentHolderName: null }))).toBe(false);
    });
  });

  describe('porAntiguedad', () => {
    it('deja primero la que lleva más tiempo esperando', () => {
      const nueva = solicitud({ id: 'nueva', waitingSince: '2026-08-22T10:00:00Z' });
      const vieja = solicitud({ id: 'vieja', waitingSince: '2026-08-01T10:00:00Z' });
      const media = solicitud({ id: 'media', waitingSince: '2026-08-15T10:00:00Z' });

      expect(porAntiguedad([nueva, vieja, media]).map((s) => s.id)).toEqual([
        'vieja',
        'media',
        'nueva',
      ]);
    });

    it('no altera el arreglo que recibe', () => {
      const original = [
        solicitud({ id: 'nueva', waitingSince: '2026-08-22T10:00:00Z' }),
        solicitud({ id: 'vieja', waitingSince: '2026-08-01T10:00:00Z' }),
      ];

      porAntiguedad(original);

      expect(original.map((s) => s.id)).toEqual(['nueva', 'vieja']);
    });
  });

  describe('esperaEnMilisegundos', () => {
    it('mide contra el instante que recibe, no contra el reloj', () => {
      const espera = esperaEnMilisegundos(
        solicitud({ waitingSince: '2026-08-20T10:00:00Z' }),
        new Date('2026-08-22T10:00:00Z'),
      );

      expect(espera).toBe(2 * 24 * 60 * 60 * 1000);
    });

    /**
     * El reloj del navegador puede ir atrasado respecto al del servidor. Una espera
     * negativa se pintaría como «hace -3 minutos», que es peor que decir «ahora mismo».
     */
    it('no devuelve una espera negativa con el reloj del navegador atrasado', () => {
      const espera = esperaEnMilisegundos(
        solicitud({ waitingSince: '2026-08-22T10:00:00Z' }),
        new Date('2026-08-22T09:55:00Z'),
      );

      expect(espera).toBe(0);
    });
  });
});
