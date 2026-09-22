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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * El listado de paquetes (`RF-PM-018`, `CA-PM-269` a `CA-PM-276`).
 *
 * <p>Las dos que pesan son <b>`CA-PM-273`</b> —ordenar por precio ordena por el precio calculado, y
 * un producto que cambia de precio mueve su paquete— y <b>`CA-PM-274`</b>: la página de uno y la de
 * veinte cuestan las mismas sentencias.
 */
@AutoConfigureMockMvc
class PackageListIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SessionFactory sessionFactory;

  private Statistics estadisticas;
  private Membresias membresias;
  private UUID barato;
  private UUID caro;
  private UUID vacio;
  private UUID retirado;
  private UUID botCaro;

  @BeforeEach
  void prepararCatalogo() {
    membresias = PackageTestSupport.limpiarCatalogoYSembrarMembresias(jdbc);
    PackageTestSupport.limpiarMonedasDePrueba(jdbc);
    UUID botA = PackageTestSupport.bot(jdbc, "BOT_A", "10.00");
    UUID botB = PackageTestSupport.bot(jdbc, "BOT_B", "20.00");
    botCaro = PackageTestSupport.bot(jdbc, "BOT_CARO", "100.00");
    UUID oro =
        PackageTestSupport.upgrade(
            jdbc, "UPGRADE_ORO", "299.00", membresias.beca(), membresias.oro());

    // Se insertan en este orden; el orden por omisión los devuelve al revés.
    barato = PackageTestSupport.paquete(jdbc, "BARATO", "Dos bots.", "ACTIVO", "TIENDA");
    PackageTestSupport.asociar(jdbc, barato, botA, "FIJO", "0");
    PackageTestSupport.asociar(jdbc, barato, botB, "PORCENTAJE", "50"); // 10 + 10 = 20

    caro = PackageTestSupport.paquete(jdbc, "CARO", "Oro con bot.", "ACTIVO", "AMBOS");
    PackageTestSupport.asociar(jdbc, caro, oro, "PORCENTAJE", "10"); // 269.10
    PackageTestSupport.asociar(jdbc, caro, botCaro, "FIJO", "20.00"); // 80 → 349.10

    vacio = PackageTestSupport.paquete(jdbc, "VACIO", null, "INACTIVO", "HOTLINK");

    retirado = PackageTestSupport.paquete(jdbc, "RETIRADO", "Se fue.", "ACTIVO", "TIENDA");
    jdbc.update("UPDATE product_packages SET deleted_at = now() WHERE id = ?", retirado);

    estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);
    estadisticas.clear();
  }

  @AfterEach
  void vaciar() {
    PackageTestSupport.limpiarPaquetes(jdbc);
    jdbc.update("DELETE FROM products");
    PackageTestSupport.limpiarMonedasDePrueba(jdbc);
  }

  @Test
  @DisplayName(
      "`CA-PM-269` y `CA-PM-272` — la página trae itemCount, los tres importes y offerable por fila, por fecha de alta descendente")
  void paginaConLaCuentaHecha() throws Exception {
    mvc.perform(listar(""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(3)))
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.sort").value("createdAt,desc"))
        .andExpect(jsonPath("$.content[0].code").value("VACIO"))
        .andExpect(jsonPath("$.content[0].itemCount").value(0))
        .andExpect(jsonPath("$.content[0].price").value(0.00))
        .andExpect(jsonPath("$.content[0].offerable").value(false))
        .andExpect(jsonPath("$.content[0].exchange").value(nullValue()))
        .andExpect(jsonPath("$.content[1].code").value("CARO"))
        .andExpect(jsonPath("$.content[1].itemCount").value(2))
        .andExpect(jsonPath("$.content[1].listPrice").value(399.00))
        .andExpect(jsonPath("$.content[1].price").value(349.10))
        .andExpect(jsonPath("$.content[1].savings").value(49.90))
        .andExpect(jsonPath("$.content[1].offerable").value(true))
        .andExpect(jsonPath("$.content[1].currency.code").value("USD"))
        .andExpect(jsonPath("$.content[2].code").value("BARATO"))
        .andExpect(jsonPath("$.content[2].price").value(20.00))
        .andExpect(jsonPath("$.content[2].deletedAt").doesNotExist())
        // Sin las líneas: eso es el detalle.
        .andExpect(jsonPath("$.content[0].items").doesNotExist());
  }

  @Test
  @DisplayName("`CA-PM-269` — los importes de la fila CUADRAN con los del detalle")
  void cuadraConElDetalle() throws Exception {
    String fila = mvc.perform(listar("?q=caro")).andReturn().getResponse().getContentAsString();
    String detalle =
        mvc.perform(get("/api/v1/packages/" + caro).with(lector()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    for (String campo : new String[] {"listPrice", "price", "savings"}) {
      Object enFila = com.jayway.jsonpath.JsonPath.read(fila, "$.content[0]." + campo);
      Object enDetalle = com.jayway.jsonpath.JsonPath.read(detalle, "$." + campo);
      assertThat(enFila).as(campo).isEqualTo(enDetalle);
    }
  }

  @Test
  @DisplayName(
      "`CA-PM-270` — excluye los retirados salvo includeDeleted=true, y entonces traen deletedAt")
  void retirados() throws Exception {
    mvc.perform(listar("?includeDeleted=true"))
        .andExpect(jsonPath("$.content", hasSize(4)))
        .andExpect(jsonPath("$.content[0].code").value("RETIRADO"))
        .andExpect(jsonPath("$.content[0].deletedAt").exists())
        .andExpect(jsonPath("$.content[0].offerable").value(false));
  }

  @Test
  @DisplayName(
      "`CA-PM-271` — los filtros por estado, alcance, moneda y nombre acotan y se combinan")
  void filtros() throws Exception {
    mvc.perform(listar("?status=inactivo"))
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].code").value("VACIO"));
    mvc.perform(listar("?scope=TIENDA"))
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].code").value("BARATO"));
    mvc.perform(get("/api/v1/packages").queryParam("q", "ÁRO").with(lector()))
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].code").value("CARO"));
    mvc.perform(listar("?currencyId=" + PackageTestSupport.USD + "&status=ACTIVO&scope=AMBOS"))
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].code").value("CARO"));
    mvc.perform(listar("?currencyId=" + UUID.randomUUID()))
        .andExpect(jsonPath("$.content", hasSize(0)))
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName("`CA-PM-272` — name y price se admiten; otro campo es 400")
  void ordenes() throws Exception {
    mvc.perform(listar("?sort=name"))
        .andExpect(jsonPath("$.content[0].code").value("BARATO"))
        .andExpect(jsonPath("$.sort").value("name,asc"));
    mvc.perform(listar("?sort=price,desc"))
        .andExpect(jsonPath("$.content[0].code").value("CARO"))
        .andExpect(jsonPath("$.content[1].code").value("BARATO"))
        .andExpect(jsonPath("$.content[2].code").value("VACIO"));
    mvc.perform(listar("?sort=itemCount"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-005"));
  }

  @Test
  @DisplayName(
      "`CA-PM-273` — el orden por precio es por el precio CALCULADO: el producto que cambia de precio mueve su paquete")
  void elOrdenPorPrecioSigueAlProducto() throws Exception {
    mvc.perform(listar("?sort=price"))
        .andExpect(jsonPath("$.content[0].code").value("VACIO"))
        .andExpect(jsonPath("$.content[1].code").value("BARATO"))
        .andExpect(jsonPath("$.content[2].code").value("CARO"));
    // CARO baja a 269.10 + 0 = 269.10; BARATO sube a 10 + 500 = 510 (50 % de 1000).
    jdbc.update("UPDATE products SET price = 20.00 WHERE id = ?", botCaro);
    jdbc.update("UPDATE products SET price = 1000.00 WHERE code = 'BOT_B'");
    mvc.perform(listar("?sort=price"))
        .andExpect(jsonPath("$.content[0].code").value("VACIO"))
        .andExpect(jsonPath("$.content[1].code").value("CARO"))
        .andExpect(jsonPath("$.content[1].price").value(269.10))
        .andExpect(jsonPath("$.content[2].code").value("BARATO"))
        .andExpect(jsonPath("$.content[2].price").value(510.00));
  }

  @Test
  @DisplayName(
      "`CA-PM-274` y `CA-PM-275` — la página de uno y la de veinte cuestan lo mismo, y exchange se resuelve por página")
  void lasSentenciasNoCrecen() throws Exception {
    estadisticas.clear();
    mvc.perform(listar("?size=1")).andExpect(status().isOk());
    long deUno = estadisticas.getPrepareStatementCount();

    estadisticas.clear();
    mvc.perform(listar("?size=20&includeDeleted=true"))
        .andExpect(jsonPath("$.content", hasSize(4)));
    long deVeinte = estadisticas.getPrepareStatementCount();

    // Cuatro: la página, sus filas, el total y la moneda de casa. La tasa no
    // se pide porque toda la página está en ella.
    assertThat(deUno).isEqualTo(4);
    assertThat(deVeinte).isEqualTo(deUno);
  }

  @Test
  @DisplayName(
      "`CA-PM-276` — los filtros inválidos van juntos con 400, y sin packages:read es 403 aunque porte products:read")
  void filtrosInvalidosYPermiso() throws Exception {
    mvc.perform(listar("?status=X&scope=Y&sort=Z&size=0"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.errors[*].field")
                .value(org.hamcrest.Matchers.hasItems("status", "scope", "sort", "size")));
    mvc.perform(listar("?currencyId=no-es-uuid")).andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/v1/packages")
                .with(
                    user(UUID.randomUUID().toString())
                        .authorities(() -> "products:read", () -> "products:list")))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName(
      "`CA-PM-376` — cada fila trae validFrom y validTo; el vencido ayer y el que empieza mañana salen offerable false y siguen ACTIVO")
  void vigenciaPorFila() throws Exception {
    LocalDate hoy = LocalDate.now(ZoneOffset.UTC);
    mvc.perform(listar("?sort=name"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].code").value("BARATO"))
        .andExpect(jsonPath("$.content[0].validFrom").value(hoy.toString()))
        .andExpect(jsonPath("$.content[0].validTo").value(nullValue()))
        .andExpect(jsonPath("$.content[0].offerable").value(true))
        .andExpect(jsonPath("$.content[1].code").value("CARO"))
        .andExpect(jsonPath("$.content[1].offerable").value(true));

    jdbc.update(
        "UPDATE product_packages SET valid_from = ?, valid_to = ? WHERE id = ?",
        Date.valueOf(hoy.minusDays(10)),
        Date.valueOf(hoy.minusDays(1)),
        barato);
    jdbc.update(
        "UPDATE product_packages SET valid_from = ? WHERE id = ?",
        Date.valueOf(hoy.plusDays(1)),
        caro);
    mvc.perform(listar("?sort=name"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].status").value("ACTIVO"))
        .andExpect(jsonPath("$.content[0].offerable").value(false))
        .andExpect(jsonPath("$.content[0].validTo").value(hoy.minusDays(1).toString()))
        .andExpect(jsonPath("$.content[1].status").value("ACTIVO"))
        .andExpect(jsonPath("$.content[1].offerable").value(false))
        .andExpect(jsonPath("$.content[1].validFrom").value(hoy.plusDays(1).toString()));
  }

  private MockHttpServletRequestBuilder listar(String query) {
    return get("/api/v1/packages" + query).with(lector());
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor lector() {
    return user(UUID.randomUUID().toString())
        .authorities(() -> "packages:read", () -> "packages:list");
  }
}
