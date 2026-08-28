package com.factech.nexus.modules.commissions.interfaces;

import static com.factech.nexus.testing.ConcurrencyHarness.runTogether;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.testing.ConcurrencyHarness.Outcome;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * `RN-CM-006` bajo concurrencia (`RF-CM-001` · `T-16`).
 *
 * <p><b>Es la prueba que verifica dónde vive la regla.</b> Las de {@code CommissionRatesIT}
 * comprueban que el solapamiento se rechaza, y pasarían igual si la comprobación fuera un {@code
 * SELECT} previo en el caso de uso. Esta no: dos peticiones simultáneas leerían las dos que no hay
 * solape y las dos insertarían.
 *
 * <p>Es el defecto que `RN-SP-018` tuvo y que se corrigió el 26-08-2026, escrito aquí antes de
 * volver a tenerlo.
 */
@AutoConfigureMockMvc
class CommissionRateConcurrencyIT extends IntegrationTestBase {

  private static final String VENDEDOR = "01a02a33-4c00-7005-9c4f-5e7ad1000005";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void preparar() {
    jdbc.update("DELETE FROM commission_rates");
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    jdbc.update("DELETE FROM commission_rates");
    jdbc.update("DELETE FROM audit_change_log WHERE module = 'CM'");
  }

  @Test
  @DisplayName("dos altas simultáneas del mismo caso y periodo: una queda, la otra recibe 409")
  void dosAltasSimultaneasDelMismoPeriodo() throws Exception {
    List<Outcome<Integer>> resultados =
        runTogether(2, indice -> estadoDe("10.0" + indice, "2026-01-01", "2026-12-31"));

    // Ninguna puede salir como 500: el fallo de integridad tiene que llegar
    // traducido, o el cliente no sabe que su problema es un solapamiento.
    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);

    assertThat(cuantasTarifas()).as("las dos altas quedaron, o no quedó ninguna").isEqualTo(1);

    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 201).count())
        .as("exactamente una debía crearse")
        .isEqualTo(1);

    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 409).count())
        .as("y la otra debía recibir el conflicto traducido")
        .isEqualTo(1);
  }

  @Test
  @DisplayName("dos altas simultáneas de periodos que NO se tocan: las dos quedan")
  void dosAltasSimultaneasConsecutivas() throws Exception {
    List<Outcome<Integer>> resultados =
        runTogether(
            2,
            indice ->
                indice == 0
                    ? estadoDe("10.00", "2026-01-01", "2026-06-30")
                    : estadoDe("12.00", "2026-07-01", "2026-12-31"));

    assertThat(resultados).allMatch(r -> r.succeeded() && r.value() == 201);
    assertThat(cuantasTarifas()).isEqualTo(2);
  }

  private int estadoDe(String porcentaje, String desde, String hasta) {
    try {
      return mvc.perform(
              post("/api/v1/commission-rates")
                  .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:create"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"roleId\":\""
                          + VENDEDOR
                          + "\",\"percentage\":"
                          + porcentaje
                          + ",\"validFrom\":\""
                          + desde
                          + "\",\"validTo\":\""
                          + hasta
                          + "\"}"))
          .andReturn()
          .getResponse()
          .getStatus();
    } catch (Exception fallo) {
      throw new IllegalStateException(fallo);
    }
  }

  private long cuantasTarifas() {
    return jdbc.queryForObject("SELECT count(*) FROM commission_rates", Long.class);
  }
}
