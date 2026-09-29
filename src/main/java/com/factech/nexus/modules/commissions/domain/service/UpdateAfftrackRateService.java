package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.AfftrackRateResponse;
import com.factech.nexus.modules.commissions.application.UpdateAfftrackRateRequest;
import com.factech.nexus.modules.commissions.domain.models.AfftrackRate;
import com.factech.nexus.modules.commissions.domain.repository.AfftrackRateQueryRepository;
import com.factech.nexus.modules.commissions.domain.repository.AfftrackRateRepository;
import com.factech.nexus.modules.commissions.domain.repository.JpaAfftrackRateRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.ValidationException;
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
 * `RF-CM-017` — corregir el límite o el valor de un escalón de rol.
 *
 * <p>La forma de `RF-CM-003`: lo inmutable se rechaza antes de buscar nada, un escalón retirado se
 * trata como inexistente, y una corrección que no cambia nada no escribe ni audita. <b>Lo ya pagado
 * no cambia</b>: cada comisión afftrack copió el límite y el valor que pagó (`RN-CM-008`).
 */
@Service
public class UpdateAfftrackRateService {

  private static final String MODULO = "CM";
  private static final String ENTIDAD = "afftrack_rates";

  private final AfftrackRateRepository escalones;
  private final AfftrackRateQueryRepository consultas;
  private final ProductCurrencyScale escala;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public UpdateAfftrackRateService(
      AfftrackRateRepository escalones,
      AfftrackRateQueryRepository consultas,
      ProductCurrencyScale escala,
      AuditWriter auditoria) {
    this(escalones, consultas, escala, auditoria, Clock.systemUTC());
  }

  UpdateAfftrackRateService(
      AfftrackRateRepository escalones,
      AfftrackRateQueryRepository consultas,
      ProductCurrencyScale escala,
      AuditWriter auditoria,
      Clock reloj) {
    this.escalones = escalones;
    this.consultas = consultas;
    this.escala = escala;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public AfftrackRateResponse update(UUID id, UpdateAfftrackRateRequest peticion) {
    if (peticion.traeInmutables()) {
      String mensaje = "El rol y el producto de una comisión afftrack no se pueden corregir.";
      String campo = peticion.roleId().presente() ? "roleId" : "productId";
      throw new ValidationException(
          "EX-002", mensaje, List.of(new FieldError(campo, "EX-002", mensaje)));
    }
    if (!peticion.informaAlgo()) {
      String mensaje = "Debe enviarse al menos un campo corregible.";
      throw new ValidationException(
          "EX-003", mensaje, List.of(new FieldError("threshold", "EX-003", mensaje)));
    }
    Integer limite = validarLimite(peticion);
    BigDecimal valor = validarValor(peticion);

    AfftrackRate escalon =
        escalones
            .findAliveForUpdate(id)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-404", "La comisión afftrack indicada no existe."));

    escala.verificarImporte(
        escalon.getProductId(), valor, "VAL-003", "amountPerFtd", "El valor por FTD");
    if (limite != null
        && limite != escalon.getThreshold()
        && escalones.existsAlive(
            escalon.getProductId(), escalon.getRoleId(), limite, escalon.getId())) {
      throw JpaAfftrackRateRepository.yaHayUnoConEseLimite();
    }

    Map<String, Object> cambios = escalon.corregir(limite, valor, OffsetDateTime.now(reloj));
    if (!cambios.isEmpty()) {
      escalones.flushChanges();
      auditoria.recordChange(
          new ChangeEvent(MODULO, ENTIDAD, escalon.getId(), ChangeAction.UPDATE, cambios));
    }

    return consultas
        .findRow(escalon.getId())
        .map(AfftrackRateResponse::from)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "EX-404", "La comisión afftrack indicada no existe."));
  }

  /** `VAL-001` y `VAL-002` para el límite: entero, mayor que cero, y no se vacía. */
  private static Integer validarLimite(UpdateAfftrackRateRequest peticion) {
    if (!peticion.threshold().presente()) {
      return null;
    }
    BigDecimal limite = peticion.threshold().valor();
    List<FieldError> errores = new ArrayList<>();
    if (limite == null) {
      errores.add(new FieldError("threshold", "VAL-002", "El límite no puede vaciarse."));
    } else if (limite.signum() <= 0 || limite.stripTrailingZeros().scale() > 0) {
      errores.add(
          new FieldError(
              "threshold", "VAL-001", "El límite debe ser un número entero mayor que cero."));
    }
    if (!errores.isEmpty()) {
      throw new ValidationException(errores.get(0).code(), errores.get(0).message(), errores);
    }
    return limite.intValueExact();
  }

  /** `VAL-002` para el valor: no negativo, y no se vacía. */
  private static BigDecimal validarValor(UpdateAfftrackRateRequest peticion) {
    if (!peticion.amountPerFtd().presente()) {
      return null;
    }
    BigDecimal valor = peticion.amountPerFtd().valor();
    if (valor == null || valor.signum() < 0) {
      String mensaje = "El valor por FTD no puede ser negativo ni vaciarse.";
      throw new ValidationException(
          "VAL-002", mensaje, List.of(new FieldError("amountPerFtd", "VAL-002", mensaje)));
    }
    return valor;
  }
}
