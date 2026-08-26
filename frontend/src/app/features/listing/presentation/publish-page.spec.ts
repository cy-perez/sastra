import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  apiUrlInterceptor,
  authInterceptor,
  errorInterceptor,
  languageInterceptor,
} from '../../../core/http/interceptors';
import type { Session } from '../../../core/session/session';
import { APP_CONFIG } from '../../../core/config/app-config';
import { SessionStore } from '../../../core/session/session.store';
import { ESPERA_DE_GUARDADO, PublishPage } from './publish-page';

/**
 * El formulario de publicar. HU-007.
 *
 * Lo que se comprueba es lo que la persona ve y puede hacer, no los métodos del
 * componente: qué se ofrece según el estado, qué se marca cuando el servidor dice que
 * falta algo, y que el envío no se ofrezca antes de tiempo.
 */
describe('PublishPage', () => {
  const API = 'https://api.pruebas.sendik.co/api/v1';
  const ID = 'af8b9a52-4a3f-4a52-9a1e-8d9a2f1c4b70';

  const SESION: Session = {
    accessToken: 'un-token',
    user: { email: 'ana@correo.co', displayName: 'Ana Maria', emailVerified: true, roles: [] },
  };

  /**
   * La configuración con otro plazo.
   *
   * <p>Se escribe entera y no se deriva de la de pruebas con un `TestBed.inject`: esa
   * inyección instancia el módulo antes de que `overrideProvider` llegue a correr.
   */
  const CONFIG_CON_PLAZO_DE_CINCO = {
    apiBaseUrl: API,
    defaultLocale: 'es',
    availableLocales: ['es', 'en'],
    enableDevtools: false,
    sentryDsn: null,
    legalVersions: {
      terms: 'borrador-local',
      privacy: 'borrador-local',
      cookies: 'borrador-local',
    },
    company: { name: null, taxId: null, address: null, supportEmail: null },
    business: {
      commissionRate: 0.05,
      claimWindowDays: 3,
      verificationReviewDays: 2,
      listingReviewDays: 5,
    },
  };

  const ARBOL = [
    {
      id: 'familia-tops',
      slug: 'tops',
      nameEs: 'Parte superior',
      nameEn: 'Tops',
      familySlug: null,
      sizeSystems: [],
      requiredMeasurements: [],
      allowsUsed: true,
      children: [
        {
          id: 'hoja-camisas',
          slug: 'camisas-y-blusas',
          nameEs: 'Camisas y blusas',
          nameEn: 'Shirts and blouses',
          familySlug: 'tops',
          sizeSystems: ['ALPHA'],
          requiredMeasurements: ['CHEST', 'LENGTH'],
          allowsUsed: true,
          children: [],
        },
      ],
    },
    {
      id: 'familia-tech',
      slug: 'tech',
      nameEs: 'Tecnología',
      nameEn: 'Tech',
      familySlug: null,
      sizeSystems: [],
      requiredMeasurements: [],
      allowsUsed: false,
      children: [
        {
          id: 'hoja-celulares',
          slug: 'celulares-y-tabletas',
          nameEs: 'Celulares y tabletas',
          nameEn: 'Phones and tablets',
          familySlug: 'tech',
          sizeSystems: ['ONE_SIZE'],
          requiredMeasurements: ['HEIGHT', 'WIDTH', 'DEPTH'],
          allowsUsed: false,
          children: [],
        },
      ],
    },
  ];

  const borrador = (cambios: Record<string, unknown> = {}) => ({
    id: ID,
    sellerId: 'vendedor',
    status: 'DRAFT',
    product: {
      categoryId: 'hoja-camisas',
      title: 'Camisa de lino',
      description: 'Usada dos veces.',
      brand: null,
      condition: 'LIKE_NEW',
      size: { system: 'ALPHA', value: 'M' },
      measurements: { CHEST: 52 },
      color: 'BEIGE',
      price: { amount: 185000, currency: 'COP' },
      shipping: { weightGrams: 600, lengthCm: 30, widthCm: 20, heightCm: 10 },
      isSealed: null,
      warrantyMonths: null,
    },
    images: [],
    requiredShots: 8,
    requiresAttention: false,
    attentionReasons: [],
    rejectionReason: null,
    rejectionNote: null,
    publishedAt: null,
    createdAt: '2026-08-26T10:00:00Z',
    updatedAt: '2026-08-26T10:00:00Z',
    version: 1,
    ...cambios,
  });

  const conOchoTomas = () =>
    Array.from({ length: 8 }, (_, position) => ({
      id: `toma-${position}`,
      kind: 'SELLER_SHOT',
      position,
      angleDegrees: position * 45,
      url: `https://cdn.sendik.co/productos/${position}.jpg`,
    }));

  const asentar = async (fixture: {
    whenStable: () => Promise<unknown>;
    detectChanges: () => void;
  }) => {
    for (let vuelta = 0; vuelta < 5; vuelta++) {
      await new Promise((listo) => setTimeout(listo, 0));
      fixture.detectChanges();
      await fixture.whenStable();
    }
  };

  /**
   * Monta la página con —o sin— identificador en la ruta.
   *
   * <p>La ruta se suple con un {@code ActivatedRoute} de prueba en lugar de navegar de
   * verdad: lo único que la página lee de ella es el `paramMap`, y montar un enrutador
   * completo para eso metería en la prueba una pieza que no se está probando.
   *
   * <p>Va por un sujeto con valor inicial porque la página lee el parámetro de forma
   * síncrona al construirse: un observable sin valor la dejaría sin ruta.
   */
  /**
   * Deja pasar unos turnos y vuelve a pintar, sin esperar la estabilidad.
   *
   * <p>`whenStable` no sirve cuando queda alguna peticion sin responder: Angular la
   * cuenta como tarea pendiente y la espera no termina hasta que la prueba conteste.
   */
  const bombear = async (fixture: { detectChanges: () => void }) => {
    for (let vuelta = 0; vuelta < 5; vuelta++) {
      await new Promise((listo) => setTimeout(listo, 0));
      fixture.detectChanges();
    }
  };

  const montar = async (publicacion: object | null) => {
    if (publicacion !== null) {
      parametros.next(convertToParamMap({ id: ID }));
    }
    // La sesión se pone aquí y no en el beforeEach: alli instancia el modulo de pruebas
    // y ninguna prueba puede ya sustituir un proveedor.
    TestBed.inject(SessionStore).set(SESION);

    const fixture = TestBed.createComponent(PublishPage);
    await fixture.whenStable();

    const backend = TestBed.inject(HttpTestingController);

    backend
      .expectOne((peticion) => peticion.method === 'GET' && peticion.url === `${API}/categories`)
      .flush(ARBOL);

    if (publicacion !== null) {
      backend
        .expectOne(
          (peticion) => peticion.method === 'GET' && peticion.url === `${API}/listings/${ID}`,
        )
        .flush(publicacion);
    }
    await asentar(fixture);

    return { fixture, backend };
  };

  /**
   * Escribe en un control y deja pasar la espera del guardado automático.
   *
   * <p>Con relojes reales el debounce de 1,5 s es inalcanzable en una prueba: sin esto,
   * el guardado de avance que pide el criterio 5 no se puede comprobar.
   */
  const escribir = async (
    fixture: {
      nativeElement: HTMLElement;
      whenStable: () => Promise<unknown>;
      detectChanges: () => void;
    },
    selector: string,
    valor: string,
  ) => {
    const campo = fixture.nativeElement.querySelector(selector) as HTMLInputElement;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));

    // Sin `whenStable`: Angular cuenta una peticion HTTP sin responder como tarea
    // pendiente, asi que esperar la estabilidad aqui se queda colgado hasta que la
    // prueba responda.
    await bombear(fixture);
  };

  const boton = (fixture: { nativeElement: HTMLElement }, texto: string) =>
    [...fixture.nativeElement.querySelectorAll('button')].find((candidato) =>
      candidato.textContent?.includes(texto),
    ) as HTMLButtonElement | undefined;

  let parametros: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

  beforeEach(() => {
    parametros = new BehaviorSubject(convertToParamMap({}));

    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'publicar/:id', component: PublishPage }]),
        { provide: ActivatedRoute, useValue: { paramMap: parametros.asObservable() } },
        // Sin espera: el guardado automático se comprueba por lo que manda, no por
        // cuánto tarda en mandarlo.
        { provide: ESPERA_DE_GUARDADO, useValue: 0 },
        provideHttpClient(
          withInterceptors([
            apiUrlInterceptor,
            authInterceptor,
            languageInterceptor,
            errorInterceptor,
          ]),
        ),
        provideHttpClientTesting(),
      ],
    });
  });

  /** Sin identificador es el paso previo: elegir categoría y crear el borrador. */
  it('pide la categoría antes de crear el borrador', async () => {
    const { fixture } = await montar(null);

    const texto = fixture.nativeElement.textContent ?? '';
    expect(texto).toContain('Publica tu producto');
    expect(fixture.nativeElement.querySelector('#categoria-nueva')).not.toBeNull();
    expect(boton(fixture, 'Empezar')?.disabled).toBe(true);
  });

  /** Criterio 19: en revisión no se edita, y lo que se ofrece es retirar. */
  it('bloquea el formulario y ofrece retirar mientras está en revisión', async () => {
    const { fixture } = await montar(
      borrador({ status: 'PENDING_REVIEW', images: conOchoTomas() }),
    );

    // Por la salida accesible y no por una clase de estilo: `matches(':disabled')` sí
    // considera el fieldset que envuelve al campo, mientras que `input.disabled` refleja
    // solo su propio atributo.
    const titulo = fixture.nativeElement.querySelector('#titulo') as HTMLInputElement;
    expect(titulo.matches(':disabled')).toBe(true);
    expect(boton(fixture, 'Retirar de revisión')).toBeDefined();
    expect(boton(fixture, 'Enviar a revisión')).toBeUndefined();
  });

  /** Criterio 22: el vendedor ve el motivo y la nota, y puede retomar. */
  it('muestra el motivo del rechazo y ofrece corregir', async () => {
    const { fixture } = await montar(
      borrador({
        status: 'REJECTED',
        rejectionReason: 'PHOTOS_UNUSABLE',
        rejectionNote: 'Se ven movidas.',
        images: conOchoTomas(),
      }),
    );

    const texto = fixture.nativeElement.textContent ?? '';
    expect(texto).toContain('No pudimos publicarla');
    expect(texto).toContain('Las fotos no se pueden usar');
    expect(texto).toContain('Se ven movidas.');
    expect(boton(fixture, 'Corregir y volver a enviar')).toBeDefined();
  });

  /** RN-016 y RN-017: sin las ocho tomas no se ofrece enviar. */
  it('no deja enviar sin las tomas completas', async () => {
    const { fixture } = await montar(borrador());

    expect(boton(fixture, 'Enviar a revisión')?.disabled).toBe(true);
  });

  it('deja enviar con las ocho tomas puestas', async () => {
    const { fixture } = await montar(borrador({ images: conOchoTomas() }));

    expect(boton(fixture, 'Enviar a revisión')?.disabled).toBe(false);
  });

  /**
   * El plazo sale de configuración y nunca quemado en el texto.
   *
   * <p>Con el valor por omisión —que también es 2— la prueba daba verde aunque alguien
   * escribiera el número en el archivo de traducción. Con otro distinto, solo pasa si de
   * verdad viene de la configuración.
   */
  it('promete el plazo de revisión que dice la configuración', async () => {
    TestBed.overrideProvider(APP_CONFIG, { useValue: CONFIG_CON_PLAZO_DE_CINCO });

    const { fixture } = await montar(borrador());

    expect(fixture.nativeElement.textContent).toContain('5 días hábiles');
    expect(fixture.nativeElement.textContent).not.toContain('2 días hábiles');
  });

  /**
   * Criterio 6: el servidor manda los campos que faltan y la pantalla los nombra.
   *
   * Sin esto la persona lee «faltan datos» y tiene que adivinar cuáles.
   */
  it('nombra los campos que el servidor dice que faltan', async () => {
    const { fixture, backend } = await montar(borrador({ images: conOchoTomas() }));

    boton(fixture, 'Enviar a revisión')?.click();
    await fixture.whenStable();

    backend
      .expectOne(
        (peticion) =>
          peticion.method === 'POST' && peticion.url === `${API}/listings/${ID}/submission`,
      )
      .flush(
        {
          code: 'CATALOG_LISTING_INCOMPLETE',
          errors: [
            { field: 'title', code: 'VALIDATION_REQUIRED' },
            { field: 'price', code: 'VALIDATION_REQUIRED' },
          ],
        },
        { status: 422, statusText: 'Unprocessable Content' },
      );
    await asentar(fixture);

    // Se asierta sobre la lista, no sobre el texto de la página: «Título» y «Precio»
    // son las etiquetas del formulario y se pintan siempre, así que buscarlas en el
    // textContent daba verde aunque la lista no existiera.
    const faltantes = fixture.nativeElement.querySelector('.publicar__faltantes');
    expect(faltantes?.textContent).toContain('Título');
    expect(faltantes?.textContent).toContain('Precio');

    // Y la validación por campo, que es lo que pide la historia: el campo queda
    // marcado y su descripción apunta al mensaje que lo nombra.
    const titulo = fixture.nativeElement.querySelector('#titulo') as HTMLInputElement;
    expect(titulo.getAttribute('aria-invalid')).toBe('true');
    expect(titulo.getAttribute('aria-describedby')).toContain('falta-title');
    expect(fixture.nativeElement.querySelector('#falta-title')).not.toBeNull();

    // El que no falta no se marca.
    const marca = fixture.nativeElement.querySelector('#marca') as HTMLInputElement;
    expect(marca.getAttribute('aria-invalid')).toBeNull();
  });

  /**
   * Las medidas llegan como `measurements.CHEST`.
   *
   * <p>Componer `listing.form.` con eso daba una clave que no existe, y en pantalla salía
   * el nombre de la clave en crudo en vez del nombre de la medida.
   */
  it('nombra también las medidas que faltan, criterio 10', async () => {
    const { fixture, backend } = await montar(borrador({ images: conOchoTomas() }));

    boton(fixture, 'Enviar a revisión')?.click();
    await fixture.whenStable();

    backend
      .expectOne(
        (llamada) =>
          llamada.method === 'POST' && llamada.url === `${API}/listings/${ID}/submission`,
      )
      .flush(
        {
          code: 'CATALOG_LISTING_INCOMPLETE',
          errors: [{ field: 'measurements.CHEST', code: 'VALIDATION_REQUIRED' }],
        },
        { status: 422, statusText: 'Unprocessable Content' },
      );
    await asentar(fixture);

    const faltantes = fixture.nativeElement.querySelector('.publicar__faltantes');
    expect(faltantes?.textContent).toContain('Pecho');
    expect(faltantes?.textContent).not.toContain('measurements');
  });

  /** RN-064: en tecnología solo se ofrece «nuevo», y aparecen sellado y garantía. */
  it('ofrece solo «nuevo», sellado y garantía en tecnología', async () => {
    const { fixture } = await montar(
      borrador({
        product: { ...borrador().product, categoryId: 'hoja-celulares', condition: 'NEW' },
      }),
    );

    const texto = fixture.nativeElement.textContent ?? '';
    expect(texto).toContain('En esta categoría solo se publica lo nuevo');
    expect(texto).toContain('Está sellado, sin abrir');
    expect(texto).toContain('Meses de garantía del fabricante');
    expect(texto).not.toContain('Con detalles');
  });

  /** Criterio 27: editar una viva la devuelve a moderación, y hay que avisarlo antes. */
  it('avisa de que editar una publicada la devuelve a revisión', async () => {
    const { fixture } = await montar(borrador({ status: 'PUBLISHED', images: conOchoTomas() }));

    expect(fixture.nativeElement.textContent).toContain('vuelve a revisión');
  });

  // --- Criterio 4: crear el borrador y llegar a su formulario -------------

  it('crea el borrador con la categoría elegida y lleva a su formulario', async () => {
    const { fixture, backend } = await montar(null);
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    const select = fixture.nativeElement.querySelector('#categoria-nueva') as HTMLSelectElement;
    select.value = 'hoja-camisas';
    select.dispatchEvent(new Event('change'));
    await asentar(fixture);

    expect(boton(fixture, 'Empezar')?.disabled).toBe(false);
    boton(fixture, 'Empezar')?.click();
    await fixture.whenStable();

    const peticion = backend.expectOne(
      (llamada) => llamada.method === 'POST' && llamada.url === `${API}/listings`,
    );
    expect(peticion.request.body).toEqual({ categoryId: 'hoja-camisas' });

    peticion.flush(borrador());
    await asentar(fixture);

    expect(navegar).toHaveBeenCalledWith(['/publicar', ID]);
  });

  // --- Criterio 5: el avance no se pierde ---------------------------------

  /**
   * Salir a la mitad y volver retoma donde iba.
   *
   * <p>Se leen los valores de los controles y no el texto de la página: el texto no
   * incluye lo que hay dentro de un input, así que una prueba sobre textContent daría
   * verde aunque el formulario naciera vacío.
   */
  it('retoma el borrador con lo que ya estaba escrito, criterio 5', async () => {
    const { fixture } = await montar(borrador());

    const titulo = fixture.nativeElement.querySelector('#titulo') as HTMLInputElement;
    const precio = fixture.nativeElement.querySelector('#precio') as HTMLInputElement;
    const categoria = fixture.nativeElement.querySelector('#categoria') as HTMLSelectElement;

    expect(titulo.value).toBe('Camisa de lino');
    expect(precio.value).toBe('185000');
    expect(categoria.value).toBe('hoja-camisas');
  });

  it('guarda solo lo que lleva cuando se deja de escribir, criterio 5', async () => {
    const { fixture, backend } = await montar(borrador());

    await escribir(fixture, '#titulo', 'Camisa de lino con detalles');

    const peticion = backend.expectOne(
      (llamada) => llamada.method === 'PATCH' && llamada.url === `${API}/listings/${ID}`,
    );
    expect(peticion.request.body.title).toBe('Camisa de lino con detalles');

    peticion.flush(borrador());
    await asentar(fixture);
    expect(fixture.nativeElement.textContent).toContain('Guardado');
  });

  /** El guardado prometía que no se pierde nada y fallaba sin decirlo. */
  it('avisa cuando el guardado automático falla', async () => {
    const { fixture, backend } = await montar(borrador());

    await escribir(fixture, '#titulo', 'Otro título');

    backend
      .expectOne((llamada) => llamada.method === 'PATCH' && llamada.url === `${API}/listings/${ID}`)
      .flush({ code: 'COMMON_UNEXPECTED' }, { status: 500, statusText: 'Server Error' });
    await asentar(fixture);

    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
  });

  // --- Criterio 28: el precio y el envío no pasan por moderación ----------

  /**
   * La que faltaba, y la que importa.
   *
   * <p>Mandar el precio por el PATCH general devuelve a moderación una publicación viva
   * (RN-062): tocar el precio de algo publicado lo sacaba de circulación, que es lo
   * contrario de lo que promete el criterio 28.
   */
  it('cambia el precio de una publicada por su propia ruta, criterio 28', async () => {
    const { fixture, backend } = await montar(
      borrador({ status: 'PUBLISHED', images: conOchoTomas() }),
    );

    await escribir(fixture, '#precio', '120000');

    const peticion = backend.expectOne(
      (llamada) => llamada.method === 'PATCH' && llamada.url === `${API}/listings/${ID}/price`,
    );
    expect(peticion.request.body).toEqual({ price: { amount: 120000, currency: 'COP' } });
    peticion.flush(borrador({ status: 'PUBLISHED', images: conOchoTomas() }));
    await asentar(fixture);
  });

  it('cambia el envío de una publicada por su propia ruta, criterio 28', async () => {
    const { fixture, backend } = await montar(
      borrador({ status: 'PUBLISHED', images: conOchoTomas() }),
    );

    await escribir(fixture, '[formcontrolname="weightGrams"]', '900');

    const peticion = backend.expectOne(
      (llamada) => llamada.method === 'PATCH' && llamada.url === `${API}/listings/${ID}/shipping`,
    );
    expect(peticion.request.body.weightGrams).toBe(900);
    peticion.flush(borrador({ status: 'PUBLISHED', images: conOchoTomas() }));
    await asentar(fixture);
  });

  /** Criterio 27: el contenido sí vuelve a moderación, y va por el PATCH general. */
  it('manda el contenido de una publicada por el PATCH general, criterio 27', async () => {
    const { fixture, backend } = await montar(
      borrador({ status: 'PUBLISHED', images: conOchoTomas() }),
    );

    await escribir(fixture, '#titulo', 'Otro título');

    backend
      .expectOne((llamada) => llamada.method === 'PATCH' && llamada.url === `${API}/listings/${ID}`)
      .flush(borrador({ status: 'PENDING_REVIEW', images: conOchoTomas() }));
    await asentar(fixture);
  });

  /** Sobre un borrador da igual la ruta: no hay moderación de por medio. */
  it('manda todo junto en un borrador, aunque solo cambie el precio', async () => {
    const { fixture, backend } = await montar(borrador());

    await escribir(fixture, '#precio', '99000');

    backend
      .expectOne((llamada) => llamada.method === 'PATCH' && llamada.url === `${API}/listings/${ID}`)
      .flush(borrador());
    await asentar(fixture);
  });

  // --- Estados de carga y de error ---------------------------------------

  it('pinta el esqueleto mientras carga', async () => {
    TestBed.overrideProvider(ActivatedRoute, {
      useValue: { paramMap: parametros.asObservable() },
    });
    parametros.next(convertToParamMap({ id: ID }));
    TestBed.inject(SessionStore).set(SESION);

    const fixture = TestBed.createComponent(PublishPage);
    await fixture.whenStable();

    const backend = TestBed.inject(HttpTestingController);
    backend
      .expectOne((llamada) => llamada.method === 'GET' && llamada.url === `${API}/categories`)
      .flush(ARBOL);
    const pendiente = backend.expectOne(
      (llamada) => llamada.method === 'GET' && llamada.url === `${API}/listings/${ID}`,
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.esqueleto')).not.toBeNull();
    // El encabezado está en las cuatro ramas, también mientras carga.
    expect(fixture.nativeElement.querySelector('h1')).not.toBeNull();

    pendiente.flush(borrador());
    await asentar(fixture);
  });

  /** Criterio 33: la publicación de otro responde 404, y eso es un error de pantalla. */
  it('muestra el error cuando la publicación no es suya', async () => {
    TestBed.overrideProvider(ActivatedRoute, {
      useValue: { paramMap: parametros.asObservable() },
    });
    parametros.next(convertToParamMap({ id: ID }));
    TestBed.inject(SessionStore).set(SESION);

    const fixture = TestBed.createComponent(PublishPage);
    await fixture.whenStable();

    const backend = TestBed.inject(HttpTestingController);
    backend
      .expectOne((llamada) => llamada.method === 'GET' && llamada.url === `${API}/categories`)
      .flush(ARBOL);
    backend
      .expectOne((llamada) => llamada.method === 'GET' && llamada.url === `${API}/listings/${ID}`)
      .flush({ code: 'COMMON_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    await asentar(fixture);

    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('h1')).not.toBeNull();
  });

  it('muestra el error cuando el árbol de categorías falla', async () => {
    TestBed.inject(SessionStore).set(SESION);
    const fixture = TestBed.createComponent(PublishPage);
    await fixture.whenStable();

    TestBed.inject(HttpTestingController)
      .expectOne((llamada) => llamada.method === 'GET' && llamada.url === `${API}/categories`)
      .flush({ code: 'COMMON_UNEXPECTED' }, { status: 500, statusText: 'Server Error' });
    await asentar(fixture);

    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
  });

  /**
   * La regresión que fija frontend/CLAUDE.md: en una carga de página el componente nace
   * antes de que la sesión llegue por la cookie de refresco. Aquí hay dos formas de que
   * la consulta nazca deshabilitada y no se reactive —la sesión y el identificador de la
   * ruta—, así que importa el doble.
   */
  it('pide la publicación aunque la sesión llegue después de crear el componente', async () => {
    parametros.next(convertToParamMap({ id: ID }));

    const fixture = TestBed.createComponent(PublishPage);
    // Turnos, no estabilidad: sin sesion hay ya una peticion en vuelo —el arbol es
    // publico— y esperar la estabilidad seria esperar a responderla uno mismo.
    await bombear(fixture);

    const backend = TestBed.inject(HttpTestingController);

    // El arbol es publico y no espera a nadie: llega antes que la sesion, igual que en
    // una carga de verdad.
    backend
      .expectOne((llamada) => llamada.method === 'GET' && llamada.url === `${API}/categories`)
      .flush(ARBOL);

    // Y ahora si, la sesion, por la cookie de refresco. Se deja pasar un turno en vez de
    // esperar la estabilidad: la peticion que la sesion desbloquea cuenta como tarea
    // pendiente, y esperarla aqui seria esperar a responderla uno mismo.
    TestBed.inject(SessionStore).set(SESION);
    await new Promise((listo) => setTimeout(listo, 0));
    fixture.detectChanges();

    // Se responde a todas las que haya: la consulta puede dispararse mas de una vez
    // segun en que orden lleguen la sesion y el identificador de la ruta, y lo que esta
    // prueba fija es que llegue a pedirse, no cuantas veces.
    const pendientes = backend.match((llamada) => llamada.url === `${API}/listings/${ID}`);
    expect(pendientes.length).toBeGreaterThan(0);
    pendientes.forEach((peticion) => peticion.flush(borrador()));
    await bombear(fixture);

    expect(fixture.nativeElement.textContent).toContain('Borrador');
  });

  /** Las categorías se ofrecen agrupadas por familia, con su nombre visible. */
  it('ofrece el árbol agrupado por familias', async () => {
    const { fixture } = await montar(null);

    const grupos = [...fixture.nativeElement.querySelectorAll('optgroup')] as HTMLElement[];
    expect(grupos.map((grupo) => grupo.getAttribute('label'))).toEqual([
      'Parte superior',
      'Tecnología',
    ]);
    expect(fixture.nativeElement.textContent).toContain('Camisas y blusas');
  });
});
