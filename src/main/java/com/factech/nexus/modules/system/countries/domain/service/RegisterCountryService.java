package com.factech.nexus.modules.system.countries.domain.service;

import com.factech.nexus.modules.system.countries.application.CountryResponse;
import com.factech.nexus.modules.system.countries.application.RegisterCountryCommand;
import com.factech.nexus.modules.system.countries.domain.models.Country;
import com.factech.nexus.modules.system.countries.domain.models.CountryCode;
import com.factech.nexus.modules.system.countries.domain.repository.CountryRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta de un país en el catálogo (`RF-SP-020`).
 *
 * <p><b>El código se normaliza antes de todo lo demás.</b> {@code CountryCode} lo recorta y lo pasa
 * a mayúsculas, de modo que la comprobación de unicidad, la validación del formato y lo que acaba
 * persistido operan sobre el mismo valor. Si la normalización viviera en el DTO, cualquier otro
 * camino hacia este servicio la esquivaría.
 *
 * <p><b>La verificación previa de unicidad existe para dar un mensaje preciso</b>, no para
 * garantizarla: la garantía la dan {@code uq_countries_code} y {@code uq_countries_name}, y su
 * violación la traduce el adaptador. La restricción decide; el {@code SELECT} solo redacta — y es
 * lo que resuelve el alta concurrente sin convertirla en un {@code 500}.
 */
@Service
public class RegisterCountryService {

  private static final String MODULO = "SP";
  private static final String ENTIDAD = "countries";

  private final CountryRepository paises;
  private final AuditWriter auditoria;
  private final UuidV7Generator ids;
  private final Clock reloj;

  /** Constructor de producción; el segundo existe para que la prueba pueda fijar el reloj. */
  @Autowired
  public RegisterCountryService(
      CountryRepository paises, AuditWriter auditoria, UuidV7Generator ids) {
    this(paises, auditoria, ids, Clock.systemUTC());
  }

  RegisterCountryService(
      CountryRepository paises, AuditWriter auditoria, UuidV7Generator ids, Clock reloj) {
    this.paises = paises;
    this.auditoria = auditoria;
    this.ids = ids;
    this.reloj = reloj;
  }

  @Transactional
  public CountryResponse register(RegisterCountryCommand comando) {
    CountryCode code = new CountryCode(comando.code());

    verificarUnicidad(code, comando.name());

    Country pais =
        paises.save(Country.create(ids.next(), code, comando.name(), OffsetDateTime.now(reloj)));

    // En la misma transacción que el alta (Art. V.14): si el alta se revierte,
    // su evento también.
    auditoria.recordChange(
        new ChangeEvent(MODULO, ENTIDAD, pais.getId(), ChangeAction.CREATE, estadoInicial(pais)));

    return CountryResponse.from(pais);
  }

  /**
   * `EX-001`.
   *
   * <p>El mensaje del duplicado por nombre <b>incluye el nombre enviado</b>, porque {@code
   * uq_countries_name} compara sobre la forma normalizada: el rechazo puede dispararse contra una
   * fila cuyo nombre no es idéntico al enviado, y sin esa precisión el actor vería rechazado un
   * «Panama» que no encuentra en ninguna parte del catálogo.
   */
  private void verificarUnicidad(CountryCode code, String name) {
    if (paises.existsCode(code)) {
      String mensaje = "Ya existe un país con el código " + code.value() + ".";
      throw new BusinessRuleException(
          "EX-001", mensaje, List.of(new FieldError("code", "EX-001", mensaje)));
    }
    if (paises.existsName(name)) {
      String mensaje =
          "Ya existe un país cuyo nombre coincide con '"
              + name
              + "' sin distinguir mayúsculas ni acentos.";
      throw new BusinessRuleException(
          "EX-001", mensaje, List.of(new FieldError("name", "EX-001", mensaje)));
    }
  }

  /** Estado inicial completo, no un diff con {@code before} en nulo (`architecture.md` §6.6.2). */
  private static Map<String, Object> estadoInicial(Country pais) {
    Map<String, Object> estado = new HashMap<>();
    estado.put("code", pais.getCode() == null ? null : pais.getCode().trim());
    estado.put("name", pais.getName());
    estado.put("is_active", pais.isActive());
    return estado;
  }
}
