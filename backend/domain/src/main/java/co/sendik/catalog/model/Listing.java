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

    /**
     * Cuando entro a revision, para que la bandeja del moderador ordene por espera real
     * (HU-008, criterio 1).
     *
     * <p>No sirve {@code updatedAt}: cambiar el precio de algo que espera turno tambien
     * lo mueve, y ordenar por el haria que tocar el precio retrasara la propia revision.
     *
     * <p>Lo sella {@link #selloDeRevision} en <strong>toda</strong> entrada a
     * {@code PENDING_REVIEW}, no solo en {@link #enviarARevision}: RN-062 tambien trae
     * de vuelta lo que se edita, y una publicacion que vuelve con el sello viejo se
     * quedaria para siempre a la cabeza de la cola.
     */
    private final @Nullable Instant submittedAt;

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

    private Listing(Builder datos) {
        this.id = Objects.requireNonNull(datos.id, "El identificador es obligatorio");
        this.product = Objects.requireNonNull(datos.product, "El producto es obligatorio");
        this.status = Objects.requireNonNull(datos.status, "El estado es obligatorio");
        this.images = List.copyOf(datos.images);
        this.submittedAt = datos.submittedAt;
        this.publishedAt = datos.publishedAt;
        this.moderatedBy = datos.moderatedBy;
        this.moderatedAt = datos.moderatedAt;
        this.rejectionReason = datos.rejectionReason;
        this.rejectionNote = datos.rejectionNote;
        this.attentionReasons =
                datos.attentionReasons.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(datos.attentionReasons));
        this.version = Objects.requireNonNull(datos.version, "La version es obligatoria (criterio 34)");
        this.createdAt = Objects.requireNonNull(datos.createdAt, "La fecha de creacion es obligatoria");
        this.updatedAt = Objects.requireNonNull(datos.updatedAt, "La fecha de actualizacion es obligatoria");

        // El invariante que V12 introduce, y el unico sitio por el que pasan los dos
        // caminos de construccion. Sin esto era solo un comentario: `estado(valor)` mueve
        // el estado sin tocar el sello, asi que reconstruir una fila en revision sin el se
        // podia, y esa publicacion se quedaba a la cabeza de la cola para siempre.
        if (this.status == ListingStatus.PENDING_REVIEW && this.submittedAt == null) {
            throw new IllegalArgumentException("Una publicacion en revision lleva sello de entrada (V12)");
        }
    }

    /** Criterio 4: nace en borrador, sin imagenes, y con la marca de RN-020 si toca. */
    public static Listing crearBorrador(ListingId id, Product producto, Instant ahora) {
        return new Builder()
                .id(id)
                .producto(producto)
                .estado(ListingStatus.DRAFT)
                .marcas(marcaPorPrecio(producto.price(), Set.of()))
                .version(0L)
                .creada(ahora)
                .tocada(ahora)
                .armar();
    }

    /**
     * Reconstruye lo guardado. Solo lo usa la capa de persistencia.
     *
     * <p>Por nombre y no por posicion, y no es cosmetica: cuatro de los campos son
     * {@code Instant} y varios admiten nulo, asi que dos argumentos intercambiados
     * compilaban sin protestar. Ademas un campo nuevo deja de romper a quien reconstruye
     * —solo se anade una llamada mas— que es exactamente lo que costo {@code
     * submittedAt}.
     */
    public static Builder reconstruir() {
        return new Builder();
    }

    /** Este mismo, listo para cambiarle lo que haga falta. */
    private Builder copia() {
        return new Builder()
                .id(id)
                .producto(product)
                .estado(status)
                .imagenes(images)
                .enviada(submittedAt)
                .publicada(publishedAt)
                .decididaPor(moderatedBy, moderatedAt)
                .rechazadaPor(rejectionReason, rejectionNote)
                .marcas(attentionReasons)
                .version(version)
                .creada(createdAt)
                .tocada(updatedAt);
    }

    /**
     * Arma una publicacion nombrando lo que se le pone.
     *
     * <p>Sirve para dos cosas: reconstruir una fila guardada y copiar esta publicacion
     * cambiandole algo. Los nombres de los metodos van en espanol como el resto del
     * dominio; el del tipo, en ingles como todos los tipos del proyecto.
     */
    public static final class Builder {

        private @Nullable ListingId id;
        private @Nullable Product product;
        private @Nullable ListingStatus status;
        private List<ProductImage> images = List.of();
        private @Nullable Instant submittedAt;
        private @Nullable Instant publishedAt;
        private @Nullable ModeratorId moderatedBy;
        private @Nullable Instant moderatedAt;
        private @Nullable ListingRejectionReason rejectionReason;
        private @Nullable String rejectionNote;
        private Set<AttentionReason> attentionReasons = Set.of();

        /**
         * Envuelta para que omitirla no compile en silencio.
         *
         * <p>Con el constructor posicional era obligatoria por construccion. Con el
         * ensamblador, un `long` sin poner vale 0, que ademas es una version valida para
         * una fila nueva: el sintoma seria un UPDATE que no afecta ninguna fila y sale como
         * conflicto optimista donde no lo habia (criterio 34).
         */
        private @Nullable Long version;

        private @Nullable Instant createdAt;
        private @Nullable Instant updatedAt;

        private Builder() {}

        public Builder id(ListingId valor) {
            this.id = valor;
            return this;
        }

        public Builder producto(Product valor) {
            this.product = valor;
            return this;
        }

        public Builder estado(ListingStatus valor) {
            this.status = valor;
            return this;
        }

        /**
         * Mueve el estado y sella la entrada a revision en el mismo gesto.
         *
         * <p>Van juntos porque separarlos deja escribir uno sin el otro, y eso es
         * justamente el error: una publicacion que entra a la cola sin sello, o que
         * vuelve conservando el viejo, se queda a la cabeza para siempre.
         */
        public Builder estado(ListingStatus valor, Instant ahora) {
            this.submittedAt = selloDeRevision(valor, ahora);
            this.status = valor;
            return tocada(ahora);
        }

        /**
         * Sella si y solo si la publicacion entra a revision.
         *
         * <p>No hace falta preguntar si ya estaba en revision: {@code PENDING_REVIEW} no
         * es destino de si mismo en {@link ListingStatus}, y todos los caminos que llaman
         * aqui pasan antes por {@code exigirTransicion} o por {@code destinoTrasEditar},
         * que lanzan. Se comprobo que esa rama era inalcanzable y se quito en vez de
         * dejarla como una defensa que ninguna prueba podia cubrir.
         *
         * <p>Lo que si protege contra un cambio futuro de esa tabla es el guardia del
         * constructor, que rechaza una publicacion en revision sin sello.
         */
        private @Nullable Instant selloDeRevision(ListingStatus destino, Instant ahora) {
            return destino == ListingStatus.PENDING_REVIEW ? ahora : submittedAt;
        }

        public Builder imagenes(List<ProductImage> valor) {
            this.images = valor;
            return this;
        }

        /** Solo para reconstruir: en una copia lo decide el propio cambio de estado. */
        public Builder enviada(@Nullable Instant valor) {
            this.submittedAt = valor;
            return this;
        }

        public Builder publicada(@Nullable Instant valor) {
            this.publishedAt = valor;
            return this;
        }

        public Builder decididaPor(@Nullable ModeratorId moderador, @Nullable Instant cuando) {
            this.moderatedBy = moderador;
            this.moderatedAt = cuando;
            return this;
        }

        public Builder rechazadaPor(@Nullable ListingRejectionReason motivo, @Nullable String nota) {
            this.rejectionReason = motivo;
            this.rejectionNote = nota;
            return this;
        }

        /** Aprobar borra el rechazo anterior: una publicacion viva no arrastra motivo. */
        public Builder sinRechazo() {
            return rechazadaPor(null, null);
        }

        public Builder marcas(Set<AttentionReason> valor) {
            this.attentionReasons = valor;
            return this;
        }

        public Builder version(long valor) {
            this.version = valor;
            return this;
        }

        public Builder creada(Instant valor) {
            this.createdAt = valor;
            return this;
        }

        public Builder tocada(Instant valor) {
            this.updatedAt = valor;
            return this;
        }

        public Listing armar() {
            return new Listing(this);
        }
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

        return copia().estado(ListingStatus.PUBLISHED, ahora)
                .publicada(ahora)
                .decididaPor(moderador, ahora)
                .sinRechazo()
                .armar();
    }

    /** Criterio 22: motivo de la lista cerrada y nota opcional. */
    public Listing rechazar(
            ModeratorId moderador, ListingRejectionReason motivo, @Nullable String nota, Instant ahora) {
        Objects.requireNonNull(moderador, "El moderador es obligatorio");
        Objects.requireNonNull(motivo, "El motivo de rechazo es obligatorio (RN-022)");
        exigirTransicion(ListingStatus.REJECTED);

        return copia().estado(ListingStatus.REJECTED, ahora)
                .decididaPor(moderador, ahora)
                .rechazadaPor(motivo, nota)
                .armar();
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

        return copia().producto(editado)
                .imagenes(nuevas)
                .estado(destino, ahora)
                .marcas(marcaPorPrecio(editado.price(), attentionReasons))
                .armar();
    }

    /**
     * RN-062 y RN-030: el precio no pasa por moderacion.
     *
     * <p>Se modera lo que describe el producto, no lo que cuesta. Congelar el precio al
     * crear el pedido, que es lo que protege al comprador, lo sigue haciendo RN-030.
     */
    public Listing cambiarPrecio(Money nuevo, Instant ahora) {
        exigirNoTerminal();

        return copia().producto(product.conPrecio(nuevo))
                .marcas(marcaPorPrecio(nuevo, attentionReasons))
                .tocada(ahora)
                .armar();
    }

    /** Tampoco pasa por moderacion: no altera lo que un moderador aprobo. */
    public Listing cambiarEnvio(ShippingDimensions nuevo, Instant ahora) {
        exigirNoTerminal();

        return copia().producto(product.conEnvio(nuevo)).tocada(ahora).armar();
    }

    /** Criterio 18: la toma vino de galeria, asi que se mira con mas atencion. */
    public Listing marcarCargaDesdeGaleria(Instant ahora) {
        exigirNoTerminal();

        Set<AttentionReason> marcas = EnumSet.noneOf(AttentionReason.class);
        marcas.addAll(attentionReasons);
        marcas.add(AttentionReason.GALLERY_UPLOAD);

        return copia().marcas(marcas).tocada(ahora).armar();
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
        return copia().version(nueva).armar();
    }

    public boolean esVisible() {
        return status.esVisible();
    }

    /** RN-063: comparar dos personas es cosa del caso de uso; nombrar la pregunta, de aqui. */
    public boolean laPublico(ModeratorId quienDecide) {
        return sellerId().value().equals(quienDecide.value());
    }

    /**
     * RN-072: si esta publicacion es de quien pregunta. Hermano de {@link #laPublico}.
     *
     * <p>Existe por lo mismo: los identificadores de persona de este contexto son tipos
     * distintos a proposito —una persona no es «un vendedor» ni «un comprador», lo es segun
     * lo que este haciendo— asi que {@code equals} entre ellos seria siempre falso y la
     * regla no protegeria de nada. La comparacion baja a los UUID, y baja **en un solo
     * sitio**: aqui.
     *
     * <p>Lo preguntan {@link Favorite#de}, que la usa para negarse a existir sobre lo
     * propio, y la lectura del estado del control, que la usa para no ofrecerlo. Antes cada
     * uno la escribia por su cuenta, que es tener la misma regla en dos capas.
     */
    public boolean esDe(BuyerId quien) {
        return sellerId().value().equals(quien.value());
    }

    public boolean requiereAtencion() {
        return !attentionReasons.isEmpty();
    }

    /** Cuando entro a revision por ultima vez, o nulo si nunca ha entrado. */
    public @Nullable Instant submittedAt() {
        return submittedAt;
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
        return copia().imagenes(nuevasImagenes).estado(nuevoEstado, ahora).armar();
    }
}
