package com.factech.nexus.modules.system.users.domain.service;

import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import com.factech.nexus.modules.system.users.application.ResetPasswordRequest;
import com.factech.nexus.modules.system.users.domain.models.User;
import com.factech.nexus.modules.system.users.domain.repository.UserRepository;
import com.factech.nexus.modules.system.users.domain.security.SelfOperationGuard;
import com.factech.nexus.shared.audit.AuditEnums.Outcome;
import com.factech.nexus.shared.audit.AuditEnums.SecurityEventType;
import com.factech.nexus.shared.audit.AuditEnums.Severity;
import com.factech.nexus.shared.audit.AuditEvents.SecurityEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.security.PasswordHasher;
import com.factech.nexus.shared.security.PasswordPolicy;
import com.factech.nexus.shared.security.SessionRevoker;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fijar una credencial provisional sobre la cuenta de otra persona (`RF-SP-038`).
 *
 * <p><b>La credencial CADUCA</b>, y esa es la decisión que define el requerimiento. Sin ella, una
 * cuenta restablecida y nunca usada conserva indefinidamente una contraseña que otra persona
 * conoce, y <b>nadie se entera porque no falla nada</b>: la cuenta sigue activa, el registro dice
 * que se restableció, y lo que alguien apuntó en un papel sigue abriendo la puerta meses después.
 * Quien comprueba el plazo es el inicio de sesión, no esta operación.
 *
 * <p>Orden de verificación (`plan.md` §4):
 *
 * <ol>
 *   <li>Formato y obligatoriedad — {@code 400}.
 *   <li>La contraseña cumple la política — {@code 400}. Va <b>antes</b> de leer nada: rechazar una
 *       contraseña débil sin tocar la cuenta es gratis.
 *   <li>La persona existe y no está eliminada — {@code 404}.
 *   <li>No es el propio actor — {@code 409}.
 * </ol>
 *
 * <p><b>El {@code 409} de `RN-SP-017` es un 409 aquí y un 403 en el cambio de estado y la
 * eliminación.</b> Los planes aprobados no coinciden; la comparación vive en un solo sitio y el
 * estado lo elige cada contrato. Queda declarado en §4.bis.
 *
 * <p>Y su mensaje <b>dice cuál es la operación correcta</b>: sin esa indicación, quien lo recibe
 * concluye que no puede cambiar su propia contraseña.
 */
@Service
public class ResetUserPasswordService {

  private final UserRepository usuarios;
  private final PasswordPolicy politica;
  private final PasswordHasher hasher;
  private final SessionRevoker sesiones;
  private final AuthenticatedActor actor;
  private final AuditWriter auditoria;
  private final Duration vigencia;
  private final Clock reloj;

  @Autowired
  public ResetUserPasswordService(
      UserRepository usuarios,
      PasswordPolicy politica,
      PasswordHasher hasher,
      SessionRevoker sesiones,
      AuthenticatedActor actor,
      AuditWriter auditoria,
      @Value("${nexus.security.password.provisional-ttl:PT48H}") Duration vigencia) {
    this(usuarios, politica, hasher, sesiones, actor, auditoria, vigencia, Clock.systemUTC());
  }

  ResetUserPasswordService(
      UserRepository usuarios,
      PasswordPolicy politica,
      PasswordHasher hasher,
      SessionRevoker sesiones,
      AuthenticatedActor actor,
      AuditWriter auditoria,
      Duration vigencia,
      Clock reloj) {
    this.usuarios = usuarios;
    this.politica = politica;
    this.hasher = hasher;
    this.sesiones = sesiones;
    this.actor = actor;
    this.auditoria = auditoria;
    this.vigencia = vigencia;
    this.reloj = reloj;
  }

  @Transactional
  public void reset(UUID userId, ResetPasswordRequest peticion) {
    // 1 y 2. Formato y política, antes de leer nada.
    String nueva = peticion == null ? null : peticion.newPassword();
    if (nueva == null || nueva.isEmpty()) {
      String mensaje = "La contraseña es obligatoria.";
      throw new ValidationException(
          "VAL-001", mensaje, List.of(new FieldError("newPassword", "VAL-001", mensaje)));
    }
    politica.verificar(nueva, null, null);

    // 3. La persona, bloqueada.
    User usuario =
        usuarios
            .findNotDeletedByIdForUpdate(userId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "VAL-004", "No existe una persona con ese identificador."));

    // 4. `RN-SP-017`.
    if (SelfOperationGuard.esSuPropiaCuenta(actor.id(), userId)) {
      String mensaje =
          "No puede restablecer su propia contraseña por esta vía: use el cambio de la propia"
              + " contraseña, que exige conocer la actual.";
      throw new BusinessRuleException(
          "RN-SP-017", mensaje, List.of(new FieldError("id", "RN-SP-017", mensaje)));
    }

    OffsetDateTime ahora = OffsetDateTime.now(reloj);
    OffsetDateTime caduca = ahora.plus(vigencia);

    usuario.resetPasswordBy(hasher.hash(nueva), caduca, ahora);

    // Dentro de la transacción: quien tuviera la cuenta abierta con la
    // contraseña anterior deja de tenerla. Si esto falla, el restablecimiento se
    // revierte antes que dejar viva una sesión con la credencial que se acaba de
    // sustituir.
    sesiones.revokeAllForAccessChange(userId);

    auditar(usuario, caduca);
  }

  /**
   * <b>Un evento de seguridad y ninguno de cambio.</b>
   *
   * <p>Lo que ocurrió no es la edición de un campo: es que la credencial de una persona pasó a
   * conocerla otra. El registro de cambios describiría un {@code UPDATE} sobre {@code users} sin
   * poder decir qué cambió —porque el valor no puede escribirse— y el de seguridad sí dice lo único
   * que importa.
   *
   * <p>El detalle lleva <b>la fecha de caducidad y nada más</b>. Ni la contraseña, ni su resumen,
   * ni su longitud (Art. IV.8): la caducidad es lo que alguien necesitará para entender por qué esa
   * cuenta dejó de entrar.
   */
  private void auditar(User usuario, OffsetDateTime caduca) {
    auditoria.recordSecurityAfterCommit(
        new SecurityEvent(
            SecurityEventType.PASSWORD_RESET,
            Severity.ALTA,
            Outcome.SUCCESS,
            usuario.getId(),
            Map.of("expires_at", caduca.toString())));
  }
}
