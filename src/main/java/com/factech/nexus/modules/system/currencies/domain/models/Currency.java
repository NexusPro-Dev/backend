package com.factech.nexus.modules.system.currencies.domain.models;

import com.factech.nexus.shared.error.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Una moneda del catálogo (`RF-SP-019`, `RF-SP-023`).
 *
 * <p><b>Inmutable por API salvo su estado</b> (`RN-SP-010`). No hay fábrica: las monedas entran por
 * migración y solo por migración. Lo único que esta clase permite cambiar es {@code isActive}, y
 * esa es toda la superficie mutable que expone — no por disciplina, sino porque no existe otro
 * método que escriba.
 */
@Entity
@Table(name = "currencies")
public class Currency {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  /**
   * {@code char(3)} y no {@code varchar}: es lo que declara `requirements/sp.md` §10.5, porque un
   * código ISO 4217 tiene exactamente tres letras y la longitud fija lo dice mejor que una cota.
   *
   * <p>La anotación de tipo JDBC es obligatoria y no decorativa: PostgreSQL llama {@code bpchar} a
   * ese tipo, y sin ella Hibernate espera {@code varchar(3)} y `ddl-auto: validate` <b>impide
   * arrancar</b>. El fallo aparece al levantar el contexto, no al consultar.
   */
  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
  @Column(name = "code", nullable = false, length = 3, updatable = false)
  private String code;

  @Column(name = "name", nullable = false, length = 100, updatable = false)
  private String name;

  @Column(name = "symbol", length = 10, updatable = false)
  private String symbol;

  /** Condiciona el redondeo de todo cálculo financiero. Cero es legítimo. */
  @Column(name = "decimal_places", nullable = false, updatable = false)
  private short decimalPlaces;

  @Column(name = "is_default", nullable = false, updatable = false)
  private boolean isDefault;

  /** Lo único que este agregado deja cambiar. */
  @Column(name = "is_active", nullable = false)
  private boolean isActive;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  /** Exigido por JPA. */
  protected Currency() {}

  /**
   * Activa o desactiva la moneda.
   *
   * <p><b>Devuelve si hubo cambio</b>, y eso no es un detalle: `FA-001` exige que pedir el estado
   * que la moneda ya tiene responda {@code 200} <b>sin dejar evento de auditoría</b> (`CA-SP-190`).
   * Un método {@code void} obligaría a quien llama a comparar antes y después, que es justo donde
   * se cuela el evento fantasma.
   *
   * <p><b>La verificación de `RN-SP-010` vive aquí y también en el esquema</b>, y la duplicación es
   * deliberada: {@code ck_currencies_default_active} garantiza que nadie —ni una migración— pueda
   * dejar la moneda por defecto inactiva, y esta comprobación existe para que el mensaje sea
   * comprensible y para que la regla se pueda probar sin levantar PostgreSQL.
   *
   * <p><b>Reactivar nunca falla por regla.</b> `EX-001` alcanza solo a la desactivación: activar
   * una moneda inactiva no puede violar nada.
   *
   * @return {@code true} si el estado cambió; {@code false} si ya era el pedido (`FA-001`)
   * @throws BusinessRuleException si se pide desactivar la moneda por defecto
   */
  public boolean changeStatus(boolean activa, OffsetDateTime ahora) {
    if (!activa && isDefault) {
      throw new BusinessRuleException(
          "RN-SP-010",
          "No se puede desactivar "
              + code
              + ", que es la moneda con la que opera el sistema: los importes quedarían sin"
              + " referencia válida. Cambiar cuál es la moneda por defecto es una operación de"
              + " migración, no de API.");
    }
    if (isActive == activa) {
      return false;
    }
    isActive = activa;
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

  public String getSymbol() {
    return symbol;
  }

  public short getDecimalPlaces() {
    return decimalPlaces;
  }

  public boolean isDefault() {
    return isDefault;
  }

  public boolean isActive() {
    return isActive;
  }
}
