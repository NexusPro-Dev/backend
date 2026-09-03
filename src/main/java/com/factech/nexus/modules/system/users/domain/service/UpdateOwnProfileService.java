package com.factech.nexus.modules.system.users.domain.service;

import com.factech.nexus.modules.system.users.application.OwnProfileResponse;
import com.factech.nexus.modules.system.users.application.UpdateOwnProfileRequest;
import com.factech.nexus.modules.system.users.domain.models.Email;
import com.factech.nexus.modules.system.users.domain.models.User;
import com.factech.nexus.modules.system.users.domain.repository.UserRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEnums.Outcome;
import com.factech.nexus.shared.audit.AuditEnums.SecurityEventType;
import com.factech.nexus.shared.audit.AuditEnums.Severity;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditEvents.SecurityEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.UnauthorizedException;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.patch.Patchable;
import com.factech.nexus.shared.security.CurrentActor;
import com.factech.nexus.shared.security.PasswordHasher;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Editar el propio perfil (`RF-SP-044`).
 *
 * <p><b>Es un servicio propio y no una rama dentro de {@code UpdateUserService}</b>, por el mismo
 * argumento con el que `RF-SP-039` no se resolvió dentro de `RF-SP-026`: la autorización es
 * distinta —ninguna frente a {@code users:update}—, el sujeto es distinto —siempre el actor frente
 * a un identificador de la ruta— y aquí hay una contraseña que comprobar. Meterlo en el servicio
 * existente lo obligaría a comportarse de dos maneras según quién lo llame, y un fallo en esa
 * bifurcación es una escalada de privilegios.
 *
 * <p><b>El sujeto sale del contexto de seguridad y de ningún otro sitio.</b> La petición no lleva
 * identificador: no existe el campo, de modo que no hay nada que manipular ni comprobación que
 * olvidar.
 *
 * <p><b>El cambio de correo exige la contraseña actual, y el de nombre no.</b> Desde `RF-SP-040` el
 * correo es la vía por la que se recupera una contraseña olvidada, así que cambiarlo es cambiar
 * <b>quién puede recuperar la cuenta</b>: quien se apodere de una sesión ajena y pueda cambiarlo se
 * queda con ella de forma permanente, aunque la persona legítima cambie después su contraseña. Una
 * sesión robada no lleva la contraseña, y exigirla convierte el robo en algo que <b>caduca</b>.
 * Equivocar un apellido no abre ninguna puerta, y cobrar ahí el precio empuja a no corregirlo.
 *
 * <p><b>El fallo de contraseña NO cuenta como intento fallido de inicio de sesión ni bloquea la
 * cuenta</b> (`CA-SP-504`). Quien está aquí ya se autenticó; tratarlo como un ataque de
 * credenciales permitiría a alguien con una sesión ajena dejar bloqueada a la persona legítima, que
 * es un daño mayor que el que se evita. Sí queda registrado en la auditoría de seguridad.
 */
@Service
public class UpdateOwnProfileService {

  private static final String MODULO = "SP";
  private static final String ENTIDAD = "users";

  private final UserRepository usuarios;
  private final PasswordHasher hasher;
  private final AuditWriter auditoria;
  private final CurrentActor actor;
  private final GetOwnProfileService perfil;
  private final Clock reloj;

  @Autowired
  public UpdateOwnProfileService(
      UserRepository usuarios,
      PasswordHasher hasher,
      AuditWriter auditoria,
      CurrentActor actor,
      GetOwnProfileService perfil) {
    this(usuarios, hasher, auditoria, actor, perfil, Clock.systemUTC());
  }

  UpdateOwnProfileService(
      UserRepository usuarios,
      PasswordHasher hasher,
      AuditWriter auditoria,
      CurrentActor actor,
      GetOwnProfileService perfil,
      Clock reloj) {
    this.usuarios = usuarios;
    this.hasher = hasher;
    this.auditoria = auditoria;
    this.actor = actor;
    this.perfil = perfil;
    this.reloj = reloj;
  }

  /** Aplica los cambios sobre el propio perfil y lo devuelve con la forma de `GET /users/me`. */
  @Transactional
  public OwnProfileResponse update(UpdateOwnProfileRequest peticion) {
    UUID quien =
        actor
            .currentActorId()
            .orElseThrow(() -> new UnauthorizedException("AUTH-001", "Se requiere autenticación."));

    List<FieldError> problemas = new ArrayList<>();

    if (!peticion.informaAlgo()) {
      problemas.add(
          new FieldError(
              null, "VAL-001", "Debe informar al menos uno de los campos modificables."));
    }

    String nombre = normalizar("firstName", peticion.firstName(), problemas);
    String apellido = normalizar("lastName", peticion.lastName(), problemas);
    String correoBruto = normalizar("email", peticion.email(), problemas);

    // La contraseña se exige por lo que la petición PIDE, no por lo que acabe
    // cambiando: que el correo enviado coincida con el actual solo se sabe
    // después de mirarlo, y condicionar la exigencia a eso daría una forma de
    // averiguar el correo vigente probando valores — el que no la pidiera sería
    // el bueno (`spec.md` FA-001).
    if (peticion.tocaElCorreo() && esVacia(peticion.currentPassword())) {
      problemas.add(
          new FieldError(
              "currentPassword",
              "VAL-006",
              "Para cambiar su correo debe indicar su contraseña actual."));
    }

    if (!problemas.isEmpty()) {
      throw new ValidationException(problemas.get(0).code(), problemas.get(0).message(), problemas);
    }

    User usuario =
        usuarios
            .findNotDeletedByIdForUpdate(quien)
            .orElseThrow(
                () ->
                    new UnauthorizedException(
                        "AUTH-001", "La sesión ya no es válida: la cuenta fue eliminada."));

    if (peticion.tocaElCorreo()) {
      exigirContrasena(usuario, peticion.currentPassword());
    }

    OffsetDateTime ahora = OffsetDateTime.now(reloj);
    String nombreAnterior = usuario.getFirstName();
    String apellidoAnterior = usuario.getLastName();
    String correoAnterior = usuario.getEmail();

    String correo = correoBruto == null ? null : new Email(correoBruto).value();

    boolean cambiaNombre = usuario.rename(nombre, apellido, ahora);
    boolean cambiaCorreo = correo != null && !correo.equals(correoAnterior);

    if (cambiaCorreo) {
      verificarCorreoLibre(correo);
      usuario.changeEmail(correo, ahora);
    }

    if (cambiaNombre || cambiaCorreo) {
      // El volcado va antes de releer el perfil: la respuesta se construye con
      // otra consulta, y sin esto devolvería los valores de antes.
      usuarios.flushChanges();
      auditar(
          usuario, nombreAnterior, apellidoAnterior, correoAnterior, cambiaNombre, cambiaCorreo);
    }

    return perfil.profile();
  }

  /**
   * La prueba de que quien pide el cambio es la persona, y no quien le robó la sesión.
   *
   * <p>Un {@code 422} y no un {@code 400}, con el mismo criterio que `RF-SP-040`: la petición está
   * bien formada y lo que falla es una comprobación contra el estado del sistema.
   */
  private void exigirContrasena(User usuario, String declarada) {
    if (hasher.matches(declarada, usuario.getPasswordHash())) {
      return;
    }

    // Se registra el intento y NO se toca `failed_attempts`: ver la nota de la
    // clase. El evento sobrevive porque no hay escritura que confirmar.
    auditoria.recordSecurity(
        new SecurityEvent(
            SecurityEventType.EMAIL_CHANGED,
            Severity.ALTA,
            Outcome.FAILURE,
            usuario.getId(),
            Map.of("stage", "CONTRASENA_ACTUAL", "self", true)));

    String mensaje = "La contraseña actual no es correcta.";
    throw new UnprocessableEntityException(
        "VAL-007", mensaje, List.of(new FieldError("currentPassword", "VAL-007", mensaje)));
  }

  private static boolean esVacia(String valor) {
    return valor == null || valor.isBlank();
  }

  private static String normalizar(
      String campo, Patchable<String> valor, List<FieldError> problemas) {
    if (!valor.presente()) {
      return null;
    }
    String contenido = valor.valor() == null ? "" : valor.valor().trim();
    if (contenido.isEmpty()) {
      problemas.add(
          new FieldError(campo, "VAL-002", "El campo '" + campo + "' no puede quedar vacío."));
      return null;
    }
    if (contenido.length() > 255) {
      problemas.add(
          new FieldError(
              campo, "VAL-005", "El campo '" + campo + "' excede la longitud admitida."));
      return null;
    }
    return contenido;
  }

  /**
   * `RN-SP-016`. Dentro de la transacción, y no antes: separarlo deja una ventana en la que dos
   * personas toman el mismo correo, y {@code uq_users_email} lo convertiría en un {@code 500} en
   * lugar del {@code 409} que la especificación pide.
   */
  private void verificarCorreoLibre(String correo) {
    if (usuarios.existsEmail(new Email(correo))) {
      String mensaje = "Ese correo ya está en uso.";
      throw new BusinessRuleException(
          "RN-SP-016", mensaje, List.of(new FieldError("email", "RN-SP-016", mensaje)));
    }
  }

  /**
   * El cambio, y —solo si tocó el correo— el evento de seguridad.
   *
   * <p>Mismo criterio que `RF-SP-027`: cambiar el correo es tocar una vía de acceso, y el nombre
   * no. Aquí el actor y el afectado son la misma persona, lo que no lo hace menos interesante para
   * quien investigue: una apropiación de cuenta se ve exactamente así.
   */
  private void auditar(
      User usuario,
      String nombreAnterior,
      String apellidoAnterior,
      String correoAnterior,
      boolean cambiaNombre,
      boolean cambiaCorreo) {
    Map<String, Object> cambios = new HashMap<>();
    if (cambiaNombre) {
      cambios.put("first_name", Map.of("before", nombreAnterior, "after", usuario.getFirstName()));
      cambios.put("last_name", Map.of("before", apellidoAnterior, "after", usuario.getLastName()));
    }
    if (cambiaCorreo) {
      cambios.put("email", Map.of("before", correoAnterior, "after", usuario.getEmail()));
    }

    auditoria.recordChange(
        new ChangeEvent(MODULO, ENTIDAD, usuario.getId(), ChangeAction.UPDATE, cambios));

    if (cambiaCorreo) {
      auditoria.recordSecurityAfterCommit(
          new SecurityEvent(
              SecurityEventType.EMAIL_CHANGED,
              Severity.ALTA,
              Outcome.SUCCESS,
              usuario.getId(),
              Map.of("before", correoAnterior, "after", usuario.getEmail(), "self", true)));
    }
  }
}
