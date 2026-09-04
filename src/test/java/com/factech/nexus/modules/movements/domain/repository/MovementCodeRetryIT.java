package com.factech.nexus.modules.movements.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.movements.domain.models.Movement;
import com.factech.nexus.modules.movements.domain.models.MovementLine;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * El reintento acotado del comprobante (`RF-MV-001` · `T-10`, `plan.md` §10 riesgo 2).
 *
 * <p><b>Se prueba contra PostgreSQL y no con un doble</b>: lo que se comprueba es que la colisión
 * la detecte {@code uq_movements_code} y que {@code ON CONFLICT (code) DO NOTHING} la devuelva como
 * una cuenta de filas afectadas en lugar de como una excepción. Con un repositorio simulado, la
 * prueba afirmaría que el bucle da tres vueltas y no que el motor rechaza.
 *
 * <p><b>La colisión se fuerza tomando el código antes</b>, que es la única forma de provocarla: el
 * azar no repite treinta y dos elevado a seis mientras una prueba espera.
 */
class MovementCodeRetryIT extends IntegrationTestBase {

  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";
  private static final String TARJETA = "01a061ba-3400-7002-9c4f-5e7ad7000021";
  private static final String TIPO_VENTA = "01a061ba-3400-7001-9c4f-5e7ad7000011";

  private static final OffsetDateTime AHORA =
      OffsetDateTime.of(2026, 9, 4, 12, 0, 0, 0, ZoneOffset.UTC);

  private static final String OCUPADO = "VTA-20260904-OCUPAD";

  @Autowired private MovementRepository repositorio;
  @Autowired private JdbcTemplate jdbc;

  private UUID cliente;
  private UUID vendedor;
  private UUID producto;

  @BeforeEach
  void sembrar() {
    limpiar();
    cliente = persona("reintento-cliente");
    vendedor = persona("reintento-vendedor");
    producto = producto();
    // La fila que ocupa el comprobante que la venta va a intentar usar.
    insertarVentaCon(OCUPADO);
  }

  @AfterEach
  void borrar() {
    limpiar();
  }

  @Test
  @Transactional
  @DisplayName("Al chocar, reintenta con otro código y la venta entra")
  void reintentaYEntra() {
    Movement venta = venta(OCUPADO);
    AtomicInteger vueltas = new AtomicInteger();

    repositorio.save(venta, () -> "VTA-20260904-LIBRE" + vueltas.incrementAndGet());

    // Una vuelta: el primer intento chocó, el segundo entró.
    assertThat(vueltas.get()).isEqualTo(1);
    assertThat(venta.getCode()).isEqualTo("VTA-20260904-LIBRE1");
    assertThat(cuantasVentas()).isEqualTo(2);
  }

  @Test
  @Transactional
  @DisplayName("Tres intentos y falla: no reintenta sin fin")
  void tresIntentosYFalla() {
    Movement venta = venta(OCUPADO);
    AtomicInteger vueltas = new AtomicInteger();

    // El generador forzado a colisionar SIEMPRE. Si tres códigos chocaran
    // seguidos en producción, lo que ocurre no es mala suerte: es que el
    // generador está roto o la tabla está llena de una forma que nadie previó.
    // Seguir intentando lo escondería detrás de una latencia rara.
    assertThatThrownBy(
            () ->
                repositorio.save(
                    venta,
                    () -> {
                      vueltas.incrementAndGet();
                      return OCUPADO;
                    }))
        // LLEGA TRADUCIDA, y no como el `IllegalStateException` que el adaptador
        // lanza: `@Repository` activa la traducción de excepciones de Spring, y
        // esta clase consume el puerto por el proxy —igual que el caso de uso—.
        // Se afirma el tipo traducido a propósito: es el que verá quien lo use.
        .isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
        .hasMessageContaining("3 intentos");

    // Tres intentos consumen DOS regeneraciones: el código del tercer intento
    // es el que devolvió la segunda.
    assertThat(vueltas.get()).isEqualTo(2);
    assertThat(cuantasVentas()).isEqualTo(1);
  }

  // ---------------------------------------------------------------------------
  // Ayudas
  // ---------------------------------------------------------------------------

  private Movement venta(String codigo) {
    return Movement.registrar(
        UUID.fromString(TIPO_VENTA),
        cliente,
        vendedor,
        UUID.fromString(TARJETA),
        UUID.fromString(USD),
        codigo,
        List.of(
            MovementLine.copiarDe(
                producto, "RTY_BOT", "Bot de prueba", 1, new BigDecimal("10.00"), null)),
        2,
        AHORA,
        AHORA);
  }

  private int cuantasVentas() {
    return jdbc.queryForObject("SELECT count(*)::int FROM movements", Integer.class);
  }

  private void insertarVentaCon(String codigo) {
    jdbc.update(
        """
        INSERT INTO movements (id, movement_type_id, client_id, seller_id, payment_method_id,
                               currency_id, code, status, total_amount, discount_amount,
                               payable_amount, occurred_at)
        VALUES (CAST(? AS uuid), CAST(? AS uuid), CAST(? AS uuid), CAST(? AS uuid),
                CAST(? AS uuid), CAST(? AS uuid), ?, 'PENDIENTE', 10.00, 0, 10.00, ?)
        """,
        UUID.randomUUID().toString(),
        TIPO_VENTA,
        cliente.toString(),
        vendedor.toString(),
        TARJETA,
        USD,
        codigo,
        AHORA);
  }

  private UUID producto() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (id, code, type, name, price, currency_id, status,"
            + " created_at, updated_at)"
            + " VALUES (CAST(? AS uuid), 'RTY_BOT', 'BOT', 'Bot de prueba', 10.00,"
            + " CAST(? AS uuid), 'ACTIVO', ?, ?)",
        id.toString(),
        USD,
        AHORA,
        AHORA);
    return id;
  }

  private UUID persona(String username) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status)
        VALUES (CAST(? AS uuid), ?, ?, 'Ana', 'Ruiz', 'no-se-usa', false, 'ACTIVO')
        """,
        id.toString(),
        username,
        username + "@nexus.test");
    return id;
  }

  private void limpiar() {
    jdbc.update("DELETE FROM movement_details");
    jdbc.update("DELETE FROM movements");
    jdbc.update("DELETE FROM products WHERE code = 'RTY_BOT'");
    jdbc.update("DELETE FROM users WHERE username LIKE 'reintento-%'");
  }
}
