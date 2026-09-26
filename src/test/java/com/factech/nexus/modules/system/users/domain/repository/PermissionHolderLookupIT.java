package com.factech.nexus.modules.system.users.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.system.users.application.PermissionHolderLookup;
import com.factech.nexus.shared.security.EffectivePermissions;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * El puerto «¿porta este permiso?» que `SP` publica a petición de `RF-AC-008` · `T-03`.
 *
 * <p>Lo que se prueba no es que la consulta funcione: es que <b>diga lo mismo que {@link
 * EffectivePermissions}</b> en los tres casos que separan «portar» de «tener una fila» — rol
 * inactivo, rol retirado, persona retirada—, porque la constante que comparten existe para eso y
 * una prueba de cada lado es lo que la mantiene compartida.
 */
class PermissionHolderLookupIT extends IntegrationTestBase {

  private static final String PERMISO = "audit:read-changes";

  @Autowired private PermissionHolderLookup puerto;
  @Autowired private EffectivePermissions efectivos;
  @Autowired private JdbcTemplate jdbc;

  private UUID rol;
  private UUID persona;

  @BeforeEach
  void sembrar() {
    rol = crearRolAcotado(jdbc, "PORTA_PERMISO_" + System.nanoTime() % 1000000, "Porta");
    persona = persona("phl-" + UUID.randomUUID().toString().substring(0, 8));
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT ?, id, role_type FROM roles WHERE id = ?",
        persona,
        rol);
  }

  @AfterEach
  void limpiar() {
    jdbc.update("DELETE FROM user_roles WHERE role_id = ?", rol);
    jdbc.update("DELETE FROM role_permissions WHERE role_id = ?", rol);
    jdbc.update("DELETE FROM roles WHERE id = ?", rol);
    jdbc.update("DELETE FROM user_products WHERE user_id = ?", persona);
    jdbc.update("DELETE FROM users WHERE id = ?", persona);
  }

  @Test
  @DisplayName("porta por un rol vivo y activo: verdadero, y coincide con los permisos efectivos")
  void portaPorUnRolActivo() {
    assertThat(puerto.holds(persona, PERMISO)).isTrue();
    assertThat(efectivos.forUser(persona).orElseThrow()).contains(PERMISO);
  }

  @Test
  @DisplayName("un rol INACTIVO no concede: falso, como en los permisos efectivos")
  void rolInactivo() {
    jdbc.update("UPDATE roles SET status = 'INACTIVO' WHERE id = ?", rol);
    assertThat(puerto.holds(persona, PERMISO)).isFalse();
    assertThat(efectivos.forUser(persona).orElseThrow()).doesNotContain(PERMISO);
  }

  @Test
  @DisplayName("un rol retirado no concede: falso")
  void rolRetirado() {
    jdbc.update("UPDATE roles SET deleted_at = now() WHERE id = ?", rol);
    assertThat(puerto.holds(persona, PERMISO)).isFalse();
  }

  @Test
  @DisplayName("una persona retirada no porta nada: falso")
  void personaRetirada() {
    jdbc.update("UPDATE users SET deleted_at = now() WHERE id = ?", persona);
    assertThat(puerto.holds(persona, PERMISO)).isFalse();
  }

  @Test
  @DisplayName("persona inexistente, permiso inexistente, nulos y blancos: falso, sin fallar")
  void loQueNoExiste() {
    assertThat(puerto.holds(UUID.randomUUID(), PERMISO)).isFalse();
    assertThat(puerto.holds(persona, "courses:no-existe")).isFalse();
    assertThat(puerto.holds(persona, "audit:read-errors")).isFalse();
    assertThat(puerto.holds(null, PERMISO)).isFalse();
    assertThat(puerto.holds(persona, null)).isFalse();
    assertThat(puerto.holds(persona, "  ")).isFalse();
  }

  private UUID persona(String usuario) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?, ?, ?, 'Porta', 'Permiso', 'no-se-usa-en-esta-prueba', false, 'ACTIVO',
                (SELECT id FROM countries ORDER BY code LIMIT 1))
        """,
        id,
        usuario,
        usuario + "@nexus.test");
    darElSuelo(jdbc, id);
    return id;
  }
}
