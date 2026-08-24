package com.factech.nexus.modules.system.users.domain.security;

import com.factech.nexus.modules.system.users.domain.repository.AssignableRole;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * `RN-SEG-010` en un solo sitio: <b>nadie concede permisos que no posee</b>.
 *
 * <p>Existe porque tres requerimientos —`RF-SP-024`, `RF-SP-030` y `RF-SP-031`— tienen que
 * comprobar exactamente lo mismo, y <b>tres copias de una comprobación divergen</b>. La que se
 * quede atrás no falla: concede. Ese es el modo de fallo que este componente evita, y es la razón
 * por la que `RF-SP-030` §3 declara que ese requerimiento «no crea ni un componente de dominio
 * nuevo».
 *
 * <p>Se compara <b>permiso a permiso y no rol a rol</b>. Dos roles distintos pueden conceder lo
 * mismo, de modo que comparar identificadores de rol rechazaría concesiones legítimas; y comparar
 * posición en la jerarquía rechazaría a un administrador que posee todo lo que un rol declara sin
 * portar ese rol. Lo que la regla protege es la <b>escalada de privilegios</b>, y un privilegio es
 * un permiso.
 *
 * <p>Devuelve <b>todos</b> los infractores y no el primero: quien recibe el rechazo tiene que poder
 * corregir su petición de una vez.
 *
 * <p>Es una función pura sobre datos ya cargados. No consulta, no lanza y no decide códigos de
 * estado — quien la llama redacta el error, porque el mismo incumplimiento se comunica distinto
 * según la operación.
 */
public final class PrivilegeContainment {

  private PrivilegeContainment() {}

  /**
   * Los roles cuyos permisos <b>no</b> están contenidos en los del actor.
   *
   * @param concedidos roles que la operación pretende conceder o retirar
   * @param permisosDelActor permisos efectivos del actor, resueltos contra la base
   * @return los infractores, en el orden en que llegaron; vacío si la operación es admisible
   */
  public static List<AssignableRole> excesos(
      Collection<AssignableRole> concedidos, Set<String> permisosDelActor) {
    return concedidos.stream()
        .filter(rol -> !permisosDelActor.containsAll(rol.permissionCodes()))
        .toList();
  }

  /**
   * La regla también gobierna el <b>retiro</b>, y no solo la concesión.
   *
   * <p>No es simetría decorativa: quien no posee `audit:read-security` no puede retirar el rol que
   * lo concede, porque hacerlo es alterar un privilegio que no alcanza. `RF-SP-031` §4 lo verifica
   * en su paso 3, y lo hace <b>sobre los permisos que el rol declara, exista o no en el catálogo
   * vigente</b> — un rol eliminado del catálogo sigue teniendo asignaciones que soltar.
   */
  public static boolean loAlcanza(AssignableRole rol, Set<String> permisosDelActor) {
    return permisosDelActor.containsAll(rol.permissionCodes());
  }
}
