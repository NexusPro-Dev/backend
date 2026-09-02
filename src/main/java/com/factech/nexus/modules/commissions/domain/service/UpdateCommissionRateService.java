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
 * Corrección del porcentaje de una tasa de rol (`RF-CM-003`).
 *
 * <p><b>Esta operación borra el pasado, y hay que decirlo aquí.</b> Hasta el 01-09-2026 la tarifa
 * tenía vigencia, y por eso «corregir» y «cambiar» eran cosas distintas: corregir reescribía lo que
 * esa tarifa decía que rigió, y cambiar la comisión a partir de una fecha era cerrar la vigente y
 * registrar otra.
 *
 * <p><b>Sin vigencia solo queda reescribir.</b> Pasar un rol de 10 a 12 <b>borra el 10</b>: no hay
 * dos filas contando cada una su parte, hay una que ahora dice otra cosa. La única defensa del
 * pasado es que la liquidación haya copiado el porcentaje que aplicó (`RN-CM-008`) — y esa
 * liquidación no existe todavía, de modo que <b>hoy esta llamada no deja rastro de lo que borró</b>
 * más allá del registro de auditoría del cambio.
 *
 * <p>Ese registro, por tanto, deja de ser un complemento y pasa a ser <b>el único sitio donde queda
 * escrito el porcentaje anterior</b>.
 */
@Service
public class UpdateCommissionRateService {

  private static final String MODULO = "CM";
  private static final String ENTIDAD = "commission_rates";

  private final CommissionRateRepository tasas;
  private final CommissionRateQueryRepository consultas;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public UpdateCommissionRateService(
      CommissionRateRepository tasas,
      CommissionRateQueryRepository consultas,
      AuditWriter auditoria) {
    this(tasas, consultas, auditoria, Clock.systemUTC());
  }

  UpdateCommissionRateService(
      CommissionRateRepository tasas,
      CommissionRateQueryRepository consultas,
      AuditWriter auditoria,
      Clock reloj) {
    this.tasas = tasas;
    this.consultas = consultas;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public CommissionRateResponse update(UUID id, UpdateCommissionRateRequest peticion) {
    // El inmutable se rechaza ANTES de buscar nada: no cuesta una consulta
    // enterarse de que la petición pedía algo que no se puede hacer.
    if (peticion.traeInmutables()) {
      String mensaje = "El rol de una tasa de comisión no se puede corregir.";
      throw new ValidationException(
          "VAL-009", mensaje, List.of(new FieldError("roleId", "VAL-009", mensaje)));
    }
    if (!peticion.informaAlgo()) {
      String mensaje = "Debe enviarse al menos un campo corregible.";
      throw new ValidationException(
          "VAL-010", mensaje, List.of(new FieldError("percentage", "VAL-010", mensaje)));
    }

    // Una tasa retirada se trata como inexistente: lo que se retiró debe quedar
    // como estaba, para que lo que la referencie siga diciendo la verdad.
    CommissionRate tasa =
        tasas
            .findAlive(id)
            .orElseThrow(
                () -> new ResourceNotFoundException("EX-404", "La tasa indicada no existe."));

    Map<String, Object> cambios = tasa.update(peticion.valor(), OffsetDateTime.now(reloj));

    if (!cambios.isEmpty()) {
      tasas.flushChanges();
      // ESTE REGISTRO ES HOY LA ÚNICA COPIA DEL PORCENTAJE ANTERIOR. Sin
      // vigencia en la tabla y sin liquidación que copie lo que aplicó, si esto
      // no se escribiera el valor previo desaparecería del sistema entero.
      auditoria.recordChange(
          new ChangeEvent(MODULO, ENTIDAD, tasa.getId(), ChangeAction.UPDATE, cambios));
    }

    // Se relee para devolver el rol resuelto y la cuenta de asociaciones, que es
    // lo que el contrato promete. Una sentencia con su `JOIN`, no una llamada al
    // puerto por cada fila.
    return consultas
        .findRow(tasa.getId())
        .map(CommissionRateResponse::from)
        .orElseThrow(() -> new ResourceNotFoundException("EX-404", "La tasa indicada no existe."));
  }
}
