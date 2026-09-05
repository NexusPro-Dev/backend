package com.factech.nexus.modules.movements.domain.repository;

import com.factech.nexus.modules.movements.domain.models.Movement;
import com.factech.nexus.modules.movements.domain.models.MovementLine;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
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

  // ---------------------------------------------------------------------------
  // `RF-MV-008` — los movimientos propios
  // ---------------------------------------------------------------------------

  /**
   * La selección, escrita una vez: la página y el conteo tienen que filtrar igual.
   *
   * <p><b>{@code OR} y no {@code UNION}</b>: un {@code UNION} duplicaría el movimiento en que
   * alguien es comprador y vendedor a la vez, y `FA-002` exige que aparezca una sola vez. Evitarlo
   * con un {@code UNION} sin {@code ALL} costaría un ordenamiento completo antes de paginar.
   *
   * <p>El {@code CAST} del estado no es adorno: sin él, PostgreSQL no sabe de qué tipo es el
   * parámetro cuando llega nulo y rechaza la comparación.
   */
  private static final String SELECCION_PROPIA =
      """
      FROM movements m
      JOIN users cli ON cli.id = m.client_id
      LEFT JOIN users ven ON ven.id = m.seller_id
      JOIN currencies cur ON cur.id = m.currency_id
      JOIN payment_methods pm ON pm.id = m.payment_method_id
      WHERE (m.client_id = :actor OR m.seller_id = :actor)
        AND (CAST(:estado AS varchar) IS NULL OR m.status = CAST(:estado AS varchar))
      """;

  /**
   * Las columnas de la cabecera, con el papel resuelto por el motor.
   *
   * <p><b>La rama de «ambos» va PRIMERO, y ahí está el defecto que se comete.</b> Escrita al final,
   * las dos anteriores ya habrían capturado la fila y nadie lo vería hasta que alguien de la fuerza
   * comercial se comprara algo a sí mismo — que es exactamente lo que `RF-MV-002` permite.
   *
   * <p><b>Lo calcula SQL y no Java</b>: el identificador de quien pregunta ya está atado a la
   * consulta, y resolverlo fuera obligaría a arrastrar los dos identificadores de las partes solo
   * para compararlos y descartarlos.
   */
  private static final String CABECERA_PROPIA =
      """
      SELECT m.id AS id, m.code AS code, m.status AS status,
             CASE
               WHEN m.client_id = :actor AND m.seller_id = :actor THEN 'BOTH'
               WHEN m.seller_id = :actor                          THEN 'SELLER'
               ELSE                                                    'BUYER'
             END AS role,
             cli.id AS cli_id, cli.username AS cli_username,
             cli.first_name AS cli_first, cli.last_name AS cli_last,
             ven.id AS ven_id, ven.username AS ven_username,
             ven.first_name AS ven_first, ven.last_name AS ven_last,
             cur.id AS cur_id, cur.code AS cur_code, pm.name AS pm_name,
             m.total_amount AS total, m.discount_amount AS descuento,
             m.payable_amount AS pagar,
             m.occurred_at AS occurred_at, m.created_at AS created_at
      """;

  @Override
  @Transactional(readOnly = true)
  public List<MyMovementRow> findMine(UUID actorId, String status, int offset, int limit) {
    List<Tuple> filas =
        em.createNativeQuery(
                CABECERA_PROPIA
                    + SELECCION_PROPIA
                    // EL DESEMPATE POR `id` NO ES COSMÉTICO: sin él, dos
                    // movimientos del mismo instante pueden repetirse en una
                    // página y faltar en la siguiente sin que nada falle.
                    + " ORDER BY m.occurred_at DESC, m.id DESC LIMIT :limite OFFSET :desde",
                Tuple.class)
            .setParameter("actor", actorId)
            .setParameter("estado", status)
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
  public long countMine(UUID actorId, String status) {
    Object total =
        em.createNativeQuery("SELECT count(*) " + SELECCION_PROPIA)
            .setParameter("actor", actorId)
            .setParameter("estado", status)
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
  public Optional<MovementDetailView> findMineById(UUID movementId, UUID actorId) {
    if (movementId == null || actorId == null) {
      return Optional.empty();
    }

    List<Tuple> cabecera =
        em.createNativeQuery(
                CABECERA_PROPIA
                    + """
                    FROM movements m
                    JOIN users cli ON cli.id = m.client_id
                    LEFT JOIN users ven ON ven.id = m.seller_id
                    JOIN currencies cur ON cur.id = m.currency_id
                    JOIN payment_methods pm ON pm.id = m.payment_method_id
                    WHERE m.id = :movimiento
                      AND (m.client_id = :actor OR m.seller_id = :actor)
                    """,
                Tuple.class)
            .setParameter("movimiento", movementId)
            .setParameter("actor", actorId)
            .getResultList();

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
                SELECT d.product_id AS product_id, p.code AS p_code, p.name AS p_name,
                       d.quantity AS cantidad, d.unit_price AS precio,
                       d.line_amount AS importe, d.validity_days AS vigencia
                  FROM movement_details d
                  JOIN products p ON p.id = d.product_id
                 WHERE d.movement_id = :movimiento
                 ORDER BY p.code ASC
                """,
                Tuple.class)
            .setParameter("movimiento", movementId)
            .getResultList();

    List<MovementLineRow> detalle = new ArrayList<>(lineas.size());
    for (Tuple linea : lineas) {
      detalle.add(
          new MovementLineRow(
              (UUID) linea.get("product_id"),
              (String) linea.get("p_code"),
              (String) linea.get("p_name"),
              ((Number) linea.get("cantidad")).intValue(),
              (BigDecimal) linea.get("precio"),
              (BigDecimal) linea.get("importe"),
              linea.get("vigencia") == null ? null : ((Number) linea.get("vigencia")).intValue()));
    }

    return Optional.of(new MovementDetailView(cabecera(cabecera.get(0)), detalle));
  }

  /** El mapeo de la cabecera, escrito una vez: el listado y el detalle piden lo mismo. */
  private static MyMovementRow cabecera(Tuple fila) {
    return new MyMovementRow(
        (UUID) fila.get("id"),
        (String) fila.get("code"),
        (String) fila.get("status"),
        (String) fila.get("role"),
        (UUID) fila.get("cli_id"),
        (String) fila.get("cli_username"),
        (String) fila.get("cli_first"),
        (String) fila.get("cli_last"),
        (UUID) fila.get("ven_id"),
        (String) fila.get("ven_username"),
        (String) fila.get("ven_first"),
        (String) fila.get("ven_last"),
        (UUID) fila.get("cur_id"),
        (String) fila.get("cur_code"),
        (String) fila.get("pm_name"),
        (BigDecimal) fila.get("total"),
        (BigDecimal) fila.get("descuento"),
        (BigDecimal) fila.get("pagar"),
        instante(fila.get("occurred_at")),
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
