package com.factech.nexus.modules.system.users.application;

import java.util.UUID;

/**
 * ¿Porta esta persona este permiso? (**D-25**, `RF-AC-008` · `T-03`).
 *
 * <p>Es la primera lectura que `SP` publica <b>sobre un permiso y no sobre un dato</b>, y la pide
 * `AC`: el instructor de un curso tiene que portar {@code courses:teach} (`RN-AC-006`), y `AC` no
 * puede leer {@code user_roles} ni {@code role_permissions} — es lo que `modules.md` §7 prohíbe y
 * la regla de ArchUnit de `RF-AC-001` impide compilar.
 *
 * <h2>Un booleano sobre un código, y no la lista</h2>
 *
 * <p>Publicar «los permisos de esta persona» daría a cualquier módulo con qué reconstruir fuera de
 * `SP` la autorización que es suya — y con qué equivocarse al hacerlo. Responder sí o no sobre un
 * código no da nada más que lo que se preguntó. Es la misma disciplina con la que {@link
 * CurrentMembershipLookup} devuelve la membresía ya evaluada y no la fecha con la que evaluarla.
 *
 * <h2>Lo que «portar» significa, y dónde vive</h2>
 *
 * <p>Por un rol <b>vivo y {@code ACTIVO}</b> de una persona <b>no retirada</b>: exactamente el
 * predicado con el que {@code JpaEffectivePermissions} resuelve los permisos efectivos para
 * `RN-SEG-010`, escrito una vez y compartido, para que un rol inactivo no conceda en un sitio y sí
 * en el otro. Una persona inexistente, retirada, o un permiso que no existe responden <b>falso</b>,
 * sin distinguirse: quien pregunta ya comprobó la existencia por {@link UserCatalog}.
 *
 * <p>Ver `architecture.md` §15.2 y `requirements/sp.md` §8.
 */
public interface PermissionHolderLookup {

  /**
   * Si la persona porta el permiso por algún rol vivo y activo.
   *
   * @param userId identificador de la persona; un valor nulo devuelve falso en lugar de fallar
   * @param permissionCode código del permiso, tal como está en el catálogo ({@code courses:teach})
   */
  boolean holds(UUID userId, String permissionCode);
}
