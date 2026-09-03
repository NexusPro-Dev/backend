package com.factech.nexus.modules.system.users.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.shared.security.PasswordHasher;
import java.util.List;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Editar el propio perfil (`RF-SP-044`, `CA-SP-494` a `CA-SP-506`).
 *
 * <p><b>El actor se autentica SIN NINGUNA AUTORIDAD</b>, y no es un detalle del montaje: es la
 * mitad de lo que este requerimiento existe para demostrar. Si alguna prueba necesitara conceder un
 * permiso para pasar, el endpoint no estaría resolviendo el problema que lo justifica.
 */
@AutoConfigureMockMvc
class UpdateOwnProfileIT extends IntegrationTestBase {

  private static final String CLAVE = "ClaveLargaYSegura2026";
  private static final String CLAVE_MALA = "EstaNoEsLaClave2026";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PasswordHasher hasher;

  private UUID juan;
  private UUID otra;

  @BeforeEach
  void preparar() {
    limpiar();
    juan = crearPersona("jperez", "juan.perez@factech.co");
    otra = crearPersona("mgomez", "maria.gomez@factech.co");
  }

  @AfterEach
  void devolverElEstadoCompartidoASuSitio() {
    limpiar();
  }

  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-SP-494 · una persona SIN NINGÚN PERMISO cambia su nombre y sus apellidos")
  void sinPermisoCambiaSuNombre() throws Exception {
    mvc.perform(editar(juan, "{\"firstName\":\"Juan Carlos\",\"lastName\":\"Pérez Gil\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstName").value("Juan Carlos"))
        .andExpect(jsonPath("$.lastName").value("Pérez Gil"));

    assertThat(campo(juan, "first_name")).isEqualTo("Juan Carlos");
  }

  @Test
  @DisplayName("CA-SP-495 · la operación afecta solo al actor, y el cuerpo no admite identificador")
  void soloAlActor() throws Exception {
    // El campo no existe: Jackson lo rechaza en lugar de ignorarlo, que es lo
    // que impide desviar la operación hacia otra persona.
    mvc.perform(editar(juan, "{\"firstName\":\"Intruso\",\"userId\":\"" + otra + "\"}"))
        .andExpect(status().isBadRequest());

    assertThat(campo(otra, "first_name")).isEqualTo("Juan");
    assertThat(campo(juan, "first_name")).isEqualTo("Juan");
  }

  @Test
  @DisplayName("CA-SP-496 · con correo y contraseña correcta, el correo queda cambiado")
  void cambiaElCorreoConSuContrasena() throws Exception {
    mvc.perform(editar(juan, cuerpoConCorreo("nuevo@factech.co", CLAVE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("nuevo@factech.co"));

    assertThat(campo(juan, "email")).isEqualTo("nuevo@factech.co");
  }

  @Test
  @DisplayName("CA-SP-497 · con correo y SIN contraseña se rechaza con VAL-006, y nada cambia")
  void elCorreoSinContrasenaSeRechaza() throws Exception {
    mvc.perform(editar(juan, "{\"email\":\"nuevo@factech.co\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-006"));

    assertThat(campo(juan, "email")).isEqualTo("juan.perez@factech.co");
  }

  @Test
  @DisplayName("CA-SP-498 · con la contraseña equivocada se rechaza con VAL-007, y nada cambia")
  void elCorreoConContrasenaEquivocadaSeRechaza() throws Exception {
    mvc.perform(editar(juan, cuerpoConCorreo("nuevo@factech.co", CLAVE_MALA)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-007"));

    assertThat(campo(juan, "email")).isEqualTo("juan.perez@factech.co");
  }

  @Test
  @DisplayName("CA-SP-499 · cambiar solo el nombre NO exige la contraseña actual")
  void elNombreNoExigeContrasena() throws Exception {
    mvc.perform(editar(juan, "{\"firstName\":\"Juan Carlos\"}")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("CA-SP-500 · un correo de otra persona se rechaza sin decir de quién es")
  void elCorreoAjenoSeRechaza() throws Exception {
    String cuerpo =
        mvc.perform(editar(juan, cuerpoConCorreo("maria.gomez@factech.co", CLAVE)))
            .andExpect(status().isConflict())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(cuerpo).doesNotContain("mgomez").doesNotContain(otra.toString());
    assertThat(campo(juan, "email")).isEqualTo("juan.perez@factech.co");
  }

  @Test
  @DisplayName("CA-SP-501 · el nombre de usuario no cambia aunque se envíe")
  void elNombreDeUsuarioNoSeToca() throws Exception {
    mvc.perform(editar(juan, "{\"username\":\"otro\"}")).andExpect(status().isBadRequest());

    assertThat(campo(juan, "username")).isEqualTo("jperez");
  }

  @Test
  @DisplayName("CA-SP-502 · roles, membresía, estado y superior quedan intactos")
  void loQueNoSeToca() throws Exception {
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT ?, id, role_type FROM roles WHERE code = 'CLIENTE'",
        juan);
    // NO se depende del catálogo sembrado: varias clases de la suite hacen
    // `DELETE FROM memberships`, de modo que `FREE` puede no existir según el
    // orden de ejecución. Se crea una propia si la tabla quedó vacía, y se
    // asigna la primera que haya.
    jdbc.update(
        """
        INSERT INTO memberships (id, code, name, description, parent_membership_id, level, color)
        SELECT ?, 'PRUEBA044', 'Prueba 044', NULL, NULL, 1, 'ABCDEF'
         WHERE NOT EXISTS (SELECT 1 FROM memberships)
        """,
        UUID.randomUUID());
    jdbc.update(
        "INSERT INTO user_memberships (user_id, membership_id)"
            + " SELECT ?, id FROM memberships ORDER BY level LIMIT 1",
        juan);
    jdbc.update(
        "INSERT INTO user_supervisors (id, user_id, supervisor_id) VALUES (?, ?, ?)",
        UUID.randomUUID(),
        juan,
        otra);

    mvc.perform(editar(juan, cuerpoConCorreo("nuevo@factech.co", CLAVE)))
        .andExpect(status().isOk());

    assertThat(cuenta("SELECT count(*) FROM user_roles WHERE user_id = ?", juan)).isEqualTo(1);
    assertThat(cuenta("SELECT count(*) FROM user_memberships WHERE user_id = ?", juan))
        .isEqualTo(1);
    assertThat(
            cuenta(
                "SELECT count(*) FROM user_supervisors WHERE user_id = ? AND ended_at IS NULL",
                juan))
        .isEqualTo(1);
    assertThat(campo(juan, "status")).isEqualTo("ACTIVO");
  }

  @Test
  @DisplayName("CA-SP-503 · el correo emite evento de seguridad ALTA; el nombre, no")
  void soloElCorreoEsEventoDeSeguridad() throws Exception {
    mvc.perform(editar(juan, "{\"firstName\":\"Juan Carlos\"}")).andExpect(status().isOk());
    assertThat(eventosDeSeguridad(juan)).isZero();

    mvc.perform(editar(juan, cuerpoConCorreo("nuevo@factech.co", CLAVE)))
        .andExpect(status().isOk());

    assertThat(
            cuenta(
                "SELECT count(*) FROM audit_security_log WHERE target_user_id = ?"
                    + " AND event_type = 'EMAIL_CHANGED' AND severity = 'ALTA'"
                    + " AND outcome = 'SUCCESS'",
                juan))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("CA-SP-504 · la contraseña fallida se audita y NO bloquea la cuenta")
  void elFalloNoSeConvierteEnArma() throws Exception {
    // Se repite más veces que cualquier umbral razonable de bloqueo. Si esto
    // contara como intento fallido de inicio de sesión, quien tuviera una sesión
    // ajena podría dejar fuera a la persona legítima.
    for (int intento = 0; intento < 6; intento++) {
      mvc.perform(editar(juan, cuerpoConCorreo("nuevo@factech.co", CLAVE_MALA)))
          .andExpect(status().isUnprocessableEntity());
    }

    assertThat(eventosDeSeguridad(juan)).isEqualTo(6);
    assertThat(
            jdbc.queryForObject(
                "SELECT failed_attempts FROM users WHERE id = ?", Integer.class, juan))
        .isZero();
    assertThat(
            jdbc.queryForObject("SELECT locked_until FROM users WHERE id = ?", Object.class, juan))
        .isNull();
  }

  @Test
  @DisplayName("CA-SP-505 · enviar los mismos valores no registra auditoría y no es un error")
  void sinCambioEfectivoNoHayAuditoria() throws Exception {
    long antes = cuenta("SELECT count(*) FROM audit_change_log WHERE entity_id = ?", juan);

    mvc.perform(editar(juan, "{\"firstName\":\"Juan\",\"lastName\":\"Pérez\"}"))
        .andExpect(status().isOk());

    assertThat(cuenta("SELECT count(*) FROM audit_change_log WHERE entity_id = ?", juan))
        .isEqualTo(antes);
  }

  @Test
  @DisplayName("CA-SP-505 · el correo repetido sigue exigiendo la contraseña")
  void elCorreoRepetidoSigueExigiendoLaContrasena() throws Exception {
    // Si la exigencia dependiera de que el valor cambie de verdad, el endpoint
    // diría cuál es el correo vigente: el que no la pidiera sería el bueno.
    mvc.perform(editar(juan, "{\"email\":\"juan.perez@factech.co\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-006"));
  }

  @Test
  @DisplayName("CA-SP-506 · sin credencial se rechaza como no autenticada")
  void sinTokenNoSeAtiende() throws Exception {
    mvc.perform(
            patch("/api/v1/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Juan Carlos\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("VAL-001 · sin ningún campo modificable se rechaza")
  void sinCamposSeRechaza() throws Exception {
    mvc.perform(editar(juan, "{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-001"));
  }

  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder editar(UUID quien, String cuerpo) {
    return patch("/api/v1/users/me")
        .with(comoActor(quien))
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }

  /** Sin autoridades: es lo que hace verificable `CA-SP-494`. */
  private RequestPostProcessor comoActor(UUID quien) {
    return user(quien.toString()).authorities();
  }

  private static String cuerpoConCorreo(String correo, String clave) {
    return "{\"email\":\"" + correo + "\",\"currentPassword\":\"" + clave + "\"}";
  }

  private String campo(UUID quien, String columna) {
    return jdbc.queryForObject(
        "SELECT " + columna + " FROM users WHERE id = ?", String.class, quien);
  }

  private long cuenta(String sql, Object... args) {
    Long total = jdbc.queryForObject(sql, Long.class, args);
    return total == null ? 0 : total;
  }

  private long eventosDeSeguridad(UUID objetivo) {
    return cuenta("SELECT count(*) FROM audit_security_log WHERE target_user_id = ?", objetivo);
  }

  private UUID crearPersona(String username, String correo) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status)
        VALUES (?, ?, ?, 'Juan', 'Pérez', ?, false, 'ACTIVO')
        """,
        id,
        username,
        correo,
        hasher.hash(CLAVE));
    return id;
  }

  private void limpiar() {
    jdbc.update("DELETE FROM audit_security_log WHERE target_user_id IS NOT NULL");
    jdbc.update("DELETE FROM refresh_tokens");
    jdbc.update("DELETE FROM user_supervisors");
    jdbc.update("DELETE FROM user_memberships");
    jdbc.update("DELETE FROM memberships WHERE code = 'PRUEBA044'");
    jdbc.update("DELETE FROM user_roles WHERE user_id <> ?", SUPERADMIN);
    jdbc.update("DELETE FROM users WHERE id <> ?", SUPERADMIN);
    for (String tabla : List.of("audit_change_log")) {
      jdbc.update(
          "DELETE FROM " + tabla + " WHERE entity = 'users' AND entity_id <> ?", SUPERADMIN);
    }
  }
}
