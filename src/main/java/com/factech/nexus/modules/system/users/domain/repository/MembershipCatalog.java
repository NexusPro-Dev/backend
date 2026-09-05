package com.factech.nexus.modules.system.users.domain.repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Lo único que este módulo necesita saber del catálogo de membresías (`RF-SP-016`).
 *
 * <p>Un puerto deliberadamente estrecho: asignar una membresía a una persona no requiere conocer su
 * cadena ni sus vecinas. Declararlo así impide que la lógica de personas empiece a razonar sobre la
 * cadena de membresías, que es competencia de su propio agregado.
 *
 * <p>El <b>nivel</b> sí viaja, porque la respuesta de `RF-SP-032` lo devuelve y sin él haría falta
 * una segunda consulta desde la capa de presentación.
 */
public interface MembershipCatalog {

  /** La membresía del catálogo, o vacío si el identificador no designa ninguna. */
  Optional<MembershipRef> find(UUID membershipId);

  /**
   * <b>El suelo: la membresía con la que arranca toda persona</b> (`RN-SP-018`, reescrita el
   * 05-09-2026).
   *
   * <p><b>Se resuelve por el código {@code FREE} y no por la forma de la cadena</b>, y esa es la
   * única decisión de este método. Lo natural sería «la que no tiene padre», y es un blanco móvil:
   * `RN-SP-007` permite <b>registrar una por debajo</b> de Free, y entonces el suelo se mueve y con
   * él cambiaría, en silencio, el nivel con el que arranca todo el mundo. El código no se mueve —
   * {@code uq_memberships_code} lo hace único, `RN-SP-008` impide borrar la fila y `V46` la siembra
   * en todos los entornos.
   *
   * <p><b>El precio queda escrito</b>: si alguien registra un nivel por debajo, el suelo de la
   * cadena y el nivel de arranque dejan de ser el mismo. Es a propósito.
   *
   * <p><b>No devuelve {@code Optional}.</b> Que no exista no es un caso de negocio que quien llama
   * deba resolver: es una base mal construida, y devolver vacío repartiría por tres servicios la
   * decisión de qué hacer con algo que no puede pasar.
   *
   * @throws IllegalStateException si el catálogo no tiene la membresía de arranque
   */
  MembershipRef floor();

  /** Una membresía de la cadena, con lo justo para nombrarla y ordenarla. */
  record MembershipRef(UUID id, String code, String name, short level) {}
}
