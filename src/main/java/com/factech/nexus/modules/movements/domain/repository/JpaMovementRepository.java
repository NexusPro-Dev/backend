package com.factech.nexus.modules.movements.domain.repository;

import com.factech.nexus.modules.movements.domain.models.LineDiscount;
import com.factech.nexus.modules.movements.domain.models.Movement;
import com.factech.nexus.modules.movements.domain.models.MovementLine;
import com.factech.nexus.modules.movements.domain.models.TypeStatus;
import com.factech.nexus.shared.pagination.BoundedCount;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
            INSERT INTO movements (id, movement_type_id, user_id, package_id,
                                   payment_method_id, currency_id, code, status,
                                   type_status_id, total_amount, discount_amount,
                                   payable_amount, occurred_at, created_at)
            VALUES (:id, :tipo, :sujeto, :paquete, :metodo, :moneda, :codigo, :estado,
                    :estadoDelTipo, :total, :descuento, :aPagar, :ocurrio, :creado)
            ON CONFLICT (code) DO NOTHING
            """)
        .setParameter("id", venta.getId())
        .setParameter("tipo", venta.getMovementTypeId())
        .setParameter("sujeto", venta.getUserId())
        .setParameter("paquete", venta.getPackageId())
        .setParameter("metodo", venta.getPaymentMethodId())
        .setParameter("moneda", venta.getCurrencyId())
        .setParameter("codigo", venta.getCode())
        .setParameter("estado", venta.getStatus().name())
        .setParameter("estadoDelTipo", venta.getTypeStatus().id())
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
              INSERT INTO movement_details (id, movement_id, product_id, seller_id,
                                            product_name, product_description,
                                            quantity, unit_price, line_discount, line_amount,
                                            validity_days, implementation)
              VALUES (:id, :venta, :producto, :vendedor, :nombre, :descripcion, :cantidad,
                      :precio, :descuento, :importe, :vigencia, :implementacion)
              """)
          .setParameter("id", linea.getId())
          .setParameter("venta", venta.getId())
          .setParameter("producto", linea.getProductId())
          .setParameter("vendedor", linea.getSellerId())
          .setParameter("nombre", linea.getProductName())
          .setParameter("descripcion", linea.getProductDescription())
          .setParameter("cantidad", linea.getQuantity())
          .setParameter("precio", linea.getUnitPrice())
          .setParameter("descuento", linea.getLineDiscount())
          .setParameter("importe", linea.getLineAmount())
          .setParameter("vigencia", linea.getValidityDays())
          // La copia de cómo se entrega (`RN-MV-030`). La entrega misma no se
          // escribe: `delivery_status` nace `PENDIENTE` por el DEFAULT de `V16`,
          // y nada se entrega antes de confirmar.
          .setParameter("implementacion", linea.getImplementation().name())
          .executeUpdate();
      insertarRebajas(linea);
    }
  }

  // Hoy ninguna entrada produce rebajas y el bucle no gira; existe para que la
  // compra de paquetes no tenga que tocar el repositorio (`RN-MV-027`).
  private void insertarRebajas(MovementLine linea) {
    for (LineDiscount rebaja : linea.getDiscounts()) {
      em.createNativeQuery(
              """
              INSERT INTO movement_detail_discounts (id, movement_detail_id, type, value,
                                                     discount_value)
              VALUES (:id, :linea, :tipo, :valor, :dinero)
              """)
          .setParameter("id", rebaja.getId())
          .setParameter("linea", linea.getId())
          .setParameter("tipo", rebaja.getType().name())
          .setParameter("valor", rebaja.getValue())
          .setParameter("dinero", rebaja.getDiscountValue())
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
  @Transactional(readOnly = true)
  public Optional<TypeStatus> findTypeStatus(UUID movementTypeId, String code) {
    if (movementTypeId == null || code == null) {
      return Optional.empty();
    }
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT id, code FROM movement_type_statuses
                 WHERE movement_type_id = :tipo AND code = :codigo
                """,
                Tuple.class)
            .setParameter("tipo", movementTypeId)
            .setParameter("codigo", code)
            .getResultList();
    return filas.stream()
        .findFirst()
        .map(fila -> new TypeStatus((UUID) fila.get("id"), (String) fila.get("code")));
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsTypeStatusCode(String code) {
    if (code == null) {
      return false;
    }
    Object hay =
        em.createNativeQuery(
                "SELECT EXISTS (SELECT 1 FROM movement_type_statuses WHERE code = :codigo)")
            .setParameter("codigo", code)
            .getSingleResult();
    return Boolean.TRUE.equals(hay);
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-016` — asignar los vendedores
  // ---------------------------------------------------------------------------

  /**
   * <b>{@code FOR UPDATE OF m}</b>, y no un {@code FOR UPDATE} a secas: la unión con el catálogo
   * bloquearía también su fila, y dos asignaciones de ventas distintas se esperarían por un estado
   * que ninguna de las dos cambia.
   */
  @Override
  @Transactional
  public Optional<AssignmentHeader> lockForAssignment(UUID movementId) {
    if (movementId == null) {
      return Optional.empty();
    }
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT m.id AS id, m.user_id AS sujeto, m.movement_type_id AS tipo,
                       m.status AS status, mts.code AS type_status
                  FROM movements m
                  JOIN movement_type_statuses mts ON mts.id = m.type_status_id
                 WHERE m.id = :id
                   FOR UPDATE OF m
                """,
                Tuple.class)
            .setParameter("id", movementId)
            .getResultList();
    return filas.stream()
        .findFirst()
        .map(
            fila ->
                new AssignmentHeader(
                    (UUID) fila.get("id"),
                    (UUID) fila.get("sujeto"),
                    (UUID) fila.get("tipo"),
                    (String) fila.get("status"),
                    (String) fila.get("type_status")));
  }

  @Override
  @Transactional(readOnly = true)
  public List<AssignmentLine> findLinesForAssignment(UUID movementId) {
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT id, product_id, seller_id FROM movement_details
                 WHERE movement_id = :id
                """,
                Tuple.class)
            .setParameter("id", movementId)
            .getResultList();
    List<AssignmentLine> resultado = new ArrayList<>(filas.size());
    for (Tuple fila : filas) {
      resultado.add(
          new AssignmentLine(
              (UUID) fila.get("id"), (UUID) fila.get("product_id"), (UUID) fila.get("seller_id")));
    }
    return resultado;
  }

  @Override
  @Transactional
  public void assignSeller(UUID lineId, UUID sellerId) {
    em.createNativeQuery("UPDATE movement_details SET seller_id = :vendedor WHERE id = :id")
        .setParameter("vendedor", sellerId)
        .setParameter("id", lineId)
        .executeUpdate();
  }

  @Override
  @Transactional
  public void changeTypeStatus(UUID movementId, UUID typeStatusId) {
    em.createNativeQuery("UPDATE movements SET type_status_id = :estado WHERE id = :id")
        .setParameter("estado", typeStatusId)
        .setParameter("id", movementId)
        .executeUpdate();
  }

  /**
   * El catálogo activo con sus exclusiones, en <b>una</b> sentencia.
   *
   * <p>La consulta devuelve una fila <b>por par método-país</b> y aquí se agrupan. Es la forma de
   * traer una colección anidada sin la {@code N+1} que `plan.md` §3.2 descarta — la que con tres
   * filas no se nota, y por eso se copia.
   *
   * <p><b>{@code LEFT JOIN} y no interno.</b> Hoy ningún método tiene exclusiones: con una unión
   * interna esta consulta devolvería <b>cero métodos</b>, y el catálogo entero desaparecería sin
   * error. Es el riesgo 2 del plan, y lo detecta la prueba de la lista vacía.
   *
   * <p><b>{@code LinkedHashMap} y no {@code HashMap}</b>: el orden que fija el {@code ORDER BY} es
   * parte del contrato —un selector que cambia de posición entre recargas está roto— y agrupar en
   * un mapa sin orden lo perdería después de haberlo pedido.
   */
  @Override
  @Transactional(readOnly = true)
  public List<PaymentMethodCatalogView> findActivePaymentMethods() {
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT m.id AS m_id, m.code AS m_code, m.name AS m_name,
                       c.id AS c_id, c.code AS c_code
                  FROM payment_methods m
                  LEFT JOIN payment_method_exclusions e ON e.payment_method_id = m.id
                  LEFT JOIN countries c ON c.id = e.country_id
                 WHERE m.is_active = true
                   -- `RN-MV-023`: lo INTERNO NO SE OFRECE NUNCA, y no hay parámetro
                   -- que lo traiga. Es la asimetría deliberada con `is_active`
                   -- —que se podría exponer bajo petición— y con `RN-MV-019`,
                   -- donde la exclusión por país se publica y el cliente filtra.
                   -- Aquí el cliente no filtra porque NO LO VE: lo elige el
                   -- sistema, no una persona, y publicarlo solo daría ocasión de
                   -- ofrecerlo por error en el selector de pago.
                   AND m.visibility = 'PUBLICO'
                 ORDER BY m.code ASC, c.code ASC
                """,
                Tuple.class)
            .getResultList();

    Map<UUID, PaymentMethodCatalogView> porMetodo = new LinkedHashMap<>();
    for (Tuple fila : filas) {
      UUID metodo = (UUID) fila.get("m_id");
      PaymentMethodCatalogView vista =
          porMetodo.computeIfAbsent(
              metodo,
              id ->
                  new PaymentMethodCatalogView(
                      id,
                      (String) fila.get("m_code"),
                      (String) fila.get("m_name"),
                      new ArrayList<>()));

      // Nulo en un método sin exclusiones, que es el estado de los tres de hoy:
      // la fila existe por el LEFT JOIN y no trae país.
      UUID pais = (UUID) fila.get("c_id");
      if (pais != null) {
        vista.excludedCountries().add(new ExcludedCountryView(pais, (String) fila.get("c_code")));
      }
    }
    return List.copyOf(porMetodo.values());
  }

  /**
   * El método por su <b>código</b>, para resolver el pago gratuito (`RN-MV-022`).
   *
   * <p>Por código y no por identificador porque <b>nadie puede aportar ese identificador</b>: el
   * catálogo de `RF-MV-009` no publica lo `INTERNO` (`RN-MV-023`). Mismo criterio que {@code
   * MembershipCatalog.floor()} con `BECA` — el literal vive en un solo sitio y `V78` lo siembra en
   * todos los entornos.
   */
  @Override
  public Optional<PaymentMethodView> findPaymentMethodByCode(String code) {
    if (code == null || code.isBlank()) {
      return Optional.empty();
    }
    List<Tuple> filas =
        em.createNativeQuery(
                "SELECT id, code, name, is_active, visibility FROM payment_methods"
                    + " WHERE code = :code",
                Tuple.class)
            .setParameter("code", code.trim().toUpperCase())
            .getResultList();

    return filas.stream().findFirst().map(JpaMovementRepository::aVista);
  }

  private static PaymentMethodView aVista(Tuple fila) {
    return new PaymentMethodView(
        (UUID) fila.get("id"),
        (String) fila.get("code"),
        (String) fila.get("name"),
        (Boolean) fila.get("is_active"),
        (String) fila.get("visibility"));
  }

  @Override
  public Optional<PaymentMethodView> findPaymentMethod(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    List<Tuple> filas =
        em.createNativeQuery(
                // SIN filtro de visibilidad, al contrario que el catálogo de
                // arriba: esta lectura RESUELVE UN MÉTODO YA ELEGIDO —el que la
                // venta declara o el que el sistema asigna— y no ofrece nada.
                // Filtrarlo aquí haría irresoluble el pago gratuito, que es
                // justo el que nadie puede elegir.
                "SELECT id, code, name, is_active, visibility FROM payment_methods WHERE id = :id",
                Tuple.class)
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
                    (Boolean) fila.get("is_active"),
                    (String) fila.get("visibility")));
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-008` — los movimientos propios
  // ---------------------------------------------------------------------------

  /**
   * La selección del LISTADO propio, escrita una vez: la página y el conteo tienen que filtrar
   * igual.
   *
   * <p><b>Desde el 22-09-2026 mira UNA columna: {@code m.user_id}</b> (`RF-MV-008` · `plan.md`
   * §2.2), por decisión del responsable del proyecto — «mis compras» trae solo lo comprado. Lo que
   * el actor <b>vendió</b> se consulta por `RF-MV-015`, que además llega a toda su red. Hasta esa
   * fecha el predicado era {@code m.user_id = :actor OR EXISTS (… d.seller_id = :actor)}, con dos
   * decisiones que ya no hacen falta aquí y que <b>siguen vivas en {@link #filtroGlobal} y en
   * {@link #filtroDeVentas}</b>: {@code OR} y no {@code UNION} —que habría duplicado el movimiento
   * de quien es las dos cosas—, y {@code EXISTS} sobre las líneas y no {@code JOIN} —que habría
   * multiplicado la venta por sus líneas—.
   *
   * <p><b>El DETALLE no se acotó con el listado</b>: {@link #findMineById} conserva el {@code OR} a
   * propósito, porque acotarlo dejaría a un vendedor sin ninguna vía para abrir lo que vendió
   * (`CA-MV-138`).
   *
   * <p>El {@code CAST} del estado no es adorno: sin él, PostgreSQL no sabe de qué tipo es el
   * parámetro cuando llega nulo y rechaza la comparación. El tipo (21-09-2026) entra con la misma
   * forma, y {@code movement_types} se une aquí desde ese día: hasta entonces la fila propia no
   * decía su tipo y la sentencia no lo necesitaba.
   */
  private static final String SELECCION_PROPIA =
      """
      FROM movements m
      JOIN movement_types mt ON mt.id = m.movement_type_id
      JOIN movement_type_statuses mts ON mts.id = m.type_status_id
      JOIN users suj ON suj.id = m.user_id
      JOIN currencies cur ON cur.id = m.currency_id
      JOIN payment_methods pm ON pm.id = m.payment_method_id
      WHERE m.user_id = :actor
        AND (CAST(:estado AS varchar) IS NULL OR m.status = CAST(:estado AS varchar))
        AND (CAST(:tipo AS varchar) IS NULL OR mt.code = CAST(:tipo AS varchar))
      """;

  /**
   * Las columnas de la cabecera propia, compartidas por el listado y por los dos detalles.
   *
   * <p><b>Desde el 22-09-2026 no resuelve el papel</b> (`RF-MV-008` · `plan.md` §2.2): el listado
   * trae solo lo comprado, de modo que {@code role} valdría siempre {@code BUYER} y se retiró del
   * contrato. Con el {@code CASE} se va el único motivo por el que {@link #findById} —el detalle
   * <b>sin alcance</b> de `RF-MV-003`— ataba {@code :actor} a nulo.
   *
   * <p>Hasta esa fecha el papel lo calculaba el motor con un {@code CASE} de tres ramas, y la de
   * «ambos» iba primero: escrita al final, las dos anteriores ya habrían capturado la fila y nadie
   * lo habría visto hasta que alguien de la fuerza comercial se comprara algo a sí mismo. Queda
   * anotado porque el día que vuelva a hacer falta un papel, ese es el orden.
   */
  private static final String CABECERA_PROPIA =
      """
      SELECT m.id AS id, m.code AS code, mt.code AS tipo, m.status AS status,
             mts.code AS type_status,
             suj.id AS suj_id, suj.username AS suj_username,
             suj.first_name AS suj_first, suj.last_name AS suj_last,
             m.package_id AS paquete,
             cur.id AS cur_id, cur.code AS cur_code, pm.name AS pm_name,
             m.total_amount AS total, m.discount_amount AS descuento,
             m.payable_amount AS pagar,
             m.occurred_at AS occurred_at, m.confirmed_at AS confirmed_at,
             m.voided_at AS voided_at, m.void_reason AS void_reason,
             m.created_at AS created_at
      """;

  /**
   * Los tres filtros del 21-09-2026 por la tarde —método de pago, comprobante y periodo— entran
   * como la clase {@link Filtro} del listado global y <b>no</b> con la forma {@code CAST(:x) IS
   * NULL OR …} del estado y el tipo, que es lo que `RF-MV-008` · `plan.md` §4.3 había escrito: un
   * identificador o un instante nulos enlazados sin tipo son exactamente lo que PostgreSQL no sabe
   * convertir, y es el motivo por el que `filtroGlobal` se hizo así. Desviación declarada en
   * `tasks.md` §3. El estado y el tipo se quedan como estaban: funcionan y son cadenas.
   */
  private static Filtro filtroPropio(MyMovementsFilter f) {
    Filtro filtro = new Filtro();
    filtro.igual("m.payment_method_id", "metodo", f.paymentMethodId());
    filtro.igual("m.code", "codigo", f.code());
    if (f.from() != null) {
      filtro.condicion("m.occurred_at >= :desdeCuando", "desdeCuando", f.from());
    }
    if (f.to() != null) {
      filtro.condicion("m.occurred_at < :hastaCuando", "hastaCuando", f.to());
    }
    return filtro;
  }

  @Override
  @Transactional(readOnly = true)
  public List<MyMovementRow> findMine(
      UUID actorId, MyMovementsFilter filtro, int offset, int limit) {
    Filtro mas = filtroPropio(filtro);
    Query consulta =
        em.createNativeQuery(
            CABECERA_PROPIA
                + SELECCION_PROPIA
                + " AND "
                + mas.sql()
                // EL DESEMPATE POR `id` NO ES COSMÉTICO: sin él, dos
                // movimientos del mismo instante pueden repetirse en una
                // página y faltar en la siguiente sin que nada falle.
                + " ORDER BY m.occurred_at DESC, m.id DESC LIMIT :limite OFFSET :desde",
            Tuple.class);
    mas.enlazar(consulta);
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        consulta
            .setParameter("actor", actorId)
            .setParameter("estado", filtro.status())
            .setParameter("tipo", filtro.type())
            .setParameter("limite", limit)
            .setParameter("desde", offset)
            .getResultList();

    List<MyMovementRow> resultado = new ArrayList<>(filas.size());
    for (Tuple fila : filas) {
      resultado.add(cabecera(fila));
    }
    return resultado;
  }

  @Override
  @Transactional(readOnly = true)
  public long countMine(UUID actorId, MyMovementsFilter filtro) {
    Filtro mas = filtroPropio(filtro);
    Query consulta =
        em.createNativeQuery("SELECT count(*) " + SELECCION_PROPIA + " AND " + mas.sql());
    mas.enlazar(consulta);
    Object total =
        consulta
            .setParameter("actor", actorId)
            .setParameter("estado", filtro.status())
            .setParameter("tipo", filtro.type())
            .getSingleResult();
    return ((Number) total).longValue();
  }

  /**
   * <b>El alcance va en la sentencia, no en una comprobación posterior.</b> Un movimiento ajeno no
   * se lee y luego se rechaza: no se lee. Por eso el vacío significa las dos cosas —no existe, o no
   * es suyo— y quien llama no puede distinguirlas (`EX-002`).
   */
  @Override
  @Transactional(readOnly = true)
  public List<MovementSellerRow> findSellersOf(Collection<UUID> movementIds) {
    if (movementIds == null || movementIds.isEmpty()) {
      return List.of();
    }
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT DISTINCT d.movement_id AS movement_id, u.id AS ven_id,
                       u.username AS ven_username, u.first_name AS ven_first,
                       u.last_name AS ven_last
                  FROM movement_details d
                  JOIN users u ON u.id = d.seller_id
                 WHERE d.movement_id IN (:movimientos)
                 ORDER BY d.movement_id, u.username
                """,
                Tuple.class)
            .setParameter("movimientos", List.copyOf(movementIds))
            .getResultList();
    List<MovementSellerRow> resultado = new ArrayList<>(filas.size());
    for (Tuple fila : filas) {
      resultado.add(
          new MovementSellerRow(
              (UUID) fila.get("movement_id"),
              (UUID) fila.get("ven_id"),
              (String) fila.get("ven_username"),
              (String) fila.get("ven_first"),
              (String) fila.get("ven_last")));
    }
    return resultado;
  }

  /**
   * <b>El detalle NO se acotó con el listado el 22-09-2026</b>, y la asimetría es deliberada
   * (`RF-MV-008` · `spec.md` §2): aquí sigue el {@code OR} —lo comprado <b>y</b> lo vendido— porque
   * acotarlo dejaría a un vendedor sin ninguna vía para abrir una venta suya: `RF-MV-007` no existe
   * y `RF-MV-015` es solo listado. Quien venga a «poner esto coherente con el listado» hará fallar
   * `CA-MV-138`, que existe para eso.
   */
  @Override
  @Transactional(readOnly = true)
  public Optional<MovementDetailView> findMineById(UUID movementId, UUID actorId) {
    if (movementId == null || actorId == null) {
      return Optional.empty();
    }
    Query cabecera =
        em.createNativeQuery(
                CABECERA_PROPIA
                    + """
                    FROM movements m
                    JOIN movement_types mt ON mt.id = m.movement_type_id
                    JOIN movement_type_statuses mts ON mts.id = m.type_status_id
                    JOIN users suj ON suj.id = m.user_id
                    JOIN currencies cur ON cur.id = m.currency_id
                    JOIN payment_methods pm ON pm.id = m.payment_method_id
                    WHERE m.id = :movimiento
                      AND (m.user_id = :actor OR EXISTS (SELECT 1 FROM movement_details d
                                                          WHERE d.movement_id = m.id
                                                            AND d.seller_id = :actor))
                    """,
                Tuple.class)
            .setParameter("movimiento", movementId)
            .setParameter("actor", actorId);
    return detalle(movementId, cabecera);
  }

  /**
   * <b>Sin alcance</b>, para quien confirma (`RF-MV-003`): la misma proyección que el detalle
   * propio sin el predicado del actor. Hasta el 22-09-2026 ataba {@code :actor} a nulo porque la
   * cabecera resolvía el papel con un {@code CASE}; retirado el papel, no ata nada — lo que sigue
   * importando es no tener dos proyecciones de la misma cabecera.
   */
  @Override
  @Transactional(readOnly = true)
  public Optional<MovementDetailView> findById(UUID movementId) {
    if (movementId == null) {
      return Optional.empty();
    }
    Query cabecera =
        em.createNativeQuery(
                CABECERA_PROPIA
                    + """
                    FROM movements m
                    JOIN movement_types mt ON mt.id = m.movement_type_id
                    JOIN movement_type_statuses mts ON mts.id = m.type_status_id
                    JOIN users suj ON suj.id = m.user_id
                    JOIN currencies cur ON cur.id = m.currency_id
                    JOIN payment_methods pm ON pm.id = m.payment_method_id
                    WHERE m.id = :movimiento
                    """,
                Tuple.class)
            .setParameter("movimiento", movementId);
    return detalle(movementId, cabecera);
  }

  /** La cabecera que la consulta dada devuelva, con sus líneas y rebajas. */
  @SuppressWarnings("unchecked")
  private Optional<MovementDetailView> detalle(UUID movementId, Query consultaDeCabecera) {
    List<Tuple> cabecera = consultaDeCabecera.getResultList();
    if (cabecera.isEmpty()) {
      return Optional.empty();
    }

    // EL CÓDIGO Y EL NOMBRE DEL PRODUCTO SALEN DE `products`, no de la línea:
    // `V54` NO LOS CONGELA en `movement_details`, que guarda el identificador, la
    // cantidad, el precio y la vigencia y nada más. Queda declarado en el puerto
    // y en `tasks.md` §3 — renombrar un producto cambia cómo se ve una venta ya
    // registrada, y eso no se resuelve aquí.
    List<Tuple> lineas =
        em.createNativeQuery(
                """
                SELECT d.id AS linea_id, d.product_id AS product_id, p.code AS p_code,
                       d.product_name AS p_name, d.product_description AS p_desc,
                       d.quantity AS cantidad, d.unit_price AS precio,
                       d.line_discount AS descuento, d.line_amount AS importe,
                       d.validity_days AS vigencia,
                       d.implementation AS impl, d.delivery_status AS entrega,
                       d.delivered_at AS entregada_en, d.delivery_note AS motivo,
                       v.id AS ven_id, v.username AS ven_username,
                       v.first_name AS ven_first, v.last_name AS ven_last
                  FROM movement_details d
                  -- EL NOMBRE Y LA DESCRIPCION SALEN DE LA LINEA desde el
                  -- 16-09-2026 (`RN-MV-002`): son copias. De `products` solo se
                  -- lee el CODIGO, que `RN-PM-013` declara inmutable.
                  JOIN products p ON p.id = d.product_id
                  -- LEFT: la columna admite nulo por los tipos de movimiento que
                  -- no venden nada y, desde `V36`, en una venta por validar.
                  LEFT JOIN users v ON v.id = d.seller_id
                 WHERE d.movement_id = :movimiento
                 ORDER BY p.code ASC
                """,
                Tuple.class)
            .setParameter("movimiento", movementId)
            .getResultList();

    Map<UUID, List<LineDiscountRow>> rebajas = rebajasDe(movementId);
    List<MovementLineRow> detalle = new ArrayList<>(lineas.size());
    for (Tuple linea : lineas) {
      detalle.add(
          new MovementLineRow(
              (UUID) linea.get("product_id"),
              (String) linea.get("p_code"),
              (String) linea.get("p_name"),
              (String) linea.get("p_desc"),
              ((Number) linea.get("cantidad")).intValue(),
              (BigDecimal) linea.get("precio"),
              (BigDecimal) linea.get("descuento"),
              (BigDecimal) linea.get("importe"),
              linea.get("vigencia") == null ? null : ((Number) linea.get("vigencia")).intValue(),
              (UUID) linea.get("ven_id"),
              (String) linea.get("ven_username"),
              (String) linea.get("ven_first"),
              (String) linea.get("ven_last"),
              rebajas.getOrDefault((UUID) linea.get("linea_id"), List.of()),
              (String) linea.get("impl"),
              (String) linea.get("entrega"),
              instante(linea.get("entregada_en")),
              (String) linea.get("motivo")));
    }

    return Optional.of(new MovementDetailView(cabecera(cabecera.get(0)), detalle));
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-003` — confirmar
  // ---------------------------------------------------------------------------

  @Override
  @Transactional(readOnly = true)
  public Optional<String> findStatus(UUID movementId) {
    @SuppressWarnings("unchecked")
    List<Object> filas =
        em.createNativeQuery("SELECT status FROM movements WHERE id = :id")
            .setParameter("id", movementId)
            .getResultList();
    return filas.isEmpty() ? Optional.empty() : Optional.of((String) filas.get(0));
  }

  /**
   * <b>La cuenta de filas es la decisión.</b> Cero filas significa «no estaba pendiente» —o no
   * existe—, y se responde sin haber leído nada antes: dos confirmaciones simultáneas no pueden
   * leer las dos «pendiente», porque ninguna lee. La que llega segunda espera el bloqueo de fila
   * que la primera tomó, y al despertar encuentra {@code CONFIRMADA} y afecta cero.
   */
  @Override
  @Transactional
  public boolean confirmIfPending(UUID movementId, OffsetDateTime at) {
    int filas =
        em.createNativeQuery(
                """
                UPDATE movements
                   SET status = 'CONFIRMADA', confirmed_at = :ahora
                 WHERE id = :id AND status = 'PENDIENTE'
                """)
            .setParameter("id", movementId)
            .setParameter("ahora", at)
            .executeUpdate();
    return filas == 1;
  }

  @Override
  @Transactional(readOnly = true)
  public List<DeliveryLineRow> findLinesForDelivery(UUID movementId) {
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT d.id AS linea_id, p.code AS p_code, d.implementation AS impl,
                       p.type AS p_type, p.target_membership_id AS m_id,
                       m.code AS m_code, m.level AS m_level,
                       d.validity_days AS vigencia
                  FROM movement_details d
                  JOIN products p ON p.id = d.product_id
                  -- La membresía destino NO se copia (`RF-PM-004` rechaza cambiarla):
                  -- se lee del producto, que `RN-PM-010` garantiza que sigue ahí.
                  LEFT JOIN memberships m ON m.id = p.target_membership_id
                 WHERE d.movement_id = :movimiento
                 ORDER BY p.code ASC
                """,
                Tuple.class)
            .setParameter("movimiento", movementId)
            .getResultList();
    List<DeliveryLineRow> resultado = new ArrayList<>(filas.size());
    for (Tuple fila : filas) {
      resultado.add(
          new DeliveryLineRow(
              (UUID) fila.get("linea_id"),
              (String) fila.get("p_code"),
              (String) fila.get("impl"),
              "UPGRADE_MEMBRESIA".equals(fila.get("p_type")),
              (UUID) fila.get("m_id"),
              (String) fila.get("m_code"),
              fila.get("m_level") == null ? null : ((Number) fila.get("m_level")).intValue(),
              fila.get("vigencia") == null ? null : ((Number) fila.get("vigencia")).intValue()));
    }
    return resultado;
  }

  @Override
  @Transactional
  public boolean voidIfPending(UUID movementId, OffsetDateTime at, String reason) {
    int filas =
        em.createNativeQuery(
                """
                UPDATE movements
                   SET status = 'ANULADA', voided_at = :ahora, void_reason = :motivo
                 WHERE id = :id AND status = 'PENDIENTE'
                """)
            .setParameter("id", movementId)
            .setParameter("ahora", at)
            .setParameter("motivo", reason)
            .executeUpdate();
    return filas == 1;
  }

  @Override
  @Transactional
  public void markDelivered(UUID lineId, OffsetDateTime at) {
    // CONDICIONADO a `PENDIENTE`: de `ENTREGADA` o `RETENIDA` no se sale, y una
    // línea que ya no estuviera pendiente aquí es un fallo, no un caso.
    int filas =
        em.createNativeQuery(
                """
                UPDATE movement_details
                   SET delivery_status = 'ENTREGADA', delivered_at = :ahora
                 WHERE id = :id AND delivery_status = 'PENDIENTE'
                """)
            .setParameter("id", lineId)
            .setParameter("ahora", at)
            .executeUpdate();
    if (filas != 1) {
      throw new IllegalStateException("La línea " + lineId + " no estaba pendiente de entrega.");
    }
  }

  @Override
  @Transactional
  public void markRetained(UUID lineId, String note) {
    int filas =
        em.createNativeQuery(
                """
                UPDATE movement_details
                   SET delivery_status = 'RETENIDA', delivery_note = :motivo
                 WHERE id = :id AND delivery_status = 'PENDIENTE'
                """)
            .setParameter("id", lineId)
            .setParameter("motivo", note)
            .executeUpdate();
    if (filas != 1) {
      throw new IllegalStateException("La línea " + lineId + " no estaba pendiente de entrega.");
    }
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-014` — los productos comprados propios
  // ---------------------------------------------------------------------------

  /**
   * El estado y el «hasta» en la MISMA expresión, para que no puedan discrepar: {@code VENCIDO} es
   * exactamente «hay hasta y ya pasó», con el borde de `SP` — igual al instante ya venció.
   *
   * <p>El alcance es un solo papel, el sujeto (`spec.md` §3): un vendedor no «tiene» lo que colocó.
   */
  private static final String PRODUCTOS_PROPIOS =
      """
      FROM movement_details d
      JOIN movements m ON m.id = d.movement_id
      JOIN products p ON p.id = d.product_id
      CROSS JOIN LATERAL (
        SELECT CASE WHEN d.delivered_at IS NULL OR d.validity_days IS NULL THEN NULL
                    ELSE d.delivered_at + make_interval(days => d.validity_days) END AS hasta
      ) v
      CROSS JOIN LATERAL (
        SELECT CASE
                 WHEN m.status = 'PENDIENTE'          THEN 'PENDIENTE_PAGO'
                 WHEN m.status = 'RECHAZADA'          THEN 'RECHAZADO'
                 WHEN m.status = 'ANULADA'            THEN 'ANULADO'
                 WHEN d.delivery_status = 'RETENIDA'  THEN 'RETENIDO'
                 WHEN d.delivery_status = 'PENDIENTE' THEN 'PENDIENTE_AUTORIZACION'
                 WHEN v.hasta IS NOT NULL AND v.hasta <= CAST(:ahora AS timestamptz)
                                                      THEN 'VENCIDO'
                 ELSE                                      'ACTIVO'
               END AS estado
      ) e
      WHERE m.user_id = :actor
        AND (CAST(:estado AS varchar) IS NULL OR e.estado = CAST(:estado AS varchar))
      """;

  @Override
  @Transactional(readOnly = true)
  public List<MyProductRow> findMyProducts(
      UUID actorId, String state, OffsetDateTime now, int offset, int limit) {
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT m.id AS mov_id, m.code AS mov_code, m.status AS mov_status,
                       d.product_id AS product_id, p.code AS p_code, d.product_name AS p_name,
                       d.quantity AS cantidad, d.implementation AS impl, e.estado AS estado,
                       m.occurred_at AS comprado_en, d.delivered_at AS entregado_en,
                       v.hasta AS hasta, d.delivery_note AS motivo
                """
                    + PRODUCTOS_PROPIOS
                    // De la compra más reciente a la más antigua; el desempate por
                    // venta y por código de producto es lo que lo hace estable.
                    + " ORDER BY m.occurred_at DESC, m.id DESC, p.code ASC"
                    + " LIMIT :limite OFFSET :desde",
                Tuple.class)
            .setParameter("actor", actorId)
            .setParameter("estado", state)
            .setParameter("ahora", now)
            .setParameter("limite", limit)
            .setParameter("desde", offset)
            .getResultList();
    List<MyProductRow> resultado = new ArrayList<>(filas.size());
    for (Tuple fila : filas) {
      resultado.add(
          new MyProductRow(
              (UUID) fila.get("mov_id"),
              (String) fila.get("mov_code"),
              (String) fila.get("mov_status"),
              (UUID) fila.get("product_id"),
              (String) fila.get("p_code"),
              (String) fila.get("p_name"),
              ((Number) fila.get("cantidad")).intValue(),
              (String) fila.get("impl"),
              (String) fila.get("estado"),
              instante(fila.get("comprado_en")),
              instante(fila.get("entregado_en")),
              instante(fila.get("hasta")),
              (String) fila.get("motivo")));
    }
    return resultado;
  }

  @Override
  @Transactional(readOnly = true)
  public long countMyProducts(UUID actorId, String state, OffsetDateTime now) {
    Object total =
        em.createNativeQuery("SELECT count(*) " + PRODUCTOS_PROPIOS)
            .setParameter("actor", actorId)
            .setParameter("estado", state)
            .setParameter("ahora", now)
            .getSingleResult();
    return ((Number) total).longValue();
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-006` — todos los movimientos
  // ---------------------------------------------------------------------------

  /**
   * Las tablas del listado global. <b>Sin alcance</b>: al revés que {@link #SELECCION_PROPIA}, aquí
   * no hay actor —la puerta es el permiso en el controlador— y el predicado lo pone {@link
   * #filtroGlobal}, escrito una vez para la página y el conteo.
   */
  private static final String TABLAS_GLOBALES =
      """
      FROM movements m
      JOIN movement_types mt ON mt.id = m.movement_type_id
      JOIN movement_type_statuses mts ON mts.id = m.type_status_id
      JOIN users suj ON suj.id = m.user_id
      JOIN currencies cur ON cur.id = m.currency_id
      JOIN payment_methods pm ON pm.id = m.payment_method_id
      WHERE
      """;

  /**
   * El predicado del listado global, <b>escrito una vez</b> para la página y el conteo.
   *
   * <p>Si se copiara en las dos sentencias acabaría distinto en una de ellas, y entonces el total
   * no correspondería a lo devuelto — es la forma que `RF-SP-011` fijó para la auditoría, y también
   * su mecánica: cada filtro se añade <b>solo cuando viene</b>, en lugar de apagarse con un {@code
   * IS NULL} sobre el parámetro, porque un identificador nulo enlazado sin tipo es lo que
   * PostgreSQL no sabe convertir.
   *
   * <p>El vendedor entra por el mismo {@code EXISTS} del listado propio —una fila por movimiento,
   * tenga las líneas que tenga— y el código por igualdad, para que lo responda {@code
   * uq_movements_code}. El rango es <b>semiabierto</b>: dos periodos consecutivos no devuelven dos
   * veces el movimiento de la medianoche. El tipo (21-09-2026) se compara con {@code mt.code}:
   * {@code movement_types} ya estaba en {@link #TABLAS_GLOBALES} para pintar el de cada fila.
   */
  private static Filtro filtroGlobal(MovementFilter f) {
    Filtro filtro = new Filtro();
    filtro.igual("m.status", "estado", f.status());
    filtro.igual("mt.code", "tipo", f.type());
    // El estado del tipo (`RF-MV-016`, 23-09-2026), por código: la pregunta de
    // cada día es «¿qué falta por validar?».
    filtro.igual("mts.code", "estadoDelTipo", f.typeStatus());
    filtro.igual("m.user_id", "sujeto", f.userId());
    if (f.sellerId() != null) {
      filtro.condicion(
          "EXISTS (SELECT 1 FROM movement_details d"
              + " WHERE d.movement_id = m.id AND d.seller_id = :vendedor)",
          "vendedor",
          f.sellerId());
    }
    filtro.igual("m.payment_method_id", "metodo", f.paymentMethodId());
    filtro.igual("m.code", "codigo", f.code());
    if (f.from() != null) {
      filtro.condicion("m.occurred_at >= :desde", "desde", f.from());
    }
    if (f.to() != null) {
      filtro.condicion("m.occurred_at < :hasta", "hasta", f.to());
    }
    return filtro;
  }

  /** Las columnas de la fila del libro, compartidas por `findAll` y `findSales`. */
  private static final String COLUMNAS_GLOBALES =
      """
      SELECT m.id AS id, m.code AS code, mt.code AS tipo, m.status AS status,
             mts.code AS type_status,
             suj.id AS suj_id, suj.username AS suj_username,
             suj.first_name AS suj_first, suj.last_name AS suj_last,
             cur.id AS cur_id, cur.code AS cur_code, pm.name AS pm_name,
             m.total_amount AS total, m.discount_amount AS descuento,
             m.payable_amount AS pagar,
             m.occurred_at AS occurred_at, m.confirmed_at AS confirmed_at
      """;

  @Override
  @Transactional(readOnly = true)
  public List<MovementRow> findAll(MovementFilter filtro, int offset, int limit) {
    // El mismo desempate que el listado propio, y el que lleva
    // `ix_movements_occurred_at` (`V15`): el motor lee el índice en orden y
    // para en el LIMIT.
    return paginaGlobal(filtroGlobal(filtro), offset, limit);
  }

  private static MovementRow filaGlobal(Tuple fila) {
    return new MovementRow(
        (UUID) fila.get("id"),
        (String) fila.get("code"),
        (String) fila.get("tipo"),
        (String) fila.get("status"),
        (String) fila.get("type_status"),
        (UUID) fila.get("suj_id"),
        (String) fila.get("suj_username"),
        (String) fila.get("suj_first"),
        (String) fila.get("suj_last"),
        (UUID) fila.get("cur_id"),
        (String) fila.get("cur_code"),
        (String) fila.get("pm_name"),
        (BigDecimal) fila.get("total"),
        (BigDecimal) fila.get("descuento"),
        (BigDecimal) fila.get("pagar"),
        instante(fila.get("occurred_at")),
        instante(fila.get("confirmed_at")));
  }

  /**
   * El conteo, acotado por construcción: la subconsulta lleva {@code LIMIT techo + 1}, de modo que
   * nunca examina más de esas filas, tenga la tabla mil o cien millones. Es la misma forma de
   * {@code JpaAuditQueryRepository}.
   */
  @Override
  @Transactional(readOnly = true)
  public BoundedCount countAll(MovementFilter filtro, int techo) {
    return contarAcotado(filtroGlobal(filtro), techo);
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-015` — las ventas de mi alcance
  // ---------------------------------------------------------------------------

  /**
   * El predicado de las ventas de mi alcance, <b>escrito una vez</b> para la página y el conteo,
   * sobre las mismas tablas y la misma {@link Filtro} del listado global.
   *
   * <p>Tres partes, en este orden: el tipo, fijo a {@code VENTA}; <b>el alcance</b> tal como `SP`
   * lo resolvió (`plan.md` §4.4) —nada si es todo; un {@code EXISTS} sobre las líneas con el
   * conjunto de vendedores de la red; o {@code m.user_id = :actor} si es solo él—; y los filtros.
   * El vendedor del filtro va en <b>otro</b> {@code EXISTS}, y con el conjunto de la red se
   * superponen sin estorbarse: el caso de uso ya comprobó que está dentro.
   */
  private static Filtro filtroDeVentas(SalesFilter f) {
    Filtro filtro = new Filtro();
    filtro.condicion("mt.code = :tipoVenta", "tipoVenta", "VENTA");
    // El método y el comprobante (21-09-2026) van DESPUÉS del alcance en el
    // mismo predicado: un comprobante ajeno no devuelve nada.
    filtro.igual("m.payment_method_id", "metodo", f.paymentMethodId());
    filtro.igual("m.code", "codigo", f.code());
    if (!f.everything()) {
      if (f.ownerId() != null) {
        filtro.igual("m.user_id", "propietario", f.ownerId());
      } else {
        filtro.condicion(
            "EXISTS (SELECT 1 FROM movement_details d"
                + " WHERE d.movement_id = m.id AND d.seller_id IN (:red))",
            "red",
            f.network());
      }
    }
    if (f.sellerId() != null) {
      filtro.condicion(
          "EXISTS (SELECT 1 FROM movement_details d"
              + " WHERE d.movement_id = m.id AND d.seller_id = :vendedor)",
          "vendedor",
          f.sellerId());
    }
    filtro.igual("m.status", "estado", f.status());
    filtro.igual("mts.code", "estadoDelTipo", f.typeStatus());
    if (f.from() != null) {
      filtro.condicion("m.occurred_at >= :desde", "desde", f.from());
    }
    if (f.to() != null) {
      filtro.condicion("m.occurred_at < :hasta", "hasta", f.to());
    }
    return filtro;
  }

  @Override
  @Transactional(readOnly = true)
  public List<MovementRow> findSales(SalesFilter filtro, int offset, int limit) {
    return paginaGlobal(filtroDeVentas(filtro), offset, limit);
  }

  @Override
  @Transactional(readOnly = true)
  public BoundedCount countSales(SalesFilter filtro, int techo) {
    return contarAcotado(filtroDeVentas(filtro), techo);
  }

  /**
   * La página del libro con el predicado dado: la misma proyección y el mismo orden de `findAll`.
   */
  private List<MovementRow> paginaGlobal(Filtro donde, int offset, int limit) {
    Query consulta =
        em.createNativeQuery(
            COLUMNAS_GLOBALES
                + TABLAS_GLOBALES
                + donde.sql()
                + " ORDER BY m.occurred_at DESC, m.id DESC LIMIT :limite OFFSET :desplazamiento",
            Tuple.class);
    donde.enlazar(consulta);
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        consulta
            .setParameter("limite", limit)
            .setParameter("desplazamiento", offset)
            .getResultList();

    List<MovementRow> resultado = new ArrayList<>(filas.size());
    for (Tuple fila : filas) {
      resultado.add(filaGlobal(fila));
    }
    return resultado;
  }

  private BoundedCount contarAcotado(Filtro donde, int techo) {
    Query consulta =
        em.createNativeQuery(
            "SELECT count(*) FROM (SELECT 1 " + TABLAS_GLOBALES + donde.sql() + " LIMIT :techo) t");
    donde.enlazar(consulta);
    Object contado = consulta.setParameter("techo", (long) techo + 1).getSingleResult();
    return BoundedCount.de(((Number) contado).longValue(), techo);
  }

  /**
   * Un predicado que crece solo con lo que viene, y sus parámetros. Misma forma que en auditoría.
   */

  /**
   * Las tablas de la línea, <b>compartidas por la página y el conteo</b>.
   *
   * <p><b>El vendedor entra con {@code LEFT JOIN} y es la línea que más importa de esta
   * consulta</b>: {@code movement_details.seller_id} es nulable desde `V12` —los tipos de
   * movimiento que no venden no lo llevan— y un {@code JOIN} corriente haría <b>desaparecer</b>
   * esas líneas en lugar de publicarlas con el vendedor nulo. Desaparecer sin error es el peor modo
   * de fallar que tiene un listado, y `CA-MV-165` existe para impedirlo.
   *
   * <p>Los demás son {@code JOIN} porque sus claves foráneas son {@code NOT NULL} y {@code
   * RESTRICT}: el producto, el sujeto, la moneda y el tipo existen siempre.
   *
   * <p><b>El tipo se acota aquí y no en el filtro</b> (`spec.md` §4.2): esta consulta es de las
   * VENTAS. El día que un depósito tenga líneas, no entran por descuido.
   */
  private static final String TABLAS_LINEAS =
      """
      FROM movement_details d
      JOIN movements m ON m.id = d.movement_id
      JOIN movement_types mt ON mt.id = m.movement_type_id
      JOIN users suj ON suj.id = m.user_id
      JOIN products p ON p.id = d.product_id
      JOIN currencies cur ON cur.id = m.currency_id
      LEFT JOIN users ven ON ven.id = d.seller_id
      WHERE mt.code = 'VENTA' AND
      """;

  /**
   * Las columnas de la fila de línea.
   *
   * <p><b>{@code d.product_name} y no {@code p.name}</b>: el nombre es el que se copió el día de la
   * venta (`RN-MV-002`), y leerlo del catálogo haría que esta consulta cambiara de respuesta cuando
   * alguien renombra un producto. El <b>código</b> sí sale de {@code products}, que `RN-PM-013`
   * declara inmutable — se copia lo que puede cambiar, y nada más.
   */
  private static final String COLUMNAS_LINEAS =
      """
      SELECT d.id AS linea, m.id AS mov_id, m.code AS mov_code, m.status AS mov_status,
             m.occurred_at AS occurred_at,
             suj.id AS suj_id, suj.username AS suj_username,
             suj.first_name AS suj_first, suj.last_name AS suj_last,
             ven.id AS ven_id, ven.username AS ven_username,
             ven.first_name AS ven_first, ven.last_name AS ven_last,
             p.id AS pro_id, p.code AS pro_code, d.product_name AS pro_name,
             d.quantity AS cantidad, d.unit_price AS precio,
             d.line_discount AS rebaja, d.line_amount AS importe,
             d.validity_days AS vigencia, cur.code AS moneda,
             d.implementation AS implementacion, d.delivery_status AS entrega,
             d.delivered_at AS entregada_en, d.delivery_note AS nota
      """;

  /** El predicado del listado de líneas, <b>escrito una vez</b> para la página y el conteo. */
  private static Filtro filtroLineas(SaleLinesFilter f) {
    Filtro filtro = new Filtro();
    filtro.igual("d.movement_id", "movimiento", f.movementId());
    filtro.igual("m.user_id", "sujeto", f.userId());
    // Por la línea y no por el movimiento: `RN-MV-003` dice que el vendedor es
    // de la línea, y una venta con dos vendedores aparece una vez por cada uno.
    filtro.igual("d.seller_id", "vendedor", f.sellerId());
    filtro.igual("d.product_id", "producto", f.productId());
    filtro.igual("m.status", "estado", f.status());
    filtro.igual("d.delivery_status", "entrega", f.deliveryStatus());
    filtro.igual("m.code", "codigo", f.code());
    if (f.from() != null) {
      filtro.condicion("m.occurred_at >= :desde", "desde", f.from());
    }
    // Semiabierto: dos periodos consecutivos no devuelven dos veces la línea de
    // la medianoche.
    if (f.to() != null) {
      filtro.condicion("m.occurred_at < :hasta", "hasta", f.to());
    }
    return filtro;
  }

  @Override
  @Transactional(readOnly = true)
  public List<SaleLineRow> findSaleLines(SaleLinesFilter filtro, int offset, int limit) {
    Filtro donde = filtroLineas(filtro);
    Query consulta =
        em.createNativeQuery(
            COLUMNAS_LINEAS
                + TABLAS_LINEAS
                + donde.sql()
                // EL DESEMPATE NO ES ADORNO: dos líneas de la misma venta
                // comparten `occurred_at` al microsegundo, y sin un orden total
                // la paginación repetiría o se saltaría filas entre páginas.
                + " ORDER BY m.occurred_at DESC, m.id DESC, d.id DESC"
                + " LIMIT :limite OFFSET :desplazamiento",
            Tuple.class);
    donde.enlazar(consulta);
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        consulta
            .setParameter("limite", limit)
            .setParameter("desplazamiento", offset)
            .getResultList();

    List<SaleLineRow> resultado = new ArrayList<>(filas.size());
    for (Tuple fila : filas) {
      resultado.add(filaDeLinea(fila));
    }
    return resultado;
  }

  @Override
  @Transactional(readOnly = true)
  public BoundedCount countSaleLines(SaleLinesFilter filtro, int techo) {
    Filtro donde = filtroLineas(filtro);
    Query consulta =
        em.createNativeQuery(
            "SELECT count(*) FROM (SELECT 1 " + TABLAS_LINEAS + donde.sql() + " LIMIT :techo) t");
    donde.enlazar(consulta);
    Number contado = (Number) consulta.setParameter("techo", techo + 1L).getSingleResult();
    return BoundedCount.de(contado.longValue(), techo);
  }

  private static SaleLineRow filaDeLinea(Tuple fila) {
    return new SaleLineRow(
        (UUID) fila.get("linea"),
        (UUID) fila.get("mov_id"),
        (String) fila.get("mov_code"),
        (String) fila.get("mov_status"),
        instante(fila.get("occurred_at")),
        (UUID) fila.get("suj_id"),
        (String) fila.get("suj_username"),
        (String) fila.get("suj_first"),
        (String) fila.get("suj_last"),
        (UUID) fila.get("ven_id"),
        (String) fila.get("ven_username"),
        (String) fila.get("ven_first"),
        (String) fila.get("ven_last"),
        (UUID) fila.get("pro_id"),
        (String) fila.get("pro_code"),
        (String) fila.get("pro_name"),
        ((Number) fila.get("cantidad")).intValue(),
        (BigDecimal) fila.get("precio"),
        (BigDecimal) fila.get("rebaja"),
        (BigDecimal) fila.get("importe"),
        fila.get("vigencia") == null ? null : ((Number) fila.get("vigencia")).intValue(),
        (String) fila.get("moneda"),
        (String) fila.get("implementacion"),
        (String) fila.get("entrega"),
        instante(fila.get("entregada_en")),
        (String) fila.get("nota"));
  }

  private static final class Filtro {

    private final StringBuilder donde = new StringBuilder("1 = 1");
    private final Map<String, Object> parametros = new LinkedHashMap<>();

    void condicion(String sql, String nombre, Object valor) {
      donde.append(" AND ").append(sql);
      parametros.put(nombre, valor);
    }

    /** Igualdad simple. Un valor nulo significa «sin filtro» y no añade nada. */
    void igual(String columna, String nombre, Object valor) {
      if (valor != null) {
        condicion(columna + " = :" + nombre, nombre, valor);
      }
    }

    String sql() {
      return donde.toString();
    }

    void enlazar(Query consulta) {
      parametros.forEach(consulta::setParameter);
    }
  }

  /** Las rebajas de todas las líneas del movimiento, por línea. Una consulta y no una por línea. */
  private Map<UUID, List<LineDiscountRow>> rebajasDe(UUID movementId) {
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT r.movement_detail_id AS linea_id, r.type AS tipo, r.value AS valor,
                       r.discount_value AS dinero
                  FROM movement_detail_discounts r
                  JOIN movement_details d ON d.id = r.movement_detail_id
                 WHERE d.movement_id = :movimiento
                 ORDER BY r.created_at ASC, r.id ASC
                """,
                Tuple.class)
            .setParameter("movimiento", movementId)
            .getResultList();
    Map<UUID, List<LineDiscountRow>> porLinea = new LinkedHashMap<>();
    for (Tuple fila : filas) {
      porLinea
          .computeIfAbsent((UUID) fila.get("linea_id"), id -> new ArrayList<>())
          .add(
              new LineDiscountRow(
                  (String) fila.get("tipo"),
                  (BigDecimal) fila.get("valor"),
                  (BigDecimal) fila.get("dinero")));
    }
    return porLinea;
  }

  private static MyMovementRow cabecera(Tuple fila) {
    return new MyMovementRow(
        (UUID) fila.get("id"),
        (String) fila.get("code"),
        (String) fila.get("tipo"),
        (String) fila.get("status"),
        (String) fila.get("type_status"),
        (UUID) fila.get("suj_id"),
        (String) fila.get("suj_username"),
        (String) fila.get("suj_first"),
        (String) fila.get("suj_last"),
        (UUID) fila.get("paquete"),
        (UUID) fila.get("cur_id"),
        (String) fila.get("cur_code"),
        (String) fila.get("pm_name"),
        (BigDecimal) fila.get("total"),
        (BigDecimal) fila.get("descuento"),
        (BigDecimal) fila.get("pagar"),
        instante(fila.get("occurred_at")),
        instante(fila.get("confirmed_at")),
        instante(fila.get("voided_at")),
        (String) fila.get("void_reason"),
        instante(fila.get("created_at")));
  }

  /**
   * El instante, venga como venga del controlador JDBC.
   *
   * <p>Una proyección nativa no garantiza el tipo: el mismo {@code timestamptz} llega como {@code
   * Timestamp} o como {@code OffsetDateTime} según el camino. Un {@code cast} directo funciona
   * hasta que deja de hacerlo, y entonces falla en tiempo de ejecución y en una sola consulta. Es
   * el mismo conversor que {@code JpaUserRepository} tiene por el mismo motivo.
   */
  private static OffsetDateTime instante(Object valor) {
    return switch (valor) {
      case null -> null;
      case OffsetDateTime momento -> momento;
      case Instant momento -> momento.atOffset(ZoneOffset.UTC);
      case Timestamp marca -> marca.toInstant().atOffset(ZoneOffset.UTC);
      default ->
          throw new IllegalStateException(
              "Tipo temporal inesperado en la proyección: " + valor.getClass());
    };
  }
}
