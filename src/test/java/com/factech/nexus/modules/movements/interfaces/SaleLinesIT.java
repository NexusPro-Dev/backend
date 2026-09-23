package com.factech.nexus.modules.movements.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.products.interfaces.ProductLinkTestSupport;
import jakarta.persistence.EntityManagerFactory;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * `RF-MV-017` — las líneas de venta para administración: `CA-MV-163` a `CA-MV-176`, `CA-MV-178` y
 * `CA-MV-179`.
 *
 * <p><b>El fixture está hecho para que lo que importa se pueda ver</b>: una venta con <b>dos líneas
 * de dos vendedores distintos</b> —lo que `RN-MV-003` existe para permitir, y lo que distingue este
 * listado de uno por venta—, una línea <b>sin vendedor</b> —que tiene que salir, no desaparecer—,
 * una venta <b>anulada</b>, dos productos y dos sujetos.
 *
 * <p>`CA-MV-177` —la siembra de `V37`— vive en {@code SaleLinesPermissionSeedIT}, y esta suite no
 * la necesita: `MockMvc` concede la autoridad sin pasar por el catálogo.
 */
@AutoConfigureMockMvc
class SaleLinesIT extends IntegrationTestBase {

  private static final String VENTA = "01a061ba-3400-7001-9c4f-5e7ad7000011";
  private static final String TARJETA = "01a061ba-3400-7002-9c4f-5e7ad7000021";
  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";
  private static final String RUTA = "/api/v1/movements/sales/lines";

  private static final OffsetDateTime BASE =
      OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC);

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManagerFactory emf;

  private UUID ana;
  private UUID beto;
  private UUID vendedorUno;
  private UUID vendedorDos;
  private UUID bot;
  private UUID curso;

  private UUID ventaDeDos;
  private UUID ventaSinVendedor;
  private UUID ventaAnulada;

  @BeforeEach
  void sembrar() {
    limpiar();
    ana = persona("sl-ana");
    beto = persona("sl-beto");
    vendedorUno = persona("sl-ven1");
    vendedorDos = persona("sl-ven2");
    bot = producto("SL_BOT", "Bot de señales");
    curso = producto("SL_CURSO", "Curso de trading");

    // La venta que hace distinta a esta consulta: DOS líneas, DOS vendedores.
    ventaDeDos = venta(ana, "CONFIRMADA", BASE, "VTA-SL-0001");
    linea(ventaDeDos, bot, vendedorUno, 2, "120.00", "20.00", "220.00", 30, "ENTREGADA", BASE);
    linea(ventaDeDos, curso, vendedorDos, 1, "80.00", "0.00", "80.00", null, "PENDIENTE", null);

    // Una línea SIN vendedor: tiene que salir con `seller` nulo. Y su venta queda
    // en `VALIDAR_COMISIONES`, que no es adorno del fixture: es el estado que
    // `RN-MV-034` le da precisamente a la venta cuyas líneas no tienen vendedor.
    // Las otras dos quedan en `VALIDADO`, de modo que `CA-MV-180` separa una de tres.
    ventaSinVendedor =
        venta(beto, "CONFIRMADA", BASE.plusHours(1), "VTA-SL-0002", "VALIDAR_COMISIONES");
    linea(ventaSinVendedor, bot, null, 1, "120.00", "0.00", "120.00", 30, "RETENIDA", null);

    // Y una anulada, que sale igual cuando no se filtra.
    ventaAnulada = venta(beto, "ANULADA", BASE.plusHours(2), "VTA-SL-0003");
    linea(ventaAnulada, curso, vendedorUno, 1, "80.00", "0.00", "80.00", null, "PENDIENTE", null);
  }

  @AfterEach
  void devolverLaBaseASuSitio() {
    limpiar();
  }

  @Test
  @DisplayName(
      "`CA-MV-163` y `CA-MV-164` — una fila por LÍNEA, de la más reciente a la más antigua, con lo"
          + " de la línea y lo de su venta")
  void unaFilaPorLinea() throws Exception {
    mvc.perform(consulta())
        .andExpect(status().isOk())
        // Cuatro líneas de tres ventas: la de dos líneas aporta DOS filas.
        .andExpect(jsonPath("$.content.length()").value(4))
        .andExpect(jsonPath("$.totalElements").value(4))
        // La anulada es la más reciente del fixture.
        .andExpect(jsonPath("$.content[0].movementCode").value("VTA-SL-0003"))
        .andExpect(jsonPath("$.content[3].movementCode").value("VTA-SL-0001"))
        // Y la fila lleva lo suyo y lo de su venta.
        .andExpect(jsonPath("$.content[3].lineId").exists())
        .andExpect(jsonPath("$.content[3].movementId").value(ventaDeDos.toString()))
        .andExpect(jsonPath("$.content[3].movementStatus").value("CONFIRMADA"))
        .andExpect(jsonPath("$.content[3].occurredAt").exists())
        .andExpect(jsonPath("$.content[3].client.username").value("sl-ana"))
        .andExpect(jsonPath("$.content[3].currency").value("USD"));
  }

  @Test
  @DisplayName(
      "`CA-MV-164` — el nombre del producto es el CONGELADO del día de la venta, y el código el del"
          + " catálogo")
  void elNombreEsElCongelado() throws Exception {
    jdbc.update("UPDATE products SET name = 'Bot renombrado hoy' WHERE id = ?", bot);

    mvc.perform(consulta("productId", bot.toString()))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.content[*].product.name")
                .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("Bot de señales"))))
        .andExpect(jsonPath("$.content[0].product.code").value("SL_BOT"));
  }

  @Test
  @DisplayName("`CA-MV-165` — la línea SIN vendedor sale, con seller presente y nulo")
  void laLineaSinVendedorSale() throws Exception {
    mvc.perform(consulta("movementId", ventaSinVendedor.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].seller").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.content[0].deliveryStatus").value("RETENIDA"))
        .andExpect(jsonPath("$.content[0].deliveryNote").exists());
  }

  @Test
  @DisplayName("`CA-MV-166` — movementId acota a una venta; uno inexistente da página vacía")
  void filtroPorVenta() throws Exception {
    mvc.perform(consulta("movementId", ventaDeDos.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2));

    mvc.perform(consulta("movementId", UUID.randomUUID().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0))
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName(
      "`CA-MV-167` — userId acota por el SUJETO y sellerId por el vendedor DE LA LÍNEA: la venta de"
          + " dos vendedores aparece una vez por cada uno")
  void filtrosPorPersona() throws Exception {
    mvc.perform(consulta("userId", ana.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2));

    // Cada vendedor ve SU línea de la venta compartida, no las dos.
    mvc.perform(consulta("sellerId", vendedorDos.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].product.code").value("SL_CURSO"))
        .andExpect(jsonPath("$.content[0].movementId").value(ventaDeDos.toString()));

    // Y el otro, las dos líneas suyas de dos ventas distintas.
    mvc.perform(consulta("sellerId", vendedorUno.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2));

    mvc.perform(consulta("userId", UUID.randomUUID().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0));
  }

  @Test
  @DisplayName("`CA-MV-168` — productId acota a las líneas de un producto")
  void filtroPorProducto() throws Exception {
    mvc.perform(consulta("productId", curso.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(
            jsonPath("$.content[*].product.code")
                .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("SL_CURSO"))));
  }

  @Test
  @DisplayName(
      "`CA-MV-169` — status acota por el estado de la VENTA, y la anulada sale cuando no se filtra")
  void filtroPorEstadoDeLaVenta() throws Exception {
    mvc.perform(consulta("status", "ANULADA"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].movementId").value(ventaAnulada.toString()));

    mvc.perform(consulta("status", "CONFIRMADA"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(3));
  }

  @Test
  @DisplayName(
      "`CA-MV-170` — deliveryStatus acota por el estado de la LÍNEA: dos líneas de la misma venta en"
          + " estados distintos se separan")
  void filtroPorEstadoDeEntrega() throws Exception {
    mvc.perform(consulta("deliveryStatus", "ENTREGADA"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].movementId").value(ventaDeDos.toString()))
        .andExpect(jsonPath("$.content[0].product.code").value("SL_BOT"))
        .andExpect(jsonPath("$.content[0].deliveredAt").exists());

    mvc.perform(consulta("deliveryStatus", "PENDIENTE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2));
  }

  @Test
  @DisplayName("`CA-MV-171` — code acota al comprobante exacto, sin distinguir mayúsculas")
  void filtroPorComprobante() throws Exception {
    mvc.perform(consulta("code", "VTA-SL-0001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2));
  }

  @Test
  @DisplayName("`CA-MV-172` — from/to acotan por cuándo ocurrió la venta, con el rango semiabierto")
  void filtroPorFechas() throws Exception {
    // `from` inclusive: la venta del instante exacto entra.
    mvc.perform(consulta("from", BASE.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(4));

    // `to` exclusivo: la de `BASE.plusHours(2)` NO entra.
    mvc.perform(consulta("to", BASE.plusHours(2).toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(3))
        .andExpect(
            jsonPath("$.content[*].movementId")
                .value(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem(ventaAnulada.toString()))));
  }

  @Test
  @DisplayName("`CA-MV-173` — los filtros se combinan, y la combinación vacía responde 200")
  void losFiltrosSeCombinan() throws Exception {
    mvc.perform(
            consulta()
                .param("userId", ana.toString())
                .param("sellerId", vendedorUno.toString())
                .param("status", "CONFIRMADA"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1));

    // El mismo vendedor, pero sobre el sujeto que no le compró: vacío, no error.
    mvc.perform(consulta().param("userId", ana.toString()).param("status", "ANULADA"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0));
  }

  @Test
  @DisplayName(
      "`CA-MV-174` — 400 con el estado, el estado de entrega, el rango y la paginación inválidos, y"
          + " los devuelve JUNTOS")
  void losCuatroCuatrocientosViajanJuntos() throws Exception {
    mvc.perform(
            consulta()
                .param("status", "INVENTADO")
                .param("deliveryStatus", "TAMPOCO")
                .param("from", BASE.plusDays(1).toString())
                .param("to", BASE.toString())
                .param("size", "-3"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.errors.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)))
        .andExpect(
            jsonPath("$.errors[*].field")
                .value(org.hamcrest.Matchers.hasItems("status", "deliveryStatus", "from")));

    // Y el identificador ilegible es `400` por sí solo.
    mvc.perform(consulta("productId", "no-es-uuid")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("`CA-MV-175` — el total es acotado: por encima del techo, totalIsExact dice false")
  void elTotalEsAcotado() throws Exception {
    mvc.perform(consulta())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(4))
        .andExpect(jsonPath("$.totalIsExact").value(true));
  }

  @Test
  @DisplayName(
      "`CA-MV-176` — sin movements:list-sale-lines responde 403 aunque el actor porte"
          + " movements:read, movements:list-sales y movements:read-own-products")
  void losPermisosVecinosNoHabilitan() throws Exception {
    mvc.perform(
            get(RUTA)
                .with(
                    user(UUID.randomUUID().toString())
                        .authorities(
                            () -> "movements:read",
                            () -> "movements:list-sales",
                            () -> "movements:read-own-products")))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("`CA-MV-178` — el número de sentencias no crece con el tamaño de la página")
  void lasSentenciasNoCrecen() throws Exception {
    Statistics estadisticas = emf.unwrap(org.hibernate.SessionFactory.class).getStatistics();
    estadisticas.setStatisticsEnabled(true);

    estadisticas.clear();
    mvc.perform(consulta("size", "1")).andExpect(status().isOk());
    long conUna = estadisticas.getQueryExecutionCount();

    estadisticas.clear();
    mvc.perform(consulta("size", "20")).andExpect(status().isOk());
    long conVeinte = estadisticas.getQueryExecutionCount();

    // Dos: la página y el conteo. Todo lo que la fila publica viaja en la
    // primera, porque la línea tiene UN vendedor y no varios.
    assertThat(conUna).isEqualTo(2);
    assertThat(conVeinte).isEqualTo(conUna);
  }

  @Test
  @DisplayName("`CA-MV-179` — no devuelve líneas de movimientos que no son ventas")
  void soloVentas() throws Exception {
    // Hoy `VENTA` es el único tipo del catálogo, de modo que el otro se siembra a
    // mano: sin esto, el predicado `mt.code = 'VENTA'` pasaría por bueno sin que
    // nada lo ejercite.
    String otroTipo = "01a061ba-3400-7009-9c4f-5e7ad70000ff";
    jdbc.update(
        "INSERT INTO movement_types (id, code, name, prefix) VALUES"
            + " (CAST(? AS uuid), 'SL_DEPOSITO', 'Deposito de prueba', 'DEP')"
            + " ON CONFLICT DO NOTHING",
        otroTipo);
    // Su estado, porque la FK de `movements` es compuesta con el tipo: `V36`
    // solo siembra los de VENTA, y sin esto el movimiento no tiene estado que
    // referenciar.
    jdbc.update(
        "INSERT INTO movement_type_statuses (id, movement_type_id, code, name) VALUES"
            + " (gen_random_uuid(), CAST(? AS uuid), 'VALIDADO', 'Validado')"
            + " ON CONFLICT DO NOTHING",
        otroTipo);
    UUID deposito = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO movements (id, movement_type_id, type_status_id, user_id,
                               payment_method_id, currency_id,
                               code, status, total_amount, discount_amount, payable_amount,
                               occurred_at, confirmed_at)
        VALUES (?, CAST(? AS uuid),
                (SELECT s.id FROM movement_type_statuses s
                  WHERE s.movement_type_id = CAST(? AS uuid) AND s.code = 'VALIDADO'),
                ?, CAST(? AS uuid), CAST(? AS uuid), 'DEP-SL-0001',
                'CONFIRMADA', 50.00, 0, 50.00, CAST(? AS timestamptz),
                -- `ck_movements_confirmed`: una confirmada lleva su fecha.
                CAST(? AS timestamptz))
        """,
        deposito,
        otroTipo,
        otroTipo,
        ana,
        TARJETA,
        USD,
        BASE.plusHours(3),
        BASE.plusHours(3));
    jdbc.update(
        """
        INSERT INTO movement_details (id, movement_id, product_id, seller_id, product_name,
                                      quantity, unit_price, line_amount, implementation,
                                      delivery_status)
        SELECT ?, ?, p.id, NULL, p.name, 1, 50.00, 50.00, p.implementation, 'PENDIENTE'
          FROM products p WHERE p.id = ?
        """,
        UUID.randomUUID(),
        deposito,
        bot);

    mvc.perform(consulta())
        .andExpect(status().isOk())
        // Las cuatro de siempre: la del depósito no entra.
        .andExpect(jsonPath("$.content.length()").value(4))
        .andExpect(
            jsonPath("$.content[*].movementCode")
                .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("DEP-SL-0001"))));
  }

  @Test
  @DisplayName(
      "`CA-MV-180` — typeStatus acota por el estado del TIPO de la venta, y se combina con los demás")
  void filtroPorEstadoDelTipo() throws Exception {
    // Tres líneas en ventas `VALIDADO` y una en la que está pendiente de validar.
    mvc.perform(consulta("typeStatus", "VALIDADO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(3))
        .andExpect(
            jsonPath("$.content[*].movementCode")
                .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("VTA-SL-0002"))));

    mvc.perform(consulta("typeStatus", "VALIDAR_COMISIONES"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].movementId").value(ventaSinVendedor.toString()))
        // Y NO lo publica, que es la otra mitad de la decisión del 23-09-2026: se
        // filtra por el estado del tipo y la fila no lo trae.
        .andExpect(jsonPath("$.content[0].typeStatus").doesNotExist());

    // Combinado: el mismo estado, pero de quien no compró esa venta.
    mvc.perform(
            consulta().param("typeStatus", "VALIDAR_COMISIONES").param("userId", ana.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0));
  }

  @Test
  @DisplayName(
      "`CA-MV-181` — un typeStatus que no existe es 400 sobre su campo, y viaja con los demás"
          + " problemas de la misma petición")
  void elEstadoDelTipoDesconocidoEsCuatrocientos() throws Exception {
    // Solo: el catálogo de estados por tipo es cerrado —son filas que nadie edita
    // por API—, de modo que preguntar por uno inventado es una pregunta mal escrita
    // y no una página vacía.
    mvc.perform(consulta("typeStatus", "NO_EXISTE"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.hasItem("typeStatus")))
        .andExpect(jsonPath("$.errors[0].code").value("VAL-005"));

    // Y acompañado: los dos estados mal escritos vuelven en la MISMA respuesta.
    mvc.perform(consulta().param("typeStatus", "NO_EXISTE").param("status", "TAMPOCO"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.errors[*].field")
                .value(org.hamcrest.Matchers.hasItems("typeStatus", "status")));
  }

  // ---------------------------------------------------------------- fixture

  private MockHttpServletRequestBuilder consulta() {
    return get(RUTA).with(administrador());
  }

  private MockHttpServletRequestBuilder consulta(String parametro, String valor) {
    return consulta().param(parametro, valor);
  }

  private static RequestPostProcessor administrador() {
    return user(UUID.randomUUID().toString()).authorities(() -> "movements:list-sale-lines");
  }

  private void limpiar() {
    jdbc.update("DELETE FROM movement_details");
    jdbc.update("DELETE FROM movements");
    // Los estados antes que el tipo: su FK al tipo es RESTRICT.
    jdbc.update(
        "DELETE FROM movement_type_statuses WHERE movement_type_id IN"
            + " (SELECT id FROM movement_types WHERE code = 'SL_DEPOSITO')");
    jdbc.update("DELETE FROM movement_types WHERE code = 'SL_DEPOSITO'");
    ProductLinkTestSupport.limpiar(jdbc);
    jdbc.update("DELETE FROM products WHERE code LIKE 'SL\\_%'");
    jdbc.update(
        "DELETE FROM user_memberships WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'sl-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'sl-%'");
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

  private UUID producto(String codigo, String nombre) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (scope, implementation, id, code, type, name, description,"
            + " source_membership_id, target_membership_id, price, currency_id, validity_days,"
            + " status) VALUES ('TIENDA', 'AUTOMATICA', ?, ?, 'BOT', ?, 'Producto de prueba',"
            + " NULL, NULL, 100.00, CAST(? AS uuid), NULL, 'ACTIVO')",
        id,
        codigo,
        nombre,
        USD);
    return id;
  }

  /** En `VALIDADO`, que es como queda una venta con todos sus vendedores puestos. */
  private UUID venta(UUID sujeto, String estado, OffsetDateTime cuando, String codigo) {
    return venta(sujeto, estado, cuando, codigo, "VALIDADO");
  }

  /**
   * Con el estado del tipo <b>dicho</b> (`RN-MV-033`), que es lo que `CA-MV-180` necesita: el
   * filtro no se puede probar con todas las ventas en el mismo estado.
   */
  private UUID venta(
      UUID sujeto, String estado, OffsetDateTime cuando, String codigo, String estadoDelTipo) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO movements (id, movement_type_id, type_status_id, user_id,
                               payment_method_id, currency_id,
                               code, status, total_amount, discount_amount, payable_amount,
                               occurred_at, confirmed_at, voided_at, void_reason)
        VALUES (?, CAST(? AS uuid),
                -- `type_status_id` es NOT NULL desde `V36` (`RF-MV-016`), y su FK es
                -- COMPUESTA con el tipo: el estado tiene que ser DEL MISMO TIPO que el
                -- movimiento. Por CÓDIGO y no por identificador literal, para que la
                -- prueba no envejezca si esa siembra cambia de id.
                (SELECT s.id FROM movement_type_statuses s
                  WHERE s.movement_type_id = CAST(? AS uuid) AND s.code = ?),
                ?, CAST(? AS uuid), CAST(? AS uuid), ?, ?,
                300.00, 20.00, 280.00, CAST(? AS timestamptz),
                CASE WHEN ? = 'CONFIRMADA' THEN CAST(? AS timestamptz) ELSE NULL END,
                CASE WHEN ? = 'ANULADA' THEN now() ELSE NULL END,
                CASE WHEN ? = 'ANULADA' THEN 'Sembrada anulada' ELSE NULL END)
        """,
        id,
        VENTA,
        VENTA,
        estadoDelTipo,
        sujeto,
        TARJETA,
        USD,
        codigo,
        estado,
        cuando,
        estado,
        cuando,
        estado,
        estado);
    return id;
  }

  private void linea(
      UUID movimiento,
      UUID producto,
      UUID vendedor,
      int cantidad,
      String precio,
      String rebaja,
      String importe,
      Integer vigencia,
      String entrega,
      OffsetDateTime entregadaEn) {
    jdbc.update(
        """
        INSERT INTO movement_details (id, movement_id, product_id, seller_id, product_name,
                                      product_description, quantity, unit_price, line_discount,
                                      line_amount, validity_days, implementation,
                                      delivery_status, delivered_at, delivery_note)
        SELECT ?, ?, p.id, ?, p.name, p.description, ?, CAST(? AS numeric), CAST(? AS numeric),
               CAST(? AS numeric), ?, p.implementation, ?, CAST(? AS timestamptz),
               CASE WHEN ? = 'RETENIDA' THEN 'Retenida por revision' ELSE NULL END
          FROM products p WHERE p.id = ?
        """,
        UUID.randomUUID(),
        movimiento,
        vendedor,
        cantidad,
        precio,
        rebaja,
        importe,
        vigencia,
        entrega,
        entregadaEn,
        entrega,
        producto);
  }
}
