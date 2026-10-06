package com.factech.nexus.modules.system.auth.domain.service;

import com.factech.nexus.modules.system.auth.application.MfaConfirmationResponse;
import com.factech.nexus.modules.system.auth.application.MfaEnrollmentResponse;
import com.factech.nexus.modules.system.auth.domain.models.MfaFactor;
import com.factech.nexus.modules.system.auth.domain.repository.MfaFactorRepository;
import com.factech.nexus.modules.system.auth.infrastructure.MfaSecrets;
import com.factech.nexus.modules.system.auth.infrastructure.MfaSettings;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEnums.Outcome;
import com.factech.nexus.shared.audit.AuditEnums.SecurityEventType;
import com.factech.nexus.shared.audit.AuditEnums.Severity;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditEvents.SecurityEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.RecentMfaRequiredException;
import com.factech.nexus.shared.error.UnauthorizedException;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import com.factech.nexus.shared.security.CurrentActor;
import com.factech.nexus.shared.security.RecentMfa;
import com.factech.nexus.shared.security.Totp;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Activar el segundo factor (`RF-SP-071`): iniciar y confirmar.
 *
 * <p><b>Dos pasos</b>: iniciar crea un factor pendiente con el secreto cifrado; confirmar con el
 * primer código lo vuelve activo y emite los diez códigos de recuperación (`RN-SP-058`,
 * `RN-SP-061`). Con un factor ya activo, iniciar es <b>cambiar de teléfono</b>, y exige
 * verificación reciente (`EX-003`); confirmar entonces retira el anterior.
 *
 * <p><b>Los fallos al confirmar no consumen intentos de la cuenta</b> (`spec.md` §10): confirmar un
 * pendiente no concede nada que la persona no tuviera, y quien llama ya está autenticado.
 */
@Service
public class MfaEnrollmentService {

  private final MfaFactorRepository factores;
  private final RecoveryCodeIssuer codigos;
  private final MfaSecrets secretos;
  private final MfaSettings ajustes;
  private final RecentMfa reciente;
  private final CurrentActor actor;
  private final AuditWriter auditoria;
  private final UuidV7Generator ids;
  private final Clock reloj;

  @Autowired
  public MfaEnrollmentService(
      MfaFactorRepository factores,
      RecoveryCodeIssuer codigos,
      MfaSecrets secretos,
      MfaSettings ajustes,
      RecentMfa reciente,
      CurrentActor actor,
      AuditWriter auditoria,
      UuidV7Generator ids) {
    this(factores, codigos, secretos, ajustes, reciente, actor, auditoria, ids, Clock.systemUTC());
  }

  MfaEnrollmentService(
      MfaFactorRepository factores,
      RecoveryCodeIssuer codigos,
      MfaSecrets secretos,
      MfaSettings ajustes,
      RecentMfa reciente,
      CurrentActor actor,
      AuditWriter auditoria,
      UuidV7Generator ids,
      Clock reloj) {
    this.factores = factores;
    this.codigos = codigos;
    this.secretos = secretos;
    this.ajustes = ajustes;
    this.reciente = reciente;
    this.actor = actor;
    this.auditoria = auditoria;
    this.ids = ids;
    this.reloj = reloj;
  }

  /**
   * Iniciar: un pendiente nuevo, el único de la persona.
   *
   * <p>El anterior pendiente, si lo hay, se retira como {@code CADUCADO} <b>antes</b> de insertar
   * el nuevo: `uq_user_mfa_factors_pendiente` no admite dos, y el orden lo fija el {@code flush}
   * del repositorio, no el proveedor de persistencia.
   */
  @Transactional
  public MfaEnrollmentResponse iniciar() {
    OffsetDateTime ahora = OffsetDateTime.now(reloj);
    UUID quien = quien();

    if (factores.activo(quien).isPresent() && !reciente.esReciente()) {
      throw new RecentMfaRequiredException(
          "Para cambiar de authenticator debe verificar antes el actual.");
    }

    factores
        .pendienteParaActualizar(quien)
        .ifPresent(
            anterior -> {
              anterior.retirar(MfaFactor.Retiro.CADUCADO, ahora);
              factores.sincronizar();
            });

    String secreto = Totp.nuevoSecreto();
    OffsetDateTime caduca = ahora.plus(ajustes.pendingTtl());
    factores.guardar(
        MfaFactor.iniciar(ids.next(), quien, secretos.cifrar(secreto, quien), caduca, ahora));

    String uri = Totp.uri(ajustes.issuer(), factores.nombreDeUsuario(quien), secreto);
    return new MfaEnrollmentResponse(uri, secreto, caduca);
  }

  /**
   * Confirmar: el pendiente pasa a activo, el anterior activo se retira y nacen diez códigos.
   *
   * <p>Se bloquean el pendiente y el activo, en ese orden siempre. Si dos confirmaciones compiten,
   * la segunda encuentra el pendiente ya activo —y responde que no hay pendiente— o topa con
   * `uq_user_mfa_factors_activo`: <b>un rechazo en lugar de dos factores</b> (`CA-SP-817`).
   */
  @Transactional
  public MfaConfirmationResponse confirmar(String codigo) {
    OffsetDateTime ahora = OffsetDateTime.now(reloj);
    UUID quien = quien();

    if (codigo == null || !codigo.matches("\\d{6}")) {
      String mensaje = "El código debe tener seis dígitos.";
      throw new ValidationException(
          "VAL-001", mensaje, List.of(new FieldError("code", "VAL-001", mensaje)));
    }

    MfaFactor pendiente =
        factores
            .pendienteParaActualizar(quien)
            .filter(f -> f.pendienteVigente(ahora))
            .orElseThrow(MfaEnrollmentService::invalido);

    String secreto = secretos.descifrar(pendiente.getSecretCiphertext(), quien);
    OptionalLong periodo = Totp.periodoQueCasa(secreto, codigo, ahora.toInstant(), null);
    if (periodo.isEmpty()) {
      throw invalido();
    }

    boolean reemplaza = false;
    var anterior = factores.activoParaActualizar(quien);
    if (anterior.isPresent()) {
      anterior.get().retirar(MfaFactor.Retiro.REEMPLAZADO, ahora);
      factores.sincronizar();
      reemplaza = true;
    }

    pendiente.confirmar(periodo.getAsLong(), ahora);
    try {
      factores.sincronizar();
    } catch (DataIntegrityViolationException carrera) {
      throw new BusinessRuleException(
          "VAL-003", "Otra confirmación del segundo factor terminó antes que esta.");
    }

    List<String> claros = codigos.emitir(pendiente.getId(), ahora);

    Map<String, Object> cambios = new HashMap<>();
    cambios.put("status", Map.of("before", MfaFactor.PENDIENTE, "after", MfaFactor.ACTIVO));
    auditoria.recordChange(
        new ChangeEvent("SP", "user_mfa_factors", pendiente.getId(), ChangeAction.UPDATE, cambios));
    auditoria.recordSecurityAfterCommit(
        new SecurityEvent(
            SecurityEventType.MFA_ENABLED,
            Severity.ALTA,
            Outcome.SUCCESS,
            quien,
            Map.of("replacedPrevious", reemplaza)));

    return new MfaConfirmationResponse(ahora, reemplaza, claros);
  }

  private UUID quien() {
    return actor
        .currentActorId()
        .orElseThrow(() -> new UnauthorizedException("AUTH-001", "Se requiere autenticación."));
  }

  private static UnprocessableEntityException invalido() {
    String mensaje =
        "El código no es válido o ha caducado. Si el problema persiste, vuelva a escanear.";
    return new UnprocessableEntityException(
        "VAL-002", mensaje, List.of(new FieldError("code", "VAL-002", mensaje)));
  }
}
