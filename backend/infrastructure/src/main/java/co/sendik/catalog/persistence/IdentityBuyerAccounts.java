package co.sendik.catalog.persistence;

import co.sendik.catalog.model.BuyerId;
import co.sendik.catalog.port.out.BuyerAccounts;
import co.sendik.identity.model.UserId;
import co.sendik.identity.usecase.ReadPublicProfileUseCase;
import org.springframework.stereotype.Component;

/**
 * Si la cuenta sigue existiendo, preguntado a {@code identity}. HU-011.
 *
 * <p>Mismo patron que {@link VerifiedSellerEligibility} y {@link IdentitySellerProfiles}, y
 * por la misma razon: se pregunta por un caso de uso publico del otro contexto, nunca por
 * sus tablas.
 *
 * <p><strong>Se apoya en que el perfil publico no existe para una cuenta cerrada.</strong>
 * Lo dice {@code ReadPublicProfileUseCase} en su javadoc y lo hace por partida doble: filtra
 * el estado, y el repositorio al que pregunta ya excluye las cerradas. Vacio significa aqui
 * «cerrada o nunca existio», y para lo que hay que decidir las dos cosas son la misma.
 *
 * <p>La respuesta es un booleano y no el perfil, por lo mismo que en
 * {@link VerifiedSellerEligibility}: devolver el perfil invitaria a tomar decisiones de
 * identidad desde el catalogo.
 */
@Component
public class IdentityBuyerAccounts implements BuyerAccounts {

    private final ReadPublicProfileUseCase perfiles;

    public IdentityBuyerAccounts(ReadPublicProfileUseCase perfiles) {
        this.perfiles = perfiles;
    }

    @Override
    public boolean estaActiva(BuyerId quien) {
        return perfiles.execute(new UserId(quien.value())).isPresent();
    }
}
