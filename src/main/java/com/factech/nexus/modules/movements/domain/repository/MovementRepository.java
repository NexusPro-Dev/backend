package com.factech.nexus.modules.movements.domain.repository;

import com.factech.nexus.modules.movements.domain.models.Movement;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Puerto de persistencia del libro de movimientos (`RF-MV-001` · `T-10`).
 *
 * <p><b>Lleva también las dos lecturas de los catálogos del propio módulo</b> —tipos y métodos de
 * pago— y eso no rompe «una interfaz por lectura»: esa regla separa los contratos que <b>cruzan la
 * frontera de un módulo</b> (**D-25**), para que ampliar uno no obligue a recompilar a otro. Aquí
 * no cruza nada: {@code movement_types} y {@code payment_methods} son de `MV`, y ningún otro módulo
 * las consulta.
 */
public interface MovementRepository {

  /**
   * Guarda la cabecera y sus líneas, con <b>reintento acotado</b> ante colisión de comprobante.
   *
   * <p><b>El código puede cambiar durante esta llamada</b>, y por eso se recibe cómo generar otro y
   * no solo el que ya trae el agregado. La unicidad la garantiza {@code uq_movements_code} y no el
   * generador: treinta y dos elevado a seis por tipo y día hace la colisión improbable y <b>no
   * imposible</b>, y sin el índice produciría dos comprobantes iguales sin que nada avisara.
   *
   * <p><b>Tres intentos y falla.</b> No es un número mágico con vocación de reintento infinito: si
   * tres códigos aleatorios chocan seguidos, lo que ocurre no es mala suerte —es que el generador
   * está roto o la tabla está llena de una forma que nadie previó—, y seguir intentando lo
   * escondería.
   *
   * @param venta el agregado; su código se reemplaza si hace falta reintentar
   * @param nuevoCodigo cómo obtener otro comprobante para la misma venta
   * @throws RuntimeException si los tres intentos chocan. El adaptador lanza un {@code
   *     IllegalStateException}, y quien lo consume por el proxy de {@code @Repository} lo recibe
   *     <b>traducido</b> a la jerarquía de Spring. No se traduce a un código de negocio a
   *     propósito: nada de lo que el actor envió está mal, y esto sube como fallo del sistema
   */
  void save(Movement venta, Supplier<String> nuevoCodigo);

  /**
   * El tipo de movimiento por su código.
   *
   * <p>Hoy solo existe {@code VENTA}, y se busca igualmente en lugar de constantear su
   * identificador: las etapas 2 a 6 traen depósito, compra de puntos y comisión <b>como filas</b>,
   * y el caso de uso que las registre pedirá el suyo por este mismo método.
   */
  Optional<MovementTypeView> findTypeByCode(String code);

  /**
   * El método de pago, <b>exista o no esté activo</b>.
   *
   * <p><b>No filtra por {@code is_active}</b>, y la diferencia decide el código de respuesta: uno
   * inexistente es {@code 422} —una referencia que no resuelve— y uno desactivado es {@code 409}
   * —un conflicto con el estado del sistema— (`EX-010`). Filtrando aquí, los dos volverían vacíos y
   * quien escribió bien el identificador buscaría el error donde no está.
   */
  Optional<PaymentMethodView> findPaymentMethod(UUID id);

  /** El tipo, con el prefijo que su comprobante lleva impreso (`RN-MV-016`). */
  record MovementTypeView(UUID id, String code, String prefix) {}

  /** El método de pago, con la marca que `RN-MV-018` obliga a mirar al vender. */
  record PaymentMethodView(UUID id, String code, String name, boolean active) {}
}
