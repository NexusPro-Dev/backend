package com.factech.nexus.modules.products.interfaces;

import static com.factech.nexus.testing.ConcurrencyHarness.runTogether;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.testing.ConcurrencyHarness.Outcome;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
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
 * `RF-PM-005` · `T-10` y `T-11` — la tarea que decide si este requerimiento está bien hecho.
 *
 * <p><b>Las demás pruebas pasan con la implementación equivocada.</b> Dos upgrades inactivos hacia
 * el mismo nivel activados a la vez: las dos transacciones leen que el destino está libre, las dos
 * concluyen que pueden proceder, y con solo la verificación previa quedarían <b>las dos activas</b>
 * — que es exactamente el desenlace que `RN-PM-004` existe para impedir. Es la misma escritura
 * sesgada que `RN-SP-018` costó en `SP`.
 *
 * <p><b>No basta con comprobar que hubo un `409`.</b> Hay que contar cuántos quedaron activos: un
 * rechazo por el motivo equivocado —o dos rechazos— daría el mismo recuento de errores y un estado
 * distinto.
 */
@AutoConfigureMockMvc
class ProductStatusConcurrencyIT extends IntegrationTestBase {

  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  private static final OffsetDateTime BASE =
      OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC);

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID oro;

  @BeforeEach
  void sembrar() {
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM memberships");
    oro = membresia("ORO", "Oro", 1);
  }

  @AfterEach
  void limpiar() {
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM audit_change_log WHERE module = 'PM'");
  }

  @Test
  @DisplayName(
      "`T-10` — dos activaciones simultáneas hacia el MISMO destino: queda exactamente una")
  void dosActivacionesHaciaElMismoDestino() {
    UUID primero = upgrade("UPGRADE_ORO", "Ascenso a Oro", oro);
    UUID segundo = upgrade("UPGRADE_ORO_2", "Ascenso a Oro premium", oro);

    List<Outcome<Integer>> resultados =
        runTogether(2, indice -> estadoDe(cambiar(indice == 0 ? primero : segundo, "ACTIVO")));

    // Ninguna puede salir como 500: la violación del índice tiene que llegar
    // TRADUCIDA, o el cliente no sabe que su problema es el destino ocupado.
    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);

    // LO QUE DECIDE LA PRUEBA. Que hubiera un 409 no basta: dos rechazos darían
    // el mismo recuento de errores y dejarían el destino sin ningún upgrade.
    assertThat(cuantosActivosHacia(oro))
        .as("`RN-PM-004`: ni dos precios simultáneos para el mismo nivel, ni ninguno")
        .isEqualTo(1);

    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 200).count())
        .as("exactamente una activación debía prosperar")
        .isEqualTo(1);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 409).count())
        .as("y exactamente una debía rechazarse con 409")
        .isEqualTo(1);
  }

  @Test
  @DisplayName("`T-10` — y con TRES compitiendo, sigue quedando exactamente una")
  void tresActivacionesHaciaElMismoDestino() {
    UUID uno = upgrade("UPGRADE_A", "Ascenso A", oro);
    UUID dos = upgrade("UPGRADE_B", "Ascenso B", oro);
    UUID tres = upgrade("UPGRADE_C", "Ascenso C", oro);
    List<UUID> candidatos = List.of(uno, dos, tres);

    List<Outcome<Integer>> resultados =
        runTogether(3, indice -> estadoDe(cambiar(candidatos.get(indice), "ACTIVO")));

    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);
    assertThat(cuantosActivosHacia(oro)).isEqualTo(1);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 200).count())
        .isEqualTo(1);
  }

  @Test
  @DisplayName("`T-11` — desactivar el que ocupa el destino mientras otro se activa")
  void desactivarYActivarEnCarrera() throws Exception {
    UUID ocupante = upgrade("UPGRADE_ORO", "Ascenso a Oro", oro);
    UUID aspirante = upgrade("UPGRADE_ORO_2", "Ascenso a Oro premium", oro);
    mvc.perform(cambiar(ocupante, "ACTIVO"));

    List<Callable<Integer>> carrera =
        List.of(
            () -> estadoDe(cambiar(ocupante, "INACTIVO")),
            () -> estadoDe(cambiar(aspirante, "ACTIVO")));

    List<Outcome<Integer>> resultados = runTogether(carrera);

    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);

    // LOS DOS DESENLACES VALEN, y por eso la afirmación no es sobre quién ganó:
    // si la activación llega primero, se rechaza y queda el ocupante; si llega
    // después, entra y el ocupante ya se fue. Lo que NO puede pasar es que
    // queden los dos — eso rompería `RN-PM-004`.
    assertThat(cuantosActivosHacia(oro)).as("uno o ninguno, nunca dos").isBetween(0, 1);
  }

  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder cambiar(UUID id, String estado) {
    return patch("/api/v1/products/{id}/status", id)
        .with(user(UUID.randomUUID().toString()).authorities(() -> "products:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"" + estado + "\"}");
  }

  private int estadoDe(MockHttpServletRequestBuilder peticion) throws Exception {
    return mvc.perform(peticion).andReturn().getResponse().getStatus();
  }

  private int cuantosActivosHacia(UUID destino) {
    Integer filas =
        jdbc.queryForObject(
            "SELECT count(*) FROM products WHERE target_membership_id = CAST(? AS uuid)"
                + " AND status = 'ACTIVO' AND deleted_at IS NULL",
            Integer.class,
            destino.toString());
    return filas == null ? 0 : filas;
  }

  private UUID membresia(String codigo, String nombre, int nivel) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO memberships (id, code, name, parent_membership_id, level, color)"
            + " VALUES (?, ?, ?, NULL, ?, upper(lpad(to_hex(? * 4919), 6, '0')))",
        id,
        codigo,
        nombre,
        nivel,
        nivel);
    return id;
  }

  /** Todos con descripción: sin ella `RN-PM-014` los pararía antes de llegar a la carrera. */
  private UUID upgrade(String codigo, String nombre, UUID destino) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (id, code, type, name, description, target_membership_id, price,"
            + " currency_id, validity_days, status, created_at, updated_at)"
            + " VALUES (CAST(? AS uuid), ?, 'UPGRADE_MEMBRESIA', ?, 'Sube al nivel oro.',"
            + " CAST(? AS uuid), 10.00, CAST(? AS uuid), NULL, 'INACTIVO', ?, ?)",
        id.toString(),
        codigo,
        nombre,
        destino.toString(),
        USD,
        BASE,
        BASE);
    return id;
  }
}
