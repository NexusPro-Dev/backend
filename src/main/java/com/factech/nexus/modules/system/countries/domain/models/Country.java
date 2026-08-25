package com.factech.nexus.modules.system.countries.domain.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Un país del catálogo (`RF-SP-020`, `RF-SP-021`, `RF-SP-022`).
 *
 * <p><b>Inmutable salvo su estado</b> (`RN-SP-009`): no se edita ni se elimina. Lo único que esta
 * clase permite cambiar es {@code isActive}, y esa es toda la superficie mutable que expone.
 *
 * <p><b>Nace siempre activo</b> y el alta no recibe el estado (`CA-SP-171`). Eso deja un único
 * camino hacia el estado inactivo —`RF-SP-022`— y un solo lugar donde auditarlo.
 */
@Entity
@Table(name = "countries")
public class Country {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  /**
   * {@code char(2)}, que en PostgreSQL es {@code bpchar}.
   *
   * <p>La anotación de tipo JDBC no es decorativa: sin ella Hibernate espera {@code varchar(2)} y
   * {@code ddl-auto: validate} <b>impide arrancar</b>. Es la misma trampa que en el código de una
   * moneda.
   */
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "code", nullable = false, length = 2, updatable = false)
  private String code;

  /**
   * El nombre se persiste <b>tal como se registró</b>, con sus acentos y sin transformación alguna:
   * el catálogo es internacional. La insensibilidad a acentos pertenece a la <b>búsqueda</b> y a la
   * comprobación de unicidad, no al dato almacenado.
   */
  @Column(name = "name", nullable = false, length = 100, updatable = false)
  private String name;

  /** Lo único que este agregado deja cambiar. */
  @Column(name = "is_active", nullable = false)
  private boolean isActive;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  /** Exigido por JPA. */
  protected Country() {}

  /**
   * Registra un país nuevo.
   *
   * <p><b>El estado no es un argumento</b>: nace activo (`CA-SP-171`). Que la fábrica no lo admita
   * es lo que hace verificable que no exista camino hacia el estado inactivo desde el alta.
   *
   * @param name se recorta de espacios al inicio y al final; el interior <b>no se toca</b>, porque
   *     los nombres compuestos llevan espacios legítimos
   */
  public static Country create(UUID id, CountryCode code, String name, OffsetDateTime ahora) {
    Country pais = new Country();
    pais.id = id;
    pais.code = code.value();
    pais.name = name == null ? null : name.trim();
    pais.isActive = true;
    pais.createdAt = ahora;
    pais.updatedAt = ahora;
    return pais;
  }

  /**
   * Activa o desactiva el país.
   *
   * <p><b>Devuelve si hubo cambio</b>, porque `FA-001` exige que pedir el estado que el país ya
   * tiene responda {@code 200} <b>sin dejar evento de auditoría</b> (`CA-SP-182`). Un método {@code
   * void} obligaría a quien llama a comparar antes y después, que es donde se cuela el evento
   * fantasma.
   *
   * <p><b>Ningún país tiene prohibido desactivarse</b>, a diferencia de la moneda por defecto: este
   * requerimiento no declara ninguna regla de negocio con rechazo, y por eso no hay {@code 409}.
   *
   * @return {@code true} si el estado cambió; {@code false} si ya era el pedido
   */
  public boolean changeStatus(boolean activo, OffsetDateTime ahora) {
    if (isActive == activo) {
      return false;
    }
    isActive = activo;
    updatedAt = ahora;
    return true;
  }

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public boolean isActive() {
    return isActive;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}
