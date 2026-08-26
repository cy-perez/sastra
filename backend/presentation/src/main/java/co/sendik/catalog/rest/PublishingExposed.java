package co.sendik.catalog.rest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Marca que el catalogo esta expuesto. Solo existe con {@code FEATURE_PUBLISHING} encendida.
 *
 * <p><strong>Existe para que la cadena de seguridad pueda decir 404 en vez de 403.</strong>
 * El criterio 3 exige que con la bandera apagada cualquier endpoint de esta historia
 * responda 404: no que rechace, sino que no exista. Los controladores ya lo cumplen con su
 * {@code @ConditionalOnProperty} —sin bandera no se crean y no hay ruta—, pero la regla de
 * {@code SecurityConfig} que exige rol de moderador se evalua en el filtro, antes de que
 * nadie busque un manejador: con la bandera apagada respondia 403, que es justo lo que la
 * bandera existe para no decir.
 *
 * <p>Es un marcador vacio y no una propiedad leida: {@code presentation} no puede ver
 * {@code FeatureFlags}, que vive en {@code infrastructure}, y {@code @Value} suelto esta
 * prohibido. Con esto, la condicion se declara con el mismo mecanismo que ya usan los
 * controladores y en un solo sitio.
 */
@Component
@ConditionalOnProperty(prefix = "sendik.features", name = "publishing", havingValue = "true")
public class PublishingExposed {}
