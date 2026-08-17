/**
 * Traduccion del formato de error de la API a algo que la interfaz pueda usar.
 *
 * El backend nunca manda texto para mostrar: manda un `code` estable, y el
 * texto sale de Transloco. Ver docs/arquitectura/contrato-api.md. Por eso aqui
 * se conserva el codigo y se descarta `detail`, que es para trazabilidad.
 */

export interface FieldError {
  readonly field: string;
  readonly code: string;
}

/** ProblemDetail segun RFC 9457, con los campos que agrega el contrato. */
export interface ProblemDetail {
  readonly status?: number;
  readonly code?: string;
  readonly traceId?: string;
  readonly errors?: readonly FieldError[];
}

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string | null,
    readonly traceId: string | null,
    readonly fieldErrors: readonly FieldError[],
  ) {
    super(code ?? `HTTP ${status}`);
    this.name = 'ApiError';
  }

  /** Estado 0: la peticion no llego a salir o no hubo respuesta. */
  get isNetworkFailure(): boolean {
    return this.status === 0;
  }

  /** Clave de Transloco con la que se muestra este error. Nunca el texto del servidor. */
  get translationKey(): string {
    if (this.isNetworkFailure) {
      return 'errors.network';
    }
    return this.code === null ? 'errors.fallback' : `errors.byCode.${this.code}`;
  }
}

/**
 * Un cuerpo que no cumpla el contrato no debe romper la interfaz: se degrada a
 * un error sin codigo, que la interfaz ya sabe mostrar con el texto de reserva.
 */
export function toApiError(status: number, body: unknown): ApiError {
  const problem = isProblemDetail(body) ? body : null;

  return new ApiError(
    status,
    typeof problem?.code === 'string' ? problem.code : null,
    typeof problem?.traceId === 'string' ? problem.traceId : null,
    readFieldErrors(problem),
  );
}

function isProblemDetail(body: unknown): body is ProblemDetail {
  return typeof body === 'object' && body !== null;
}

function readFieldErrors(problem: ProblemDetail | null): readonly FieldError[] {
  if (!Array.isArray(problem?.errors)) {
    return [];
  }

  return problem.errors.filter(
    (entry): entry is FieldError =>
      typeof entry === 'object' &&
      entry !== null &&
      typeof entry.field === 'string' &&
      typeof entry.code === 'string',
  );
}
