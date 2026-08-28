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
  one: (id: string) => ['catalog', 'public', 'listing', id] as const,
  seller: (id: string) => ['catalog', 'public', 'seller', id] as const,
  sellerListings: (id: string) => ['catalog', 'public', 'seller', id, 'listings'] as const,
} as const;
