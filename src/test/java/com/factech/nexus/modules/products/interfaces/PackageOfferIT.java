package com.factech.nexus.modules.products.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.products.interfaces.PackageTestSupport.Membresias;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Los paquetes en la oferta propia (`RF-PM-007` v0.13.0, `CA-PM-335` a `CA-PM-338`; construida por
 * `RF-PM-019` · `T-11`/`T-12`).
 *
 * <p>La que pesa es <b>`CA-PM-337`</b>: el paquete se ofrece a quien tiene el ORIGEN de sus
 * upgrades, y el de solo bots a todo el mundo, incluido quien no tiene membresía.
 */
@AutoConfigureMockMvc
class PackageOfferIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SessionFactory sessionFactory;

  private Statistics estadisticas;
  private Membresias m;
  private UUID enPlatino;
  private UUID enOro;
  private UUID sinMembresia;
  private UUID desdePlatino;
  private UUID desdeOro;
  private UUID soloBots;
  private UUID botA;

  @BeforeEach
  void sembrar() {
    limpiarPersonas();
    m = PackageTestSupport.limpiarCatalogoYSembrarMembresias(jdbc);
    PackageTestSupport.limpiarMonedasDePrueba(jdbc);

    UUID upPlatinoOro =
        PackageTestSupport.upgrade(jdbc, "UP_PLATINO_ORO", "100.00", m.platino(), m.oro());
    UUID upOroOro = PackageTestSupport.upgrade(jdbc, "UP_ORO_ORO", "50.00", m.oro(), m.oro());
    botA = PackageTestSupport.bot(jdbc, "BOT_A", "10.00");
    UUID botB = PackageTestSupport.bot(jdbc, "BOT_B", "20.00");
    jdbc.update("UPDATE products SET purchase_price = 5.00 WHERE id = ?", botA);

    desdePlatino =
        PackageTestSupport.paquete(jdbc, "DESDE_PLATINO", "Oro con bot.", "ACTIVO", "TIENDA");
    PackageTestSupport.asociar(jdbc, desdePlatino, upPlatinoOro, "PORCENTAJE", "10");
    PackageTestSupport.asociar(jdbc, desdePlatino, botA, "FIJO", "1.00");

    desdeOro =
        PackageTestSupport.paquete(jdbc, "DESDE_ORO", "Renovar oro con bot.", "ACTIVO", "AMBOS");
    PackageTestSupport.asociar(jdbc, desdeOro, upOroOro, "FIJO", "0");
    PackageTestSupport.asociar(jdbc, desdeOro, botB, "FIJO", "0");

    soloBots = PackageTestSupport.paquete(jdbc, "SOLO_BOTS", "Dos bots.", "ACTIVO", "TIENDA");
    PackageTestSupport.asociar(jdbc, soloBots, botA, "PORCENTAJE", "50");
    PackageTestSupport.asociar(jdbc, soloBots, botB, "PORCENTAJE", "50");

    enPlatino = persona("oferta-pk-platino", m.platino());
    enOro = persona("oferta-pk-oro", m.oro());
    sinMembresia = persona("oferta-pk-sin", null);

    estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);
    estadisticas.clear();
  }

  @AfterEach
  void vaciar() {
    PackageTestSupport.limpiarPaquetes(jdbc);
    jdbc.update("DELETE FROM products");
    PackageTestSupport.limpiarMonedasDePrueba(jdbc);
    limpiarPersonas();
  }

  @Test
  @DisplayName(
      "`CA-PM-335` — packages envuelta y presente, cada producto en la forma de la oferta, la cuenta cuadra y sin purchasePrice")
  void packagesConLaCuentaHecha() throws Exception {
    String cuerpo =
        oferta(enPlatino)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.packages.content", hasSize(2)))
            .andExpect(jsonPath("$.packages.content[0].code").value("DESDE_PLATINO"))
            .andExpect(jsonPath("$.packages.content[0].currency.code").value("USD"))
            .andExpect(jsonPath("$.packages.content[0].items", hasSize(2)))
            .andExpect(
                jsonPath("$.packages.content[0].items[0].product.code").value("UP_PLATINO_ORO"))
            .andExpect(
                jsonPath("$.packages.content[0].items[0].product.targetMembership.code")
                    .value("ORO"))
            .andExpect(jsonPath("$.packages.content[0].items[0].product.rating.count").value(0))
            .andExpect(jsonPath("$.packages.content[0].items[0].discount.value").value(10.00))
            .andExpect(jsonPath("$.packages.content[0].items[0].priceInPackage").value(90.00))
            .andExpect(jsonPath("$.packages.content[0].items[1].priceInPackage").value(9.00))
            .andExpect(jsonPath("$.packages.content[0].listPrice").value(110.00))
            .andExpect(jsonPath("$.packages.content[0].price").value(99.00))
            .andExpect(jsonPath("$.packages.content[0].savings").value(11.00))
            .andExpect(jsonPath("$.packages.content[1].code").value("SOLO_BOTS"))
            .andExpect(jsonPath("$.packages.content[1].price").value(15.00))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(cuerpo).doesNotContain("purchasePrice");

    // Presente aunque vacía: sin paquetes ofrecibles sigue viajando.
    jdbc.update("UPDATE product_packages SET status = 'INACTIVO'");
    String vacia =
        oferta(enPlatino)
            .andExpect(jsonPath("$.packages.content", hasSize(0)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(vacia).contains("\"packages\":{\"content\":[]}");
  }

  @Test
  @DisplayName(
      "`CA-PM-336` — no devuelve el inactivo, el retirado, el de menos de dos ni el que tiene un producto inactivo o retirado, y lo vuelve a devolver; el alcance del paquete no filtra")
  void loQueNoSePuedeOfrecerNoAparece() throws Exception {
    oferta(sinMembresia).andExpect(jsonPath("$.packages.content", hasSize(1)));

    jdbc.update("UPDATE products SET status = 'INACTIVO' WHERE id = ?", botA);
    oferta(sinMembresia).andExpect(jsonPath("$.packages.content", hasSize(0)));
    jdbc.update("UPDATE products SET status = 'ACTIVO' WHERE id = ?", botA);
    oferta(sinMembresia).andExpect(jsonPath("$.packages.content", hasSize(1)));

    jdbc.update("UPDATE products SET deleted_at = now() WHERE id = ?", botA);
    oferta(sinMembresia).andExpect(jsonPath("$.packages.content", hasSize(0)));
    jdbc.update("UPDATE products SET deleted_at = NULL WHERE id = ?", botA);

    jdbc.update("UPDATE product_packages SET status = 'INACTIVO' WHERE id = ?", soloBots);
    oferta(sinMembresia).andExpect(jsonPath("$.packages.content", hasSize(0)));
    jdbc.update(
        "UPDATE product_packages SET status = 'ACTIVO', deleted_at = now() WHERE id = ?", soloBots);
    oferta(sinMembresia).andExpect(jsonPath("$.packages.content", hasSize(0)));
    jdbc.update("UPDATE product_packages SET deleted_at = NULL WHERE id = ?", soloBots);

    jdbc.update(
        "DELETE FROM product_package_items WHERE package_id = ? AND product_id = ?",
        soloBots,
        botA);
    oferta(sinMembresia).andExpect(jsonPath("$.packages.content", hasSize(0)));
    PackageTestSupport.asociar(jdbc, soloBots, botA, "PORCENTAJE", "50");

    // El alcance SÍ filtra el paquete: HOTLINK y NINGUNO no entran en la oferta;
    // TIENDA y AMBOS sí.
    jdbc.update("UPDATE product_packages SET scope = 'HOTLINK' WHERE id = ?", soloBots);
    oferta(sinMembresia).andExpect(jsonPath("$.packages.content", hasSize(0)));
    jdbc.update("UPDATE product_packages SET scope = 'AMBOS' WHERE id = ?", soloBots);
    oferta(sinMembresia).andExpect(jsonPath("$.packages.content", hasSize(1)));
    // Y el de sus productos no: un bot de alcance HOTLINK dentro no lo saca.
    jdbc.update("UPDATE products SET scope = 'HOTLINK' WHERE id = ?", botA);
    oferta(sinMembresia).andExpect(jsonPath("$.packages.content", hasSize(1)));
  }

  @Test
  @DisplayName(
      "`CA-PM-337` — a quien está en PLATINO el que sale de PLATINO y no el de ORO; el de solo bots a todo el mundo, incluido quien no tiene membresía")
  void elOrigenDecideAQuienSeOfrece() throws Exception {
    oferta(enPlatino)
        .andExpect(
            jsonPath("$.packages.content[*].code")
                .value(org.hamcrest.Matchers.contains("DESDE_PLATINO", "SOLO_BOTS")));
    oferta(enOro)
        .andExpect(
            jsonPath("$.packages.content[*].code")
                .value(org.hamcrest.Matchers.contains("DESDE_ORO", "SOLO_BOTS")));
    oferta(sinMembresia)
        .andExpect(
            jsonPath("$.packages.content[*].code")
                .value(org.hamcrest.Matchers.contains("SOLO_BOTS")));
  }

  @Test
  @DisplayName(
      "`CA-PM-338` — con tres paquetes en dos monedas, las sentencias suben exactamente en una respecto de la oferta sin paquetes")
  void lasSentenciasNoCrecenConLosPaquetes() throws Exception {
    // La oferta sin paquetes ofrecibles: todos inactivos.
    jdbc.update("UPDATE product_packages SET status = 'INACTIVO'");
    estadisticas.clear();
    oferta(enPlatino).andExpect(status().isOk());
    long sinPaquetes = estadisticas.getPrepareStatementCount();

    // Los tres, y uno de ellos en otra moneda con tasa: la tasa va en la misma
    // sentencia que las de los productos.
    jdbc.update("UPDATE product_packages SET status = 'ACTIVO'");
    UUID cop = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO currencies (id, code, name, symbol, decimal_places, is_default, is_active)"
            + " VALUES (?, 'COP', 'Peso', '$', 0, false, true)",
        cop);
    jdbc.update(
        "INSERT INTO exchange_rates (id, source_currency_id, target_currency_id, price,"
            + " valid_from, valid_to, is_active)"
            + " VALUES (?, ?, CAST(? AS uuid), 0.00025, CURRENT_DATE - 1, NULL, true)",
        UUID.randomUUID(),
        cop,
        PackageTestSupport.USD);
    UUID enPesos =
        PackageTestSupport.paquete(jdbc, "EN_PESOS", "Bots en pesos.", "ACTIVO", "TIENDA");
    jdbc.update("UPDATE product_packages SET currency_id = ? WHERE id = ?", cop, enPesos);
    UUID c1 = PackageTestSupport.bot(jdbc, "BOT_COP_A", "10.00");
    UUID c2 = PackageTestSupport.bot(jdbc, "BOT_COP_B", "20.00");
    jdbc.update(
        "UPDATE products SET currency_id = ?, price = 40000 WHERE id IN (?, ?)", cop, c1, c2);
    PackageTestSupport.asociar(jdbc, enPesos, c1, "FIJO", "0");
    PackageTestSupport.asociar(jdbc, enPesos, c2, "FIJO", "0");

    estadisticas.clear();
    oferta(enPlatino)
        .andExpect(jsonPath("$.packages.content", hasSize(3)))
        .andExpect(jsonPath("$.packages.content[2].code").value("EN_PESOS"))
        .andExpect(jsonPath("$.packages.content[2].exchange.currency.code").value("USD"))
        .andExpect(jsonPath("$.packages.content[2].exchange.amount").value(20.00));
    long conPaquetes = estadisticas.getPrepareStatementCount();

    // Una sentencia más que sin paquetes ofrecibles —la que trae los paquetes
    // con sus líneas siempre se paga; la de tasas la habría pagado igual una
    // oferta con productos en dos monedas—, y no una por paquete ni por moneda.
    assertThat(conPaquetes - sinPaquetes).isLessThanOrEqualTo(1);
  }

  @Test
  @DisplayName(
      "`CA-PM-378` — cada paquete trae validFrom y validTo; el que empieza mañana o terminó ayer no aparece, el que termina hoy sí")
  void fueraDeLaVigenciaNoAparece() throws Exception {
    LocalDate hoy = LocalDate.now(ZoneOffset.UTC);
    oferta(sinMembresia)
        .andExpect(jsonPath("$.packages.content", hasSize(1)))
        .andExpect(jsonPath("$.packages.content[0].validFrom").value(hoy.toString()))
        .andExpect(jsonPath("$.packages.content[0].validTo").value(nullValue()));

    jdbc.update(
        "UPDATE product_packages SET valid_to = ? WHERE id = ?", Date.valueOf(hoy), soloBots);
    oferta(sinMembresia)
        .andExpect(jsonPath("$.packages.content", hasSize(1)))
        .andExpect(jsonPath("$.packages.content[0].validTo").value(hoy.toString()));

    jdbc.update(
        "UPDATE product_packages SET valid_from = ?, valid_to = ? WHERE id = ?",
        Date.valueOf(hoy.minusDays(10)),
        Date.valueOf(hoy.minusDays(1)),
        soloBots);
    oferta(sinMembresia).andExpect(jsonPath("$.packages.content", hasSize(0)));

    jdbc.update(
        "UPDATE product_packages SET valid_from = ?, valid_to = NULL WHERE id = ?",
        Date.valueOf(hoy.plusDays(1)),
        soloBots);
    oferta(sinMembresia).andExpect(jsonPath("$.packages.content", hasSize(0)));
    // Y el estado no se movió: sigue ACTIVO, oculto por las fechas.
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM product_packages WHERE id = ?", String.class, soloBots))
        .isEqualTo("ACTIVO");
  }

  // ---------------------------------------------------------------------------

  private ResultActions oferta(UUID quien) throws Exception {
    return mvc.perform(
        get("/api/v1/products/available")
            .with(user(quien.toString()).authorities(() -> "products:sale")));
  }

  private UUID persona(String username, UUID membresia) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (CAST(? AS uuid), ?, ?, 'Ana', 'Ruiz', 'x', false, 'ACTIVO', (SELECT id FROM countries WHERE code = 'COL'))
        """,
        id.toString(),
        username,
        username + "@nexus.test");
    if (membresia != null) {
      jdbc.update(
          """
          INSERT INTO user_products (id, user_id, membership_id, started_at, ends_at,
                                        created_at, updated_at)
          VALUES (gen_random_uuid(), CAST(? AS uuid), CAST(? AS uuid), now() - interval '30 days',
                  NULL, now(), now())
          """,
          id.toString(),
          membresia.toString());
    }
    return id;
  }

  private void limpiarPersonas() {
    jdbc.update(
        "DELETE FROM user_products WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'oferta-pk-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'oferta-pk-%'");
  }
}
