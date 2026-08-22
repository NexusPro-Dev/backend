package com.factech.nexus.shared.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEnums.ErrorType;
import com.factech.nexus.shared.audit.AuditEnums.Outcome;
import com.factech.nexus.shared.audit.AuditEnums.SecurityEventType;
import com.factech.nexus.shared.audit.AuditEnums.Severity;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditEvents.ErrorEvent;
import com.factech.nexus.shared.audit.AuditEvents.SecurityEvent;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Mecánica transaccional de la auditoría (`RF-SP-001` · `T-07`, `T-08`).
 *
 * <p>Es la parte del diseño que no se puede comprobar leyendo el código: qué sobrevive a un {@code
 * rollback} y qué no. Y es también la que más importa, porque de ella depende que la auditoría no
 * deje huecos ni invente hechos.
 */
class AuditTransaccionalidadIT extends IntegrationTestBase {

  @Autowired private AuditWriter auditoria;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transacciones;

  @Test
  @DisplayName("el registro de errores SOBREVIVE al rollback de la transacción de negocio")
  void elErrorSobreviveAlRollback() {
    // Un rechazo se registra precisamente MIENTRAS la transacción se revierte.
    // Escrito dentro de ella, el rollback borraría el evento que hay que
    // conservar: este es el caso para el que REQUIRES_NEW existe.
    String marca = "T08-" + UUID.randomUUID();

    assertThatThrownBy(
            () ->
                enTransaccion(
                    () -> {
                      auditoria.recordError(unError(marca));
                      throw new IllegalStateException("se revierte a propósito");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(contarErrores(marca)).isEqualTo(1);
  }

  @Test
  @DisplayName("el evento de seguridad NO se escribe si la transacción se revierte")
  void laSeguridadNoDejaEventoFantasma() {
    // Emitido antes del commit, una reversión dejaría un evento SUCCESS de una
    // operación que nunca ocurrió, y ese evento no se puede retirar porque su
    // transacción ya cerró.
    UUID rol = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                enTransaccion(
                    () -> {
                      auditoria.recordSecurityAfterCommit(unEventoDeSeguridad(rol));
                      throw new IllegalStateException("se revierte a propósito");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(contarEventosDeSeguridad(rol)).isZero();
  }

  @Test
  @DisplayName("al confirmarse, el evento de seguridad sí se escribe, y en transacción propia")
  void laSeguridadSeEscribeTrasElCommit() {
    UUID rol = UUID.randomUUID();

    enTransaccion(() -> auditoria.recordSecurityAfterCommit(unEventoDeSeguridad(rol)));

    assertThat(contarEventosDeSeguridad(rol)).isEqualTo(1);
  }

  @Test
  @DisplayName("el evento de cambio se revierte CON el alta: comparten transacción (Art. V.14)")
  void elCambioSeRevierteConElAlta() {
    UUID entidad = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                enTransaccion(
                    () -> {
                      auditoria.recordChange(
                          new ChangeEvent(
                              "SP", "roles", entidad, ChangeAction.CREATE, Map.of("code", "X")));
                      throw new IllegalStateException("se revierte a propósito");
                    }))
        .isInstanceOf(IllegalStateException.class);

    Integer filas =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_change_log WHERE entity_id = ?", Integer.class, entidad);
    assertThat(filas).isZero();
  }

  @Test
  @DisplayName("recordChange exige transacción abierta: fuera de una, falla al invocarse")
  void elCambioExigeTransaccion() {
    // MANDATORY y no REQUIRED: con REQUIRED, una llamada desde un punto sin
    // transacción abriría una propia y escribiría el evento de algo que
    // todavía podía revertirse. Así, ese error no llega a ejecutarse.
    assertThatThrownBy(
            () ->
                auditoria.recordChange(
                    new ChangeEvent(
                        "SP", "roles", UUID.randomUUID(), ChangeAction.CREATE, Map.of())))
        .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
  }

  @Test
  @DisplayName("sin transacción activa, el evento de seguridad se escribe de inmediato")
  void sinTransaccionSeEscribeYa() {
    UUID rol = UUID.randomUUID();

    auditoria.recordSecurityAfterCommit(unEventoDeSeguridad(rol));

    assertThat(contarEventosDeSeguridad(rol)).isEqualTo(1);
  }

  @Test
  @DisplayName("fuera de una petición HTTP, las tres columnas de origen quedan en nulo")
  void sinPeticionNoHayOrigen() {
    // Es como el esquema dice «no vino de la red» (Art. V.15). Estas pruebas
    // no pasan por el filtro de correlación, de modo que reproducen el caso de
    // una migración o una tarea programada.
    UUID rol = UUID.randomUUID();

    auditoria.recordSecurityAfterCommit(unEventoDeSeguridad(rol));

    Integer filas =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM audit_security_log
             WHERE detail->>'roleId' = ?
               AND correlation_id IS NULL AND ip_address IS NULL AND actor_id IS NULL
            """,
            Integer.class,
            rol.toString());
    assertThat(filas).isEqualTo(1);
  }

  // ---------------------------------------------------------------------------

  private void enTransaccion(Runnable cuerpo) {
    TransactionTemplate plantilla = new TransactionTemplate(transacciones);
    plantilla.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    plantilla.executeWithoutResult(estado -> cuerpo.run());
  }

  private static ErrorEvent unError(String marca) {
    return new ErrorEvent(
        "roles",
        null,
        "POST /api/v1/roles",
        "RN-SEG-003",
        ErrorType.BUSINESS_RULE,
        409,
        Severity.ALTA,
        marca);
  }

  private static SecurityEvent unEventoDeSeguridad(UUID rol) {
    return new SecurityEvent(
        SecurityEventType.ROLE_CREATED,
        Severity.ALTA,
        Outcome.SUCCESS,
        null,
        Map.of("roleId", rol.toString()));
  }

  private Integer contarErrores(String marca) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM audit_error_log WHERE message = ?", Integer.class, marca);
  }

  private Integer contarEventosDeSeguridad(UUID rol) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM audit_security_log WHERE detail->>'roleId' = ?",
        Integer.class,
        rol.toString());
  }
}
