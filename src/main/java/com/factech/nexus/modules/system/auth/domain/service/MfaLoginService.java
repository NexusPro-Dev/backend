package com.factech.nexus.modules.system.auth.domain.service;

import com.factech.nexus.modules.system.auth.application.MfaLoginRequest;
import com.factech.nexus.modules.system.auth.application.SessionResponse;
import com.factech.nexus.modules.system.auth.domain.models.MfaChallenge;
import com.factech.nexus.modules.system.auth.domain.models.MfaFactor;
import com.factech.nexus.modules.system.auth.domain.models.OpaqueToken;
import com.factech.nexus.modules.system.auth.domain.repository.AuthUser;
import com.factech.nexus.modules.system.auth.domain.repository.AuthUserRepository;
import com.factech.nexus.modules.system.auth.domain.repository.MfaChallengeRepository;
import com.factech.nexus.modules.system.auth.domain.repository.MfaFactorRepository;
import com.factech.nexus.modules.system.auth.domain.service.CredentialFailures.FalloDeCodigo;
import com.factech.nexus.shared.audit.AuditEnums.Outcome;
import com.factech.nexus.shared.audit.AuditEnums.SecurityEventType;
import com.factech.nexus.shared.audit.AuditEnums.Severity;
import com.factech.nexus.shared.audit.AuditEvents.SecurityEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BlockedAccountException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.UnauthorizedException;
import com.factech.nexus.shared.error.ValidationException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El segundo paso del inicio de sesión (`RF-SP-072`): el desafío y un código abren la sesión.
 *
 * <p><b>Orden de bloqueo: desafío → cuenta → factor</b>, siempre el mismo, para que dos segundos
 * pasos concurrentes no se traben (`plan.md` §7). <b>La cuenta se relee con su fila bloqueada</b>
 * antes de abrir la sesión, por la misma carrera que cierra el paso 5 de `RF-SP-034`: entre los dos
 * pasos pueden haber desactivado a la persona.
 *
 * <p><b>Un desafío muerto no revela de quién era</b> (`EX-001`): el rechazo es el genérico de
 * credenciales, sin intentos restantes y sin tocar ninguna cuenta. <b>Un código malo sí cuenta</b>,
 * en la cuenta y en el desafío (`EX-002`): quien lo presenta ya acertó la contraseña.
 */
@Service
public class MfaLoginService {

  private final MfaChallengeRepository desafios;
  private final AuthUserRepository cuentas;
  private final MfaFactorRepository factores;
  private final SecondFactorVerifier verificador;
  private final CredentialFailures fallos;
  private final SessionOpener aperturas;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public MfaLoginService(
      MfaChallengeRepository desafios,
      AuthUserRepository cuentas,
      MfaFactorRepository factores,
      SecondFactorVerifier verificador,
      CredentialFailures fallos,
      SessionOpener aperturas,
      AuditWriter auditoria) {
    this(desafios, cuentas, factores, verificador, fallos, aperturas, auditoria, Clock.systemUTC());
  }

  MfaLoginService(
      MfaChallengeRepository desafios,
      AuthUserRepository cuentas,
      MfaFactorRepository factores,
      SecondFactorVerifier verificador,
      CredentialFailures fallos,
      SessionOpener aperturas,
      AuditWriter auditoria,
      Clock reloj) {
    this.desafios = desafios;
    this.cuentas = cuentas;
    this.factores = factores;
    this.verificador = verificador;
    this.fallos = fallos;
    this.aperturas = aperturas;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional(noRollbackFor = {UnauthorizedException.class, BlockedAccountException.class})
  public SessionResponse completar(MfaLoginRequest peticion) {
    OffsetDateTime ahora = OffsetDateTime.now(reloj);

    if (peticion == null
        || peticion.challengeToken() == null
        || peticion.challengeToken().isBlank()) {
      String mensaje = "Debe indicar el desafío.";
      throw new ValidationException(
          "VAL-001", mensaje, List.of(new FieldError("challengeToken", "VAL-001", mensaje)));
    }
    SecondFactorVerifier.exigirUnCodigo(peticion.code(), peticion.recoveryCode(), "VAL-002");

    // 1. El desafío, bloqueado. Muerto o inexistente: la respuesta genérica, sin cuenta.
    MfaChallenge desafio =
        desafios
            .buscarPorHashParaActualizar(OpaqueToken.resumen(peticion.challengeToken()))
            .filter(d -> d.vigente(ahora))
            .orElseThrow(MfaLoginService::desafioInvalido);

    // 2. La cuenta, bloqueada. Bloqueada: 423, como siempre. Deshabilitada: genérico.
    AuthUser cuenta =
        cuentas
            .findByIdForUpdate(desafio.getUserId())
            .orElseThrow(MfaLoginService::desafioInvalido);
    if (cuenta.bloqueadaAMano() || cuenta.bloqueadaPorIntentos(ahora)) {
      desafio.consumir(ahora);
      throw LoginService.bloqueada(cuenta.bloqueadaAMano(), cuenta.lockedUntil(), ahora);
    }
    if (!cuenta.puedeEntrar()) {
      desafio.consumir(ahora);
      throw desafioInvalido();
    }

    // 3. El factor activo, bloqueado. Si ya no hay —lo restablecieron entre los dos pasos—, el
    //    código no casa con nada y cuenta como un fallo más (`spec.md` §13).
    Optional<MfaFactor> factor = factores.activoParaActualizar(cuenta.id());
    SecondFactorVerifier.Resultado resultado =
        factor
            .map(f -> verificador.verificar(f, peticion.code(), peticion.recoveryCode(), ahora))
            .orElse(new SecondFactorVerifier.Resultado(false, null));

    if (!resultado.vale()) {
      desafio.fallar();
      FalloDeCodigo fallo = fallos.rechazarCodigo(cuenta, "LOGIN", ahora);
      if (fallo.bloqueo()) {
        desafio.consumir(ahora);
      }
      throw CredentialFailures.credencialesInvalidas(
          fallo.restantes(), fallo.bloqueadaHasta(), ahora);
    }

    desafio.consumir(ahora);

    if (resultado.conCodigoDeRecuperacion()) {
      auditoria.recordSecurityAfterCommit(
          new SecurityEvent(
              SecurityEventType.MFA_RECOVERY_CODE_USED,
              Severity.ALTA,
              Outcome.SUCCESS,
              cuenta.id(),
              Map.of("remaining", resultado.codigosRestantes(), "stage", "LOGIN")));
    }

    return aperturas.abrir(cuenta, ahora, resultado.codigosRestantes(), ahora);
  }

  /** Un solo rechazo para los cuatro casos de `EX-001`, sin intentos restantes: no hay cuenta. */
  private static UnauthorizedException desafioInvalido() {
    return new UnauthorizedException("VAL-003", "Las credenciales no son válidas.");
  }
}
