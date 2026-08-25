package co.sendik.identity.dto;

import co.sendik.identity.model.User;
import org.jspecify.annotations.Nullable;

/**
 * Peticion de abrir una sesion nueva para un usuario ya autenticado por otro
 * medio: sus credenciales, o el enlace de verificacion del correo.
 *
 * <p>Recibe el {@code User} ya cargado y no su identificador porque quien llama
 * acaba de comprobar quien es. Volver a consultarlo aqui no anadiria ninguna
 * garantia y si una consulta.
 */
public record IssueSessionCommand(
        User usuario, @Nullable String userAgent, @Nullable String ipHash) {}
