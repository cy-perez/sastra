import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { VerificationReviewApi } from './verification-review.api';

/** El adaptador HTTP de la bandeja. HTTP simulado: ninguna prueba sale a la red. */
describe('VerificationReviewApi', () => {
  let api: VerificationReviewApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(VerificationReviewApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('pide la bandeja con página y tamaño', () => {
    void api.pendientes();

    const peticion = http.expectOne((r) => r.url === 'verifications');
    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.params.get('page')).toBe('0');
    expect(peticion.request.params.get('size')).toBe('20');
    peticion.flush({ items: [], page: 0, size: 20 });
  });

  /**
   * Los nombres son los del contrato y no los del código.
   *
   * <p>Esta ruta pedía `?limite=`, en español y sin desplazamiento. Sin esta prueba,
   * volver a ese nombre no rompe nada de esta mitad: el servidor ignoraría el parámetro
   * desconocido y devolvería la primera página como si nada.
   */
  it('no usa el nombre viejo del parámetro', () => {
    void api.pendientes(2, 5);

    const peticion = http.expectOne((r) => r.url === 'verifications');
    expect(peticion.request.params.get('limite')).toBeNull();
    expect(peticion.request.params.get('page')).toBe('2');
    expect(peticion.request.params.get('size')).toBe('5');
    peticion.flush({ items: [], page: 2, size: 5 });
  });

  /**
   * <strong>Bytes, no una dirección.</strong> Es lo que permite saber quién miró: un
   * enlace que funciona por sí solo no puede registrar quién lo usó (ADR-0018, RN-046).
   * Si esto pasara a devolver una URL, la bitácora dejaría de significar nada.
   */
  it('pide la imagen como bytes', () => {
    void api.imagen('una-solicitud', 'document-front');

    const peticion = http.expectOne(
      (r) => r.url === 'verifications/una-solicitud/images/document-front',
    );
    expect(peticion.request.responseType).toBe('blob');
    peticion.flush(new Blob(['x']));
  });

  /**
   * Criterio 6: la interfaz manda siempre un motivo y el moderador no lo escribe. Sin
   * él, la bitácora queda con actor y sin motivo, que es lo que HU-002 dice que no debe
   * pasar.
   */
  it('manda un motivo en cada lectura de imagen, para la bitácora', () => {
    void api.imagen('una-solicitud', 'selfie');

    const peticion = http.expectOne((r) => r.url === 'verifications/una-solicitud/images/selfie');
    expect(peticion.request.params.get('motivo')).toBeTruthy();
    peticion.flush(new Blob(['x']));
  });

  it('aprueba sin cuerpo', () => {
    void api.aprobar('una-solicitud');

    const peticion = http.expectOne('verifications/una-solicitud/approval');
    expect(peticion.request.method).toBe('POST');
    peticion.flush(null);
  });

  it('rechaza con motivo y nota', () => {
    void api.rechazar('una-solicitud', 'ILLEGIBLE_PHOTOS', 'El reverso sale oscuro');

    const peticion = http.expectOne('verifications/una-solicitud/rejection');
    expect(peticion.request.body).toEqual({
      reason: 'ILLEGIBLE_PHOTOS',
      note: 'El reverso sale oscuro',
    });
    peticion.flush(null);
  });

  it('rechaza sin nota, que es opcional', () => {
    void api.rechazar('una-solicitud', 'EXPIRED_DOCUMENT', null);

    const peticion = http.expectOne('verifications/una-solicitud/rejection');
    expect(peticion.request.body).toEqual({ reason: 'EXPIRED_DOCUMENT', note: null });
    peticion.flush(null);
  });
});
