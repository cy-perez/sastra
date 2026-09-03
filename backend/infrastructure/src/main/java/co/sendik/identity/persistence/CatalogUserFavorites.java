package co.sendik.identity.persistence;

import co.sendik.catalog.model.BuyerId;
import co.sendik.catalog.model.Favorite;
import co.sendik.catalog.usecase.EraseFavoritesUseCase;
import co.sendik.catalog.usecase.ExportFavoritesUseCase;
import co.sendik.identity.dto.UserDataExport;
import co.sendik.identity.model.UserId;
import co.sendik.identity.port.out.UserFavorites;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Los favoritos de una persona, preguntados al catalogo. HU-011.
 *
 * <p><strong>Pregunta por dos casos de uso publicos de {@code catalog}, no por su
 * tabla.</strong> Es lo que permite {@code docs/arquitectura/vision-tecnica.md} —«si
 * necesita algo, es por un caso de uso publico o por un evento de dominio»— y lo que
 * prohibe es justo lo alternativo: leer {@code favorites} desde aqui ataria la identidad
 * al esquema del catalogo, y cualquier cambio alli romperia esto en silencio.
 *
 * <p>Es el gemelo de {@code VerifiedSellerEligibility} con la flecha al reves: alli el
 * catalogo le pregunta a identidad si alguien esta verificado. Que la conversacion exista
 * en las dos direcciones no es un ciclo entre contextos, son dos preguntas distintas y
 * cada una entra por la puerta publica del otro.
 *
 * <p>Aqui es tambien donde se traduce entre los dos identificadores. {@link UserId} y
 * {@link BuyerId} envuelven el mismo UUID y son tipos distintos a proposito; el sitio para
 * pasar de uno a otro es el borde entre contextos, que es este.
 *
 * <p><strong>Por que la traduccion del favorito la hace este adaptador y no el caso de
 * uso.</strong> {@code identity} no puede recibir un {@code Favorite} del catalogo sin
 * importar su modelo, y {@code catalog} no tiene por que saber que forma tiene el archivo
 * de descarga de datos personales. En medio queda esta clase, que conoce a los dos porque
 * es su unica razon de existir.
 */
@Component
public class CatalogUserFavorites implements UserFavorites {

    private final ExportFavoritesUseCase paraDescargar;
    private final EraseFavoritesUseCase paraBorrar;

    public CatalogUserFavorites(ExportFavoritesUseCase paraDescargar, EraseFavoritesUseCase paraBorrar) {
        this.paraDescargar = paraDescargar;
        this.paraBorrar = paraBorrar;
    }

    @Override
    public List<UserDataExport.Favorito> de(UserId usuario) {
        return paraDescargar.execute(new BuyerId(usuario.value())).stream()
                .map(CatalogUserFavorites::aFilaDeDescarga)
                .toList();
    }

    @Override
    public void borrarDe(UserId usuario) {
        paraBorrar.execute(new BuyerId(usuario.value()));
    }

    /**
     * El identificador de la publicacion y la fecha, y nada mas.
     *
     * <p>No se copia el titulo: es del vendedor, cambia y desaparece cuando se archiva.
     * Lo que es dato de esta persona es que guardo eso, y cuando.
     */
    private static UserDataExport.Favorito aFilaDeDescarga(Favorite favorito) {
        return new UserDataExport.Favorito(favorito.publicacion().toString(), favorito.marcadoEn());
    }
}
