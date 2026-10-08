package com.factech.nexus.modules.movements.domain.models;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.movements.PayoutFixtures;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verificación de {@code V88__mv_entidades_de_cobro_de_colombia.sql} (`RF-MV-032` · `T-11`).
 *
 * <p><b>No lee lo que dejó Flyway</b>: las suites de las cuentas de cobro vacían el catálogo
 * ({@link PayoutFixtures#limpiar}) y no lo reponen, así que lo sembrado al migrar puede haber
 * desaparecido según el orden de la suite. Esta clase vacía el catálogo y <b>aplica el guion</b>,
 * que es idempotente por diseño (`plan.md` §2).
 */
class PayoutInstitutionsSeedIT extends IntegrationTestBase {

  private static final String AUDITORIAS_DE_LA_SIEMBRA =
      "entity = 'payout_institutions' AND id::text LIKE '01a10e82-9000-7013-9c4f-5e7ad70009%'";

  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void vaciar() {
    limpiar();
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  @Test
  @DisplayName(
      "CA-MV-703 — la siembra trae las 26 de Colombia, activas y con su código ACH; repetirla no"
          + " duplica nada")
  void siembra() throws Exception {
    aplicar();

    assertThat(contar("country_id = (SELECT id FROM countries WHERE code = 'COL')")).isEqualTo(26);
    assertThat(contar("kind = 'BANCO'")).isEqualTo(22);
    assertThat(contar("kind = 'BILLETERA_MOVIL'")).isEqualTo(4);
    assertThat(contar("NOT is_active")).isZero();
    assertThat(nombreYTipo("1007")).isEqualTo("Bancolombia|BANCO");
    assertThat(nombreYTipo("1051")).isEqualTo("Davivienda|BANCO");
    assertThat(nombreYTipo("1507")).isEqualTo("Nequi|BILLETERA_MOVIL");
    assertThat(nombreYTipo("1551")).isEqualTo("Daviplata|BILLETERA_MOVIL");
    assertThat(auditorias()).isEqualTo(26);

    aplicar();

    assertThat(contar("true")).isEqualTo(26);
    assertThat(auditorias()).isEqualTo(26);
  }

  @Test
  @DisplayName("CA-MV-703 — la siembra no pisa una entidad que ya existía con el mismo código")
  void noPisa() throws Exception {
    UUID previa = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO payout_institutions (id, code, name, kind, country_id, is_active)
        VALUES (?, '1007', 'Bancolombia S.A.', 'BANCO',
                (SELECT id FROM countries WHERE code = 'COL'), false)
        """,
        previa);

    aplicar();

    Map<String, Object> fila =
        jdbc.queryForMap("SELECT id, name, is_active FROM payout_institutions WHERE code = '1007'");
    assertThat(fila)
        .containsEntry("id", previa)
        .containsEntry("name", "Bancolombia S.A.")
        .containsEntry("is_active", false);
    assertThat(contar("true")).isEqualTo(26);
    assertThat(auditorias()).isEqualTo(25);
  }

  private void aplicar() throws Exception {
    jdbc.execute(
        new String(
            new ClassPathResource("db/migration/V88__mv_entidades_de_cobro_de_colombia.sql")
                .getInputStream()
                .readAllBytes(),
            StandardCharsets.UTF_8));
  }

  private int contar(String condicion) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM payout_institutions WHERE " + condicion, Integer.class);
  }

  private String nombreYTipo(String codigo) {
    return jdbc.queryForObject(
        "SELECT name || '|' || kind FROM payout_institutions WHERE code = ?", String.class, codigo);
  }

  private int auditorias() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM audit_change_log WHERE " + AUDITORIAS_DE_LA_SIEMBRA, Integer.class);
  }

  /** Las del Flyway del arranque también: su identificador es el mismo que el de esta prueba. */
  private void limpiar() {
    PayoutFixtures.limpiar(jdbc);
    jdbc.update("DELETE FROM audit_change_log WHERE " + AUDITORIAS_DE_LA_SIEMBRA);
  }
}
