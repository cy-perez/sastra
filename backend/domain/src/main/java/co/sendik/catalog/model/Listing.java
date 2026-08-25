package co.sendik.catalog.model;

import co.sendik.catalog.exception.InvalidListingTransitionException;
import co.sendik.catalog.exception.ReferenceImageNotAllowedException;
import co.sendik.catalog.exception.ShotsIncompleteException;
import co.sendik.shared.money.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
 * <p><strong>Toda edicion pasa por {@link #destinoTrasEditar()}.</strong> Cambiar el
 * titulo y cambiar una foto son la misma clase de cambio para RN-062 —las dos son
 * contenido moderable— y por eso comparten camino. Que estuvieran separados fue el
 * agujero que permitia sustituir las ocho tomas de una publicacion ya aprobada sin que
 * volviera a revision.
 *
 * <p>La version es del criterio 34 y viaja con el agregado: es la que se leyo. Sin
 * ella, quien guarda no puede decir "yo lei esta" y el bloqueo optimista no bloquea
 * nada.
 */
public final class Listing {

    /** RN-020: rango blando. Fuera de el se publica, marcado para revision mas atenta. */
    public static final Money PRECIO_MINIMO_RAZONABLE = Money.dePesos(10_000);

    public static final Money PRECIO_MAXIMO_RAZONABLE = Money.dePesos(20_000_000);

    /** RN-065: la tecnologia sellada se queda en las cuatro canonicas del empaque. */
    public static final int TOMAS_SI_ESTA_SELLADO = 4;

    /** RN-066: suficientes para mostrar el producto, pocas para no llenar el almacen. */
    public static final int MAXIMO_DE_REFERENCIAS = 4;

    private final ListingId id;
    private final Product product;
    private final ListingStatus status;
    private final List<ProductImage> images;

    private final @Nullable Instant publishedAt;
    private final @Nullable ModeratorId moderatedBy;
    private final @Nullable Instant moderatedAt;
    private final @Nullable ListingRejectionReason rejectionReason;
    private final @Nullable String rejectionNote;

    /**
     * Las dos marcas caben a la vez: un borrador barato con tomas de galeria las tiene
     * las dos, y el moderador tiene que ver las dos. Con un solo campo, la ultima
     * pisaba a la anterior segun el orden de los clics.
     */
    private final Set<AttentionReason> attentionReasons;

    private final long version;
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
            Set<AttentionReason> attentionReasons,
            long version,
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
        this.attentionReasons = attentionReasons.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(attentionReasons));
        this.version = version;
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
                marcaPorPrecio(producto.price(), Set.of()),
                0L,
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
            Set<AttentionReason> attentionReasons,
            long version,
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
                attentionReasons,
                version,
                createdAt,
                updatedAt);
    }

    // ------------------------------------------------------------------ imagenes

    /**
     * Agrega o reemplaza una imagen. Criterios 16 y 27.
     *
     * <p>Sobre una publicacion viva la devuelve a moderacion: RN-062 nombra «cualquiera
     * de las tomas» como contenido moderable, y una foto sustituida despues de aprobar
     * es justo el fraude que la moderacion existe para impedir.
     *
     * @throws ReferenceImageNotAllowedException si es de referencia y el producto no es
     *     tecnologia declarada sellada, o si ya hay demasiadas (RN-066)
     */
    public Listing conImagen(ProductImage imagen, Instant ahora) {
        Objects.requireNonNull(imagen, "La imagen es obligatoria");
        ListingStatus destino = destinoTrasEditar();

        if (imagen.kind() == ImageKind.REFERENCE) {
            exigirReferenciaAdmisible(imagen);
        }

        List<ProductImage> nuevas = new ArrayList<>(images);
        nuevas.removeIf(otra -> otra.kind() == imagen.kind() && otra.position() == imagen.position());
        nuevas.add(imagen);
        nuevas.sort(Comparator.comparing(ProductImage::kind).thenComparingInt(ProductImage::position));

        return conEstadoEImagenes(destino, nuevas, ahora);
    }

    public Listing sinImagen(ProductImageId imagenId, Instant ahora) {
        Objects.requireNonNull(imagenId, "El identificador de la imagen es obligatorio");
        ListingStatus destino = destinoTrasEditar();

        List<ProductImage> nuevas = new ArrayList<>(images);
        nuevas.removeIf(imagen -> imagen.id().equals(imagenId));

        return conEstadoEImagenes(destino, nuevas, ahora);
    }

    private void exigirReferenciaAdmisible(ProductImage imagen) {
        if (!product.estaSellado()) {
            throw new ReferenceImageNotAllowedException(
                    product.esTecnologia() ? "el producto no se declaro sellado" : "el producto no es tecnologia");
        }
        boolean esNueva = imagenesDeReferencia().stream().noneMatch(otra -> otra.position() == imagen.position());

        if (esNueva && imagenesDeReferencia().size() >= MAXIMO_DE_REFERENCIAS) {
            throw new ReferenceImageNotAllowedException("ya hay " + MAXIMO_DE_REFERENCIAS + ", que es el maximo");
        }
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
     * Criterios 6, 17 y 19. Comprueba las tomas antes de mover el estado.
     *
     * <p>Que el producto este completo lo comprueba el caso de uso con la categoria
     * delante: esta clase no la conoce.
     *
     * @throws ShotsIncompleteException si faltan tomas o alguna canonica
     */
    public Listing enviarARevision(Instant ahora) {
        exigirTransicion(ListingStatus.PENDING_REVIEW);
        exigirTomasCompletas();
        return conEstadoEImagenes(ListingStatus.PENDING_REVIEW, images, ahora);
    }

    /** Criterio 20: retirar antes de que se decida. */
    public Listing retirarDeRevision(Instant ahora) {
        exigirTransicion(ListingStatus.DRAFT);
        return conEstadoEImagenes(ListingStatus.DRAFT, images, ahora);
    }

    /** Criterio 23: retomar una rechazada conserva datos e imagenes. */
    public Listing retomar(Instant ahora) {
        exigirTransicion(ListingStatus.DRAFT);
        return conEstadoEImagenes(ListingStatus.DRAFT, images, ahora);
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
                attentionReasons,
                version,
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
                attentionReasons,
                version,
                createdAt,
                ahora);
    }

    /** Criterio 29: pausar y reanudar no pasan por moderacion. */
    public Listing pausar(Instant ahora) {
        exigirTransicion(ListingStatus.PAUSED);
        return conEstadoEImagenes(ListingStatus.PAUSED, images, ahora);
    }

    public Listing reanudar(Instant ahora) {
        exigirTransicion(ListingStatus.PUBLISHED);
        return conEstadoEImagenes(ListingStatus.PUBLISHED, images, ahora);
    }

    /** Criterios 30 y 31. Terminal: de aqui no se vuelve. */
    public Listing archivar(Instant ahora) {
        exigirTransicion(ListingStatus.ARCHIVED);
        return conEstadoEImagenes(ListingStatus.ARCHIVED, images, ahora);
    }

    // ------------------------------------------------------------------ edicion

    /**
     * RN-062: cambiar lo que describe el producto devuelve a moderacion.
     *
     * <p>Si el producto deja de estar sellado, sus imagenes de referencia se van con la
     * declaracion: sin empaque cerrado no hay nada que las justifique (RN-066, caso
     * borde de la historia).
     */
    public Listing editarContenido(Product editado, Instant ahora) {
        Objects.requireNonNull(editado, "El producto es obligatorio");
        ListingStatus destino = destinoTrasEditar();

        List<ProductImage> nuevas = editado.estaSellado() ? images : soloTomas();

        return new Listing(
                id,
                editado,
                destino,
                nuevas,
                publishedAt,
                moderatedBy,
                moderatedAt,
                rejectionReason,
                rejectionNote,
                marcaPorPrecio(editado.price(), attentionReasons),
                version,
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

        return new Listing(
                id,
                product.conPrecio(nuevo),
                status,
                images,
                publishedAt,
                moderatedBy,
                moderatedAt,
                rejectionReason,
                rejectionNote,
                marcaPorPrecio(nuevo, attentionReasons),
                version,
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
                attentionReasons,
                version,
                createdAt,
                ahora);
    }

    /** Criterio 18: la toma vino de galeria, asi que se mira con mas atencion. */
    public Listing marcarCargaDesdeGaleria(Instant ahora) {
        exigirNoTerminal();

        Set<AttentionReason> marcas = EnumSet.noneOf(AttentionReason.class);
        marcas.addAll(attentionReasons);
        marcas.add(AttentionReason.GALLERY_UPLOAD);

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
                marcas,
                version,
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

    public long version() {
        return version;
    }

    /** Tras guardar, la fila queda una version por delante. */
    public Listing conVersion(long nueva) {
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
                attentionReasons,
                nueva,
                createdAt,
                updatedAt);
    }

    public boolean esVisible() {
        return status.esVisible();
    }

    /** RN-063: comparar dos personas es cosa del caso de uso; nombrar la pregunta, de aqui. */
    public boolean laPublico(ModeratorId quienDecide) {
        return sellerId().value().equals(quienDecide.value());
    }

    public boolean requiereAtencion() {
        return !attentionReasons.isEmpty();
    }

    public Set<AttentionReason> attentionReasons() {
        return attentionReasons;
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

    /**
     * A donde va la publicacion cuando se le cambia contenido moderable. RN-062.
     *
     * <p>Desde un borrador se queda donde esta; desde una rechazada vuelve a borrador,
     * que es como se corrige y se reenvia (criterio 23); desde una viva o pausada vuelve
     * a revision y deja de verse. En revision no se toca, porque el moderador la esta
     * mirando (criterio 19), y en terminal tampoco.
     */
    private ListingStatus destinoTrasEditar() {
        exigirNoTerminal();

        if (status == ListingStatus.PENDING_REVIEW) {
            throw new InvalidListingTransitionException(status, ListingStatus.DRAFT);
        }
        if (status.admiteEdicionLibre()) {
            return status;
        }

        ListingStatus destino = status == ListingStatus.REJECTED ? ListingStatus.DRAFT : ListingStatus.PENDING_REVIEW;
        exigirTransicion(destino);
        return destino;
    }

    private List<ProductImage> soloTomas() {
        return images.stream().filter(ProductImage::esTomaDelVendedor).toList();
    }

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

    private void exigirNoTerminal() {
        if (status.esTerminal()) {
            throw new InvalidListingTransitionException(status, status);
        }
    }

    /**
     * RN-020: el rango es blando y salirse solo marca.
     *
     * <p>Recalcula la marca de precio y conserva las demas. Antes las sobrescribia
     * todas, asi que corregir el precio borraba la marca de carga desde galeria.
     */
    private static Set<AttentionReason> marcaPorPrecio(@Nullable Money precio, Set<AttentionReason> previas) {
        Set<AttentionReason> marcas = EnumSet.noneOf(AttentionReason.class);
        marcas.addAll(previas);
        marcas.remove(AttentionReason.PRICE_OUT_OF_RANGE);

        // Un borrador sin precio todavia no puede estar fuera de rango: no hay rango
        // que comparar. La marca aparece en cuanto el vendedor escriba uno.
        if (precio != null
                && (precio.esMenorQue(PRECIO_MINIMO_RAZONABLE) || precio.esMayorQue(PRECIO_MAXIMO_RAZONABLE))) {
            marcas.add(AttentionReason.PRICE_OUT_OF_RANGE);
        }
        return marcas;
    }

    private Listing conEstadoEImagenes(ListingStatus nuevoEstado, List<ProductImage> nuevasImagenes, Instant ahora) {
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
                attentionReasons,
                version,
                createdAt,
                ahora);
    }
}
