package com.factech.nexus.modules.system.permissions.domain.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Modelo persistente de {@code permissions} (`RF-SP-010` · `T-04`).
 *
 * <p><b>Se usa como metamodelo, no como agregado.</b> La consulta del catálogo lo nombra para
 * referirse a sus columnas con la API de criterios y proyecta directamente sobre {@code
 * PermissionItem}; no instancia esta clase ni la devuelve hacia {@code interfaces}. El mapeo JPA
 * vive aquí porque {@code architecture.md} §5.1 sitúa el modelo persistente en {@code
 * domain/models}; lo que sigue sin cruzar la frontera es la clase, que ninguna respuesta expone:
 * §5.2 prohíbe que {@code interfaces} dependa de tipos de persistencia.
 *
 * <p><b>Es de solo lectura por construcción.</b> El catálogo es inmutable por API (`RN-SP-004`) y
 * solo cambia mediante migración Flyway, de modo que la entidad no expone constructor público con
 * argumentos ni setters. Si algún día apareciera un caso de uso que escriba aquí, tendría que
 * empezar por contradecir esa regla.
 */
@Entity
@Table(name = "permissions")
public class Permission {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "code", nullable = false, length = 100)
  private String code;

  @Column(name = "resource", nullable = false, length = 50)
  private String resource;

  @Column(name = "action", nullable = false, length = 50)
  private String action;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  /**
   * Nulable: hay permisos sin descripción, y la búsqueda debe encontrarlos igual (`plan.md` §4).
   */
  @Column(name = "description")
  private String description;

  // Las marcas temporales las escribe la base de datos con su valor por
  // omisión y ninguna migración las toca después. Se mapean por Art. V.7 y
  // para que `ddl-auto: validate` vea la tabla completa, no porque el catálogo
  // las exponga: plan.md §4 decide de forma explícita no devolverlas.
  @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
  private OffsetDateTime updatedAt;

  /** Exigido por JPA. */
  protected Permission() {}

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getResource() {
    return resource;
  }

  public String getAction() {
    return action;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}
