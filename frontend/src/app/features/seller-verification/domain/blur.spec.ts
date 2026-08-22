import { describe, expect, it } from 'vitest';

import {
  aGrises,
  estaNitida,
  UMBRAL_DE_NITIDEZ,
  varianzaDelLaplaciano,
  type ImagenEnGrises,
} from './blur';

/** Detección de desenfoque. Criterio 2 de HU-002, probada con datos sintéticos. */
describe('detección de desenfoque', () => {
  /** Un cuadro de un solo tono: no hay ningún borde que medir. */
  const plana = (ancho: number, alto: number, tono = 128): ImagenEnGrises => ({
    ancho,
    alto,
    pixeles: new Uint8Array(ancho * alto).fill(tono),
  });

  /** Franjas de un píxel en blanco y negro: el borde más marcado que existe. */
  const franjas = (ancho: number, alto: number): ImagenEnGrises => {
    const pixeles = new Uint8Array(ancho * alto);
    for (let y = 0; y < alto; y++) {
      for (let x = 0; x < ancho; x++) {
        pixeles[y * ancho + x] = x % 2 === 0 ? 0 : 255;
      }
    }
    return { ancho, alto, pixeles };
  };

  /** Un degradado suave: cambia, pero sin bordes. Es lo que parece una foto movida. */
  const degradado = (ancho: number, alto: number): ImagenEnGrises => {
    const pixeles = new Uint8Array(ancho * alto);
    for (let y = 0; y < alto; y++) {
      for (let x = 0; x < ancho; x++) {
        pixeles[y * ancho + x] = Math.round((x / (ancho - 1)) * 255);
      }
    }
    return { ancho, alto, pixeles };
  };

  it('una imagen de un solo tono no tiene detalle', () => {
    expect(varianzaDelLaplaciano(plana(20, 20))).toBe(0);
  });

  it('unas franjas marcadas tienen mucho más detalle que un degradado', () => {
    const conBordes = varianzaDelLaplaciano(franjas(20, 20));
    const sinBordes = varianzaDelLaplaciano(degradado(20, 20));

    expect(conBordes).toBeGreaterThan(sinBordes);
    expect(conBordes).toBeGreaterThan(UMBRAL_DE_NITIDEZ);
  });

  /** Lo que el criterio 2 pide rechazar: una foto movida antes de subirla. */
  it('rechaza como borrosa una imagen sin bordes', () => {
    expect(estaNitida(plana(20, 20))).toBe(false);
    expect(estaNitida(degradado(20, 20))).toBe(false);
  });

  it('acepta como nítida una imagen con bordes', () => {
    expect(estaNitida(franjas(20, 20))).toBe(true);
  });

  it('admite un umbral distinto del de por omisión', () => {
    const suave = degradado(20, 20);

    expect(estaNitida(suave, 0)).toBe(true);
    expect(estaNitida(franjas(20, 20), 1_000_000)).toBe(false);
  });

  /**
   * Sin interior no hay laplaciano que calcular. Devolver cero es lo correcto: se lee
   * como «no se puede afirmar que sea nítida», que es más seguro que afirmar que lo es.
   */
  it('no afirma nitidez cuando la imagen es demasiado pequeña', () => {
    expect(varianzaDelLaplaciano(plana(2, 2))).toBe(0);
    expect(varianzaDelLaplaciano(plana(1, 40))).toBe(0);
    expect(estaNitida(franjas(2, 2))).toBe(false);
  });

  it('no revienta si llegan menos píxeles de los que las medidas prometen', () => {
    const incompleta: ImagenEnGrises = { ancho: 10, alto: 10, pixeles: new Uint8Array(20) };

    expect(varianzaDelLaplaciano(incompleta)).toBe(0);
  });

  // --- Conversión a grises --------------------------------------------------

  it('convierte cuatro canales a un byte por píxel', () => {
    // Dos píxeles: blanco y negro.
    const rgba = new Uint8Array([255, 255, 255, 255, 0, 0, 0, 255]);

    const grises = aGrises(rgba, 2, 1);

    expect(grises.pixeles[0]).toBe(255);
    expect(grises.pixeles[1]).toBe(0);
  });

  /**
   * El ojo ve el verde mucho más que el azul. Promediar los tres canales a partes
   * iguales daría el mismo gris para los dos, y la nitidez se mide sobre el brillo que
   * la persona percibe.
   */
  it('pesa el verde más que el azul', () => {
    const verde = aGrises(new Uint8Array([0, 255, 0, 255]), 1, 1);
    const azul = aGrises(new Uint8Array([0, 0, 255, 255]), 1, 1);

    expect(verde.pixeles[0]).toBeGreaterThan(azul.pixeles[0] ?? 0);
  });

  it('ignora el canal de transparencia', () => {
    const opaco = aGrises(new Uint8Array([100, 100, 100, 255]), 1, 1);
    const transparente = aGrises(new Uint8Array([100, 100, 100, 0]), 1, 1);

    expect(opaco.pixeles[0]).toBe(transparente.pixeles[0]);
  });
});
