package co.sendik.identity.model;

/**
 * Clase de cuenta donde el vendedor recibe su dinero.
 *
 * <p>Tres y no dos. {@link #ELECTRONIC_DEPOSIT} es el de Nequi, DaviPlata, Movii,
 * Dale y RappiPay, que no son cuentas de ahorros aunque se usen igual: la
 * distincion no es cosmetica, porque el desembolso de la Fase 3 necesita el tipo
 * correcto o el dinero no llega.
 *
 * <p>«Deposito electronico» y no «billetera» porque es lo que la cuenta es;
 * billetera describe el producto con el que la persona llega a ella
 * (docs/producto/glosario.md).
 *
 * <p>Credito y debito no estan porque no son tipos de cuenta sino de tarjeta, y una
 * tarjeta no recibe una transferencia.
 */
public enum BankAccountType {
    SAVINGS,
    CHECKING,
    ELECTRONIC_DEPOSIT
}
