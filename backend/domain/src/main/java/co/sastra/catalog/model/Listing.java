package co.sastra.catalog.model;

import co.sastra.catalog.exception.InvalidListingTransitionException;
import co.sastra.catalog.exception.ReferenceImageNotAllowedException;
import co.sastra.catalog.exception.ShotsIncompleteException;
import co.sastra.shared.money.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * La publicacion: raiz del agregado de catalogo. HU-007.
 *
 * <p>Contiene el producto y sus imagenes porque comparten invariantes que no se pueden
 * comprobar por separado. "Ocho tomas para publicar" necesita ver a la vez el estado,
 * las imagenes y si el producto esta sellado; con tres agregados, esa comprobacion
 * quedaria repartida y nadie seria responsable de ella (ADR-0022).
 *
 * <p>Inmutable como el resto del dominio: cada paso devuelve una instancia nueva.
 *
 * <p>Las transiciones son RN-061 y viven en {@link ListingStatus}. Aqui se decide
 * <em>cuando</em> se intenta cada una y con que condiciones; que sea legal lo dice el
 * enum, y ningun metodo de esta clase se salta esa comprobacion.
 */
public final class Listing {

    /** RN-020: rango blando. Fuera de el se publica, marcado para revision mas atenta. */
    public static final Money PRECIO_MINIMO_RAZONABLE = Money.dePesos(10_000);

    public static final Money PRECIO_MAXIMO_RAZONABLE = Money.dePesos(20_000_000);

    /** RN-065: la tecnologia sellada se queda en las cuatro canonicas del empaque. */
    public static final int TOMAS_SI_ESTA_SELLADO = 4;

    private final ListingId id;
    private final Product product;
    private final ListingStatus status;
    private final List<ProductImage> images;

    private final @Nullable Instant publishedAt;
    private final @Nullable ModeratorId moderatedBy;
    private final @Nullable Instant moderatedAt;
    private final @Nullable ListingRejectionReason rejectionReason;
    private final @Nullable String rejectionNote;
    private final @Nullable AttentionReason attentionReason;

    private final Instant createdAt;
    private final Instant updatedAt;

    private Listing(
            ListingId id,
            Product product,
            ListingStatus status,
            List<ProductImage> images,
            @Nullable Instant publishedAt,
            @Nullable ModeratorId moderatedBy,
            @Nullable Instant moderatedAt,
            @Nullable ListingRejectionReason rejectionReason,
            @Nullable String rejectionNote,
            @Nullable AttentionReason attentionReason,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "El identificador es obligatorio");
        this.product = Objects.requireNonNull(product, "El producto es obligatorio");
        this.status = Objects.requireNonNull(status, "El estado es obligatorio");
        this.images = List.copyOf(images);
        this.publishedAt = publishedAt;
        this.moderatedBy = moderatedBy;
        this.moderatedAt = moderatedAt;
        this.rejectionReason = rejectionReason;
        this.rejectionNote = rejectionNote;
        this.attentionReason = attentionReason;
        this.createdAt = Objects.requireNonNull(createdAt, "La fecha de creacion es obligatoria");
        this.updatedAt = Objects.requireNonNull(updatedAt, "La fecha de actualizacion es obligatoria");
    }

    /** Criterio 4: nace en borrador, sin imagenes, y con la marca de RN-020 si toca. */
    public static Listing crearBorrador(ListingId id, Product producto, Instant ahora) {
        return new Listing(
                id,
                producto,
                ListingStatus.DRAFT,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                marcaPorPrecio(producto.price()),
                ahora,
                ahora);
    }

    /** Reconstruye lo guardado. Solo lo usa la capa de persistencia. */
    public static Listing existente(
            ListingId id,
            Product product,
            ListingStatus status,
            List<ProductImage> images,
            @Nullable Instant publishedAt,
            @Nullable ModeratorId moderatedBy,
            @Nullable Instant moderatedAt,
            @Nullable ListingRejectionReason rejectionReason,
            @Nullable String rejectionNote,
            @Nullable AttentionReason attentionReason,
            Instant createdAt,
            Instant updatedAt) {
        return new Listing(
                id,
                product,
                status,
                images,
                publishedAt,
                moderatedBy,
                moderatedAt,
                rejectionReason,
                rejectionNote,
                attentionReason,
                createdAt,
                updatedAt);
    }

    // ------------------------------------------------------------------ imagenes

    /**
     * Agrega o reemplaza una imagen. Criterio 16: nunca hay dos en la misma posicion.
     *
     * @throws ReferenceImageNotAllowedException si es de referencia y el producto no es
     *     tecnologia declarada sellada (RN-066)
     */
    public Listing conImagen(ProductImage imagen, Instant ahora) {
        Objects.requireNonNull(imagen, "La imagen es obligatoria");
        exigirEditable();

        if (imagen.kind() == ImageKind.REFERENCE && !product.estaSellado()) {
            throw new ReferenceImageNotAllowedException(
                    product.esTecnologia() ? "el producto no se declaro sellado" : "el producto no es tecnologia");
        }

        List<ProductImage> nuevas = new ArrayList<>(images);
        nuevas.removeIf(otra -> otra.kind() == imagen.kind() && otra.position() == imagen.position());
        nuevas.add(imagen);
        nuevas.sort(Comparator.comparing(ProductImage::kind).thenComparingInt(ProductImage::position));

        return copiaCon(status, nuevas, ahora);
    }

    public Listing sinImagen(ProductImageId imagenId, Instant ahora) {
        Objects.requireNonNull(imagenId, "El identificador de la imagen es obligatorio");
        exigirEditable();

        List<ProductImage> nuevas = new ArrayList<>(images);
        nuevas.removeIf(imagen -> imagen.id().equals(imagenId));
        return copiaCon(status, nuevas, ahora);
    }

    /** Las que cuentan para RN-016 y RN-017. Una de referencia nunca cuenta. */
    public List<ProductImage> tomasDelVendedor() {
        return images.stream().filter(ProductImage::esTomaDelVendedor).toList();
    }

    public List<ProductImage> imagenesDeReferencia() {
        return images.stream().filter(imagen -> !imagen.esTomaDelVendedor()).toList();
    }

    /** RN-065: ocho en general, cuatro si es tecnologia declarada sellada. */
    public int tomasExigidas() {
        return product.estaSellado() ? TOMAS_SI_ESTA_SELLADO : ProductImage.TOMAS_DE_LA_SECUENCIA;
    }

    // ------------------------------------------------------------------ ciclo

    /**
     * Criterio 19. Comprueba las tomas antes de mover el estado.
     *
     * <p>Que el producto este completo —medidas, sobre todo— lo comprueba el caso de
     * uso con la categoria delante: esta clase no la conoce.
     *
     * @throws ShotsIncompleteException si faltan tomas o alguna canonica
     */
    public Listing enviarARevision(Instant ahora) {
        exigirTransicion(ListingStatus.PENDING_REVIEW);
        exigirTomasCompletas();
        return copiaCon(ListingStatus.PENDING_REVIEW, images, ahora);
    }

    /** Criterio 20: retirar antes de que se decida. */
    public Listing retirarDeRevision(Instant ahora) {
        exigirTransicion(ListingStatus.DRAFT);
        return copiaCon(ListingStatus.DRAFT, images, ahora);
    }

    /** Criterio 23: retomar una rechazada conserva datos e imagenes. */
    public Listing retomar(Instant ahora) {
        exigirTransicion(ListingStatus.DRAFT);
        return new Listing(
                id,
                product,
                ListingStatus.DRAFT,
                images,
                publishedAt,
                moderatedBy,
                moderatedAt,
                rejectionReason,
                rejectionNote,
                attentionReason,
                createdAt,
                ahora);
    }

    /** Criterio 21. */
    public Listing aprobar(ModeratorId moderador, Instant ahora) {
        Objects.requireNonNull(moderador, "El moderador es obligatorio");
        exigirTransicion(ListingStatus.PUBLISHED);

        return new Listing(
                id,
                product,
                ListingStatus.PUBLISHED,
                images,
                ahora,
                moderador,
                ahora,
                null,
                null,
                attentionReason,
                createdAt,
                ahora);
    }

    /** Criterio 22: motivo de la lista cerrada y nota opcional. */
    public Listing rechazar(
            ModeratorId moderador, ListingRejectionReason motivo, @Nullable String nota, Instant ahora) {
        Objects.requireNonNull(moderador, "El moderador es obligatorio");
        Objects.requireNonNull(motivo, "El motivo de rechazo es obligatorio (RN-022)");
        exigirTransicion(ListingStatus.REJECTED);

        return new Listing(
                id,
                product,
                ListingStatus.REJECTED,
                images,
                publishedAt,
                moderador,
                ahora,
                motivo,
                nota,
                attentionReason,
                createdAt,
                ahora);
    }

    /** Criterio 29: pausar y reanudar no pasan por moderacion. */
    public Listing pausar(Instant ahora) {
        exigirTransicion(ListingStatus.PAUSED);
        return copiaCon(ListingStatus.PAUSED, images, ahora);
    }

    public Listing reanudar(Instant ahora) {
        exigirTransicion(ListingStatus.PUBLISHED);
        return copiaCon(ListingStatus.PUBLISHED, images, ahora);
    }

    /** Criterios 30 y 31. Terminal: de aqui no se vuelve. */
    public Listing archivar(Instant ahora) {
        exigirTransicion(ListingStatus.ARCHIVED);
        return copiaCon(ListingStatus.ARCHIVED, images, ahora);
    }

    // ------------------------------------------------------------------ edicion

    /**
     * RN-062: cambiar lo que describe el producto devuelve a moderacion.
     *
     * <p>Desde un borrador se queda en borrador; desde una publicacion viva o pausada
     * vuelve a {@code PENDING_REVIEW} y deja de verse. Es la unica lectura que
     * satisface RN-015 y RN-030 a la vez.
     */
    public Listing editarContenido(Product editado, Instant ahora) {
        Objects.requireNonNull(editado, "El producto es obligatorio");
        exigirNoTerminal();

        ListingStatus destino = status.admiteEdicionLibre() ? status : ListingStatus.PENDING_REVIEW;
        if (destino != status) {
            exigirTransicion(destino);
        }

        return new Listing(
                id,
                editado,
                destino,
                images,
                publishedAt,
                moderatedBy,
                moderatedAt,
                rejectionReason,
                rejectionNote,
                marcaPorPrecio(editado.price()),
                createdAt,
                ahora);
    }

    /**
     * RN-062 y RN-030: el precio no pasa por moderacion.
     *
     * <p>Se modera lo que describe el producto, no lo que cuesta. Congelar el precio al
     * crear el pedido, que es lo que protege al comprador, lo sigue haciendo RN-030.
     */
    public Listing cambiarPrecio(Money nuevo, Instant ahora) {
        exigirNoTerminal();
        Product conNuevoPrecio = product.conPrecio(nuevo);

        return new Listing(
                id,
                conNuevoPrecio,
                status,
                images,
                publishedAt,
                moderatedBy,
                moderatedAt,
                rejectionReason,
                rejectionNote,
                marcaPorPrecio(nuevo),
                createdAt,
                ahora);
    }

    /** Tampoco pasa por moderacion: no altera lo que un moderador aprobo. */
    public Listing cambiarEnvio(ShippingDimensions nuevo, Instant ahora) {
        exigirNoTerminal();
        return new Listing(
                id,
                product.conEnvio(nuevo),
                status,
                images,
                publishedAt,
                moderatedBy,
                moderatedAt,
                rejectionReason,
                rejectionNote,
                attentionReason,
                createdAt,
                ahora);
    }

    /** Criterio 18: la toma vino de galeria, asi que se mira con mas atencion. */
    public Listing marcarCargaDesdeGaleria(Instant ahora) {
        return new Listing(
                id,
                product,
                status,
                images,
                publishedAt,
                moderatedBy,
                moderatedAt,
                rejectionReason,
                rejectionNote,
                AttentionReason.GALLERY_UPLOAD,
                createdAt,
                ahora);
    }

    // ------------------------------------------------------------------ consultas

    public ListingId id() {
        return id;
    }

    public Product product() {
        return product;
    }

    public ListingStatus status() {
        return status;
    }

    public List<ProductImage> images() {
        return images;
    }

    public SellerId sellerId() {
        return product.sellerId();
    }

    public boolean esVisible() {
        return status.esVisible();
    }

    public boolean requiereAtencion() {
        return attentionReason != null;
    }

    public @Nullable AttentionReason attentionReason() {
        return attentionReason;
    }

    public @Nullable ListingRejectionReason rejectionReason() {
        return rejectionReason;
    }

    public @Nullable String rejectionNote() {
        return rejectionNote;
    }

    public @Nullable ModeratorId moderatedBy() {
        return moderatedBy;
    }

    public @Nullable Instant moderatedAt() {
        return moderatedAt;
    }

    public @Nullable Instant publishedAt() {
        return publishedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    // ------------------------------------------------------------------ interno

    /** RN-016 y RN-017, con la excepcion de RN-065 ya resuelta en {@link #tomasExigidas}. */
    private void exigirTomasCompletas() {
        List<ProductImage> tomas = tomasDelVendedor();
        int exigidas = tomasExigidas();

        if (tomas.size() != exigidas) {
            throw new ShotsIncompleteException(
                    exigidas, tomas.size(), product.estaSellado() ? "empaque sellado (RN-065)" : "secuencia 360");
        }

        long canonicas = tomas.stream().filter(ProductImage::esCanonica).count();
        if (canonicas != TOMAS_SI_ESTA_SELLADO) {
            throw new ShotsIncompleteException(
                    exigidas, tomas.size(), "faltan canonicas: hay " + canonicas + " de 4 (RN-016)");
        }
    }

    private void exigirTransicion(ListingStatus destino) {
        if (!status.puedePasarA(destino)) {
            throw new InvalidListingTransitionException(status, destino);
        }
    }

    private void exigirEditable() {
        exigirNoTerminal();
        if (status == ListingStatus.PENDING_REVIEW) {
            throw new InvalidListingTransitionException(status, ListingStatus.DRAFT);
        }
    }

    private void exigirNoTerminal() {
        if (status.esTerminal()) {
            throw new InvalidListingTransitionException(status, status);
        }
    }

    /** RN-020: el rango es blando y salirse solo marca. */
    private static @Nullable AttentionReason marcaPorPrecio(Money precio) {
        boolean fueraDeRango = precio.esMenorQue(PRECIO_MINIMO_RAZONABLE) || precio.esMayorQue(PRECIO_MAXIMO_RAZONABLE);
        return fueraDeRango ? AttentionReason.PRICE_OUT_OF_RANGE : null;
    }

    private Listing copiaCon(ListingStatus nuevoEstado, List<ProductImage> nuevasImagenes, Instant ahora) {
        return new Listing(
                id,
                product,
                nuevoEstado,
                nuevasImagenes,
                publishedAt,
                moderatedBy,
                moderatedAt,
                rejectionReason,
                rejectionNote,
                attentionReason,
                createdAt,
                ahora);
    }
}
