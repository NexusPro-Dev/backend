package com.factech.nexus.modules.movements.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.MyProductRow;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

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
    mvc.perform(get("/api/v1/movements/mine/products").with(user(vendedor.toString())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));

    mvc.perform(get("/api/v1/movements/mine/products").with(user(comprador.toString())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  @DisplayName("CA-MV-109 — responde a cualquier autenticado; sin autenticar, 401")
  void acceso() throws Exception {
    mvc.perform(get("/api/v1/movements/mine/products")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/movements/mine/products").with(user(persona("mp-nuevo").toString())))
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
        mvc.perform(get("/api/v1/movements/mine/products").with(user(comprador.toString())))
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

    mvc.perform(get("/api/v1/movements/mine/products").with(user(comprador.toString())))
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
        mvc.perform(get("/api/v1/movements/mine/products").with(user(comprador.toString())))
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

    mvc.perform(get("/api/v1/movements/mine/products").with(user(comprador.toString())))
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

    mvc.perform(get("/api/v1/movements/mine/products").with(user(comprador.toString())))
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

    mvc.perform(get("/api/v1/movements/mine/products").with(user(comprador.toString())))
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

    mvc.perform(
            get("/api/v1/movements/mine/products?state=activo").with(user(comprador.toString())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].state").value("ACTIVO"));

    mvc.perform(
            get("/api/v1/movements/mine/products?state=INVENTADO").with(user(comprador.toString())))
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

    mvc.perform(get("/api/v1/movements/mine/products?size=1").with(user(comprador.toString())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.content[0].movementId").value(nueva.toString()))
        .andExpect(jsonPath("$.content[0].product.code").value("MP_BOT"))
        .andExpect(jsonPath("$.content[0].product.name").value("Bot del registro"))
        .andExpect(jsonPath("$.content[0].quantity").value(1))
        .andExpect(jsonPath("$.content[0].purchasedAt").isNotEmpty());

    mvc.perform(
            get("/api/v1/movements/mine/products?size=1&page=1").with(user(comprador.toString())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].movementId").value(vieja.toString()));
  }

  @Test
  @DisplayName("`products` no lo captura `/mine/{id}`")
  void productsNoEsUnIdentificador() throws Exception {
    mvc.perform(get("/api/v1/movements/mine/products").with(user(comprador.toString())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray());
  }

  // ---------------------------------------------------------------------------
  // Auxiliares
  // ---------------------------------------------------------------------------

  private void limpiar() {
    jdbc.update("DELETE FROM movement_details");
    jdbc.update("DELETE FROM movements");
    jdbc.update("DELETE FROM products WHERE code LIKE 'MP\\_%'");
    jdbc.update(
        "DELETE FROM user_memberships WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'mp-%')");
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
        INSERT INTO movements (id, movement_type_id, user_id, payment_method_id,
                               currency_id, code, status, total_amount, discount_amount,
                               payable_amount, occurred_at, confirmed_at,
                               voided_at, void_reason)
        VALUES (?, CAST(? AS uuid), ?, CAST(? AS uuid), CAST(? AS uuid), ?, ?,
                100.00, 0, 100.00, CAST(? AS timestamptz),
                CASE WHEN ? = 'CONFIRMADA' THEN CAST(? AS timestamptz) ELSE NULL END,
                -- `ck_movements_voided`: una anulada lleva fecha y motivo, y solo ella.
                CASE WHEN ? = 'ANULADA' THEN now() ELSE NULL END,
                CASE WHEN ? = 'ANULADA' THEN 'Sembrada anulada' ELSE NULL END)
        """,
        id,
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
        UUID.randomUUID(),
        id,
        vendedor,
        vigencia,
        entrega,
        entregadoEn == null ? null : entregadoEn.toString(),
        entrega,
        producto);
    return id;
  }
}
