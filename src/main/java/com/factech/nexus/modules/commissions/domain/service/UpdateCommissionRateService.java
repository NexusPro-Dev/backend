package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.CommissionRateResponse;
import com.factech.nexus.modules.commissions.application.UpdateCommissionRateRequest;
import com.factech.nexus.modules.commissions.domain.models.CommissionRate;
import com.factech.nexus.modules.commissions.domain.repository.CommissionRateQueryRepository;
import com.factech.nexus.modules.commissions.domain.repository.CommissionRateRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.ValidationException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Corrección de una tarifa (`RF-CM-003`).
 *
 * <p><b>Corregir no es cambiar.</b> Corregir arregla un error y reescribe lo que esa tarifa dice
 * que rigió; cambiar la comisión a partir de una fecha es cerrar la vigente y registrar otra, que
 * son dos operaciones distintas.
 */
@Service
public class UpdateCommissionRateService {

  private static final String MODULO = "CM";
  private static final String ENTIDAD = "commission_rates";

  private final CommissionRateRepository tarifas;
  private final CommissionRateQueryRepository consultas;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public UpdateCommissionRateService(
      CommissionRateRepository tarifas,
      CommissionRateQueryRepository consultas,
      AuditWriter auditoria) {
    this(tarifas, consultas, auditoria, Clock.systemUTC());
  }

  UpdateCommissionRateService(
      CommissionRateRepository tarifas,
      CommissionRateQueryRepository consultas,
      AuditWriter auditoria,
      Clock reloj) {
    this.tarifas = tarifas;
    this.consultas = consultas;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public CommissionRateResponse update(UUID id, UpdateCommissionRateRequest peticion) {
    // Los cuatro inmutables se rechazan ANTES de buscar nada: no cuesta una
    // consulta enterarse de que la petición pedía algo que no se puede hacer.
    if (peticion.traeInmutables()) {
      String mensaje =
          "El rol, el producto, la persona y el inicio de vigencia de una tarifa no se pueden"
              + " corregir.";
      throw new ValidationException(
          "VAL-009", mensaje, List.of(new FieldError("roleId", "VAL-009", mensaje)));
    }
    if (!peticion.informaAlgo()) {
      String mensaje = "Debe enviarse al menos un campo corregible.";
      throw new ValidationException(
          "VAL-010", mensaje, List.of(new FieldError("percentage", "VAL-010", mensaje)));
    }

    // Una tarifa retirada se trata como inexistente: lo que se retiró debe
    // quedar como estaba, para que lo que la referencie siga diciendo la verdad.
    CommissionRate tarifa =
        tarifas
            .findAlive(id)
            .orElseThrow(
                () -> new ResourceNotFoundException("EX-404", "La tarifa indicada no existe."));

    // EL BLOQUEO SE TOMA ANTES DE TOCAR LA ENTIDAD, y el orden no es
    // cosmetico: `lockCase` es una consulta nativa, y Hibernate vuelca lo
    // pendiente antes de ejecutar una. Tomandolo despues de `update(...)`,
    // ese volcado ocurriria DENTRO del bloqueo y fuera de todo try, y la
    // violacion del solapamiento volveria a escaparse como 500.
    tarifas.lockCase(tarifa);

    Map<String, Object> cambios =
        tarifa.update(peticion.percentage(), peticion.validTo(), OffsetDateTime.now(reloj));

    // EL VOLCADO EXPLÍCITO ES LA LÍNEA QUE IMPIDE UN 500. La entidad está
    // gestionada y el UPDATE saldría en el `commit`, FUERA DE TODO TRY, de modo
    // que la violación del solapamiento se escaparía sin traducir. Es
    // exactamente lo que le ocurrió a `RF-SP-027` con el correo duplicado.
    if (!cambios.isEmpty()) {
      tarifas.flushChanges();
      auditoria.recordChange(
          new ChangeEvent(MODULO, ENTIDAD, tarifa.getId(), ChangeAction.UPDATE, cambios));
    }

    // Se relee para devolver el rol, el producto y la persona resueltos, que es
    // lo que el contrato promete. Una sentencia con sus tres `JOIN`, no tres
    // llamadas a los puertos.
    return consultas
        .findRow(tarifa.getId())
        .map(CommissionRateResponse::from)
        .orElseThrow(
            () -> new ResourceNotFoundException("EX-404", "La tarifa indicada no existe."));
  }
}
