package com.factech.nexus.modules.movements.interfaces;

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
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
 * Las cuentas de cobro: registrar (`RF-MV-035`), mis cuentas (`RF-MV-036`), editar (`RF-MV-037`),
 * dar de baja (`RF-MV-038`) y la consulta de administración (`RF-MV-039`).
 */
@AutoConfigureMockMvc
class PayoutAccountsIT extends IntegrationTestBase {

  private static final String REGISTRAR = "movements:create-own-payout-account";
  private static final String MIS = "movements:list-own-payout-accounts";
  private static final String EDITAR = "movements:update-own-payout-account";
  private static final String BAJA = "movements:delete-own-payout-account";
  private static final String DE_OTRO = "movements:read-user-payout-accounts";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SessionFactory sessionFactory;

  private UUID persona;
  private UUID otra;
  private UUID administrador;
  private UUID banco;
  private UUID movil;

  @BeforeEach
  void sembrar() {
    limpiar();
    persona = persona("pa-persona", true);
    otra = persona("pa-otra", true);
    administrador = persona("pa-admin", true);
    banco = PayoutFixtures.entidad(jdbc, "BANCO", true);
    movil = PayoutFixtures.entidad(jdbc, "BILLETERA_MOVIL", true);
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-035`
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-378 y CA-MV-381 — en un banco se registra a nombre de quien la pide, con su nombre"
          + " y su documento, y el número se guarda solo con dígitos")
  void registraEnUnBanco() throws Exception {
    String documento = documentoDe(persona);
    mvc.perform(registrar(persona, banco, "AHORROS", "1234-5678 90", null))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.institution.id").value(banco.toString()))
        .andExpect(jsonPath("$.institution.kind").value("BANCO"))
        .andExpect(jsonPath("$.accountType").value("AHORROS"))
        .andExpect(jsonPath("$.number").value("1234567890"))
        .andExpect(jsonPath("$.holder.name").value("Nombre Apellido"))
        .andExpect(jsonPath("$.holder.documentType").value("CC"))
        .andExpect(jsonPath("$.holder.documentNumber").value(documento))
        .andExpect(jsonPath("$.usable").value(true));
    assertThat(
            jdbc.queryForObject(
                "SELECT user_id FROM payout_accounts WHERE number = '1234567890'", UUID.class))
        .isEqualTo(persona);
  }

  @Test
  @DisplayName(
      "CA-MV-379 — en una billetera móvil se registra con el celular y sin tipo de cuenta;"
          + " enviarle un tipo es 400")
  void registraEnUnaBilletera() throws Exception {
    mvc.perform(registrar(persona, movil, "AHORROS", "3001234567", null))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("EX-004"));
    mvc.perform(registrar(persona, movil, null, "300 123 4567", null))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.accountType").doesNotExist())
        .andExpect(jsonPath("$.number").value("3001234567"));
  }

  @Test
  @DisplayName(
      "CA-MV-380 — un banco sin tipo, o un número con la longitud de otro tipo, es 400 y nada"
          + " cambia")
  void formaPorTipo() throws Exception {
    mvc.perform(registrar(persona, banco, null, "1234567890", null))
        .andExpect(status().isBadRequest());
    mvc.perform(registrar(persona, movil, null, "12345", null)).andExpect(status().isBadRequest());
    mvc.perform(registrar(persona, banco, "CORRIENTE", "123", null))
        .andExpect(status().isBadRequest());
    assertThat(cuentasDe(persona)).isZero();
  }

  @Test
  @DisplayName(
      "CA-MV-382 y CA-MV-383 — la primera es la principal sin pedirlo, la segunda no, y pedir"
          + " otra principal desmarca la anterior")
  void laPrincipal() throws Exception {
    UUID primera = registrarYLeer(persona, banco, "AHORROS", "11112222", null);
    UUID segunda = registrarYLeer(persona, banco, "AHORROS", "33334444", null);
    assertThat(principales(persona)).containsExactly(primera);
    assertThat(esPrincipal(segunda)).isFalse();

    UUID tercera = registrarYLeer(persona, movil, null, "3009998877", true);
    assertThat(principales(persona)).containsExactly(tercera);
  }

  @Test
  @DisplayName(
      "CA-MV-384 — la misma entidad y el mismo número otra vez, también con otros espacios, es"
          + " 409 y no registra nada")
  void repetida() throws Exception {
    registrarYLeer(persona, banco, "AHORROS", "55556666", null);
    mvc.perform(registrar(persona, banco, "CORRIENTE", "5555-6666", null))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-006"));
    assertThat(cuentasDe(persona)).isEqualTo(1);
    // Otra persona con el mismo número sí puede (`spec.md` §13).
    mvc.perform(registrar(otra, banco, "AHORROS", "55556666", null))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("CA-MV-385 — entidad inactiva o de otro país: 409; inexistente: 422. Nada cambia")
  void entidades() throws Exception {
    UUID apagada = PayoutFixtures.entidad(jdbc, "BANCO", false);
    UUID extranjera = entidadDeOtroPais();
    mvc.perform(registrar(persona, apagada, "AHORROS", "12341234", null))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));
    mvc.perform(registrar(persona, extranjera, "AHORROS", "12341234", null))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));
    mvc.perform(registrar(persona, UUID.randomUUID(), "AHORROS", "12341234", null))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));
    assertThat(cuentasDe(persona)).isZero();
  }

  @Test
  @DisplayName("CA-MV-386 — una persona sin documento recibe 409 y no registra nada")
  void sinDocumento() throws Exception {
    UUID sinDoc = persona("pa-sindoc", false);
    mvc.perform(registrar(sinDoc, banco, "AHORROS", "12341234", null))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-005"));
    assertThat(cuentasDe(sinDoc)).isZero();
  }

  @Test
  @DisplayName("CA-MV-387 — dos registros simultáneos de la primera cuenta dejan una principal")
  void carreraDeLaPrimera() throws Exception {
    CountDownLatch salida = new CountDownLatch(1);
    ExecutorService hilos = Executors.newFixedThreadPool(2);
    try {
      List<Future<Integer>> respuestas = new ArrayList<>();
      String[] numeros = {"70001111", "70002222"};
      for (String numero : numeros) {
        respuestas.add(
            hilos.submit(
                () -> {
                  salida.await();
                  return mvc.perform(registrar(persona, banco, "AHORROS", numero, null))
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      }
      salida.countDown();
      for (Future<Integer> r : respuestas) {
        assertThat(r.get()).isEqualTo(201);
      }
    } finally {
      hilos.shutdownNow();
    }
    assertThat(cuentasDe(persona)).isEqualTo(2);
    assertThat(principales(persona)).hasSize(1);
  }

  @Test
  @DisplayName(
      "CA-MV-388 y CA-MV-389 — sin el permiso 403, sin token 401; queda auditada y el número no"
          + " aparece completo en la auditoría")
  void permisoYAuditoria() throws Exception {
    mvc.perform(
            post("/api/v1/movements/mine/payout-accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(banco, "AHORROS", "12345678", null))
                .with(user(persona.toString()).authorities(() -> MIS)))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/movements/mine/payout-accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(banco, "AHORROS", "12345678", null)))
        .andExpect(status().isUnauthorized());

    UUID id = registrarYLeer(persona, banco, "AHORROS", "9988776655", null);
    String cambios =
        jdbc.queryForObject(
            "SELECT CAST(changes AS text) FROM audit_change_log WHERE entity = 'payout_accounts'"
                + " AND entity_id = ? AND action = 'CREATE' AND actor_id = ?",
            String.class,
            id,
            persona);
    assertThat(cambios).contains("****6655").doesNotContain("9988776655");
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-036`
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-390 y CA-MV-391 — solo las vivas de quien pregunta, la principal primero y después"
          + " de la más reciente a la más antigua")
  void misCuentas() throws Exception {
    UUID primera = registrarYLeer(persona, banco, "AHORROS", "10000001", null);
    UUID segunda = registrarYLeer(persona, banco, "AHORROS", "10000002", null);
    UUID tercera = registrarYLeer(persona, banco, "AHORROS", "10000003", null);
    UUID baja = registrarYLeer(persona, banco, "AHORROS", "10000004", null);
    mvc.perform(
            delete("/api/v1/movements/mine/payout-accounts/{id}", baja).with(con(persona, BAJA)))
        .andExpect(status().isNoContent());
    registrarYLeer(otra, banco, "AHORROS", "10000005", null);

    String cuerpo =
        mvc.perform(get("/api/v1/movements/mine/payout-accounts").with(con(persona, MIS)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> ids = JsonPath.read(cuerpo, "$[*].id");
    assertThat(ids).containsExactly(primera.toString(), tercera.toString(), segunda.toString());
  }

  @Test
  @DisplayName(
      "CA-MV-392 y CA-MV-393 — la de una entidad desactivada sale marcada como no usable, y el"
          + " titular refleja el documento corregido")
  void usableYTitular() throws Exception {
    UUID apagable = PayoutFixtures.entidad(jdbc, "BANCO", true);
    UUID enApagada = registrarYLeer(persona, apagable, "AHORROS", "20000001", null);
    UUID viva = registrarYLeer(persona, banco, "AHORROS", "20000002", null);
    jdbc.update("UPDATE payout_institutions SET is_active = false WHERE id = ?", apagable);
    jdbc.update("UPDATE users SET document_number = '777888999' WHERE id = ?", persona);

    mvc.perform(get("/api/v1/movements/mine/payout-accounts").with(con(persona, MIS)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == '%s')].usable".formatted(enApagada)).value(false))
        .andExpect(jsonPath("$[?(@.id == '%s')].usable".formatted(viva)).value(true))
        .andExpect(jsonPath("$[0].holder.documentNumber").value("777888999"));
  }

  @Test
  @DisplayName(
      "CA-MV-394 — sin cuentas es una lista vacía; sin el permiso 403, sin token 401; y son dos"
          + " sentencias tenga las cuentas que tenga")
  void vaciaPermisoYSentencias() throws Exception {
    mvc.perform(get("/api/v1/movements/mine/payout-accounts").with(con(persona, MIS)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    mvc.perform(get("/api/v1/movements/mine/payout-accounts").with(con(persona, REGISTRAR)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/movements/mine/payout-accounts")).andExpect(status().isUnauthorized());

    for (int i = 0; i < 4; i++) {
      registrarYLeer(persona, banco, "AHORROS", "3000000" + i, null);
    }
    Statistics estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);
    estadisticas.clear();
    mvc.perform(get("/api/v1/movements/mine/payout-accounts").with(con(persona, MIS)))
        .andExpect(jsonPath("$.length()").value(4));
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(2);
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-037`
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-395 y CA-MV-396 — corrige el número y el tipo en un banco; un tipo enviado a una"
          + " billetera es 400")
  void corrige() throws Exception {
    UUID cuenta = registrarYLeer(persona, banco, "AHORROS", "40000001", null);
    mvc.perform(editar(cuenta, "{\"number\":\"4000-0009\",\"accountType\":\"CORRIENTE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.number").value("40000009"))
        .andExpect(jsonPath("$.accountType").value("CORRIENTE"));

    UUID enMovil = registrarYLeer(persona, movil, null, "3001112222", null);
    mvc.perform(editar(enMovil, "{\"accountType\":\"AHORROS\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("EX-004"));
  }

  @Test
  @DisplayName(
      "CA-MV-397 y CA-MV-398 — marcar otra como principal desmarca la anterior; «principal:"
          + " false» es 400 y nada cambia")
  void cambiaLaPrincipal() throws Exception {
    UUID primera = registrarYLeer(persona, banco, "AHORROS", "50000001", null);
    UUID segunda = registrarYLeer(persona, banco, "AHORROS", "50000002", null);
    mvc.perform(editar(segunda, "{\"principal\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.principal").value(true));
    assertThat(principales(persona)).containsExactly(segunda);

    mvc.perform(editar(segunda, "{\"principal\":false}")).andExpect(status().isBadRequest());
    assertThat(principales(persona)).containsExactly(segunda);
    assertThat(esPrincipal(primera)).isFalse();
  }

  @Test
  @DisplayName(
      "CA-MV-399 a CA-MV-401 — un número que repite otra suya es 409; una ajena, dada de baja o"
          + " inexistente, 404; una de entidad inactiva, 409. Nada cambia")
  void edicionesRechazadas() throws Exception {
    UUID una = registrarYLeer(persona, banco, "AHORROS", "60000001", null);
    registrarYLeer(persona, banco, "AHORROS", "60000002", null);
    mvc.perform(editar(una, "{\"number\":\"60000002\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-005"));

    UUID ajena = registrarYLeer(otra, banco, "AHORROS", "60000003", null);
    mvc.perform(editar(ajena, "{\"number\":\"60000004\"}")).andExpect(status().isNotFound());
    UUID dadaDeBaja = registrarYLeer(persona, banco, "AHORROS", "60000005", null);
    mvc.perform(
            delete("/api/v1/movements/mine/payout-accounts/{id}", dadaDeBaja)
                .with(con(persona, BAJA)))
        .andExpect(status().isNoContent());
    mvc.perform(editar(dadaDeBaja, "{\"number\":\"60000006\"}")).andExpect(status().isNotFound());
    mvc.perform(editar(UUID.randomUUID(), "{\"number\":\"60000007\"}"))
        .andExpect(status().isNotFound());

    UUID apagable = PayoutFixtures.entidad(jdbc, "BANCO", true);
    UUID enApagada = registrarYLeer(persona, apagable, "AHORROS", "60000008", null);
    jdbc.update("UPDATE payout_institutions SET is_active = false WHERE id = ?", apagable);
    mvc.perform(editar(enApagada, "{\"principal\":true}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));

    assertThat(numero(una)).isEqualTo("60000001");
    assertThat(numero(ajena)).isEqualTo("60000003");
    assertThat(esPrincipal(enApagada)).isFalse();
  }

  @Test
  @DisplayName(
      "CA-MV-403 — lo mismo no escribe ni audita; un cambio se audita con el anterior y el número"
          + " enmascarado; sin el permiso 403 y sin token 401")
  void sinCambiosAuditoriaYPermiso() throws Exception {
    UUID cuenta = registrarYLeer(persona, banco, "AHORROS", "80000001", null);
    int antes = auditoriasDe(cuenta);
    mvc.perform(editar(cuenta, "{\"number\":\"80000001\",\"principal\":true}"))
        .andExpect(status().isOk());
    assertThat(auditoriasDe(cuenta)).isEqualTo(antes);

    mvc.perform(editar(cuenta, "{\"number\":\"80000002\"}")).andExpect(status().isOk());
    String cambios =
        jdbc.queryForObject(
            "SELECT CAST(changes AS text) FROM audit_change_log WHERE entity_id = ?"
                + " AND action = 'UPDATE'",
            String.class,
            cuenta);
    assertThat(cambios).contains("****0001").contains("****0002").doesNotContain("80000002");

    mvc.perform(
            patch("/api/v1/movements/mine/payout-accounts/{id}", cuenta)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"number\":\"80000003\"}")
                .with(con(persona, MIS)))
        .andExpect(status().isForbidden());
    mvc.perform(
            patch("/api/v1/movements/mine/payout-accounts/{id}", cuenta)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"number\":\"80000003\"}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(editar(cuenta, "{\"institutionId\":\"" + movil + "\"}"))
        .andExpect(status().isBadRequest());
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-038`
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-404 y CA-MV-405 — dar de baja otra no cambia la principal; dar de baja la principal"
          + " la pasa a la más antigua")
  void bajas() throws Exception {
    UUID primera = registrarYLeer(persona, banco, "AHORROS", "90000001", null);
    UUID segunda = registrarYLeer(persona, banco, "AHORROS", "90000002", null);
    UUID tercera = registrarYLeer(persona, banco, "AHORROS", "90000003", null);
    UUID cuarta = registrarYLeer(persona, banco, "AHORROS", "90000004", true);

    mvc.perform(darDeBaja(persona, tercera)).andExpect(status().isNoContent());
    assertThat(principales(persona)).containsExactly(cuarta);
    assertThat(vivasDe(persona)).doesNotContain(tercera);

    mvc.perform(darDeBaja(persona, cuarta)).andExpect(status().isNoContent());
    assertThat(principales(persona)).containsExactly(primera);
    assertThat(vivasDe(persona)).containsExactlyInAnyOrder(primera, segunda);
  }

  @Test
  @DisplayName("CA-MV-406 — dar de baja la última deja a la persona sin cuentas y sin principal")
  void laUltima() throws Exception {
    UUID unica = registrarYLeer(persona, banco, "AHORROS", "91000001", null);
    mvc.perform(darDeBaja(persona, unica)).andExpect(status().isNoContent());
    assertThat(vivasDe(persona)).isEmpty();
    assertThat(principales(persona)).isEmpty();
    // Y el retiro, sin cuenta, no se puede pedir: lo afirma `WithdrawalDestinationIT`
    // (`CA-MV-417`), con la billetera llena.
  }

  @Test
  @DisplayName(
      "CA-MV-408 y CA-MV-409 — una ajena, ya dada de baja o inexistente es 404; queda auditada;"
          + " sin el permiso 403 y sin token 401")
  void bajasRechazadasYAuditoria() throws Exception {
    UUID ajena = registrarYLeer(otra, banco, "AHORROS", "92000001", null);
    mvc.perform(darDeBaja(persona, ajena)).andExpect(status().isNotFound());
    assertThat(vivasDe(otra)).containsExactly(ajena);

    UUID mia = registrarYLeer(persona, banco, "AHORROS", "92000002", null);
    mvc.perform(delete("/api/v1/movements/mine/payout-accounts/{id}", mia).with(con(persona, MIS)))
        .andExpect(status().isForbidden());
    mvc.perform(delete("/api/v1/movements/mine/payout-accounts/{id}", mia))
        .andExpect(status().isUnauthorized());

    mvc.perform(darDeBaja(persona, mia)).andExpect(status().isNoContent());
    mvc.perform(darDeBaja(persona, mia)).andExpect(status().isNotFound());
    mvc.perform(darDeBaja(persona, UUID.randomUUID())).andExpect(status().isNotFound());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_deletion_log WHERE entity = 'payout_accounts'"
                    + " AND entity_id = ? AND deletion_type = 'LOGICAL' AND actor_id = ?",
                Integer.class,
                mia,
                persona))
        .isEqualTo(1);
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-039`
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-410 y CA-MV-411 — las vivas de la persona indicada y ninguna de otra; con el filtro,"
          + " también las dadas de baja, al final y con su fecha")
  void deUnaPersona() throws Exception {
    UUID viva = registrarYLeer(persona, banco, "AHORROS", "93000001", null);
    UUID baja = registrarYLeer(persona, banco, "AHORROS", "93000002", null);
    mvc.perform(darDeBaja(persona, baja)).andExpect(status().isNoContent());
    registrarYLeer(otra, banco, "AHORROS", "93000003", null);

    mvc.perform(
            get("/api/v1/movements/users/{userId}/payout-accounts", persona)
                .with(con(administrador, DE_OTRO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(viva.toString()))
        .andExpect(jsonPath("$[0].number").value("93000001"))
        .andExpect(jsonPath("$[0].deletedAt").doesNotExist());

    mvc.perform(
            get("/api/v1/movements/users/{userId}/payout-accounts", persona)
                .param("includeDeleted", "true")
                .with(con(administrador, DE_OTRO)))
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].id").value(viva.toString()))
        .andExpect(jsonPath("$[1].id").value(baja.toString()))
        .andExpect(jsonPath("$[1].deletedAt").exists())
        .andExpect(jsonPath("$[1].usable").value(false));
  }

  @Test
  @DisplayName(
      "CA-MV-412 a CA-MV-414 — sin cuentas es vacía, la persona inexistente 404; el permiso propio"
          + " no abre esta ruta, ni para uno mismo; sin token 401")
  void deUnaPersonaBordes() throws Exception {
    mvc.perform(
            get("/api/v1/movements/users/{userId}/payout-accounts", otra)
                .with(con(administrador, DE_OTRO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    mvc.perform(
            get("/api/v1/movements/users/{userId}/payout-accounts", UUID.randomUUID())
                .with(con(administrador, DE_OTRO)))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/v1/movements/users/{userId}/payout-accounts", persona)
                .with(con(persona, MIS)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/movements/users/{userId}/payout-accounts", persona))
        .andExpect(status().isUnauthorized());
  }

  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder registrar(
      UUID quien, UUID entidad, String tipo, String numero, Boolean principal) {
    return post("/api/v1/movements/mine/payout-accounts")
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(entidad, tipo, numero, principal))
        .with(con(quien, REGISTRAR));
  }

  private UUID registrarYLeer(
      UUID quien, UUID entidad, String tipo, String numero, Boolean principal) throws Exception {
    String cuerpo =
        mvc.perform(registrar(quien, entidad, tipo, numero, principal))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(JsonPath.read(cuerpo, "$.id"));
  }

  private static String cuerpo(UUID entidad, String tipo, String numero, Boolean principal) {
    StringBuilder json = new StringBuilder("{\"institutionId\":\"" + entidad + "\"");
    json.append(",\"number\":\"").append(numero).append('"');
    if (tipo != null) {
      json.append(",\"accountType\":\"").append(tipo).append('"');
    }
    if (principal != null) {
      json.append(",\"principal\":").append(principal);
    }
    return json.append('}').toString();
  }

  private MockHttpServletRequestBuilder editar(UUID cuenta, String cuerpo) {
    return patch("/api/v1/movements/mine/payout-accounts/{id}", cuenta)
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo)
        .with(con(persona, EDITAR));
  }

  private MockHttpServletRequestBuilder darDeBaja(UUID quien, UUID cuenta) {
    return delete("/api/v1/movements/mine/payout-accounts/{id}", cuenta).with(con(quien, BAJA));
  }

  private static RequestPostProcessor con(UUID quien, String permiso) {
    return user(quien.toString()).authorities(() -> permiso);
  }

  private int cuentasDe(UUID quien) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM payout_accounts WHERE user_id = ?", Integer.class, quien);
  }

  private List<UUID> principales(UUID quien) {
    return jdbc.queryForList(
        "SELECT id FROM payout_accounts WHERE user_id = ? AND is_principal", UUID.class, quien);
  }

  private List<UUID> vivasDe(UUID quien) {
    return jdbc.queryForList(
        "SELECT id FROM payout_accounts WHERE user_id = ? AND deleted_at IS NULL",
        UUID.class,
        quien);
  }

  private boolean esPrincipal(UUID cuenta) {
    return jdbc.queryForObject(
        "SELECT is_principal FROM payout_accounts WHERE id = ?", Boolean.class, cuenta);
  }

  private String numero(UUID cuenta) {
    return jdbc.queryForObject(
        "SELECT number FROM payout_accounts WHERE id = ?", String.class, cuenta);
  }

  private int auditoriasDe(UUID id) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM audit_change_log WHERE entity_id = ?", Integer.class, id);
  }

  private String documentoDe(UUID quien) {
    return jdbc.queryForObject(
        "SELECT document_number FROM users WHERE id = ?", String.class, quien);
  }

  private UUID entidadDeOtroPais() {
    UUID pais = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO countries (id, code, name, is_active) VALUES (?, 'ZPB', 'País ZPB', true)",
        pais);
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO payout_institutions (id, code, name, kind, country_id)"
            + " VALUES (?, 'ZEXTRANJERA', 'Extranjera', 'BANCO', ?)",
        id,
        pais);
    return id;
  }

  private void limpiar() {
    LedgerFixtures.limpiar(jdbc);
    jdbc.update("DELETE FROM audit_change_log WHERE entity = 'payout_accounts'");
    jdbc.update("DELETE FROM audit_deletion_log WHERE entity = 'payout_accounts'");
    jdbc.update(
        "DELETE FROM user_products WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'pa-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'pa-%'");
    jdbc.update("DELETE FROM countries WHERE code = 'ZPB'");
  }

  private UUID persona(String username, boolean conDocumento) {
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
    if (conDocumento) {
      PayoutFixtures.documento(jdbc, id);
    }
    return id;
  }
}
