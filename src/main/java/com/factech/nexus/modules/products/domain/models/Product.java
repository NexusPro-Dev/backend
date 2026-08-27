package com.factech.nexus.modules.products.domain.models;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Una unidad de venta del catálogo (`RF-PM-001`).
 *
 * <p><b>Es a la vez agregado y modelo persistente</b>, como {@code Role} y {@code Membership}:
 * `architecture.md` §5.1 sitúa el modelo persistente en {@code domain/models}, de modo que no hay
 * dos representaciones que unir ni mapeador que las una.
 *
 * <p><b>Nace {@link ProductStatus#INACTIVO}</b> (`RN-PM-012`), y el estado no se recibe: no existe
 * forma de crear un producto ya publicado. Es lo que hace verificable que `RN-PM-004` solo pueda
 * violarse desde `RF-PM-005`.
 *
 * <p><b>Lo que no se puede corregir vive aquí sin mutador</b>: el tipo, el código y la membresía
 * destino definen qué derecho otorga el producto, y cambiarlos convertiría lo comprado en otra
 * cosa.
 */
@Entity
@Table(name = "products")
public class Product {

  /**
   * Letras mayúsculas, dígitos y guion bajo, empezando por letra (`VAL-010`).
   *
   * <p>Compilado una sola vez: {@code String.matches} recompila el patrón en cada llamada.
   */
  private static final Pattern PATRON_CODIGO = Pattern.compile("^[A-Z][A-Z0-9_]*$");

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "code", nullable = false, length = 50, updatable = false)
  private String code;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 30, updatable = false)
  private ProductType type;

  @Column(name = "name", nullable = false, length = 150)
  private String name;

  @Column(name = "description")
  private String description;

  /**
   * Identificador y no una asociación {@code @ManyToOne}: apunta a una tabla de otro módulo, y una
   * asociación traería aquí su entidad — que es exactamente lo que D-25 impide. Los datos del
   * destino entran por la interfaz que `SP` publica.
   */
  @Column(name = "target_membership_id", updatable = false)
  private UUID targetMembershipId;

  @Column(name = "price", nullable = false, precision = 14, scale = 4)
  private BigDecimal price;

  @Column(name = "currency_id", nullable = false)
  private UUID currencyId;

  /** Días que dura lo adquirido, desde la compra. Nulo: no caduca (`RN-PM-015`). */
  @Column(name = "validity_days")
  private Integer validityDays;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ProductStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;

  /** Exigido por JPA. */
  protected Product() {}

  /**
   * Registra un producto, siempre inactivo.
   *
   * <p><b>El estado no se recibe</b> (`CA-PM-068`): nace {@link ProductStatus#INACTIVO} y solo
   * `RF-PM-005` puede publicarlo. Que no haya forma de pasarlo por argumento es lo que lo hace
   * verificable.
   *
   * <p><b>La condición cruzada de `RN-PM-002` se comprueba aquí, en los dos sentidos</b>, y no solo
   * en el esquema: un upgrade sin destino es inservible, y un servicio <b>con</b> destino promete
   * un cambio de nivel que nadie va a aplicar. La segunda mitad es la que se olvida, y es la
   * peligrosa — no falla, promete.
   *
   * @param ahora instante del alta, inyectado para que la prueba pueda fijarlo
   */
  public static Product create(
      UUID id,
      String code,
      ProductType type,
      String name,
      String description,
      UUID targetMembershipId,
      BigDecimal price,
      UUID currencyId,
      Integer validityDays,
      OffsetDateTime ahora) {

    Product producto = new Product();
    producto.id = id;
    producto.code = normalizarCodigo(code);
    producto.type = type;
    producto.name = recortar(name);
    producto.description = recortar(description);
    verificarTipoYDestino(type, targetMembershipId);
    producto.targetMembershipId = targetMembershipId;
    producto.price = price;
    producto.currencyId = currencyId;
    producto.validityDays = validityDays;
    producto.status = ProductStatus.INACTIVO;
    producto.createdAt = ahora;
    producto.updatedAt = ahora;
    return producto;
  }

  /**
   * Publica el producto (`RF-PM-005`).
   *
   * <p><b>Devuelve si hubo cambio, y no lanza si ya estaba activo</b> (`FA-001`): quien pulsa dos
   * veces el mismo botón no ha hecho nada malo, y rechazarlo obligaría a la interfaz a consultar el
   * estado antes de cada pulsación. El valor devuelto es lo que decide si se audita — un evento por
   * una petición que no cambió nada convertiría el registro en ruido.
   *
   * <p><b>Aquí no se comprueba `RN-PM-004`</b> —un solo upgrade activo por destino—: eso mira a
   * <b>otras</b> filas y el agregado solo conoce la suya. Vive en el caso de uso y, sobre todo, en
   * {@code uq_products_upgrade_target}.
   *
   * @return {@code true} si el producto pasó de inactivo a activo
   */
  public boolean activate(OffsetDateTime ahora) {
    return cambiarEstado(ProductStatus.ACTIVO, ahora);
  }

  /**
   * Retira el producto de la oferta sin sacarlo del catálogo (`RF-PM-005`).
   *
   * <p><b>Desactivar no es eliminar</b>: la fila sigue viva, sigue apareciendo en el catálogo y
   * puede volver a publicarse. Lo que cambia es que deja de ofrecerse.
   *
   * @return {@code true} si el producto pasó de activo a inactivo
   */
  public boolean deactivate(OffsetDateTime ahora) {
    return cambiarEstado(ProductStatus.INACTIVO, ahora);
  }

  private boolean cambiarEstado(ProductStatus destino, OffsetDateTime ahora) {
    if (status == destino) {
      return false;
    }
    status = destino;
    updatedAt = ahora;
    return true;
  }

  /**
   * ¿Tiene descripción con la que publicarse? (`RN-PM-014`)
   *
   * <p>Vive en el agregado y no en el caso de uso porque la respuesta depende de cómo se normaliza
   * la descripción al escribirla: {@link #recortar} deja en nulo la que solo trae espacios, de modo
   * que preguntar por el nulo aquí es preguntar por lo mismo que se guardó.
   */
  public boolean tieneDescripcion() {
    return description != null && !description.isBlank();
  }

  /**
   * Recorta el código y lo pasa a mayúsculas, y rechaza lo que no cumpla el formato.
   *
   * <p><b>Valida en el dominio y no solo en el DTO.</b> El {@code @Pattern} del DTO atiende a quien
   * llega por HTTP; esta comprobación atiende a cualquier otro camino —una siembra, otro caso de
   * uso— y es la que hace que un código mal formado no pueda existir dentro del modelo.
   */
  private static String normalizarCodigo(String valor) {
    String normalizado = valor == null ? null : valor.trim().toUpperCase(Locale.ROOT);
    if (normalizado == null || !PATRON_CODIGO.matcher(normalizado).matches()) {
      String mensaje =
          "El código solo admite letras mayúsculas, dígitos y guion bajo, y debe empezar por letra.";
      throw new ValidationException(
          "VAL-010", mensaje, List.of(new FieldError("code", "VAL-010", mensaje)));
    }
    return normalizado;
  }

  /**
   * `RN-PM-002`, en los dos sentidos.
   *
   * <p><b>Es pública porque el caso de uso la ejecuta ANTES de buscar nada</b> (`plan.md` §4, paso
   * 2). Comprobar que un upgrade TRAE destino no es lo mismo que comprobar que ese destino EXISTE,
   * y hacerlas juntas reportaría un upgrade sin destino como «la membresía no existe» —un `422`
   * sobre un dato que el actor nunca envió— en lugar del `400` que le corresponde. La regla sigue
   * viviendo en un solo sitio: {@link #create} también la llama.
   *
   * <p>El `CHECK` del esquema dice lo mismo, y esta comprobación no es redundante: la restricción
   * produciría un fallo de integridad —un {@code 500}— donde corresponde un {@code 400} que diga
   * <b>cuál</b> de las dos mitades se incumplió.
   */
  public static void verificarTipoYDestino(ProductType tipo, UUID destino) {
    if (tipo.exigeDestino() && destino == null) {
      String mensaje = "Un producto de upgrade debe declarar su membresía destino.";
      throw new ValidationException(
          "VAL-007", mensaje, List.of(new FieldError("targetMembershipId", "VAL-007", mensaje)));
    }
    if (!tipo.exigeDestino() && destino != null) {
      String mensaje = "Un producto de servicio no puede declarar membresía destino.";
      throw new ValidationException(
          "VAL-008", mensaje, List.of(new FieldError("targetMembershipId", "VAL-008", mensaje)));
    }
  }

  /**
   * Recorta espacios al inicio y al final.
   *
   * <p>Sin este recorte, {@code "Plan Oro "} y {@code "Plan Oro"} serían dos nombres distintos para
   * {@code uq_products_name} y la unicidad se burlaría con un espacio.
   */
  private static String recortar(String valor) {
    if (valor == null) {
      return null;
    }
    String recortado = valor.trim();
    return recortado.isEmpty() ? null : recortado;
  }

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public ProductType getType() {
    return type;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public UUID getTargetMembershipId() {
    return targetMembershipId;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public UUID getCurrencyId() {
    return currencyId;
  }

  public Integer getValidityDays() {
    return validityDays;
  }

  public ProductStatus getStatus() {
    return status;
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
