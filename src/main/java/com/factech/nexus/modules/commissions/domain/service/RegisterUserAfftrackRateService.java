package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.RegisterUserAfftrackRateRequest;
import com.factech.nexus.modules.commissions.application.UserAfftrackRateResponse;
import com.factech.nexus.modules.commissions.domain.models.UserAfftrackRate;
import com.factech.nexus.modules.commissions.domain.repository.UserAfftrackRateQueryRepository;
import com.factech.nexus.modules.commissions.domain.repository.UserAfftrackRateRepository;
import com.factech.nexus.modules.products.application.ProductCatalog.ProductView;
import com.factech.nexus.modules.system.users.application.UserCatalog;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
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
 * `RF-CM-019` — registrar un escalón afftrack de una persona sobre un producto FTD, con vigencia.
 *
 * <p>La persona existe; el producto existe, no está retirado y es FTD (`RN-CM-037`); el valor cabe
 * en la moneda del producto (`RN-CM-038`); y ningún día choca con otro escalón vivo de la misma
 * persona, producto y límite (`RN-CM-039`), que el {@code EXCLUDE} cierra aunque aquí no se
 * comprobara. <b>No se exige que la persona venda</b>, como en `RF-CM-006` `FA-004`.
 */
@Service
public class RegisterUserAfftrackRateService {

  private static final String MODULO = "CM";
  private static final String ENTIDAD = "user_afftrack_rates";

  private final UserAfftrackRateRepository escalones;
  private final UserAfftrackRateQueryRepository consultas;
  private final UserCatalog personas;
  private final AfftrackProductCheck producto;
  private final ProductCurrencyScale escala;
  private final AuditWriter auditoria;
  private final UuidV7Generator ids;
  private final Clock reloj;

  @Autowired
  public RegisterUserAfftrackRateService(
      UserAfftrackRateRepository escalones,
      UserAfftrackRateQueryRepository consultas,
      UserCatalog personas,
      AfftrackProductCheck producto,
      ProductCurrencyScale escala,
      AuditWriter auditoria,
      UuidV7Generator ids) {
    this(escalones, consultas, personas, producto, escala, auditoria, ids, Clock.systemUTC());
  }

  RegisterUserAfftrackRateService(
      UserAfftrackRateRepository escalones,
      UserAfftrackRateQueryRepository consultas,
      UserCatalog personas,
      AfftrackProductCheck producto,
      ProductCurrencyScale escala,
      AuditWriter auditoria,
      UuidV7Generator ids,
      Clock reloj) {
    this.escalones = escalones;
    this.consultas = consultas;
    this.personas = personas;
    this.producto = producto;
    this.escala = escala;
    this.auditoria = auditoria;
    this.ids = ids;
    this.reloj = reloj;
  }

  @Transactional
  public UserAfftrackRateResponse register(RegisterUserAfftrackRateRequest peticion) {
    if (peticion.validTo() != null && peticion.validTo().isBefore(peticion.validFrom())) {
      String mensaje = "El fin de vigencia no puede ser anterior a su inicio.";
      throw new ValidationException(
          "VAL-004", mensaje, List.of(new FieldError("validTo", "VAL-004", mensaje)));
    }
    if (personas.find(peticion.userId()).isEmpty()) {
      String mensaje = "La persona indicada no existe.";
      throw new UnprocessableEntityException(
          "EX-001", mensaje, List.of(new FieldError("userId", "EX-001", mensaje)));
    }
    ProductView ftd = producto.verificar(peticion.productId());
    escala.verificarImporte(
        ftd.id(), peticion.amountPerFtd(), "VAL-005", "amountPerFtd", "El valor por FTD");

    int limite = peticion.limite();
    if (escalones.overlaps(
        peticion.userId(), ftd.id(), limite, peticion.validFrom(), peticion.validTo(), null)) {
      throw UserAfftrackRateRepository.solapamiento();
    }

    UserAfftrackRate nuevo =
        escalones.save(
            UserAfftrackRate.create(
                ids.next(),
                peticion.userId(),
                ftd.id(),
                limite,
                peticion.amountPerFtd(),
                peticion.validFrom(),
                peticion.validTo(),
                OffsetDateTime.now(reloj)));

    auditoria.recordChange(
        new ChangeEvent(MODULO, ENTIDAD, nuevo.getId(), ChangeAction.CREATE, nuevo.instantanea()));

    return consultas
        .findRow(nuevo.getId())
        .map(UserAfftrackRateResponse::from)
        .orElseThrow(() -> new IllegalStateException("El escalón recién creado no se lee."));
  }
}
