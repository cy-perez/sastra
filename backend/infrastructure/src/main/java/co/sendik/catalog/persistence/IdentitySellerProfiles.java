package co.sendik.catalog.persistence;

import co.sendik.catalog.dto.SellerProfileView;
import co.sendik.catalog.model.SellerId;
import co.sendik.catalog.port.out.SellerEligibility;
import co.sendik.catalog.port.out.SellerProfiles;
import co.sendik.identity.model.UserId;
import co.sendik.identity.usecase.ReadPublicProfileUseCase;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Quien vende, preguntado a {@code identity}. HU-009.
 *
 * <p>Mismo patron que {@link VerifiedSellerEligibility} y por la misma razon: se pregunta
 * por un caso de uso publico del otro contexto, nunca por sus tablas. Leer {@code users}
 * desde aqui ataria el catalogo al esquema de identidad, y el dia que esa tabla cambie
 * esto se romperia en silencio.
 *
 * <p>El caso de uso al que pregunta devuelve {@code PublicProfileView}, que solo lleva
 * nombre y foto. **El criterio 19 —que el perfil no exponga el correo ni el documento— no
 * lo cumple este adaptador acordandose de filtrar: lo cumple el tipo, que no tiene donde
 * llevarlos.**
 *
 * <p>La insignia se compone aqui, con la elegibilidad que ya existia. Son dos preguntas a
 * {@code identity} y no una porque son dos cosas distintas: quien eres y si estas
 * verificado. Juntarlas en un caso de uso de identidad le pediria al otro contexto que
 * supiera para que lo quiere el catalogo.
 */
@Component
public class IdentitySellerProfiles implements SellerProfiles {

    private final ReadPublicProfileUseCase perfiles;
    private final SellerEligibility elegibilidad;

    public IdentitySellerProfiles(ReadPublicProfileUseCase perfiles, SellerEligibility elegibilidad) {
        this.perfiles = perfiles;
        this.elegibilidad = elegibilidad;
    }

    @Override
    public Optional<SellerProfileView> buscar(SellerId vendedor) {
        return perfiles.execute(new UserId(vendedor.value()))
                .map(persona -> new SellerProfileView(
                        vendedor, persona.nombre(), persona.avatar(), elegibilidad.puedePublicar(vendedor)));
    }
}
