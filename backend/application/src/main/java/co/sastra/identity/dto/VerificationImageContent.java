package co.sastra.identity.dto;

/**
 * Los bytes de una imagen de verificacion y su tipo, ya detectado por el contenido.
 *
 * <p>El tipo se detecta y no se declara, como en todo lo demas del proyecto: lo que
 * guardamos paso por el normalizador, asi que es una imagen de verdad, pero el borde
 * necesita decir cual para que el navegador la pinte.
 *
 * @param mediaType {@code image/jpeg} o {@code image/png}
 */
public record VerificationImageContent(byte[] contenido, String mediaType) {}
