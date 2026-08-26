package co.sendik.catalog.rest.mapper;

import co.sendik.catalog.dto.ProductData;
import co.sendik.catalog.model.Brand;
import co.sendik.catalog.model.CategoryId;
import co.sendik.catalog.model.Color;
import co.sendik.catalog.model.Condition;
import co.sendik.catalog.model.Description;
import co.sendik.catalog.model.MeasurementKind;
import co.sendik.catalog.model.Measurements;
import co.sendik.catalog.model.ShippingDimensions;
import co.sendik.catalog.model.Size;
import co.sendik.catalog.model.SizeSystem;
import co.sendik.catalog.model.Title;
import co.sendik.catalog.model.WarrantyMonths;
import co.sendik.catalog.rest.dto.MoneyPayload;
import co.sendik.catalog.rest.dto.ProductRequest;
import co.sendik.catalog.rest.dto.ShippingPayload;
import co.sendik.catalog.rest.dto.SizePayload;
import co.sendik.shared.money.Money;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Del cuerpo de la peticion a los objetos de valor del dominio.
 *
 * <p><strong>Aqui no se decide ninguna regla de negocio.</strong> Lo unico que pasa es
 * traduccion: una cadena a una enumeracion, un numero a un objeto de dinero. Que un
 * titulo sea demasiado largo, que un color no exista o que un precio tenga decimales lo
 * rechazan los propios objetos de valor, que es donde vive esa regla y donde se puede
 * probar sin HTTP.
 *
 * <p>Las enumeraciones se convierten a mano y no se declaran en la firma del DTO. Con la
 * enumeracion en la firma, un valor desconocido produce un fallo de conversion de Spring
 * que no esta mapeado y sale como 500. Convertido aqui, la {@link IllegalArgumentException}
 * ya tiene manejador y la respuesta es el error de validacion que corresponde
 * (criterios 8 y 9). Es la misma decision que tomaron los controladores de identidad.
 */
public final class ProductRequests {

    private ProductRequests() {}

    public static ProductData aDatos(ProductRequest peticion) {
        return new ProductData(
                CategoryId.de(peticion.categoryId()),
                titulo(peticion.title()),
                descripcion(peticion.description()),
                marca(peticion.brand()),
                condicion(peticion.condition()),
                talla(peticion.size()),
                medidas(peticion.measurements()),
                color(peticion.color()),
                dinero(peticion.price()),
                envio(peticion.shipping()),
                peticion.isSealed(),
                garantia(peticion.warrantyMonths()));
    }

    /**
     * El envio como objeto de valor. Es publico porque su ruta propia lo manda suelto.
     *
     * @throws IllegalArgumentException si falta cualquiera de los cuatro datos: media
     *     caja no es una caja, y {@code ShippingDimensions} no admite nulos
     */
    public static ShippingDimensions aDimensiones(ShippingPayload envio) {
        if (envio.weightGrams() == null
                || envio.lengthCm() == null
                || envio.widthCm() == null
                || envio.heightCm() == null) {
            throw new IllegalArgumentException("El envio necesita peso y las tres medidas");
        }
        return new ShippingDimensions(envio.weightGrams(), envio.lengthCm(), envio.widthCm(), envio.heightCm());
    }

    /**
     * El dinero del contrato a {@code Money}.
     *
     * <p>La moneda se comprueba en lugar de ignorarse. Un cliente que mande USD y reciba
     * un 200 se queda creyendo que publico en dolares.
     */
    public static Money aDinero(MoneyPayload precio) {
        if (precio.currency() != null
                && !Money.MONEDA.equalsIgnoreCase(precio.currency().trim())) {
            throw new IllegalArgumentException("La unica moneda del catalogo es " + Money.MONEDA);
        }
        return new Money(precio.amount());
    }

    private static @Nullable Money dinero(@Nullable MoneyPayload precio) {
        return precio == null ? null : aDinero(precio);
    }

    private static @Nullable Title titulo(@Nullable String valor) {
        return enBlanco(valor) ? null : new Title(valor);
    }

    private static @Nullable Description descripcion(@Nullable String valor) {
        return enBlanco(valor) ? null : new Description(valor);
    }

    private static @Nullable Brand marca(@Nullable String valor) {
        return enBlanco(valor) ? null : new Brand(valor);
    }

    private static @Nullable Condition condicion(@Nullable String valor) {
        if (enBlanco(valor)) {
            return null;
        }
        try {
            return Condition.valueOf(normalizar(valor));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("La condicion no es una de las cuatro del glosario", e);
        }
    }

    private static @Nullable Color color(@Nullable String valor) {
        if (enBlanco(valor)) {
            return null;
        }
        try {
            return Color.valueOf(normalizar(valor));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El color no esta en la lista cerrada", e);
        }
    }

    private static @Nullable Size talla(@Nullable SizePayload talla) {
        if (talla == null || enBlanco(talla.system()) || enBlanco(talla.value())) {
            return null;
        }
        try {
            return new Size(SizeSystem.valueOf(normalizar(talla.system())), talla.value());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El sistema de talla no esta en la lista cerrada", e);
        }
    }

    private static @Nullable ShippingDimensions envio(@Nullable ShippingPayload envio) {
        return envio == null ? null : aDimensiones(envio);
    }

    private static @Nullable WarrantyMonths garantia(@Nullable Integer meses) {
        return meses == null ? null : new WarrantyMonths(meses);
    }

    /**
     * Las medidas, con su clave convertida a la enumeracion.
     *
     * <p>Un mapa vacio y uno ausente son lo mismo y significan lo mismo: todavia ninguna.
     * Que falten las obligatorias del grupo lo decide la categoria al enviar a revision,
     * no aqui (RN-021).
     */
    private static Measurements medidas(@Nullable Map<String, BigDecimal> valores) {
        Map<MeasurementKind, BigDecimal> convertidas = new EnumMap<>(MeasurementKind.class);

        if (valores != null) {
            valores.forEach((medida, centimetros) -> convertidas.put(tipoDeMedida(medida), centimetros));
        }
        return new Measurements(convertidas);
    }

    private static MeasurementKind tipoDeMedida(String valor) {
        try {
            return MeasurementKind.valueOf(normalizar(valor));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("La medida " + valor + " no esta en la lista cerrada", e);
        }
    }

    private static String normalizar(String valor) {
        return valor.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static boolean enBlanco(@Nullable String valor) {
        return valor == null || valor.isBlank();
    }
}
