package com.factech.nexus.modules.system.roles.application;

import com.factech.nexus.modules.system.roles.domain.models.RoleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/roles} (`RF-SP-001` · `T-16`).
 *
 * <p><b>No existe el campo {@code status}.</b> El rol nace activo y el cuerpo se deserializa con
 * {@code FAIL_ON_UNKNOWN_PROPERTIES} activo: enviar {@code "status": "INACTIVO"} devuelve {@code
 * 400} y <b>no se ignora en silencio</b>. Es lo que hace verificable a `CA-SP-146` — sin ese
 * rechazo, el criterio comprobaría que el campo se ignora, que no es lo mismo que comprobar que no
 * hay camino hacia {@code INACTIVO}.
 *
 * <p><b>Tampoco existe {@code isSystem}.</b> Un rol creado por la API nunca es de sistema; el valor
 * lo fija el {@code DEFAULT false} del esquema y solo {@code V7} lo pone en {@code true}.
 *
 * <p><b>Los permisos se declaran por identificador, no por código.</b> El resto del contrato ya
 * referencia entidades por UUID —{@code parentRoleId}—, y mezclar dos espacios de identificación en
 * el mismo cuerpo obliga al cliente a decidir cuál usar. La legibilidad que se pierde se recupera
 * en la respuesta, que devuelve identificador, código y nombre de cada permiso.
 *
 * <p><b>El código de cada validación viaja como prefijo del mensaje.</b> Las anotaciones estándar
 * de Bean Validation no admiten un atributo propio para él, y el manejador global lo separa. La
 * alternativa era un catálogo paralelo campo-a-código que habría que mantener sincronizado con
 * estas anotaciones.
 *
 * @param permissionIds admite ausencia, {@code null} y lista vacía, los tres con el mismo
 *     significado: alta sin permisos (`FA-001`)
 */
public record CreateRoleRequest(
    @NotBlank(message = "VAL-001: El código del rol es obligatorio.")
        @Size(max = 50, message = "VAL-007: El código del rol no puede exceder 50 caracteres.")
        @Pattern(
            regexp = "^[A-Z][A-Z0-9_]*$",
            message =
                "VAL-008: El código solo admite letras mayúsculas, dígitos y guion bajo, y debe"
                    + " empezar por letra.")
        String code,
    @NotBlank(message = "VAL-002: El nombre del rol es obligatorio.")
        @Size(max = 100, message = "VAL-007: El nombre del rol no puede exceder 100 caracteres.")
        String name,
    @Size(max = 500, message = "VAL-007: La descripción no puede exceder 500 caracteres.")
        String description,
    @NotNull(message = "VAL-003: La clasificación del rol no es válida.") RoleType roleType,
    @NotNull(message = "VAL-004: El rol padre es obligatorio.") UUID parentRoleId,
    List<UUID> permissionIds) {

  /**
   * Normaliza <b>antes</b> de que corran las validaciones.
   *
   * <p>Jackson construye el registro por este constructor y Bean Validation se ejecuta después, de
   * modo que aquí es donde el recorte llega a tiempo para importar. Y sí importa: sin él, {@code
   * "Contabilidad "} y {@code "Contabilidad"} serían dos nombres distintos para {@code
   * uq_roles_name} y la unicidad se burlaría con un espacio.
   *
   * <p><b>El código no se recorta.</b> Se rechaza si no cumple el formato, para que el actor vea
   * exactamente qué código quedó registrado (`spec.md` §13).
   *
   * <p><b>Los permisos duplicados se colapsan a una sola ocurrencia, sin error</b> (`spec.md` §13).
   * Se conserva el orden de llegada porque la respuesta enumera los infractores de `EX-003` y
   * `EX-004`, y un orden estable hace comparable la salida entre dos peticiones iguales.
   */
  public CreateRoleRequest {
    name = recortar(name);
    description = recortar(description);
    permissionIds =
        permissionIds == null
            ? List.of()
            : List.copyOf(
                new LinkedHashSet<>(permissionIds.stream().filter(id -> id != null).toList()));
  }

  private static String recortar(String valor) {
    return valor == null ? null : valor.trim();
  }

  /** Comando equivalente, ya sin nada del transporte. */
  public CreateRoleCommand toCommand() {
    Set<UUID> ids = new LinkedHashSet<>(permissionIds);
    return new CreateRoleCommand(code, name, description, roleType, parentRoleId, ids);
  }
}
