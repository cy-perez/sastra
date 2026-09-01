package co.sendik.catalog.usecase;

import co.sendik.catalog.dto.CatalogCursor;
import co.sendik.catalog.dto.CategoryView;
import co.sendik.catalog.dto.SellerProfileView;
import co.sendik.catalog.model.Category;
import co.sendik.catalog.model.CategoryId;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.model.ListingRejectionReason;
import co.sendik.catalog.model.ListingStatus;
import co.sendik.catalog.model.MeasurementGroup;
import co.sendik.catalog.model.ModerationAction;
import co.sendik.catalog.model.ModeratorId;
import co.sendik.catalog.model.SellerId;
import co.sendik.catalog.model.SizeSystem;
import co.sendik.catalog.port.out.Categories;
import co.sendik.catalog.port.out.ListingNotifier;
import co.sendik.catalog.port.out.ListingRepository;
import co.sendik.catalog.port.out.ModerationLog;
import co.sendik.catalog.port.out.SellerEligibility;
import co.sendik.catalog.port.out.SellerProfiles;
import co.sendik.shared.file.FileKey;
import co.sendik.shared.file.NormalizedImage;
import co.sendik.shared.port.out.PublicFileStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Dobles de los puertos del catalogo, en memoria.
 *
 * <p>Escritos a mano y no con un simulador. Un repositorio simulado obliga a decir en
 * cada prueba que devuelve cada llamada, y entonces la prueba describe la
 * implementacion en vez del comportamiento. Con estos, una prueba guarda algo y luego
 * lo lee, que es lo que de verdad hace el sistema.
 */
final class CatalogoEnMemoria {

    private CatalogoEnMemoria() {}

    static final class Publicaciones implements ListingRepository {

        private final Map<ListingId, Listing> filas = new LinkedHashMap<>();

        @Override
        public Listing guardar(Listing publicacion) {
            filas.put(publicacion.id(), publicacion);
            return publicacion;
        }

        @Override
        public Optional<Listing> buscar(ListingId id) {
            return Optional.ofNullable(filas.get(id));
        }

        @Override
        public Optional<Listing> buscarDelDueno(ListingId id, SellerId vendedor) {
            return buscar(id).filter(publicacion -> publicacion.sellerId().equals(vendedor));
        }

        @Override
        public List<Listing> buscarDelVendedor(SellerId vendedor, int pagina, int tamano) {
            List<Listing> suyas = filas.values().stream()
                    .filter(publicacion -> publicacion.sellerId().equals(vendedor))
                    .sorted(Comparator.comparing(Listing::createdAt).reversed())
                    .toList();

            int desde = Math.min(pagina * tamano, suyas.size());
            return suyas.subList(desde, Math.min(desde + tamano, suyas.size()));
        }

        @Override
        public List<Listing> pendientesDeRevision(int pagina, int tamano) {
            List<Listing> esperando = filas.values().stream()
                    .filter(publicacion -> publicacion.status() == ListingStatus.PENDING_REVIEW)
                    .sorted(Comparator.comparing(Listing::submittedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();

            int desde = Math.min(pagina * tamano, esperando.size());
            return esperando.subList(desde, Math.min(desde + tamano, esperando.size()));
        }

        /**
         * El catalogo publico. Ordena y corta igual que el SQL: por fecha de publicacion
         * descendente, desempatando por identificador, y aplicando el cursor sobre la
         * pareja entera. Si esto ordenara solo por fecha, la prueba del cursor pasaria
         * aqui y fallaria contra PostgreSQL.
         */
        @Override
        public List<Listing> publicadas(List<CategoryId> categorias, @Nullable CatalogCursor desde, int limite) {
            return tramo(
                    filas.values().stream()
                            .filter(publicacion -> categorias.isEmpty()
                                    || categorias.contains(publicacion.product().categoryId())),
                    desde,
                    limite);
        }

        @Override
        public List<Listing> publicadasDelVendedor(SellerId vendedor, @Nullable CatalogCursor desde, int limite) {
            return tramo(
                    filas.values().stream()
                            .filter(publicacion -> publicacion.sellerId().equals(vendedor)),
                    desde,
                    limite);
        }

        private static List<Listing> tramo(
                java.util.stream.Stream<Listing> candidatas, @Nullable CatalogCursor desde, int limite) {

            return candidatas
                    .filter(publicacion -> publicacion.status() == ListingStatus.PUBLISHED)
                    .filter(publicacion -> publicacion.publishedAt() != null)
                    .sorted(Comparator.comparing((Listing p) -> Objects.requireNonNull(p.publishedAt()))
                            .thenComparing(p -> p.id().value())
                            .reversed())
                    .filter(publicacion -> desde == null || despuesDe(publicacion, desde))
                    .limit(limite)
                    .toList();
        }

        /** La misma comparacion de pareja que hace `(published_at, id) < (:fecha, :id)`. */
        private static boolean despuesDe(Listing publicacion, CatalogCursor cursor) {
            java.time.Instant cuando = Objects.requireNonNull(publicacion.publishedAt());
            int porFecha = cuando.compareTo(cursor.publicadaEn());

            return porFecha < 0
                    || (porFecha == 0
                            && publicacion.id().value().compareTo(cursor.id().value()) < 0);
        }

        int cuantas() {
            return filas.size();
        }
    }

    /**
     * Los perfiles publicos, en memoria.
     *
     * <p>Devuelve vacio para quien no se haya dado de alta aqui, que es lo que hace el
     * adaptador de verdad con una cuenta inexistente o cerrada.
     */
    static final class Perfiles implements SellerProfiles {

        private final Map<SellerId, SellerProfileView> filas = new LinkedHashMap<>();

        void alta(SellerId vendedor, String nombre, boolean verificado) {
            filas.put(vendedor, new SellerProfileView(vendedor, nombre, null, verificado));
        }

        @Override
        public Optional<SellerProfileView> buscar(SellerId vendedor) {
            return Optional.ofNullable(filas.get(vendedor));
        }
    }

    static final class Arbol implements Categories {

        private final Map<CategoryId, Category> filas = new HashMap<>();

        /** Una hoja activa es ella misma; una familia activa son sus hojas activas. */
        @Override
        public List<CategoryId> publicablesBajo(CategoryId id) {
            Category elegida = filas.get(id);
            if (elegida == null || !elegida.active()) {
                return List.of();
            }

            if (!elegida.esFamilia()) {
                return List.of(elegida.id());
            }

            return filas.values().stream()
                    .filter(Category::active)
                    .filter(categoria -> id.equals(categoria.parentId()))
                    .map(Category::id)
                    .toList();
        }

        /**
         * El arbol como lo pide una pantalla.
         *
         * <p>Los nombres visibles no estan en {@code Category}, asi que aqui se componen
         * del slug: a estas pruebas les importa que el arbol salga armado por familias,
         * no como se llama cada categoria.
         */
        @Override
        public List<CategoryView> arbolActivo() {
            Map<CategoryId, List<CategoryView>> hijas = new LinkedHashMap<>();

            filas.values().stream()
                    .filter(categoria -> !categoria.esFamilia() && categoria.active())
                    .forEach(hija -> hijas.computeIfAbsent(
                                    Objects.requireNonNull(hija.parentId()), cualquiera -> new ArrayList<>())
                            .add(vista(hija, "familia", List.of())));

            return filas.values().stream()
                    .filter(Category::esFamilia)
                    .filter(Category::active)
                    .map(familia -> vista(familia, null, hijas.getOrDefault(familia.id(), List.of())))
                    .toList();
        }

        private static CategoryView vista(Category categoria, @Nullable String familiaSlug, List<CategoryView> hijas) {
            return new CategoryView(
                    categoria.id(),
                    categoria.slug(),
                    categoria.slug(),
                    categoria.slug(),
                    familiaSlug,
                    categoria.sizeSystems(),
                    categoria.measurementGroup() == null
                            ? Set.of()
                            : categoria.measurementGroup().obligatorias(),
                    categoria.allowsUsed(),
                    hijas);
        }

        Category agregar(Category categoria) {
            filas.put(categoria.id(), categoria);
            return categoria;
        }

        /** Una hoja de moda, con las cuatro condiciones y medidas de parte superior. */
        Category camisas() {
            return agregar(new Category(
                    CategoryId.nuevo(),
                    "camisas-y-blusas",
                    CategoryId.nuevo(),
                    Set.of(SizeSystem.ALPHA),
                    MeasurementGroup.TOP,
                    true,
                    true));
        }

        /** Una hoja de tecnologia: RN-064, solo lo nuevo. */
        Category celulares() {
            return agregar(new Category(
                    CategoryId.nuevo(),
                    "celulares-y-tabletas",
                    CategoryId.nuevo(),
                    Set.of(SizeSystem.ONE_SIZE),
                    MeasurementGroup.DEVICE,
                    false,
                    true));
        }

        Category retirada() {
            return agregar(new Category(
                    CategoryId.nuevo(),
                    "gafas",
                    CategoryId.nuevo(),
                    Set.of(SizeSystem.ONE_SIZE),
                    MeasurementGroup.ACCESSORY_FLAT,
                    true,
                    false));
        }

        @Override
        public Optional<Category> buscar(CategoryId id) {
            return Optional.ofNullable(filas.get(id));
        }
    }

    /**
     * RN-011 y RN-013.
     *
     * <p><strong>Mira el vendedor que le preguntan.</strong> El doble anterior devolvia
     * un booleano de instancia e ignoraba el argumento, asi que un caso de uso que
     * consultara la elegibilidad de otra persona —el moderador, el dueno anterior—
     * pasaba todas las pruebas. Es justo el fallo que un doble tiene que atrapar.
     */
    static final class Elegibilidad implements SellerEligibility {

        private final Set<SellerId> revocados = new HashSet<>();

        void revocar(SellerId vendedor) {
            revocados.add(vendedor);
        }

        @Override
        public boolean puedePublicar(SellerId vendedor) {
            return !revocados.contains(vendedor);
        }
    }

    static final class Bitacora implements ModerationLog {

        /** Guarda todos los argumentos: un doble que descarta uno no puede probarlo. */
        record Entrada(
                ListingId publicacion,
                ModeratorId actor,
                ModerationAction accion,
                @Nullable String motivo,
                @Nullable String nota) {}

        private final List<Entrada> entradas = new ArrayList<>();

        @Override
        public void registrar(
                ListingId publicacion,
                ModeratorId actor,
                ModerationAction accion,
                @Nullable String motivo,
                @Nullable String nota) {
            entradas.add(new Entrada(publicacion, actor, accion, motivo, nota));
        }

        List<Entrada> entradas() {
            return List.copyOf(entradas);
        }
    }

    /**
     * Los avisos del criterio 26.
     *
     * <p>Guarda la nota, que es el dato del criterio 22. El doble anterior la tiraba, asi
     * que invertir los argumentos de motivo y nota, o dejar de pasarla al correo, no lo
     * habria notado ninguna prueba.
     */
    static final class Avisos implements ListingNotifier {

        record Aviso(
                String tipo,
                ListingId publicacion,
                @Nullable String nota) {}

        private final List<Aviso> enviados = new ArrayList<>();

        @Override
        public void publicacionAprobada(Listing publicacion) {
            enviados.add(new Aviso("aprobada", publicacion.id(), null));
        }

        @Override
        public void publicacionRechazada(Listing publicacion, @Nullable String nota) {
            enviados.add(new Aviso("rechazada", publicacion.id(), nota));
        }

        @Override
        public void publicacionRetirada(Listing publicacion, ListingRejectionReason motivo, @Nullable String nota) {
            enviados.add(new Aviso("retirada", publicacion.id(), nota));
        }

        List<Aviso> enviados() {
            return List.copyOf(enviados);
        }
    }

    /** Almacen publico en memoria, para comprobar que lo que se borra se borra. */
    static final class Almacen implements PublicFileStore {

        private final Set<FileKey> guardados = new java.util.LinkedHashSet<>();
        private int contador;

        @Override
        public FileKey guardar(String carpeta, NormalizedImage imagen) {
            FileKey clave = new FileKey(carpeta + "/prueba-" + (++contador) + ".jpg");
            guardados.add(clave);
            return clave;
        }

        @Override
        public void borrar(FileKey clave) {
            guardados.remove(clave);
        }

        @Override
        public java.net.URI direccionDe(FileKey clave) {
            return java.net.URI.create("https://ejemplo.co/" + clave.value());
        }

        Set<FileKey> guardados() {
            return Set.copyOf(guardados);
        }
    }
}
