package com.factech.nexus.modules.products.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Lo que quien consulta puede comprar hoy (`RF-PM-007`).
 *
 * <h2>Las dos colecciones van ENVUELTAS, y no como arreglos desnudos</h2>
 *
 * <p>Es `CA-PM-091`, y resuelve la quinta pregunta abierta de la especificación sin tener que
 * decidirla: <b>hoy la oferta no se pagina</b> —los upgrades están acotados por la longitud de la
 * cadena y los bots activos son pocos— y el día que los bots crezcan, añadir {@code page}, {@code
 * size} y {@code totalElements} dentro de la envoltura <b>no rompe a ningún cliente</b>. Con los
 * arreglos en la raíz, paginar obligaría a cambiar el tipo de la propiedad, que es un cambio
 * incompatible para todos a la vez.
 *
 * <p>Es la misma decisión que `RF-SP-017` tomó con la cadena de membresías.
 *
 * <h2>Dos colecciones y no una lista mezclada</h2>
 *
 * <p>Un upgrade y un bot no se comparan: llevan datos distintos —solo el primero tiene destino— y
 * se ordenan por criterios distintos. Devolverlos juntos obligaría al frontend a separarlos por
 * {@code type}, que es exactamente el filtrado en el navegador que este requerimiento existe para
 * evitar.
 *
 * @param currentMembership el nivel desde el que mira quien consulta, o {@code null} si no tiene
 *     ninguno vigente. <b>Nulo presente y no ausente</b>: es la respuesta a «¿desde dónde subo?», y
 *     un campo que falta es indistinguible de uno que el cliente no conoce
 * @param upgrades los upgrades hacia niveles <b>superiores</b> al suyo, del salto más corto al más
 *     largo. Vacío es una respuesta normal —quien está en la cima, y quien no tiene nivel— y no un
 *     error ni un mensaje especial (`FA-001`, `FA-002`)
 * @param services los bots activos, <b>todos, para cualquiera</b>: no dependen del nivel de quien
 *     mira ni de que tenga uno (`spec.md` §14, resolución 2)
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record OfferResponse(
    ProductResponse.MembershipRef currentMembership, Offered upgrades, Offered services) {

  /**
   * La envoltura de una colección de la oferta.
   *
   * <p>Un solo tipo para las dos: si cada una tuviera el suyo, paginar una obligaría a decidir dos
   * veces cómo se llaman los campos de paginación.
   */
  public record Offered(List<OfferItem> content) {}

  public static OfferResponse de(
      ProductResponse.MembershipRef actual, List<OfferItem> upgrades, List<OfferItem> bots) {
    return new OfferResponse(actual, new Offered(upgrades), new Offered(bots));
  }
}
