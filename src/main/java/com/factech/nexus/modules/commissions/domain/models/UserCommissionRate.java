package com.factech.nexus.modules.commissions.domain.models;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.patch.Patchable;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * La tasa de comisión <b>de una persona</b> (`RF-CM-006`).
 *
 * <p><b>Es una excepción, no un grado más.</b> Quien la tiene <b>gana siempre</b> sobre la de su
 * rol, <b>sobre su producto</b> (`RN-CM-004`). <b>Desde el 16-09-2026 nace con ese producto</b>
 * (`RN-CM-021`), como la de rol: una excepción que abarque varios productos son varias tasas. Hasta
 * el 11-09-2026 no miraba el producto —ganaba lo mismo vendiera lo que vendiera— y del 11-09-2026
 * al 16-09-2026 se asociaba a productos en una tabla aparte.
 *
 * <p><b>No lleva rol</b>, por decisión del responsable del proyecto: es de la persona y punto. El
 * modelo anterior sí lo exigía, y con ello impedía que una excepción sobreviviera a que su titular
 * dejara de vender. <b>Esa protección ya no existe</b>: la tasa sigue viva aunque la persona pase a
 * un rol que no comisiona, y no falla — se queda callada hasta que alguien la mira (`cm.md` §5.3).
 *
 * <p><b>Es la única tabla del módulo con vigencia</b>, y por tanto <b>la única que conserva
 * historial</b>: sus filas cerradas dicen qué ganó esa persona y hasta cuándo.
 *
 * <p><b>`RN-CM-006` no se comprueba aquí</b>, y no es un olvido: el solapamiento mira a
 * <b>otras</b> filas y el agregado solo conoce la suya. Vive en {@code
 * uq_user_commission_rates_vigente}, en el motor —otra vez desde `V10`, sobre persona, producto y
 * rango—, porque comprobarlo solo en el caso de uso sería una carrera.
 */
@Entity
@Table(name = "user_commission_rates")
public class UserCommissionRate {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  /**
   * <b>El mismo objeto que incrusta la tasa de rol</b>, y no un gemelo (`RN-CM-016`).
   *
   * <p>Que sea el mismo es lo que permite a `RF-CM-005` devolver la comisión resuelta <b>sin saber
   * de cuál de las dos tablas salió</b>. Dos objetos iguales obligarían a la resolución a elegir
   * uno o a inventar un tercero al que convertir los dos.
   *
   * <p><b>Y un importe fijo es dinero en la moneda de su producto</b> (`RN-CM-017`): desde el
   * 16-09-2026 la tasa lo conoce, y el caso de uso lo valida contra sus decimales. Hasta el
   * 11-09-2026 esta tasa no se asociaba a nada y se interpretaba en tantas monedas como hubiera.
   */
  @Embedded private CommissionValue value;

  /** Nace con la tasa y no se corrige (`RN-CM-021`, 16-09-2026). */
  @Column(name = "product_id", nullable = false, updatable = false)
  private UUID productId;

  /**
   * Inmutable. Cambiar desde cuándo rige no corrige la tasa: <b>reescribe a quién se le pagó
   * qué</b> en los días que pasa a cubrir o a dejar de cubrir.
   */
  @Column(name = "valid_from", nullable = false, updatable = false)
  private LocalDate validFrom;

  /** Nulo: rige indefinidamente. No significa «se desconoce». */
  @Column(name = "valid_to")
  private LocalDate validTo;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;

  /** Exigido por JPA. */
  protected UserCommissionRate() {}

  /**
   * Declara la tasa personalizada de una persona.
   *
   * @param ahora instante del alta, inyectado para que la prueba pueda fijarlo
   */
  public static UserCommissionRate create(
      UUID id,
      UUID userId,
      UUID productId,
      CommissionValue value,
      LocalDate validFrom,
      LocalDate validTo,
      OffsetDateTime ahora) {

    if (productId == null) {
      String mensaje = "El producto de la tasa es obligatorio.";
      throw new ValidationException(
          "VAL-013", mensaje, List.of(new FieldError("productId", "VAL-013", mensaje)));
    }
    verificarValor(value);
    verificarVigencia(validFrom, validTo);

    UserCommissionRate tasa = new UserCommissionRate();
    tasa.id = id;
    tasa.userId = userId;
    tasa.productId = productId;
    tasa.value = value;
    tasa.validFrom = validFrom;
    tasa.validTo = validTo;
    tasa.createdAt = ahora;
    tasa.updatedAt = ahora;
    return tasa;
  }

  /**
   * Corrige lo corregible y <b>devuelve qué cambió de verdad</b>.
   *
   * <p><b>Los dos campos se tratan de forma opuesta ante el nulo explícito</b>: quitar el fin de
   * vigencia es una orden que se cumple —la tasa vuelve a regir indefinidamente—, y quitar el
   * porcentaje se rechaza, porque una tasa sin porcentaje no significa nada.
   *
   * <p><b>Aquí corregir y cambiar sí siguen siendo cosas distintas</b>, al revés que en {@link
   * CommissionRate}: como esta tabla conserva vigencia, cambiar lo que gana alguien a partir de una
   * fecha es <b>cerrar la vigente y registrar otra</b>, y no reescribir esta.
   *
   * @return los campos que cambiaron, cada uno con {@code before} y {@code after}. Vacío si la
   *     petición no cambió nada
   */
  public Map<String, Object> update(
      Patchable<CommissionValue> nuevoValor, Patchable<LocalDate> nuevoFin, OffsetDateTime ahora) {

    Map<String, Object> cambios = new LinkedHashMap<>();

    if (nuevoValor.presente()) {
      CommissionValue valor = nuevoValor.valor();
      if (valor == null) {
        String mensaje = "La forma de la comisión no puede vaciarse.";
        throw new ValidationException(
            "VAL-002", mensaje, List.of(new FieldError("rateType", "VAL-002", mensaje)));
      }
      // Ver `CommissionRate.update`: ni `equals` ni `compareTo` sobre la cifra
      // sirven, por motivos opuestos. La comparación vive en `CommissionValue`.
      if (!value.mismoValorQue(valor)) {
        cambios.put(
            "value", Map.of("before", value.paraAuditoria(), "after", valor.paraAuditoria()));
        value = valor;
      }
    }

    if (nuevoFin.presente()) {
      LocalDate valor = nuevoFin.valor();
      verificarVigencia(validFrom, valor);
      if (!Objects.equals(valor, validTo)) {
        cambios.put("valid_to", Map.of("before", fecha(validTo), "after", fecha(valor)));
        validTo = valor;
      }
    }

    if (!cambios.isEmpty()) {
      updatedAt = ahora;
    }
    return cambios;
  }

  /**
   * Retira la tasa (`RN-CM-005`).
   *
   * <p><b>La vigencia NO se toca</b>, y no es un olvido: el registro de eliminación debe poder
   * decir <b>qué periodo cubría</b> lo que se retiró. Cerrarla «de paso» haría que todas las
   * instantáneas dijeran lo mismo y ese dato dejaría de significar nada — la salvaguarda habría
   * destruido la evidencia que protege.
   *
   * @return {@code true} si la tasa pasó de viva a retirada
   */
  public boolean delete(OffsetDateTime ahora) {
    if (deletedAt != null) {
      return false;
    }
    deletedAt = ahora;
    updatedAt = ahora;
    return true;
  }

  /** El estado completo, para la auditoría. */
  public Map<String, Object> instantanea() {
    Map<String, Object> estado = new LinkedHashMap<>();
    estado.put("user_id", userId.toString());
    estado.put("product_id", productId.toString());
    estado.put("rate_type", value.getRateType().name());
    estado.put("value", value.cifra().toPlainString());
    estado.put("valid_from", validFrom.toString());
    estado.put("valid_to", validTo == null ? null : validTo.toString());
    return estado;
  }

  public boolean estaRetirada() {
    return deletedAt != null;
  }

  /** El rango de cada forma lo comprueba {@link CommissionValue}; aquí solo la presencia. */
  private static void verificarValor(CommissionValue valor) {
    if (valor == null) {
      String mensaje = "La forma de la comisión es obligatoria: porcentaje o valor fijo.";
      throw new ValidationException(
          "VAL-002", mensaje, List.of(new FieldError("rateType", "VAL-002", mensaje)));
    }
  }

  /**
   * `RN-CM-009`. El fin es opcional; su ausencia significa «indefinidamente».
   *
   * <p>Un fin <b>igual</b> al inicio se admite: una tasa que rigió un solo día es válida.
   */
  /**
   * `RN-CM-009`: el inicio es obligatorio y el fin no lo precede. Pública porque el caso de uso la
   * necesita ANTES de consultar el solapamiento: un rango invertido no se puede ni preguntar.
   */
  public static void verificarVigencia(LocalDate desde, LocalDate hasta) {
    if (desde == null) {
      String mensaje = "El inicio de vigencia es obligatorio.";
      throw new ValidationException(
          "VAL-004", mensaje, List.of(new FieldError("validFrom", "VAL-004", mensaje)));
    }
    if (hasta != null && hasta.isBefore(desde)) {
      String mensaje = "El fin de vigencia no puede ser anterior a su inicio.";
      throw new ValidationException(
          "VAL-005", mensaje, List.of(new FieldError("validTo", "VAL-005", mensaje)));
    }
  }

  /**
   * La fecha en el registro de auditoría va como texto y no como ausencia.
   *
   * <p>{@code Map.of} rechaza los nulos, y aunque los admitiera, una clave que desaparece del JSON
   * haría indistinguible «se quitó el fin de vigencia» de «no se tocó».
   */
  private static String fecha(LocalDate valor) {
    return valor == null ? "" : valor.toString();
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getProductId() {
    return productId;
  }

  public CommissionValue getValue() {
    return value;
  }

  /** Nulo si esta tasa paga un importe fijo. Léase con {@link CommissionValue#getRateType()}. */
  public BigDecimal getPercentage() {
    return value.getPercentage();
  }

  /** Nulo si esta tasa paga un porcentaje. */
  public BigDecimal getFixedAmount() {
    return value.getFixedAmount();
  }

  public LocalDate getValidFrom() {
    return validFrom;
  }

  public LocalDate getValidTo() {
    return validTo;
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
