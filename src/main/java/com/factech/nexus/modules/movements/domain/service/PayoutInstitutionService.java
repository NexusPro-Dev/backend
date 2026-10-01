package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.PayoutInstitutionResponse;
import com.factech.nexus.modules.movements.application.PayoutRequests;
import com.factech.nexus.modules.movements.domain.models.PayoutInstitutionKind;
import com.factech.nexus.modules.movements.domain.repository.PayoutInstitutionRepository;
import com.factech.nexus.modules.movements.domain.repository.PayoutInstitutionRepository.InstitutionRow;
import com.factech.nexus.modules.system.countries.application.CountryCatalog;
import com.factech.nexus.modules.system.countries.application.CountryCatalog.CountryView;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import com.factech.nexus.shared.error.ValidationException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El catálogo de entidades de cobro (`RN-MV-054`): registrar (`RF-MV-032`), consultar (`RF-MV-033`)
 * y editar (`RF-MV-034`).
 */
@Service
public class PayoutInstitutionService {

  private static final String MODULO = "MV";
  private static final String ENTIDAD = "payout_institutions";
  private static final Pattern CODIGO = Pattern.compile("[A-Z][A-Z0-9_]{1,29}");
  private static final int NOMBRE = 100;

  private final PayoutInstitutionRepository entidades;
  private final CountryCatalog paises;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public PayoutInstitutionService(
      PayoutInstitutionRepository entidades, CountryCatalog paises, AuditWriter auditoria) {
    this(entidades, paises, auditoria, Clock.systemUTC());
  }

  PayoutInstitutionService(
      PayoutInstitutionRepository entidades,
      CountryCatalog paises,
      AuditWriter auditoria,
      Clock reloj) {
    this.entidades = entidades;
    this.paises = paises;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-032` — registrar
  // ---------------------------------------------------------------------------

  @Transactional
  public PayoutInstitutionResponse register(PayoutRequests.RegisterInstitution peticion) {
    // 1. Los cuatro datos, con los errores juntos, antes de consultar nada (`EX-001`).
    String codigo =
        peticion == null || peticion.code() == null
            ? null
            : peticion.code().trim().toUpperCase(Locale.ROOT);
    String nombre = peticion == null || peticion.name() == null ? null : peticion.name().trim();
    Optional<PayoutInstitutionKind> tipo =
        PayoutInstitutionKind.parse(peticion == null ? null : peticion.kind());
    UUID paisId = peticion == null ? null : peticion.countryId();

    List<FieldError> errores = new ArrayList<>();
    if (codigo == null || !CODIGO.matcher(codigo).matches()) {
      errores.add(
          new FieldError(
              "code",
              "VAL-001",
              "El código es obligatorio: de 2 a 30 caracteres, empieza por letra y sigue con"
                  + " letras, dígitos o guion bajo."));
    }
    String problema = problemaDelNombre(nombre);
    if (problema != null) {
      errores.add(new FieldError("name", "VAL-002", problema));
    }
    if (tipo.isEmpty()) {
      errores.add(new FieldError("kind", "VAL-003", "El tipo es BANCO o BILLETERA_MOVIL."));
    }
    if (paisId == null) {
      errores.add(new FieldError("countryId", "VAL-004", "El país es obligatorio."));
    }
    if (!errores.isEmpty()) {
      throw new ValidationException(errores.get(0).code(), errores.get(0).message(), errores);
    }

    // 2. El país: existe (`EX-002`) y está activo (`EX-003`).
    CountryView pais =
        paises
            .find(paisId)
            .orElseThrow(
                () ->
                    new UnprocessableEntityException(
                        "EX-002",
                        "El país indicado no existe.",
                        List.of(
                            new FieldError("countryId", "EX-002", "El país indicado no existe."))));
    if (!pais.active()) {
      String mensaje = "El país " + pais.name() + " está inactivo.";
      throw new BusinessRuleException(
          "EX-003", mensaje, List.of(new FieldError("countryId", "EX-003", mensaje)));
    }

    // 3. El código lo decide el índice único (`EX-004`, `CA-MV-361`).
    UUID id = UUID.randomUUID();
    OffsetDateTime ahora = OffsetDateTime.now(reloj);
    if (!entidades.insert(id, codigo, nombre, tipo.get().name(), pais.id(), ahora)) {
      String mensaje = "Ya existe una entidad de cobro con el código " + codigo + ".";
      throw new BusinessRuleException(
          "EX-004", mensaje, List.of(new FieldError("code", "EX-004", mensaje)));
    }

    Map<String, Object> despues = new LinkedHashMap<>();
    despues.put("code", codigo);
    despues.put("name", nombre);
    despues.put("kind", tipo.get().name());
    despues.put("country_id", pais.id().toString());
    despues.put("is_active", true);
    auditar(id, ChangeAction.CREATE, Map.of("after", despues));

    return respuesta(entidades.find(id).orElseThrow());
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-033` — consultar
  // ---------------------------------------------------------------------------

  @Transactional(readOnly = true)
  public List<PayoutInstitutionResponse> list(String countryId, String kind, String status) {
    List<FieldError> errores = new ArrayList<>();
    UUID pais = null;
    if (countryId != null && !countryId.isBlank()) {
      try {
        pais = UUID.fromString(countryId.trim());
      } catch (IllegalArgumentException e) {
        errores.add(new FieldError("countryId", "VAL-001", "El país no es un identificador."));
      }
    }
    String tipo = null;
    if (kind != null && !kind.isBlank()) {
      Optional<PayoutInstitutionKind> leido = PayoutInstitutionKind.parse(kind);
      if (leido.isEmpty()) {
        errores.add(new FieldError("kind", "VAL-002", "El tipo es BANCO o BILLETERA_MOVIL."));
      } else {
        tipo = leido.get().name();
      }
    }
    Boolean activa = Boolean.TRUE;
    if (status != null && !status.isBlank()) {
      switch (status.trim().toUpperCase(Locale.ROOT)) {
        case "ACTIVA" -> activa = Boolean.TRUE;
        case "INACTIVA" -> activa = Boolean.FALSE;
        case "TODAS" -> activa = null;
        default ->
            errores.add(
                new FieldError("status", "VAL-003", "El estado es ACTIVA, INACTIVA o TODAS."));
      }
    }
    if (!errores.isEmpty()) {
      throw new ValidationException(errores.get(0).code(), errores.get(0).message(), errores);
    }
    return entidades.list(pais, tipo, activa).stream()
        .map(PayoutInstitutionService::respuesta)
        .toList();
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-034` — editar
  // ---------------------------------------------------------------------------

  @Transactional
  public PayoutInstitutionResponse update(UUID id, PayoutRequests.UpdateInstitution peticion) {
    String nombre = peticion == null || peticion.name() == null ? null : peticion.name().trim();
    Boolean activa = peticion == null ? null : peticion.active();
    if (nombre == null && activa == null) {
      throw new ValidationException(
          "VAL-001",
          "Indique el nombre o el estado.",
          List.of(new FieldError("body", "VAL-001", "Indique el nombre o el estado.")));
    }
    if (nombre != null) {
      String problema = problemaDelNombre(nombre);
      if (problema != null) {
        throw new ValidationException(
            "VAL-002", problema, List.of(new FieldError("name", "VAL-002", problema)));
      }
    }

    InstitutionRow actual =
        entidades
            .lock(id)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-002", "No existe una entidad de cobro con ese identificador."));

    String nombreNuevo = nombre == null ? actual.name() : nombre;
    boolean activaNueva = activa == null ? actual.active() : activa;
    if (nombreNuevo.equals(actual.name()) && activaNueva == actual.active()) {
      return respuesta(actual); // `FA-001`
    }

    entidades.update(id, nombreNuevo, activaNueva, OffsetDateTime.now(reloj));

    Map<String, Object> antes = new LinkedHashMap<>();
    Map<String, Object> despues = new LinkedHashMap<>();
    if (!nombreNuevo.equals(actual.name())) {
      antes.put("name", actual.name());
      despues.put("name", nombreNuevo);
    }
    if (activaNueva != actual.active()) {
      antes.put("is_active", actual.active());
      despues.put("is_active", activaNueva);
    }
    Map<String, Object> cambios = new LinkedHashMap<>();
    cambios.put("before", antes);
    cambios.put("after", despues);
    auditar(id, ChangeAction.UPDATE, cambios);

    return respuesta(entidades.find(id).orElseThrow());
  }

  // ---------------------------------------------------------------------------

  private static String problemaDelNombre(String nombre) {
    if (nombre == null || nombre.isEmpty()) {
      return "El nombre es obligatorio.";
    }
    if (nombre.length() > NOMBRE) {
      return "El nombre admite hasta " + NOMBRE + " caracteres.";
    }
    return null;
  }

  private void auditar(UUID id, ChangeAction accion, Map<String, Object> cambios) {
    auditoria.recordChange(new ChangeEvent(MODULO, ENTIDAD, id, accion, cambios));
  }

  static PayoutInstitutionResponse respuesta(InstitutionRow f) {
    return new PayoutInstitutionResponse(
        f.id(),
        f.code(),
        f.name(),
        f.kind(),
        new PayoutInstitutionResponse.Country(f.countryId(), f.countryCode(), f.countryName()),
        f.active(),
        f.createdAt());
  }
}
