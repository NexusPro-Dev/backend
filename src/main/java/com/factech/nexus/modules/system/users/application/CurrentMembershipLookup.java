package com.factech.nexus.modules.system.users.application;

import java.util.Optional;
import java.util.UUID;

/**
 * La membresía <b>vigente</b> de una persona (**D-25**, `RF-PM-007` · `T-01`).
 *
 * <p>Es la tercera y última lectura que D-25 previó, y la única que `RF-PM-007` estrena. Responde
 * <b>una sola pregunta</b>, y por eso es una interfaz aparte de {@link UserCatalog} y de {@link
 * SellerRoleCatalog}: una interfaz por lectura y no una fachada, de modo que añadir un método a una
 * no cambie el contrato de las otras dos ni el de sus dobles de prueba.
 *
 * <h2>Devuelve la membresía YA EVALUADA, y ahí está todo el valor</h2>
 *
 * <p>Este puerto no devuelve la asignación con su fecha de fin para que el consumidor decida si
 * sigue valiendo: <b>devuelve vacío cuando ya no vale</b>. La definición de «vigente» vive en `SP`
 * en un solo sitio desde el 24-08-2026 —{@code UserMembership.isCurrentAt}— y su borde está fijado
 * por prueba: <b>una fecha exactamente igual al instante consultado ya NO está vigente</b>, porque
 * {@code ends_at} es el momento en que la membresía deja de valer y no el último en que vale.
 *
 * <p>Publicar la fecha en lugar de la respuesta invitaría a `PM` a reimplementar esa comparación, y
 * ese es el defecto que <b>no falla</b>: devolvería resultados plausibles durante meses y solo se
 * notaría en el borde. `FA-003` de `RF-PM-007` —la membresía vencida se comporta como la ausencia
 * de membresía— depende enteramente de que esto se respete.
 *
 * <h2>Vacío significa «hoy no tiene nivel», y cubre tres casos a la vez</h2>
 *
 * <p>Quien nunca tuvo membresía —un funcionario, un vendedor (`RN-SP-018`)—, quien la tuvo y venció
 * y quien no existe. Los tres producen la misma oferta: ningún upgrade y todos los bots. Que sean
 * indistinguibles aquí es correcto, porque la pregunta que este puerto responde es «desde qué nivel
 * puede subir», y en los tres casos la respuesta es «desde ninguno».
 *
 * <p>Ver `architecture.md` §15.2.
 */
public interface CurrentMembershipLookup {

  /**
   * La membresía que le vale hoy a esa persona, si alguna.
   *
   * @param userId identificador de la persona; un valor nulo devuelve vacío en lugar de fallar
   * @return vacío si no tiene ninguna asignada, si la que tiene ya venció, o si no existe
   */
  Optional<CurrentMembershipView> currentMembershipOf(UUID userId);

  /**
   * Lo que cruza la frontera: datos planos, sin comportamiento y sin entidad.
   *
   * <p><b>No viaja la fecha de fin</b>, y su ausencia es deliberada: es el dato con el que el
   * consumidor podría rehacer la comparación que este puerto existe para no repetir.
   *
   * <p>{@code level} sí viaja, porque es lo único que permite decidir «hacia arriba» sin conocer la
   * estructura de la cadena. Crece hacia abajo: {@code 1} es la cima (`requirements/sp.md` §10.4),
   * de modo que <b>nivel superior es número menor</b>.
   */
  record CurrentMembershipView(UUID id, String code, String name, int level) {}
}
