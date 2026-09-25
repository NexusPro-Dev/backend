package com.factech.nexus.modules.academy.domain.models;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** La clave de la clasificación: la pareja curso-categoría (`RN-AC-010`). */
@Embeddable
public class CourseCategoryItemId implements Serializable {

  @Column(name = "course_id", nullable = false, updatable = false)
  private UUID courseId;

  @Column(name = "category_id", nullable = false, updatable = false)
  private UUID categoryId;

  protected CourseCategoryItemId() {}

  public CourseCategoryItemId(UUID courseId, UUID categoryId) {
    this.courseId = courseId;
    this.categoryId = categoryId;
  }

  public UUID getCourseId() {
    return courseId;
  }

  public UUID getCategoryId() {
    return categoryId;
  }

  @Override
  public boolean equals(Object otro) {
    return otro instanceof CourseCategoryItemId id
        && Objects.equals(courseId, id.courseId)
        && Objects.equals(categoryId, id.categoryId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(courseId, categoryId);
  }
}
