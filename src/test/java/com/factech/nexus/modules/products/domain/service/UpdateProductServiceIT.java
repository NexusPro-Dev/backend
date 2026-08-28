package com.factech.nexus.modules.products.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.products.application.UpdateProductRequest;
import com.factech.nexus.shared.patch.Patchable;
import java.math.BigDecimal;
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
 * Lo que la corrección consulta <b>y lo que no</b> (`RF-PM-004` · `T-08` y `T-11`).
 *
 * <p>Corregir solo la descripción y corregir el nombre devuelven el mismo `200`: la diferencia —que
 * la unicidad del nombre <b>no se consulta</b> cuando el nombre no cambia— solo se ve contando
 * sentencias. Sin esta prueba, cada corrección de descripción pagaría una consulta que no necesita
 * y nadie se enteraría.
 */
class UpdateProductServiceIT extends IntegrationTestBase {

  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  private static final OffsetDateTime BASE =
      OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC);

  @Autowired private UpdateProductService service;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SessionFactory sessionFactory;

  private Statistics estadisticas;
  private UUID producto;

  @BeforeEach
  void sembrar() {
    jdbc.update("DELETE FROM products");
    producto = bot("SOPORTE", "Soporte prioritario", "Atención prioritaria.");

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
  @DisplayName("`T-11` — corregir el NOMBRE cuesta una sentencia más que corregir la descripción")
  void laUnicidadSoloSiElNombreCambia() {
    estadisticas.clear();
    service.update(producto, soloDescripcion("Una descripción nueva."));
    long deLaDescripcion = estadisticas.getPrepareStatementCount();

    estadisticas.clear();
    service.update(producto, soloNombre("Soporte premium"));
    long delNombre = estadisticas.getPrepareStatementCount();

    // La diferencia ES la consulta de unicidad. Se mide como diferencia y no
    // como número absoluto para que la prueba siga diciendo lo mismo si cambia
    // cuánto cuesta el resto de la operación.
    assertThat(delNombre - deLaDescripcion)
        .as("corregir la descripción no debe consultar si el nombre está libre")
        .isEqualTo(1);
  }

  @Test
  @DisplayName("`T-11` — enviar el nombre que YA TIENE tampoco consulta la unicidad")
  void elMismoNombreNoConsultaNada() {
    estadisticas.clear();
    service.update(producto, soloDescripcion("Una descripción nueva."));
    long deLaDescripcion = estadisticas.getPrepareStatementCount();

    estadisticas.clear();
    service.update(
        producto,
        new UpdateProductRequest(
            Patchable.de("Soporte prioritario"),
            Patchable.de("Otra descripción."),
            Patchable.ausente(),
            Patchable.ausente(),
            Patchable.ausente(),
            Patchable.ausente(),
            Patchable.ausente(),
            Patchable.ausente(),
            Patchable.ausente()));

    // El nombre llega, pero es el mismo: no hay nada contra lo que chocar, y no
    // se consulta.
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(deLaDescripcion);
  }

  @Test
  @DisplayName("`CA-PM-038` — la petición que no cambia nada no escribe: ni la fila ni el registro")
  void sinCambioNoEscribeNada() {
    long eventosAntes = eventos();
    estadisticas.clear();

    service.update(producto, soloDescripcion("Atención prioritaria."));

    assertThat(eventos()).as("`audit_change_log` no debía crecer").isEqualTo(eventosAntes);
    assertThat(estadisticas.getEntityUpdateCount()).isZero();
    assertThat(estadisticas.getEntityInsertCount()).isZero();
  }

  @Test
  @DisplayName("el cambio real escribe una fila y un evento, y ni uno más")
  void elCambioRealEscribeUnaVez() {
    estadisticas.clear();

    service.update(producto, soloDescripcion("Una descripción nueva."));

    assertThat(estadisticas.getEntityUpdateCount()).isEqualTo(1);
    assertThat(estadisticas.getEntityInsertCount()).as("el evento de auditoría").isEqualTo(1);
  }

  @Test
  @DisplayName("corregir sin tocar precio ni moneda NO consulta el catálogo de monedas")
  void sinPrecioNoConsultaLaMoneda() {
    estadisticas.clear();
    service.update(producto, soloDescripcion("Una descripción nueva."));
    long sinMoneda = estadisticas.getPrepareStatementCount();

    estadisticas.clear();
    service.update(
        producto,
        new UpdateProductRequest(
            Patchable.ausente(),
            Patchable.ausente(),
            Patchable.ausente(),
            Patchable.de(new BigDecimal("99.99")),
            Patchable.ausente(),
            Patchable.ausente(),
            Patchable.ausente(),
            Patchable.ausente(),
            Patchable.ausente()));

    // Cambiar el precio obliga a mirar cuántos decimales admite su moneda,
    // aunque la moneda no cambie: es la diferencia.
    assertThat(estadisticas.getPrepareStatementCount() - sinMoneda).isEqualTo(1);
  }

  // ---------------------------------------------------------------------------

  private static UpdateProductRequest soloNombre(String nombre) {
    return new UpdateProductRequest(
        Patchable.de(nombre),
        Patchable.ausente(),
        Patchable.ausente(),
        Patchable.ausente(),
        Patchable.ausente(),
        Patchable.ausente(),
        Patchable.ausente(),
        Patchable.ausente(),
        Patchable.ausente());
  }

  private static UpdateProductRequest soloDescripcion(String descripcion) {
    return new UpdateProductRequest(
        Patchable.ausente(),
        Patchable.de(descripcion),
        Patchable.ausente(),
        Patchable.ausente(),
        Patchable.ausente(),
        Patchable.ausente(),
        Patchable.ausente(),
        Patchable.ausente(),
        Patchable.ausente());
  }

  private long eventos() {
    Long filas =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_change_log WHERE module = 'PM'", Long.class);
    return filas == null ? 0 : filas;
  }

  private UUID bot(String codigo, String nombre, String descripcion) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (id, code, type, name, description, target_membership_id, price,"
            + " currency_id, validity_days, status, created_at, updated_at)"
            + " VALUES (CAST(? AS uuid), ?, 'BOT', ?, ?, NULL, 49.99, CAST(? AS uuid), NULL,"
            + " 'INACTIVO', ?, ?)",
        id.toString(),
        codigo,
        nombre,
        descripcion,
        USD,
        BASE,
        BASE);
    return id;
  }
}
