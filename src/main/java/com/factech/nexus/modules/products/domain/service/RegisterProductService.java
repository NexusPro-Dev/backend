package com.factech.nexus.modules.products.domain.service;

import com.factech.nexus.modules.products.application.ProductPrice;
import com.factech.nexus.modules.products.application.ProductResponse;
import com.factech.nexus.modules.products.application.RegisterProductCommand;
import com.factech.nexus.modules.products.domain.models.Product;
import com.factech.nexus.modules.products.domain.repository.ProductRepository;
import com.factech.nexus.modules.system.currencies.application.CurrencyCatalog;
import com.factech.nexus.modules.system.currencies.application.CurrencyCatalog.CurrencyView;
import com.factech.nexus.modules.system.memberships.application.MembershipCatalog;
import com.factech.nexus.modules.system.memberships.application.MembershipCatalog.MembershipView;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta de un producto del catálogo (`RF-PM-001`).
 *
 * <p><b>El orden de verificación es el contrato</b> (`plan.md` §4):
 *
 * <ol>
 *   <li>La moneda existe y está <b>activa</b>, y el precio cabe en sus decimales.
 *   <li>Si es un upgrade, la membresía destino existe.
 *   <li>El código no lo ha tenido nunca otro producto; el nombre no lo tiene ningún producto vivo.
 *   <li>Se registra <b>inactivo</b> y se emite el evento de creación.
 * </ol>
 *
 * <p><b>Los dos primeros pasos leen de `SP` por las interfaces que publica</b> (**D-25**), nunca de
 * sus tablas. Una regla de ArchUnit lo ancla: sin ella la frontera sería una convención, y las
 * convenciones se saltan sin que nada falle.
 *
 * <p><b>No hay bloqueo pesimista</b>, y no es un olvido: aquí no se lee ningún agregado para
 * modificarlo. El alta inserta, y la unicidad la resuelven las restricciones — dos altas
 * simultáneas con el mismo código se serializan en el índice y la perdedora recibe su {@code 409}
 * traducido.
 */
@Service
public class RegisterProductService {

  private static final String MODULO = "PM";
  private static final String ENTIDAD = "products";

  private final ProductRepository productos;
  private final MembershipCatalog membresias;
  private final CurrencyCatalog monedas;
  private final AuditWriter auditoria;
  private final UuidV7Generator ids;
  private final Clock reloj;

  /**
   * Constructor de producción. La anotación es obligatoria porque la clase declara dos
   * constructores —el segundo existe para que la prueba pueda fijar el reloj— y Spring solo infiere
   * cuando hay uno.
   */
  @Autowired
  public RegisterProductService(
      ProductRepository productos,
      MembershipCatalog membresias,
      CurrencyCatalog monedas,
      AuditWriter auditoria,
      UuidV7Generator ids) {
    this(productos, membresias, monedas, auditoria, ids, Clock.systemUTC());
  }

  RegisterProductService(
      ProductRepository productos,
      MembershipCatalog membresias,
      CurrencyCatalog monedas,
      AuditWriter auditoria,
      UuidV7Generator ids,
      Clock reloj) {
    this.productos = productos;
    this.membresias = membresias;
    this.monedas = monedas;
    this.auditoria = auditoria;
    this.ids = ids;
    this.reloj = reloj;
  }

  @Transactional
  public ProductResponse register(RegisterProductCommand comando) {
    // PASO 2 del contrato (`plan.md` §4): los datos obligatorios de ESE tipo están
    // y no llegan los que ese tipo prohíbe. Va ANTES de buscar el destino: si se
    // buscara primero, un upgrade sin destino saldría como «la membresía no
    // existe» —un 422 sobre un dato que el actor nunca envió— en vez del 400 que
    // le corresponde. Lo destapó la prueba de la condición cruzada.
    Product.verificarTipoYDestino(comando.type(), comando.targetMembershipId());

    // `RN-PM-016` —el icono solo en el upgrade— NO se sube aquí, al revés que
    // `RN-PM-002`: el motivo de subir aquella es que su incumplimiento se
    // reportaría como «la membresía no existe», y el icono no interviene en esa
    // búsqueda. Lo comprueba `Product.create`, que es donde vive la regla.
    CurrencyView moneda = verificarMoneda(comando);
    MembershipView destino = verificarDestino(comando);
    verificarUnicidad(comando);

    Product nuevo =
        productos.save(
            Product.create(
                ids.next(),
                comando.code(),
                comando.type(),
                comando.name(),
                comando.description(),
                comando.icon(),
                comando.targetMembershipId(),
                comando.price(),
                comando.currencyId(),
                comando.validityDays(),
                OffsetDateTime.now(reloj)));

    auditar(nuevo);

    return ProductResponse.from(nuevo, destino, moneda);
  }

  /**
   * `EX-003` y `RN-PM-007`.
   *
   * <p><b>Inexistente y desactivada se distinguen</b>: una es un dato equivocado y la otra una
   * decisión del sistema que el actor no puede saltarse. Devolver el mismo mensaje haría que quien
   * escribió bien el identificador buscara el error donde no está.
   */
  private CurrencyView verificarMoneda(RegisterProductCommand comando) {
    CurrencyView moneda =
        monedas
            .find(comando.currencyId())
            .orElseThrow(
                () ->
                    new UnprocessableEntityException(
                        "EX-003",
                        "La moneda indicada no existe.",
                        List.of(
                            new FieldError(
                                "currencyId", "EX-003", "La moneda indicada no existe."))));

    if (!moneda.active()) {
      String mensaje = "La moneda indicada está desactivada y no admite productos nuevos.";
      throw new UnprocessableEntityException(
          "EX-003", mensaje, List.of(new FieldError("currencyId", "EX-003", mensaje)));
    }

    // `RN-PM-007`. No lo puede comprobar un CHECK: la escala admisible vive en
    // otra tabla, y PostgreSQL no admite subconsultas en una restricción.
    if (!ProductPrice.cabeEn(comando.price(), moneda.decimalPlaces())) {
      String mensaje =
          "El precio no admite más de %d decimales en %s."
              .formatted(moneda.decimalPlaces(), moneda.code());
      throw new ValidationException(
          "VAL-005", mensaje, List.of(new FieldError("price", "VAL-005", mensaje)));
    }
    return moneda;
  }

  /**
   * `EX-002`. Solo aplica a los upgrades; en un servicio ni siquiera se consulta.
   *
   * <p><b>No es un «no encontrado»</b>: lo que no existe es un dato que el actor envió, no el
   * recurso que estaba pidiendo. Por eso `422` y no `404`.
   */
  private MembershipView verificarDestino(RegisterProductCommand comando) {
    if (comando.type() == null || !comando.type().exigeDestino()) {
      return null;
    }
    return membresias
        .find(comando.targetMembershipId())
        .orElseThrow(
            () ->
                new UnprocessableEntityException(
                    "EX-002",
                    "La membresía indicada no existe.",
                    List.of(
                        new FieldError(
                            "targetMembershipId", "EX-002", "La membresía indicada no existe."))));
  }

  /**
   * `EX-001` y `EX-005`. La verificación previa existe <b>para poder dar un mensaje preciso</b>
   * —cuál de los dos campos está duplicado—; la garantía la dan los índices únicos, y su violación
   * la traduce el adaptador. La restricción decide; esto solo redacta.
   *
   * <p>El código se compara <b>incluyendo los eliminados</b> y el nombre <b>solo entre los
   * vivos</b>: esa asimetría es `RN-PM-013` y no un descuido.
   */
  private void verificarUnicidad(RegisterProductCommand comando) {
    String codigo =
        comando.code() == null ? null : comando.code().trim().toUpperCase(java.util.Locale.ROOT);
    if (codigo != null && productos.existsCode(codigo)) {
      String mensaje = "Ya existe un producto con ese código.";
      throw new BusinessRuleException(
          "EX-005", mensaje, List.of(new FieldError("code", "EX-005", mensaje)));
    }
    String nombre = comando.name() == null ? null : comando.name().trim();
    if (nombre != null && productos.existsAliveName(nombre)) {
      String mensaje = "Ya existe un producto con ese nombre.";
      throw new BusinessRuleException(
          "EX-001", mensaje, List.of(new FieldError("name", "EX-001", mensaje)));
    }
  }

  /**
   * Un evento de creación con el estado inicial completo (`CA-PM-011`).
   *
   * <p><b>Sin evento de seguridad</b>, y no es una omisión: un producto no concede privilegios
   * sobre el sistema y el catálogo de `security.md` §8.1 es cerrado. Es la misma postura que
   * `RF-SP-016` tomó con las membresías. Quién puso un precio lo responde este mismo evento.
   */
  private void auditar(Product nuevo) {
    // La instantánea la arma el agregado, y la misma que usa el retiro
    // (`RF-PM-006`): si cada caso de uso armara su mapa, el registro de
    // creación y el de eliminación describirían el mismo producto con claves
    // distintas, y compararlos —que es para lo que existen— dejaría de ser
    // posible.
    auditoria.recordChange(
        new ChangeEvent(MODULO, ENTIDAD, nuevo.getId(), ChangeAction.CREATE, nuevo.instantanea()));
  }
}
