package com.factech.nexus.modules.system.brokers.domain.service;

import com.factech.nexus.modules.system.brokers.application.BrokerItem;
import com.factech.nexus.modules.system.brokers.application.UpdateBrokerRequest;
import com.factech.nexus.modules.system.brokers.domain.repository.BrokerQueryRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.ValidationException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Editar el enlace y el modelo de un broker (`RF-SP-084`, `RN-SP-039` enmendada).
 *
 * <p>Sobre el patrón de {@code PayoutInstitutionService.update} (`RF-MV-034`): validar, bloquear,
 * escribir solo si algo cambia y auditar el antes y el después de lo que cambió. La diferencia es
 * que aquí {@code ""} <b>vacía</b> el campo —los dos admiten nulo—, mientras que nulo o ausente lo
 * deja como está.
 */
@Service
public class UpdateBrokerService {

  private static final String MODULO = "SP";
  private static final String ENTIDAD = "brokers";
  private static final int URL = 500;
  private static final int MODELO = 120;

  /** La misma expresión que {@code ck_brokers_url}: sin ella el motor respondería {@code 500}. */
  private static final Pattern ENLACE = Pattern.compile("^https?://.*", Pattern.DOTALL);

  private final BrokerQueryRepository brokers;
  private final AuditWriter auditoria;

  public UpdateBrokerService(BrokerQueryRepository brokers, AuditWriter auditoria) {
    this.brokers = brokers;
    this.auditoria = auditoria;
  }

  @Transactional
  public BrokerItem update(UUID id, UpdateBrokerRequest peticion) {
    String url = peticion == null || peticion.url() == null ? null : peticion.url().trim();
    String modelo = peticion == null || peticion.model() == null ? null : peticion.model().trim();
    validar(url, modelo);

    BrokerItem actual =
        brokers
            .lock(id)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "VAL-002", "No existe un broker con ese identificador."));

    // Nulo = no se toca; "" = se vacía.
    String urlNueva = url == null ? actual.url() : vacioANulo(url);
    String modeloNuevo = modelo == null ? actual.model() : vacioANulo(modelo);
    if (Objects.equals(urlNueva, actual.url()) && Objects.equals(modeloNuevo, actual.model())) {
      return actual;
    }

    brokers.updateLinkAndModel(id, urlNueva, modeloNuevo);

    Map<String, Object> antes = new LinkedHashMap<>();
    Map<String, Object> despues = new LinkedHashMap<>();
    if (!Objects.equals(urlNueva, actual.url())) {
      antes.put("url", actual.url());
      despues.put("url", urlNueva);
    }
    if (!Objects.equals(modeloNuevo, actual.model())) {
      antes.put("model", actual.model());
      despues.put("model", modeloNuevo);
    }
    Map<String, Object> cambios = new LinkedHashMap<>();
    cambios.put("before", antes);
    cambios.put("after", despues);
    auditoria.recordChange(new ChangeEvent(MODULO, ENTIDAD, id, ChangeAction.UPDATE, cambios));

    return new BrokerItem(actual.id(), actual.name(), actual.isActive(), urlNueva, modeloNuevo);
  }

  private static void validar(String url, String modelo) {
    if (url == null && modelo == null) {
      String mensaje = "Indique el enlace o el modelo.";
      throw new ValidationException(
          "VAL-001", mensaje, List.of(new FieldError("body", "VAL-001", mensaje)));
    }
    List<FieldError> errores = new ArrayList<>();
    if (url != null && !url.isEmpty()) {
      if (!ENLACE.matcher(url).matches()) {
        errores.add(
            new FieldError("url", "VAL-001", "El enlace debe empezar por http:// o https://."));
      } else if (url.length() > URL) {
        errores.add(
            new FieldError("url", "VAL-001", "El enlace admite hasta " + URL + " caracteres."));
      }
    }
    if (modelo != null && modelo.length() > MODELO) {
      errores.add(
          new FieldError("model", "VAL-001", "El modelo admite hasta " + MODELO + " caracteres."));
    }
    if (!errores.isEmpty()) {
      throw new ValidationException("VAL-001", errores.get(0).message(), errores);
    }
  }

  private static String vacioANulo(String valor) {
    return valor.isEmpty() ? null : valor;
  }
}
