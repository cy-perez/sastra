-- Cierre de cuenta. HU-001 rebanada D, criterio 23 y derecho de supresion de la
-- Ley 1581 de 2012.
--
-- Los estados CLOSING y CLOSED ya existian en la restriccion de V2: se
-- anticiparon con RN-009 y aqui empiezan a usarse. Lo unico que falta es saber
-- cuando se cerro.
--
-- Recordatorio: una migracion aplicada no se edita nunca. Se crea la siguiente.

ALTER TABLE users ADD COLUMN closed_at timestamptz;

COMMENT ON COLUMN users.closed_at IS
    'Cuando se cerro la cuenta. Nulo mientras siga abierta (criterio 23)';

-- Al cerrar, la fila se vacia en vez de borrarse: quedan el identificador, la
-- fecha de creacion y el estado, que ya no apuntan a nadie. Asi lo que manana
-- haya que conservar por obligacion contable sigue teniendo a que referirse.
--
-- El correo se sustituye por uno del dominio reservado .invalid, que por norma no
-- puede existir. Si se conservara el original, RN-001 impediria a esa persona
-- volver a registrarse nunca con su propia direccion.
COMMENT ON TABLE users IS
    'Cuentas. Una cerrada conserva la fila anonimizada, no se elimina (docs/operacion/datos-personales.md)';

-- Las cuentas cerradas no se buscan por correo ni entran en el ingreso, pero el
-- indice de unicidad las sigue cubriendo: por eso el correo sustituido tiene que
-- ser unico, y lleva el identificador dentro.
