package com.factech.nexus.modules.system.auth.domain.service;

import com.factech.nexus.modules.system.auth.domain.models.LockoutPolicy;
import com.factech.nexus.modules.system.auth.domain.repository.AuthUser;
import com.factech.nexus.modules.system.auth.domain.repository.AuthUserRepository;
import com.factech.nexus.modules.system.auth.domain.service.FailedAttemptLedger.Fallos;
import com.factech.nexus.shared.audit.AuditEnums.Outcome;
import com.factech.nexus.shared.audit.AuditEnums.SecurityEventType;
import com.factech.nexus.shared.audit.AuditEnums.Severity;
import com.factech.nexus.shared.audit.AuditEvents.SecurityEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.UnauthorizedException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * La contabilidad de un fallo de credencial: el contador, el bloqueo, su auditoría y el rechazo con
 * los intentos que quedan (`RF-SP-034`; extraída el 06-10-2026 por `RF-SP-072` · `plan.md` §1).
 *
 * <p><b>Existe para que la contraseña y el código del segundo factor cuenten en el MISMO contador
 * con la MISMA política</b> (`RN-SP-059`). Escrita dos veces, la segunda copia acabaría con otro
 * umbral —o sin techo—, y quien ya tiene la contraseña sumaría los intentos de las dos.
 *
 * <p>El código de {@link #rechazar} y {@link #credencialesInvalidas} es el que vivía en {@code
 * LoginService}, sin cambios: su argumentación —por qué el identificador sin cuenta también cuenta,
 * por qué el bloqueo se audita en el intento que lo provoca, por qué la espera viaja como dato—
 * está en `RF-SP-034` y no se repite.
 */
@Component
public class CredentialFailures {

  private final AuthUserRepository cuentas;
  private final FailedAttemptLedger sinCuenta;
  private final AuditWriter auditoria;
  private final int intentosParaBloquear;
  private final LockoutPolicy politica;

  public CredentialFailures(
      AuthUserRepository cuentas,
      FailedAttemptLedger sinCuenta,
      AuditWriter auditoria,
      @Value("${nexus.security.lockout.max-attempts:5}") int intentosParaBloquear,
      @Value("${nexus.security.lockout.base-delay:PT1M}") Duration bloqueoBase,
      @Value("${nexus.security.lockout.max-delay:PT1H}") Duration bloqueoMaximo) {
    this.cuentas = cuentas;
    this.sinCuenta = sinCuenta;
    this.auditoria = auditoria;
    this.intentosParaBloquear = intentosParaBloquear;
    this.politica = new LockoutPolicy(intentosParaBloquear, bloqueoBase, bloqueoMaximo);
  }

  /** Un fallo de contraseña (`RF-SP-034` `EX-001`): anota, audita y construye el rechazo. */
  public UnauthorizedException rechazar(
      Optional<AuthUser> encontrada,
      Fallos anonimos,
      String identificador,
      String motivo,
      OffsetDateTime ahora) {

    int intentos = encontrada.map(AuthUser::failedAttempts).orElse(anonimos.intentos()) + 1;
    OffsetDateTime hasta = politica.bloqueoTras(intentos, ahora).orElse(null);

    if (encontrada.isPresent()) {
      cuentas.registrarFallo(encontrada.get().id(), intentos, hasta);
    } else {
      sinCuenta.registrarFallo(identificador, intentos, hasta, ahora);
    }

    auditarFallo(encontrada.orElse(null), identificador, motivo, hasta != null);
    return credencialesInvalidas(Math.max(0, intentosParaBloquear - intentos), hasta, ahora);
  }

  /**
   * Un código del segundo factor rechazado (`RN-SP-059`): consume un intento <b>de la cuenta</b>,
   * en el mismo contador que la contraseña.
   *
   * <p>Se audita como {@code MFA_VERIFICATION_FAILED} y no como fallo de inicio de sesión: quien
   * falla aquí <b>acertó la contraseña</b> (`security.md` §8.1). Si el fallo bloquea, el evento es
   * {@code ACCOUNT_LOCKED}, como siempre.
   *
   * @param etapa {@code LOGIN} o {@code STEP_UP}, para el detalle del evento
   */
  public FalloDeCodigo rechazarCodigo(AuthUser cuenta, String etapa, OffsetDateTime ahora) {
    Map<String, Object> detalle = new HashMap<>();
    detalle.put("stage", etapa);
    return registrarFallo(
        cuenta, SecurityEventType.MFA_VERIFICATION_FAILED, Severity.MEDIA, detalle, ahora);
  }

  /**
   * Un fallo de credencial de quien ya está autenticado —el código al reverificar, la contraseña al
   * desactivar el factor (`RF-SP-075`)—: consume un intento de la cuenta y se audita con el evento
   * que diga quien llama, o {@code ACCOUNT_LOCKED} si bloquea.
   */
  public FalloDeCodigo registrarFallo(
      AuthUser cuenta,
      SecurityEventType evento,
      Severity severidad,
      Map<String, Object> detalle,
      OffsetDateTime ahora) {
    int intentos = cuenta.failedAttempts() + 1;
    OffsetDateTime hasta = politica.bloqueoTras(intentos, ahora).orElse(null);
    cuentas.registrarFallo(cuenta.id(), intentos, hasta);

    boolean bloquea = hasta != null;
    auditoria.recordSecurity(
        new SecurityEvent(
            bloquea ? SecurityEventType.ACCOUNT_LOCKED : evento,
            bloquea ? Severity.ALTA : severidad,
            Outcome.FAILURE,
            cuenta.id(),
            detalle));

    return new FalloDeCodigo(Math.max(0, intentosParaBloquear - intentos), hasta);
  }

  /** Todo intento fallido se audita (`security.md` §3.2). El actor es nulo: no hay identidad. */
  public void auditarFallo(
      AuthUser cuenta, String identificador, String motivo, boolean bloqueada) {
    Map<String, Object> detalle = new HashMap<>();
    detalle.put("identifier", identificador);
    detalle.put("reason", motivo);

    boolean bloqueoDeCuenta = bloqueada && cuenta != null;

    auditoria.recordSecurity(
        new SecurityEvent(
            bloqueoDeCuenta ? SecurityEventType.ACCOUNT_LOCKED : SecurityEventType.LOGIN_FAILURE,
            bloqueoDeCuenta ? Severity.ALTA : Severity.MEDIA,
            Outcome.FAILURE,
            cuenta == null ? null : cuenta.id(),
            detalle));
  }

  public LockoutPolicy politica() {
    return politica;
  }

  /** Un solo mensaje para todos los rechazos de credencial, sin diferencia observable. */
  public static UnauthorizedException credencialesInvalidas(
      int restantes, OffsetDateTime hasta, OffsetDateTime ahora) {

    Map<String, Object> extensiones = new HashMap<>();
    extensiones.put("remainingAttempts", restantes);

    if (hasta == null) {
      String aviso =
          restantes == 1
              ? " Le queda 1 intento antes de que la cuenta se bloquee."
              : " Le quedan " + restantes + " intentos antes de que la cuenta se bloquee.";
      return new UnauthorizedException(
          "VAL-003", "Las credenciales no son válidas." + aviso, extensiones);
    }

    Duration espera = Duration.between(ahora, hasta);
    extensiones.put("unlockAt", hasta);
    extensiones.put("retryAfterSeconds", Math.max(0, espera.toSeconds()));

    return new UnauthorizedException(
        "VAL-003",
        "Las credenciales no son válidas. La cuenta ha quedado bloqueada temporalmente.",
        extensiones);
  }

  /**
   * Lo que dejó un código rechazado: cuántos intentos le quedan a la cuenta y, si se bloqueó, hasta
   * cuándo.
   */
  public record FalloDeCodigo(int restantes, OffsetDateTime bloqueadaHasta) {
    public boolean bloqueo() {
      return bloqueadaHasta != null;
    }
  }
}
