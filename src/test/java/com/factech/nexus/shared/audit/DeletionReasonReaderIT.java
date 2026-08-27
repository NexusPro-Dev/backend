package com.factech.nexus.shared.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.IntegrationTestBase;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * La lectura estrecha del motivo de una eliminación (`RF-PM-003` · `T-01` y `T-02`).
 *
 * <p><b>`T-02` es la prueba que importa</b>, y no el camino feliz: un puerto de lectura sobre la
 * auditoría puede convertirse en su puerta trasera, y lo único que lo impide es que la clave por la
 * que busca <b>incluya de quién es la fila</b>. Sin esta prueba, alguien podría relajar el
 * predicado para «reutilizar» el componente desde otro módulo y nadie se enteraría.
 *
 * <p>Las filas se siembran directamente en {@code audit_deletion_log} y no a través de una
 * eliminación real. Es deliberado: lo que se prueba es <b>la lectura</b>, y hacerla depender de un
 * caso de uso que la escriba —`RF-PM-006`, que todavía no existe— ataría esta prueba a otro
 * requerimiento sin comprobar nada más.
 */
class DeletionReasonReaderIT extends IntegrationTestBase {

  private static final OffsetDateTime AHORA =
      OffsetDateTime.of(2026, 8, 27, 12, 0, 0, 0, ZoneOffset.UTC);

  @Autowired private DeletionReasonReader lector;
  @Autowired private JdbcTemplate jdbc;

  private final List<UUID> sembrados = new ArrayList<>();

  @AfterEach
  void limpiarLoSembrado() {
    // SOLO las filas que sembró esta prueba, por su identificador. Borrar por
    // entidad —«todo lo de `users`»— se llevaría por delante lo que escriben
    // las pruebas de auditoría de `SP`, y el fallo aparecería en ellas.
    sembrados.forEach(
        id ->
            jdbc.update(
                "DELETE FROM audit_deletion_log WHERE entity_id = CAST(? AS uuid)", id.toString()));
    sembrados.clear();
  }

  @Test
  @DisplayName("`T-01` — devuelve el motivo LITERAL de una eliminación registrada")
  void devuelveElMotivo() {
    UUID producto = UUID.randomUUID();
    registrarEliminacion("PM", "products", producto, "LOGICAL", "Se descontinuó la línea.");

    assertThat(lector.reasonFor("PM", "products", producto)).contains("Se descontinuó la línea.");
  }

  @Test
  @DisplayName("`T-01` — devuelve VACÍO si esa entidad no tiene ninguna eliminación registrada")
  void vacioSinEliminacion() {
    assertThat(lector.reasonFor("PM", "products", UUID.randomUUID())).isEmpty();
  }

  @Test
  @DisplayName("`T-02` — pedir el motivo de una entidad de OTRO módulo no devuelve nada")
  void noAlcanzaLoAjeno() {
    UUID persona = UUID.randomUUID();
    registrarEliminacion("SP", "users", persona, "LOGICAL", "Dejó la organización.");

    // El mismo identificador, preguntado desde `PM`: la fila existe y no se
    // alcanza. No hay comprobación de permisos de por medio —no la hay— sino
    // que el módulo y la entidad son parte de la pregunta.
    assertThat(lector.reasonFor("PM", "products", persona)).isEmpty();
    assertThat(lector.reasonFor("PM", "users", persona)).isEmpty();
    assertThat(lector.reasonFor("SP", "products", persona)).isEmpty();

    // Y desde el suyo sí, que es lo que demuestra que la fila estaba ahí.
    assertThat(lector.reasonFor("SP", "users", persona)).contains("Dejó la organización.");
  }

  @Test
  @DisplayName(
      "una eliminación de ASOCIACIÓN no aporta motivo, y no tapa al de la eliminación real")
  void laAsociacionNoTapaAlMotivo() {
    UUID producto = UUID.randomUUID();
    registrarEliminacion("PM", "products", producto, "LOGICAL", "Se descontinuó la línea.");
    // Registrada DESPUÉS y sin motivo: el Art. V.13 no se lo exige. Sin el
    // filtro por tipo, esta ganaría el `ORDER BY occurred_at DESC` y la lectura
    // devolvería vacío teniendo el motivo delante.
    registrarAsociacion("PM", "products", producto);

    assertThat(lector.reasonFor("PM", "products", producto)).contains("Se descontinuó la línea.");
  }

  @Test
  @DisplayName("con dos eliminaciones registradas gana la más reciente")
  void ganaLaMasReciente() {
    UUID producto = UUID.randomUUID();
    registrarEliminacion("PM", "products", producto, "LOGICAL", "El primero.", AHORA.minusDays(1));
    registrarEliminacion("PM", "products", producto, "LOGICAL", "El segundo.", AHORA);

    assertThat(lector.reasonFor("PM", "products", producto)).contains("El segundo.");
  }

  @Test
  @DisplayName("los nulos no revientan la consulta: devuelven vacío")
  void nulosDevuelvenVacio() {
    assertThat(lector.reasonFor(null, "products", UUID.randomUUID())).isEmpty();
    assertThat(lector.reasonFor("PM", null, UUID.randomUUID())).isEmpty();
    assertThat(lector.reasonFor("PM", "products", null)).isEmpty();
  }

  // ---------------------------------------------------------------------------

  /** Anota el identificador para que {@link #limpiarLoSembrado} sepa qué borrar, y lo devuelve. */
  private String registrado(UUID entidadId) {
    sembrados.add(entidadId);
    return entidadId.toString();
  }

  private void registrarEliminacion(
      String modulo, String entidad, UUID entidadId, String tipo, String motivo) {
    registrarEliminacion(modulo, entidad, entidadId, tipo, motivo, AHORA);
  }

  private void registrarEliminacion(
      String modulo,
      String entidad,
      UUID entidadId,
      String tipo,
      String motivo,
      OffsetDateTime cuando) {

    jdbc.update(
        "INSERT INTO audit_deletion_log (id, occurred_at, module, entity, entity_id,"
            + " deletion_type, reason, snapshot)"
            + " VALUES (CAST(? AS uuid), ?, ?, ?, CAST(? AS uuid), ?, ?, CAST(? AS jsonb))",
        UUID.randomUUID().toString(),
        cuando,
        modulo,
        entidad,
        registrado(entidadId),
        tipo,
        motivo,
        "{}");
  }

  /** Una eliminación de asociación: sin motivo, que es lo que el Art. V.13 le permite. */
  private void registrarAsociacion(String modulo, String entidad, UUID entidadId) {
    jdbc.update(
        "INSERT INTO audit_deletion_log (id, occurred_at, module, entity, entity_id,"
            + " deletion_type, reason, snapshot)"
            + " VALUES (CAST(? AS uuid), ?, ?, ?, CAST(? AS uuid), 'ASSOCIATION', NULL,"
            + " CAST(? AS jsonb))",
        UUID.randomUUID().toString(),
        AHORA.plusHours(1),
        modulo,
        entidad,
        registrado(entidadId),
        "{}");
  }
}
