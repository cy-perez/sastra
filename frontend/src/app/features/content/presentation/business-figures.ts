/**
 * Formateo de las cifras de negocio que el sitio informativo anuncia.
 *
 * <p>Funcion pura y no un servicio: no tiene estado, no habla con nadie y asi se
 * prueba sin TestBed. Lo que si tiene es una decision que conviene que este en un
 * solo sitio.
 *
 * <p><strong>La comision se guarda como fraccion y se muestra como porcentaje.</strong>
 * 0.05 en la configuracion, "5%" en pantalla. Hacerlo con `Intl` y no con
 * `rate * 100 + '%'` importa por el idioma: en ingles se escribe `5%` y en
 * espanol `5 %`, con espacio duro. Un porcentaje compuesto a mano se ve mal en
 * uno de los dos y nadie vuelve a mirarlo.
 *
 * <p>Sin decimales cuando no los hay: 0.05 es "5%", no "5.0%". Con ellos si los
 * lleva, porque una comision del 5.5% no se puede redondear en una pagina que la
 * anuncia (docs/operacion/configuracion.md).
 */
export function formatearComision(rate: number, locale: string): string {
  return new Intl.NumberFormat(locale, {
    style: 'percent',
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  }).format(rate);
}
