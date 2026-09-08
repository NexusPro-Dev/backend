package com.factech.nexus.modules.system.users.domain.service;

import com.factech.nexus.modules.system.users.application.UpdateUserRequest;
import com.factech.nexus.modules.system.users.application.UserResponse;
import com.factech.nexus.modules.system.users.domain.models.Email;
import com.factech.nexus.modules.system.users.domain.models.User;
import com.factech.nexus.modules.system.users.domain.repository.AssignableCountry;
import com.factech.nexus.modules.system.users.domain.repository.AssignableRole;
import com.factech.nexus.modules.system.users.domain.repository.RoleCatalog;
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
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.patch.Patchable;
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
 * Editar el nombre, los apellidos y el correo de una persona (`RF-SP-027`).
 *
 * <p>Orden de verificación (`plan.md` §4), y dos pasos de ese orden importan más de lo que parece:
 *
 * <ol>
 *   <li>Formato y obligatoriedad, <b>todas juntas</b>.
 *   <li>La persona existe y no está eliminada, <b>con bloqueo de fila</b>.
 *   <li><b>Normalización</b> de lo recibido: recorte en los tres, minúsculas en el correo.
 *   <li><b>Detección del cambio efectivo</b>, contra el estado ya cargado.
 *   <li>Unicidad del correo, <b>solo si el correo cambió</b>.
 *   <li>Escritura y auditoría.
 * </ol>
 *
 * <p><b>La unicidad va después de detectar el cambio</b>, de modo que reenviar el correo actual no
 * dispara ninguna consulta ni puede producir un conflicto de la persona consigo misma. Y <b>la
 * normalización va antes de comparar</b>, o {@code " JUAN.PEREZ@FACTECH.CO "} parecería un cambio y
 * dejaría un evento de auditoría de algo que no cambió.
 *
 * <p><b>El actor SÍ puede editarse a sí mismo</b>, y es una asimetría deliberada con el cambio de
 * estado y la eliminación: corregir el propio apellido no concede ningún privilegio. `RN-SP-017`
 * protege el acceso, no los datos de contacto.
 */
@Service
public class UpdateUserService {

  private static final String MODULO = "SP";
  private static final String ENTIDAD = "users";

  private final UserRepository usuarios;
  private final RoleCatalog roles;
  private final AssignableCountry paises;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public UpdateUserService(
      UserRepository usuarios, RoleCatalog roles, AuditWriter auditoria, AssignableCountry paises) {
    this(usuarios, roles, auditoria, paises, Clock.systemUTC());
  }

  UpdateUserService(
      UserRepository usuarios,
      RoleCatalog roles,
      AuditWriter auditoria,
      AssignableCountry paises,
      Clock reloj) {
    this.usuarios = usuarios;
    this.roles = roles;
    this.paises = paises;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public UserResponse update(UUID userId, UpdateUserRequest peticion) {
    // 1. Formato y obligatoriedad, todas juntas.
    List<FieldError> problemas = new ArrayList<>();
    if (!peticion.informaAlgo()) {
      problemas.add(
          new FieldError(
              null, "VAL-001", "Debe informar al menos uno de los campos modificables."));
    }
    String nombre = normalizar("firstName", peticion.firstName(), problemas);
    String apellido = normalizar("lastName", peticion.lastName(), problemas);
    String correoBruto = normalizar("email", peticion.email(), problemas);
    // EL NULO EXPLÍCITO SE RECHAZA, igual que en los otros tres campos y por lo
    // mismo: `country_id` es `NOT NULL` y el estado «persona sin país» no
    // existe (`RN-SP-034`). Aceptarlo en silencio daría un `500` por violación
    // de integridad en lugar del `400` que corresponde.
    UUID paisPedido = null;
    if (peticion.countryId().presente()) {
      paisPedido = peticion.countryId().valor();
      if (paisPedido == null) {
        problemas.add(new FieldError("countryId", "VAL-006", "El país no puede quedar vacío."));
      }
    }

    if (!problemas.isEmpty()) {
      throw new ValidationException(problemas.get(0).code(), problemas.get(0).message(), problemas);
    }

    // 2. La persona, bloqueada.
    User usuario =
        usuarios
            .findNotDeletedByIdForUpdate(userId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-002", "No existe una persona con ese identificador."));

    OffsetDateTime ahora = OffsetDateTime.now(reloj);
    String correoAnterior = usuario.getEmail();
    String nombreAnterior = usuario.getFirstName();
    String apellidoAnterior = usuario.getLastName();

    // 3. La normalización del correo la hace su propio tipo, y no este caso de
    //    uso: escribirla aquí produciría una segunda normalización que divergiría
    //    de la que el alta aplica.
    String correo = correoBruto == null ? null : new Email(correoBruto).value();

    // 4 y 5. El cambio se detecta en el agregado; la unicidad solo si cambió.
    boolean cambiaNombre = usuario.rename(nombre, apellido, ahora);
    boolean cambiaCorreo = correo != null && !correo.equals(correoAnterior);

    // SE VERIFICA EL PAÍS DE DESTINO, NUNCA EL ACTUAL, y esa es la decisión que
    // hace utilizable esta operación: es la herramienta con la que se saca a
    // alguien de un país recién desactivado, de modo que exigir que el vigente
    // estuviera activo la volvería inútil justo cuando hace falta.
    //
    // Y se verifica solo si CAMBIA: reenviar el mismo país no consulta el
    // catálogo, no audita nada y no puede fallar aunque ese país esté hoy
    // inactivo.
    UUID paisAnterior = usuario.getCountryId();
    boolean cambiaPais = paisPedido != null && !paisPedido.equals(paisAnterior);
    if (cambiaPais) {
      verificarPais(paisPedido);
      usuario.changeCountry(paisPedido, ahora);
    }

    if (cambiaCorreo) {
      verificarCorreoLibre(correo);
      usuario.changeEmail(correo, ahora);

      // El volcado se pide AQUÍ y no se deja al commit, y esa es la diferencia
      // entre un `409` y un `500`. `verificarCorreoLibre` existe para el
      // mensaje; entre su lectura y esta escritura hay una ventana que dos
      // ediciones simultáneas hacia el mismo correo atraviesan las dos. La
      // segunda la rechaza `uq_users_email`, y esa violación solo puede
      // traducirse mientras siga habiendo alguien escuchando — en el commit ya
      // no lo hay.
      usuarios.flushChanges();
    }

    if (cambiaNombre || cambiaCorreo || cambiaPais) {
      auditar(
          usuario,
          nombreAnterior,
          apellidoAnterior,
          correoAnterior,
          paisAnterior,
          cambiaNombre,
          cambiaCorreo,
          cambiaPais);
    }

    List<AssignableRole> catalogo = roles.findAllById(roles.roleIdsOf(userId));
    return UserResponses.de(usuario, catalogo, usuarios, paises, userId);
  }

  /**
   * `VAL-002`: el nulo explícito y el blanco se rechazan, y el ausente pasa.
   *
   * <p>El {@code Optional} nulo significa «no se envió»; {@code Optional.empty()} significa «se
   * envió nulo». La segunda forma <b>no puede ser una orden</b>, porque las tres columnas son
   * {@code NOT NULL}: aceptarla produciría una violación de integridad traducida a {@code 500} en
   * lugar del {@code 400} que corresponde.
   */
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
   * `EX-003` → {@code 422} si el país no existe, {@code 409} si está inactivo (`RN-SP-034`).
   *
   * <p>Misma correspondencia que el alta: inexistente es una referencia que no resuelve, inactivo
   * es una que resuelve y que una regla rechaza. Y misma razón para distinguirlos — quien edita ya
   * tiene {@code users:update} y ve el catálogo de todos modos, de modo que separarlos no revela
   * nada y sí le dice qué hacer.
   */
  private void verificarPais(UUID countryId) {
    var pais =
        paises
            .find(countryId)
            .orElseThrow(
                () ->
                    new UnprocessableEntityException(
                        "EX-003",
                        "El país indicado no existe.",
                        List.of(
                            new FieldError(
                                "countryId",
                                "EX-003",
                                "El país '" + countryId + "' no existe en el catálogo."))));

    if (!pais.active()) {
      String mensaje = "El país '" + pais.code() + "' está inactivo y no se puede asignar.";
      throw new BusinessRuleException(
          "RN-SP-034", mensaje, List.of(new FieldError("countryId", "RN-SP-034", mensaje)));
    }
  }

  /**
   * `EX-001` → {@code 409}, y el mensaje <b>no dice de quién es el correo</b>.
   *
   * <p>`RN-SP-016` reserva el correo de los eliminados <b>para siempre</b>, de modo que el
   * conflicto puede ser con alguien que ya no está; decirlo informaría de la existencia de una
   * cuenta que la respuesta no debe revelar.
   *
   * <p>Esta consulta existe <b>para el mensaje</b>. La garantía la da {@code uq_users_email}, que
   * rechazaría la operación igualmente en el volcado — y el adaptador traduce esa violación al
   * mismo {@code 409}, distinguiéndola por el <b>nombre de la restricción</b> y nunca por el texto
   * del driver.
   */
  private void verificarCorreoLibre(String correo) {
    if (usuarios.existsEmail(new Email(correo))) {
      String mensaje = "Ese correo ya está en uso.";
      throw new BusinessRuleException(
          "RN-SP-016", mensaje, List.of(new FieldError("email", "RN-SP-016", mensaje)));
    }
  }

  /**
   * Un evento de cambio con solo <b>lo que cambió</b>, y uno de seguridad <b>solo si cambió el
   * correo</b>.
   *
   * <p>El correo es la identidad con la que se entra y la llave de la recuperación de contraseña:
   * cambiarlo es un hecho de seguridad y no una corrección de datos, y por eso lleva su propio
   * evento con severidad alta. Corregir un apellido no lo es.
   */
  private void auditar(
      User usuario,
      String nombreAnterior,
      String apellidoAnterior,
      String correoAnterior,
      UUID paisAnterior,
      boolean cambiaNombre,
      boolean cambiaCorreo,
      boolean cambiaPais) {

    Map<String, Object> cambios = new HashMap<>();
    if (cambiaNombre) {
      cambios.put("first_name", Map.of("before", nombreAnterior, "after", usuario.getFirstName()));
      cambios.put("last_name", Map.of("before", apellidoAnterior, "after", usuario.getLastName()));
    }
    if (cambiaCorreo) {
      cambios.put("email", Map.of("before", correoAnterior, "after", usuario.getEmail()));
    }
    // EL PAÍS VA AQUÍ Y NO EN EL EVENTO DE SEGURIDAD, y es el campo que más se
    // le parece al correo: los dos los corrige un tercero y los dos cambian cómo
    // la persona usa el sistema. La diferencia es que el correo ES UNA VÍA DE
    // ACCESO y el país no. Cambiárselo a alguien altera qué medios de pago se le
    // ofrecen (`RN-MV-019`) —una decisión comercial con consecuencias—, y eso se
    // responde aquí. Meterlo además en `audit_security_log` diluiría un registro
    // que existe para credenciales y vías de acceso, y que no se purga sin
    // decisión documentada (Art. XV.8).
    //
    // Y se guarda el IDENTIFICADOR, no el código: es lo que quedó en la columna.
    if (cambiaPais) {
      cambios.put(
          "country_id",
          Map.of("before", paisAnterior.toString(), "after", usuario.getCountryId().toString()));
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
              Map.of("before", correoAnterior, "after", usuario.getEmail())));
    }
  }
}
