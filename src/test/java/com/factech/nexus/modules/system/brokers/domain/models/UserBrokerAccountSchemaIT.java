package com.factech.nexus.modules.system.brokers.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.factech.nexus.IntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * `RF-SP-052` · `T-06` — la regla de `user_brokers`, probada por los DOS lados.
 *
 * <h2>Por qué esta prueba existe antes que el endpoint</h2>
 *
 * <p><b>La tabla se creó sin caso de uso que la escriba</b> (`RF-SP-053` no está decidido), y la
 * regla que la gobierna vive en el <b>esquema</b>: se puede declarar y comprobar sin endpoint. Lo
 * que no se puede es dejarla sin comprobar — un único escrito sobre las columnas equivocadas no
 * falla al aplicar la migración: falla el día que alguien declare su segunda cuenta.
 *
 * <h2>Y por qué las dos mitades</h2>
 *
 * <p>El único natural al escribir esta tabla es {@code (user_id, broker_id)}, y es <b>el
 * contrario</b> del que hace falta. Con una sola mitad de esta prueba, los dos únicos pasarían:
 *
 * <ul>
 *   <li>Probar solo que la cuenta repetida se rechaza — la pasa también {@code (user_id,
 *       broker_id)} si el segundo intento es de la misma persona.
 *   <li>Probar solo que dos cuentas de la misma persona se admiten — la pasa también un esquema
 *       <b>sin ningún único</b>.
 * </ul>
 *
 * <p>Juntas, solo las pasa el único correcto (`RN-SP-038`).
 */
class UserBrokerAccountSchemaIT extends IntegrationTestBase {

  @Autowired private JdbcTemplate jdbc;

  private UUID broker;
  private UUID otroBroker;
  private UUID ana;
  private UUID beto;

  @BeforeEach
  void sembrar() {
    limpiar();
    broker = broker("Exness de prueba");
    otroBroker = broker("IC Markets de prueba");
    ana = persona("ub-ana");
    beto = persona("ub-beto");
  }

  @AfterEach
  void vaciar() {
    limpiar();
  }

  @Test
  @DisplayName("`RN-SP-038` — la MISMA persona puede tener VARIAS cuentas en el mismo broker")
  void variasCuentasDeLaMismaPersona() {
    vincular(ana, broker, "12345");

    // Es lo normal en el ramo, y lo que el único obvio —`(user_id, broker_id)`—
    // prohibiría sin que nadie lo notara hasta el primer cliente con dos.
    assertThatCode(() -> vincular(ana, broker, "67890")).doesNotThrowAnyException();

    assertThat(cuantas(ana)).isEqualTo(2);
  }

  @Test
  @DisplayName("`RN-SP-038` — la MISMA cuenta no puede ser de dos personas")
  void unaCuentaEsDeUnaSolaPersona() {
    vincular(ana, broker, "12345");

    // Y lo sostiene el índice, no una comprobación previa: dos altas
    // simultáneas leerían una tabla sin la fila y las dos creerían que pueden.
    assertThatThrownBy(() -> vincular(beto, broker, "12345"))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThat(cuantas(beto)).isZero();
  }

  @Test
  @DisplayName("el mismo identificador en OTRO broker sí se admite: el único es del par")
  void elIdentificadorSoloEsUnicoDentroDeSuBroker() {
    vincular(ana, broker, "12345");

    // Dos brokers distintos numeran sus cuentas por su cuenta: exigir que el
    // identificador fuera único en todo el sistema sería inventarse una regla
    // que nadie puede cumplir.
    assertThatCode(() -> vincular(beto, otroBroker, "12345")).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("`RN-SP-040` — el nombre de usuario en el broker nace NULO, y eso significa algo")
  void elNombreDeUsuarioLlegaDespues() {
    vincular(ana, broker, "12345");

    // Nulo = «el broker todavía no lo ha confirmado». Lo rellenará el webhook
    // de `RF-SP-054`. Con la columna obligatoria habría que inventar un valor
    // en el alta, y el inventado sobreviviría a la confirmación.
    assertThat(
            jdbc.queryForObject(
                "SELECT broker_username FROM user_brokers WHERE external_id = '12345'",
                String.class))
        .isNull();

    jdbc.update("UPDATE user_brokers SET broker_username = 'ana.ruiz' WHERE external_id = '12345'");

    assertThat(
            jdbc.queryForObject(
                "SELECT broker_username FROM user_brokers WHERE external_id = '12345'",
                String.class))
        .isEqualTo("ana.ruiz");
  }

  @Test
  @DisplayName("una cuenta no puede apuntar a un broker que no existe")
  void elBrokerTieneQueExistir() {
    assertThatThrownBy(() -> vincular(ana, UUID.randomUUID(), "12345"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void vincular(UUID persona, UUID broker, String identificador) {
    jdbc.update(
        "INSERT INTO user_brokers (id, user_id, broker_id, external_id)"
            + " VALUES (CAST(? AS uuid), CAST(? AS uuid), CAST(? AS uuid), ?)",
        UUID.randomUUID().toString(),
        persona.toString(),
        broker.toString(),
        identificador);
  }

  private int cuantas(UUID persona) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM user_brokers WHERE user_id = CAST(? AS uuid)",
        Integer.class,
        persona.toString());
  }

  private UUID broker(String nombre) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO brokers (id, name) VALUES (CAST(? AS uuid), ?)", id.toString(), nombre);
    return id;
  }

  private UUID persona(String usuario) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, username, email, first_name, last_name, password_hash, status,"
            + " country_id)"
            + " VALUES (CAST(? AS uuid), ?, ?, 'Ana', 'Ruiz', 'x', 'ACTIVO',"
            + " (SELECT id FROM countries ORDER BY code LIMIT 1))",
        id.toString(),
        usuario,
        usuario + "@nexus.test");
    return id;
  }

  private void limpiar() {
    jdbc.update("DELETE FROM user_brokers");
    jdbc.update("DELETE FROM brokers");
    jdbc.update(
        "DELETE FROM user_memberships WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'ub-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'ub-%'");
  }
}
