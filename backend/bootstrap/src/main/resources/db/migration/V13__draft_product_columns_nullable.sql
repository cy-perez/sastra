-- El borrador se guarda a medias. HU-007, criterio 5.
--
-- V9 declaro NOT NULL las doce columnas que describen el producto, y eso contradice
-- al criterio 5, que dice literalmente que un borrador incompleto "se guarda sin
-- exigir que este completo". El dominio siempre estuvo del lado del criterio:
-- `Product` marca @Nullable el titulo, la descripcion, la condicion, la talla, el
-- color, el precio y las dimensiones de envio, y solo `Listing.enviarARevision`
-- exige que esten todos.
--
-- El efecto era que crear un borrador con solo la categoria -- que es exactamente lo
-- que hace el formulario de publicar al pulsar «Empezar» -- respondia 500. No lo vio
-- ninguna prueba porque todas creaban el producto completo: la pantalla nunca se
-- habia ejercitado contra una base de datos real.
--
-- Lo obligatorio no desaparece, cambia de sitio: lo exige el dominio al enviar a
-- revision (criterio 6, con una entrada en `errors` por campo que falta), que es
-- donde de verdad tiene que estar. Una columna NOT NULL no puede distinguir un
-- borrador a medias de una publicacion incompleta que se quiere publicar.
ALTER TABLE products
    ALTER COLUMN title        DROP NOT NULL,
    ALTER COLUMN description  DROP NOT NULL,
    ALTER COLUMN condition    DROP NOT NULL,
    ALTER COLUMN size_system  DROP NOT NULL,
    ALTER COLUMN size_value   DROP NOT NULL,
    ALTER COLUMN color        DROP NOT NULL,
    ALTER COLUMN price        DROP NOT NULL,
    ALTER COLUMN weight_grams DROP NOT NULL,
    ALTER COLUMN length_cm    DROP NOT NULL,
    ALTER COLUMN width_cm     DROP NOT NULL,
    ALTER COLUMN height_cm    DROP NOT NULL;

-- Las restricciones de V9 no se tocan y siguen valiendo: un CHECK con un operando
-- nulo da desconocido y la fila pasa, asi que `products_price_positivo` y
-- `products_envio_positivo` siguen prohibiendo el cero y el negativo sin estorbar al
-- borrador vacio. Lo mismo las tres listas de valores admitidos.
--
-- `measurements` si se queda NOT NULL, y no por descuido: el dominio no la deja nula
-- nunca -- un producto sin medidas declaradas tiene el mapa vacio, que se guarda como
-- `{}` -- asi que la columna nula no significaria nada que el modelo pueda producir.

COMMENT ON COLUMN products.title IS
    'Nulo mientras es borrador. Obligatorio para enviar a revision: HU-007, criterios 5 y 6';
