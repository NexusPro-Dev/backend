package com.factech.nexus.modules.system.memberships.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

/**
 * La cadena completa (`RF-SP-017`).
 *
 * <p><b>Envuelta en {@code content} y no como arreglo desnudo</b>: un {@code [...]} en la raíz
 * cierra la puerta a añadir después cualquier metadato sin romper a todos los clientes, y el nombre
 * {@code content} hace que la forma de leer la lista sea idéntica a la de los endpoints paginados,
 * faltando solo lo que aquí no existe.
 *
 * <p><b>No se reutiliza {@code PageResponse} con valores de adorno.</b> Rellenar {@code totalPages:
 * 1} diría que hay paginación, y `CA-SP-120` exige que no la haya.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record MembershipChainResponse(List<Item> content) {

  public static MembershipChainResponse from(List<MembershipItem> cadena) {
    return new MembershipChainResponse(cadena.stream().map(Item::from).toList());
  }

  /** Un eslabón del listado. Sin marcas temporales: el eje de este recurso es la posición. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Item(
      UUID id,
      String code,
      String name,
      String description,
      String color,
      int level,
      UUID parentMembershipId,
      UUID childMembershipId) {

    static Item from(MembershipItem item) {
      return new Item(
          item.id(),
          item.code(),
          item.name(),
          item.description(),
          item.color(),
          item.level(),
          item.parentMembershipId(),
          item.childMembershipId());
    }
  }
}
