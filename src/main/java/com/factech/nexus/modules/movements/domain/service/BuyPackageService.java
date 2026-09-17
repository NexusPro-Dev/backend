package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.BuyPackageRequest;
import com.factech.nexus.modules.movements.application.PurchaseResponse;
import com.factech.nexus.modules.movements.application.SaleResponse;
import com.factech.nexus.modules.movements.domain.models.LineDiscount;
import com.factech.nexus.modules.movements.domain.models.Movement;
import com.factech.nexus.modules.movements.domain.models.MovementCode;
import com.factech.nexus.modules.movements.domain.models.MovementDiscountType;
import com.factech.nexus.modules.movements.domain.models.MovementLine;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.MovementTypeView;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.PaymentMethodView;
import com.factech.nexus.modules.products.application.PackageCatalog;
import com.factech.nexus.modules.products.application.PackageCatalog.PackageSaleLine;
import com.factech.nexus.modules.products.application.PackageCatalog.PackageSaleView;
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
import com.factech.nexus.shared.error.UnauthorizedException;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import com.factech.nexus.shared.security.CurrentActor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comprar un paquete para uno mismo (`RF-MV-012`).
 *
 * <h2>Un caso de uso propio, y no un modo de {@link RegisterSaleService}</h2>
 *
 * <p>`RF-MV-002` argumentó que registrar y comprar comparten servicio porque hacen <b>las mismas
 * verificaciones sobre los mismos datos</b>. Aquí no se cumple la premisa (`plan.md` §3.3): lo que
 * se resuelve es <b>un paquete</b> y no una lista de líneas, las comprobaciones de composición no
 * aplican —`RN-PM-046` garantiza un upgrade como máximo, `RN-PM-038` que ningún producto se repite,
 * y la cantidad es uno por construcción—, y aparece una que no existía: <b>que el paquete se pueda
 * ofrecer</b>.
 *
 * <p>Lo que <b>sí</b> se comparte es todo lo de después —copiar, congelar, sumar, emitir el código,
 * guardar y auditar—, que vive en {@link Movement}, {@link MovementLine} y el repositorio, y las
 * reglas de vender que no dependen de qué se vende, que viven en {@link SaleRules}. Es lo que hace
 * cierto `CA-MV-059`: la venta resultante es indistinguible de cualquier otra.
 *
 * <h2>El orden es el de `spec.md` §8, y dos pasos están donde están por un motivo</h2>
 *
 * <ol>
 *   <li><b>Quién es</b>, por su credencial, y que puede comprar (`EX-006`).
 *   <li><b>El paquete</b>, y que hoy se le puede ofrecer a esa persona: `PM` lo decide y `MV`
 *       <b>pregunta</b> (`EX-001` a `EX-003`). Va <b>antes</b> de mirar los productos a propósito:
 *       resolver los productos de un paquete que no se puede ofrecer es trabajo tirado, y además
 *       daría mensajes de producto a un problema que es del paquete.
 *   <li><b>Cada producto</b>, en la oferta de quien compra (`EX-004`), y el upgrade que sube
 *       (`EX-005`). Se hace <b>aunque el paso anterior lo dé por bueno</b>: `RN-PM-039` mira el
 *       paquete como un todo y `RN-MV-007` mira a quién se le vende cada producto — son dos
 *       preguntas distintas.
 *   <li>La moneda, la copia con la rebaja congelada, los importes, el método de pago y el alta.
 * </ol>
 *
 * <h2>Se compra ENTERO: si algo no procede, no se registra nada</h2>
 *
 * <p>Todo rechazo ocurre <b>antes de escribir</b>, de modo que un producto caído no deja ni la
 * cabecera (`CA-MV-054`). No se vende lo que queda ni se recalcula un paquete que nadie configuró
 * (`RN-MV-028`, `spec.md` §4.1).
 *
 * <h2>Lo que este caso de uso NO hace</h2>
 *
 * <p>No concede ningún nivel, no habilita ninguna cuenta y no comisiona (`RN-MV-004`); y <b>no
 * bloquea el paquete</b> (`plan.md` §7): que alguien corrija un descuento mientras esta venta se
 * registra produce una venta con el descuento de antes o con el de después, y las dos son correctas
 * — lo que se cobró queda congelado en cualquier caso.
 */
@Service
public class BuyPackageService {

  private static final String MODULO = "MV";
  private static final String ENTIDAD = "movements";

  /** Ver el Javadoc homónimo de {@link RegisterSaleService}: la misma tensión declarada. */
  private static final int DECIMALES_DEL_LIBRO = 2;

  private final MovementRepository movimientos;
  private final PackageCatalog paquetes;
  private final ProductCatalog productos;
  private final ClientCatalog clientes;
  private final CurrentActor actor;
  private final AuditWriter auditoria;
  private final Clock reloj;
  private final SaleRules reglas;

  @Autowired
  public BuyPackageService(
      MovementRepository movimientos,
      PackageCatalog paquetes,
      ProductCatalog productos,
      ClientCatalog clientes,
      CurrentMembershipLookup membresias,
      CurrentActor actor,
      AuditWriter auditoria) {
    this(
        movimientos,
        paquetes,
        productos,
        clientes,
        membresias,
        actor,
        auditoria,
        Clock.systemUTC());
  }

  BuyPackageService(
      MovementRepository movimientos,
      PackageCatalog paquetes,
      ProductCatalog productos,
      ClientCatalog clientes,
      CurrentMembershipLookup membresias,
      CurrentActor actor,
      AuditWriter auditoria,
      Clock reloj) {
    this.movimientos = movimientos;
    this.paquetes = paquetes;
    this.productos = productos;
    this.clientes = clientes;
    this.actor = actor;
    this.auditoria = auditoria;
    this.reloj = reloj;
    this.reglas = new SaleRules(movimientos, membresias);
  }

  /**
   * La compra propia: <b>el cliente es quien pide</b>, la fecha es ahora, y el vendedor es el de
   * `RN-MV-003` — su superior vigente, o él mismo si no cuelga de nadie.
   *
   * <p>Lo que de verdad cambia entre las puertas —quién compra y quién vende— se resuelve <b>antes
   * de entrar</b>, y el registro recibe todo decidido (`RF-MV-013` · `plan.md` §3.1). Por eso
   * {@link #registrar} no sabe por dónde entró la petición, y la compra por hotlink podrá pasar su
   * propio vendedor sin una bandera.
   */
  @Transactional
  public PurchaseResponse buy(String codigoDelPaquete, BuyPackageRequest peticion) {
    UUID quien =
        actor
            .currentActorId()
            .orElseThrow(() -> new UnauthorizedException("AUTH-001", "Se requiere autenticación."));
    ClientView cliente = verificarCliente(quien);
    SellerView vendedor = resolverVendedor(cliente);
    return registrar(
        cliente, vendedor, codigoDelPaquete, peticion == null ? null : peticion.paymentMethodId());
  }

  PurchaseResponse registrar(
      ClientView cliente, SellerView vendedor, String codigoDelPaquete, UUID metodoDePago) {
    OffsetDateTime ahora = OffsetDateTime.now(reloj);

    PackageSaleView paquete = resolverPaquete(codigoDelPaquete, cliente.id());
    verificarQueSeOfrece(paquete);

    List<PackageSaleLine> items = paquete.items();
    verificarOferta(cliente, items);
    SaleView upgrade = upgradeDe(items);
    if (upgrade != null) {
      reglas.verificarQueSube(cliente.id(), upgrade, "EX-005");
    }
    verificarMonedaUnica(paquete, items);

    List<MovementLine> lineas = copiar(paquete, items, vendedor.id());
    PaymentMethodView metodo = reglas.resolverMetodoDePago(metodoDePago, aPagar(lineas));

    MovementTypeView tipo = reglas.tipoDeVenta();
    Movement venta =
        Movement.registrar(
            tipo.id(),
            cliente.id(),
            paquete.id(),
            metodo.id(),
            paquete.currencyId(),
            MovementCode.generar(tipo.prefix(), ahora),
            lineas,
            paquete.currencyDecimalPlaces(),
            ahora,
            ahora);

    movimientos.save(venta, () -> MovementCode.generar(tipo.prefix(), ahora));

    auditoria.recordChange(
        new ChangeEvent(MODULO, ENTIDAD, venta.getId(), ChangeAction.CREATE, venta.instantanea()));

    return PurchaseResponse.de(
        venta,
        new SaleResponse.Party(cliente.id(), cliente.username(), nombre(cliente)),
        new SaleResponse.Money(paquete.currencyId(), paquete.currencyCode()),
        metodo.code());
  }

  // ---------------------------------------------------------------------------
  // 1. Quién compra
  // ---------------------------------------------------------------------------

  /**
   * El actor existe por definición (`RF-MV-002`), de modo que aquí no hay `EX-001` de cliente: una
   * credencial que no resuelve a nadie es una credencial que no vale, y se responde como tal.
   */
  private ClientView verificarCliente(UUID quien) {
    ClientView cliente =
        clientes
            .findClient(quien)
            .orElseThrow(() -> new UnauthorizedException("AUTH-001", "Se requiere autenticación."));
    // `RN-MV-008`, `EX-006`. El cliente no vino en el cuerpo: lo que falla es su estado.
    reglas.verificarQueOpera(cliente, "EX-006", "status");
    return cliente;
  }

  /** `RN-MV-003`: el superior vigente, o el propio comprador. Ver {@link RegisterSaleService}. */
  private SellerView resolverVendedor(ClientView cliente) {
    return clientes
        .sellerOf(cliente.id())
        .orElseGet(
            () ->
                new SellerView(
                    cliente.id(), cliente.username(), cliente.firstName(), cliente.lastName()));
  }

  // ---------------------------------------------------------------------------
  // 2. El paquete
  // ---------------------------------------------------------------------------

  /**
   * `EX-001`: inexistente o retirado, y los dos son lo mismo para quien compra.
   *
   * <p><b>Por código</b>: es lo que el cliente tiene delante —la oferta lo publica— y lo que un
   * enlace puede llevar escrito. El identificador es un dato de administración.
   */
  private PackageSaleView resolverPaquete(String codigo, UUID compradorId) {
    return paquetes
        .storeSaleViewOf(codigo, compradorId)
        .orElseThrow(
            () ->
                new UnprocessableEntityException(
                    "EX-001",
                    "El paquete indicado no existe.",
                    List.of(
                        new FieldError("packageId", "EX-001", "El paquete indicado no existe."))));
  }

  /**
   * `EX-002` y `EX-003`, distintos a propósito (`CA-MV-055`, `CA-MV-056`): uno dice «este paquete
   * no se ofrece» y el otro «no se te ofrece a ti».
   *
   * <p><b>El motivo de `EX-002` lo pone `PM`</b>, y es el mismo que el catálogo publica en {@code
   * offerableReason}: vencido, inactivo, sin productos, fuera de la tienda… No se reescribe aquí,
   * porque el día que `PM` añada un motivo la venta lo diría sin cambiar.
   */
  private static void verificarQueSeOfrece(PackageSaleView paquete) {
    if (!paquete.offerable()) {
      throw new BusinessRuleException(
          "EX-002",
          paquete.offerableReason(),
          List.of(new FieldError("packageId", "EX-002", paquete.offerableReason())));
    }
    if (!paquete.offeredTo()) {
      String mensaje =
          "El paquete «%s» no está entre los que esa persona puede comprar."
              .formatted(paquete.code());
      throw new BusinessRuleException(
          "EX-003", mensaje, List.of(new FieldError("packageId", "EX-003", mensaje)));
    }
  }

  // ---------------------------------------------------------------------------
  // 3. Cada producto
  // ---------------------------------------------------------------------------

  /**
   * `RN-MV-007` y `EX-004`, <b>producto a producto y nombrándolo</b> (`CA-MV-054`). Una sola
   * llamada resuelve todas las líneas, y la decisión la responde `PM` — la misma consulta que la
   * oferta.
   *
   * <p>Se hace aunque `PM` ya haya dado el paquete por ofrecible: aquella pregunta es sobre el
   * paquete y esta sobre <b>a quién se le vende cada producto</b>. Un bot que solo se publica por
   * hotlink dentro de un paquete de tienda cae aquí, y es correcto que caiga.
   */
  private void verificarOferta(ClientView cliente, List<PackageSaleLine> items) {
    List<UUID> ids = items.stream().map(item -> item.product().id()).toList();
    Set<UUID> ofrecidos = productos.offeredTo(cliente.id(), ids);
    for (PackageSaleLine item : items) {
      if (!ofrecidos.contains(item.product().id())) {
        String mensaje =
            "El producto «%s» no está entre los que esa persona puede comprar."
                .formatted(item.product().code());
        throw new BusinessRuleException(
            "EX-004", mensaje, List.of(new FieldError("packageId", "EX-004", mensaje)));
      }
    }
  }

  /**
   * El upgrade del paquete, o nulo si son todos bots (`FA-001`).
   *
   * <p><b>No se comprueba que sea uno</b>: `RN-PM-046` lo garantiza al asociar, y `spec.md` §5
   * declara la ausencia de `RN-MV-010` como una afirmación. Si hubiera dos, se comprobaría el nivel
   * sobre el primero y la confirmación tendría que resolver el resto — pero no hay petición capaz
   * de producirlos.
   */
  private static SaleView upgradeDe(List<PackageSaleLine> items) {
    for (PackageSaleLine item : items) {
      if (item.product().upgrade()) {
        return item.product();
      }
    }
    return null;
  }

  // ---------------------------------------------------------------------------
  // 4. La moneda, la copia y los importes
  // ---------------------------------------------------------------------------

  /**
   * `RN-MV-012`: todas las líneas comparten la moneda — la del paquete.
   *
   * <p>`RF-PM-023` ya impide asociar un producto en otra moneda, de modo que hoy esta rama no se
   * alcanza. Se comprueba igual: es una regla de `MV` y no puede depender de que `PM` siga
   * decidiendo lo mismo.
   */
  private static void verificarMonedaUnica(PackageSaleView paquete, List<PackageSaleLine> items) {
    for (PackageSaleLine item : items) {
      SaleView producto = item.product();
      if (!producto.currencyId().equals(paquete.currencyId())) {
        String mensaje =
            "Una venta se cobra en una sola moneda, y «%s» está en %s frente a %s."
                .formatted(producto.code(), producto.currencyCode(), paquete.currencyCode());
        throw new BusinessRuleException(
            "RN-MV-012", mensaje, List.of(new FieldError("packageId", "RN-MV-012", mensaje)));
      }
    }
  }

  /**
   * `RN-MV-002` y `RN-MV-027`: <b>se copia lo que puede cambiar y se congela la rebaja</b>.
   *
   * <p>Una línea por producto, cantidad uno (`RN-MV-028`), con el nombre, la descripción, el precio
   * y la vigencia de <b>hoy</b>, el vendedor del comprador, y <b>la rebaja que el paquete
   * declara</b> llevada a dinero por {@link LineDiscount} con la fórmula de `RN-PM-036`: se
   * redondea la rebaja y no el resultado. Es la misma cuenta escrita en dos sitios, aceptada a
   * conciencia (`plan.md` §3.2); lo que impide que diverjan es `CA-MV-050`.
   *
   * <p>`RN-MV-014` se comprueba sobre <b>cada</b> precio copiado, como en `RF-MV-001`.
   */
  private static List<MovementLine> copiar(
      PackageSaleView paquete, List<PackageSaleLine> items, UUID vendedorId) {
    int decimales = Math.min(paquete.currencyDecimalPlaces(), DECIMALES_DEL_LIBRO);

    List<MovementLine> lineas = new ArrayList<>(items.size());
    for (PackageSaleLine item : items) {
      SaleView producto = item.product();
      BigDecimal precio = producto.price();

      if (!ProductPrice.cabeEn(precio, decimales)) {
        String mensaje =
            "El precio de «%s» no cabe en %d decimales y la venta no puede redondearlo."
                .formatted(producto.code(), decimales);
        throw new BusinessRuleException(
            "RN-MV-014", mensaje, List.of(new FieldError("packageId", "RN-MV-014", mensaje)));
      }

      BigDecimal unitario = ProductPrice.enLaEscalaDe(precio, decimales);
      LineDiscount rebaja =
          LineDiscount.de(
              MovementDiscountType.valueOf(item.discountType()),
              item.discountValue(),
              unitario,
              decimales);

      lineas.add(
          MovementLine.copiarDe(
              producto.id(),
              vendedorId,
              producto.code(),
              producto.name(),
              producto.description(),
              1,
              unitario,
              producto.validityDays(),
              List.of(rebaja)));
    }
    return lineas;
  }

  /** Lo que se cobra, que es lo que `RN-MV-022` necesita para decidir el método. */
  private static BigDecimal aPagar(List<MovementLine> lineas) {
    return lineas.stream()
        .map(MovementLine::getLineAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static String nombre(ClientView cliente) {
    String completo =
        ((cliente.firstName() == null ? "" : cliente.firstName())
                + " "
                + (cliente.lastName() == null ? "" : cliente.lastName()))
            .trim();
    return completo.isEmpty() ? null : completo;
  }
}
