package com.factech.nexus.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * El token de acceso deja de admitirse <b>de inmediato</b> (`RF-SP-028` · `T-09` y `T-10`,
 * `security.md` §4.5).
 *
 * <p><b>Qué prueba esta clase que no probaba ninguna otra.</b> Que retirarle el acceso a alguien
 * revoca sus refresh tokens estaba verificado desde el 24-08-2026. Lo que no lo estaba —y no era
 * cierto— es la otra mitad: su <b>token de acceso</b>, un JWT firmado que se valida sin consultar
 * la base, seguía abriendo puertas los quince minutos que le quedaran de vida. Quien fuera
 * desactivado, eliminado o le restablecieran la contraseña conservaba un cuarto de hora de acceso,
 * y ninguna prueba lo decía.
 *
 * <p>La víctima autentica de verdad y presenta su token: con el actor simulado no hay token que
 * cortar y la prueba daría verde sin ejercitar nada. Quien ejecuta la operación administrativa sí
 * va simulado — lo que aquí se verifica es el corte, no su autenticación, que ya está probada
 * aparte.
 */
@AutoConfigureMockMvc
class AccessRevocationIT extends IntegrationTestBase {

  private static final String CONTABILIDAD = "01a02a33-4c00-7003-9c4f-5e7ad1000003";
  private static final String SUPERADMIN_ROL = "01a02a33-4c00-7001-9c4f-5e7ad1000001";
  private static final String CLAVE = "ClaveLargaYSegura2026";
  private static final String NUEVA = "OtraClaveLargaDistinta2026";

  /** Un endpoint que `CONTABILIDAD` sí puede usar. */
  private static final String CUALQUIERA = "/api/v1/audit/changes";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;
  @Autowired private PasswordHasher hasher;
  @Autowired private AccessRevocationRegistry registro;

  private UUID persona;

  @BeforeEach
  void prepararCuenta() {
    limpiar();
    persona = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status)
        VALUES (?, 'JPerez', 'juan@factech.co', 'Juan', 'Pérez', ?, false, 'ACTIVO')
        """,
        persona,
        hasher.hash(CLAVE));
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?::uuid)", persona, CONTABILIDAD);
  }

  @AfterEach
  void limpiarDespues() {
    limpiar();
  }

  private void limpiar() {
    jdbc.update("DELETE FROM refresh_tokens");
    jdbc.update("DELETE FROM user_supervisors");
    jdbc.update("DELETE FROM user_memberships");
    jdbc.update("DELETE FROM user_roles WHERE user_id <> ?", SUPERADMIN);
    jdbc.update("DELETE FROM users WHERE id <> ?", SUPERADMIN);
    jdbc.update(
        """
        UPDATE users
           SET status = 'ACTIVO', deleted_at = NULL, locked_until = NULL, failed_attempts = 0,
               must_change_password = true, provisional_password_expires_at = NULL
         WHERE id = ?
        """,
        SUPERADMIN);
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?::uuid) ON CONFLICT DO NOTHING",
        SUPERADMIN,
        SUPERADMIN_ROL);
  }

  // ---------------------------------------------------------------------------
  // Los cuatro caminos que retiran el acceso
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-SP-233` — desactivar corta el token de acceso en la petición SIGUIENTE")
  void desactivarCortaElToken() throws Exception {
    String token = accessToken(login());
    mvc.perform(conToken(token)).andExpect(status().isOk());

    mvc.perform(
            patch("/api/v1/users/{id}/status", persona)
                .with(user(SUPERADMIN.toString()).authorities(() -> "users:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INACTIVO\",\"reason\":\"Baja temporal del contrato\"}"))
        .andExpect(status().isOk());

    // Sin esperar a que expire, y sin que la persona vuelva a pedir nada.
    mvc.perform(conToken(token)).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("eliminar corta el token, que es donde más importa: no hay reactivación posible")
  void eliminarCortaElToken() throws Exception {
    String token = accessToken(login());

    mvc.perform(
            post("/api/v1/users/{id}/deletion", persona)
                .with(user(SUPERADMIN.toString()).authorities(() -> "users:delete"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Terminación del contrato laboral\"}"))
        .andExpect(status().is2xxSuccessful());

    mvc.perform(conToken(token)).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName(
      "restablecer la contraseña de otro corta su token: es la ventana que se quiere cerrar")
  void restablecerCortaElToken() throws Exception {
    String token = accessToken(login());

    mvc.perform(
            post("/api/v1/users/{id}/password-reset", persona)
                .with(user(SUPERADMIN.toString()).authorities(() -> "users:reset-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"%s\"}".formatted(NUEVA)))
        .andExpect(status().is2xxSuccessful());

    mvc.perform(conToken(token)).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("cambiar la propia contraseña corta el token viejo y NO el que se emite después")
  void cambiarLaPropiaCortaElViejoYNoElNuevo() throws Exception {
    String viejo = accessToken(login());

    mvc.perform(
            post("/api/v1/auth/password")
                .header("Authorization", "Bearer " + viejo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(CLAVE, NUEVA)))
        .andExpect(status().isNoContent());

    mvc.perform(conToken(viejo)).andExpect(status().isUnauthorized());

    // Y volver a entrar funciona EN EL ACTO, dentro del mismo segundo del corte.
    // Es lo que sostiene que el emisor selle el token con el corte cuando este
    // es posterior al reloj: sin eso, el `iat` en segundos enteros no distingue
    // este token del que acaba de morir, y el fallo dependería de en qué
    // milisegundo cayera la petición.
    mvc.perform(conToken(accessToken(login("JPerez", NUEVA)))).andExpect(status().isOk());
  }

  @Test
  @DisplayName("el corte no depende del milisegundo: un token sellado EN el corte sobrevive")
  void elSegundoDelCorteNoEsAmbiguo() {
    RelojMovible reloj = new RelojMovible(Instant.parse("2026-08-26T10:00:00.400Z"));
    var propio = new AccessRevocationRegistry(jdbc, Duration.ofMinutes(15), reloj);

    propio.publicarCorte(persona);

    // Un token cuyo `iat` truncó al segundo del corte nació ANTES: muere.
    assertThat(propio.estaCortado(persona, Instant.parse("2026-08-26T10:00:00Z"))).isTrue();

    // Y uno sellado con el corte mismo —lo que hace el emisor tras revocar—
    // sobrevive, sin esperar al segundo siguiente.
    assertThat(propio.estaCortado(persona, Instant.parse("2026-08-26T10:00:01Z"))).isFalse();
  }

  // ---------------------------------------------------------------------------
  // El registro por dentro
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`T-10` — el corte NO se publica si la transacción se revierte")
  void sinCommitNoHayCorte() throws Exception {
    accessToken(login());
    int antes = registro.tamano();

    // `RN-SP-017` rechaza que alguien se restablezca su propia contraseña, y lo
    // hace DESPUÉS de haber leído y bloqueado la fila: la transacción se
    // revierte entera. Publicar el corte dentro de ella habría invalidado los
    // tokens del superadministrador por un cambio que no llegó a ocurrir.
    mvc.perform(
            post("/api/v1/users/{id}/password-reset", SUPERADMIN)
                .with(user(SUPERADMIN.toString()).authorities(() -> "users:reset-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"%s\"}".formatted(NUEVA)))
        .andExpect(status().isConflict());

    assertThat(registro.tamano()).isEqualTo(antes);
  }

  @Test
  @DisplayName("el corte caduca solo: el registro no crece sin límite ni necesita barrendero")
  void elRegistroNoCrece() {
    RelojMovible reloj = new RelojMovible(Instant.parse("2026-08-26T10:00:00Z"));
    var propio = new AccessRevocationRegistry(jdbc, Duration.ofMinutes(15), reloj);

    propio.publicarCorte(persona);
    assertThat(propio.estaCortado(persona, reloj.instant().minusSeconds(60))).isTrue();
    assertThat(propio.tamano()).isEqualTo(1);

    // Dieciséis minutos después el corte ya no distingue nada: ningún token
    // anterior a él sigue siendo válido por firma. La entrada se retira al
    // consultarla, y eso es lo que acota el registro sin una tarea de limpieza.
    reloj.avanzar(Duration.ofMinutes(16));
    assertThat(propio.estaCortado(persona, Instant.parse("2026-08-26T09:59:00Z"))).isFalse();
    assertThat(propio.tamano()).isZero();
  }

  @Test
  @DisplayName("`T-09` — al arrancar el registro se siembra: reiniciar no devuelve la validez")
  void elReinicioNoDevuelveLaValidez() {
    jdbc.update("UPDATE users SET status = 'INACTIVO', updated_at = now() WHERE id = ?", persona);

    // Un proceso recién arrancado: el registro nace vacío.
    var reciennacido = new AccessRevocationRegistry(jdbc, Duration.ofMinutes(15));
    assertThat(reciennacido.estaCortado(persona, Instant.now().minusSeconds(120))).isFalse();

    reciennacido.sembrar();

    // El token emitido antes de la desactivación sigue cortado tras el
    // reinicio. Sin la siembra volvería a valer, y es un agujero que ninguna
    // prueba funcional detecta: solo aparece reiniciando el proceso entre el
    // corte y la expiración del token.
    assertThat(reciennacido.estaCortado(persona, Instant.now().minusSeconds(120))).isTrue();
  }

  @Test
  @DisplayName("la siembra no alcanza a quien perdió el acceso hace más de una vida de token")
  void laSiembraNoResucitaCortesViejos() {
    jdbc.update(
        """
        UPDATE users SET status = 'INACTIVO', updated_at = now() - interval '2 hours'
         WHERE id = ?
        """,
        persona);

    var reciennacido = new AccessRevocationRegistry(jdbc, Duration.ofMinutes(15));
    reciennacido.sembrar();

    // No hay nada que cortar: sus tokens ya no valen por firma. Sembrarlos
    // llenaría el registro de entradas inútiles en cada arranque, con una fila
    // por cada cuenta inactiva de la historia del sistema.
    assertThat(reciennacido.tamano()).isZero();
  }

  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder conToken(String token) {
    return get(CUALQUIERA).header("Authorization", "Bearer " + token);
  }

  private MockHttpServletRequestBuilder login() {
    return login("JPerez", CLAVE);
  }

  private MockHttpServletRequestBuilder login(String identificador, String clave) {
    return post("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"identifier\":\"%s\",\"password\":\"%s\"}".formatted(identificador, clave));
  }

  private String accessToken(MockHttpServletRequestBuilder peticion) throws Exception {
    JsonNode arbol =
        json.readTree(mvc.perform(peticion).andReturn().getResponse().getContentAsString());
    return arbol.get("accessToken").asText();
  }

  /**
   * {@code Clock.fixed} no se mueve, y aquí lo que se prueba es precisamente el paso del tiempo.
   */
  private static final class RelojMovible extends Clock {

    private Instant ahora;

    private RelojMovible(Instant desde) {
      this.ahora = desde;
    }

    void avanzar(Duration cuanto) {
      ahora = ahora.plus(cuanto);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zona) {
      return this;
    }

    @Override
    public Instant instant() {
      return ahora;
    }
  }
}
