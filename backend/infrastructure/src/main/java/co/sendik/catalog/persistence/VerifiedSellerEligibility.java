package co.sendik.catalog.persistence;

import co.sendik.catalog.model.SellerId;
import co.sendik.catalog.port.out.SellerEligibility;
import co.sendik.identity.model.SellerVerification;
import co.sendik.identity.model.UserId;
import co.sendik.identity.model.VerificationStatus;
import co.sendik.identity.usecase.ReadSellerVerificationUseCase;
import org.springframework.stereotype.Component;

/**
 * Si un vendedor puede publicar hoy. RN-011 y RN-013.
 *
 * <p><strong>Pregunta por un caso de uso publico de {@code identity}, no por sus
 * tablas.</strong> Es lo que {@code docs/arquitectura/vision-tecnica.md} permite —«si
 * necesita algo, es por un caso de uso publico o por un evento de dominio»— y lo que
 * prohibe es justo lo alternativo: leer {@code seller_verifications} desde aqui seria
 * atar el catalogo al esquema de otro contexto, y cualquier cambio alli romperia esto
 * en silencio.
 *
 * <p>Aqui es tambien donde se traduce entre los dos identificadores. {@code SellerId}
 * y {@code UserId} envuelven el mismo UUID y son tipos distintos a proposito; el sitio
 * para pasar de uno a otro es el borde entre contextos, que es este.
 *
 * <p>La respuesta es un booleano y no el estado. {@code REVOKED} y «nunca empezo» dan
 * lo mismo para lo que catalog necesita decidir, y devolver el estado invitaria a que
 * alguien tomara decisiones de identidad desde el catalogo.
 */
@Component
public class VerifiedSellerEligibility implements SellerEligibility {

    private final ReadSellerVerificationUseCase verificaciones;

    public VerifiedSellerEligibility(ReadSellerVerificationUseCase verificaciones) {
        this.verificaciones = verificaciones;
    }

    @Override
    public boolean puedePublicar(SellerId vendedor) {
        return verificaciones
                .execute(new UserId(vendedor.value()))
                .map(SellerVerification::status)
                .filter(VerificationStatus::esVerificado)
                .isPresent();
    }
}
