package co.sastra.identity.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * La cuenta bancaria que entrega el vendedor. Criterio 4 de HU-002.
 *
 * <p>Se valida en el borde y se vuelve a validar en el dominio, las dos cosas
 * (backend/CLAUDE.md). Lo de aqui es forma —que venga algo, que quepa, que sean
 * digitos— y sirve para responder con el detalle por campo que el formulario necesita
 * pintar. Lo del dominio es la regla, y es la que manda.
 *
 * @param bank el codigo de la entidad, no su nombre: los nombres cambian
 * @param accountType {@code SAVINGS}, {@code CHECKING} o {@code ELECTRONIC_DEPOSIT}.
 *     Llega como texto y lo traduce el controlador, para que un valor desconocido sea
 *     un error de validacion y no una excepcion de deserializacion
 * @param holderName tiene que coincidir con el titular del documento (RN-012), y eso
 *     lo comprueba el agregado
 */
public record SubmitBankAccountRequest(
        @NotBlank @Size(max = 40) String bank,
        @NotBlank String accountType,

        @NotBlank @Size(max = 25) @Pattern(regexp = "[0-9 .-]+")
        String accountNumber,

        @NotBlank @Size(max = 120) String holderName) {}
