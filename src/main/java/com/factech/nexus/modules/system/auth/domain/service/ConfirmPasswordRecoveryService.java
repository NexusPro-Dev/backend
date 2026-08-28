package com.factech.nexus.modules.system.auth.domain.service;

import com.factech.nexus.modules.system.auth.application.PasswordRecoveryConfirmation;
import com.factech.nexus.modules.system.auth.domain.models.OpaqueToken;
import com.factech.nexus.modules.system.auth.domain.models.PasswordResetPermit;
import com.factech.nexus.modules.system.auth.domain.models.RevokedReason;
import com.factech.nexus.modules.system.auth.domain.repository.AuthUserRepository;
import com.factech.nexus.modules.system.auth.domain.repository.PasswordResetPermitRepository;
import com.factech.nexus.modules.system.auth.domain.repository.RefreshTokenRepository;
import com.factech.nexus.shared.audit.AuditEnums.Outcome;
import com.factech.nexus.shared.audit.AuditEnums.SecurityEventType;
import com.factech.nexus.shared.audit.AuditEnums.Severity;
import com.factech.nexus.shared.audit.AuditEvents.SecurityEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.security.AccessRevocationPublisher;
import com.factech.nexus.shared.security.PasswordHasher;
import com.factech.nexus.shared.security.PasswordPolicy;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumir el permiso y fijar la contraseña nueva (`RF-SP-040`).
 *
 * <p>Orden de verificación (`plan.md` §4), y el orden <b>es</b> el requerimiento:
 *
 * <ol>
 *   <li>Formato y obligatoriedad — {@code 400}.
 *   <li>La contraseña cumple la política — {@code 400}. <b>Aquí no se ha tocado el permiso.</b>
 *   <li>Localizar el permiso por su hash, con bloqueo de fila.
 *   <li>Vigente, no consumido y no sustituido — {@code 422}.
 *   <li>Sustituir la credencial, consumir el permiso y cortar los accesos.
 * </ol>
 *
 * <p><b>El paso 2 va antes del 3 y eso es `CA-SP-461`.</b> Una contraseña que no cumple la política
 * es un error de la persona legítima, y consumir su permiso por ello la obligaría a pedir otro —y a
 * esperar otro correo— por haber escrito una contraseña corta. Se castigaría el intento correcto.
 *
 * <p><b>Los cuatro casos de `EX-001` comparten respuesta</b>: inexistente, caducado, ya usado y
 * sustituido. Distinguirlos le diría a quien prueba permisos al azar cuál estuvo a punto de
 * acertar.
 *
 * <p><b>La credencial que se fija aquí NO es provisional</b>, y esa es la diferencia deliberada con
 * `RF-SP-038`: la eligió su titular y nadie más la conoce, de modo que no hay ventana que cerrar.
 * No se marca la cuenta para cambio obligatorio ni se fija caducidad.
 *
 * <p><b>Y no levanta el bloqueo ni cambia el estado.</b> Recuperar la contraseña prueba que se
 * tiene el correo, no que alguien decidiera devolver el acceso: eso es `RF-SP-028`.
 */
@Service
public class ConfirmPasswordRecoveryService {

  private final PasswordResetPermitRepository permisos;
  private final AuthUserRepository cuentas;
  private final RefreshTokenRepository sesiones;
  private final AccessRevocationPublisher cortes;
  private final PasswordPolicy politica;
  private final PasswordHasher hasher;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public ConfirmPasswordRecoveryService(
      PasswordResetPermitRepository permisos,
      AuthUserRepository cuentas,
      RefreshTokenRepository sesiones,
      AccessRevocationPublisher cortes,
      PasswordPolicy politica,
      PasswordHasher hasher,
      AuditWriter auditoria) {
    this(permisos, cuentas, sesiones, cortes, politica, hasher, auditoria, Clock.systemUTC());
  }

  ConfirmPasswordRecoveryService(
      PasswordResetPermitRepository permisos,
      AuthUserRepository cuentas,
      RefreshTokenRepository sesiones,
      AccessRevocationPublisher cortes,
      PasswordPolicy politica,
      PasswordHasher hasher,
      AuditWriter auditoria,
      Clock reloj) {
    this.permisos = permisos;
    this.cuentas = cuentas;
    this.sesiones = sesiones;
    this.cortes = cortes;
    this.politica = politica;
    this.hasher = hasher;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public void confirmar(PasswordRecoveryConfirmation peticion) {
    // 1. Formato.
    String valor = peticion == null ? null : peticion.permit();
    String nueva = peticion == null ? null : peticion.newPassword();

    if (valor == null || valor.isBlank()) {
      throw formato("permit", "VAL-002", "El código de recuperación es obligatorio.");
    }
    if (nueva == null || nueva.isEmpty()) {
      throw formato("newPassword", "VAL-003", "La contraseña es obligatoria.");
    }

    // 2. La política, ANTES de tocar el permiso. Ver la nota de la clase.
    politica.verificar(nueva, null, null);

    // 3 y 4. El permiso, bloqueado.
    OffsetDateTime ahora = OffsetDateTime.now(reloj);
    Optional<PasswordResetPermit> hallado =
        permisos.buscarPorHashParaActualizar(OpaqueToken.resumen(valor));

    if (hallado.isEmpty() || !hallado.get().vigente(ahora)) {
      // El objetivo va SOLO si el permiso resolvió a alguien: con un valor
      // inventado no hay a quién señalar, y señalar a alguien equivocado es
      // peor que no señalar a nadie.
      auditar(Outcome.FAILURE, hallado.map(PasswordResetPermit::getUserId).orElse(null));
      throw new UnprocessableEntityException(
          "VAL-005",
          "El código de recuperación no es válido o ha caducado. Solicite uno nuevo.",
          List.of(
              new FieldError(
                  "permit",
                  "VAL-005",
                  "El código de recuperación no es válido o ha caducado. Solicite uno nuevo.")));
    }

    PasswordResetPermit permiso = hallado.get();

    // 5. Todo junto y en la misma transacción.
    //
    // `recuperarContrasena` y NO `cambiarContrasena`: aquel levanta el bloqueo
    // automático y este NO debe (`CA-SP-464`). Recuperar prueba que se tiene el
    // CORREO, no que alguien decidiera devolver el acceso — eso es `RF-SP-028`
    // y exige un actor con permiso.
    //
    // Sí limpia la marca de cambio obligatorio con su caducidad: quien recibió
    // una credencial provisional de un administrador y la olvidó antes de
    // usarla llega por aquí, y su cuenta debe quedar tan libre como si hubiera
    // ejecutado `RF-SP-037`.
    cuentas.recuperarContrasena(permiso.getUserId(), hasher.hash(nueva), ahora);
    permiso.consumir(ahora);
    sesiones.revokeAllActive(permiso.getUserId(), RevokedReason.CAMBIO_CONTRASENA, ahora);
    cortes.publicarCorte(permiso.getUserId());

    auditar(Outcome.SUCCESS, permiso.getUserId());
  }

  private void auditar(Outcome resultado, java.util.UUID objetivo) {
    SecurityEvent evento =
        new SecurityEvent(
            SecurityEventType.PASSWORD_RESET,
            Severity.ALTA,
            resultado,
            objetivo,
            Map.of("stage", "CONFIRMACION", "self", true));

    if (resultado == Outcome.SUCCESS) {
      auditoria.recordSecurityAfterCommit(evento);
    } else {
      // Sin esperar al commit: la petición termina en rechazo y la transacción
      // se revierte, y un permiso probado sin acertar es justo lo que hay que
      // poder ver cuando alguien está probando muchos.
      auditoria.recordSecurity(evento);
    }
  }

  private static ValidationException formato(String campo, String codigo, String mensaje) {
    return new ValidationException(
        codigo, mensaje, List.of(new FieldError(campo, codigo, mensaje)));
  }
}
