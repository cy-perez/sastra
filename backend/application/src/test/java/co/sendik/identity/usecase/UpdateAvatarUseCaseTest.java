package co.sendik.identity.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.sendik.identity.dto.UpdateAvatarCommand;
import co.sendik.identity.exception.AccountNoLongerExistsException;
import co.sendik.identity.model.BirthDate;
import co.sendik.identity.model.DisplayName;
import co.sendik.identity.model.Email;
import co.sendik.identity.model.Role;
import co.sendik.identity.model.User;
import co.sendik.identity.model.UserId;
import co.sendik.identity.model.UserLocale;
import co.sendik.identity.model.UserStatus;
import co.sendik.identity.port.out.UserRepository;
import co.sendik.shared.file.FileKey;
import co.sendik.shared.file.ImageContentType;
import co.sendik.shared.file.ImageDimensions;
import co.sendik.shared.file.ImagePolicy;
import co.sendik.shared.file.ImageTooLargeException;
import co.sendik.shared.file.ImageTooSmallException;
import co.sendik.shared.file.NormalizedImage;
import co.sendik.shared.file.UnsupportedImageTypeException;
import co.sendik.shared.port.out.ImageNormalizer;
import co.sendik.shared.port.out.PublicFileStore;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Criterio 21: poner la foto de perfil.
 *
 * <p>Lo que se prueba aqui no es que la foto se guarde —eso es lo facil— sino el
 * <strong>orden</strong> y la <strong>secuencia de rechazos</strong>. Las dos cosas
 * se ven desde fuera solo cuando algo falla, y para entonces ya hay un archivo
 * huerfano o una fila apuntando a nada.
 */
@ExtendWith(MockitoExtension.class)
class UpdateAvatarUseCaseTest {

    private static final Instant AHORA = Instant.parse("2026-08-20T15:00:00Z");
    private static final long MAXIMO = 1_000;

    /** Un PNG de mentira: la firma correcta es lo unico que la politica mira. */
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private static final FileKey NUEVA = new FileKey("avatares/la-nueva.png");
    private static final FileKey ANTERIOR = new FileKey("avatares/la-vieja.png");

    @Mock
    private UserRepository usuarios;

    @Mock
    private PublicFileStore almacen;

    @Mock
    private ImageNormalizer normalizador;

    private UpdateAvatarUseCase caso;
    private UserId usuario;

    @BeforeEach
    void prepararCaso() {
        caso = new UpdateAvatarUseCase(
                usuarios, almacen, normalizador, new ImagePolicy(MAXIMO, new ImageDimensions(200, 200)));
        usuario = UserId.nuevo();
    }

    private User cuentaCon(@Nullable FileKey avatar) {
        return User.rehidratar(
                usuario,
                new Email("ana@correo.co"),
                new DisplayName("Ana Maria"),
                new BirthDate(LocalDate.of(1990, 3, 4)),
                null,
                null,
                avatar,
                UserLocale.ES,
                UserStatus.ACTIVE,
                AHORA.minus(Duration.ofDays(1)),
                EnumSet.of(Role.BUYER),
                AHORA.minus(Duration.ofDays(30)));
    }

    private NormalizedImage normalizada(int ancho, int alto) {
        return new NormalizedImage(new byte[] {1, 2, 3}, ImageContentType.PNG, new ImageDimensions(ancho, alto));
    }

    private void todoEnOrden(@Nullable FileKey avatarActual) {
        when(usuarios.buscarPorId(usuario)).thenReturn(Optional.of(cuentaCon(avatarActual)));
        when(normalizador.normalizar(any(), eq(ImageContentType.PNG))).thenReturn(normalizada(400, 400));
        when(almacen.guardar(eq("avatares"), any())).thenReturn(NUEVA);
    }

    @Test
    void deberia_guardar_la_foto_y_dejarla_en_la_cuenta() {
        todoEnOrden(null);

        User actualizada = caso.execute(new UpdateAvatarCommand(usuario, PNG));

        assertThat(actualizada.avatarKey()).isEqualTo(NUEVA);
        verify(usuarios).actualizar(actualizada);
    }

    /**
     * El orden que evita el fallo que no se puede arreglar.
     *
     * <p>Si se borrara el archivo viejo antes de guardar la fila y el guardado
     * fallara, la cuenta quedaria apuntando a un archivo que ya no existe: perfil
     * roto y sin vuelta atras. Al reves, lo que queda es un archivo huerfano que
     * cuesta unos centimos y se limpia. De los dos fallos posibles se elige el que
     * se puede limpiar, y eso solo se puede afirmar sobre el orden.
     */
    @Test
    void deberia_guardar_el_archivo_y_la_cuenta_antes_de_borrar_el_anterior() {
        todoEnOrden(ANTERIOR);

        caso.execute(new UpdateAvatarCommand(usuario, PNG));

        InOrder orden = inOrder(almacen, usuarios);
        orden.verify(almacen).guardar(eq("avatares"), any());
        orden.verify(usuarios).actualizar(any());
        orden.verify(almacen).borrar(ANTERIOR);
    }

    /** Cambiar de foto borra la anterior: si no, cada cambio deja un archivo pagando almacenamiento. */
    @Test
    void deberia_borrar_la_foto_anterior_al_reemplazarla() {
        todoEnOrden(ANTERIOR);

        caso.execute(new UpdateAvatarCommand(usuario, PNG));

        verify(almacen).borrar(ANTERIOR);
    }

    @Test
    void no_deberia_intentar_borrar_nada_cuando_no_habia_foto() {
        todoEnOrden(null);

        caso.execute(new UpdateAvatarCommand(usuario, PNG));

        verify(almacen, never()).borrar(any());
    }

    /**
     * El tamano se mira antes de tocar el contenido. Sin esto, un archivo de 500MB
     * se decodifica primero y se rechaza despues: el trabajo de decodificarlo lo
     * paga el servidor y lo elige quien sube.
     */
    @Test
    void deberia_rechazar_por_tamano_sin_decodificar_nada() {
        when(usuarios.buscarPorId(usuario)).thenReturn(Optional.of(cuentaCon(null)));

        assertThatThrownBy(() -> caso.execute(new UpdateAvatarCommand(usuario, new byte[(int) MAXIMO + 1])))
                .isInstanceOf(ImageTooLargeException.class);

        verify(normalizador, never()).normalizar(any(), any());
        verify(almacen, never()).guardar(any(), any());
    }

    /** HTML disfrazado de imagen: se rechaza por el contenido y no llega al almacen. */
    @Test
    void deberia_rechazar_lo_que_no_es_una_imagen_sin_guardar_nada() {
        when(usuarios.buscarPorId(usuario)).thenReturn(Optional.of(cuentaCon(null)));

        assertThatThrownBy(() -> caso.execute(new UpdateAvatarCommand(usuario, "<script>alert(1)</script>".getBytes())))
                .isInstanceOf(UnsupportedImageTypeException.class);

        verify(almacen, never()).guardar(any(), any());
    }

    /**
     * Las dimensiones se comprueban despues de normalizar, porque hasta entonces no
     * existen, pero antes de guardar: una imagen demasiado pequena no debe llegar al
     * almacen ni un instante.
     */
    @Test
    void deberia_rechazar_por_dimensiones_sin_guardar_nada() {
        when(usuarios.buscarPorId(usuario)).thenReturn(Optional.of(cuentaCon(null)));
        when(normalizador.normalizar(any(), eq(ImageContentType.PNG))).thenReturn(normalizada(50, 50));

        assertThatThrownBy(() -> caso.execute(new UpdateAvatarCommand(usuario, PNG)))
                .isInstanceOf(ImageTooSmallException.class);

        verify(almacen, never()).guardar(any(), any());
        verify(usuarios, never()).actualizar(any());
    }

    /** Lo que se guarda es la imagen normalizada, no los bytes que llegaron. */
    @Test
    void deberia_guardar_la_imagen_normalizada_y_no_lo_que_llego() {
        NormalizedImage normalizada = normalizada(400, 400);
        when(usuarios.buscarPorId(usuario)).thenReturn(Optional.of(cuentaCon(null)));
        when(normalizador.normalizar(any(), eq(ImageContentType.PNG))).thenReturn(normalizada);
        when(almacen.guardar(eq("avatares"), any())).thenReturn(NUEVA);

        caso.execute(new UpdateAvatarCommand(usuario, PNG));

        // El mismo objeto que devolvio el normalizador, no los bytes de entrada.
        verify(almacen).guardar("avatares", normalizada);
    }

    /**
     * El token de acceso vive quince minutos y no se invalida al cerrar la cuenta
     * (ADR-0003), asi que este caso puede llegar con credencial valida y sujeto
     * inexistente.
     */
    @Test
    void deberia_fallar_si_la_cuenta_ya_no_existe() {
        when(usuarios.buscarPorId(usuario)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> caso.execute(new UpdateAvatarCommand(usuario, PNG)))
                .isInstanceOf(AccountNoLongerExistsException.class);

        verify(almacen, never()).guardar(any(), any());
    }
}
