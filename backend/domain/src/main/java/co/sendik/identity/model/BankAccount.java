package co.sendik.identity.model;

import java.util.Objects;

/**
 * La cuenta donde el vendedor recibe el pago de lo que venda.
 *
 * <p>Los cuatro datos que pide el criterio 4 de HU-002: entidad, tipo, numero y
 * titular. El titular va aqui y no se deduce del perfil a proposito: el nombre con
 * el que alguien aparece en el sitio no es el que aparece en su cuenta bancaria, y
 * confundirlos convertiria RN-012 en una comparacion de otra cosa.
 */
public record BankAccount(BankCode bank, BankAccountType type, BankAccountNumber number, LegalName holderName) {

    public BankAccount {
        Objects.requireNonNull(bank, "La entidad es obligatoria");
        Objects.requireNonNull(type, "El tipo de cuenta es obligatorio");
        Objects.requireNonNull(number, "El numero de la cuenta es obligatorio");
        Objects.requireNonNull(holderName, "El titular de la cuenta es obligatorio");
    }
}
