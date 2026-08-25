package com.factech.nexus.modules.system.countries.domain.service;

import com.factech.nexus.modules.system.countries.application.CountryResponse;
import com.factech.nexus.modules.system.countries.domain.models.Country;
import com.factech.nexus.modules.system.countries.domain.repository.CountryRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Activa o desactiva un país del catálogo (`RF-SP-022`).
 *
 * <p><b>No hay ninguna regla de negocio que pueda rechazar esta operación</b>, y por eso no hay
 * {@code 409}: ningún país tiene prohibido desactivarse. Es la diferencia con la moneda por
 * defecto, que sí lo tiene, y con el rol de sistema y el rol raíz.
 *
 * <p><b>El cambio de estado de un catálogo se audita en `audit_change_log` y solo ahí.</b> Es el
 * criterio que este requerimiento fijó para todo el módulo: no hay privilegio en juego —un país
 * inactivo deja de ofrecerse, no retira acceso a nadie— y `security.md` §8.1 es un catálogo cerrado
 * de eventos de control de acceso. Es la asimetría con el cambio de estado de un <b>rol</b>, que sí
 * registra en ambos porque un rol inactivo deja de conceder permisos.
 *
 * <p><b>Desactivar no es corregir</b>: el código y el nombre erróneos permanecen, y los datos que
 * ya los referencian siguen resolviéndolos (`CA-SP-181`). Es lo que evita que el error se propague
 * a partir de ese momento, no lo que lo repara.
 */
@Service
public class ChangeCountryStatusService {

  private static final String MODULO = "SP";
  private static final String ENTIDAD = "countries";

  private final CountryRepository paises;
  private final AuditWriter auditoria;
  private final Clock reloj;

  /** Constructor de producción; el segundo existe para que la prueba pueda fijar el reloj. */
  @Autowired
  public ChangeCountryStatusService(CountryRepository paises, AuditWriter auditoria) {
    this(paises, auditoria, Clock.systemUTC());
  }

  ChangeCountryStatusService(CountryRepository paises, AuditWriter auditoria, Clock reloj) {
    this.paises = paises;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public CountryResponse changeStatus(UUID id, boolean activo) {
    Country pais =
        paises
            .findByIdForUpdate(id)
            // 404 y no 422: el recurso DE LA RUTA es el país, y su ausencia es
            // exactamente lo que el 404 significa. No se audita.
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", "No existe un país con ese identificador."));

    boolean anterior = pais.isActive();

    // `FA-001`: si no hubo cambio, NO se emite evento (`CA-SP-182`).
    if (pais.changeStatus(activo, OffsetDateTime.now(reloj))) {
      auditoria.recordChange(
          new ChangeEvent(
              MODULO, ENTIDAD, pais.getId(), ChangeAction.UPDATE, diff(anterior, activo)));
    }

    return CountryResponse.from(pais);
  }

  /**
   * {@code changes} lleva <b>solo</b> {@code is_active}. {@code updated_at} queda fuera por ser
   * consecuencia de la escritura y no un dato que alguien decidiera cambiar.
   */
  private static Map<String, Object> diff(boolean antes, boolean despues) {
    Map<String, Object> cambio = new HashMap<>();
    cambio.put("before", antes);
    cambio.put("after", despues);
    Map<String, Object> changes = new HashMap<>();
    changes.put("is_active", cambio);
    return changes;
  }
}
