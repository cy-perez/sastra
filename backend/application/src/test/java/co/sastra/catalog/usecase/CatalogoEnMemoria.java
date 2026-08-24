package co.sastra.catalog.usecase;

import co.sastra.catalog.model.Category;
import co.sastra.catalog.model.CategoryId;
import co.sastra.catalog.model.Listing;
import co.sastra.catalog.model.ListingId;
import co.sastra.catalog.model.MeasurementGroup;
import co.sastra.catalog.model.ModerationAction;
import co.sastra.catalog.model.ModeratorId;
import co.sastra.catalog.model.SellerId;
import co.sastra.catalog.model.SizeSystem;
import co.sastra.catalog.port.out.Categories;
import co.sastra.catalog.port.out.ListingNotifier;
import co.sastra.catalog.port.out.ListingRepository;
import co.sastra.catalog.port.out.ModerationLog;
import co.sastra.catalog.port.out.SellerEligibility;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /** RN-011 y RN-013. Por omision deja publicar, que es el caso normal. */
    static final class Elegibilidad implements SellerEligibility {

        private boolean permitido = true;

        void revocar() {
            permitido = false;
        }

        @Override
        public boolean puedePublicar(SellerId vendedor) {
            return permitido;
        }
    }

    static final class Bitacora implements ModerationLog {

        record Entrada(
                ListingId publicacion,
                ModeratorId actor,
                ModerationAction accion,
                @Nullable String motivo) {}

        private final List<Entrada> entradas = new ArrayList<>();

        @Override
        public void registrar(
                ListingId publicacion,
                ModeratorId actor,
                ModerationAction accion,
                @Nullable String motivo,
                @Nullable String nota) {
            entradas.add(new Entrada(publicacion, actor, accion, motivo));
        }

        List<Entrada> entradas() {
            return List.copyOf(entradas);
        }
    }

    static final class Avisos implements ListingNotifier {

        private final List<String> enviados = new ArrayList<>();

        @Override
        public void publicacionAprobada(Listing publicacion) {
            enviados.add("aprobada:" + publicacion.id());
        }

        @Override
        public void publicacionRechazada(Listing publicacion, @Nullable String nota) {
            enviados.add("rechazada:" + publicacion.id());
        }

        @Override
        public void publicacionRetirada(Listing publicacion, @Nullable String nota) {
            enviados.add("retirada:" + publicacion.id());
        }

        List<String> enviados() {
            return List.copyOf(enviados);
        }
    }
}
