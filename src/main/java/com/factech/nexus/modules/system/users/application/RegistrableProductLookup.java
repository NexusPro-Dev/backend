package com.factech.nexus.modules.system.users.application;

import java.util.Optional;
import java.util.UUID;

/**
 * El producto de un enlace de registro, publicado <b>por `PM` para `SP`</b> (`RF-SP-045` · `T-05`).
 *
 * <h2>Este puerto invierte la dirección de las otras tres, y esa es su razón de ser</h2>
 *
 * <p>`SP` es la raíz del grafo de dependencias (`modules.md` §7): consume de nadie. Las otras
 * interfaces de aplicación del sistema van al revés —`SP` publica, `PM` y `MV` consumen (**D-25**)—
 * y escribir esta igual, con `SP` importando algo de `PM`, <b>compila y abre un ciclo</b> que solo
 * detecta la regla de ArchUnit.
 *
 * <p>Por eso se declara <b>aquí</b>, en `SP`, y la implementa `PM`, que es quien tiene el dato. La
 * dirección de la <b>importación</b> sigue siendo la misma que en las otras tres: `PM` mira a `SP`.
 *
 * <h2>Devuelve vacío en los tres casos que no proceden, y eso es deliberado</h2>
 *
 * <p>Producto inexistente, inactivo o retirado <b>no se distinguen</b>: el endpoint que lo consume
 * es público, y tres respuestas distintas lo convertirían en una forma de enumerar el catálogo
 * comercial sin credenciales (`spec.md` `EX-001`).
 */
public interface RegistrableProductLookup {

  /**
   * El producto del enlace, por <b>código o identificador</b> en el mismo argumento.
   *
   * <p><b>Se resuelve por la FORMA del valor</b>: lo que parece un UUID se busca por identificador
   * y lo demás por código. Es lo que el inicio de sesión ya hace con la identidad, que admite
   * nombre de usuario o correo en un solo campo.
   *
   * @return vacío si no existe, si está inactivo o si está retirado — <b>los tres iguales</b>
   */
  Optional<RegistrableProductView> findRegistrable(String codigoOIdentificador);

  /**
   * Lo que el registro necesita saber del producto, y nada más.
   *
   * <p><b>No lleva precio</b>, y no es un olvido: el registro por enlace no cobra. El camino de
   * pago lo rechaza `EX-004` hasta que exista el área de Finanzas, y traer aquí un importe que
   * nadie usa invitaría a usarlo.
   *
   * @param upgrade si es un producto de tipo upgrade. Un bot no declara membresía destino, y sin
   *     ella la cuenta violaría `RN-SP-018` en el instante de nacer (`EX-003`)
   * @param sourceMembershipCode el código de la membresía <b>de origen</b>, que es lo que permite
   *     saber si el enlace es un `BECA → BECA` y por tanto si exige cuenta de broker (`RN-SP-042`)
   * @param validityDays días de vigencia de la membresía que concede, o {@code null} si no caduca
   */
  record RegistrableProductView(
      UUID id,
      String code,
      boolean upgrade,
      UUID sourceMembershipId,
      String sourceMembershipCode,
      UUID targetMembershipId,
      String targetMembershipCode,
      Integer validityDays) {}
}
