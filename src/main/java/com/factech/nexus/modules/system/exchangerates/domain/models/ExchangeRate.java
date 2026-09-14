package com.factech.nexus.modules.system.exchangerates.domain.models;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A cuánto se cambia una moneda por otra, y desde cuándo (`RF-SP-047`).
 *
 * <p><b>Es a la vez agregado y modelo persistente</b>, como {@code Product} y {@code Role}.
 *
 * <p><b>Lo que este agregado NO puede comprobar es lo único crítico que tiene</b>: `RN-SP-032` —dos
 * tasas vigentes del mismo par no se solapan— mira <b>otras filas</b>, y el agregado solo conoce la
 * suya. Vive en el {@code EXCLUDE} de {@code uq_exchange_rates_vigente}.
 */
@Entity
@Table(name = "exchange_rates")
public class ExchangeRate {

  /** Ocho decimales: los que la columna declara y los que una tasa necesita. */
  private static final int ESCALA = 8;

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  /**
   * De qué moneda se parte. <b>Inmutable</b>: junto al destino define <b>qué cambio expresa</b> la
   * tasa, y corregirla la convertiría en otra — quien necesite otro par registra otra.
   */
  @Column(name = "source_currency_id", nullable = false, updatable = false)
  private UUID sourceCurrencyId;

  /** A qué moneda se llega. Inmutable por el mismo motivo. */
  @Column(name = "target_currency_id", nullable = false, updatable = false)
  private UUID targetCurrencyId;

  /**
   * Cuántas unidades de destino da <b>una</b> de origen.
   *
   * <p><b>La dirección contraria no se deduce</b>: la inversa aritmética casi nunca es la tasa real
   * y calcularla produciría un número plausible y falso. Quien necesite las dos declara dos tasas.
   */
  @Column(name = "price", nullable = false, precision = 18, scale = 8)
  private BigDecimal price;

  @Column(name = "valid_from", nullable = false)
  private LocalDate validFrom;

  /** Nula: la tasa es <b>vitalicia</b> (`RN-SP-031`). */
  @Column(name = "valid_to")
  private LocalDate validTo;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;

  /** Exigido por JPA. */
  protected ExchangeRate() {}

  /**
   * Registra una tasa.
   *
   * <p><b>El estado SÍ se recibe</b>, al revés que en {@code Product.create} (`spec.md` §14,
   * resolución 1): allí `RN-PM-012` obliga a nacer inactivo y aquí no hay regla equivalente. El
   * coste está aceptado — el alta puede violar `RN-SP-032` y tiene que traducirlo.
   */
  public static ExchangeRate create(
      UUID id,
      UUID sourceCurrencyId,
      UUID targetCurrencyId,
      BigDecimal price,
      LocalDate validFrom,
      LocalDate validTo,
      boolean active,
      OffsetDateTime ahora) {

    verificarMonedas(sourceCurrencyId, targetCurrencyId);
    verificarPrecio(price);
    verificarVigencia(validFrom, validTo);

    ExchangeRate tasa = new ExchangeRate();
    tasa.id = id;
    tasa.sourceCurrencyId = sourceCurrencyId;
    tasa.targetCurrencyId = targetCurrencyId;
    tasa.price = price;
    tasa.validFrom = validFrom;
    tasa.validTo = validTo;
    tasa.active = active;
    tasa.createdAt = ahora;
    tasa.updatedAt = ahora;
    return tasa;
  }

  /**
   * `RN-SP-029` — las dos monedas están y <b>no son la misma</b>.
   *
   * <p><b>Es pública porque el caso de uso la ejecuta ANTES de buscar nada</b>: comprobar que una
   * tasa trae dos monedas distintas no es lo mismo que comprobar que esas monedas <b>existen</b>, y
   * hacerlas juntas reportaría «origen igual a destino» como «la moneda no existe» — un `422` sobre
   * un dato que el actor sí envió, en lugar del `400` que le corresponde.
   *
   * <p>El {@code CHECK} del esquema dice lo mismo, y esto no es redundante: la restricción
   * produciría un {@code 500} donde corresponde un {@code 400} que nombre el campo.
   */
  public static void verificarMonedas(UUID origen, UUID destino) {
    if (origen == null) {
      throw val("sourceCurrencyId", "VAL-001", "La moneda de origen es obligatoria.");
    }
    if (destino == null) {
      throw val("targetCurrencyId", "VAL-002", "La moneda de destino es obligatoria.");
    }
    if (origen.equals(destino)) {
      throw val(
          "targetCurrencyId",
          "VAL-003",
          "La moneda de origen y la de destino no pueden ser la misma.");
    }
  }

  private static void verificarPrecio(BigDecimal precio) {
    if (precio == null || precio.compareTo(BigDecimal.ZERO) <= 0) {
      throw val("price", "VAL-004", "El precio de la tasa debe ser mayor que cero.");
    }
    if (precio.stripTrailingZeros().scale() > ESCALA) {
      throw val("price", "VAL-005", "El precio admite como mucho ocho decimales.");
    }
  }

  /**
   * `RN-SP-031`.
   *
   * <p><b>El mismo día es un periodo legítimo</b> —una tasa que rige una jornada—, y por eso la
   * comparación es {@code isBefore} y no un {@code <=}.
   */
  private static void verificarVigencia(LocalDate desde, LocalDate hasta) {
    if (desde == null) {
      throw val("validFrom", "VAL-006", "La fecha de inicio de la vigencia es obligatoria.");
    }
    if (hasta != null && hasta.isBefore(desde)) {
      throw val("validTo", "VAL-007", "La fecha de fin no puede ser anterior a la de inicio.");
    }
  }

  private static ValidationException val(String campo, String codigo, String mensaje) {
    return new ValidationException(
        codigo, mensaje, List.of(new FieldError(campo, codigo, mensaje)));
  }

  /**
   * El estado completo, para el registro de auditoría (Art. V.13).
   *
   * <p><b>La comparte con el retiro</b> (`RF-SP-050`): si cada caso de uso armara su mapa, el
   * registro de creación y el de eliminación describirían la misma tasa con claves distintas, y
   * compararlos —que es para lo que existen— dejaría de ser posible.
   *
   * <p>El precio va como <b>texto</b>: {@code BigDecimal} serializado a JSON puede perder la
   * escala, y en un registro de auditoría {@code 4150.0} y {@code 4150.00000000} no son lo mismo.
   */
  public Map<String, Object> instantanea() {
    Map<String, Object> estado = new LinkedHashMap<>();
    estado.put("source_currency_id", sourceCurrencyId.toString());
    estado.put("target_currency_id", targetCurrencyId.toString());
    estado.put("price", price.toPlainString());
    estado.put("valid_from", validFrom.toString());
    estado.put("valid_to", validTo == null ? null : validTo.toString());
    estado.put("is_active", active);
    return estado;
  }

  public UUID getId() {
    return id;
  }

  public UUID getSourceCurrencyId() {
    return sourceCurrencyId;
  }

  public UUID getTargetCurrencyId() {
    return targetCurrencyId;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public LocalDate getValidFrom() {
    return validFrom;
  }

  public LocalDate getValidTo() {
    return validTo;
  }

  public boolean isActive() {
    return active;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public OffsetDateTime getDeletedAt() {
    return deletedAt;
  }
}
