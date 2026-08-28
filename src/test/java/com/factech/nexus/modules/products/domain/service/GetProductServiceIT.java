package com.factech.nexus.modules.products.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Coste del detalle de un producto (`RF-PM-003` · `T-04` y `T-05`).
 *
 * <p><b>Lo que se comprueba aquí no se ve en la respuesta</b>: que un producto vivo cueste
 * <b>una</b> sentencia y no dos. El motivo del retiro vive en el registro de eliminación, y pedirlo
 * siempre —también cuando no hay retiro que explicar— añadiría una consulta a cada consulta de un
 * producto vivo, que son casi todas. El JSON sería idéntico y ninguna prueba de API lo notaría.
 */
class GetProductServiceIT extends IntegrationTestBase {

  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  private static final OffsetDateTime BASE =
      OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC);

  @Autowired private GetProductService service;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SessionFactory sessionFactory;

  private Statistics estadisticas;
  private UUID vivo;
  private UUID retirado;

  @BeforeEach
  void sembrar() {
    jdbc.update("DELETE FROM products");
    vivo = producto("VIVO", "Producto vivo");
    retirado = producto("RETIRADO", "Producto retirado");
    jdbc.update(
        "UPDATE products SET deleted_at = ? WHERE id = CAST(? AS uuid)",
        BASE.plusDays(1),
        retirado.toString());
    jdbc.update(
        "INSERT INTO audit_deletion_log (id, occurred_at, module, entity, entity_id,"
            + " deletion_type, reason, snapshot)"
            + " VALUES (CAST(? AS uuid), ?, 'PM', 'products', CAST(? AS uuid), 'LOGICAL',"
            + " 'Se descontinuó.', CAST(? AS jsonb))",
        UUID.randomUUID().toString(),
        BASE.plusDays(1),
        retirado.toString(),
        "{}");

    estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);
    estadisticas.clear();
  }

  @AfterEach
  void limpiar() {
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM audit_deletion_log WHERE module = 'PM'");
  }

  @Test
  @DisplayName("`T-05` — un producto VIVO cuesta UNA sentencia: no se consulta el registro")
  void elProductoVivoCuestaUna() {
    var detalle = service.detail(vivo);

    assertThat(detalle.deletionReason()).isNull();
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("`T-05` — uno RETIRADO cuesta dos: la segunda trae el motivo")
  void elRetiradoCuestaDos() {
    var detalle = service.detail(retirado);

    assertThat(detalle.deletionReason()).isEqualTo("Se descontinuó.");
    assertThat(detalle.deletedAt()).isNotNull();
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("`T-04` — el destino y la moneda vienen en LA MISMA sentencia, no en una por dato")
  void destinoYMonedaEnLaMisma() {
    // Dos uniones externas: si alguna se resolviera aparte, aquí habría tres
    // sentencias y la respuesta sería exactamente la misma.
    service.detail(vivo);

    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("un retirado SIN motivo registrado no falla: llega sin él")
  void retiradoSinMotivoRegistrado() {
    // Puede ocurrir con una fila cargada directamente en la base. El detalle
    // tiene que seguir respondiendo: el motivo es un dato de la respuesta, no
    // una condición para darla.
    jdbc.update("DELETE FROM audit_deletion_log WHERE module = 'PM'");

    var detalle = service.detail(retirado);

    assertThat(detalle.deletedAt()).isNotNull();
    assertThat(detalle.deletionReason()).isNull();
  }

  @Test
  @DisplayName("un identificador inexistente es `EX-001`, y no cuesta ninguna consulta de más")
  void inexistente() {
    ResourceNotFoundException fallo =
        catchThrowableOfType(
            () -> service.detail(UUID.randomUUID()), ResourceNotFoundException.class);

    assertThat(fallo).isNotNull();
    assertThat(fallo.errorCode()).isEqualTo("EX-001");
    // Una: no existe, de modo que no hay retiro que explicar ni motivo que
    // buscar.
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(1);
  }

  // ---------------------------------------------------------------------------

  private UUID producto(String codigo, String nombre) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (id, code, type, name, target_membership_id, price, currency_id,"
            + " validity_days, status, created_at, updated_at)"
            + " VALUES (CAST(? AS uuid), ?, 'BOT', ?, NULL, 10.00, CAST(? AS uuid), NULL,"
            + " 'INACTIVO', ?, ?)",
        id.toString(),
        codigo,
        nombre,
        USD,
        BASE,
        BASE);
    return id;
  }
}
