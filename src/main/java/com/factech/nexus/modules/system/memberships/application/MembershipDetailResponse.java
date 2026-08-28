package com.factech.nexus.modules.system.memberships.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/**
 * Detalle de una membresía con sus dos vecinos inmediatos (`RF-SP-018`).
 *
 * <p>{@code parentMembership} es {@code null} en la superior de la cadena y {@code childMembership}
 * lo es en la inferior. En la única membresía del sistema ambos son nulos a la vez, y es válido.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record MembershipDetailResponse(
    UUID id,
    String code,
    String name,
    String description,
    String color,
    int level,
    Neighbor parentMembership,
    Neighbor childMembership) {

  public static MembershipDetailResponse from(MembershipDetailItem detalle) {
    return new MembershipDetailResponse(
        detalle.id(),
        detalle.code(),
        detalle.name(),
        detalle.description(),
        detalle.color(),
        detalle.level(),
        Neighbor.from(detalle.parent()),
        Neighbor.from(detalle.child()));
  }

  /** Un vecino: sus datos, <b>no</b> sus propios vecinos. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Neighbor(UUID id, String code, String name, int level, String color) {

    static Neighbor from(MembershipNeighborItem vecino) {
      return vecino == null
          ? null
          : new Neighbor(vecino.id(), vecino.code(), vecino.name(), vecino.level(), vecino.color());
    }
  }
}
