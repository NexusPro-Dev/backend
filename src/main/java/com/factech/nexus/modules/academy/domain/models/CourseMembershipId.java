package com.factech.nexus.modules.academy.domain.models;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** La clave de la visibilidad por membresía: la pareja curso-membresía (`RN-AC-012`). */
@Embeddable
public class CourseMembershipId implements Serializable {

  @Column(name = "course_id", nullable = false, updatable = false)
  private UUID courseId;

  @Column(name = "membership_id", nullable = false, updatable = false)
  private UUID membershipId;

  protected CourseMembershipId() {}

  public CourseMembershipId(UUID courseId, UUID membershipId) {
    this.courseId = courseId;
    this.membershipId = membershipId;
  }

  public UUID getCourseId() {
    return courseId;
  }

  public UUID getMembershipId() {
    return membershipId;
  }

  @Override
  public boolean equals(Object otro) {
    return otro instanceof CourseMembershipId id
        && Objects.equals(courseId, id.courseId)
        && Objects.equals(membershipId, id.membershipId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(courseId, membershipId);
  }
}
