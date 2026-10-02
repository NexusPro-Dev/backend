package com.factech.nexus.modules.movements;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Siembra y limpieza de las cuentas de cobro (`RF-MV-032` a `RF-MV-039`).
 *
 * <p><b>Desde el 01-10-2026 un retiro exige una cuenta de cobro</b> (`RN-MV-056`): toda suite que
 * pide retiros prepara a la persona con {@link #listaParaRetirar}, que le da un documento y una
 * cuenta principal en una entidad activa de su país.
 */
public final class PayoutFixtures {

  private PayoutFixtures() {}

  /** Una entidad de Colombia, con un código único y empezado por {@code Z}. */
  public static UUID entidad(JdbcTemplate jdbc, String tipo, boolean activa) {
    UUID id = UUID.randomUUID();
    String codigo =
        "Z"
            + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase(Locale.ROOT);
    jdbc.update(
        """
        INSERT INTO payout_institutions (id, code, name, kind, country_id, is_active)
        VALUES (?, ?, ?, ?, (SELECT id FROM countries WHERE code = 'COL'), ?)
        """,
        id,
        codigo,
        "Entidad " + codigo,
        tipo,
        activa);
    return id;
  }

  /** Le da a la persona un documento CC, si no lo tenía. */
  public static void documento(JdbcTemplate jdbc, UUID persona) {
    jdbc.update(
        """
        UPDATE users
           SET document_type_id = (SELECT id FROM document_types WHERE abbreviation = 'CC'),
               document_number = ?
         WHERE id = ? AND document_number IS NULL
        """,
        String.valueOf(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L)),
        persona);
  }

  /** Una cuenta de ahorros viva, principal o no, en esa entidad. */
  public static UUID cuenta(
      JdbcTemplate jdbc, UUID persona, UUID entidad, String numero, boolean principal) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO payout_accounts (id, user_id, institution_id, account_type, number,
                                     is_principal)
        VALUES (?, ?, ?, 'AHORROS', ?, ?)
        """,
        id,
        persona,
        entidad,
        numero,
        principal);
    return id;
  }

  /** Documento y una cuenta principal en un banco activo: lo que pide un retiro. */
  public static UUID listaParaRetirar(JdbcTemplate jdbc, UUID persona) {
    documento(jdbc, persona);
    // Idempotente: si ya tiene principal, esa sirve.
    return jdbc
        .queryForList(
            "SELECT id FROM payout_accounts WHERE user_id = ? AND is_principal"
                + " AND deleted_at IS NULL",
            UUID.class,
            persona)
        .stream()
        .findFirst()
        .orElseGet(() -> cuenta(jdbc, persona, entidad(jdbc, "BANCO", true), "1234567890", true));
  }

  /** Las cuentas (y con ellas las copias de los retiros) y después las entidades. */
  public static void limpiar(JdbcTemplate jdbc) {
    jdbc.update("DELETE FROM payout_accounts");
    jdbc.update("DELETE FROM payout_institutions");
  }
}
