package com.factech.nexus.modules.system.users.application;

import java.util.Optional;

/**
 * El nombre de quien reparte un enlace, para el hotlink público (`RF-PM-008`, **D-25**).
 *
 * <p><b>Devuelve VACÍO cuando la persona no es fuerza comercial</b>, y no un objeto que `PM` tenga
 * que filtrar. La regla de quién es publicable depende de los <b>roles</b>, que son de `SP`: si
 * este puerto devolviera a cualquiera y `PM` decidiera, la definición de «fuerza comercial» viviría
 * en dos módulos y el segundo se quedaría atrás <b>sin que nada fallara</b>.
 *
 * <p>Es el mismo criterio con el que {@code CurrentMembershipLookup} devuelve la membresía <b>ya
 * evaluada</b> en lugar de su fecha de fin.
 *
 * <p><b>Publica dos campos y solo dos</b> (`RN-PM-022`): ni correo, ni identificador, ni estado, ni
 * roles. Lo que no cruza la frontera no se puede publicar por descuido al otro lado.
 */
public interface PublicSellerLookup {

  /**
   * @param username el nombre de usuario, comparado <b>sin distinguir mayúsculas</b>: un enlace se
   *     teclea y se comparte por mensajería, y exigir la caja exacta rompería la mitad de las
   *     visitas
   * @return vacío si no existe, si no está activa o si no porta un rol de tipo {@code VENDEDOR} —
   *     <b>los tres casos iguales</b>, para que quien consuma no pueda distinguirlos
   */
  Optional<PublicSellerView> findSellerByUsername(String username);

  /** Nombre y apellido. Nada más. */
  record PublicSellerView(String firstName, String lastName) {}
}
