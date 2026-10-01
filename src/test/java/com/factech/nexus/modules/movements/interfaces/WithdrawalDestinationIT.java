package com.factech.nexus.modules.movements.interfaces;

import static com.factech.nexus.modules.movements.LedgerFixtures.USD;
import static com.factech.nexus.modules.movements.LedgerFixtures.llenarBilletera;
import static com.factech.nexus.modules.movements.LedgerFixtures.saldo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.movements.LedgerFixtures;
import com.factech.nexus.modules.movements.PayoutFixtures;
import com.factech.nexus.modules.movements.domain.service.CreditService;
import com.jayway.jsonpath.JsonPath;
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
 * A dónde se paga un retiro (`RN-MV-056`): la enmienda de `RF-MV-019` —el retiro exige una cuenta y
 * copia su destino— y la de `RF-MV-007` —el detalle lo publica—. Y los criterios de las cuentas que
 * necesitan un retiro con destino: `CA-MV-402`, `CA-MV-406` y `CA-MV-407`.
 */
@AutoConfigureMockMvc
class WithdrawalDestinationIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CreditService abonos;

  private UUID persona;
  private UUID administrador;
  private UUID banco;
  private UUID principal;

  @BeforeEach
  void sembrar() {
    limpiar();
    persona = persona("wdd-persona");
    administrador = persona("wdd-admin");
    PayoutFixtures.documento(jdbc, persona);
    banco = PayoutFixtures.entidad(jdbc, "BANCO", true);
    principal = PayoutFixtures.cuenta(jdbc, persona, banco, "1111222233", true);
    llenarBilletera(abonos, persona, "100.00");
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-019` · enmienda del 01-10-2026
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-415 — con una cuenta indicada, el retiro guarda la copia de su destino y la"
          + " respuesta la trae")
  void copiaLaIndicada() throws Exception {
    UUID movil = PayoutFixtures.entidad(jdbc, "BILLETERA_MOVIL", true);
    UUID otra = cuentaMovil(movil, "3005556677");
    String documento = documento();
    String codigoMovil =
        jdbc.queryForObject(
            "SELECT code FROM payout_institutions WHERE id = ?", String.class, movil);

    String cuerpo =
        mvc.perform(pedir("10.00", otra))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.destination.payoutAccountId").value(otra.toString()))
            .andExpect(jsonPath("$.destination.institution.code").value(codigoMovil))
            .andExpect(jsonPath("$.destination.institution.kind").value("BILLETERA_MOVIL"))
            .andExpect(jsonPath("$.destination.accountType").doesNotExist())
            .andExpect(jsonPath("$.destination.number").value("3005556677"))
            .andExpect(jsonPath("$.destination.holder.name").value("Nombre Apellido"))
            .andExpect(jsonPath("$.destination.holder.documentType").value("CC"))
            .andExpect(jsonPath("$.destination.holder.documentNumber").value(documento))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID retiro = UUID.fromString(JsonPath.read(cuerpo, "$.movement.id"));
    assertThat(
            jdbc.queryForObject(
                "SELECT institution_code || '|' || institution_kind || '|' || number || '|'"
                    + " || holder_name || '|' || holder_document_type || '|'"
                    + " || holder_document_number FROM withdrawal_destinations"
                    + " WHERE movement_id = ? AND payout_account_id = ?",
                String.class,
                retiro,
                otra))
        .isEqualTo(codigoMovil + "|BILLETERA_MOVIL|3005556677|Nombre Apellido|CC|" + documento);
  }

  @Test
  @DisplayName("CA-MV-416 — sin indicar cuenta, el retiro va a la principal")
  void vaALaPrincipal() throws Exception {
    cuentaMovil(PayoutFixtures.entidad(jdbc, "BILLETERA_MOVIL", true), "3001110000");
    mvc.perform(pedir("10.00", null))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.destination.payoutAccountId").value(principal.toString()))
        .andExpect(jsonPath("$.destination.accountType").value("AHORROS"))
        .andExpect(jsonPath("$.destination.number").value("1111222233"));
  }

  @Test
  @DisplayName(
      "CA-MV-417 y CA-MV-406 — quien no tiene ninguna cuenta (también tras dar de baja la"
          + " última) recibe 409, y no queda retiro, ni copia, ni asiento")
  void sinCuenta() throws Exception {
    mvc.perform(
            delete("/api/v1/movements/mine/payout-accounts/{id}", principal)
                .with(
                    user(persona.toString())
                        .authorities(() -> "movements:delete-own-payout-account")))
        .andExpect(status().isNoContent());

    mvc.perform(pedir("10.00", null))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-006"));
    nadaCambio();
  }

  @Test
  @DisplayName("CA-MV-418 — una cuenta ajena, dada de baja o inexistente es 422, y nada cambia")
  void cuentaQueNoSirve() throws Exception {
    UUID otraPersona = persona("wdd-otra");
    UUID ajena = PayoutFixtures.cuenta(jdbc, otraPersona, banco, "9999000011", true);
    UUID dadaDeBaja = PayoutFixtures.cuenta(jdbc, persona, banco, "8888000011", false);
    jdbc.update("UPDATE payout_accounts SET deleted_at = now() WHERE id = ?", dadaDeBaja);

    for (UUID cuenta : new UUID[] {ajena, dadaDeBaja, UUID.randomUUID()}) {
      mvc.perform(pedir("10.00", cuenta))
          .andExpect(status().isUnprocessableEntity())
          .andExpect(jsonPath("$.errors[0].code").value("EX-007"));
    }
    nadaCambio();
  }

  @Test
  @DisplayName(
      "CA-MV-419 — una cuenta de entidad inactiva es 409 y nada cambia; reactivada, el mismo"
          + " retiro se pide")
  void entidadInactiva() throws Exception {
    jdbc.update("UPDATE payout_institutions SET is_active = false WHERE id = ?", banco);
    mvc.perform(pedir("10.00", principal))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-008"));
    nadaCambio();

    jdbc.update("UPDATE payout_institutions SET is_active = true WHERE id = ?", banco);
    mvc.perform(pedir("10.00", principal)).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("CA-MV-420 — quien no tiene documento recibe 409 y nada cambia")
  void sinDocumento() throws Exception {
    jdbc.update(
        "UPDATE users SET document_type_id = NULL, document_number = NULL WHERE id = ?", persona);
    mvc.perform(pedir("10.00", null))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-009"));
    nadaCambio();
  }

  @Test
  @DisplayName(
      "CA-MV-421, CA-MV-402 y CA-MV-407 — la copia no cambia al editar la cuenta, darla de baja,"
          + " renombrar la entidad o corregir el documento; y el retiro se aprueba igual")
  void laCopiaNoCambia() throws Exception {
    String documento = documento();
    UUID retiro = pedirYLeer("20.00", principal);
    String antes = copia(retiro);

    // CA-MV-402: editar la cuenta.
    mvc.perform(
            patch("/api/v1/movements/mine/payout-accounts/{id}", principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"number\":\"5555666677\",\"accountType\":\"CORRIENTE\"}")
                .with(
                    user(persona.toString())
                        .authorities(() -> "movements:update-own-payout-account")))
        .andExpect(status().isOk());
    jdbc.update("UPDATE payout_institutions SET name = 'Renombrado' WHERE id = ?", banco);
    jdbc.update("UPDATE users SET document_number = '424242' WHERE id = ?", persona);
    assertThat(copia(retiro)).isEqualTo(antes);

    // CA-MV-407: darla de baja, y el retiro se aprueba.
    mvc.perform(
            delete("/api/v1/movements/mine/payout-accounts/{id}", principal)
                .with(
                    user(persona.toString())
                        .authorities(() -> "movements:delete-own-payout-account")))
        .andExpect(status().isNoContent());
    assertThat(copia(retiro)).isEqualTo(antes).contains("1111222233").contains(documento);
    mvc.perform(
            post("/api/v1/movements/{id}/withdrawal-approval", retiro)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(
                    user(administrador.toString())
                        .authorities(() -> "movements:approve-withdrawal")))
        .andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject("SELECT status FROM movements WHERE id = ?", String.class, retiro))
        .isEqualTo("CONFIRMADA");
  }

  @Test
  @DisplayName("CA-MV-422 — un retiro sin destino, de antes del 01-10-2026, se aprueba y se niega")
  void retirosDeAntes() throws Exception {
    UUID uno = pedirYLeer("10.00", null);
    UUID otro = pedirYLeer("10.00", null);
    jdbc.update("DELETE FROM withdrawal_destinations WHERE movement_id IN (?, ?)", uno, otro);

    mvc.perform(
            post("/api/v1/movements/{id}/withdrawal-approval", uno)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(
                    user(administrador.toString())
                        .authorities(() -> "movements:approve-withdrawal")))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/movements/{id}/withdrawal-rejection", otro)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"No\"}")
                .with(
                    user(administrador.toString())
                        .authorities(() -> "movements:reject-withdrawal")))
        .andExpect(status().isOk());
    assertThat(saldo(jdbc, persona, "BILLETERA")).isEqualByComparingTo("90.00");
  }

  @Test
  @DisplayName("CA-MV-423 — la auditoría del retiro lleva el destino con el número enmascarado")
  void auditoria() throws Exception {
    UUID retiro = pedirYLeer("10.00", null);
    String cambios =
        jdbc.queryForObject(
            "SELECT CAST(changes AS text) FROM audit_change_log WHERE entity = 'movements'"
                + " AND entity_id = ? AND action = 'CREATE'",
            String.class,
            retiro);
    assertThat(cambios).contains("destination").contains("****2233").doesNotContain("1111222233");
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-007` · enmienda del 01-10-2026
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-424 — el detalle de un retiro trae su destino copiado, también tras editar la"
          + " cuenta, y el detalle propio trae el mismo")
  void detalleConDestino() throws Exception {
    UUID retiro = pedirYLeer("10.00", null);
    jdbc.update("UPDATE payout_accounts SET number = '7777' WHERE id = ?", principal);

    mvc.perform(get("/api/v1/movements/{id}", retiro).with(lectorDeAdministracion()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.withdrawalDestination.number").value("1111222233"))
        .andExpect(jsonPath("$.withdrawalDestination.payoutAccountId").value(principal.toString()))
        .andExpect(jsonPath("$.withdrawalDestination.holder.name").value("Nombre Apellido"));
    mvc.perform(
            get("/api/v1/movements/mine/{id}", retiro)
                .with(user(persona.toString()).authorities(() -> "movements:read-own")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.withdrawalDestination.number").value("1111222233"));
  }

  @Test
  @DisplayName("CA-MV-425 — el detalle de una venta, y el de un retiro sin copia, no traen destino")
  void detalleSinDestino() throws Exception {
    UUID retiro = pedirYLeer("10.00", null);
    jdbc.update("DELETE FROM withdrawal_destinations WHERE movement_id = ?", retiro);
    mvc.perform(get("/api/v1/movements/{id}", retiro).with(lectorDeAdministracion()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("RETIRO"))
        .andExpect(jsonPath("$.withdrawalDestination").doesNotExist());

    UUID venta = venta();
    mvc.perform(get("/api/v1/movements/{id}", venta).with(lectorDeAdministracion()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("VENTA"))
        .andExpect(jsonPath("$.withdrawalDestination").doesNotExist());
  }

  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder pedir(String importe, UUID cuenta) {
    String json =
        "{\"currencyId\":\""
            + USD
            + "\",\"amount\":"
            + importe
            + (cuenta == null ? "" : ",\"payoutAccountId\":\"" + cuenta + "\"")
            + "}";
    return post("/api/v1/movements/mine/withdrawals")
        .contentType(MediaType.APPLICATION_JSON)
        .content(json)
        .with(user(persona.toString()).authorities(() -> "movements:request-withdrawal"));
  }

  private UUID pedirYLeer(String importe, UUID cuenta) throws Exception {
    String cuerpo =
        mvc.perform(pedir(importe, cuenta))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(JsonPath.read(cuerpo, "$.movement.id"));
  }

  private RequestPostProcessor lectorDeAdministracion() {
    return user(administrador.toString()).authorities(() -> "movements:read-detail");
  }

  private UUID cuentaMovil(UUID entidad, String celular) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO payout_accounts (id, user_id, institution_id, number) VALUES (?, ?, ?, ?)",
        id,
        persona,
        entidad,
        celular);
    return id;
  }

  private String documento() {
    return jdbc.queryForObject(
        "SELECT document_number FROM users WHERE id = ?", String.class, persona);
  }

  private String copia(UUID retiro) {
    return jdbc.queryForObject(
        """
        SELECT payout_account_id || '|' || institution_code || '|' || institution_name || '|'
               || coalesce(account_type, '-') || '|' || number || '|' || holder_name || '|'
               || holder_document_type || '|' || holder_document_number
          FROM withdrawal_destinations WHERE movement_id = ?
        """,
        String.class,
        retiro);
  }

  /** Ni retiro, ni copia, ni asiento de solicitud; y la billetera, intacta. */
  private void nadaCambio() {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM movements m JOIN movement_types t"
                    + " ON t.id = m.movement_type_id WHERE t.code = 'RETIRO'",
                Integer.class))
        .isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM withdrawal_destinations", Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM movement_entries WHERE event = 'SOLICITUD'", Integer.class))
        .isZero();
    assertThat(saldo(jdbc, persona, "BILLETERA")).isEqualByComparingTo("100.00");
  }

  private UUID venta() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO movements (id, movement_type_id, type_status_id, user_id,
                               currency_id, code, status, total_amount, discount_amount,
                               payable_amount, occurred_at)
        SELECT ?, t.id, s.id, ?, CAST(? AS uuid), ?, 'PENDIENTE', 10.00, 0, 10.00, now()
          FROM movement_types t
          JOIN movement_type_statuses s ON s.movement_type_id = t.id AND s.code = 'VALIDADO'
         WHERE t.code = 'VENTA'
        """,
        id,
        persona,
        USD,
        "VEN-WDD-" + id.toString().substring(0, 8));
    return id;
  }

  private void limpiar() {
    LedgerFixtures.limpiar(jdbc);
    jdbc.update("DELETE FROM audit_change_log WHERE module = 'MV'");
    jdbc.update(
        "DELETE FROM user_products WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'wdd-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'wdd-%'");
  }

  private UUID persona(String username) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?, ?, ?, 'Nombre', 'Apellido', 'x', false, 'ACTIVO',
                (SELECT id FROM countries WHERE code = 'COL'))
        """,
        id,
        username,
        username + "@factech.co");
    darElSuelo(jdbc, id);
    return id;
  }
}
