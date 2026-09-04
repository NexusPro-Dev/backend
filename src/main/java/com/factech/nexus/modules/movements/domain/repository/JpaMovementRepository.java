package com.factech.nexus.modules.movements.domain.repository;

import com.factech.nexus.modules.movements.domain.models.Movement;
import com.factech.nexus.modules.movements.domain.models.MovementLine;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Repository;

/**
 * Adaptador del libro de movimientos.
 *
 * <h2>Escribe con {@code INSERT} nativo y no con {@code persist}, y no es una preferencia</h2>
 *
 * <p>El reintento acotado del comprobante lo exige. Con {@code persist}, la violación de {@code
 * uq_movements_code} llega como excepción y <b>marca la transacción para deshacerse</b>: el segundo
 * intento ya no podría ocurrir dentro de ella, y «tres intentos y falla» pasaría a necesitar una
 * transacción por intento —con la cabecera y sus líneas repartidas entre varias, que es justo lo
 * que `plan.md` §7 prohíbe—.
 *
 * <p>Con {@code ON CONFLICT (code) DO NOTHING}, el rechazo es <b>una cuenta de filas afectadas</b>
 * y no una excepción, de modo que el reintento es un bucle dentro de la misma transacción. Es el
 * mismo recurso, y por el mismo motivo, que {@code UserRepository.addRoles}: declarar el conflicto
 * como esperado en lugar de descubrirlo por excepción.
 *
 * <p><b>El conflicto se apunta a {@code (code)} y no se deja abierto.</b> Un {@code ON CONFLICT DO
 * NOTHING} sin columna atraparía también la clave primaria, y una colisión de {@code UUID}
 * —imposible en la práctica, pero no declarada imposible— se trataría como una colisión de
 * comprobante: se reintentaría con otro código y el identificador repetido seguiría ahí.
 */
@Repository
public class JpaMovementRepository implements MovementRepository {

  /**
   * Tres, y el número está aquí y no en el caso de uso porque es una propiedad de <b>cómo se
   * escribe</b>. Si tres códigos aleatorios chocan seguidos, lo que ocurre no es mala suerte: es
   * que el generador está roto o la tabla está llena de una forma que nadie previó. Seguir
   * intentando lo escondería detrás de una latencia rara.
   */
  static final int INTENTOS = 3;

  private final EntityManager em;

  public JpaMovementRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public void save(Movement venta, Supplier<String> nuevoCodigo) {
    for (int intento = 1; intento <= INTENTOS; intento++) {
      if (insertarCabecera(venta) == 1) {
        insertarLineas(venta);
        return;
      }
      if (intento < INTENTOS) {
        venta.reemplazarCodigo(nuevoCodigo.get());
      }
    }
    // No es un error del cliente y no se traduce a un código de negocio: nada
    // de lo que envió está mal. Sube como fallo del sistema, que es lo que es.
    throw new IllegalStateException(
        "No se pudo emitir un comprobante único en %d intentos para la venta %s."
            .formatted(INTENTOS, venta.getId()));
  }

  /**
   * @return {@code 1} si la fila entró, {@code 0} si el comprobante ya estaba tomado
   */
  private int insertarCabecera(Movement venta) {
    return em.createNativeQuery(
            """
            INSERT INTO movements (id, movement_type_id, client_id, seller_id,
                                   payment_method_id, currency_id, code, status,
                                   total_amount, discount_amount, payable_amount,
                                   occurred_at, created_at)
            VALUES (:id, :tipo, :cliente, :vendedor, :metodo, :moneda, :codigo, :estado,
                    :total, :descuento, :aPagar, :ocurrio, :creado)
            ON CONFLICT (code) DO NOTHING
            """)
        .setParameter("id", venta.getId())
        .setParameter("tipo", venta.getMovementTypeId())
        .setParameter("cliente", venta.getClientId())
        .setParameter("vendedor", venta.getSellerId())
        .setParameter("metodo", venta.getPaymentMethodId())
        .setParameter("moneda", venta.getCurrencyId())
        .setParameter("codigo", venta.getCode())
        .setParameter("estado", venta.getStatus().name())
        .setParameter("total", venta.getTotalAmount())
        .setParameter("descuento", venta.getDiscountAmount())
        .setParameter("aPagar", venta.getPayableAmount())
        .setParameter("ocurrio", venta.getOccurredAt())
        .setParameter("creado", venta.getCreatedAt())
        // `confirmed_at` NO se escribe, y su ausencia es la que satisface
        // `ck_movements_confirmed`: nula si y solo si el estado no es
        // CONFIRMADA. Pasarla explícitamente como nula diría lo mismo y
        // sugeriría que este INSERT podría escribir otra cosa.
        .executeUpdate();
  }

  /**
   * Las líneas, después de la cabecera y en la misma transacción.
   *
   * <p><b>Sin {@code ON CONFLICT}</b>, a diferencia de la cabecera: aquí un choque contra {@code
   * uq_movement_details_producto} significa que la venta lleva el mismo producto dos veces, que es
   * `RN-MV-011` y no una colisión de azar. Debe fallar, y no reintentarse.
   */
  private void insertarLineas(Movement venta) {
    for (MovementLine linea : venta.getLines()) {
      em.createNativeQuery(
              """
              INSERT INTO movement_details (id, movement_id, product_id, quantity,
                                            unit_price, line_amount, validity_days)
              VALUES (:id, :venta, :producto, :cantidad, :precio, :importe, :vigencia)
              """)
          .setParameter("id", linea.getId())
          .setParameter("venta", venta.getId())
          .setParameter("producto", linea.getProductId())
          .setParameter("cantidad", linea.getQuantity())
          .setParameter("precio", linea.getUnitPrice())
          .setParameter("importe", linea.getLineAmount())
          .setParameter("vigencia", linea.getValidityDays())
          .executeUpdate();
    }
  }

  @Override
  public Optional<MovementTypeView> findTypeByCode(String code) {
    if (code == null) {
      return Optional.empty();
    }
    List<Tuple> filas =
        em.createNativeQuery(
                "SELECT id, code, prefix FROM movement_types WHERE code = :codigo", Tuple.class)
            .setParameter("codigo", code)
            .getResultList();

    return filas.stream()
        .findFirst()
        .map(
            fila ->
                new MovementTypeView(
                    (UUID) fila.get("id"), (String) fila.get("code"), (String) fila.get("prefix")));
  }

  @Override
  public Optional<PaymentMethodView> findPaymentMethod(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    List<Tuple> filas =
        em.createNativeQuery(
                "SELECT id, code, name, is_active FROM payment_methods WHERE id = :id", Tuple.class)
            .setParameter("id", id)
            .getResultList();

    return filas.stream()
        .findFirst()
        .map(
            fila ->
                new PaymentMethodView(
                    (UUID) fila.get("id"),
                    (String) fila.get("code"),
                    (String) fila.get("name"),
                    (Boolean) fila.get("is_active")));
  }
}
