package co.sendik.catalog.port.out;

import co.sendik.catalog.dto.SellerProfileView;
import co.sendik.catalog.model.SellerId;
import java.util.Optional;

/**
 * Quien vende, como lo ve cualquiera. HU-009.
 *
 * <p>Puerto y no consulta a las tablas de {@code identity}, por lo mismo que
 * {@link SellerEligibility}: un contexto no lee el estado de otro, pregunta lo que
 * necesita saber. Lo que el catalogo necesita de una persona son tres cosas —como se
 * llama, que foto tiene y si esta verificada— y ninguna de ellas es su correo.
 *
 * <p>Responde vacio si no existe o si la cuenta esta cerrada. Las dos salen como 404 y no
 * se distinguen desde fuera.
 */
public interface SellerProfiles {

    Optional<SellerProfileView> buscar(SellerId vendedor);
}
