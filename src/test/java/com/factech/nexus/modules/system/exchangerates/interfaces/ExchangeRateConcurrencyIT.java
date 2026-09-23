package com.factech.nexus.modules.system.exchangerates.interfaces;

import static com.factech.nexus.testing.ConcurrencyHarness.runTogether;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
 * `RF-SP-047` · `T-12` — dos altas simultáneas del mismo par y el mismo periodo.
 *
 * <p><b>Lo que se prueba es que la garantía NO es la comprobación previa.</b> El caso de uso
 * pregunta si hay solapamiento antes de guardar, y esa pregunta <b>no vale nada</b> con dos
 * peticiones a la vez: las dos la hacen sobre una base sin tasas, las dos creen que pueden, y lo
 * que las separa es la restricción del esquema. Una prueba secuencial <b>no puede distinguir</b>
 * las dos situaciones — pasa igual si el {@code EXCLUDE} no existiera.
 *
 * <p><b>Y la mitad que importa es el ESTADO del rechazo</b>: sin la traducción por nombre de
 * restricción, la perdedora subiría como violación de integridad y respondería {@code 500} — un
 * error del servidor para una regla de negocio que el sistema conoce y sabe explicar.
 */
@AutoConfigureMockMvc
class ExchangeRateConcurrencyIT extends IntegrationTestBase {

  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private String cop;

  /**
   * Deja las dos tablas como las encontró, y no solo como las necesita.
   *
   * <p><b>Limpiar solo al empezar no basta</b>: al terminar quedan tasas y monedas que no son la de
   * casa, y la siguiente clase que haga {@code DELETE FROM currencies WHERE is_default = false}
   * —`CurrenciesIT`— revienta con {@code fk_exchange_rates_target} antes de ejecutar una sola
   * prueba. El fallo sale en otra suite, con un mensaje que no menciona a esta.
   *
   * <p>Es la misma lección que {@code PackageTestSupport} tiene escrita para las monedas de prueba,
   * y la que las suites de equipos siguen desde `CA-SP-683`: se limpia al empezar <b>y</b> al
   * terminar. Lo destapó el CI de `RF-SP-070` el 23-09-2026, cuando las clases nuevas del submódulo
   * de Equipos cambiaron el orden de ejecución en Linux.
   */
  @AfterEach
  void dejarLoQueEncontro() {
    // Las tasas primero: apuntan a las monedas que se borran a continuación.
    jdbc.update("DELETE FROM exchange_rates");
    jdbc.update("DELETE FROM currencies WHERE is_default = false");
  }

  @BeforeEach
  void dejarElCatalogoLimpio() {
    jdbc.update("DELETE FROM exchange_rates");
    jdbc.update("DELETE FROM currencies WHERE is_default = false");
    cop = insertarMoneda("COP", "Peso colombiano");
  }

  @Test
  @DisplayName("dos altas simultáneas del mismo par: una 201 y la otra 409, nunca 500")
  void dosAltasSimultaneas() {
    List<Outcome<Integer>> resultados = runTogether(2, indice -> estadoDe(alta(indice)));

    assertThat(resultados).as("alguna petición reventó").allMatch(Outcome::succeeded);

    // Las dos vigencias se solapan enteras; los precios distintos solo sirven
    // para saber cuál ganó.
    assertThat(resultados).extracting(Outcome::value).containsExactlyInAnyOrder(201, 409);

    assertThat(jdbc.queryForObject("SELECT count(*) FROM exchange_rates", Integer.class)).isOne();
  }

  private MockHttpServletRequestBuilder alta(int indice) {
    return post("/api/v1/exchange-rates")
        .with(user(UUID.randomUUID().toString()).authorities(() -> "exchange-rates:create"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {"sourceCurrencyId":"%s","targetCurrencyId":"%s","price":%s,
             "validFrom":"2026-09-01","validTo":"2026-09-30"}
            """
                .formatted(USD, cop, indice == 0 ? "4150.00" : "4200.00"));
  }

  private int estadoDe(MockHttpServletRequestBuilder peticion) throws Exception {
    return mvc.perform(peticion).andReturn().getResponse().getStatus();
  }

  private String insertarMoneda(String code, String name) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO currencies (id, code, name, symbol, decimal_places, is_default, is_active)
        VALUES (?, ?, ?, '#', 2, false, true)
        """,
        id,
        code,
        name);
    return id.toString();
  }
}
