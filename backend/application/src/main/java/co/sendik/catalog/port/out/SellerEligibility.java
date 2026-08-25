package co.sendik.catalog.port.out;

import co.sendik.catalog.model.SellerId;

/**
 * Si un vendedor puede publicar hoy. RN-011 y RN-013.
 *
 * <p><strong>Es un puerto y no una consulta a las tablas de identity.</strong> Un
 * contexto no lee el estado de otro (docs/arquitectura/vision-tecnica.md); pregunta lo
 * que necesita saber y deja que el adaptador decida a quien preguntar. Lo que catalog
 * necesita saber cabe en un booleano: no le hace falta el estado de la verificacion, ni
 * los intentos, ni la cedula.
 */
public interface SellerEligibility {

    /** Verificado y con el sello vigente. Falso si nunca lo tuvo o si se lo revocaron. */
    boolean puedePublicar(SellerId vendedor);
}
