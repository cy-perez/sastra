-- Cuando una publicacion entro a revision. HU-008, criterio 1.
--
-- La bandeja del moderador ordena por espera real, y `updated_at` no sirve para eso:
-- una publicacion que espera turno puede cambiar de precio -- RN-062 y RN-030 lo
-- permiten a proposito, porque el precio no pasa por moderacion -- y eso mueve
-- `updated_at`. Ordenar por el haria que tocar el precio retrasara la propia revision,
-- que es un incentivo torcido, y ademas dejaria mintiendo al "espera desde hace" de la
-- pantalla, que se reiniciaria solo.
--
-- Lo sella el dominio en toda entrada a PENDING_REVIEW, no solo al enviar por primera
-- vez: RN-062 tambien devuelve a la cola lo que se edita, y una publicacion que vuelve
-- con el sello viejo se quedaria para siempre a la cabeza.
ALTER TABLE listings ADD COLUMN submitted_at timestamptz;

-- Las filas que ya esperan turno se quedan con lo unico que hay, que ademas es correcto
-- para ellas: al entrar a revision se escribio `updated_at`, y en ese estado no se ha
-- podido editar el contenido. Solo se rellenan esas: para una publicada o un borrador el
-- valor seria inventado, y nulo dice la verdad -- todavia no ha entrado, o entro antes
-- de que existiera esta columna.
UPDATE listings SET submitted_at = updated_at WHERE status = 'PENDING_REVIEW';

-- El indice es exactamente la consulta de la bandeja: filtra por estado y ordena por
-- espera. Parcial porque la cola solo mira un estado de siete, y un indice que solo
-- cubre lo que se consulta no crece con el catalogo publicado.
CREATE INDEX idx_listings_review_queue
    ON listings (submitted_at)
    WHERE status = 'PENDING_REVIEW';
