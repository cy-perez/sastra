package co.sastra.identity.config;

import co.sastra.identity.model.ConsentDocument;
import co.sastra.identity.port.out.LegalDocuments;
import org.springframework.stereotype.Component;

/** Adaptador que resuelve la version vigente desde la configuracion. */
@Component
public class ConfiguredLegalDocuments implements LegalDocuments {

    private final LegalDocumentProperties propiedades;

    public ConfiguredLegalDocuments(LegalDocumentProperties propiedades) {
        this.propiedades = propiedades;
    }

    @Override
    public String versionVigente(ConsentDocument documento) {
        return switch (documento) {
            case TERMS -> propiedades.termsVersion();
            case PRIVACY -> propiedades.privacyVersion();
        };
    }
}
