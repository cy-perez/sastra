package co.sendik.catalog.usecase;

import co.sendik.catalog.dto.SellerProfileView;
import co.sendik.catalog.model.SellerId;
import co.sendik.catalog.port.out.SellerProfiles;
import java.util.Optional;

/**
 * El perfil publico de un vendedor. HU-009, criterios 18 y 19.
 *
 * <p>Es una linea, y aun asi es un caso de uso y no una llamada suelta desde el
 * controlador: el dia que el perfil deje de mostrarse para alguien —una cuenta suspendida,
 * un vendedor sin ninguna publicacion nunca— la regla entra aqui y no en el borde.
 *
 * <p>Responde vacio si no existe, si la cuenta se cerro o si el identificador no es de
 * nadie. Las tres salen como 404 y no se distinguen: decir «esta persona existe pero no
 * te la muestro» ya es decir algo.
 */
public class ReadSellerProfileUseCase {

    private final SellerProfiles perfiles;

    public ReadSellerProfileUseCase(SellerProfiles perfiles) {
        this.perfiles = perfiles;
    }

    public Optional<SellerProfileView> execute(SellerId vendedor) {
        return perfiles.buscar(vendedor);
    }
}
