package co.sendik.catalog.dto;

/**
 * Si una publicacion esta guardada, y si se puede guardar. HU-011, criterios 1 y 5.
 *
 * <p><strong>Dos booleanos y no uno, y el segundo es el que evita un cambio de
 * contrato.</strong> El criterio 5 dice que sobre la publicacion propia el control no se
 * ofrece, y la pantalla no tiene con que saberlo: la sesion que el navegador guarda lleva
 * correo, nombre, roles y si el correo esta verificado, pero no el identificador de la
 * cuenta, asi que no puede comparar contra el vendedor de la ficha.
 *
 * <p>Se podria haber agregado el identificador a la sesion. No se hizo: seria cambiar el
 * contrato de HU-001 para que una pantalla haga una comprobacion que RN-072 pone en el
 * servidor de todas formas. Con {@code elegible}, la regla se queda donde debe y la
 * pantalla solo obedece lo que le responden.
 *
 * @param favorito si esta guardada por quien pregunta
 * @param elegible si se puede guardar: esta publicada y no es suya. Falso tambien cuando
 *     ya no se ve, que es lo que hace que la ficha deje de ofrecer el control sobre algo
 *     que el servidor va a rechazar
 */
public record FavoriteState(boolean favorito, boolean elegible) {}
