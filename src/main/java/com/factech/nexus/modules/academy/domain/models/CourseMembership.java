package com.factech.nexus.modules.academy.domain.models;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Una membresía que abre un curso (`RF-AC-020`, `RN-AC-012`).
 *
 * <p>{@link CourseProduct} con el nivel en lugar del servicio: una asociación que se borra
 * físicamente al quitarla (`RF-AC-021`) y se audita como {@code ASSOCIATION} sin motivo. <b>Una
 * lista y no un nivel mínimo</b> (`ac.md` §5.2.2): que {@code ORO} abra un curso no lo abre a
 * {@code PLATINO}.
 */
@Entity
@Table(name = "course_memberships")
public class CourseMembership {

  @EmbeddedId private CourseMembershipId id;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  /** Exigido por JPA. */
  protected CourseMembership() {}

  public static CourseMembership create(UUID courseId, UUID membershipId, OffsetDateTime ahora) {
    CourseMembership fila = new CourseMembership();
    fila.id = new CourseMembershipId(courseId, membershipId);
    fila.createdAt = ahora;
    return fila;
  }

  /** La instantánea de auditoría, con el código de la membresía, que es como se la nombra. */
  public Map<String, Object> instantanea(String codigoDeLaMembresia) {
    Map<String, Object> estado = new LinkedHashMap<>();
    estado.put("course_id", id.getCourseId().toString());
    estado.put("membership_id", id.getMembershipId().toString());
    estado.put("membership_code", codigoDeLaMembresia);
    return estado;
  }

  public CourseMembershipId getId() {
    return id;
  }

  public UUID getCourseId() {
    return id.getCourseId();
  }

  public UUID getMembershipId() {
    return id.getMembershipId();
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
