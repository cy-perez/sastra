package co.sendik.catalog.usecase;

import co.sendik.catalog.dto.SellerListingsSummary;
import co.sendik.catalog.dto.SummarizeSellerListingsQuery;
import co.sendik.catalog.model.ListingStatus;
import co.sendik.catalog.port.out.ListingRepository;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las cifras del panel del vendedor. HU-012, RN-061.
 *
 * <p>Quien vende ve de un vistazo cuantas publicaciones tiene en cada estado, sin contar
 * filas ni acordarse de cuales envio.
 *
 * <p><strong>Aqui es donde el cero existe.</strong> El repositorio devuelve solo los
 * estados con filas, porque un {@code GROUP BY} no inventa grupos vacios. Completar los
 * siete de RN-061 es una decision de producto -«0 en revision» es informacion y omitirlo
 * obliga a deducir por ausencia- y por eso vive en un caso de uso, con su prueba, y no
 * escondida en una consulta ni resuelta en la pantalla. Si viviera en la pantalla, cada
 * cliente nuevo tendria que volver a acordarse.
 *
 * <p>Solo cuenta lo del vendedor que pregunta, y el vendedor sale del contexto de
 * seguridad y nunca de un parametro de la peticion. No hay cifras de nadie mas ni
 * agregadas para Sendik: esto es el panel de quien vende.
 */
public class SummarizeSellerListingsUseCase {

    private final ListingRepository publicaciones;

    public SummarizeSellerListingsUseCase(ListingRepository publicaciones) {
        this.publicaciones = publicaciones;
    }

    @Transactional
    public SellerListingsSummary execute(SummarizeSellerListingsQuery consulta) {
        Map<ListingStatus, Long> contadas = publicaciones.contarPorEstadoDelVendedor(consulta.vendedor());

        Map<ListingStatus, Long> completo = new EnumMap<>(ListingStatus.class);
        for (ListingStatus estado : ListingStatus.values()) {
            completo.put(estado, contadas.getOrDefault(estado, 0L));
        }

        return new SellerListingsSummary(completo);
    }
}
