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

  /** Una membresía de la cadena, con lo justo para nombrarla y ordenarla. */
  record MembershipRef(UUID id, String code, String name, short level) {}
}
