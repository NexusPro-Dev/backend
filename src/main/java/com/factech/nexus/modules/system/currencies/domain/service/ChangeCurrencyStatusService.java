package com.factech.nexus.modules.system.currencies.domain.service;

import com.factech.nexus.modules.system.currencies.application.CurrencyResponse;
import com.factech.nexus.modules.system.currencies.domain.models.Currency;
import com.factech.nexus.modules.system.currencies.domain.repository.CurrencyRepository;
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
 * Activa o desactiva una moneda del catálogo (`RF-SP-023`).
 *
 * <p><b>Orden de verificación</b> (`plan.md` §4): identificador y cuerpo —que resuelve el
 * framework—, moneda existente <b>cargada con bloqueo de fila</b>, regla `RN-SP-010` si se pide
 * desactivar, y aplicación del estado. El tercer paso va después de cargar y antes de aplicar: no
 * puede evaluarse sin la fila, y no debe evaluarse después de haberla modificado.
 *
 * <p><b>El cambio de estado de un catálogo se audita en `audit_change_log` y solo ahí</b>, que es
 * el criterio que `RF-SP-022` fijó para todo el módulo. No hay privilegio en juego —una moneda
 * inactiva deja de ofrecerse, no retira acceso a nadie— y `security.md` §8.1 es un catálogo cerrado
 * de eventos de control de acceso. Es la asimetría con `RF-SP-007`, que sí registra en ambos porque
 * un rol inactivo deja de conceder permisos.
 */
@Service
public class ChangeCurrencyStatusService {

  private static final String MODULO = "SP";
  private static final String ENTIDAD = "currencies";

  private final CurrencyRepository monedas;
  private final AuditWriter auditoria;
  private final Clock reloj;

  /** Constructor de producción; el segundo existe para que la prueba pueda fijar el reloj. */
  @Autowired
  public ChangeCurrencyStatusService(CurrencyRepository monedas, AuditWriter auditoria) {
    this(monedas, auditoria, Clock.systemUTC());
  }

  ChangeCurrencyStatusService(CurrencyRepository monedas, AuditWriter auditoria, Clock reloj) {
    this.monedas = monedas;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public CurrencyResponse changeStatus(UUID id, boolean activa) {
    Currency moneda =
        monedas
            .findByIdForUpdate(id)
            // 404 y no 422: lo que no existe es el recurso DE LA RUTA. No se
            // audita — `architecture.md` §6.6.4 lo deja fuera y
            // `ck_audit_error_log_status` lo impediría en el esquema.
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-002", "No existe una moneda con ese identificador."));

    boolean anterior = moneda.isActive();

    // `FA-001`: si no hubo cambio, NO se emite evento (`CA-SP-190`). Es la razón
    // de que el agregado devuelva si cambió en lugar de no devolver nada.
    if (moneda.changeStatus(activa, OffsetDateTime.now(reloj))) {
      auditoria.recordChange(
          new ChangeEvent(
              MODULO, ENTIDAD, moneda.getId(), ChangeAction.UPDATE, diff(anterior, activa)));
    }

    return CurrencyResponse.from(moneda);
  }

  /**
   * {@code changes} lleva <b>solo</b> {@code is_active}, no la moneda entera, y {@code updated_at}
   * queda fuera por ser consecuencia de la escritura y no un dato que alguien decidiera cambiar.
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
