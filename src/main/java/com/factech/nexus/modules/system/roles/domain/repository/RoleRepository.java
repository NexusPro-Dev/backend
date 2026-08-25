package com.factech.nexus.modules.system.roles.domain.repository;

import com.factech.nexus.modules.system.roles.domain.models.Role;
import com.factech.nexus.modules.system.roles.domain.models.RoleCode;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Puerto de persistencia del agregado {@link Role} (`RF-SP-001` · `T-11`).
 *
 * <p>Las dos comprobaciones de existencia no son lo que garantiza `RN-SEG-001`: eso lo hacen los
 * índices únicos parciales del esquema. Existen <b>para poder dar un mensaje preciso</b> —cuál de
 * los dos está duplicado, el código o el nombre— antes de intentar la inserción. La restricción
 * decide; el {@code SELECT} solo redacta.
 *
 * <p>Verificar la unicidad <b>solo</b> con estas consultas no serviría: dos altas simultáneas con
 * el mismo código pasan ambas la comprobación y la segunda moriría con un error de integridad
 * convertido en {@code 500}, que es justo el caso límite que `spec.md` §13 prohíbe.
 */
public interface RoleRepository {

  /**
   * Persiste un rol nuevo.
   *
   * @throws com.factech.nexus.shared.error.BusinessRuleException si el alta viola {@code
   *     uq_roles_code} o {@code uq_roles_name}, distinguiendo cuál de los dos
   */
  Role save(Role rol);

  /**
   * Busca por identificador, incluidos los eliminados lógicamente: quien llama decide qué hacer.
   */
  Optional<Role> findById(UUID id);

  /** ¿Hay ya un rol no eliminado con ese código? (`RN-SEG-001`) */
  boolean existsActiveCode(RoleCode code);

  /** ¿Hay ya un rol no eliminado con ese nombre? (`RN-SEG-001`) */
  boolean existsActiveName(String name);

  // ---------------------------------------------------------------------------
  // Escrituras posteriores al alta (`RF-SP-004` a `RF-SP-009`)
  // ---------------------------------------------------------------------------

  /**
   * El rol vigente, <b>con la fila bloqueada</b> hasta el fin de la transacción.
   *
   * <p>El bloqueo no es precaución genérica: las seis operaciones de escritura leen el rol, deciden
   * sobre lo leído y escriben. Sin él, dos peticiones simultáneas deciden sobre el mismo estado y
   * la segunda pisa a la primera — y en `RF-SP-008` eso no es un cambio perdido sino una jerarquía
   * con un ciclo, porque cada una comprueba la descendencia que la otra está a punto de cambiar
   * (`CA-SP-161`).
   *
   * @return vacío si no existe o está eliminado: para estas operaciones son el mismo hecho
   */
  Optional<Role> findNotDeletedByIdForUpdate(UUID id);

  /**
   * ¿Hay <b>otro</b> rol no eliminado con ese nombre? (`RF-SP-004`, `EX-003`)
   *
   * <p>Excluye al propio rol, porque un nombre no entra en conflicto consigo mismo: sin esa
   * exclusión, reenviar el nombre actual daría un {@code 409} contra uno mismo.
   */
  boolean existsActiveNameForOther(String name, UUID roleId);

  /**
   * ¿El rol está asignado <b>directamente</b> a esa persona? (`RN-SEG-011`)
   *
   * <p>Directamente y no por ancestros: `RF-SP-004` §14 lo resolvió así porque `RN-SEG-010` ya
   * impide conceder permisos que no se poseen, de modo que tocar un ancestro no permite ganar nada,
   * y extender la regla añadiría un recorrido del árbol en cada escritura sin cerrar ningún hueco.
   */
  boolean isAssignedTo(UUID roleId, UUID userId);

  /**
   * Códigos de los roles hijos <b>vigentes</b> (`RF-SP-009`, `EX-002`).
   *
   * <p>Devuelve los códigos y no un conteo porque la respuesta debe informar <b>cuáles</b> lo
   * impiden: un rechazo que solo dice «tiene hijos» obliga a buscarlos a mano.
   */
  List<String> childCodesOf(UUID roleId);

  /**
   * Cuántas personas vigentes tienen el rol asignado (`RF-SP-009`, `EX-003`).
   *
   * <p>En <b>cualquier estado</b> —activo, inactivo, bloqueado— y excluyendo las eliminadas: quien
   * está bloqueado sigue portando el rol, y es justo su existencia lo que `RN-SEG-008` protege.
   */
  long countAssignedUsers(UUID roleId);

  /**
   * ¿{@code candidato} es el propio rol o desciende de él? (`RF-SP-008`, `RN-SEG-006`)
   *
   * <p>Recorre la descendencia con profundidad acotada: una jerarquía ya corrupta —con un ciclo
   * introducido por fuera de la API— haría que un recorrido sin tope no terminara nunca, y el
   * síntoma sería una petición colgada y no un error.
   */
  boolean isSelfOrDescendant(UUID candidato, UUID roleId);

  /**
   * Roles hijos <b>directos</b> que declaran alguno de esos permisos (`RF-SP-006`, `RN-SEG-005`).
   *
   * <p>Directos y no toda la descendencia: si el hijo no declara el permiso, el nieto tampoco puede
   * declararlo — la contención es transitiva. Incluye los <b>inactivos</b> (`CA-SP-155`), porque el
   * invariante vale siempre y no solo mientras el rol concede algo, y excluye los eliminados.
   */
  List<PermissionHolder> childrenDeclaring(UUID roleId, Set<UUID> permissionIds);

  /** Qué rol hijo impide retirar qué permiso. Ambos por código, para que el mensaje sea legible. */
  record PermissionHolder(String roleCode, String permissionCode) {}
}
