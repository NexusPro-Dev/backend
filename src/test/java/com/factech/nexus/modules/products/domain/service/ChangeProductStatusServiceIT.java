package com.factech.nexus.modules.products.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.products.application.ChangeProductStatusRequest;
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
 * Lo que el cambio de estado hace <b>y lo que no ejecuta</b> (`RF-PM-005` · `T-06` y `T-09`).
 *
 * <p><b>`T-09` pide comprobar una ausencia</b>, y una ausencia no se ve en la respuesta: activar un
 * servicio y activar un upgrade devuelven el mismo `200`, tanto si la comprobación del destino se
 * saltó como si se ejecutó y no encontró nada. La diferencia solo aparece contando sentencias.
 */
class ChangeProductStatusServiceIT extends IntegrationTestBase {

  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  private static final OffsetDateTime BASE =
      OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC);

  @Autowired private ChangeProductStatusService service;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SessionFactory sessionFactory;

  private Statistics estadisticas;
  private UUID oro;

  @BeforeEach
  void sembrar() {
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM memberships");
    oro = membresia("ORO", "Oro", 1);

    estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);
    estadisticas.clear();
  }

  @AfterEach
  void limpiar() {
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM audit_change_log WHERE module = 'PM'");
  }

  @Test
  @DisplayName("`T-09` — activar un UPGRADE cuesta una sentencia MÁS que activar un servicio")
  void elUpgradeCuestaLaComprobacionDelDestino() {
    UUID servicio = producto("SOPORTE", "SERVICIO", "Soporte", "Atención prioritaria.", null);
    UUID upgrade = producto("UPGRADE_ORO", "UPGRADE_MEMBRESIA", "Ascenso", "Sube de nivel.", oro);

    estadisticas.clear();
    service.change(servicio, new ChangeProductStatusRequest("ACTIVO"));
    long delServicio = estadisticas.getPrepareStatementCount();

    estadisticas.clear();
    service.change(upgrade, new ChangeProductStatusRequest("ACTIVO"));
    long delUpgrade = estadisticas.getPrepareStatementCount();

    // La diferencia ES la comprobación del destino. Se mide como diferencia y
    // no como número absoluto a propósito: así la prueba sigue diciendo lo
    // mismo si algún día cambia cuánto cuesta el resto de la operación.
    assertThat(delUpgrade - delServicio)
        .as("activar un servicio no debe consultar quién ocupa un destino que no tiene")
        .isEqualTo(1);
  }

  @Test
  @DisplayName("`T-09` — y desactivar un upgrade cuesta lo mismo que desactivar un servicio")
  void desactivarNoComprobaraNunca() {
    UUID servicio = producto("SOPORTE", "SERVICIO", "Soporte", "Atención prioritaria.", null);
    UUID upgrade = producto("UPGRADE_ORO", "UPGRADE_MEMBRESIA", "Ascenso", "Sube de nivel.", oro);
    service.change(servicio, new ChangeProductStatusRequest("ACTIVO"));
    service.change(upgrade, new ChangeProductStatusRequest("ACTIVO"));

    estadisticas.clear();
    service.change(servicio, new ChangeProductStatusRequest("INACTIVO"));
    long delServicio = estadisticas.getPrepareStatementCount();

    estadisticas.clear();
    service.change(upgrade, new ChangeProductStatusRequest("INACTIVO"));
    long delUpgrade = estadisticas.getPrepareStatementCount();

    // `FA-002`: liberar un destino nunca produce conflicto, de modo que no hay
    // nada que comprobar. Si esta prueba empezara a fallar, alguien habría
    // puesto la comprobación fuera del `if` de activación.
    assertThat(delUpgrade).isEqualTo(delServicio);
  }

  @Test
  @DisplayName("`CA-PM-044` — la petición sin cambio no escribe: ni la fila ni el registro")
  void sinCambioNoEscribeNada() {
    UUID servicio = producto("SOPORTE", "SERVICIO", "Soporte", "Atención prioritaria.", null);
    long eventosAntes = eventos();

    estadisticas.clear();
    service.change(servicio, new ChangeProductStatusRequest("INACTIVO"));

    assertThat(eventos()).as("`audit_change_log` no debía crecer").isEqualTo(eventosAntes);
    // Cero escrituras: sin `UPDATE` y sin `INSERT`. Un `updatedAt` movido por
    // una petición que no cambió nada haría creer que alguien tocó el producto.
    assertThat(estadisticas.getEntityUpdateCount()).isZero();
    assertThat(estadisticas.getEntityInsertCount()).isZero();
  }

  @Test
  @DisplayName("el cambio real sí escribe una fila y un evento, y ni uno más")
  void elCambioRealEscribeUnaVez() {
    UUID servicio = producto("SOPORTE", "SERVICIO", "Soporte", "Atención prioritaria.", null);

    estadisticas.clear();
    service.change(servicio, new ChangeProductStatusRequest("ACTIVO"));

    assertThat(estadisticas.getEntityUpdateCount()).isEqualTo(1);
    assertThat(estadisticas.getEntityInsertCount()).as("el evento de auditoría").isEqualTo(1);
  }

  // ---------------------------------------------------------------------------

  private long eventos() {
    Long filas =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_change_log WHERE module = 'PM'", Long.class);
    return filas == null ? 0 : filas;
  }

  private UUID membresia(String codigo, String nombre, int nivel) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO memberships (id, code, name, parent_membership_id, level, color)"
            + " VALUES (?, ?, ?, NULL, ?, upper(lpad(to_hex(? * 4919), 6, '0')))",
        id,
        codigo,
        nombre,
        nivel,
        nivel);
    return id;
  }

  private UUID producto(
      String codigo, String tipo, String nombre, String descripcion, UUID destino) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (id, code, type, name, description, target_membership_id, price,"
            + " currency_id, validity_days, status, created_at, updated_at)"
            + " VALUES (CAST(? AS uuid), ?, ?, ?, CAST(? AS text), CAST(? AS uuid), 10.00,"
            + " CAST(? AS uuid), NULL, 'INACTIVO', ?, ?)",
        id.toString(),
        codigo,
        tipo,
        nombre,
        descripcion,
        destino == null ? null : destino.toString(),
        USD,
        BASE,
        BASE);
    return id;
  }
}
