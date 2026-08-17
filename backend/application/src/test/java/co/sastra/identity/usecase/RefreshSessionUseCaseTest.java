package co.sastra.identity.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.sastra.identity.dto.RefreshSessionCommand;
import co.sastra.identity.dto.SessionResult;
import co.sastra.identity.exception.RefreshTokenInvalidException;
import co.sastra.identity.model.BirthDate;
import co.sastra.identity.model.DisplayName;
import co.sastra.identity.model.Email;
import co.sastra.identity.model.RefreshToken;
import co.sastra.identity.model.User;
import co.sastra.identity.model.UserId;
import co.sastra.identity.model.UserLocale;
import co.sastra.identity.port.out.AccessTokenIssuer;
import co.sastra.identity.port.out.MailSender;
import co.sastra.identity.port.out.RefreshTokenRepository;
import co.sastra.identity.port.out.TokenGenerator;
import co.sastra.identity.port.out.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshSessionUseCaseTest {

    private static final Instant AHORA = Instant.parse("2026-08-17T15:00:00Z");
    private static final Instant ABIERTA = AHORA.minus(Duration.ofDays(1));

    /** Ventana de gracia de RN-007, la misma que trae application.yaml. */
    private static final Duration GRACIA = Duration.ofSeconds(10);

    @Mock
    private RefreshTokenRepository refrescos;

    @Mock
    private UserRepository usuarios;

    @Mock
    private AccessTokenIssuer accesos;

    @Mock
    private TokenGenerator generadorDeTokens;

    @Mock
    private MailSender correo;

    private RefreshSessionUseCase caso;
    private User usuario;
    private RefreshToken vigente;

    @BeforeEach
    void prepararCaso() {
        caso = new RefreshSessionUseCase(
                refrescos,
                usuarios,
                accesos,
                generadorDeTokens,
                correo,
                RefreshToken.VIGENCIA,
                GRACIA,
                Clock.fixed(AHORA, ZoneOffset.UTC));

        usuario = User.registrar(
                UserId.nuevo(),
                new Email("ana@correo.co"),
                new DisplayName("Ana Maria"),
                new BirthDate(LocalDate.of(1990, 3, 4)),
                UserLocale.ES,
                LocalDate.of(2026, 8, 17),
                ABIERTA);

        vigente = RefreshToken.abrirSesion(
                usuario.id(), "hash-vigente", ABIERTA, RefreshToken.VIGENCIA, "Firefox", "ip-vieja");
    }

    private RefreshSessionCommand comando() {
        return new RefreshSessionCommand("refresco-en-claro", "Chrome", "ip-nueva");
    }

    private void conTokenEncontrado(RefreshToken token) {
        when(generadorDeTokens.hashearRecibido("refresco-en-claro")).thenReturn("hash-vigente");
        when(refrescos.buscarPorHash("hash-vigente")).thenReturn(Optional.of(token));
    }

    private void conTokenNuevoGenerado() {
        when(generadorDeTokens.generar()).thenReturn(new TokenGenerator.GeneratedToken("refresco-nuevo", "hash-nuevo"));
        when(accesos.emitir(usuario, AHORA))
                .thenReturn(
                        new AccessTokenIssuer.IssuedAccessToken("acceso-nuevo", AHORA.plus(Duration.ofMinutes(15))));
    }

    // Criterio 14: rota en cada uso y el anterior queda invalido.
    @Test
    void deberia_rotar_el_token_en_cada_uso_RN_007() {
        conTokenEncontrado(vigente);
        when(usuarios.buscarPorId(usuario.id())).thenReturn(Optional.of(usuario));
        conTokenNuevoGenerado();

        SessionResult sesion = caso.execute(comando());

        assertThat(sesion.refreshToken()).isEqualTo("refresco-nuevo");
        assertThat(sesion.accessToken()).isEqualTo("acceso-nuevo");

        ArgumentCaptor<RefreshToken> consumido = ArgumentCaptor.forClass(RefreshToken.class);
        ArgumentCaptor<RefreshToken> emitido = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refrescos).rotar(consumido.capture(), emitido.capture());

        assertThat(emitido.getValue().tokenHash()).isEqualTo("hash-nuevo");
        assertThat(emitido.getValue().familyId()).isEqualTo(vigente.familyId());
        assertThat(consumido.getValue().id()).isEqualTo(vigente.id());
        assertThat(consumido.getValue().fueReemplazado()).isTrue();
        assertThat(consumido.getValue().replacedBy())
                .isEqualTo(emitido.getValue().id());
    }

    @Test
    void deberia_guardar_el_navegador_y_la_ip_de_esta_peticion_en_el_token_nuevo() {
        conTokenEncontrado(vigente);
        when(usuarios.buscarPorId(usuario.id())).thenReturn(Optional.of(usuario));
        conTokenNuevoGenerado();

        caso.execute(comando());

        ArgumentCaptor<RefreshToken> emitido = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refrescos).rotar(any(), emitido.capture());
        assertThat(emitido.getValue().userAgent()).isEqualTo("Chrome");
        assertThat(emitido.getValue().ipHash()).isEqualTo("ip-nueva");
    }

    // El usuario se recarga: si verifico su correo desde que entro, el token de
    // acceso nuevo tiene que decirlo.
    @Test
    void deberia_reflejar_el_estado_actual_del_usuario_en_el_token_nuevo() {
        User verificado = usuario.conCorreoVerificado(AHORA.minus(Duration.ofHours(2)));
        conTokenEncontrado(vigente);
        when(usuarios.buscarPorId(usuario.id())).thenReturn(Optional.of(verificado));
        when(generadorDeTokens.generar()).thenReturn(new TokenGenerator.GeneratedToken("refresco-nuevo", "hash-nuevo"));
        when(accesos.emitir(verificado, AHORA))
                .thenReturn(
                        new AccessTokenIssuer.IssuedAccessToken("acceso-nuevo", AHORA.plus(Duration.ofMinutes(15))));

        SessionResult sesion = caso.execute(comando());

        assertThat(sesion.user().emailVerified()).isTrue();
    }

    // Criterio 15: un token ya usado revoca la familia completa y avisa al titular.
    @Test
    void deberia_revocar_la_familia_completa_ante_un_token_reutilizado_criterio_15() {
        RefreshToken consumido = vigente.rotar(
                        "hash-siguiente", AHORA.minus(Duration.ofHours(1)), RefreshToken.VIGENCIA, null, null)
                .consumido();
        conTokenEncontrado(consumido);
        when(usuarios.buscarPorId(usuario.id())).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> caso.execute(comando())).isInstanceOf(RefreshTokenInvalidException.class);

        verify(refrescos).revocarFamilia(vigente.familyId(), AHORA);
        verify(correo).enviarAvisoDeSesionRevocadaPorSeguridad(usuario);
        verify(refrescos, never()).rotar(any(), any());
    }

    // Y no emite nada: el que reutiliza el token no se lleva una sesion nueva.
    @Test
    void no_deberia_emitir_sesion_ante_un_token_reutilizado_criterio_15() {
        RefreshToken consumido = vigente.rotar(
                        "hash-siguiente", AHORA.minus(Duration.ofHours(1)), RefreshToken.VIGENCIA, null, null)
                .consumido();
        conTokenEncontrado(consumido);
        when(usuarios.buscarPorId(usuario.id())).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> caso.execute(comando())).isInstanceOf(RefreshTokenInvalidException.class);

        verifyNoInteractions(accesos);
    }

    @Test
    void deberia_revocar_la_familia_aunque_el_titular_ya_no_exista() {
        RefreshToken consumido = vigente.rotar(
                        "hash-siguiente", AHORA.minus(Duration.ofHours(1)), RefreshToken.VIGENCIA, null, null)
                .consumido();
        conTokenEncontrado(consumido);
        when(usuarios.buscarPorId(usuario.id())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> caso.execute(comando())).isInstanceOf(RefreshTokenInvalidException.class);

        verify(refrescos).revocarFamilia(vigente.familyId(), AHORA);
        verifyNoInteractions(correo);
    }

    /**
     * Ventana de gracia de RN-007. Dos pestanas que arrancan a la vez mandan la
     * misma cookie: la primera rota y la segunda llega con el token recien
     * consumido. Eso no es un incidente, es una carrera, y cerrarle la sesion al
     * titular por abrir dos pestanas convierte el aviso mas importante del sistema
     * en ruido que se deja de leer.
     */
    @Test
    void no_deberia_revocar_ante_una_carrera_dentro_de_la_ventana_de_gracia_RN_007() {
        RefreshToken.Rotacion rotacion =
                vigente.rotar("hash-siguiente", AHORA.minus(Duration.ofSeconds(2)), RefreshToken.VIGENCIA, null, null);
        conTokenEncontrado(rotacion.consumido());
        // El que salio de la rotacion sigue sin usarse: nadie ha seguido la cadena.
        when(refrescos.buscarPorId(rotacion.emitido().id())).thenReturn(Optional.of(rotacion.emitido()));

        assertThatThrownBy(() -> caso.execute(comando())).isInstanceOf(RefreshTokenInvalidException.class);

        verify(refrescos, never()).revocarFamilia(any(), any());
        verifyNoInteractions(correo, accesos);
    }

    // Pasada la ventana ya no hay carrera que valer: vuelve a ser el criterio 15.
    @Test
    void deberia_revocar_la_familia_pasada_la_ventana_de_gracia() {
        RefreshToken.Rotacion rotacion =
                vigente.rotar("hash-siguiente", AHORA.minus(GRACIA).minusSeconds(1), RefreshToken.VIGENCIA, null, null);
        conTokenEncontrado(rotacion.consumido());
        when(usuarios.buscarPorId(usuario.id())).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> caso.execute(comando())).isInstanceOf(RefreshTokenInvalidException.class);

        verify(refrescos).revocarFamilia(vigente.familyId(), AHORA);
        verify(correo).enviarAvisoDeSesionRevocadaPorSeguridad(usuario);
    }

    /**
     * La otra mitad de la ventana, y la que la mantiene estrecha: si la cadena ya
     * avanzo, hay alguien usando la sesion y un token viejo que reaparece vuelve a
     * ser un incidente por reciente que sea la rotacion.
     */
    @Test
    void deberia_revocar_dentro_de_la_ventana_si_la_cadena_ya_avanzo() {
        RefreshToken.Rotacion primera =
                vigente.rotar("hash-siguiente", AHORA.minus(Duration.ofSeconds(2)), RefreshToken.VIGENCIA, null, null);
        RefreshToken siguienteYaUsado = primera.emitido()
                .rotar("hash-tercero", AHORA.minus(Duration.ofSeconds(1)), RefreshToken.VIGENCIA, null, null)
                .consumido();

        conTokenEncontrado(primera.consumido());
        when(refrescos.buscarPorId(primera.emitido().id())).thenReturn(Optional.of(siguienteYaUsado));
        when(usuarios.buscarPorId(usuario.id())).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> caso.execute(comando())).isInstanceOf(RefreshTokenInvalidException.class);

        verify(refrescos).revocarFamilia(vigente.familyId(), AHORA);
        verify(correo).enviarAvisoDeSesionRevocadaPorSeguridad(usuario);
    }

    // Si el reemplazo no esta, no hay nada que sostenga la excepcion: se revoca.
    @Test
    void deberia_revocar_si_el_reemplazo_ya_no_existe() {
        RefreshToken.Rotacion rotacion =
                vigente.rotar("hash-siguiente", AHORA.minus(Duration.ofSeconds(2)), RefreshToken.VIGENCIA, null, null);
        conTokenEncontrado(rotacion.consumido());
        when(refrescos.buscarPorId(rotacion.emitido().id())).thenReturn(Optional.empty());
        when(usuarios.buscarPorId(usuario.id())).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> caso.execute(comando())).isInstanceOf(RefreshTokenInvalidException.class);

        verify(refrescos).revocarFamilia(vigente.familyId(), AHORA);
    }

    // Caso borde de HU-001: el navegador bloqueo la cookie. No debe fallar en
    // silencio ni con un error inesperado.
    @Test
    void deberia_rechazar_una_peticion_sin_cookie() {
        assertThatThrownBy(() -> caso.execute(new RefreshSessionCommand(null, "Chrome", null)))
                .isInstanceOf(RefreshTokenInvalidException.class);
        assertThatThrownBy(() -> caso.execute(new RefreshSessionCommand("  ", "Chrome", null)))
                .isInstanceOf(RefreshTokenInvalidException.class);

        verifyNoInteractions(refrescos, generadorDeTokens, correo);
    }

    @Test
    void deberia_rechazar_un_token_que_no_existe() {
        when(generadorDeTokens.hashearRecibido("refresco-en-claro")).thenReturn("hash-vigente");
        when(refrescos.buscarPorHash("hash-vigente")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> caso.execute(comando())).isInstanceOf(RefreshTokenInvalidException.class);

        verify(refrescos, never()).revocarFamilia(any(), any());
        verifyNoInteractions(correo);
    }

    // Una sesion que caduco no es un incidente: no se revoca la familia ni se
    // molesta al titular por haber vuelto a los 40 dias.
    @Test
    void deberia_rechazar_un_token_caducado_sin_revocar_la_familia() {
        RefreshToken caducado = RefreshToken.abrirSesion(
                usuario.id(), "hash-vigente", AHORA.minus(Duration.ofDays(31)), RefreshToken.VIGENCIA, null, null);
        conTokenEncontrado(caducado);
        when(generadorDeTokens.generar()).thenReturn(new TokenGenerator.GeneratedToken("refresco-nuevo", "hash-nuevo"));

        assertThatThrownBy(() -> caso.execute(comando())).isInstanceOf(RefreshTokenInvalidException.class);

        verify(refrescos, never()).revocarFamilia(any(), any());
        verifyNoInteractions(correo);
    }

    @Test
    void deberia_rechazar_un_token_revocado() {
        conTokenEncontrado(vigente.revocar(AHORA.minus(Duration.ofHours(1))));
        when(generadorDeTokens.generar()).thenReturn(new TokenGenerator.GeneratedToken("refresco-nuevo", "hash-nuevo"));

        assertThatThrownBy(() -> caso.execute(comando())).isInstanceOf(RefreshTokenInvalidException.class);

        verify(refrescos, never()).rotar(any(), any());
    }

    @Test
    void deberia_rechazar_un_token_cuyo_usuario_ya_no_existe() {
        conTokenEncontrado(vigente);
        when(usuarios.buscarPorId(usuario.id())).thenReturn(Optional.empty());
        when(generadorDeTokens.generar()).thenReturn(new TokenGenerator.GeneratedToken("refresco-nuevo", "hash-nuevo"));

        assertThatThrownBy(() -> caso.execute(comando())).isInstanceOf(RefreshTokenInvalidException.class);

        verify(refrescos, never()).rotar(any(), any());
    }

    // La cookie nunca se consulta tal cual: se hashea antes de tocar la base.
    @Test
    void deberia_buscar_por_el_hash_y_no_por_el_valor_de_la_cookie() {
        conTokenEncontrado(vigente);
        when(usuarios.buscarPorId(usuario.id())).thenReturn(Optional.of(usuario));
        conTokenNuevoGenerado();

        caso.execute(comando());

        verify(refrescos).buscarPorHash("hash-vigente");
        verify(refrescos, never()).buscarPorHash("refresco-en-claro");
    }
}
