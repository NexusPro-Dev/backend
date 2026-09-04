package com.factech.nexus.modules.movements.domain.repository;

import com.factech.nexus.modules.movements.domain.models.Movement;
import java.util.List;
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

  /**
   * Los métodos de pago <b>activos</b>, cada uno con los países en los que no vale (`RF-MV-009`).
   *
   * <p><b>Solo los activos.</b> Quien consume esto pinta un selector, y un elemento que no se puede
   * elegir no va en un selector. Leer una venta vieja pagada con un método retirado es `RF-MV-007`,
   * y esa lectura trae el método <b>de la venta</b> y no del catálogo — que es la mitad de
   * `RN-MV-018` que a esta consulta no le toca.
   *
   * <p><b>Una sola sentencia para el catálogo y sus exclusiones.</b> Tres métodos resueltos uno a
   * uno serían cuatro consultas: con tres filas no se nota, y ese es exactamente el problema — no
   * se notaría hasta que alguien añadiera métodos, y para entonces el patrón estaría copiado en las
   * lecturas que vengan detrás.
   *
   * <p><b>Y la unión es externa.</b> Hoy <b>ningún método tiene exclusiones</b>, de modo que con
   * una unión interna la respuesta vendría vacía: el catálogo entero desaparecería sin error y sin
   * que nada avisara.
   *
   * @return ordenados por código, de forma estable. Sin orden declarado, dos peticiones pueden
   *     devolverlos en distinta posición y un selector cambiaría entre recargas
   */
  List<PaymentMethodCatalogView> findActivePaymentMethods();

  /** El tipo, con el prefijo que su comprobante lleva impreso (`RN-MV-016`). */
  record MovementTypeView(UUID id, String code, String prefix) {}

  /**
   * Un método de pago con sus exclusiones (`RN-MV-019`).
   *
   * <p><b>{@code excludedCountries} vacío significa que vale en todas partes</b>, y nunca es nulo:
   * es la ausencia con significado que `RN-MV-019` declara, y colapsarla con el nulo obligaría a
   * cada consumidor a tratar los dos casos.
   */
  record PaymentMethodCatalogView(
      UUID id, String code, String name, List<ExcludedCountryView> excludedCountries) {}

  /**
   * Un país en el que un método no vale.
   *
   * <p><b>No lleva el nombre</b>, solo el identificador y el código. Quien pinta países ya tiene su
   * catálogo (`RF-SP-021`), y repetir el nombre aquí lo dejaría desincronizado el día que se
   * corrija una tilde.
   */
  record ExcludedCountryView(UUID id, String code) {}

  /** El método de pago, con la marca que `RN-MV-018` obliga a mirar al vender. */
  record PaymentMethodView(UUID id, String code, String name, boolean active) {}
}
