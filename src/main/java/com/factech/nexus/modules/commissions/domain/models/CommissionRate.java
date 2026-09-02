package com.factech.nexus.modules.commissions.domain.models;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.patch.Patchable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Una tasa de comisión <b>de rol</b> (`RF-CM-001`).
 *
 * <p><b>Es catálogo, no configuración aplicada.</b> Existir no la pone en vigor: rige únicamente
 * sobre los productos a los que se la asocia (`RN-CM-012`). Una tasa recién creada y sin asociar
 * <b>no paga nada a nadie</b> — y eso no falla, se descubre liquidando.
 *
 * <p><b>Ya no lleva producto, ni persona, ni vigencia</b>, que es lo que la distingue de la versión
 * del 28-08-2026. El producto salió a {@link ProductCommissionRate} y la persona a {@link
 * UserCommissionRate}, con su propia vigencia.
 *
 * <p><b>Y por no llevar vigencia, esta tabla ya no es un historial.</b> No hay dos filas contando
 * cada una su parte: hay una que ahora dice otra cosa. <b>Corregir un porcentaje reescribe lo que
 * rigió siempre</b>, y lo único que puede preservar el pasado es que la liquidación copie el
 * porcentaje que aplicó (`RN-CM-008`) — liquidación que todavía no existe.
 *
 * <p><b>Varias tasas por rol son legítimas</b>: el catálogo puede ofrecer «`AGENTE` 10 %» y
 * «`AGENTE` 15 %» para asociarlas a productos distintos. Lo que no puede repetirse es un rol sobre
 * el <b>mismo</b> producto, y eso lo cierra la clave primaria de la asociación (`RN-CM-013`).
 */
@Entity
@Table(name = "commission_rates")
public class CommissionRate {

  private static final BigDecimal CIEN = new BigDecimal("100");

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  /**
   * El rol al que la tasa paga. <b>Inmutable</b>: cambiarlo no corrige la tasa, crea otra — y
   * arrastraría consigo todas sus asociaciones a un rol que nadie eligió.
   */
  @Column(name = "role_id", nullable = false, updatable = false)
  private UUID roleId;

  @Column(name = "percentage", nullable = false, precision = 5, scale = 2)
  private BigDecimal percentage;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;

  /** Exigido por JPA. */
  protected CommissionRate() {}

  /**
   * Declara una tasa de rol.
   *
   * @param ahora instante del alta, inyectado para que la prueba pueda fijarlo
   */
  public static CommissionRate create(
      UUID id, UUID roleId, BigDecimal percentage, OffsetDateTime ahora) {

    verificarPorcentaje(percentage);

    CommissionRate tasa = new CommissionRate();
    tasa.id = id;
    tasa.roleId = roleId;
    tasa.percentage = percentage;
    tasa.createdAt = ahora;
    tasa.updatedAt = ahora;
    return tasa;
  }

  /**
   * Corrige el porcentaje y <b>devuelve qué cambió de verdad</b> (`RF-CM-003`).
   *
   * <p><b>Aquí ya no hay «corregir» frente a «cambiar».</b> En el modelo anterior eran dos
   * operaciones distintas —corregir reescribía, cambiar cerraba una vigencia y abría otra— porque
   * la tarifa tenía fechas. Sin ellas <b>solo queda reescribir</b>, y con ello la certeza de que
   * esta llamada <b>borra lo que la tasa dijo hasta ahora</b> sin dejar rastro en ningún sitio.
   *
   * <p><b>{@code updatedAt} solo se mueve si algo cambió</b>: una petición que no cambia nada no es
   * un cambio, y moverla haría creer que alguien tocó la tasa.
   *
   * @return los campos que cambiaron, cada uno con {@code before} y {@code after}. Vacío si la
   *     petición no cambió nada
   */
  public Map<String, Object> update(Patchable<BigDecimal> nuevoPorcentaje, OffsetDateTime ahora) {

    Map<String, Object> cambios = new LinkedHashMap<>();

    if (nuevoPorcentaje.presente()) {
      BigDecimal valor = nuevoPorcentaje.valor();
      if (valor == null) {
        String mensaje = "El porcentaje no puede vaciarse.";
        throw new ValidationException(
            "VAL-002", mensaje, List.of(new FieldError("percentage", "VAL-002", mensaje)));
      }
      verificarPorcentaje(valor);
      // `compareTo` y no `equals`: 10.00 y 10.0000 son el mismo porcentaje con
      // distinta escala, y `equals` los daría por distintos — el registro se
      // llenaría de cambios que no cambian nada.
      if (percentage.compareTo(valor) != 0) {
        cambios.put(
            "percentage",
            Map.of("before", percentage.toPlainString(), "after", valor.toPlainString()));
        percentage = valor;
      }
    }

    if (!cambios.isEmpty()) {
      updatedAt = ahora;
    }
    return cambios;
  }

  /**
   * Retira la tasa (`RF-CM-004`, `RN-CM-005`).
   *
   * <p><b>No es idempotente</b>: retirar dos veces con dos motivos distintos dejaría el segundo
   * escrito sobre un hecho anterior. Se devuelve si hubo cambio para que ese fallo no dependa de
   * acordarse de comprobarlo.
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

  /**
   * El estado completo de la tasa, para la auditoría.
   *
   * <p><b>La arma el agregado y la usan los dos registros</b> —creación y eliminación—, por lo
   * mismo que en `PM`: si cada caso de uso armara su mapa, los dos describirían la misma tasa con
   * claves distintas y compararlos dejaría de ser posible.
   */
  public Map<String, Object> instantanea() {
    Map<String, Object> estado = new LinkedHashMap<>();
    estado.put("role_id", roleId.toString());
    estado.put("percentage", percentage.toPlainString());
    return estado;
  }

  public boolean estaRetirada() {
    return deletedAt != null;
  }

  /** `RN-CM-007`. El cero se admite: significa «no comisiona». */
  private static void verificarPorcentaje(BigDecimal valor) {
    if (valor == null) {
      String mensaje = "El porcentaje es obligatorio.";
      throw new ValidationException(
          "VAL-002", mensaje, List.of(new FieldError("percentage", "VAL-002", mensaje)));
    }
    if (valor.compareTo(BigDecimal.ZERO) < 0 || valor.compareTo(CIEN) > 0) {
      String mensaje = "El porcentaje debe estar entre cero y cien.";
      throw new ValidationException(
          "VAL-003", mensaje, List.of(new FieldError("percentage", "VAL-003", mensaje)));
    }
  }

  public UUID getId() {
    return id;
  }

  public UUID getRoleId() {
    return roleId;
  }

  public BigDecimal getPercentage() {
    return percentage;
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
