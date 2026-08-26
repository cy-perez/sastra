package co.sendik.catalog.usecase;

import co.sendik.catalog.dto.CategoryView;
import co.sendik.catalog.model.Category;
import co.sendik.catalog.model.CategoryId;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.ListingId;
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

        int cuantas() {
            return filas.size();
        }
    }

    static final class Arbol implements Categories {

        private final Map<CategoryId, Category> filas = new HashMap<>();

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
        public void publicacionRetirada(Listing publicacion, @Nullable String nota) {
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
