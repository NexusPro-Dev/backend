package com.factech.nexus.modules.system.users.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.factech.nexus.IntegrationTestBase;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * `RF-SP-059` · `T-02` — el esquema de `client_sellers` y la mudanza de `V20`, probados por SQL.
 *
 * <h2>Por qué la mudanza se prueba aquí y no confiando en la migración</h2>
 *
 * <p>La base de pruebas nace <b>vacía</b>: `V20` corre sobre cero filas y su {@code INSERT …
 * SELECT} y su {@code DELETE} no mueven nada. Que no fallen no dice que muevan bien. Esta prueba
 * siembra los tres casos que el plan distingue —un cliente con tramo vigente y otro cerrado, un
 * cliente <b>ascendido</b> a vendedor, y un vendedor corriente— y <b>vuelve a ejecutar la mudanza
 * tal como está escrita en el guion</b>, leída del classpath: si alguien cambia el predicado en
 * `V20`, esta prueba lo ejecuta cambiado.
 *
 * <h2>Y por qué el índice único parcial se prueba por los DOS lados</h2>
 *
 * <p>Con una sola mitad, los dos índices equivocados pasarían: probar solo que el segundo {@code
 * REGISTRO} se rechaza la pasa también un único total sobre {@code client_id}; probar solo que
 * {@code REGISTRO} y {@code HOTLINK} conviven la pasa también un esquema sin ningún único. Juntas,
 * solo las pasa `uq_client_sellers_principal` (`RN-SP-049`).
 */
class ClientSellersSchemaIT extends IntegrationTestBase {

  private static final String CONSUMIDOR_ROL = "01a02a33-4c00-7008-9c4f-5e7ad1000008";
  private static final String AGENTE_ROL = "01a02a33-4c00-7007-9c4f-5e7ad1000005";
  private static final String DIRECTOR_ROL = "01a02a33-4c00-7006-9c4f-5e7ad1000004";

  @Autowired private JdbcTemplate jdbc;

  private UUID agente;
  private UUID otroAgente;
  private UUID director;
  private UUID cliente;

  @BeforeEach
  void sembrar() {
    limpiar();
    director = persona("cs-director", DIRECTOR_ROL);
    agente = persona("cs-agente", AGENTE_ROL);
    otroAgente = persona("cs-otro", AGENTE_ROL);
    cliente = persona("cs-cliente", CONSUMIDOR_ROL);
  }

  @AfterEach
  void vaciar() {
    limpiar();
  }

  // ---------------------------------------------------------------------------
  // El esquema
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-SP-688` — un segundo REGISTRO para el mismo cliente lo rechaza la base")
  void unSoloPrincipal() {
    vincular(cliente, agente, "REGISTRO");

    assertThatThrownBy(() -> vincular(cliente, otroAgente, "REGISTRO"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("uq_client_sellers_principal");
  }

  @Test
  @DisplayName("`RN-SP-049` — REGISTRO y HOTLINK conviven: el único es PARCIAL")
  void elPrincipalYLosVinculadosConviven() {
    vincular(cliente, agente, "REGISTRO");

    assertThatCode(() -> vincular(cliente, otroAgente, "HOTLINK")).doesNotThrowAnyException();
    assertThatCode(() -> vincular(cliente, director, "HOTLINK")).doesNotThrowAnyException();
    assertThat(vinculos(cliente)).isEqualTo(3);
  }

  @Test
  @DisplayName("la pareja es la clave: el mismo vendedor no se vincula dos veces al mismo cliente")
  void laParejaEsLaClave() {
    vincular(cliente, agente, "REGISTRO");

    // Ni siquiera con otro origen: la segunda compra por su hotlink no añade fila.
    assertThatThrownBy(() -> vincular(cliente, agente, "HOTLINK"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("pk_client_sellers");
  }

  @Test
  @DisplayName("los dos CHECK: ni origen fuera del par, ni vincularse a uno mismo")
  void losDosCheck() {
    assertThatThrownBy(() -> vincular(cliente, agente, "REFERIDO"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_client_sellers_origin");
    assertThatThrownBy(() -> vincular(cliente, cliente, "REGISTRO"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_client_sellers_no_self");
  }

  // ---------------------------------------------------------------------------
  // La mudanza de `V20`
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "`CA-SP-692` — la mudanza copia la vigente como REGISTRO y borra TODAS las del cliente")
  void laMudanzaMueveYNoCopia() throws IOException {
    // Un cliente con historia: colgó de `otroAgente`, lo reasignaron a `agente`.
    colgar(cliente, otroAgente, "2026-09-02T10:00:00Z", "2026-09-10T10:00:00Z");
    colgar(cliente, agente, "2026-09-10T10:00:00Z", null);
    // Un vendedor corriente, que NO debe moverse.
    colgar(agente, director, "2026-09-01T10:00:00Z", null);
    // Un cliente ASCENDIDO a agente —porta los dos tipos de rol—: se queda,
    // porque su fila vigente es mando y tocarla lo dejaría sin superior
    // siendo vendedor (`RN-SP-019`).
    UUID ascendido = persona("cs-ascendido", CONSUMIDOR_ROL);
    conceder(ascendido, AGENTE_ROL);
    colgar(ascendido, director, "2026-09-05T10:00:00Z", null);

    ejecutarLaMudanzaDeV20();

    // El cliente: UNA fila REGISTRO, con la vigente, con su fecha, sin venta.
    assertThat(
            jdbc.queryForList(
                "SELECT v.username || ' ' || cs.origin || ' ' || cs.created_at::date"
                    + " || ' ' || coalesce(cs.first_movement_id::text, 'sin-venta')"
                    + " FROM client_sellers cs JOIN users v ON v.id = cs.seller_id"
                    + " WHERE cs.client_id = ?",
                String.class,
                cliente))
        .containsExactly("cs-agente REGISTRO 2026-09-10 sin-venta");
    // Y ni una fila suya en la tabla de mando, ni vigente ni cerrada.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM user_supervisors WHERE user_id = ?", Integer.class, cliente))
        .isZero();

    // El vendedor y el ascendido siguen donde estaban, y no ganaron vínculo.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM user_supervisors WHERE user_id IN (?, ?) AND ended_at IS"
                    + " NULL",
                Integer.class,
                agente,
                ascendido))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM client_sellers WHERE client_id IN (?, ?)",
                Integer.class,
                agente,
                ascendido))
        .isZero();
  }

  @Test
  @DisplayName("la mudanza es idempotente: volver a correrla no duplica ni falla")
  void laMudanzaEsIdempotente() throws IOException {
    colgar(cliente, agente, "2026-09-10T10:00:00Z", null);

    ejecutarLaMudanzaDeV20();
    assertThatCode(this::ejecutarLaMudanzaDeV20).doesNotThrowAnyException();

    assertThat(vinculos(cliente)).isEqualTo(1);
  }

  /**
   * La mudanza <b>tal como está escrita</b> en `V20`, leída del classpath desde el rótulo que la
   * separa de la creación de la tabla. Si el predicado cambia allí, cambia aquí.
   */
  private void ejecutarLaMudanzaDeV20() throws IOException {
    String guion =
        new ClassPathResource("db/migration/V20__sp_vendedores_del_cliente.sql")
            .getContentAsString(StandardCharsets.UTF_8);
    int corte = guion.indexOf("-- La mudanza.");
    assertThat(corte).as("el rótulo de la mudanza sigue en V20").isPositive();
    jdbc.execute(guion.substring(corte));
  }

  // ---------------------------------------------------------------------------
  // Utilidades
  // ---------------------------------------------------------------------------

  private void vincular(UUID cliente, UUID vendedor, String origen) {
    jdbc.update(
        "INSERT INTO client_sellers (client_id, seller_id, origin) VALUES (?, ?, ?)",
        cliente,
        vendedor,
        origen);
  }

  private void colgar(UUID subordinado, UUID superior, String desde, String hasta) {
    jdbc.update(
        "INSERT INTO user_supervisors (id, user_id, supervisor_id, started_at, ended_at)"
            + " VALUES (gen_random_uuid(), ?, ?, ?::timestamptz, ?::timestamptz)",
        subordinado,
        superior,
        desde,
        hasta);
  }

  private int vinculos(UUID cliente) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM client_sellers WHERE client_id = ?", Integer.class, cliente);
  }

  private UUID persona(String usuario, String rol) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, username, email, first_name, last_name, password_hash, status,"
            + " country_id)"
            + " VALUES (?, ?, ?, 'Ana', 'Ruiz', 'x', 'ACTIVO',"
            + " (SELECT id FROM countries ORDER BY code LIMIT 1))",
        id,
        usuario,
        usuario + "@nexus.test");
    conceder(id, rol);
    return id;
  }

  private void conceder(UUID persona, String rol) {
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        persona,
        rol);
  }

  private void limpiar() {
    jdbc.update(
        "DELETE FROM client_sellers WHERE client_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'cs-%')");
    jdbc.update(
        "DELETE FROM user_supervisors WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'cs-%')");
    jdbc.update(
        "DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'cs-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'cs-%'");
  }
}
