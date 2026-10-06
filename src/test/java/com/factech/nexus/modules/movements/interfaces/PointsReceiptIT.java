package com.factech.nexus.modules.movements.interfaces;

import static com.factech.nexus.modules.movements.LedgerFixtures.USD;
import static com.factech.nexus.modules.movements.LedgerFixtures.saldo;
import static com.factech.nexus.modules.movements.PointsFixtures.TARJETA;
import static com.factech.nexus.modules.movements.interfaces.PointsMovementsIT.PNG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.movements.PointsFixtures;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** `RF-MV-057` — adjuntar el comprobante de un ajuste de puntos, al ajustar o después. */
@AutoConfigureMockMvc
class PointsReceiptIT extends IntegrationTestBase {

  private static final String AJUSTES = "/api/v1/movements/points-adjustments";
  private static final byte[] PDF =
      "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] JPG = {
    (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46
  };

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID persona;
  private UUID administrador;

  @BeforeEach
  void sembrar() {
    limpiar();
    persona = persona("prc-persona");
    administrador = persona("prc-admin");
    PointsFixtures.tasa(jdbc, USD, "100", administrador);
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  @Test
  @DisplayName(
      "CA-MV-685 y CA-MV-686 — ajustar con un PDF deja el ajuste y el comprobante; sin archivo,"
          + " como siempre")
  void ajustarConComprobante() throws Exception {
    mvc.perform(ajusteConArchivo("100", "prc-clave-0001", archivo("consignacion.pdf", PDF)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.points").value(100))
        .andExpect(jsonPath("$.pointsBalance").value(100))
        .andExpect(jsonPath("$.receipt.fileName").value("consignacion.pdf"))
        .andExpect(jsonPath("$.receipt.contentType").value("application/pdf"))
        .andExpect(jsonPath("$.receipt.sizeBytes").value(PDF.length));
    assertThat(comprobantes()).isEqualTo(1);

    mvc.perform(
            post(AJUSTES)
                .header("Idempotency-Key", "prc-clave-0002")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("5"))
                .with(ajustador()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.receipt").value(Matchers.nullValue()));
    assertThat(comprobantes()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "CA-MV-687 y CA-MV-692 — un archivo inválido no crea el ajuste; vacío, de otro tipo o de más"
          + " de 5 MB, cada uno con su motivo; y una resta que no alcanza no guarda el archivo")
  void archivoInvalidoNoAjusta() throws Exception {
    mvc.perform(ajusteConArchivo("100", "prc-clave-0011", archivo("vacio.pdf", new byte[0])))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
    mvc.perform(
            ajusteConArchivo(
                "100",
                "prc-clave-0012",
                archivo("falso.pdf", "no soy un pdf".getBytes(StandardCharsets.UTF_8))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-003"))
        .andExpect(jsonPath("$.errors[0].field").value("file"));
    byte[] grande = Arrays.copyOf(PDF, 5 * 1024 * 1024 + 1);
    mvc.perform(ajusteConArchivo("100", "prc-clave-0013", archivo("grande.pdf", grande)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-004"));
    assertThat(ajustes()).isZero();
    assertThat(saldo(jdbc, persona, "PUNTOS")).isEqualByComparingTo(BigDecimal.ZERO);

    mvc.perform(ajusteConArchivo("-5", "prc-clave-0014", archivo("resta.png", PNG)))
        .andExpect(status().isUnprocessableEntity());
    assertThat(ajustes()).isZero();
    assertThat(comprobantes()).isZero();

    // Exactamente 5 MB se admite (§13).
    byte[] justo = Arrays.copyOf(PDF, 5 * 1024 * 1024);
    mvc.perform(ajusteConArchivo("1", "prc-clave-0015", archivo("justo.pdf", justo)))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName(
      "CA-MV-688 — la misma clave con el mismo archivo responde el ajuste hecho; con otro, o con"
          + " archivo donde no lo había, 409")
  void repeticion() throws Exception {
    mvc.perform(ajusteConArchivo("10", "prc-clave-0021", archivo("a.png", PNG)))
        .andExpect(status().isCreated());
    mvc.perform(ajusteConArchivo("10", "prc-clave-0021", archivo("a.png", PNG)))
        .andExpect(status().isOk());
    mvc.perform(ajusteConArchivo("10", "prc-clave-0021", archivo("b.pdf", PDF)))
        .andExpect(status().isConflict());

    mvc.perform(
            post(AJUSTES)
                .header("Idempotency-Key", "prc-clave-0022")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("10"))
                .with(ajustador()))
        .andExpect(status().isCreated());
    mvc.perform(ajusteConArchivo("10", "prc-clave-0022", archivo("a.png", PNG)))
        .andExpect(status().isConflict());
    assertThat(ajustes()).isEqualTo(2);
    assertThat(comprobantes()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "CA-MV-689, CA-MV-690 y CA-MV-694 — adjuntar después lo deja; otra vez, lo reemplaza; la"
          + " auditoría guarda el resumen del anterior y nunca el contenido")
  void adjuntarYReemplazar() throws Exception {
    UUID ajuste = ajustar("20", "prc-clave-0031");
    mvc.perform(adjuntar(ajuste, archivo("primero.png", PNG)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contentType").value("image/png"))
        .andExpect(jsonPath("$.fileName").value("primero.png"));
    String primero =
        jdbc.queryForObject(
            "SELECT sha256 FROM points_adjustment_receipts WHERE movement_id = ?",
            String.class,
            ajuste);

    mvc.perform(adjuntar(ajuste, archivo("segundo.pdf", PDF)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contentType").value("application/pdf"));
    assertThat(comprobantes()).isEqualTo(1);
    mvc.perform(
            get("/api/v1/movements/points-movements/{id}/receipt", ajuste)
                .with(
                    user(administrador.toString())
                        .authorities(() -> "movements:download-points-receipt")))
        .andExpect(status().isOk())
        .andExpect(content().bytes(PDF));

    String auditoria =
        jdbc.queryForObject(
            "SELECT changes::text FROM audit_change_log WHERE entity = 'points_adjustment_receipts'"
                + " AND entity_id = ? AND action = 'UPDATE'",
            String.class,
            ajuste);
    assertThat(auditoria)
        .contains(primero.trim())
        .contains("segundo.pdf")
        .doesNotContain("PDF-1.7");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_change_log WHERE entity = 'points_adjustment_receipts'"
                    + " AND entity_id = ? AND action = 'CREATE'",
                Integer.class,
                ajuste))
        .isEqualTo(1);
  }

  @Test
  @DisplayName(
      "CA-MV-691 — el tipo lo dicen los bytes: un PNG llamado .jpg se guarda como PNG; un JPG,"
          + " como JPEG; un texto llamado .pdf, no; y el nombre se limpia")
  void tipoPorContenido() throws Exception {
    UUID ajuste = ajustar("20", "prc-clave-0041");
    mvc.perform(adjuntar(ajuste, archivo("foto.jpg", PNG)))
        .andExpect(jsonPath("$.contentType").value("image/png"));
    mvc.perform(adjuntar(ajuste, archivo("C:\\fotos\\recibo.jpeg", JPG)))
        .andExpect(jsonPath("$.contentType").value("image/jpeg"))
        .andExpect(jsonPath("$.fileName").value("recibo.jpeg"));
    mvc.perform(adjuntar(ajuste, archivo("", PDF)))
        .andExpect(jsonPath("$.fileName").value("comprobante.pdf"));
    mvc.perform(
            adjuntar(
                ajuste, archivo("factura.pdf", "texto plano".getBytes(StandardCharsets.UTF_8))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-003"));
  }

  @Test
  @DisplayName(
      "CA-MV-692 y CA-MV-693 — adjuntar sin archivo es VAL-001; sobre una compra o un movimiento"
          + " que no existe, 404")
  void bordesDeAdjuntar() throws Exception {
    UUID ajuste = ajustar("20", "prc-clave-0051");
    mvc.perform(multipart(HttpMethod.PUT, AJUSTES + "/{id}/receipt", ajuste).with(adjuntador()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-001"));
    mvc.perform(adjuntar(ajuste, archivo("vacio.png", new byte[0])))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));

    UUID compra = comprar();
    mvc.perform(adjuntar(compra, archivo("a.png", PNG))).andExpect(status().isNotFound());
    mvc.perform(adjuntar(UUID.randomUUID(), archivo("a.png", PNG)))
        .andExpect(status().isNotFound());
    assertThat(comprobantes()).isZero();
  }

  @Test
  @DisplayName(
      "CA-MV-695 — adjuntar después exige movements:attach-points-receipt, aunque se pueda ajustar;"
          + " sin sesión, 401")
  void permisos() throws Exception {
    UUID ajuste = ajustar("20", "prc-clave-0061");
    mvc.perform(
            multipart(HttpMethod.PUT, AJUSTES + "/{id}/receipt", ajuste)
                .file(archivo("a.png", PNG))
                .with(ajustador()))
        .andExpect(status().isForbidden());
    mvc.perform(
            multipart(HttpMethod.PUT, AJUSTES + "/{id}/receipt", ajuste)
                .file(archivo("a.png", PNG)))
        .andExpect(status().isUnauthorized());
    assertThat(comprobantes()).isZero();
  }

  // ---------------------------------------------------------------------------

  private MockMultipartHttpServletRequestBuilder ajusteConArchivo(
      String puntos, String clave, MockMultipartFile file) {
    return (MockMultipartHttpServletRequestBuilder)
        multipart(AJUSTES)
            .file(
                new MockMultipartFile(
                    "adjustment",
                    "",
                    MediaType.APPLICATION_JSON_VALUE,
                    cuerpo(puntos).getBytes(StandardCharsets.UTF_8)))
            .file(file)
            .header("Idempotency-Key", clave)
            .with(ajustador());
  }

  private MockMultipartHttpServletRequestBuilder adjuntar(UUID ajuste, MockMultipartFile file) {
    return (MockMultipartHttpServletRequestBuilder)
        multipart(HttpMethod.PUT, AJUSTES + "/{id}/receipt", ajuste).file(file).with(adjuntador());
  }

  private static MockMultipartFile archivo(String nombre, byte[] bytes) {
    // El tipo de la parte es el que diga el cliente; se ignora a propósito (RN-MV-077).
    return new MockMultipartFile("file", nombre, "application/octet-stream", bytes);
  }

  private String cuerpo(String puntos) {
    return "{\"userId\":\"%s\",\"currencyId\":\"%s\",\"points\":%s,\"concept\":\"Consignación\"}"
        .formatted(persona, USD, puntos);
  }

  private UUID ajustar(String puntos, String clave) throws Exception {
    String respuesta =
        mvc.perform(
                post(AJUSTES)
                    .header("Idempotency-Key", clave)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(cuerpo(puntos))
                    .with(ajustador()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(JsonPath.read(respuesta, "$.id"));
  }

  private UUID comprar() throws Exception {
    String respuesta =
        mvc.perform(
                post("/api/v1/movements/mine/points-purchases")
                    .header("Idempotency-Key", "prc-compra-0001")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"currencyId\":\"%s\",\"amount\":1.00,\"paymentMethodId\":\"%s\"}"
                            .formatted(USD, TARJETA))
                    .with(user(persona.toString()).authorities(() -> "movements:buy-points")))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(JsonPath.read(respuesta, "$.id"));
  }

  private RequestPostProcessor ajustador() {
    return user(administrador.toString()).authorities(() -> "movements:adjust-points");
  }

  private RequestPostProcessor adjuntador() {
    return user(administrador.toString()).authorities(() -> "movements:attach-points-receipt");
  }

  private int ajustes() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM movements m JOIN movement_types t ON t.id = m.movement_type_id"
            + " WHERE t.code = 'AJUSTE_PUNTOS'",
        Integer.class);
  }

  private int comprobantes() {
    return jdbc.queryForObject("SELECT count(*) FROM points_adjustment_receipts", Integer.class);
  }

  private void limpiar() {
    PointsFixtures.limpiar(jdbc);
    jdbc.update("DELETE FROM audit_change_log WHERE entity = 'points_adjustment_receipts'");
    jdbc.update(
        "DELETE FROM user_products WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'prc-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'prc-%'");
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
