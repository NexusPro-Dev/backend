package com.factech.nexus.modules.system.auth.domain.service;

import com.factech.nexus.modules.system.auth.application.RecoveryCodesResponse;
import com.factech.nexus.modules.system.auth.domain.models.MfaFactor;
import com.factech.nexus.modules.system.auth.domain.repository.MfaFactorRepository;
import com.factech.nexus.shared.audit.AuditEnums.Outcome;
import com.factech.nexus.shared.audit.AuditEnums.SecurityEventType;
import com.factech.nexus.shared.audit.AuditEnums.Severity;
import com.factech.nexus.shared.audit.AuditEvents.SecurityEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.UnauthorizedException;
import com.factech.nexus.shared.security.CurrentActor;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regenerar los propios códigos de recuperación (`RF-SP-074`, `RN-SP-061`).
 *
 * <p><b>{@link RecoveryCodeIssuer} precedido de un {@code UPDATE}</b> (`plan.md` §1): con el factor
 * activo bloqueado, se anulan sus códigos vigentes y se emiten diez. Se anulan <b>solo los
 * vigentes</b>: un código usado ya tiene su historia y no hay que reescribirla.
 *
 * <p><b>La verificación reciente no se comprueba aquí</b>: {@code
 * users:regenerate-own-recovery-codes} está marcado en `V75` y {@code RecentMfaInterceptor}
 * (`RF-SP-073`) responde antes de llegar a este servicio.
 */
@Service
public class RecoveryCodeRegenerationService {

  private final MfaFactorRepository factores;
  private final RecoveryCodeIssuer codigos;
  private final CurrentActor actor;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public RecoveryCodeRegenerationService(
      MfaFactorRepository factores,
      RecoveryCodeIssuer codigos,
      CurrentActor actor,
      AuditWriter auditoria) {
    this(factores, codigos, actor, auditoria, Clock.systemUTC());
  }

  RecoveryCodeRegenerationService(
      MfaFactorRepository factores,
      RecoveryCodeIssuer codigos,
      CurrentActor actor,
      AuditWriter auditoria,
      Clock reloj) {
    this.factores = factores;
    this.codigos = codigos;
    this.actor = actor;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public RecoveryCodesResponse regenerar() {
    OffsetDateTime ahora = OffsetDateTime.now(reloj);
    UUID quien =
        actor
            .currentActorId()
            .orElseThrow(() -> new UnauthorizedException("AUTH-001", "Se requiere autenticación."));

    // Con el factor bloqueado, dos regeneraciones simultáneas se ordenan y la
    // segunda anula los de la primera: lo mismo que hacerlas una detrás de otra.
    MfaFactor factor =
        factores
            .activoParaActualizar(quien)
            .orElseThrow(
                () -> new BusinessRuleException("VAL-001", "No tiene activado el segundo factor."));

    factores.anularCodigosVigentes(factor.getId(), ahora);
    List<String> nuevos = codigos.emitir(factor.getId(), ahora);

    // Sin detalle: cuántos se anularon no responde ninguna pregunta que importe,
    // y cuáles no se puede decir (`plan.md` §6).
    auditoria.recordSecurityAfterCommit(
        new SecurityEvent(
            SecurityEventType.MFA_RECOVERY_CODES_REGENERATED,
            Severity.ALTA,
            Outcome.SUCCESS,
            quien,
            Map.of()));

    return new RecoveryCodesResponse(ahora, nuevos);
  }
}
