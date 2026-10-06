package com.factech.nexus.modules.system.auth.domain.service;

import com.factech.nexus.modules.system.auth.application.MfaResetRequest;
import com.factech.nexus.modules.system.auth.domain.models.MfaFactor;
import com.factech.nexus.modules.system.auth.domain.models.RevokedReason;
import com.factech.nexus.modules.system.auth.domain.repository.AuthUser;
import com.factech.nexus.modules.system.auth.domain.repository.AuthUserRepository;
import com.factech.nexus.modules.system.auth.domain.repository.MfaFactorRepository;
import com.factech.nexus.modules.system.auth.domain.repository.RefreshTokenRepository;
import com.factech.nexus.modules.system.users.domain.security.PrivilegeContainment;
import com.factech.nexus.modules.system.users.domain.security.SelfOperationGuard;
import com.factech.nexus.shared.audit.AuditEnums.DeletionType;
import com.factech.nexus.shared.audit.AuditEnums.Outcome;
import com.factech.nexus.shared.audit.AuditEnums.SecurityEventType;
import com.factech.nexus.shared.audit.AuditEnums.Severity;
import com.factech.nexus.shared.audit.AuditEvents.DeletionEvent;
import com.factech.nexus.shared.audit.AuditEvents.SecurityEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ForbiddenException;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.UnauthorizedException;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.security.AccessRevocationPublisher;
import com.factech.nexus.shared.security.CurrentActor;
import com.factech.nexus.shared.security.EffectivePermissions;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Restablecer el segundo factor de otra persona (`RF-SP-076`, `RN-SP-064`, `RN-SP-065`).
 *
 * <p><b>El orden de las comprobaciones</b> (`plan.md` §1): motivo → uno mismo → la persona existe →
 * <b>contención</b> → tiene factor. La contención va antes que «tiene factor» para que a un
 * administrador sin privilegio suficiente la respuesta no le diga si el superadministrador tiene el
 * factor activo.
 *
 * <p>Retira el activo y el pendiente con el motivo {@code RESTABLECIDO}, <b>cierra todas las
 * sesiones de la persona</b> —una sesión abierta en el teléfono perdido la tiene otro— y deja el
 * motivo en la auditoría de eliminación. <b>No toca la contraseña</b>: si también la olvidó, es
 * `RF-SP-038`.
 */
@Service
public class MfaResetService {

  private static final int MOTIVO_MAXIMO = 500;

  private final AuthUserRepository cuentas;
  private final MfaFactorRepository factores;
  private final RefreshTokenRepository sesiones;
  private final AccessRevocationPublisher cortes;
  private final EffectivePermissions permisos;
  private final CurrentActor actor;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public MfaResetService(
      AuthUserRepository cuentas,
      MfaFactorRepository factores,
      RefreshTokenRepository sesiones,
      AccessRevocationPublisher cortes,
      EffectivePermissions permisos,
      CurrentActor actor,
      AuditWriter auditoria) {
    this(cuentas, factores, sesiones, cortes, permisos, actor, auditoria, Clock.systemUTC());
  }

  MfaResetService(
      AuthUserRepository cuentas,
      MfaFactorRepository factores,
      RefreshTokenRepository sesiones,
      AccessRevocationPublisher cortes,
      EffectivePermissions permisos,
      CurrentActor actor,
      AuditWriter auditoria,
      Clock reloj) {
    this.cuentas = cuentas;
    this.factores = factores;
    this.sesiones = sesiones;
    this.cortes = cortes;
    this.permisos = permisos;
    this.actor = actor;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public void restablecer(UUID personaId, MfaResetRequest peticion) {
    OffsetDateTime ahora = OffsetDateTime.now(reloj);
    UUID quien =
        actor
            .currentActorId()
            .orElseThrow(() -> new UnauthorizedException("AUTH-001", "Se requiere autenticación."));

    String motivo = peticion == null ? null : peticion.reason();
    if (motivo == null || motivo.isBlank() || motivo.length() > MOTIVO_MAXIMO) {
      String mensaje = "Debe indicar el motivo, de hasta " + MOTIVO_MAXIMO + " caracteres.";
      throw new ValidationException(
          "VAL-001", mensaje, List.of(new FieldError("reason", "VAL-001", mensaje)));
    }

    if (SelfOperationGuard.esSuPropiaCuenta(quien, personaId)) {
      throw new ForbiddenException(
          "VAL-002",
          "No puede restablecer su propio segundo factor: desactívelo o cámbielo usted mismo.");
    }

    AuthUser persona =
        cuentas
            .findByIdForUpdate(personaId)
            .filter(cuenta -> !cuenta.deleted())
            .orElseThrow(() -> new ResourceNotFoundException("VAL-005", "El usuario no existe."));

    // `RN-SP-065`: sin decir cuál falta —diría qué puede hacer la persona—.
    Set<String> deLaPersona = permisos.forUser(persona.id()).orElseGet(Set::of);
    if (!PrivilegeContainment.abarca(actor.currentPermissions(), deLaPersona)) {
      throw new ForbiddenException(
          "VAL-003", "No puede restablecer el segundo factor de esta persona.");
    }

    List<MfaFactor> retirados = new ArrayList<>();
    factores.activoParaActualizar(personaId).ifPresent(retirados::add);
    factores.pendienteParaActualizar(personaId).ifPresent(retirados::add);
    if (retirados.isEmpty()) {
      throw new BusinessRuleException(
          "VAL-004", "Esta persona no tiene activado el segundo factor.");
    }

    for (MfaFactor factor : retirados) {
      String estadoAnterior = factor.getStatus();
      factor.retirar(MfaFactor.Retiro.RESTABLECIDO, ahora);
      Map<String, Object> instantanea = new HashMap<>();
      instantanea.put("id", factor.getId().toString());
      instantanea.put("user_id", personaId.toString());
      instantanea.put("status", estadoAnterior);
      instantanea.put(
          "confirmed_at",
          factor.getConfirmedAt() == null ? null : factor.getConfirmedAt().toString());
      // El secreto NO va en la instantánea (`071` · `plan.md` §6).
      auditoria.recordDeletion(
          new DeletionEvent(
              "SP", "user_mfa_factors", factor.getId(), DeletionType.LOGICAL, motivo, instantanea));
    }
    factores.sincronizar();

    sesiones.revokeAllActive(personaId, RevokedReason.ACCESO_RETIRADO, ahora);
    cortes.publicarCorte(personaId);

    auditoria.recordSecurityAfterCommit(
        new SecurityEvent(
            SecurityEventType.MFA_RESET,
            Severity.ALTA,
            Outcome.SUCCESS,
            personaId,
            Map.of("factors", retirados.size())));
  }
}
