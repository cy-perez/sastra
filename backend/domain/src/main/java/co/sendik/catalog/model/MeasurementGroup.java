package co.sendik.catalog.model;

import static co.sendik.catalog.model.MeasurementKind.CHEST;
import static co.sendik.catalog.model.MeasurementKind.DEPTH;
import static co.sendik.catalog.model.MeasurementKind.HEIGHT;
import static co.sendik.catalog.model.MeasurementKind.HIP;
import static co.sendik.catalog.model.MeasurementKind.INSOLE;
import static co.sendik.catalog.model.MeasurementKind.LENGTH;
import static co.sendik.catalog.model.MeasurementKind.RISE;
import static co.sendik.catalog.model.MeasurementKind.SHOULDERS;
import static co.sendik.catalog.model.MeasurementKind.SLEEVE;
import static co.sendik.catalog.model.MeasurementKind.WAIST;
import static co.sendik.catalog.model.MeasurementKind.WIDTH;

import java.util.EnumSet;
import java.util.Set;

/**
 * Que medidas son obligatorias, segun lo que sea el producto. RN-021.
 *
 * <p>Existe porque una camisa, un zapato y un bolso no se miden igual, y sin agrupar
 * habria que pedirlas todas —y quedarian casi todas vacias— o ninguna. Cada categoria
 * declara el suyo (docs/producto/categorias.md).
 *
 * <p><strong>El accesorio son dos grupos y no uno.</strong> Alto, ancho y profundidad
 * describen un bolso y no describen una correa. Salio al dibujar el arbol, no antes.
 *
 * <p><strong>{@link #DEVICE} mide lo mismo que {@link #ACCESSORY_VOLUME} y aun asi es
 * otro grupo.</strong> Lo que un grupo significa no es su lista de medidas: es a que
 * se le pueden pedir. El dia que la tecnologia necesite pulgadas de pantalla, se le
 * agregan a {@code DEVICE} sin tocar los bolsos.
 */
public enum MeasurementGroup {
    TOP(EnumSet.of(CHEST, LENGTH, SHOULDERS, SLEEVE)),
    BOTTOM(EnumSet.of(WAIST, HIP, RISE, LENGTH)),
    FULL_BODY(EnumSet.of(CHEST, WAIST, HIP, LENGTH)),
    FOOTWEAR(EnumSet.of(INSOLE)),
    ACCESSORY_VOLUME(EnumSet.of(HEIGHT, WIDTH, DEPTH)),
    ACCESSORY_FLAT(EnumSet.of(LENGTH, WIDTH)),
    DEVICE(EnumSet.of(HEIGHT, WIDTH, DEPTH));

    private final Set<MeasurementKind> obligatorias;

    MeasurementGroup(Set<MeasurementKind> obligatorias) {
        this.obligatorias = EnumSet.copyOf(obligatorias);
    }

    /** Copia: nadie modifica la definicion de un grupo desde fuera. */
    public Set<MeasurementKind> obligatorias() {
        return EnumSet.copyOf(obligatorias);
    }
}
