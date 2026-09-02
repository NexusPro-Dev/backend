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

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  /**
   * El rol al que la tasa paga. <b>Inmutable</b>: cambiarlo no corrige la tasa, crea otra — y
   * arrastraría consigo todas sus asociaciones a un rol que nadie eligió.
   */
  @Column(name = "role_id", nullable = false, updatable = false)
  private UUID roleId;

  /**
   * <b>Lo que paga: la forma y la cifra, como una sola cosa.</b>
   *
   * <p>Desde el 02-09-2026 puede ser un porcentaje o un importe fijo (`RN-CM-016`). Está incrustado
   * y no suelto porque «una forma y solo una» no se puede evaluar mirando un campo — ver {@link
   * CommissionValue}.
   */
  @Embedded private CommissionValue value;

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
      UUID id, UUID roleId, CommissionValue value, OffsetDateTime ahora) {

    if (value == null) {
      String mensaje = "La forma de la comisión es obligatoria: porcentaje o valor fijo.";
      throw new ValidationException(
          "VAL-002", mensaje, List.of(new FieldError("rateType", "VAL-002", mensaje)));
    }

    CommissionRate tasa = new CommissionRate();
    tasa.id = id;
    tasa.roleId = roleId;
    tasa.value = value;
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
  public Map<String, Object> update(Patchable<CommissionValue> nuevoValor, OffsetDateTime ahora) {

    Map<String, Object> cambios = new LinkedHashMap<>();

    if (nuevoValor.presente()) {
      CommissionValue valor = nuevoValor.valor();
      if (valor == null) {
        String mensaje = "La forma de la comisión no puede vaciarse.";
        throw new ValidationException(
            "VAL-002", mensaje, List.of(new FieldError("rateType", "VAL-002", mensaje)));
      }
      // NO es `compareTo` sobre la cifra, y ese detalle es el defecto silencioso
      // de esta operación. `10 %` y `10` de importe fijo dan `compareTo == 0` y
      // NO son ni remotamente el mismo valor: si se comparara así, esta
      // corrección devolvería éxito sin escribir, sin auditar y sin mover la
      // marca de modificación, y la tasa seguiría pagando el 10 %.
      // Tampoco es `equals`, que daría 10.00 y 10.0000 por distintos y llenaría
      // el registro de cambios que no cambian nada. Ver `CommissionValue`.
      if (!value.mismoValorQue(valor)) {
        cambios.put(
            "value", Map.of("before", value.paraAuditoria(), "after", valor.paraAuditoria()));
        value = valor;
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
    // La forma va SIEMPRE, y no solo el número. Sin ella, un `10` guardado aquí
    // no dice si esa tasa pagaba una décima parte de la venta o diez unidades
    // de dinero — y como esta tabla no tiene vigencia, este registro es la
    // única copia que queda de lo que la tasa decía antes.
    estado.put("rate_type", value.getRateType().name());
    estado.put("value", value.cifra().toPlainString());
    return estado;
  }

  public boolean estaRetirada() {
    return deletedAt != null;
  }

  public UUID getId() {
    return id;
  }

  public UUID getRoleId() {
    return roleId;
  }

  public CommissionValue getValue() {
    return value;
  }

  /**
   * El porcentaje, o <b>nulo si esta tasa paga un importe fijo</b>.
   *
   * <p>Se conserva por comodidad de quien arma respuestas, y hay que leerlo con {@link
   * CommissionValue#getRateType()} delante: un nulo aquí <b>no significa que falte</b>.
   */
  public BigDecimal getPercentage() {
    return value.getPercentage();
  }

  /** El importe fijo, o nulo si esta tasa paga un porcentaje. Ver {@link #getPercentage()}. */
  public BigDecimal getFixedAmount() {
    return value.getFixedAmount();
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
