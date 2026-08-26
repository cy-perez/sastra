package co.sendik.config;

import co.sendik.catalog.port.out.Categories;
import co.sendik.catalog.port.out.ListingNotifier;
import co.sendik.catalog.port.out.ListingRepository;
import co.sendik.catalog.port.out.ModerationLog;
import co.sendik.catalog.port.out.SellerEligibility;
import co.sendik.catalog.usecase.ApproveListingUseCase;
import co.sendik.catalog.usecase.ArchiveListingUseCase;
import co.sendik.catalog.usecase.ChangeListingPriceUseCase;
import co.sendik.catalog.usecase.ChangeListingShippingUseCase;
import co.sendik.catalog.usecase.CreateListingUseCase;
import co.sendik.catalog.usecase.ListSellerListingsUseCase;
import co.sendik.catalog.usecase.PauseListingUseCase;
import co.sendik.catalog.usecase.ReadListingUseCase;
import co.sendik.catalog.usecase.RejectListingUseCase;
import co.sendik.catalog.usecase.RemoveListingImageUseCase;
import co.sendik.catalog.usecase.ReopenListingUseCase;
import co.sendik.catalog.usecase.ResumeListingUseCase;
import co.sendik.catalog.usecase.SubmitListingForReviewUseCase;
import co.sendik.catalog.usecase.TakeDownListingUseCase;
import co.sendik.catalog.usecase.UpdateListingContentUseCase;
import co.sendik.catalog.usecase.UploadListingImageUseCase;
import co.sendik.catalog.usecase.WithdrawListingUseCase;
import co.sendik.shared.config.FeatureFlags;
import co.sendik.shared.file.ImageDimensions;
import co.sendik.shared.file.ImagePolicy;
import co.sendik.shared.file.StorageProperties;
import co.sendik.shared.port.out.ImageNormalizer;
import co.sendik.shared.port.out.PublicFileStore;
import co.sendik.shared.rest.ExposedFeatures;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cableado de los casos de uso del catalogo. HU-007.
 *
 * <p>Por lo mismo que {@link IdentityWiring}: los casos de uso no llevan {@code @Service}
 * ni ninguna otra anotacion, porque el modulo {@code application} solo puede ver
 * {@code spring-tx} y una prueba de arquitectura falla si aparece cualquier otra cosa de
 * Spring. Se registran aqui, que es el modulo del cableado, y en sus pruebas se
 * construyen con {@code new}, sin contexto y sin simular un contenedor.
 *
 * <p><strong>No van detras de la bandera.</strong> Los que se apagan con
 * {@code FEATURE_PUBLISHING} son los controladores, que son la puerta; estos beans
 * existen siempre y no hacen nada si nadie los llama. Ponerles la condicion aqui
 * significaria que encender la bandera cambia que hay dentro del contexto y no solo que
 * esta expuesto, que es mas dificil de razonar y de probar.
 *
 * <p>El {@code Clock} lo declara {@link IdentityWiring}: es uno solo para toda la
 * aplicacion, en la zona de operacion y no en UTC.
 */
@Configuration
public class CatalogWiring {

    /**
     * Lo que la cadena de seguridad necesita saber de las banderas.
     *
     * <p>Se declara aqui y no en {@code presentation} porque {@code FeatureFlags} vive en
     * {@code infrastructure}, que ese modulo no ve. {@code bootstrap} es el unico que ve a
     * todos, y cablear es su trabajo.
     *
     * <p>Sin esto, las rutas de decision del moderador responderian 403 con la bandera
     * apagada, y el criterio 3 pide 404: la funcionalidad no esta, y un 403 diria que si.
     * Lo mismo vale para la revision de verificaciones de HU-002.
     *
     * <p>Vive en {@code CatalogWiring} porque es la historia que lo trajo. Si un tercer
     * contexto necesitara declarar rutas condicionadas, este bean se muda a un cableado
     * propio; con dos banderas no vale la pena todavia.
     */
    @Bean
    ExposedFeatures expuestas(FeatureFlags banderas) {
        return new ExposedFeatures(banderas.sellerVerification(), banderas.publishing());
    }

    @Bean
    CreateListingUseCase createListingUseCase(
            ListingRepository publicaciones, Categories categorias, SellerEligibility elegibilidad, Clock reloj) {
        return new CreateListingUseCase(publicaciones, categorias, elegibilidad, reloj);
    }

    @Bean
    ReadListingUseCase readListingUseCase(ListingRepository publicaciones) {
        return new ReadListingUseCase(publicaciones);
    }

    @Bean
    UpdateListingContentUseCase updateListingContentUseCase(
            ListingRepository publicaciones, Categories categorias, SellerEligibility elegibilidad, Clock reloj) {
        return new UpdateListingContentUseCase(publicaciones, categorias, elegibilidad, reloj);
    }

    @Bean
    ChangeListingPriceUseCase changeListingPriceUseCase(ListingRepository publicaciones, Clock reloj) {
        return new ChangeListingPriceUseCase(publicaciones, reloj);
    }

    @Bean
    ChangeListingShippingUseCase changeListingShippingUseCase(ListingRepository publicaciones, Clock reloj) {
        return new ChangeListingShippingUseCase(publicaciones, reloj);
    }

    /**
     * La politica de las tomas de producto. RN-018 y RN-019.
     *
     * <p><strong>Propia, y con cualificador, porque hay otra.</strong> {@code IdentityWiring}
     * declara {@code politicaDeAvatar} —200x200 y sin proporcion— y ya avisaba de que las
     * tomas tendrian la suya. Sin este bean, pedir un {@code ImagePolicy} por tipo devolvia
     * la del avatar: los criterios 14 y 15 quedaban sin aplicar y una imagen de 200x200 se
     * aceptaba como toma de producto.
     *
     * <p>La proporcion se calcula del minimo y no se configura aparte. Dos numeros que
     * pueden contradecirse entre si —3:4 por un lado y 900x1200 por otro— acaban
     * contradiciendose.
     */
    @Bean
    ImagePolicy politicaDeTomas(StorageProperties almacenamiento) {
        ImageDimensions minimo =
                new ImageDimensions(almacenamiento.listingMinWidth(), almacenamiento.listingMinHeight());

        return new ImagePolicy(almacenamiento.maxImageBytes(), minimo, (double) minimo.width() / minimo.height());
    }

    @Bean
    UploadListingImageUseCase uploadListingImageUseCase(
            ListingRepository publicaciones,
            PublicFileStore almacen,
            ImageNormalizer normalizador,
            @Qualifier("politicaDeTomas") ImagePolicy politica,
            Clock reloj) {
        return new UploadListingImageUseCase(publicaciones, almacen, normalizador, politica, reloj);
    }

    @Bean
    RemoveListingImageUseCase removeListingImageUseCase(
            ListingRepository publicaciones, PublicFileStore almacen, Clock reloj) {
        return new RemoveListingImageUseCase(publicaciones, almacen, reloj);
    }

    @Bean
    SubmitListingForReviewUseCase submitListingForReviewUseCase(
            ListingRepository publicaciones, Categories categorias, SellerEligibility elegibilidad, Clock reloj) {
        return new SubmitListingForReviewUseCase(publicaciones, categorias, elegibilidad, reloj);
    }

    @Bean
    WithdrawListingUseCase withdrawListingUseCase(ListingRepository publicaciones, Clock reloj) {
        return new WithdrawListingUseCase(publicaciones, reloj);
    }

    @Bean
    ReopenListingUseCase reopenListingUseCase(ListingRepository publicaciones, Clock reloj) {
        return new ReopenListingUseCase(publicaciones, reloj);
    }

    @Bean
    ApproveListingUseCase approveListingUseCase(
            ListingRepository publicaciones, ModerationLog bitacora, ListingNotifier avisos, Clock reloj) {
        return new ApproveListingUseCase(publicaciones, bitacora, avisos, reloj);
    }

    @Bean
    RejectListingUseCase rejectListingUseCase(
            ListingRepository publicaciones, ModerationLog bitacora, ListingNotifier avisos, Clock reloj) {
        return new RejectListingUseCase(publicaciones, bitacora, avisos, reloj);
    }

    @Bean
    TakeDownListingUseCase takeDownListingUseCase(
            ListingRepository publicaciones,
            ModerationLog bitacora,
            ListingNotifier avisos,
            PublicFileStore almacen,
            Clock reloj) {
        return new TakeDownListingUseCase(publicaciones, bitacora, avisos, almacen, reloj);
    }

    @Bean
    PauseListingUseCase pauseListingUseCase(ListingRepository publicaciones, Clock reloj) {
        return new PauseListingUseCase(publicaciones, reloj);
    }

    @Bean
    ResumeListingUseCase resumeListingUseCase(ListingRepository publicaciones, Clock reloj) {
        return new ResumeListingUseCase(publicaciones, reloj);
    }

    @Bean
    ArchiveListingUseCase archiveListingUseCase(ListingRepository publicaciones, PublicFileStore almacen, Clock reloj) {
        return new ArchiveListingUseCase(publicaciones, almacen, reloj);
    }

    @Bean
    ListSellerListingsUseCase listSellerListingsUseCase(ListingRepository publicaciones) {
        return new ListSellerListingsUseCase(publicaciones);
    }
}
