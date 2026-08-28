package com.factech.nexus.modules.products.domain.service;

import com.factech.nexus.modules.products.application.ProductDetailResponse;
import com.factech.nexus.modules.products.application.ProductPrice;
import com.factech.nexus.modules.products.application.UpdateProductRequest;
import com.factech.nexus.modules.products.domain.models.Product;
import com.factech.nexus.modules.products.domain.repository.ProductQueryRepository;
import com.factech.nexus.modules.products.domain.repository.ProductRepository;
import com.factech.nexus.modules.system.currencies.application.CurrencyCatalog;
import com.factech.nexus.modules.system.currencies.application.CurrencyCatalog.CurrencyView;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.patch.Patchable;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Corregir un producto del catálogo (`RF-PM-004`).
 *
 * <p>Orden de verificación, que es el contrato (`plan.md` §5):
 *
 * <ol>
 *   <li>Los campos <b>inmutables</b> no llegan (`VAL-006`), y algo se informa.
 *   <li><b>Bloqueo</b> de la fila. Va antes de validar contra el estado: validar sobre una fila que
 *       otra transacción está corrigiendo produce decisiones tomadas sobre un estado que ya no
 *       existe.
 *   <li>El producto existe y <b>no está retirado</b> (`EX-001`).
 *   <li>Cada campo recibido, contra su regla.
 *   <li>Si llega nombre: unicidad <b>excluyendo al propio producto</b>.
 *   <li>Si llega precio o moneda: la moneda existe, está activa, y los decimales cuadran <b>con la
 *       moneda nueva</b>.
 *   <li>Se aplica, y <b>solo si algo cambió</b> se emite el evento.
 * </ol>
 *
 * <h2>La unicidad se comprueba ANTES de tocar el agregado</h2>
 *
 * <p>Es un defecto que `RF-SP-004` ya pagó: con el nombre nuevo escrito en la entidad gestionada,
 * el propio {@code SELECT} de la comprobación <b>dispara el vaciado de Hibernate</b>, la violación
 * del índice llega antes que la comprobación y sale un {@code 500} donde corresponde un {@code
 * 409}. Solo aparece con el dato ya duplicado, que es justo cuando importa.
 *
 * <p><b>Ningún rechazo aplica nada</b> (`CA-PM-034`): todas las comprobaciones ocurren antes del
 * único punto en que el agregado se modifica.
 */
@Service
public class UpdateProductService {

  private static final String MODULO = "PM";
  private static final String ENTIDAD = "products";

  private static final int LONGITUD_NOMBRE = 150;
  private static final int LONGITUD_DESCRIPCION = 1000;

  private final ProductRepository productos;
  private final ProductQueryRepository consultas;
  private final CurrencyCatalog monedas;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public UpdateProductService(
      ProductRepository productos,
      ProductQueryRepository consultas,
      CurrencyCatalog monedas,
      AuditWriter auditoria) {
    this(productos, consultas, monedas, auditoria, Clock.systemUTC());
  }

  UpdateProductService(
      ProductRepository productos,
      ProductQueryRepository consultas,
      CurrencyCatalog monedas,
      AuditWriter auditoria,
      Clock reloj) {
    this.productos = productos;
    this.consultas = consultas;
    this.monedas = monedas;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public ProductDetailResponse update(UUID id, UpdateProductRequest peticion) {
    verificarQueNoTraeInmutables(peticion);
    verificarQueInformaAlgo(peticion);

    Product producto =
        productos
            .findAliveByIdForUpdate(id)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", "No existe un producto vivo con ese identificador."));

    verificarFormato(peticion);
    verificarNombreLibre(peticion, producto);
    verificarPrecioYMoneda(peticion, producto);

    Map<String, Object> cambios =
        producto.update(
            peticion.name(),
            peticion.description(),
            peticion.icon(),
            peticion.price(),
            peticion.currencyId(),
            peticion.validityDays(),
            OffsetDateTime.now(reloj));

    if (cambios.containsKey("name")) {
      volcarElCambioDeNombre();
    }

    if (!cambios.isEmpty()) {
      // Solo lo que cambió, y ninguno si no cambió nada (`CA-PM-038`): un
      // cambio que no cambió nada no es un cambio, y registrarlo llena la línea
      // de tiempo de ruido que oculta lo que sí ocurrió.
      auditoria.recordChange(
          new ChangeEvent(MODULO, ENTIDAD, producto.getId(), ChangeAction.UPDATE, cambios));
    }

    return consultas
        .findDetail(producto.getId())
        .map(fila -> ProductDetailResponse.from(fila, null))
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "EX-001", "No existe un producto con ese identificador."));
  }

  /**
   * Vuelca el cambio de nombre <b>aquí</b> y no en el {@code commit}.
   *
   * <p><b>Esa es la diferencia entre un `409` y un `500`</b>, y la descubrió la prueba concurrente:
   * {@link #verificarNombreLibre} existe para el mensaje, pero entre su lectura y esta escritura
   * hay una ventana que dos correcciones simultáneas hacia el mismo nombre atraviesan las dos —cada
   * una bloquea <b>su propia</b> fila, de modo que el bloqueo no las serializa—. La segunda la
   * rechaza {@code uq_products_name}, y esa violación solo puede traducirse mientras siga habiendo
   * alguien escuchando: en el {@code commit} ya no lo hay.
   *
   * <p><b>Se reetiqueta a `EX-002`.</b> El adaptador traduce por nombre de restricción y no puede
   * saber desde qué operación se le llamó: el alta numera «nombre duplicado» como `EX-001` y esta
   * spec como `EX-002`. Lo que cambia es la etiqueta, no el diagnóstico.
   */
  private void volcarElCambioDeNombre() {
    try {
      productos.flush();
    } catch (BusinessRuleException conflicto) {
      throw new BusinessRuleException(
          "EX-002",
          conflicto.getMessage(),
          conflicto.errors().stream()
              .map(error -> new FieldError(error.field(), "EX-002", error.message()))
              .toList());
    }
  }

  /**
   * `VAL-006` — el tipo, el código y el destino <b>no se corrigen</b>.
   *
   * <p><b>Se rechaza y no se ignora</b> (`CA-PM-033`): ignorarlos haría creer al actor que el
   * cambio se aplicó, y lo descubriría cuando alguien comprara el producto equivocado.
   *
   * <p>Va lo primero, incluso antes de buscar el producto: una petición que pide lo imposible no
   * debe costar una consulta.
   */
  private static void verificarQueNoTraeInmutables(UpdateProductRequest peticion) {
    if (!peticion.traeInmutables()) {
      return;
    }
    List<FieldError> problemas = new ArrayList<>();
    String mensaje = "El tipo, el código y la membresía destino no se pueden modificar.";
    if (peticion.type().presente()) {
      problemas.add(new FieldError("type", "VAL-006", mensaje));
    }
    if (peticion.code().presente()) {
      problemas.add(new FieldError("code", "VAL-006", mensaje));
    }
    if (peticion.targetMembershipId().presente()) {
      problemas.add(new FieldError("targetMembershipId", "VAL-006", mensaje));
    }
    throw new ValidationException("VAL-006", mensaje, problemas);
  }

  private static void verificarQueInformaAlgo(UpdateProductRequest peticion) {
    if (!peticion.informaAlgo()) {
      String mensaje = "Debe informar al menos uno de los campos corregibles.";
      throw new ValidationException(
          "VAL-002", mensaje, List.of(new FieldError(null, "VAL-002", mensaje)));
    }
  }

  /**
   * Las reglas de cada campo, <b>todas juntas</b>: quien se equivocó en dos corrige una vez.
   *
   * <p><b>El nombre no admite vaciarse y la descripción sí</b>, y esa es la distinción que {@code
   * Patchable} existe para transportar: la columna del nombre es {@code NOT NULL}, de modo que
   * aceptar su nulo produciría una violación de integridad traducida a {@code 500} en lugar del
   * {@code 400} que corresponde. La descripción admite nulo en la base, y ahí el nulo <b>es una
   * orden</b>: bórrala.
   */
  private static void verificarFormato(UpdateProductRequest peticion) {
    List<FieldError> problemas = new ArrayList<>();

    if (peticion.name().presente()) {
      String valor = peticion.name().valor();
      if (valor == null || valor.isBlank()) {
        problemas.add(
            new FieldError("name", "VAL-002", "El nombre del producto no puede quedar vacío."));
      } else if (valor.trim().length() > LONGITUD_NOMBRE) {
        problemas.add(
            new FieldError(
                "name",
                "VAL-003",
                "El nombre no puede exceder " + LONGITUD_NOMBRE + " caracteres."));
      }
    }

    if (peticion.description().presente()) {
      String valor = peticion.description().valor();
      if (valor != null && valor.trim().length() > LONGITUD_DESCRIPCION) {
        problemas.add(
            new FieldError(
                "description",
                "VAL-003",
                "La descripción no puede exceder " + LONGITUD_DESCRIPCION + " caracteres."));
      }
    }

    if (peticion.price().presente()) {
      BigDecimal valor = peticion.price().valor();
      if (valor == null) {
        problemas.add(new FieldError("price", "VAL-004", "El precio es obligatorio."));
      } else if (valor.compareTo(BigDecimal.ZERO) <= 0) {
        problemas.add(new FieldError("price", "VAL-004", "El precio debe ser mayor que cero."));
      }
    }

    if (peticion.currencyId().presente() && peticion.currencyId().valor() == null) {
      problemas.add(new FieldError("currencyId", "VAL-004", "La moneda es obligatoria."));
    }

    // La vigencia SÍ admite vaciarse: el nulo la convierte en un producto que
    // no caduca (`CA-PM-094`). Lo que no admite es un número no positivo.
    if (peticion.validityDays().presente()) {
      Integer valor = peticion.validityDays().valor();
      if (valor != null && valor < 1) {
        problemas.add(
            new FieldError(
                "validityDays",
                "VAL-011",
                "La vigencia debe ser un número de días mayor que cero."));
      }
    }

    if (!problemas.isEmpty()) {
      throw new ValidationException(problemas.get(0).code(), problemas.get(0).message(), problemas);
    }
  }

  /**
   * `EX-002` — el nombre nuevo no lo tiene otro producto vivo.
   *
   * <p><b>Solo si el nombre llega y es distinto del actual.</b> Comprobarlo siempre costaría una
   * consulta a cada corrección de descripción, y comprobarlo sin excluir al propio producto
   * rechazaría enviar el nombre que ya tiene.
   */
  private void verificarNombreLibre(UpdateProductRequest peticion, Product producto) {
    if (!peticion.name().presente()) {
      return;
    }
    String nuevo = peticion.name().valor().trim();
    if (nuevo.equals(producto.getName())) {
      return;
    }
    if (productos.existsAliveNameForOther(nuevo, producto.getId())) {
      String mensaje = "Ya existe un producto con ese nombre.";
      throw new BusinessRuleException(
          "EX-002", mensaje, List.of(new FieldError("name", "EX-002", mensaje)));
    }
  }

  /**
   * `EX-003` y `VAL-005` — el precio se valida contra la moneda <b>nueva</b>.
   *
   * <p>Es el caso que se hace mal: cambiar moneda y precio a la vez y validar los decimales contra
   * la moneda <b>anterior</b> dejaría entrar un precio que la nueva no admite. Por eso se resuelve
   * primero cuál va a ser la moneda y después se mide el precio contra ella.
   *
   * <p><b>El importe no se convierte</b> (`spec.md` §13): el sistema no hace conversión de divisa.
   * Cambiar de moneda es declarar que ese número siempre estuvo en la otra.
   */
  private void verificarPrecioYMoneda(UpdateProductRequest peticion, Product producto) {
    boolean cambiaMoneda = presenteConValor(peticion.currencyId());
    boolean cambiaPrecio = presenteConValor(peticion.price());
    if (!cambiaMoneda && !cambiaPrecio) {
      return;
    }

    UUID monedaFinal = cambiaMoneda ? peticion.currencyId().valor() : producto.getCurrencyId();
    CurrencyView moneda =
        monedas
            .find(monedaFinal)
            .orElseThrow(
                () ->
                    new UnprocessableEntityException(
                        "EX-003",
                        "La moneda indicada no existe.",
                        List.of(
                            new FieldError(
                                "currencyId", "EX-003", "La moneda indicada no existe."))));

    // Solo se exige que esté ACTIVA cuando se está cambiando a ella: un
    // producto cuya moneda se desactivó después no puede quedar bloqueado para
    // corregirle la descripción.
    if (cambiaMoneda && !moneda.active()) {
      String mensaje = "La moneda indicada está desactivada y no admite productos.";
      throw new UnprocessableEntityException(
          "EX-003", mensaje, List.of(new FieldError("currencyId", "EX-003", mensaje)));
    }

    BigDecimal precioFinal = cambiaPrecio ? peticion.price().valor() : producto.getPrice();
    if (!ProductPrice.cabeEn(precioFinal, moneda.decimalPlaces())) {
      String mensaje =
          "El precio no admite más de %d decimales en %s."
              .formatted(moneda.decimalPlaces(), moneda.code());
      throw new ValidationException(
          "VAL-005", mensaje, List.of(new FieldError("price", "VAL-005", mensaje)));
    }
  }

  private static boolean presenteConValor(Patchable<?> campo) {
    return campo.presente() && campo.valor() != null;
  }
}
