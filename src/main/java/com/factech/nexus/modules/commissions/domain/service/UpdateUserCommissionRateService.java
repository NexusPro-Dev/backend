package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.UpdateUserCommissionRateRequest;
import com.factech.nexus.modules.commissions.application.UserCommissionRateResponse;
import com.factech.nexus.modules.commissions.domain.models.UserCommissionRate;
import com.factech.nexus.modules.commissions.domain.repository.UserCommissionRateQueryRepository;
import com.factech.nexus.modules.commissions.domain.repository.UserCommissionRateRepository;
import com.factech.nexus.modules.commissions.domain.repository.UserRateProductRepository;
import com.factech.nexus.modules.products.application.ProductCatalog;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.ValidationException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Corrección de una tasa personalizada.
 *
 * <p><b>Corregir no es cambiar, y aquí la distinción sigue viva.</b> Corregir arregla un error y
 * reescribe lo que esa tasa dice que rigió; cambiar lo que gana alguien a partir de una fecha es
 * <b>cerrar la vigente y registrar otra</b>, que son dos operaciones. Es lo que el catálogo de rol
 * perdió al quedarse sin vigencia.
 */
@Service
public class UpdateUserCommissionRateService {

  private static final String MODULO = "CM";
  private static final String ENTIDAD = "user_commission_rates";

  private final UserCommissionRateRepository tasas;
  private final UserCommissionRateQueryRepository consultas;
  private final UserRateProductRepository asociaciones;
  private final ProductCatalog productos;
  private final ProductCommissionCapGuard tope;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public UpdateUserCommissionRateService(
      UserCommissionRateRepository tasas,
      UserCommissionRateQueryRepository consultas,
      UserRateProductRepository asociaciones,
      ProductCatalog productos,
      ProductCommissionCapGuard tope,
      AuditWriter auditoria) {
    this(tasas, consultas, asociaciones, productos, tope, auditoria, Clock.systemUTC());
  }

  UpdateUserCommissionRateService(
      UserCommissionRateRepository tasas,
      UserCommissionRateQueryRepository consultas,
      UserRateProductRepository asociaciones,
      ProductCatalog productos,
      ProductCommissionCapGuard tope,
      AuditWriter auditoria,
      Clock reloj) {
    this.tasas = tasas;
    this.consultas = consultas;
    this.asociaciones = asociaciones;
    this.productos = productos;
    this.tope = tope;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  /**
   * Revalida `RN-CM-006` y `RN-CM-019` en <b>todos</b> los productos donde la tasa ya rige.
   *
   * <p><b>Alargar la vigencia puede pisar a otra tasa</b> de la misma persona en alguno de esos
   * productos, y <b>subir un importe fijo</b> puede pasarse del precio de alguno. Las dos cosas
   * eran imposibles antes de que esta tasa tuviera asociaciones.
   *
   * <p><b>Si cualquiera de los productos se pasaría, la corrección se rechaza entera</b>, que es el
   * mismo criterio que `RF-CM-003` aplica a la tasa de rol: corregir a medias dejaría la tasa
   * diciendo una cosa en unos productos y otra en otros.
   *
   * <p>El bloqueo por persona lo toma {@code update} justo después, y basta: entre esta lectura y
   * la escritura no hay otro punto de entrada que pueda insertar una asociación de esta persona.
   */
  private void revalidar(UserCommissionRate tasa, UpdateUserCommissionRateRequest peticion) {
    List<UUID> donde = asociaciones.productosDe(tasa.getId());
    if (donde.isEmpty()) {
      // Sin asociaciones no rige en ninguna parte (`RN-CM-012`): no hay nada
      // contra lo que chocar.
      return;
    }

    boolean cambiaVigencia = peticion.validTo().presente();
    LocalDate hasta = cambiaVigencia ? peticion.validTo().valor() : tasa.getValidTo();
    boolean cambiaValor = peticion.valor().presente() && peticion.valor().valor() != null;

    for (UUID productId : donde) {
      if (cambiaVigencia
          && asociaciones.haySolape(
              tasa.getUserId(), productId, tasa.getId(), tasa.getValidFrom(), hasta)) {
        String mensaje =
            "Con esa vigencia la tasa pisaría a otra viva de la misma persona en alguno de sus"
                + " productos.";
        throw new BusinessRuleException(
            "EX-006", mensaje, List.of(new FieldError("validTo", "EX-006", mensaje)));
      }
      if (cambiaValor) {
        productos
            .find(productId)
            .ifPresent(
                producto ->
                    tope.verificarIndividual(
                        producto.id(),
                        producto.code(),
                        peticion.valor().valor(),
                        "EX-007",
                        "EX-008"));
      }
    }
  }

  @Transactional
  public UserCommissionRateResponse update(UUID id, UpdateUserCommissionRateRequest peticion) {
    // Los dos inmutables se rechazan ANTES de buscar nada: no cuesta una
    // consulta enterarse de que la petición pedía algo que no se puede hacer.
    if (peticion.traeInmutables()) {
      String mensaje =
          "La persona y el inicio de vigencia de una tasa personalizada no se pueden corregir.";
      throw new ValidationException(
          "VAL-009", mensaje, List.of(new FieldError("userId", "VAL-009", mensaje)));
    }
    if (!peticion.informaAlgo()) {
      String mensaje = "Debe enviarse al menos un campo corregible.";
      throw new ValidationException(
          "VAL-010", mensaje, List.of(new FieldError("percentage", "VAL-010", mensaje)));
    }

    UserCommissionRate tasa =
        tasas
            .findAlive(id)
            .orElseThrow(
                () -> new ResourceNotFoundException("EX-404", "La tasa indicada no existe."));

    // EL BLOQUEO SE TOMA ANTES DE TOCAR LA ENTIDAD, y el orden no es cosmético:

    // LO QUE LA CORRECCIÓN PUEDE ROMPER EN LOS PRODUCTOS DONDE YA RIGE, y que
    // hay que revalidar ANTES de tocar la entidad (11-09-2026).
    //
    // Antes de `V85` nada de esto hacía falta aquí: `RN-CM-006` la garantizaba
    // el motor y `RN-CM-019` no alcanzaba a esta tasa porque no conocía ningún
    // precio. Con la asociación, las dos pasan a depender de que este caso de
    // uso se acuerde — y ese es el coste que la enmienda declara.
    revalidar(tasa, peticion);
    // `lockUser` es una consulta nativa, y Hibernate vuelca lo pendiente antes
    // de ejecutar una. Tomándolo después de `update(...)`, ese volcado ocurriría
    // DENTRO del bloqueo y fuera de todo try, y la violación del solapamiento
    // volvería a escaparse como 500.
    tasas.lockUser(tasa.getUserId());

    Map<String, Object> cambios =
        tasa.update(peticion.valor(), peticion.validTo(), OffsetDateTime.now(reloj));

    // EL VOLCADO EXPLÍCITO ES LA LÍNEA QUE IMPIDE UN 500. La entidad está
    // gestionada y el UPDATE saldría en el `commit`, FUERA DE TODO TRY, de modo
    // que la violación del solapamiento se escaparía sin traducir. Es
    // exactamente lo que le ocurrió a `RF-SP-027` con el correo duplicado.
    if (!cambios.isEmpty()) {
      tasas.flushChanges();
      auditoria.recordChange(
          new ChangeEvent(MODULO, ENTIDAD, tasa.getId(), ChangeAction.UPDATE, cambios));
    }

    // Se relee para devolver la persona resuelta, que es lo que el contrato
    // promete. Una sentencia con su `JOIN`, no una llamada al puerto.
    return consultas
        .findRow(tasa.getId())
        .map(UserCommissionRateResponse::from)
        .orElseThrow(() -> new ResourceNotFoundException("EX-404", "La tasa indicada no existe."));
  }
}
