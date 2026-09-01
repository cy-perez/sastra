/**
 * Claves de consulta del catálogo público.
 *
 * Centralizadas y nunca como arreglo literal suelto (frontend/CLAUDE.md).
 *
 * <p>`categories` es **la misma clave** que usa el formulario de publicar, y a propósito:
 * es el mismo recurso, el mismo endpoint y los mismos treinta y siete nombres. Compartir
 * la entrada de caché hace que ir de `/publicar` a `/catalogo` no vuelva a pedir el árbol.
 * No crea acoplamiento entre funcionalidades —ninguna importa de la otra—, solo evita
 * pedir dos veces lo que no cambia.
 *
 * <p>`list` depende de la categoría porque cada una es un listado distinto: con una clave
 * común, abrir «Camisas» pintaría por un instante lo que quedó de «Todo».
 */
export const queryKeys = {
  categories: ['catalog', 'categories'] as const,
  list: (categoria: string | null) => ['catalog', 'public', 'list', categoria ?? 'todo'] as const,
  /**
   * Una publicación, y **desde qué perspectiva se pidió**.
   *
   * <p>El segundo tramo no es un capricho. `GET /listings/{id}` responde una forma u otra
   * según quién pregunte, así que la respuesta para un moderador y la de un visitante son
   * dos cosas distintas guardadas bajo el mismo nombre. Sin distinguirlas pasa esto: al
   * cargar la página, la consulta sale antes de que la cookie de refresco devuelva la
   * sesión, cachea la forma pública —sin estado— y no vuelve a pedirla nunca; un moderador
   * que abre una ficha por su dirección no ve la acción de bajarla, y al recargar tampoco.
   *
   * <p>Para quien no modera la clave no cambia entre el servidor y el cliente, así que el
   * catálogo público no pide nada de más: solo se vuelve a pedir para quien sí.
   */
  /**
   * Las dos entradas de una misma publicación, para invalidarlas juntas.
   *
   * <p>Hace falta porque {@link one} devuelve una clave **completa** y no un prefijo: al
   * bajar una publicación hay que tirar tanto la copia pública como la del moderador, y
   * pasar `one(id)` invalida solo la primera. El síntoma era que quien acababa de bajarla
   * seguía viendo la acción de bajarla.
   */
  anyOne: (id: string) => ['catalog', 'public', 'listing', id] as const,
  one: (id: string, comoModerador = false) =>
    ['catalog', 'public', 'listing', id, comoModerador ? 'moderacion' : 'publica'] as const,
  seller: (id: string) => ['catalog', 'public', 'seller', id] as const,
  sellerListings: (id: string) => ['catalog', 'public', 'seller', id, 'listings'] as const,
  /**
   * El sello de un vendedor, para quien modera. HU-010.
   *
   * <p>Cuelga del vendedor y no de la verificación, y es a propósito: la pantalla parte
   * del identificador del perfil y no conoce el de la verificación hasta que llega la
   * respuesta. Con la clave puesta en el identificador que sí se tiene, revocar puede
   * invalidar las dos entradas de esa persona sin volver a preguntar cuál era.
   */
  verification: (vendedor: string) => ['catalog', 'moderation', 'verification', vendedor] as const,
} as const;
