package com.factech.nexus.modules.system.users.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Lo que `SP` publica de sus personas para que otro módulo pueda consultarlas (**D-25**).
 *
 * <p>Nace con `RF-CM-001` · `T-04`, porque `CM` necesita comprobar que la persona de una excepción
 * existe y devolverla resuelta en la respuesta.
 *
 * <p><b>Publica la identidad y nada más.</b> No viaja el correo, que es una vía de acceso, ni el
 * estado, ni los roles: una interfaz por lectura y no una fachada (`architecture.md` §15.2). El
 * nombre de usuario es <b>inmutable</b> (`RN-SP-016`) y por eso es la identidad que se publica; el
 * nombre completo es el actual, y quien lo muestre debe saberlo.
 *
 * <p><b>No filtra por eliminado</b>, y devuelve la marca: `RF-SP-029` elimina de forma lógica, y
 * una tarifa declarada sobre alguien que después se dio de baja sigue explicando lo que ganó
 * mientras estuvo.
 */
public interface UserCatalog {

  /**
   * La persona, si existe.
   *
   * @param id identificador de la persona; un valor nulo devuelve vacío en lugar de fallar
   */
  Optional<UserView> find(UUID id);

  /** Lo que cruza la frontera: datos planos, sin comportamiento y sin entidad. */
  record UserView(UUID id, String username, String fullName, boolean deleted) {}
}
