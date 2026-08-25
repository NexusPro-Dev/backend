package com.factech.nexus.modules.system.auth.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.IntegrationTestBase;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * La purga de sesiones caducadas (issue #25).
 *
 * <p>Lo que estas pruebas vigilan no es que borre —eso es lo fácil—, sino <b>que no borre de
 * más</b> y que no reviente contra la cadena de rotación. `RF-SP-035` detecta el robo por
 * reutilización leyendo filas revocadas: una purga demasiado ansiosa apaga esa alarma sin que nadie
 * se entere.
 */
class RefreshTokenPurgeIT extends IntegrationTestBase {

  @Autowired private PurgeExpiredTokensService purga;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void empezarLimpio() {
    jdbc.update("UPDATE refresh_tokens SET replaced_by_id = NULL");
    jdbc.update("DELETE FROM refresh_tokens");
    jdbc.update("DELETE FROM audit_security_log WHERE event_type = 'SESSION_TOKENS_PURGED'");
  }

  @Test
  @DisplayName("una familia caducada hace mucho se va entera")
  void laFamiliaCaducadaSeVa() {
    UUID familia = sembrarFamilia(dias(-100), dias(-90));

    assertThat(purga.purgar()).isEqualTo(2);
    assertThat(quedan(familia)).isZero();
  }

  @Test
  @DisplayName("la cadena de rotación no bloquea el borrado, aunque la clave foránea sea RESTRICT")
  void laCadenaDeRotacionNoBloquea() {
    // Es el fallo que un `DELETE` directo produce: `fk_refresh_tokens_replaced_by`
    // se comprueba fila a fila, de modo que borrar el primer token de la cadena
    // falla porque el segundo todavía lo apunta. Si esta prueba pasa, es que los
    // punteros se anulan antes.
    UUID familia = sembrarFamilia(dias(-100), dias(-90));
    List<UUID> ids =
        jdbc.queryForList("SELECT id FROM refresh_tokens ORDER BY expires_at", UUID.class);
    jdbc.update(
        "UPDATE refresh_tokens SET replaced_by_id = ? WHERE id = ?", ids.get(1), ids.get(0));

    assertThat(purga.purgar()).isEqualTo(2);
    assertThat(quedan(familia)).isZero();
  }

  @Test
  @DisplayName(
      "una familia con un token todavía vigente NO se toca, aunque casi toda esté revocada")
  void laFamiliaVivaSeRespeta() {
    // El caso de una sesión real: veinte rotaciones revocadas hace semanas y un
    // token vigente. Contar el plazo desde la revocación se llevaría por delante
    // la sesión de alguien que está trabajando.
    UUID familia = sembrarFamilia(dias(-100), dias(30));

    assertThat(purga.purgar()).isZero();
    assertThat(quedan(familia)).isEqualTo(2);
  }

  @Test
  @DisplayName("una familia caducada AYER se conserva: el plazo protege la alarma de robo")
  void laFamiliaRecienCaducadaSeConserva() {
    // Aquí vive la detección de robo por reutilización de `RF-SP-035`. Con
    // retención de treinta días, lo de ayer no se toca.
    UUID familia = sembrarFamilia(dias(-40), dias(-1));

    assertThat(purga.purgar()).isZero();
    assertThat(quedan(familia)).isEqualTo(2);
  }

  @Test
  @DisplayName("deja constancia de cuánto borró: una purga sin apunte no es auditable")
  void laPurgaSeAudita() {
    sembrarFamilia(dias(-100), dias(-90));
    purga.purgar();

    Map<String, Object> evento =
        jdbc.queryForMap(
            "SELECT * FROM audit_security_log WHERE event_type = 'SESSION_TOKENS_PURGED'"
                + " ORDER BY occurred_at DESC LIMIT 1");

    assertThat(evento.get("severity")).isEqualTo("INFORMATIVA");
    assertThat(evento.get("outcome")).isEqualTo("SUCCESS");
    // Nadie lo hizo: lo hizo el sistema. Un actor inventado sería peor que
    // ninguno.
    assertThat(evento.get("actor_id")).isNull();
    assertThat(evento.get("detail").toString()).contains("\"deletedRows\": 2");
    // La identidad de quien tenía esas sesiones no aporta nada a la pregunta
    // que este evento responde, y convertiría el mantenimiento en un rastro de
    // quién usó el sistema.
    assertThat(evento.get("target_user_id")).isNull();
  }

  @Test
  @DisplayName("sin nada que purgar no escribe ningún evento: el silencio también es información")
  void sinNadaQuePurgarNoAudita() {
    sembrarFamilia(dias(-40), dias(-1));

    assertThat(purga.purgar()).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_security_log WHERE event_type = 'SESSION_TOKENS_PURGED'",
                Long.class))
        .isZero();
  }

  /**
   * Dos tokens de una misma familia: uno rotado y el siguiente, con las caducidades que se piden.
   */
  private UUID sembrarFamilia(Instant primerVencimiento, Instant ultimoVencimiento) {
    UUID familia = UUID.randomUUID();
    insertar(familia, primerVencimiento, "ROTACION");
    insertar(familia, ultimoVencimiento, null);
    return familia;
  }

  private void insertar(UUID familia, Instant vence, String motivoRevocacion) {
    jdbc.update(
        """
        INSERT INTO refresh_tokens
            (id, user_id, token_hash, family_id, family_started_at, expires_at,
             revoked_at, revoked_reason, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        UUID.randomUUID(),
        SUPERADMIN,
        "hash-" + UUID.randomUUID(),
        familia,
        java.sql.Timestamp.from(dias(-120)),
        java.sql.Timestamp.from(vence),
        motivoRevocacion == null ? null : java.sql.Timestamp.from(dias(-119)),
        motivoRevocacion,
        // `ck_refresh_tokens_periodo` exige que la caducidad sea posterior a la
        // creación, de modo que la siembra no puede nacer «hoy» con un
        // vencimiento de hace cien días.
        java.sql.Timestamp.from(dias(-120)));
  }

  private static Instant dias(int cuantos) {
    return Instant.now().plus(cuantos, ChronoUnit.DAYS);
  }

  private long quedan(UUID familia) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM refresh_tokens WHERE family_id = ?", Long.class, familia);
  }
}
