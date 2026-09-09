package com.factech.nexus.modules.system.users.interfaces;

import static com.factech.nexus.testing.ConcurrencyHarness.runTogether;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.testing.ConcurrencyHarness.Outcome;
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

/**
 * `RF-SP-045` · `T-15` y `CL-003` — dos registros a la vez.
 *
 * <h2>Lo que se prueba es que la garantía NO es la comprobación previa</h2>
 *
 * <p>El caso de uso pregunta si el nombre de usuario está tomado antes de escribir, y esa pregunta
 * <b>no vale nada</b> con dos peticiones simultáneas: las dos la hacen sobre una tabla sin la fila,
 * las dos creen que pueden, y lo que las separa es el índice único. Una prueba secuencial <b>no
 * puede distinguir</b> las dos situaciones — pasa igual si el índice no existiera.
 *
 * <p><b>Y es un endpoint PÚBLICO</b>, que es lo que hace que esto no sea teórico: no hace falta
 * ninguna credencial para lanzar dos peticiones a la vez.
 */
@AutoConfigureMockMvc
class SelfRegistrationConcurrencyIT extends IntegrationTestBase {

  private static final String AGENTE = "01a02a33-4c00-7007-9c4f-5e7ad1000005";
  private static final String BROKER = "01a081f0-6000-7101-9c4f-5e7adb000001";
  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID free;

  @BeforeEach
  void sembrar() {
    limpiar();
    UUID oro = membresia("ORO", "Oro", 1, null, "D4AF37");
    free = membresia("FREE", "Free", 2, oro, "9E9E9E");
    producto(free);
    vendedor();
  }

  @AfterEach
  void vaciar() {
    limpiar();
  }

  @Test
  @DisplayName("`CL-003` — dos registros con el MISMO nombre de usuario: uno entra y el otro no")
  void mismoNombreDeUsuario() {
    // Cada uno con su cuenta de broker: lo que se prueba aquí es la unicidad
    // del nombre de usuario, y con la misma cuenta chocarían por `RN-SP-038`.
    List<Outcome<Integer>> resultados =
        runTogether(2, indice -> estadoDe(registro("ana.ruiz", "ana" + indice + "@x.co", indice)));

    assertThat(resultados).as("alguna petición reventó").allMatch(Outcome::succeeded);

    // Uno `201` y otro que NO. No se afirma cuál rechazo: la carrera puede
    // resolverse en la comprobación previa —`409`— o en el índice, y las dos
    // son correctas mientras solo quede una persona.
    assertThat(resultados).extracting(Outcome::value).contains(201);
    assertThat(cuantas("ana.ruiz")).isOne();
  }

  @Test
  @DisplayName("`RN-SP-038` — dos registros con la MISMA cuenta de broker: uno entra y el otro no")
  void mismaCuentaDeBroker() {
    List<Outcome<Integer>> resultados =
        runTogether(2, indice -> estadoDe(registro("ana" + indice, "ana" + indice + "@x.co", 0)));

    assertThat(resultados).allMatch(Outcome::succeeded);
    assertThat(resultados).extracting(Outcome::value).contains(201);

    // Y la cuenta quedó declarada UNA vez: la transacción de la perdedora se
    // deshizo entera, incluida su persona.
    assertThat(jdbc.queryForObject("SELECT count(*) FROM user_brokers", Integer.class)).isOne();
    assertThat(cuantas("ana0") + cuantas("ana1")).isOne();
  }

  private int estadoDe(MockHttpServletRequestBuilder peticion) throws Exception {
    return mvc.perform(peticion).andReturn().getResponse().getStatus();
  }

  private MockHttpServletRequestBuilder registro(String usuario, String correo, int cuenta) {
    String cuerpo =
        """
        {"product":"REG_FREE","referrer":"reg-agente",
         "firstName":"Ana","lastName":"Ruiz",
         "username":"%s","email":"%s","password":"ClaveSegura2026!",
         "countryCode":"%s","documentType":"CC","documentNumber":"%s",
         "phone":"+573001234567",
         "brokerId":"%s","brokerAccountId":"cuenta-%d"}
        """
            .formatted(
                usuario, correo, pais(), Integer.toString(correo.hashCode()), BROKER, cuenta);

    return post("/api/v1/auth/registration")
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }

  private String pais() {
    return jdbc.queryForObject(
        "SELECT code FROM countries WHERE is_active ORDER BY code LIMIT 1", String.class);
  }

  private int cuantas(String usuario) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM users WHERE username = ?", Integer.class, usuario);
  }

  private UUID membresia(String codigo, String nombre, int nivel, UUID superior, String color) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO memberships (id, code, name, level, color, parent_membership_id)"
            + " VALUES (CAST(? AS uuid), ?, ?, ?, ?, CAST(? AS uuid))",
        id.toString(),
        codigo,
        nombre,
        nivel,
        color,
        superior == null ? null : superior.toString());
    return id;
  }

  private void producto(UUID membresia) {
    jdbc.update(
        "INSERT INTO products (id, code, type, name, source_membership_id, target_membership_id,"
            + " price, currency_id, validity_days, status, scope, implementation)"
            + " VALUES (CAST(? AS uuid), 'REG_FREE', 'UPGRADE_MEMBRESIA', 'Renovación',"
            + " CAST(? AS uuid), CAST(? AS uuid), 0.00, CAST(? AS uuid), 30, 'ACTIVO', 'TIENDA',"
            + " 'AUTOMATICA')",
        UUID.randomUUID().toString(),
        membresia.toString(),
        membresia.toString(),
        USD);
  }

  private void vendedor() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, username, email, first_name, last_name, password_hash, status,"
            + " country_id)"
            + " VALUES (CAST(? AS uuid), 'reg-agente', 'reg-agente@nexus.test', 'A', 'B', 'x',"
            + " 'ACTIVO', (SELECT id FROM countries ORDER BY code LIMIT 1))",
        id.toString());
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT CAST(? AS uuid), CAST(? AS uuid), role_type FROM roles WHERE id = CAST(? AS"
            + " uuid)",
        id.toString(),
        AGENTE,
        AGENTE);
  }

  private void limpiar() {
    jdbc.update("DELETE FROM user_brokers");
    jdbc.update("DELETE FROM refresh_tokens");
    jdbc.update("DELETE FROM user_supervisors");
    jdbc.update("DELETE FROM user_memberships");
    jdbc.update(
        "DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'reg-%'"
            + " OR username LIKE 'ana%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'reg-%' OR username LIKE 'ana%'");
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM memberships");
  }
}
