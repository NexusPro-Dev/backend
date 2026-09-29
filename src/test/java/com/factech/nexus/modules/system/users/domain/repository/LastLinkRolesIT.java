package com.factech.nexus.modules.system.users.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.system.users.application.LastLinkRoles;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Los roles del último eslabón (`RN-CM-045`, `RF-CM-013` `T-15`).
 *
 * <p>Se definen por la forma de la jerarquía: la sembrada devuelve `AGENTE`, y un rol vendedor que
 * cuelgue de él lo saca del conjunto sin tocar código.
 */
class LastLinkRolesIT extends IntegrationTestBase {

  @Autowired private LastLinkRoles ultimoEslabon;
  @Autowired private JdbcTemplate jdbc;

  @AfterEach
  void limpiar() {
    jdbc.update("DELETE FROM roles WHERE code = 'LL_APRENDIZ'");
  }

  @Test
  @DisplayName("la jerarquía sembrada tiene un solo último eslabón: AGENTE")
  void laSembrada() {
    assertThat(ultimoEslabon.ids()).containsExactly(rol("AGENTE"));
  }

  @Test
  @DisplayName("un rol vendedor bajo AGENTE lo convierte en superior; retirado, deja de contar")
  void laFormaYNoElCodigo() {
    UUID aprendiz = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO roles (id, code, name, role_type, parent_role_id)"
            + " VALUES (?, 'LL_APRENDIZ', 'Aprendiz', 'VENDEDOR', ?)",
        aprendiz,
        rol("AGENTE"));

    assertThat(ultimoEslabon.ids()).containsExactly(aprendiz);

    jdbc.update("UPDATE roles SET deleted_at = now() WHERE id = ?", aprendiz);
    assertThat(ultimoEslabon.ids()).containsExactly(rol("AGENTE"));
  }

  private UUID rol(String codigo) {
    return jdbc.queryForObject("SELECT id FROM roles WHERE code = ?", UUID.class, codigo);
  }
}
