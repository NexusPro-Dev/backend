package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.UpdateUserAfftrackRateRequest;
import com.factech.nexus.modules.commissions.application.UserAfftrackRateResponse;
import com.factech.nexus.modules.commissions.domain.models.UserAfftrackRate;
import com.factech.nexus.modules.commissions.domain.repository.UserAfftrackRateQueryRepository;
import com.factech.nexus.modules.commissions.domain.repository.UserAfftrackRateRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.ValidationException;
import java.math.BigDecimal;
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
 * `RF-CM-019`, la corrección: el límite, el valor o el fin de vigencia de un escalón de persona.
 *
 * <p>El choque de vigencias se comprueba con <b>el límite y el fin resultantes</b>, no con los que
 * llegan sueltos: bajar el límite a uno que ya ocupa otro escalón vigente es tan choque como
 * alargar el fin sobre él.
 */
@Service
public class UpdateUserAfftrackRateService {

  private static final String MODULO = "CM";
  private static final String ENTIDAD = "user_afftrack_rates";

  private final UserAfftrackRateRepository escalones;
  private final UserAfftrackRateQueryRepository consultas;
  private final ProductCurrencyScale escala;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public UpdateUserAfftrackRateService(
      UserAfftrackRateRepository escalones,
      UserAfftrackRateQueryRepository consultas,
      ProductCurrencyScale escala,
      AuditWriter auditoria) {
    this(escalones, consultas, escala, auditoria, Clock.systemUTC());
  }

  UpdateUserAfftrackRateService(
      UserAfftrackRateRepository escalones,
      UserAfftrackRateQueryRepository consultas,
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
  public UserAfftrackRateResponse update(UUID id, UpdateUserAfftrackRateRequest peticion) {
    if (peticion.traeInmutables()) {
      String mensaje =
          "La persona, el producto y el inicio de vigencia de una comisión afftrack no se pueden"
              + " corregir.";
      throw new ValidationException(
          "EX-008", mensaje, List.of(new FieldError("userId", "EX-008", mensaje)));
    }
    if (!peticion.informaAlgo()) {
      String mensaje = "Debe enviarse al menos un campo corregible.";
      throw new ValidationException(
          "VAL-006", mensaje, List.of(new FieldError("threshold", "VAL-006", mensaje)));
    }
    Integer limite = limite(peticion);
    BigDecimal valor = valor(peticion);

    UserAfftrackRate escalon =
        escalones
            .findAnyForUpdate(id)
            .filter(e -> !e.estaRetirado())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-006", "La comisión afftrack indicada no existe."));

    boolean corrigeFin = peticion.validTo().presente();
    LocalDate fin = corrigeFin ? peticion.validTo().valor() : escalon.getValidTo();
    if (fin != null && fin.isBefore(escalon.getValidFrom())) {
      String mensaje = "El fin de vigencia no puede ser anterior a su inicio.";
      throw new ValidationException(
          "VAL-004", mensaje, List.of(new FieldError("validTo", "VAL-004", mensaje)));
    }
    escala.verificarImporte(
        escalon.getProductId(), valor, "VAL-005", "amountPerFtd", "El valor por FTD");

    int limiteResultante = limite == null ? escalon.getThreshold() : limite;
    if (escalones.overlaps(
        escalon.getUserId(),
        escalon.getProductId(),
        limiteResultante,
        escalon.getValidFrom(),
        fin,
        escalon.getId())) {
      throw UserAfftrackRateRepository.solapamiento();
    }

    Map<String, Object> cambios =
        escalon.corregir(limite, valor, corrigeFin, fin, OffsetDateTime.now(reloj));
    if (!cambios.isEmpty()) {
      escalones.flushChanges();
      auditoria.recordChange(
          new ChangeEvent(MODULO, ENTIDAD, escalon.getId(), ChangeAction.UPDATE, cambios));
    }
    return consultas
        .findRow(escalon.getId())
        .map(UserAfftrackRateResponse::from)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "EX-006", "La comisión afftrack indicada no existe."));
  }

  private static Integer limite(UpdateUserAfftrackRateRequest peticion) {
    if (!peticion.threshold().presente()) {
      return null;
    }
    BigDecimal limite = peticion.threshold().valor();
    if (limite == null || limite.signum() <= 0 || limite.stripTrailingZeros().scale() > 0) {
      String mensaje = "El límite debe ser un número entero mayor que cero.";
      throw new ValidationException(
          "VAL-002", mensaje, List.of(new FieldError("threshold", "VAL-002", mensaje)));
    }
    return limite.intValueExact();
  }

  private static BigDecimal valor(UpdateUserAfftrackRateRequest peticion) {
    if (!peticion.amountPerFtd().presente()) {
      return null;
    }
    BigDecimal valor = peticion.amountPerFtd().valor();
    if (valor == null || valor.signum() < 0) {
      String mensaje = "El valor por FTD no puede ser negativo ni vaciarse.";
      throw new ValidationException(
          "VAL-003", mensaje, List.of(new FieldError("amountPerFtd", "VAL-003", mensaje)));
    }
    return valor;
  }
}
