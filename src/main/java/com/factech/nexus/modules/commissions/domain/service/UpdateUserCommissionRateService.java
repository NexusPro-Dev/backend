package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.UpdateUserCommissionRateRequest;
import com.factech.nexus.modules.commissions.application.UserCommissionRateResponse;
import com.factech.nexus.modules.commissions.domain.models.UserCommissionRate;
import com.factech.nexus.modules.commissions.domain.repository.UserCommissionRateQueryRepository;
import com.factech.nexus.modules.commissions.domain.repository.UserCommissionRateRepository;
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
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public UpdateUserCommissionRateService(
      UserCommissionRateRepository tasas,
      UserCommissionRateQueryRepository consultas,
      AuditWriter auditoria) {
    this(tasas, consultas, auditoria, Clock.systemUTC());
  }

  UpdateUserCommissionRateService(
      UserCommissionRateRepository tasas,
      UserCommissionRateQueryRepository consultas,
      AuditWriter auditoria,
      Clock reloj) {
    this.tasas = tasas;
    this.consultas = consultas;
    this.auditoria = auditoria;
    this.reloj = reloj;
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
    // `lockUser` es una consulta nativa, y Hibernate vuelca lo pendiente antes
    // de ejecutar una. Tomándolo después de `update(...)`, ese volcado ocurriría
    // DENTRO del bloqueo y fuera de todo try, y la violación del solapamiento
    // volvería a escaparse como 500.
    tasas.lockUser(tasa.getUserId());

    Map<String, Object> cambios =
        tasa.update(peticion.percentage(), peticion.validTo(), OffsetDateTime.now(reloj));

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
