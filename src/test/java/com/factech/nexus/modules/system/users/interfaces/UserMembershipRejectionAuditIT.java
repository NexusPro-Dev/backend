package com.factech.nexus.modules.system.users.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Los rechazos de la asignación de membresía **quedan registrados, y los que no, no**
 * (`RF-SP-032`).
 *
 * <h2>Qué se verifica y por qué importa que sea una prueba y no una lectura del código</h2>
 *
 * <p>`plan.md` §6 reparte los desenlaces entre dos destinos y una papelera, y la frontera no es
 * arbitraria: {@code ck_audit_error_log_status} <b>rechaza</b> en el esquema los estados `400`,
 * `401`, `403` y `404`. Escribir uno de ellos no produce una fila fea: produce una violación de
 * integridad dentro de la transacción de auditoría. Y al revés, un rechazo de negocio que dejara de
 * registrarse no rompe nada visible — la respuesta al cliente es idéntica, y lo único que ocurre es
 * que ese intento deja de poder contarse.
 *
 * <p>Es, otra vez, la clase de cosa que ninguna prueba funcional detecta. La suite de `RF-SP-032`
 * comprueba los códigos de respuesta; esto comprueba **el rastro**.
 *
 * <h2>Se cuenta por diferencia</h2>
 *
 * <p>Las tablas de auditoría <b>no se vacían</b>: la semilla escribe en ellas y otras clases
 * verifican esas filas. Se cuenta antes y después de cada petición, que además es más honesto — la
 * afirmación pasa a ser «esta petición dejó exactamente una fila» en lugar de «hay una fila».
 */
@AutoConfigureMockMvc
class UserMembershipRejectionAuditIT extends IntegrationTestBase {

  private static final String ADMIN_ROL = "01a02a33-4c00-7002-9c4f-5e7ad1000002";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID persona;
  private UUID membresia;

  @BeforeEach
  void preparar() {
    limpiar();

    persona = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status)
        VALUES (?, 'PMembresia', 'pmembresia@factech.co', 'Paula', 'Membresia', 'x', false,
                'ACTIVO')
        """,
        persona);
    // Un rol FUNCIONARIO: la persona NO es consumidora, que es la condición de
    // `EX-001`.
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        persona,
        ADMIN_ROL);

    // La cadena de membresías arranca VACÍA: ninguna migración la siembra, de
    // modo que hay que crear el primer eslabón. Nivel 1 y sin padre, que es lo
    // único que `uq_memberships_parent` admite como raíz.
    membresia = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO memberships (id, code, name, level, color)"
            + " VALUES (?, 'BASICA', 'Basica', 1, '1234AB')",
        membresia);
  }

  @AfterEach
  void limpiarDespues() {
    limpiar();
  }

  @Nested
  @DisplayName("los rechazos de negocio SÍ dejan rastro")
  class SiSeRegistran {

    @Test
    @DisplayName("EX-001 — la persona no es consumidora: 409 con su fila, severidad MEDIA")
    void noEsConsumidora() {
      long antes = filasDeError();

      ejecutar(cuerpo(membresia, null), status().isConflict());

      assertThat(filasDeError() - antes).as("el rechazo debe dejar UNA fila").isEqualTo(1);

      Map<String, Object> fila = ultimoError();
      assertThat(fila.get("error_type")).isEqualTo("BUSINESS_RULE");
      assertThat(fila.get("severity"))
          .as("no es un intento de escalada: es una regla de negocio corriente")
          .isEqualTo("MEDIA");
      assertThat((String) fila.get("resource")).contains("users");
    }

    @Test
    @DisplayName("EX-002 — la membresía no existe: 422 con su fila")
    void membresiaInexistente() {
      long antes = filasDeError();

      ejecutar(cuerpo(UUID.randomUUID(), null), status().isUnprocessableEntity());

      assertThat(filasDeError() - antes).isEqualTo(1);
      assertThat(ultimoError().get("error_type")).isEqualTo("BUSINESS_RULE");
    }
  }

  @Nested
  @DisplayName("y los que el esquema no admite, NO")
  class NoSeRegistran {

    @Test
    @DisplayName("EX-003 — la persona no existe: 404 y ninguna fila")
    void personaInexistente() {
      /*
       * `ck_audit_error_log_status` rechaza el `404`, y la razón está en
       * `architecture.md` §6.6.4: `request_log` ya recoge toda petición, de modo
       * que registrar además aquí cada identificador inventado convertiría un
       * barrido de rutas en ruido dentro del registro que se usa para
       * investigar fallos reales.
       */
      long antes = filasDeError();

      mvc(UUID.randomUUID(), cuerpo(membresia, null), status().isNotFound());

      assertThat(filasDeError() - antes).as("un `404` no se audita como error").isZero();
    }

    @Test
    @DisplayName("un cuerpo mal formado: 400 y ninguna fila")
    void cuerpoInvalido() {
      long antes = filasDeError();

      mvc(persona, "{\"membershipId\":\"esto-no-es-un-uuid\"}", status().isBadRequest());

      assertThat(filasDeError() - antes).as("un `400` de formato no se audita").isZero();
    }
  }

  // ---------------------------------------------------------------------------

  private void ejecutar(
      String cuerpo, org.springframework.test.web.servlet.ResultMatcher esperado) {
    mvc(persona, cuerpo, esperado);
  }

  private void mvc(
      UUID destino, String cuerpo, org.springframework.test.web.servlet.ResultMatcher esperado) {
    try {
      mvc.perform(peticion(destino, cuerpo)).andExpect(esperado);
    } catch (Exception fallo) {
      throw new IllegalStateException(fallo);
    }
  }

  private MockHttpServletRequestBuilder peticion(UUID destino, String cuerpo) {
    return put("/api/v1/users/{id}/membership", destino)
        .with(administrador())
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }

  private static String cuerpo(UUID membresia, String finaliza) {
    return finaliza == null
        ? "{\"membershipId\":\"" + membresia + "\"}"
        : "{\"membershipId\":\"" + membresia + "\",\"endsAt\":\"" + finaliza + "\"}";
  }

  private RequestPostProcessor administrador() {
    return user(SUPERADMIN.toString())
        .authorities(() -> "users:assign-membership", () -> "users:read");
  }

  private long filasDeError() {
    Long cuantas = jdbc.queryForObject("SELECT count(*) FROM audit_error_log", Long.class);
    return cuantas == null ? 0 : cuantas;
  }

  private Map<String, Object> ultimoError() {
    List<Map<String, Object>> filas =
        jdbc.queryForList(
            "SELECT resource, error_type, severity, http_status FROM audit_error_log"
                + " ORDER BY occurred_at DESC, id DESC LIMIT 1");
    assertThat(filas).as("se esperaba una fila de error y no hay ninguna").isNotEmpty();
    return filas.get(0);
  }

  private void limpiar() {
    // Las de auditoría NO: la semilla escribe en ellas y otras clases las leen.
    // Por eso aquí se cuenta por diferencia.
    jdbc.update("DELETE FROM refresh_tokens");
    jdbc.update("DELETE FROM user_supervisors");
    jdbc.update("DELETE FROM user_memberships");
    jdbc.update("DELETE FROM user_roles WHERE user_id <> ?", SUPERADMIN);
    jdbc.update("DELETE FROM users WHERE id <> ?", SUPERADMIN);
    // La cadena de membresías también: esta clase crea su primer eslabón, y sin
    // barrerlo la segunda prueba choca contra `uq_memberships_code`.
    jdbc.update("DELETE FROM memberships");
  }
}
