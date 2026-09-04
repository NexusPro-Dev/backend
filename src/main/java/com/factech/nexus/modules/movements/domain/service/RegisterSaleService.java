package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.RegisterSaleRequest;
import com.factech.nexus.modules.movements.application.SaleResponse;
import com.factech.nexus.modules.movements.domain.models.Movement;
import com.factech.nexus.modules.movements.domain.models.MovementCode;
import com.factech.nexus.modules.movements.domain.models.MovementLine;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.MovementTypeView;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.PaymentMethodView;
import com.factech.nexus.modules.products.application.ProductCatalog;
import com.factech.nexus.modules.products.application.ProductCatalog.SaleView;
import com.factech.nexus.modules.products.application.ProductPrice;
import com.factech.nexus.modules.system.users.application.ClientCatalog;
import com.factech.nexus.modules.system.users.application.ClientCatalog.ClientView;
import com.factech.nexus.modules.system.users.application.ClientCatalog.SellerView;
import com.factech.nexus.modules.system.users.application.CurrentMembershipLookup;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import com.factech.nexus.shared.error.ValidationException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registrar una venta (`RF-MV-001`).
 *
 * <h2>El orden de verificación es el contrato</h2>
 *
 * <p>Es el de `spec.md` §8, y dos de sus pasos están donde están por un motivo escrito:
 *
 * <ol>
 *   <li>El cliente existe y <b>puede comprar</b> (`EX-001`, `EX-002`).
 *   <li><b>El vendedor</b>, antes que todo lo demás: si el cliente no cuelga de nadie la venta no
 *       se puede atribuir, y es mejor saberlo antes de resolver ofertas y precios (`EX-003`).
 *   <li>El método de pago, que es una lectura barata del propio módulo (`EX-010`).
 *   <li>La <b>composición</b> de las líneas: sin repetidos, como mucho un upgrade, cantidad uno en
 *       él (`VAL-006`, `EX-006`, `EX-009`).
 *   <li>La <b>oferta</b>, que va después de la composición a propósito: es lo más caro de la
 *       operación —hay que resolver qué puede comprar esa persona— y hacerlo antes de saber si la
 *       petición está bien formada gastaría ese trabajo para rechazarla por un producto repetido
 *       (`EX-004`).
 *   <li>El nivel, la moneda, la copia, los totales y el comprobante.
 * </ol>
 *
 * <h2>Lo que este caso de uso NO hace</h2>
 *
 * <p><b>No concede ningún nivel, no habilita ninguna cuenta y no comisiona</b> (`RN-MV-004`). La
 * venta nace pendiente y eso significa exactamente que alguien dijo que iba a pagar y que nadie ha
 * comprobado que pagara. `CA-MV-007` afirma que el sistema <b>no</b> hace algo, y es el criterio
 * que sostiene todo el módulo: sin él, la diferencia entre registrar y confirmar es una palabra en
 * un documento.
 *
 * <p><b>No bloquea nada</b>, y su ausencia es una afirmación. Dos peticiones simultáneas pueden
 * vender dos veces el mismo upgrade al mismo cliente y las dos se registran: no hay nada que
 * proteger, porque <b>ninguna de las dos concede nada todavía</b>. El conflicto aparece al
 * confirmar la segunda, y es `RF-MV-003` quien tiene que resolverlo — bloquear aquí daría la
 * impresión de que ya está resuelto.
 */
@Service
public class RegisterSaleService {

  private static final String MODULO = "MV";
  private static final String ENTIDAD = "movements";

  /** Hoy el único tipo del catálogo. Se busca por código y no se constantea su identificador. */
  private static final String TIPO_VENTA = "VENTA";

  /**
   * El estado que `RF-SP-045` estrena (`RN-SP-026`).
   *
   * <p><b>Hoy ningún camino del sistema lo produce</b>: {@code ck_users_status} todavía no lo
   * admite y `RF-SP-045` no tiene una línea de código. La comprobación se escribe igualmente,
   * porque la alternativa es que el día que ese requerimiento aterrice <b>se le empiece a vender a
   * cuentas que no pueden operar</b> sin que nada falle. Es la única rama de este servicio que hoy
   * no se puede alcanzar, y por eso `CA-MV-008` queda sin prueba (`tasks.md` §4).
   */
  private static final String FTD_PENDIENTE = "FTD_PENDIENTE";

  /**
   * Los decimales con los que el libro guarda los importes ({@code numeric(14,2)}).
   *
   * <p><b>Y aquí hay una tensión declarada, no resuelta por este requerimiento.</b> {@code
   * currencies.decimal_places} admite de cero a cuatro (`V14`) y {@code products.price} es {@code
   * numeric(14,4)} justamente por eso, mientras que `requirements/mv.md` §7 fija los importes del
   * movimiento en dos decimales. Con una moneda de tres o cuatro, el libro <b>redondearía en
   * silencio lo que alguien pagó</b>.
   *
   * <p>No se cambia el esquema aquí —lo fija un documento aprobado— y no se deja pasar: el importe
   * que no quepa se rechaza al registrar, que es el único momento en que alguien está mirando. Hoy
   * la única moneda sembrada es {@code USD} con dos decimales, de modo que esa rama no se alcanza.
   */
  private static final int DECIMALES_DEL_LIBRO = 2;

  private final MovementRepository movimientos;
  private final ProductCatalog productos;
  private final ClientCatalog clientes;
  private final CurrentMembershipLookup membresias;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public RegisterSaleService(
      MovementRepository movimientos,
      ProductCatalog productos,
      ClientCatalog clientes,
      CurrentMembershipLookup membresias,
      AuditWriter auditoria) {
    this(movimientos, productos, clientes, membresias, auditoria, Clock.systemUTC());
  }

  RegisterSaleService(
      MovementRepository movimientos,
      ProductCatalog productos,
      ClientCatalog clientes,
      CurrentMembershipLookup membresias,
      AuditWriter auditoria,
      Clock reloj) {
    this.movimientos = movimientos;
    this.productos = productos;
    this.clientes = clientes;
    this.membresias = membresias;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public SaleResponse register(RegisterSaleRequest peticion) {
    OffsetDateTime ahora = OffsetDateTime.now(reloj);
    OffsetDateTime ocurrioEn = fechaDelHecho(peticion.occurredAt(), ahora);

    ClientView cliente = verificarCliente(peticion.clientId());
    SellerView vendedor = verificarVendedor(cliente);
    PaymentMethodView metodo = verificarMetodoDePago(peticion.paymentMethodId());

    List<RegisterSaleRequest.Line> lineas = peticion.lines();
    verificarSinRepetidos(lineas);

    Map<UUID, SaleView> catalogo = resolverProductos(lineas);
    SaleView upgrade = verificarComposicion(lineas, catalogo);

    verificarOferta(cliente, lineas, catalogo);
    if (upgrade != null) {
      verificarQueSube(cliente, upgrade);
    }

    SaleView referencia = catalogo.get(lineas.get(0).productId());
    verificarMonedaUnica(lineas, catalogo, referencia);

    MovementTypeView tipo = tipoDeVenta();
    Movement venta =
        Movement.registrar(
            tipo.id(),
            cliente.id(),
            vendedor.id(),
            metodo.id(),
            referencia.currencyId(),
            MovementCode.generar(tipo.prefix(), ocurrioEn),
            copiar(lineas, catalogo, referencia.currencyDecimalPlaces()),
            referencia.currencyDecimalPlaces(),
            ocurrioEn,
            ahora);

    movimientos.save(venta, () -> MovementCode.generar(tipo.prefix(), ocurrioEn));

    auditoria.recordChange(
        new ChangeEvent(MODULO, ENTIDAD, venta.getId(), ChangeAction.CREATE, venta.instantanea()));

    return SaleResponse.de(
        venta,
        new SaleResponse.Party(cliente.id(), cliente.username(), nombre(cliente)),
        new SaleResponse.Party(vendedor.id(), vendedor.username(), nombre(vendedor)),
        new SaleResponse.Money(referencia.currencyId(), referencia.currencyCode()),
        metodo.code());
  }

  // ---------------------------------------------------------------------------
  // 1 y 2. Quién compra y a quién se le atribuye
  // ---------------------------------------------------------------------------

  /** `EX-001` y `EX-002`, que se distinguen a propósito (`CA-MV-009`). */
  private ClientView verificarCliente(UUID clientId) {
    ClientView cliente =
        clientes
            .findClient(clientId)
            .orElseThrow(
                () ->
                    new UnprocessableEntityException(
                        "EX-001",
                        "El cliente indicado no existe.",
                        List.of(
                            new FieldError(
                                "clientId", "EX-001", "El cliente indicado no existe."))));

    // `RN-MV-008`. El mensaje dice QUE LE FALTA, y no solo que no se puede:
    // quien intenta vender necesita saber que la salida es confirmar el
    // depósito, no reintentar. Ver el Javadoc de FTD_PENDIENTE.
    if (FTD_PENDIENTE.equals(cliente.status())) {
      String mensaje =
          "Esa cuenta todavía no puede operar: le falta la confirmación de su depósito.";
      throw new BusinessRuleException(
          "EX-002", mensaje, List.of(new FieldError("clientId", "EX-002", mensaje)));
    }
    return cliente;
  }

  /**
   * `EX-003`. `RN-SP-027` promete que esto no ocurre, y se comprueba igual: <b>una promesa de otro
   * módulo no es una comprobación de este</b>. El día que falle, la venta tiene que negarse a
   * existir en lugar de nacer sin dueño — una venta sin vendedor no se descubre hasta el día de
   * pagar una comisión, y entonces ya no hay a quién preguntarle.
   */
  private SellerView verificarVendedor(ClientView cliente) {
    return clientes
        .sellerOf(cliente.id())
        .orElseThrow(
            () -> {
              String mensaje =
                  "La venta no se puede atribuir: esa persona no cuelga de ningún vendedor.";
              return new BusinessRuleException(
                  "EX-003", mensaje, List.of(new FieldError("clientId", "EX-003", mensaje)));
            });
  }

  // ---------------------------------------------------------------------------
  // 3. Con qué se paga
  // ---------------------------------------------------------------------------

  /**
   * `EX-010`, con los dos casos separados: inexistente es {@code 422} y desactivado es {@code 409}.
   *
   * <p><b>Un método desactivado no invalida lo ya vendido con él</b> (`RN-MV-018`) — las ventas
   * viejas lo siguen referenciando y se leen con normalidad—, pero no sirve para vender hoy.
   */
  private PaymentMethodView verificarMetodoDePago(UUID id) {
    PaymentMethodView metodo =
        movimientos
            .findPaymentMethod(id)
            .orElseThrow(
                () ->
                    new UnprocessableEntityException(
                        "EX-010",
                        "El método de pago indicado no existe.",
                        List.of(
                            new FieldError(
                                "paymentMethodId",
                                "EX-010",
                                "El método de pago indicado no existe."))));

    if (!metodo.active()) {
      String mensaje = "El método de pago indicado está desactivado.";
      throw new BusinessRuleException(
          "EX-010", mensaje, List.of(new FieldError("paymentMethodId", "EX-010", mensaje)));
    }
    return metodo;
  }

  // ---------------------------------------------------------------------------
  // 4. La composición de las líneas
  // ---------------------------------------------------------------------------

  /**
   * `VAL-006`, y es de <b>entrada</b> aunque `RN-MV-011` sea una regla: la repetición se ve mirando
   * la petición, sin consultar nada. Rechazarla aquí ahorra resolver el catálogo y la oferta de una
   * venta que ya se sabe mal formada — que es lo que la verificación de `T-11` comprueba contando
   * consultas.
   */
  private void verificarSinRepetidos(List<RegisterSaleRequest.Line> lineas) {
    Set<UUID> vistos = new HashSet<>();
    for (RegisterSaleRequest.Line linea : lineas) {
      if (!vistos.add(linea.productId())) {
        String mensaje = "Un producto no puede aparecer dos veces en la misma venta.";
        throw new ValidationException(
            "VAL-006", mensaje, List.of(new FieldError("lines", "VAL-006", mensaje)));
      }
    }
  }

  /** `EX-011`: un identificador que no corresponde a nada, distinto de uno fuera de la oferta. */
  private Map<UUID, SaleView> resolverProductos(List<RegisterSaleRequest.Line> lineas) {
    Set<UUID> pedidos = new LinkedHashSet<>();
    for (RegisterSaleRequest.Line linea : lineas) {
      pedidos.add(linea.productId());
    }

    Map<UUID, SaleView> catalogo = new LinkedHashMap<>();
    for (SaleView producto : productos.saleViewOf(pedidos)) {
      catalogo.put(producto.id(), producto);
    }

    for (UUID pedido : pedidos) {
      if (!catalogo.containsKey(pedido)) {
        String mensaje = "El producto indicado no existe.";
        throw new UnprocessableEntityException(
            "EX-011", mensaje, List.of(new FieldError("lines", "EX-011", mensaje)));
      }
    }
    return catalogo;
  }

  /**
   * `RN-MV-010` y `RN-MV-015`.
   *
   * <p><b>Como mucho un upgrade por venta</b>: dos cambios de nivel en la misma operación no tienen
   * un orden que aplicar al confirmar, y elegirlo por el sistema sería inventar cuál se quiso.
   * Bots, los que se quiera.
   *
   * <p><b>Cantidad uno en el upgrade</b>: comprar dos veces el mismo cambio de nivel no lleva a
   * ninguna parte el doble.
   *
   * @return el upgrade de la venta, o {@code null} si no lleva ninguno (`FA-001`)
   */
  private SaleView verificarComposicion(
      List<RegisterSaleRequest.Line> lineas, Map<UUID, SaleView> catalogo) {

    SaleView upgrade = null;
    for (RegisterSaleRequest.Line linea : lineas) {
      SaleView producto = catalogo.get(linea.productId());
      if (!producto.upgrade()) {
        continue;
      }
      if (upgrade != null) {
        String mensaje = "Solo se admite un cambio de nivel por operación.";
        throw new BusinessRuleException(
            "EX-006", mensaje, List.of(new FieldError("lines", "EX-006", mensaje)));
      }
      if (linea.quantity() != 1) {
        String mensaje =
            "Un cambio de nivel no admite cantidad: «%s» va de uno en uno."
                .formatted(producto.code());
        throw new BusinessRuleException(
            "EX-009", mensaje, List.of(new FieldError("lines", "EX-009", mensaje)));
      }
      upgrade = producto;
    }
    return upgrade;
  }

  // ---------------------------------------------------------------------------
  // 5 y 6. Qué le corresponde a esa persona
  // ---------------------------------------------------------------------------

  /**
   * `RN-MV-007` y `EX-004`. <b>Una sola llamada resuelve todas las líneas</b>, y la decisión no se
   * toma aquí: la responde `PM`, que es quien la publica. Recalcularla en `MV` crearía dos
   * definiciones de «lo que alguien puede comprar», y el día que una cambiara la otra seguiría
   * vendiendo lo que la primera ya no ofrece.
   *
   * <p><b>El mensaje nombra el producto</b> (`CA-MV-010`): «no está en la oferta» sin decir cuál
   * obliga a probar de uno en uno cuando la venta lleva cinco líneas.
   */
  private void verificarOferta(
      ClientView cliente, List<RegisterSaleRequest.Line> lineas, Map<UUID, SaleView> catalogo) {

    Set<UUID> ofrecidos = productos.offeredTo(cliente.id(), catalogo.keySet());

    for (RegisterSaleRequest.Line linea : lineas) {
      if (!ofrecidos.contains(linea.productId())) {
        SaleView producto = catalogo.get(linea.productId());
        String mensaje =
            "El producto «%s» no está entre los que esa persona puede comprar."
                .formatted(producto.code());
        throw new BusinessRuleException(
            "EX-004", mensaje, List.of(new FieldError("lines", "EX-004", mensaje)));
      }
    }
  }

  /**
   * `RN-MV-006` y `EX-005`: <b>solo se sube de nivel</b>.
   *
   * <p><b>Se comprueba aunque la oferta ya lo garantice hoy</b>, y eso no es redundancia por exceso
   * de celo: la oferta es una decisión de `PM` y puede ampliarse —el día que se vendan renovaciones
   * del mismo nivel, por ejemplo—, mientras que «una venta no baja a nadie de nivel» es una regla
   * de `MV` que no puede depender de que otro módulo siga decidiendo lo mismo. Es lo que mantiene
   * `EX-005` alcanzable: hoy no se llega por la oferta, y se llegaría el día siguiente a que `PM`
   * la ampliara.
   *
   * <p><b>Se rechaza al registrar y no al confirmar</b>, que es lo único que evita cobrarle a
   * alguien por algo que no le da nada.
   *
   * <p>La cadena crece hacia abajo: {@code 1} es la cima, de modo que <b>nivel superior es número
   * menor</b> (`requirements/sp.md` §10.4).
   */
  private void verificarQueSube(ClientView cliente, SaleView upgrade) {
    Optional<Integer> nivelActual =
        membresias.currentMembershipOf(cliente.id()).map(m -> (Integer) m.level());

    // Sin membresía no hay nada por debajo de lo que subir, y no es un rechazo:
    // cualquier destino está por encima de no tener nivel. Que la oferta se lo
    // haya ofrecido ya es la decisión de `PM`.
    if (nivelActual.isEmpty()) {
      return;
    }
    Integer destino = upgrade.targetMembershipLevel();
    if (destino == null || destino >= nivelActual.get()) {
      String mensaje =
          "El producto «%s» no lleva a una membresía superior a la que esa persona ya tiene."
              .formatted(upgrade.code());
      throw new BusinessRuleException(
          "EX-005", mensaje, List.of(new FieldError("lines", "EX-005", mensaje)));
    }
  }

  // ---------------------------------------------------------------------------
  // 7 y 8. La moneda, la copia y los importes
  // ---------------------------------------------------------------------------

  /**
   * `RN-MV-012` y `EX-008`.
   *
   * <p><b>No hay conversión posible</b>: el sistema no tiene ninguna tasa de cambio, y no la va a
   * improvisar una venta. Si dos productos vienen en monedas distintas, no hay ninguna venta que
   * los pueda contener.
   */
  private void verificarMonedaUnica(
      List<RegisterSaleRequest.Line> lineas, Map<UUID, SaleView> catalogo, SaleView referencia) {

    for (RegisterSaleRequest.Line linea : lineas) {
      SaleView producto = catalogo.get(linea.productId());
      if (!producto.currencyId().equals(referencia.currencyId())) {
        String mensaje =
            "Una venta se cobra en una sola moneda, y «%s» está en %s frente a %s."
                .formatted(producto.code(), producto.currencyCode(), referencia.currencyCode());
        throw new BusinessRuleException(
            "EX-008", mensaje, List.of(new FieldError("lines", "EX-008", mensaje)));
      }
    }
  }

  /**
   * `RN-MV-002`: <b>se copia lo que puede cambiar</b>.
   *
   * <p>El precio unitario y la vigencia se toman del catálogo <b>ahora</b> y no se vuelven a leer.
   * Corregir mañana el precio del producto no cambia esta venta, y una venta del mismo producto
   * registrada después llevará el precio nuevo: las dos convivirán con importes distintos, y eso es
   * lo correcto (`FA-003`).
   *
   * <p>`RN-MV-014` se comprueba aquí, sobre <b>cada</b> precio copiado y no solo sobre el total: un
   * precio con más decimales de los que la moneda admite entraría redondeado en el importe de línea
   * y la suma cuadraría, de modo que mirar solo el total no lo detectaría.
   */
  private List<MovementLine> copiar(
      List<RegisterSaleRequest.Line> lineas,
      Map<UUID, SaleView> catalogo,
      int decimalesDeLaMoneda) {

    int decimales = Math.min(decimalesDeLaMoneda, DECIMALES_DEL_LIBRO);

    List<MovementLine> copiadas = new ArrayList<>(lineas.size());
    for (RegisterSaleRequest.Line linea : lineas) {
      SaleView producto = catalogo.get(linea.productId());
      BigDecimal precio = producto.price();

      if (!ProductPrice.cabeEn(precio, decimales)) {
        String mensaje =
            "El precio de «%s» no cabe en %d decimales y la venta no puede redondearlo."
                .formatted(producto.code(), decimales);
        throw new BusinessRuleException(
            "EX-008", mensaje, List.of(new FieldError("lines", "EX-008", mensaje)));
      }

      copiadas.add(
          MovementLine.copiarDe(
              producto.id(),
              producto.code(),
              producto.name(),
              linea.quantity(),
              ProductPrice.enLaEscalaDe(precio, decimales),
              producto.validityDays()));
    }
    return copiadas;
  }

  // ---------------------------------------------------------------------------
  // Auxiliares
  // ---------------------------------------------------------------------------

  /**
   * `VAL-007`: la fecha del hecho no puede estar en el futuro, porque una venta que aún no ha
   * ocurrido no es un hecho. <b>El pasado remoto sí se admite</b>, que es exactamente lo que hace
   * falta para registrar lo que ya ocurrió.
   *
   * <p>No vive en el DTO con una anotación porque {@code @PastOrPresent} compara contra el reloj
   * del sistema, y este servicio recibe el suyo inyectado: con la anotación, una prueba que fije el
   * reloj comprobaría una cosa y la validación de entrada otra.
   */
  private OffsetDateTime fechaDelHecho(OffsetDateTime enviada, OffsetDateTime ahora) {
    if (enviada == null) {
      return ahora;
    }
    if (enviada.isAfter(ahora)) {
      String mensaje = "La fecha de la venta no puede estar en el futuro.";
      throw new ValidationException(
          "VAL-007", mensaje, List.of(new FieldError("occurredAt", "VAL-007", mensaje)));
    }
    return enviada;
  }

  private MovementTypeView tipoDeVenta() {
    return movimientos
        .findTypeByCode(TIPO_VENTA)
        .orElseThrow(
            // No es un error del cliente: la siembra de `V54` lo garantiza, y
            // su ausencia significa que el catálogo del módulo está roto.
            () ->
                new IllegalStateException(
                    "El tipo de movimiento «%s» no está en el catálogo.".formatted(TIPO_VENTA)));
  }

  private static String nombre(ClientView cliente) {
    return nombreCompleto(cliente.firstName(), cliente.lastName());
  }

  private static String nombre(SellerView vendedor) {
    return nombreCompleto(vendedor.firstName(), vendedor.lastName());
  }

  private static String nombreCompleto(String nombre, String apellido) {
    String completo =
        ((nombre == null ? "" : nombre) + " " + (apellido == null ? "" : apellido)).trim();
    return completo.isEmpty() ? null : completo;
  }
}
