package com.factech.nexus.modules.system.roles.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Lo que `SP` publica de sus roles para que otro módulo pueda consultarlos (**D-25**).
 *
 * <p><b>La declara el módulo dueño del dato y la importa el consumidor</b>, como fija
 * `architecture.md` §15.2. La dependencia apunta del consumidor al proveedor y `SP` no se entera de
 * quién la usa.
 *
 * <p><b>La escribe quien la necesita.</b> Nace con `RF-CM-001` · `T-03`, porque `CM` necesita
 * comprobar que el rol de una tarifa es de tipo vendedor (`RN-CM-001`) y devolverlo resuelto. No es
 * un requerimiento nuevo de `SP`: ningún actor pide «publicar una interfaz» como comportamiento.
 *
 * <p><b>{@code roleType} es el motivo de que esta interfaz exista.</b> Sin él, el consumidor no
 * puede distinguir un rol vendedor de uno que no lo es, y `RN-CM-001` pasaría a comprobarse leyendo
 * la tabla de otro módulo — que es justo lo que `modules.md` §7 prohíbe.
 *
 * <p><b>La ausencia es {@link Optional#empty()}, no una excepción</b>: qué {@code 4xx} produce lo
 * decide quien tiene el contrato HTTP.
 */
public interface RoleCatalog {

  /**
   * El rol, si existe.
   *
   * @param id identificador del rol; un valor nulo devuelve vacío en lugar de fallar
   */
  Optional<RoleView> find(UUID id);

  /**
   * Lo que cruza la frontera: datos planos, sin comportamiento y sin entidad.
   *
   * <p><b>{@code deleted} viaja</b> porque un rol eliminado sigue existiendo para la auditoría, y
   * quien consume decide si le sirve. Devolver vacío en su lugar escondería la diferencia entre
   * «nunca existió» y «se eliminó», que no son lo mismo.
   *
   * @param roleType el valor tal como lo persiste {@code ck_roles_type}: `FUNCIONARIO`, `VENDEDOR`
   *     o `CONSUMIDOR`
   */
  record RoleView(UUID id, String code, String name, String roleType, boolean deleted) {

    /** ¿Es un rol de tipo vendedor? Es la pregunta que `RN-CM-001` hace. */
    public boolean esVendedor() {
      return "VENDEDOR".equals(roleType);
    }
  }
}
