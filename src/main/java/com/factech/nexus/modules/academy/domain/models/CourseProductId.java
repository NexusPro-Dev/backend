package com.factech.nexus.modules.academy.domain.models;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** La clave de la visibilidad por servicio: la pareja curso-producto (`RN-AC-020`). */
@Embeddable
public class CourseProductId implements Serializable {

  @Column(name = "course_id", nullable = false, updatable = false)
  private UUID courseId;

  @Column(name = "product_id", nullable = false, updatable = false)
  private UUID productId;

  protected CourseProductId() {}

  public CourseProductId(UUID courseId, UUID productId) {
    this.courseId = courseId;
    this.productId = productId;
  }

  public UUID getCourseId() {
    return courseId;
  }

  public UUID getProductId() {
    return productId;
  }

  @Override
  public boolean equals(Object otro) {
    return otro instanceof CourseProductId id
        && Objects.equals(courseId, id.courseId)
        && Objects.equals(productId, id.productId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(courseId, productId);
  }
}
