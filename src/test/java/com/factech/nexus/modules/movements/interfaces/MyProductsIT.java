package com.factech.nexus.modules.movements.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.MyProductRow;
import com.factech.nexus.modules.products.interfaces.ProductLinkTestSupport;
import jakarta.persistence.EntityManagerFactory;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * `RF-MV-014` — los productos comprados propios, con su estado.
 *
 * <p>Los seis estados se siembran por SQL directamente en la forma que `RF-MV-003` deja: la venta
 * en su estado y la línea con su entrega. <b>El borde del vencimiento se prueba contra el
 * repositorio con el reloj en la mano</b>, porque por HTTP el reloj es el de verdad y «exactamente
 * ahora» no se puede pedir.
 */
@AutoConfigureMockMvc
class MyProductsIT extends IntegrationTestBase {
  private static final String VENTA = "01a061ba-3400-7001-9c4f-5e7ad7000011";
  private static final String TARJETA = "01a061ba-3400-7002-9c4f-5e7ad7000021";
  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  private static final OffsetDateTime BASE =
      OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC);

  @Autowired private MockMvc mvc;
  @Autowired private EntityManagerFactory emf;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MovementRepository movimientos;

  private UUID comprador;
  private UUID vendedor;
  private UUID producto;

  @BeforeEach
  void sembrar() {
    limpiar();
    comprador = persona("mp-comprador");
    vendedor = persona("mp-vendedor");
    producto = producto("MP_BOT", "Bot del registro");
  }

  // ---------------------------------------------------------------------------
  // El alcance
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-MV-099 — solo lo comprado por el actor; lo que vendió a otros no aparece")
  void soloLoPropio() throws Exception {
    venta(comprador, vendedor, "PENDIENTE", BASE, null, "PENDIENTE", null);
    // El vendedor vendió esa línea; no la «tiene».
    mvc.perform(get("/api/v1/movements/mine/products").with(propio(vendedor)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));

    mvc.perform(get("/api/v1/movements/mine/products").with(propio(comprador)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  @DisplayName("CA-MV-109 — responde a cualquier autenticado; sin autenticar, 401")
  void acceso() throws Exception {
    mvc.perform(get("/api/v1/movements/mine/products")).andExpect(status().isUnauthorized());
    // Y con token pero sin movements:read-own-products, 403 (RF-SP-062, CA-MV-109).
    UUID nuevo = persona("mp-nuevo");
    mvc.perform(get("/api/v1/movements/mine/products").with(user(nuevo.toString()).authorities()))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/movements/mine/products").with(propio(nuevo)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty());
  }

  // ---------------------------------------------------------------------------
  // Los seis estados
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-MV-100 — pendiente de pago: sin desde ni hasta")
  void pendienteDePago() throws Exception {
    venta(comprador, vendedor, "PENDIENTE", BASE, null, "PENDIENTE", null);

    String cuerpo =
        mvc.perform(get("/api/v1/movements/mine/products").with(propio(comprador)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].state").value("PENDIENTE_PAGO"))
            .andExpect(jsonPath("$.content[0].movementStatus").value("PENDIENTE"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    // NULOS Y PRESENTES: «no se ha entregado» tiene que distinguirse de «este
    // endpoint no informa».
    assertThat(cuerpo).contains("\"deliveredAt\":null").contains("\"validUntil\":null");
  }

  @Test
  @DisplayName("CA-MV-101 — entregado con vigencia: ACTIVO con hasta, y VENCIDO al pasar")
  void activoYVencido() throws Exception {
    OffsetDateTime entregadoHace10 = OffsetDateTime.now(ZoneOffset.UTC).minusDays(10);
    // 30 días de vigencia desde hace 10: activo 20 días más.
    UUID activa = venta(comprador, vendedor, "CONFIRMADA", BASE, 30, "ENTREGADA", entregadoHace10);
    // 5 días desde hace 10: venció hace 5.
    UUID vencida =
        venta(
            comprador, vendedor, "CONFIRMADA", BASE.minusDays(1), 5, "ENTREGADA", entregadoHace10);

    mvc.perform(get("/api/v1/movements/mine/products").with(propio(comprador)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].movementId").value(activa.toString()))
        .andExpect(jsonPath("$.content[0].state").value("ACTIVO"))
        .andExpect(jsonPath("$.content[0].deliveredAt").isNotEmpty())
        .andExpect(jsonPath("$.content[0].validUntil").isNotEmpty())
        .andExpect(jsonPath("$.content[1].movementId").value(vencida.toString()))
        .andExpect(jsonPath("$.content[1].state").value("VENCIDO"));
  }

  @Test
  @DisplayName("CA-MV-101 — el borde: una vigencia que vence exactamente ahora ya venció")
  void elBordeDelVencimiento() {
    OffsetDateTime entregado = BASE;
    venta(comprador, vendedor, "CONFIRMADA", BASE, 30, "ENTREGADA", entregado);
    OffsetDateTime vence = entregado.plusDays(30);

    List<MyProductRow> unSegundoAntes =
        movimientos.findMyProducts(comprador, null, vence.minusSeconds(1), 0, 10);
    List<MyProductRow> justoAhora = movimientos.findMyProducts(comprador, null, vence, 0, 10);

    assertThat(unSegundoAntes.get(0).state()).isEqualTo("ACTIVO");
    assertThat(unSegundoAntes.get(0).validUntil()).isEqualTo(vence);
    // El mismo borde que SP fija para la membresía vigente.
    assertThat(justoAhora.get(0).state()).isEqualTo("VENCIDO");
    assertThat(movimientos.countMyProducts(comprador, "VENCIDO", vence)).isEqualTo(1);
    assertThat(movimientos.countMyProducts(comprador, "ACTIVO", vence)).isEqualTo(0);
  }

  @Test
  @DisplayName("CA-MV-102 — entregado sin vigencia: ACTIVO sin hasta")
  void activoSinVigencia() throws Exception {
    venta(comprador, vendedor, "CONFIRMADA", BASE, null, "ENTREGADA", BASE);

    String cuerpo =
        mvc.perform(get("/api/v1/movements/mine/products").with(propio(comprador)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].state").value("ACTIVO"))
            .andExpect(jsonPath("$.content[0].deliveredAt").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(cuerpo).contains("\"validUntil\":null");
  }

  @Test
  @DisplayName("CA-MV-103 — manual confirmado y sin autorizar: PENDIENTE_AUTORIZACION")
  void pendienteDeAutorizacion() throws Exception {
    UUID manual = producto("MP_MANUAL", "Bot manual", "MANUAL");
    venta(comprador, vendedor, "CONFIRMADA", BASE, null, "PENDIENTE", null, manual);

    mvc.perform(get("/api/v1/movements/mine/products").with(propio(comprador)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].state").value("PENDIENTE_AUTORIZACION"))
        .andExpect(jsonPath("$.content[0].implementation").value("MANUAL"));
  }

  @Test
  @DisplayName("CA-MV-104 — retenido, con su motivo")
  void retenido() throws Exception {
    UUID venta = venta(comprador, vendedor, "CONFIRMADA", BASE, 30, "RETENIDA", null);
    jdbc.update(
        "UPDATE movement_details SET delivery_note = 'La membresía comprada (VIP) es inferior a la"
            + " vigente (PLATINO).' WHERE movement_id = ?",
        venta);

    mvc.perform(get("/api/v1/movements/mine/products").with(propio(comprador)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].state").value("RETENIDO"))
        .andExpect(
            jsonPath("$.content[0].deliveryNote")
                .value(org.hamcrest.Matchers.containsString("PLATINO")));
  }

  @Test
  @DisplayName("CA-MV-105 — rechazada y anulada aparecen con ese estado")
  void rechazadoYAnulado() throws Exception {
    venta(comprador, vendedor, "RECHAZADA", BASE, null, "PENDIENTE", null);
    venta(comprador, vendedor, "ANULADA", BASE.plusDays(1), null, "PENDIENTE", null);

    mvc.perform(get("/api/v1/movements/mine/products").with(propio(comprador)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].state").value("ANULADO"))
        .andExpect(jsonPath("$.content[1].state").value("RECHAZADO"));
  }

  // ---------------------------------------------------------------------------
  // Filtro, orden y forma
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-MV-106 — el filtro por estado devuelve solo ese; uno inexistente es 400")
  void filtroPorEstado() throws Exception {
    venta(comprador, vendedor, "PENDIENTE", BASE, null, "PENDIENTE", null);
    venta(comprador, vendedor, "CONFIRMADA", BASE.plusDays(1), null, "ENTREGADA", BASE.plusDays(1));

    mvc.perform(get("/api/v1/movements/mine/products?state=activo").with(propio(comprador)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].state").value("ACTIVO"));

    mvc.perform(get("/api/v1/movements/mine/products?state=INVENTADO").with(propio(comprador)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
  }

  @Test
  @DisplayName(
      "CA-MV-107 y CA-MV-108 — paginado, del más reciente al más antiguo, una fila por línea, con el nombre copiado")
  void ordenYForma() throws Exception {
    UUID vieja = venta(comprador, vendedor, "PENDIENTE", BASE, null, "PENDIENTE", null);
    UUID nueva = venta(comprador, vendedor, "PENDIENTE", BASE.plusDays(3), null, "PENDIENTE", null);
    // El catálogo se renombra después: la fila sigue diciendo lo que se compró.
    jdbc.update("UPDATE products SET name = 'Otro nombre' WHERE id = ?", producto);

    mvc.perform(get("/api/v1/movements/mine/products?size=1").with(propio(comprador)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.content[0].movementId").value(nueva.toString()))
        .andExpect(jsonPath("$.content[0].product.code").value("MP_BOT"))
        .andExpect(jsonPath("$.content[0].product.name").value("Bot del registro"))
        .andExpect(jsonPath("$.content[0].quantity").value(1))
        .andExpect(jsonPath("$.content[0].purchasedAt").isNotEmpty());

    mvc.perform(get("/api/v1/movements/mine/products?size=1&page=1").with(propio(comprador)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].movementId").value(vieja.toString()));
  }

  @Test
  @DisplayName("`products` no lo captura `/mine/{id}`")
  void productsNoEsUnIdentificador() throws Exception {
    mvc.perform(get("/api/v1/movements/mine/products").with(propio(comprador)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray());
  }

  // ---------------------------------------------------------------------------
  // El cupón del bot (`RN-MV-032`) — 22-09-2026
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-MV-140 — la línea ENTREGADA trae `couponUrl` resuelto, en ACTIVO y en VENCIDO")
  void elCuponDeLaLineaEntregada() throws Exception {
    ProductLinkTestSupport.enlace(jdbc, producto, "CUPON_BOT", "https://t.me/nexusbot", "cupon-15");
    OffsetDateTime entregadoHace10 = OffsetDateTime.now(ZoneOffset.UTC).minusDays(10);
    // Una vigente y una vencida: las dos están entregadas, y lo entregado no
    // se desentrega al vencer (`RN-MV-032`).
    venta(comprador, vendedor, "CONFIRMADA", BASE, 30, "ENTREGADA", entregadoHace10);
    venta(comprador, vendedor, "CONFIRMADA", BASE.minusDays(1), 5, "ENTREGADA", entregadoHace10);

    mvc.perform(get("/api/v1/movements/mine/products").with(propio(comprador)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].state").value("ACTIVO"))
        // RESUELTO: el identificador pegado como último segmento de ruta. La
        // composición la hace `PM`, que es de quien es la regla.
        .andExpect(jsonPath("$.content[0].couponUrl").value("https://t.me/nexusbot/cupon-15"))
        .andExpect(jsonPath("$.content[1].state").value("VENCIDO"))
        .andExpect(jsonPath("$.content[1].couponUrl").value("https://t.me/nexusbot/cupon-15"));
  }

  @Test
  @DisplayName("CA-MV-141 — sin entregar NO hay cupón, aunque el producto lo declare")
  void sinEntregarNoHayCupon() throws Exception {
    ProductLinkTestSupport.enlace(jdbc, producto, "CUPON_BOT", "https://t.me/nexusbot", "cupon-15");

    // Los tres estados anteriores a la entrega, con el MISMO producto: es el
    // criterio que impide entregar POR UNA CONSULTA lo que `RN-MV-021` no ha
    // autorizado todavía — un defecto que no falla, regala.
    venta(comprador, vendedor, "PENDIENTE", BASE, null, "PENDIENTE", null);
    venta(comprador, vendedor, "CONFIRMADA", BASE.minusDays(1), null, "PENDIENTE", null);
    venta(comprador, vendedor, "CONFIRMADA", BASE.minusDays(2), null, "RETENIDA", null);

    String cuerpo =
        mvc.perform(get("/api/v1/movements/mine/products").with(propio(comprador)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(3))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Sobre el cuerpo ENTERO y no campo a campo: basta con que el enlace salga
    // una vez en cualquier rincón para haber entregado lo que se compró.
    assertThat(cuerpo).doesNotContain("t.me/nexusbot").doesNotContain("cupon-15");
  }

  @Test
  @DisplayName("CA-MV-142 — sin cupón el campo no viaja, y veinte líneas cuestan UNA llamada")
  void sinCuponYSinConsultaPorLinea() throws Exception {
    // El producto de la siembra no declara cupón: el campo no aparece.
    venta(
        comprador,
        vendedor,
        "CONFIRMADA",
        BASE,
        null,
        "ENTREGADA",
        OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));

    String sinCupon =
        mvc.perform(get("/api/v1/movements/mine/products").with(propio(comprador)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].couponUrl").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(sinCupon).doesNotContain("t.me");

    // Y ahora veinte líneas entregadas, cada una de un producto distinto y con
    // su cupón: si se preguntara por línea, el recuento de sentencias crecería
    // con la página. Se comparan dos tamaños, no una cifra absoluta.
    for (int i = 0; i < 20; i++) {
      UUID otro = producto("MP_BOT_%02d".formatted(i), "Bot " + i);
      ProductLinkTestSupport.enlace(jdbc, otro, "CUPON_BOT", "https://t.me/bot" + i, "c" + i);
      venta(
          comprador,
          vendedor,
          "CONFIRMADA",
          BASE.plusMinutes(i),
          null,
          "ENTREGADA",
          OffsetDateTime.now(ZoneOffset.UTC).minusDays(1),
          otro);
    }

    long conDos = sentenciasDe(2);
    long conVeinte = sentenciasDe(20);
    assertThat(conVeinte)
        .as("la página de veinte no puede costar más sentencias que la de dos")
        .isEqualTo(conDos);
  }

  /** Las sentencias preparadas que cuesta una página de ese tamaño. */
  private long sentenciasDe(int size) throws Exception {
    Statistics estadisticas = emf.unwrap(org.hibernate.SessionFactory.class).getStatistics();
    estadisticas.clear();
    mvc.perform(get("/api/v1/movements/mine/products?size=" + size).with(propio(comprador)))
        .andExpect(status().isOk());
    return estadisticas.getPrepareStatementCount();
  }

  // ---------------------------------------------------------------------------
  // Auxiliares
  // ---------------------------------------------------------------------------

  // Tambien AL TERMINAR: la ultima prueba dejaba movimientos y sus detalles apuntando
  // a los productos de esta clase, y cualquier suite posterior que empiece con
  // "DELETE FROM products" a secas —treinta y siete lo hacen— caia por la clave
  // foranea de movement_details. Con el orden local no se veia; en CI si (21-09-2026).
  @AfterEach
  void devolverLaBaseASuSitio() {
    limpiar();
  }

  private void limpiar() {
    // LAS POSESIONES VAN PRIMERO, y desde `V38` importa por el PRODUCTO y no por
    // la linea: aquella se lleva su posesion por `ON DELETE CASCADE`, pero
    // `fk_user_products_product` es RESTRICT y el `DELETE FROM products` de abajo
    // falla con un mensaje de clave foranea que no nombra a esta clase. Es la
    // misma leccion que `product_links` dejo escrita arriba, y el mismo orden: lo
    // que apunta, antes que lo apuntado.
    jdbc.update("DELETE FROM user_products WHERE movement_detail_id IS NOT NULL");
    jdbc.update(
        "DELETE FROM user_products WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'mp-%')");
    jdbc.update("DELETE FROM movement_details");
    jdbc.update("DELETE FROM movements");
    ProductLinkTestSupport.limpiar(jdbc);
    jdbc.update("DELETE FROM products WHERE code LIKE 'MP\\_%'");
    jdbc.update("DELETE FROM users WHERE username LIKE 'mp-%'");
  }

  private UUID persona(String username) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?, ?, ?, 'Nombre', 'Apellido', 'x', false, 'ACTIVO', (SELECT id FROM countries WHERE code = 'COL'))
        """,
        id,
        username,
        username + "@factech.co");
    darElSuelo(jdbc, id);
    return id;
  }

  private UUID producto(String codigo, String nombre) {
    return producto(codigo, nombre, "AUTOMATICA");
  }

  private UUID producto(String codigo, String nombre, String implementacion) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (scope, implementation, id, code, type, name, description, source_membership_id,"
            + " target_membership_id, price, currency_id, validity_days, status)"
            + " VALUES ('TIENDA', ?, ?, ?, 'BOT', ?, 'Producto de prueba', NULL,"
            + " NULL, 100.00, CAST(? AS uuid), NULL, 'ACTIVO')",
        implementacion,
        id,
        codigo,
        nombre,
        USD);
    return id;
  }

  private UUID venta(
      UUID sujeto,
      UUID vendedor,
      String estado,
      OffsetDateTime cuando,
      Integer vigencia,
      String entrega,
      OffsetDateTime entregadoEn) {
    return venta(sujeto, vendedor, estado, cuando, vigencia, entrega, entregadoEn, producto);
  }

  /** Una venta con una línea, sembrada en la forma exacta que `RF-MV-003` deja. */
  private UUID venta(
      UUID sujeto,
      UUID vendedor,
      String estado,
      OffsetDateTime cuando,
      Integer vigencia,
      String entrega,
      OffsetDateTime entregadoEn,
      UUID producto) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO movements (id, movement_type_id, type_status_id, user_id, payment_method_id,
                               currency_id, code, status, total_amount, discount_amount,
                               payable_amount, occurred_at, confirmed_at,
                               voided_at, void_reason)
        VALUES (?, CAST(? AS uuid), (SELECT s.id FROM movement_type_statuses s WHERE s.movement_type_id = CAST(? AS uuid) AND s.code = 'VALIDADO'), ?, CAST(? AS uuid), CAST(? AS uuid), ?, ?,
                100.00, 0, 100.00, CAST(? AS timestamptz),
                CASE WHEN ? = 'CONFIRMADA' THEN CAST(? AS timestamptz) ELSE NULL END,
                -- `ck_movements_voided`: una anulada lleva fecha y motivo, y solo ella.
                CASE WHEN ? = 'ANULADA' THEN now() ELSE NULL END,
                CASE WHEN ? = 'ANULADA' THEN 'Sembrada anulada' ELSE NULL END)
        """,
        id,
        VENTA,
        VENTA,
        sujeto,
        TARJETA,
        USD,
        "VTA-" + id.toString().substring(0, 8).toUpperCase(),
        estado,
        cuando.toString(),
        estado,
        cuando.toString(),
        estado,
        estado);
    // `ck_movement_details_delivery`: ENTREGADA exige fecha y RETENIDA exige
    // motivo; el motivo lo pone la prueba que lo mira.
    UUID linea = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO movement_details (id, movement_id, product_id, seller_id, product_name,
                                      product_description, quantity, unit_price, line_amount,
                                      validity_days, implementation, delivery_status, delivered_at,
                                      delivery_note)
        SELECT ?, ?, p.id, ?, p.name, p.description, 1, 100.00, 100.00, ?, p.implementation, ?,
               CAST(? AS timestamptz), CASE WHEN ? = 'RETENIDA' THEN 'Retenida' ELSE NULL END
          FROM products p WHERE p.id = ?
        """,
        linea,
        id,
        vendedor,
        vigencia,
        entrega,
        entregadoEn == null ? null : entregadoEn.toString(),
        entrega,
        producto);

    // LA LÍNEA ENTREGADA DEJA SU POSESIÓN, igual que la deja `ConfirmSaleService`
    // desde el 23-09-2026 (`RN-MV-036`). Sin esta mitad, la siembra describiría un
    // estado que el sistema ya no puede producir —una entrega sin nada poseído— y
    // la consulta devolvería `ACTIVO` sin vencimiento para todo, que es justo el
    // borde que `plan.md` dejó declarado para lo anterior a `V38`.
    if ("ENTREGADA".equals(entrega)) {
      // El «hasta» se calcula AQUÍ y no en la sentencia, igual que lo calcula la
      // escritura publicada: `make_interval(days => ?)` no admite un parámetro
      // JDBC en notación con nombre, y una fecha inventada en SQL sería además
      // una segunda definición de lo que `PublishedMembershipGrant` ya decide.
      OffsetDateTime hasta =
          entregadoEn == null || vigencia == null ? null : entregadoEn.plusDays(vigencia);
      jdbc.update(
          """
          INSERT INTO user_products (id, user_id, product_id, movement_detail_id,
                                     validity_days, started_at, ends_at)
          VALUES (?, ?, CAST(? AS uuid), ?, ?, CAST(? AS timestamptz), CAST(? AS timestamptz))
          """,
          UUID.randomUUID(),
          sujeto,
          producto,
          linea,
          vigencia,
          entregadoEn == null ? null : entregadoEn.toString(),
          hasta == null ? null : hasta.toString());
    }
    return id;
  }

  // Desde RF-SP-062 (21-09-2026) lo propio exige permiso —autenticarse no autoriza
  // nada—: el actor porta la familia de alcance propio de MV, que es lo que V31 da a
  // todo rol. Hasta entonces bastaba con `user(id)`.
  private static RequestPostProcessor propio(UUID persona) {
    return user(persona.toString())
        .authorities(
            () -> "movements:list-own",
            () -> "movements:read-own",
            () -> "movements:read-own-products",
            () -> "packages:buy");
  }
}
