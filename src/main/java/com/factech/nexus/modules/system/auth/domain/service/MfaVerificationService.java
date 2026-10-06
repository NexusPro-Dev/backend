package com.factech.nexus.modules.system.auth.domain.service;

import com.factech.nexus.modules.system.auth.application.MfaVerificationRequest;
import com.factech.nexus.modules.system.auth.application.MfaVerificationResponse;
import com.factech.nexus.modules.system.auth.domain.models.MfaFactor;
import com.factech.nexus.modules.system.auth.domain.models.RevokedReason;
import com.factech.nexus.modules.system.auth.domain.repository.AuthUser;
import com.factech.nexus.modules.system.auth.domain.repository.AuthUserRepository;
import com.factech.nexus.modules.system.auth.domain.repository.MfaFactorRepository;
import com.factech.nexus.modules.system.auth.domain.repository.RefreshTokenRepository;
import com.factech.nexus.modules.system.auth.domain.service.CredentialFailures.FalloDeCodigo;
import com.factech.nexus.shared.audit.AuditEnums.Outcome;
import com.factech.nexus.shared.audit.AuditEnums.SecurityEventType;
import com.factech.nexus.shared.audit.AuditEnums.Severity;
import com.factech.nexus.shared.audit.AuditEvents.SecurityEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BlockedAccountException;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.UnauthorizedException;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.security.AccessRevocationPublisher;
import com.factech.nexus.shared.security.AccessTokenIssuer;
import com.factech.nexus.shared.security.CurrentActor;
import com.factech.nexus.shared.security.RecentMfa;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reverificar el segundo factor antes de una operación sensible (`RF-SP-073`).
 *
 * <p><b>Devuelve un token de acceso nuevo con {@code mfa} en el instante actual</b> —mismos roles y
 * mismas marcas que el de la persona— y <b>no toca el refresh token</b>: la prueba viaja en el
 * token firmado y nada se guarda en el servidor (`plan.md` §1).
 *
 * <p><b>Los fallos consumen intentos de la cuenta</b>, como al entrar: una sesión robada no puede
 * usar esta ruta para probar códigos. <b>Y si el fallo bloquea, se cierran todas las sesiones</b>
 * (`EX-005`): quien falla cinco veces con una sesión en la mano es, con toda probabilidad, quien la
 * robó. Un código malo es {@code 422} y no {@code 401}: la credencial de la petición —el token— es
 * válida, y un {@code 401} haría que el cliente renovara la sesión o la cerrase.
 */
@Service
public class MfaVerificationService {

  private final AuthUserRepository cuentas;
  private final MfaFactorRepository factores;
  private final SecondFactorVerifier verificador;
  private final CredentialFailures fallos;
  private final RefreshTokenRepository sesiones;
  private final AccessRevocationPublisher cortes;
  private final AccessTokenIssuer tokens;
  private final RecentMfa reciente;
  private final CurrentActor actor;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public MfaVerificationService(
      AuthUserRepository cuentas,
      MfaFactorRepository factores,
      SecondFactorVerifier verificador,
      CredentialFailures fallos,
      RefreshTokenRepository sesiones,
      AccessRevocationPublisher cortes,
      AccessTokenIssuer tokens,
      RecentMfa reciente,
      CurrentActor actor,
      AuditWriter auditoria) {
    this(
        cuentas,
        factores,
        verificador,
        fallos,
        sesiones,
        cortes,
        tokens,
        reciente,
        actor,
        auditoria,
        Clock.systemUTC());
  }

  MfaVerificationService(
      AuthUserRepository cuentas,
      MfaFactorRepository factores,
      SecondFactorVerifier verificador,
      CredentialFailures fallos,
      RefreshTokenRepository sesiones,
      AccessRevocationPublisher cortes,
      AccessTokenIssuer tokens,
      RecentMfa reciente,
      CurrentActor actor,
      AuditWriter auditoria,
      Clock reloj) {
    this.cuentas = cuentas;
    this.factores = factores;
    this.verificador = verificador;
    this.fallos = fallos;
    this.sesiones = sesiones;
    this.cortes = cortes;
    this.tokens = tokens;
    this.reciente = reciente;
    this.actor = actor;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional(
      noRollbackFor = {
        UnprocessableEntityException.class,
        BlockedAccountException.class,
        ValidationException.class
      })
  public MfaVerificationResponse verificar(MfaVerificationRequest peticion) {
    OffsetDateTime ahora = OffsetDateTime.now(reloj);
    UUID quien =
        actor
            .currentActorId()
            .orElseThrow(() -> new UnauthorizedException("AUTH-001", "Se requiere autenticación."));

    SecondFactorVerifier.exigirUnCodigo(
        peticion == null ? null : peticion.code(),
        peticion == null ? null : peticion.recoveryCode(),
        "VAL-001");

    // Orden de bloqueo: cuenta → factor (`plan.md` §7).
    AuthUser cuenta =
        cuentas
            .findByIdForUpdate(quien)
            .filter(AuthUser::puedeEntrar)
            .orElseThrow(() -> new UnauthorizedException("AUTH-001", "La sesión ya no es válida."));
    if (cuenta.bloqueadaAMano() || cuenta.bloqueadaPorIntentos(ahora)) {
      throw LoginService.bloqueada(cuenta.bloqueadaAMano(), cuenta.lockedUntil(), ahora);
    }

    MfaFactor factor =
        factores
            .activoParaActualizar(quien)
            .orElseThrow(
                () ->
                    new BusinessRuleException(
                        "VAL-002",
                        "No tiene activado el segundo factor.",
                        List.of(
                            new FieldError(
                                "code", "VAL-002", "No tiene activado el segundo factor."))));

    SecondFactorVerifier.Resultado resultado =
        verificador.verificar(factor, peticion.code(), peticion.recoveryCode(), ahora);

    if (!resultado.vale()) {
      FalloDeCodigo fallo = fallos.rechazarCodigo(cuenta, "STEP_UP", ahora);
      if (fallo.bloqueo()) {
        // `EX-005`: con el bloqueo, fuera todas las sesiones, y el token de acceso
        // cortado tras el commit, como al desactivar (`RF-SP-028` `plan.md` §7).
        sesiones.revokeAllActive(quien, RevokedReason.ACCESO_RETIRADO, ahora);
        cortes.publicarCorte(quien);
        throw LoginService.bloqueada(false, fallo.bloqueadaHasta(), ahora);
      }
      Map<String, Object> extensiones = new HashMap<>();
      extensiones.put("remainingAttempts", fallo.restantes());
      String mensaje = "El código no es válido.";
      throw new UnprocessableEntityException(
          "VAL-003", mensaje, List.of(new FieldError("code", "VAL-003", mensaje)), extensiones);
    }

    // El acierto pone el contador a cero, como completar el inicio de sesión.
    cuentas.registrarFallo(quien, 0, null);

    if (resultado.conCodigoDeRecuperacion()) {
      auditoria.recordSecurityAfterCommit(
          new SecurityEvent(
              SecurityEventType.MFA_RECOVERY_CODE_USED,
              Severity.ALTA,
              Outcome.SUCCESS,
              quien,
              Map.of("remaining", resultado.codigosRestantes(), "stage", "STEP_UP")));
    }
    // La reverificación correcta NO se audita (`CA-SP-851`, `security.md` §8.1).

    String token =
        tokens.emitir(
            quien,
            cuenta.roleCodes(),
            cuenta.credencialAjena(),
            cuenta.activacionObligatoriaPendiente(),
            ahora.toInstant(),
            ahora.toInstant());
    return new MfaVerificationResponse(
        token,
        "Bearer",
        tokens.vidaEnSegundos(),
        reciente.validaHasta(ahora.toInstant()).atOffset(ahora.getOffset()),
        resultado.codigosRestantes());
  }
}
