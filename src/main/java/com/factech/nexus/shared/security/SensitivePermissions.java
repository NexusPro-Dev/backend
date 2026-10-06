package com.factech.nexus.shared.security;

import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Los permisos cuyas operaciones son sensibles (`RN-SP-063`): {@code
 * permissions.requires_recent_mfa}.
 *
 * <p><b>Se leen una vez</b> (`RF-SP-073` · `plan.md` §1): el catálogo solo cambia por migración, y
 * una migración solo entra con un despliegue, que reinicia. Consultar la tabla en cada petición no
 * compraría nada.
 *
 * <p><b>La primera vez que se necesitan y no al arrancar</b>: al crearse este componente Flyway
 * puede no haber migrado todavía, y una lectura temprana encontraría la columna sin crear.
 */
@Component
public class SensitivePermissions {

  private final JdbcTemplate jdbc;
  private volatile Set<String> codigos;

  public SensitivePermissions(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public boolean esSensible(String permiso) {
    return permiso != null && codigos().contains(permiso);
  }

  public Set<String> codigos() {
    Set<String> leidos = codigos;
    if (leidos == null) {
      leidos =
          Set.copyOf(
              jdbc.queryForList(
                  "SELECT code FROM permissions WHERE requires_recent_mfa", String.class));
      codigos = leidos;
    }
    return leidos;
  }
}
