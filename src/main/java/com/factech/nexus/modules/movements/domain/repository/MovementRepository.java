package com.factech.nexus.modules.movements.domain.repository;

import com.factech.nexus.modules.movements.domain.models.Movement;
import com.factech.nexus.modules.movements.domain.models.TypeStatus;
import com.factech.nexus.shared.pagination.BoundedCount;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
   * Un estado del catálogo de un tipo, por su código (`RN-MV-033`). Se busca y no se constantea,
   * por lo mismo que {@link #findTypeByCode}.
   */
  Optional<TypeStatus> findTypeStatus(UUID movementTypeId, String code);

  /**
   * ¿Declara algún tipo un estado con este código? Es lo que valida el filtro de los listados: un
   * código que no existe es un error y no una página vacía (`RF-MV-016`).
   */
  boolean existsTypeStatusCode(String code);

  // ---------------------------------------------------------------------------
  // `RF-MV-016` — asignar los vendedores
  // ---------------------------------------------------------------------------

  /**
   * La cabecera de la venta <b>bloqueada</b> hasta el final de la transacción ({@code SELECT … FOR
   * UPDATE}). Serializa la asignación con confirmar —cuyo {@code UPDATE} condicionado espera a la
   * fila— y con otra asignación. Vacío si no existe.
   */
  Optional<AssignmentHeader> lockForAssignment(UUID movementId);

  /** Las líneas de la venta, con su producto y su vendedor actual (nulo si no lo tiene). */
  List<AssignmentLine> findLinesForAssignment(UUID movementId);

  /** Escribe el vendedor de una línea. */
  void assignSeller(UUID lineId, UUID sellerId);

  /** Cambia el estado del tipo de un movimiento. */
  void changeTypeStatus(UUID movementId, UUID typeStatusId);

  /** Lo que la asignación necesita de la cabecera: de quién es, de qué tipo y en qué estados. */
  record AssignmentHeader(
      UUID id, UUID userId, UUID movementTypeId, String status, String typeStatus) {}

  record AssignmentLine(UUID lineId, UUID productId, UUID sellerId) {}

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
   * El método por su <b>código</b>, para resolver el pago gratuito (`RN-MV-022`).
   *
   * <p>Por código y no por identificador porque <b>nadie puede aportar ese identificador</b>: el
   * catálogo de `RF-MV-009` no publica lo `INTERNO` (`RN-MV-023`), de modo que el caso de uso lo
   * resuelve por la convención que `V78` siembra — mismo criterio que {@code
   * MembershipCatalog.floor()} con `BECA`.
   */
  Optional<PaymentMethodView> findPaymentMethodByCode(String code);

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
  /**
   * Un método de pago ya elegido o ya asignado.
   *
   * <p><b>Lleva la visibilidad aunque esta lectura no filtre por ella</b>: quien la consume es el
   * registro de una venta, y `RN-MV-022` necesita saber si el método es el gratuito para rechazarlo
   * en una venta con importe. El catálogo de `RF-MV-009` sí filtra, y lo hace en su propia
   * consulta.
   */
  record PaymentMethodView(UUID id, String code, String name, boolean active, String visibility) {}

  // ---------------------------------------------------------------------------
  // `RF-MV-008` — los movimientos propios
  // ---------------------------------------------------------------------------

  /**
   * La página de movimientos a nombre de {@code actorId}, del más reciente al más antiguo.
   *
   * <p><b>Desde el 22-09-2026 son los movimientos A NOMBRE de {@code actorId}</b> —lo que compró— y
   * no aquellos en los que participa de cualquier forma: lo que vendió se consulta por `RF-MV-015`,
   * que además llega a toda su red (`spec.md` §2).
   *
   * <p><b>El alcance va DENTRO de la sentencia y no se aplica después</b>, y esa es la única
   * decisión de este método. Traer de más y descartar en Java haría que el total contase
   * movimientos ajenos, y dejaría el filtro en un sitio donde moverlo no rompe nada visible.
   *
   * <p>No hay sobrecarga que acepte otra persona: consultar las de un tercero es `RF-MV-006`, con
   * su permiso.
   */
  List<MyMovementRow> findMine(UUID actorId, MyMovementsFilter filter, int offset, int limit);

  /** Cuántos hay en total. Exacto: es el conjunto de una persona, no una tabla sin límite. */
  long countMine(UUID actorId, MyMovementsFilter filter);

  /**
   * Los filtros del listado propio (`RF-MV-008`), ya normalizados y validados: el estado y el tipo
   * desde el 21-09-2026 por la mañana, y el método de pago, el comprobante y el periodo desde esa
   * tarde. <b>No lleva al actor</b>: el actor no es un filtro, es el alcance.
   *
   * @param code ya en mayúsculas
   * @param from inclusive, sobre {@code occurred_at}
   * @param to exclusive, sobre {@code occurred_at}
   */
  record MyMovementsFilter(
      String status,
      String type,
      UUID paymentMethodId,
      String code,
      OffsetDateTime from,
      OffsetDateTime to) {}

  /**
   * Los vendedores de las líneas de esos movimientos, <b>sin repetir</b> por movimiento.
   *
   * <p>Es una segunda consulta por página y no un agregado dentro de {@link #findMine}, a
   * propósito: la sentencia paginada devuelve <b>una fila por movimiento</b> y un {@code JOIN} con
   * las líneas la multiplicaría. Hoy cada venta trae un vendedor; el día que las líneas difieran
   * (`RN-MV-003`), esta lectura ya lo dice.
   */
  List<MovementSellerRow> findSellersOf(Collection<UUID> movementIds);

  /**
   * El detalle de un movimiento propio, con sus líneas.
   *
   * <p><b>Devuelve vacío tanto si no existe como si es ajeno</b> (`EX-002`): quien llama no puede
   * distinguir los dos casos, porque distinguirlos confirmaría la existencia de un identificador
   * ajeno.
   */
  /**
   * El detalle de un movimiento del actor: lo que compró <b>y lo que vendió</b>.
   *
   * <p><b>No se acotó cuando el listado sí, el 22-09-2026</b> (`spec.md` §2): acotarlo dejaría a un
   * vendedor sin ninguna vía para abrir una venta suya mientras `RF-MV-007` no exista.
   */
  Optional<MovementDetailView> findMineById(UUID movementId, UUID actorId);

  /**
   * Una fila del listado propio, con el papel ya resuelto.
   *
   * <p><b>{@code role} llega calculado por el motor</b> y no por Java: el identificador de quien
   * pregunta ya está atado a la consulta, y resolverlo fuera obligaría a arrastrar el sujeto y los
   * vendedores de las líneas solo para compararlos y descartarlos. Desde el 16-09-2026 el vendedor
   * es de la línea (`RN-MV-003`) y la cabecera lleva solo al sujeto ({@code user_id}, `RN-MV-026`);
   * los vendedores se piden aparte con {@link #findSellersOf}.
   */
  record MyMovementRow(
      UUID id,
      String code,
      String type,
      String status,
      String typeStatus,
      UUID userId,
      String userUsername,
      String userFirstName,
      String userLastName,
      UUID packageId,
      UUID currencyId,
      String currencyCode,
      String paymentMethod,
      BigDecimal totalAmount,
      BigDecimal discountAmount,
      BigDecimal payableAmount,
      OffsetDateTime occurredAt,
      OffsetDateTime confirmedAt,
      OffsetDateTime voidedAt,
      String voidReason,
      OffsetDateTime createdAt) {}

  /** Un vendedor de las líneas de un movimiento. */
  record MovementSellerRow(
      UUID movementId, UUID sellerId, String username, String firstName, String lastName) {}

  /** La cabecera y sus líneas. */
  record MovementDetailView(MyMovementRow header, List<MovementLineRow> lines) {}

  /**
   * Una línea del detalle.
   *
   * <p><b>El nombre y la descripción salen de la LÍNEA</b> desde el 16-09-2026 (`RN-MV-002`): son
   * copias, y `RF-PM-004` puede corregir el catálogo sin reescribir lo vendido. <b>El código sigue
   * saliendo de {@code products}</b> porque `RN-PM-013` lo declara inmutable — lo inmutable se
   * referencia.
   *
   * <p><b>Hasta el 16-09-2026 los dos salían del catálogo</b>, porque `V54` no los congelaba, y la
   * consecuencia estaba declarada sin resolver en `tasks.md` §3: renombrar un producto cambiaba
   * cómo se veía una venta pasada. `V14` lo cierra para el nombre y la descripción.
   *
   * <p><b>El vendedor sí es de la línea</b> (`RN-MV-003`, `V12`) y se lee de ella; nulo solo en los
   * tipos de movimiento que no venden nada. <b>Y el descuento también</b> (`RN-MV-027`, `V14`):
   * {@code lineDiscount} es la suma congelada y {@code discounts} son las rebajas que la explican,
   * como se pactaron y como se cobraron.
   *
   * <p><b>Y la entrega</b> (`RN-MV-030`, `V16`): {@code implementation} es la copia de cómo se
   * entrega, y {@code deliveryStatus}, {@code deliveredAt} y {@code deliveryNote} son lo único de
   * una línea que cambia después de escribirse.
   */
  record MovementLineRow(
      UUID productId,
      String productCode,
      String productName,
      String productDescription,
      int quantity,
      BigDecimal unitPrice,
      BigDecimal lineDiscount,
      BigDecimal lineAmount,
      Integer validityDays,
      UUID sellerId,
      String sellerUsername,
      String sellerFirstName,
      String sellerLastName,
      List<LineDiscountRow> discounts,
      String implementation,
      String deliveryStatus,
      OffsetDateTime deliveredAt,
      String deliveryNote) {}

  // ---------------------------------------------------------------------------
  // `RF-MV-003` — confirmar
  // ---------------------------------------------------------------------------

  /**
   * El detalle de un movimiento <b>sin alcance</b>: para quien confirma, que confirma cualquiera.
   *
   * <p>Es la misma proyección que {@link #findMineById} sin el predicado del actor. Vacío solo si
   * no existe.
   */
  Optional<MovementDetailView> findById(UUID movementId);

  /** El estado actual, para decir en el {@code 409} en qué estado está (`EX-002`). */
  Optional<String> findStatus(UUID movementId);

  /**
   * La transición, <b>condicionada al estado anterior</b>: {@code PENDIENTE} → {@code CONFIRMADA}
   * con {@code confirmed_at} en {@code at}, en una sola sentencia que solo acierta si la venta
   * seguía pendiente.
   *
   * <p><b>La cuenta de filas es la decisión.</b> No es un {@code SELECT} seguido de un {@code
   * UPDATE}: entre los dos puede entrar otra confirmación, y las dos leerían «pendiente». Con la
   * escritura condicionada, la segunda afecta cero filas y responde `EX-002` sin haber leído nada
   * antes — que es lo que hace que una pasarela que reentrega no conceda dos veces (`RN-MV-005`).
   *
   * @return {@code true} si esta llamada hizo la transición; {@code false} si la venta no estaba
   *     pendiente (o no existe: quien llama distingue los dos casos con {@link #findStatus})
   */
  boolean confirmIfPending(UUID movementId, OffsetDateTime at);

  /**
   * Las líneas de un movimiento con lo que hace falta para entregarlas: la implementación copiada,
   * y —para las de upgrade— la membresía destino y su nivel <b>leídos del producto</b>, que no se
   * copian porque `RF-PM-004` rechaza cambiarlos (`requirements/mv.md` §5.4).
   *
   * <p>Se leen <b>después</b> de la transición, no antes: solo quien la ganó recorre las líneas.
   */
  List<DeliveryLineRow> findLinesForDelivery(UUID movementId);

  /**
   * `RF-MV-005`: {@code PENDIENTE} → {@code ANULADA} con el instante y el motivo, en una sola
   * sentencia condicionada al estado anterior — la misma forma que {@link #confirmIfPending}, y por
   * lo mismo. No hay líneas que recorrer: una pendiente no concedió nada.
   *
   * @return {@code true} si esta llamada anuló; {@code false} si no estaba pendiente o no existe
   */
  boolean voidIfPending(UUID movementId, OffsetDateTime at, String reason);

  /** {@code PENDIENTE} → {@code ENTREGADA} en {@code at}. */
  void markDelivered(UUID lineId, OffsetDateTime at);

  /** {@code PENDIENTE} → {@code RETENIDA} con el motivo, escrito para una persona (`RN-MV-029`). */
  void markRetained(UUID lineId, String note);

  /**
   * Una línea a punto de entregarse.
   *
   * @param upgrade si el producto concede una membresía
   * @param targetMembershipId la destino del producto; nula si no es un upgrade
   * @param targetMembershipLevel su nivel; nulo si no es un upgrade
   */
  record DeliveryLineRow(
      UUID lineId,
      String productCode,
      String implementation,
      boolean upgrade,
      UUID targetMembershipId,
      String targetMembershipCode,
      Integer targetMembershipLevel,
      Integer validityDays) {}

  // ---------------------------------------------------------------------------
  // `RF-MV-014` — los productos comprados propios
  // ---------------------------------------------------------------------------

  /**
   * Los productos de las ventas <b>a nombre de</b> {@code actorId}, una fila por línea, de la
   * compra más reciente a la más antigua, <b>con el estado ya calculado</b>.
   *
   * <p>El estado se calcula en la sentencia y no en Java, por lo mismo que el papel en {@link
   * #findMine}: el filtro por estado tiene que aplicarse en la consulta para que el total cuente lo
   * que devuelve. Y {@code now} entra como parámetro y no como {@code now()} de la base: es lo que
   * permite probar el vencimiento sin esperar y lo que deja el borde fijado — una vigencia que
   * vence exactamente ahora ya venció, como la membresía en `SP`.
   *
   * @param state uno de los seis de {@code PurchasedProductState}, o nulo para todos
   */
  List<MyProductRow> findMyProducts(
      UUID actorId, String state, OffsetDateTime now, int offset, int limit);

  /** Cuántos hay. Exacto: es el conjunto de una persona. */
  long countMyProducts(UUID actorId, String state, OffsetDateTime now);

  /**
   * Una fila del registro de lo comprado (`RF-MV-014`).
   *
   * @param state calculado por el motor de la venta, la entrega y la vigencia
   * @param validUntil {@code deliveredAt + validityDays}; nulo si no se entregó o si no caduca
   */
  record MyProductRow(
      UUID movementId,
      String movementCode,
      String movementStatus,
      UUID productId,
      String productCode,
      String productName,
      int quantity,
      String implementation,
      String state,
      OffsetDateTime purchasedAt,
      OffsetDateTime deliveredAt,
      OffsetDateTime validUntil,
      String deliveryNote) {}

  /** Una rebaja de una línea, tal como quedó. */
  record LineDiscountRow(String type, BigDecimal value, BigDecimal discountValue) {}

  // ---------------------------------------------------------------------------
  // `RF-MV-006` — todos los movimientos
  // ---------------------------------------------------------------------------

  /**
   * La página de movimientos que cumplen el filtro, del más reciente al más antiguo.
   *
   * <p><b>Aquí no hay alcance dentro de la sentencia</b>, y esa es la diferencia con {@link
   * #findMine}: la puerta es {@code movements:read} en el controlador, y esta consulta devuelve lo
   * que el filtro deje. El sujeto y el vendedor son <b>filtros</b> y no el actor.
   *
   * <p><b>El vendedor se filtra con {@code EXISTS} sobre las líneas y no con {@code JOIN}</b>, por
   * lo mismo que en el listado propio: un {@code JOIN} multiplicaría el movimiento por sus líneas,
   * y una compra de paquete tiene varias con el mismo vendedor.
   */
  List<MovementRow> findAll(MovementFilter filter, int offset, int limit);

  /**
   * Cuántos cumplen el filtro, <b>acotado</b>.
   *
   * <p>Exacto hasta {@code techo} y «más de {@code techo}» por encima, como los cuatro registros de
   * auditoría y al revés que {@link #countMine}: allí se cuenta el conjunto de una persona; aquí,
   * la tabla entera, y un {@code COUNT(*)} exacto sin filtros es un recorrido completo por página.
   * <b>El predicado es el mismo que el de {@link #findAll}</b>, escrito una vez.
   */
  BoundedCount countAll(MovementFilter filter, int techo);

  // ---------------------------------------------------------------------------
  // `RF-MV-015` — las ventas de mi alcance
  // ---------------------------------------------------------------------------

  /**
   * La página de <b>ventas</b> del alcance dado, del más reciente al más antiguo.
   *
   * <p><b>El alcance va dentro de la sentencia</b>, como en {@link #findMine}, y a diferencia de
   * allí no es «el actor» sino lo que `SP` resolvió de él (`RN-MV-031`, {@code CommercialReach}):
   * todo, el conjunto de vendedores de su red, o solo él como sujeto. Quién entra en el conjunto no
   * lo decide este repositorio; lo aplica.
   *
   * <p>El tipo va fijo a {@code VENTA}: es la consulta de las ventas, y los otros tipos tendrán la
   * suya. El vendedor —el del alcance y el del filtro— entra por el mismo {@code EXISTS} sobre las
   * líneas, una fila por venta.
   */
  List<MovementRow> findSales(SalesFilter filter, int offset, int limit);

  /** Cuántas ventas del alcance cumplen el filtro, <b>acotado</b>, sobre el mismo predicado. */
  BoundedCount countSales(SalesFilter filter, int techo);

  /**
   * El alcance ya resuelto por `SP` y los filtros de `RF-MV-015`, para escribir el predicado una
   * vez.
   *
   * <p><b>Lo que NO tiene es el caso «fuera del alcance»</b>: si {@code sellerId} no está en la red
   * —o no es el actor cuando el alcance es propio— el caso de uso <b>no llama</b> a este
   * repositorio y responde vacío por definición (`plan.md` §4.4). Aquí llega solo lo que sí puede
   * verse.
   *
   * @param everything todo el libro de ventas (un rol de tipo {@code FUNCIONARIO})
   * @param network los vendedores de mi red, conmigo dentro; vacío cuando no aplica
   * @param ownerId el sujeto, cuando el alcance es «solo yo»; nulo cuando no aplica
   * @param sellerId el vendedor por el que se acota, ya comprobado dentro del alcance; nulo si no
   *     se acota
   */
  record SalesFilter(
      boolean everything,
      Set<UUID> network,
      UUID ownerId,
      UUID sellerId,
      String status,
      String typeStatus,
      UUID paymentMethodId,
      String code,
      OffsetDateTime from,
      OffsetDateTime to) {
    public SalesFilter {
      network = network == null ? Set.of() : Set.copyOf(network);
    }
  }

  /**
   * Lo que acota el listado global. Todo opcional; nulo significa «sin acotar por esto».
   *
   * <p><b>Vive en el puerto y no en {@code application}</b>: es lo que el adaptador necesita para
   * escribir el predicado, y la petición HTTP lo produce. Al revés, el adaptador tendría que
   * conocer la forma de la petición.
   *
   * @param code ya en mayúsculas: la comparación es por igualdad, para que la responda {@code
   *     uq_movements_code}
   * @param from inclusive, sobre {@code occurred_at}
   * @param to exclusive, sobre {@code occurred_at}
   * @param type el código del tipo de movimiento, ya en mayúsculas y ya validado contra el catálogo
   *     (21-09-2026)
   */

  /**
   * Las líneas de las ventas, paginadas (`RF-MV-017`).
   *
   * <p><b>Una fila por LÍNEA</b>, con lo de su venta repetido: es la pregunta «qué se ha vendido»,
   * que ningún listado por movimiento contesta sin abrir cada venta.
   *
   * <p><b>Todo lo que la fila publica viaja en esta sentencia.</b> Al contrario que {@link
   * #findAll}, aquí no hace falta una segunda consulta para el vendedor: allí una venta tiene
   * varios y la fila es la venta; aquí la fila es la línea y tiene <b>uno</b>.
   */
  List<SaleLineRow> findSaleLines(SaleLinesFilter filter, int offset, int limit);

  /** El total de lo mismo, <b>acotado</b>: `movement_details` es la tabla que más crece. */
  BoundedCount countSaleLines(SaleLinesFilter filter, int techo);

  /**
   * Los siete filtros del listado de líneas, todos opcionales y combinables.
   *
   * <p><b>No lleva actor ni alcance</b>, y su ausencia es la implementación: `RF-MV-017` es de
   * administración y con el permiso se ve todo el libro. El alcance por estructura vive en {@link
   * SalesFilter}.
   */
  record SaleLinesFilter(
      UUID movementId,
      UUID userId,
      UUID sellerId,
      UUID productId,
      String status,
      String deliveryStatus,
      String code,
      OffsetDateTime from,
      OffsetDateTime to) {}

  /**
   * Una línea con su venta, plana como sale del motor.
   *
   * <p><b>El vendedor puede venir nulo entero</b> —las cuatro columnas— porque la unión es un
   * {@code LEFT JOIN}: `movement_details.seller_id` es nulable desde `V12` y una línea sin vendedor
   * tiene que <b>salir</b>, no desaparecer.
   */
  record SaleLineRow(
      UUID lineId,
      UUID movementId,
      String movementCode,
      String movementStatus,
      OffsetDateTime occurredAt,
      UUID clientId,
      String clientUsername,
      String clientFirstName,
      String clientLastName,
      UUID sellerId,
      String sellerUsername,
      String sellerFirstName,
      String sellerLastName,
      UUID productId,
      String productCode,
      String productName,
      int quantity,
      BigDecimal unitPrice,
      BigDecimal lineDiscount,
      BigDecimal lineAmount,
      Integer validityDays,
      String currencyCode,
      String implementation,
      String deliveryStatus,
      OffsetDateTime deliveredAt,
      String deliveryNote) {}

  record MovementFilter(
      String status,
      String type,
      String typeStatus,
      UUID userId,
      UUID sellerId,
      UUID paymentMethodId,
      String code,
      OffsetDateTime from,
      OffsetDateTime to) {}

  /**
   * Una fila del listado global: la cabecera con su tipo y su confirmación, <b>sin papel</b>.
   *
   * <p>Es un registro distinto de {@link MyMovementRow} porque son dos contratos que cambian por
   * motivos distintos (`plan.md` §3). Los vendedores se piden aparte con {@link #findSellersOf},
   * igual que allí.
   */
  record MovementRow(
      UUID id,
      String code,
      String type,
      String status,
      String typeStatus,
      UUID userId,
      String userUsername,
      String userFirstName,
      String userLastName,
      UUID currencyId,
      String currencyCode,
      String paymentMethod,
      BigDecimal totalAmount,
      BigDecimal discountAmount,
      BigDecimal payableAmount,
      OffsetDateTime occurredAt,
      OffsetDateTime confirmedAt) {}
}
