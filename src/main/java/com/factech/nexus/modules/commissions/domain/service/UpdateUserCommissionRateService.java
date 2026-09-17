package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.UpdateUserCommissionRateRequest;
import com.factech.nexus.modules.commissions.application.UserCommissionRateResponse;
import com.factech.nexus.modules.commissions.domain.models.CommissionValue;
import com.factech.nexus.modules.commissions.domain.models.UserCommissionRate;
import com.factech.nexus.modules.commissions.domain.repository.UserCommissionRateQueryRepository;
import com.factech.nexus.modules.commissions.domain.repository.UserCommissionRateRepository;
import com.factech.nexus.modules.products.application.ProductCatalog;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
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
 *
 * <p><b>Desde el 16-09-2026 revalida contra el único producto de la tasa</b> (`RN-CM-021`): si
 * cambia la vigencia, que no pise a otra viva de la misma persona sobre ese producto (`RN-CM-006`);
 * si cambia el valor, que quepa en el producto —tope individual, gratuito y decimales de la moneda
 * (`RN-CM-019`, `RN-CM-020`, `RN-CM-017`)—. Del 11-09-2026 al 16-09-2026 recorría todos los
 * productos asociados y rechazaba entera si cualquiera se pasaba.
 */
@Service
public class UpdateUserCommissionRateService {

  private static final String MODULO = "CM";
  private static final String ENTIDAD = "user_commission_rates";

  private final UserCommissionRateRepository tasas;
  private final UserCommissionRateQueryRepository consultas;
  private final ProductCatalog productos;
  private final ProductCommissionCapGuard tope;
  private final ProductCurrencyScale escala;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public UpdateUserCommissionRateService(
      UserCommissionRateRepository tasas,
      UserCommissionRateQueryRepository consultas,
      ProductCatalog productos,
      ProductCommissionCapGuard tope,
      ProductCurrencyScale escala,
      AuditWriter auditoria) {
    this(tasas, consultas, productos, tope, escala, auditoria, Clock.systemUTC());
  }

  UpdateUserCommissionRateService(
      UserCommissionRateRepository tasas,
      UserCommissionRateQueryRepository consultas,
      ProductCatalog productos,
      ProductCommissionCapGuard tope,
      ProductCurrencyScale escala,
      AuditWriter auditoria,
      Clock reloj) {
    this.tasas = tasas;
    this.consultas = consultas;
    this.productos = productos;
    this.tope = tope;
    this.escala = escala;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public UserCommissionRateResponse update(UUID id, UpdateUserCommissionRateRequest peticion) {
    // Los dos inmutables se rechazan ANTES de buscar nada: no cuesta una
    // consulta enterarse de que la petición pedía algo que no se puede hacer.
    // El producto no entra aquí: el cuerpo no lo declara, y enviarlo es un
    // campo desconocido que el deserializador rechaza por su cuenta.
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

    // Lo que la corrección puede romper en SU producto, revalidado antes de
    // tocar la entidad: rechazar tarde dejaría la tasa a medio corregir.
    revalidar(tasa, peticion);

    // EL BLOQUEO SE TOMA ANTES DE TOCAR LA ENTIDAD, y el orden no es cosmético:
    // `lockUser` es una consulta nativa, y Hibernate vuelca lo pendiente antes
    // de ejecutar una. Tomado después de `update(...)`, ese volcado ocurriría
    // dentro del bloqueo y fuera de todo try, y la violación del solapamiento
    // volvería a escaparse como 500. El bloqueo acompaña al EXCLUDE: pone en
    // fila a la misma persona para que la carrera no acabe en interbloqueo.
    tasas.lockUser(tasa.getUserId());

    Map<String, Object> cambios =
        tasa.update(peticion.valor(), peticion.validTo(), OffsetDateTime.now(reloj));

    if (!cambios.isEmpty()) {
      // EL VOLCADO EXPLÍCITO ES LA LÍNEA QUE IMPIDE UN 500. La entidad está
      // gestionada y el UPDATE saldría en el `commit`, FUERA DE TODO TRY, de
      // modo que una violación del solapamiento —la carrera que la consulta
      // previa no puede ver— se escaparía sin traducir. Es exactamente lo que
      // le ocurrió a `RF-SP-027` con el correo duplicado.
      tasas.flushChanges();
      auditoria.recordChange(
          new ChangeEvent(MODULO, ENTIDAD, tasa.getId(), ChangeAction.UPDATE, cambios));
    }

    return consultas
        .findRow(tasa.getId())
        .map(UserCommissionRateResponse::from)
        .orElseThrow(() -> new ResourceNotFoundException("EX-404", "La tasa indicada no existe."));
  }

  /**
   * Revalida `RN-CM-006`, `RN-CM-017`, `RN-CM-019` y `RN-CM-020` contra el producto de la tasa.
   *
   * <p><b>Alargar la vigencia puede pisar a otra tasa</b> de la misma persona sobre ese producto, y
   * <b>cambiar el valor</b> puede pasarse del precio o dejar un porcentaje sobre un gratuito. Un
   * valor nulo —vaciar la forma— se deja pasar al agregado, que lo rechaza con `VAL-002`.
   */
  private void revalidar(UserCommissionRate tasa, UpdateUserCommissionRateRequest peticion) {
    if (peticion.validTo().presente()) {
      LocalDate hasta = peticion.validTo().valor();
      UserCommissionRate.verificarVigencia(tasa.getValidFrom(), hasta);
      if (tasas
          .findOverlapping(
              tasa.getUserId(), tasa.getProductId(), tasa.getValidFrom(), hasta, tasa.getId())
          .isPresent()) {
        String mensaje =
            "Con esa vigencia la tasa pisaría a otra viva de la misma persona sobre su producto.";
        throw new com.factech.nexus.shared.error.BusinessRuleException(
            "EX-006", mensaje, List.of(new FieldError("validTo", "EX-006", mensaje)));
      }
    }

    if (peticion.valor().presente() && peticion.valor().valor() != null) {
      CommissionValue nuevo = peticion.valor().valor();
      String codigo =
          productos
              .find(tasa.getProductId())
              .map(ProductCatalog.ProductView::code)
              .orElse(tasa.getProductId().toString());
      escala.verificar(tasa.getProductId(), nuevo, "VAL-014");
      tope.verificarIndividual(tasa.getProductId(), codigo, nuevo, "EX-007", "EX-008");
    }
  }
}
