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

/**
 * Recorre el asistente de captura entero: las ocho tomas, con la camara. HU-003.
 *
 * <p>Se entra por el enlace de la rejilla del formulario, que es el unico camino que
 * existe, y se sale sola: al subir la octava el asistente devuelve al formulario porque ya
 * no queda paso pendiente.
 *
 * <p>No comparte nada con {@link tomarUnaFoto}, y no es un descuido. Aquella sube al
 * primer campo pendiente de una pantalla con varios; esta encadena ocho pasos de una
 * maquina que decide ella cual toca, y espera despues de cada disparo a que la subida
 * aterrice. Lo unico que tienen en comun es el dispositivo falso.
 */
export async function capturarLasOchoTomas(page: Page): Promise<void> {
  await page.getByRole('link', { name: 'Tomar las fotos con la cámara' }).click();
  await expect(page.getByRole('heading', { name: 'Tomar las fotos con la cámara' })).toBeVisible();

  await page.getByRole('button', { name: 'Tomar las fotos con la cámara' }).click();

  // Lo mismo que en `tomarUnaFoto`: hay imagen **y** esta reproduciendo. Un visor
  // enganchado que nadie arranco tiene ancho y esta congelado.
  await page.waitForFunction(() => {
    const video = document.querySelector('video');
    return video !== null && video.videoWidth > 0 && !video.paused;
  });

  const resolucion = await page.evaluate(() => {
    const video = document.querySelector('video') as HTMLVideoElement;
    return { ancho: video.videoWidth, alto: video.videoHeight };
  });

  /*
   * La hipotesis que nadie habia comprobado, y por eso se afirma aqui y no en la suite.
   *
   * `CameraService.abrir(false, true)` pide 1200 x 1600 en vertical, y el comentario que
   * explica ese numero dice que pedirla apaisada daria un recorte de 810 x 1200 que no
   * llega al minimo de RN-019. Todo eso se escribio sin poder ejecutar una sola captura de
   * verdad. Si el dispositivo falso de Chromium no honra la peticion, las ocho tomas se
   * rechazan una por una y el fallo que se ve tres lineas mas abajo es un tiempo de espera
   * agotado que no dice por que.
   *
   * Se mide sobre el recorte a 3:4, que es lo que RN-019 gobierna, y no sobre el fotograma
   * entero: el minimo se aplica a lo que queda despues de recortar.
   */
  const alto = Math.min(resolucion.alto, Math.round((resolucion.ancho * 4) / 3));
  const ancho = Math.round((alto * 3) / 4);

  const queda =
    `La camara falsa entrego ${resolucion.ancho} x ${resolucion.alto}, que recortado a 3:4 ` +
    `deja ${ancho} x ${alto}.`;

  expect(ancho, queda).toBeGreaterThanOrEqual(900);
  expect(alto, queda).toBeGreaterThanOrEqual(1200);

  const progreso = page.locator('progress.asistente__progreso');
  const fallo = page.locator('.asistente p.error');

  for (let hechas = 1; hechas <= 8; hechas++) {
    await page.getByRole('button', { name: 'Tomar la foto' }).click();

    // La octava cierra el asistente, asi que su progreso ya no esta para mirarlo.
    if (hechas === 8) {
      break;
    }

    try {
      await expect(progreso).toHaveAttribute('aria-label', `${hechas} de 8 tomas listas`);
    } catch (error: unknown) {
      // El mismo patron que `publicarYEnviarARevision`: si la pantalla explico el rechazo,
      // el fallo lo dice, y no un tiempo de espera agotado esperando un numero.
      const motivo = await fallo.textContent().catch(() => null);
      if (motivo === null) {
        throw error;
      }
      throw new Error(`El asistente rechazo la toma ${hechas}: ${motivo.trim()}`);
    }
  }

  // De vuelta en el formulario, con la rejilla llena. Es la salida del criterio 10.
  await expect(page.getByRole('heading', { name: 'Las fotos' })).toBeVisible();
  await expect(page.getByText('8 de 8 fotos')).toBeVisible();
}
