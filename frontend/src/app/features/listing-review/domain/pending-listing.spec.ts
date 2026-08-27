import { describe, expect, it } from 'vitest';

import { porEspera, type PendingListing } from './pending-listing';

/** El orden de la cola. HU-008, criterio 1. Sin TestBed: es TypeScript puro. */
describe('porEspera', () => {
  const fila = (id: string, waitingSince: string): PendingListing => ({
    id,
    title: `Publicación ${id}`,
    price: { amount: 185000, currency: 'COP' },
    waitingSince,
    requiresAttention: false,
    attentionReasons: [],
    coverUrl: null,
    own: false,
  });

  it('pone primero la que lleva más tiempo esperando', () => {
    const cola = porEspera([
      fila('nueva', '2026-08-22T10:00:00Z'),
      fila('vieja', '2026-08-01T10:00:00Z'),
      fila('media', '2026-08-15T10:00:00Z'),
    ]);

    expect(cola.map((p) => p.id)).toEqual(['vieja', 'media', 'nueva']);
  });

  /**
   * ISO 8601 se ordena bien como texto **si el desfase horario es el mismo**, y el
   * backend siempre manda en UTC. Esta prueba fija esa dependencia: el día que llegue
   * con otro desfase, falla aquí y no en la pantalla.
   */
  it('ordena bien dos instantes del mismo día', () => {
    const cola = porEspera([
      fila('tarde', '2026-08-20T17:00:00Z'),
      fila('manana', '2026-08-20T09:00:00Z'),
    ]);

    expect(cola.map((p) => p.id)).toEqual(['manana', 'tarde']);
  });

  it('no altera el arreglo que recibe', () => {
    const original = [fila('nueva', '2026-08-22T10:00:00Z'), fila('vieja', '2026-08-01T10:00:00Z')];

    porEspera(original);

    expect(original.map((p) => p.id)).toEqual(['nueva', 'vieja']);
  });

  it('devuelve vacío con una cola vacía', () => {
    expect(porEspera([])).toEqual([]);
  });
});
