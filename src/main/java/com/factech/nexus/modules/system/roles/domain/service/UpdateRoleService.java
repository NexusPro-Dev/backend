package com.factech.nexus.modules.system.roles.domain.service;

import com.factech.nexus.modules.system.roles.application.RoleResponse;
import com.factech.nexus.modules.system.roles.application.UpdateRoleRequest;
import com.factech.nexus.modules.system.roles.domain.models.Role;
import com.factech.nexus.modules.system.roles.domain.repository.RoleRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEnums.Outcome;
import com.factech.nexus.shared.audit.AuditEnums.SecurityEventType;
import com.factech.nexus.shared.audit.AuditEnums.Severity;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditEvents.SecurityEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
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
 * Editar el nombre y la descripción de un rol (`RF-SP-004`).
 *
 * <p>Orden de verificación, y dos pasos importan más de lo que parece:
 *
 * <ol>
 *   <li>Formato y obligatoriedad, <b>todas juntas</b> (`VAL-001`, `VAL-002`, `VAL-004`).
 *   <li>Las tres puertas de {@link RoleWriteAccess}: existe, no es de sistema, no es del actor.
 *   <li><b>Detección del cambio efectivo</b>, contra el estado ya cargado.
 *   <li>Unicidad del nombre, <b>solo si el nombre cambió</b>.
 *   <li>Escritura y auditoría.
 * </ol>
 *
 * <p><b>La unicidad va después de detectar el cambio</b>, de modo que reenviar el nombre actual no
 * dispara consulta alguna ni puede producir un conflicto del rol consigo mismo (`spec.md` §13). Y
 * el recorte de espacios lo hace el agregado <b>antes</b> de comparar, o {@code "Contabilidad "}
 * parecería un cambio y dejaría un evento de auditoría de algo que no cambió.
 *
 * <p><b>Gana el último en escribir</b> y no hay versión optimista: `spec.md` §14 lo decidió así —el
 * dato en juego es un nombre o una descripción, y dos administradores editando el mismo rol a la
 * vez es remoto—, y la auditoría de cambios conserva ambas ediciones, de modo que el cambio perdido
 * es reconstruible. El bloqueo de fila las serializa en lugar de mezclarlas.
 */
@Service
public class UpdateRoleService {

  private static final String MODULO = "SP";
  private static final String ENTIDAD = "roles";
  private static final int LONGITUD_MAXIMA_DESCRIPCION = 500;

  private final RoleRepository roles;
  private final RoleWriteAccess acceso;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public UpdateRoleService(RoleRepository roles, RoleWriteAccess acceso, AuditWriter auditoria) {
    this(roles, acceso, auditoria, Clock.systemUTC());
  }

  UpdateRoleService(
      RoleRepository roles, RoleWriteAccess acceso, AuditWriter auditoria, Clock reloj) {
    this.roles = roles;
    this.acceso = acceso;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public RoleResponse update(UUID roleId, UpdateRoleRequest peticion) {
    verificarFormato(peticion);

    Role rol = acceso.cargarModificable(roleId, "EX-004");

    String nombreAnterior = rol.getName();
    String descripcionAnterior = rol.getDescription();
    OffsetDateTime ahora = OffsetDateTime.now(reloj);

    // La unicidad se comprueba ANTES de tocar el agregado, y el orden no es
    // estético: la consulta dispara el vaciado automático de Hibernate, de modo
    // que con el nombre nuevo ya escrito en la entidad el propio `SELECT`
    // provocaría la violación del índice único antes de llegar a evaluarse — un
    // `500` donde corresponde un `409`.
    //
    // Se comprueba solo si el nombre CAMBIA: un nombre no entra en conflicto
    // consigo mismo, y reenviar el actual no debe disparar consulta alguna. La
    // garantía sigue siendo `uq_roles_name`; esto solo redacta el mensaje.
    String nombreNuevo = peticion.name().presente() ? peticion.name().valor().trim() : null;
    if (nombreNuevo != null && !nombreNuevo.equals(nombreAnterior)) {
      verificarNombreLibre(nombreNuevo, roleId);
    }

    boolean cambiaNombre = peticion.name().presente() && rol.rename(nombreNuevo, ahora);
    boolean cambiaDescripcion =
        peticion.description().presente() && rol.redescribe(peticion.description().valor(), ahora);

    if (cambiaNombre || cambiaDescripcion) {
      auditar(rol, nombreAnterior, descripcionAnterior, cambiaNombre, cambiaDescripcion);
    }
    // `FA-001`: sin cambio efectivo no hay evento y tampoco error. Se devuelve
    // el rol tal cual, que es lo que el cliente pidió ver.
    return acceso.respuesta(rol);
  }

  /** Las tres validaciones se evalúan <b>juntas</b> y se devuelven juntas. */
  private static void verificarFormato(UpdateRoleRequest peticion) {
    List<FieldError> problemas = new ArrayList<>();

    if (!peticion.informaAlgo()) {
      problemas.add(new FieldError(null, "VAL-001", "Debe indicar al menos un campo a modificar."));
    }
    if (peticion.name().presente()) {
      String nombre = peticion.name().valor() == null ? "" : peticion.name().valor().trim();
      if (nombre.isEmpty()) {
        // El nulo explícito NO es una orden aquí: la columna es NOT NULL, y
        // aceptarlo produciría una violación de integridad traducida a 500 en
        // lugar del 400 que corresponde.
        problemas.add(new FieldError("name", "VAL-002", "El nombre del rol no puede estar vacío."));
      } else if (nombre.length() > Role.longitudMaximaNombre()) {
        problemas.add(new FieldError("name", "VAL-004", "El nombre excede la longitud permitida."));
      }
    }
    if (peticion.description().presente()
        && peticion.description().valor() != null
        && peticion.description().valor().trim().length() > LONGITUD_MAXIMA_DESCRIPCION) {
      problemas.add(
          new FieldError("description", "VAL-004", "La descripción excede la longitud permitida."));
    }

    if (!problemas.isEmpty()) {
      throw new ValidationException(problemas.get(0).code(), problemas.get(0).message(), problemas);
    }
  }

  private void verificarNombreLibre(String nombre, UUID roleId) {
    if (roles.existsActiveNameForOther(nombre, roleId)) {
      String mensaje = "Ya existe un rol con ese nombre.";
      throw new BusinessRuleException(
          "RN-SEG-001", mensaje, List.of(new FieldError("name", "RN-SEG-001", mensaje)));
    }
  }

  /**
   * Un evento de cambio con <b>solo lo que cambió</b> y uno de seguridad de severidad alta.
   *
   * <p>El de seguridad lo exige `security.md` §8.1 para toda modificación de rol, y no distingue
   * qué campo se tocó: lo que importa es que alguien alteró una pieza del control de accesos.
   */
  private void auditar(
      Role rol,
      String nombreAnterior,
      String descripcionAnterior,
      boolean cambiaNombre,
      boolean cambiaDescripcion) {

    Map<String, Object> cambios = new HashMap<>();
    if (cambiaNombre) {
      cambios.put("name", mapaDeCambio(nombreAnterior, rol.getName()));
    }
    if (cambiaDescripcion) {
      cambios.put("description", mapaDeCambio(descripcionAnterior, rol.getDescription()));
    }

    auditoria.recordChange(
        new ChangeEvent(MODULO, ENTIDAD, rol.getId(), ChangeAction.UPDATE, cambios));

    auditoria.recordSecurityAfterCommit(
        new SecurityEvent(
            SecurityEventType.ROLE_UPDATED,
            Severity.ALTA,
            Outcome.SUCCESS,
            null,
            Map.of(
                "roleId", rol.getId().toString(),
                "roleCode", rol.getCode().value(),
                "fields", cambios.keySet().stream().sorted().toList())));
  }

  /**
   * {@code HashMap} y no {@code Map.of}: la descripción puede ser nula en cualquiera de los dos
   * lados —borrarla es una orden legítima— y las fábricas inmutables del JDK rechazan nulos.
   */
  private static Map<String, Object> mapaDeCambio(String antes, String despues) {
    Map<String, Object> cambio = new HashMap<>();
    cambio.put("before", antes);
    cambio.put("after", despues);
    return cambio;
  }
}
