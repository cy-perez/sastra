import { expect, type Page } from '@playwright/test';

/**
 * Abre la primera camara pendiente de la pagina, espera a que entregue imagen y toma la
 * foto.
 *
 * <p>Vive aqui y no dentro de una suite porque lo usan las dos que capturan —la de HU-002
 * y la de la bandeja del moderador— y una copia de este ayudante en cada una es una copia
 * que pierde el reintento. Paso: la segunda suite nacio con una version simplificada y
 * fallaba una foto de cada tantas por algo que la primera ya tenia resuelto.
 *
 * <p>Siempre la primera camara pendiente, sin indices fijos: los campos de captura estan
 * en la misma pantalla y cada uno que ya tiene foto deja de ofrecer «Abrir la cámara»,
 * asi que las posiciones se corren a medida que se avanza.
 */
export async function tomarUnaFoto(page: Page): Promise<void> {
  const tomadas = page.getByRole('button', { name: 'Tomar otra' });
  const antes = await tomadas.count();

  await page.getByRole('button', { name: 'Abrir la cámara' }).first().click();

  // Se espera a que haya imagen **y** a que este reproduciendo. Lo primero solo dice que
  // llegaron los metadatos; un visor enganchado que nadie arranco tiene ancho y esta
  // congelado, y esa es la diferencia entre ver la camara y ver un cuadro negro.
  await page.waitForFunction(() => {
    const video = document.querySelector('video');
    return video !== null && video.videoWidth > 0 && !video.paused;
  });

  /*
   * Se reintenta, y no porque la prueba sea fragil.
   *
   * El patron del dispositivo falso tiene zonas de degradado suave, y algunos fotogramas
   * caen por debajo del umbral de nitidez de verdad. Lo que la pantalla ofrece entonces es
   * volver a tomarla sin cerrar la camara, y eso es lo que hace una persona; de paso, el
   * camino de la foto borrosa queda recorrido con una foto borrosa de verdad. Bajar el
   * umbral para que pase a la primera seria cambiar una regla del producto para acomodar
   * una prueba.
   *
   * Acotado a proposito: si ningun fotograma pasara nunca, esto tiene que fallar y no
   * girar para siempre.
   */
  const nueva = tomadas.nth(antes);

  for (let intento = 0; intento < 15; intento++) {
    await page.getByRole('button', { name: 'Tomar la foto' }).first().click();

    const acepto = await nueva.waitFor({ state: 'visible', timeout: 1_000 }).then(
      () => true,
      () => false,
    );

    if (acepto) {
      return;
    }
  }

  await expect(tomadas).toHaveCount(antes + 1);
}
