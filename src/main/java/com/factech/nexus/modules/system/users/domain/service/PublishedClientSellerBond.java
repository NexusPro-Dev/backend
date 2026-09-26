package com.factech.nexus.modules.system.users.domain.service;

import com.factech.nexus.modules.system.users.application.ClientSellerBond;
import com.factech.nexus.modules.system.users.domain.repository.ClientSellerRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * La segunda escritura publicada de `SP`: vincular al cliente con el vendedor del enlace
 * (`RF-MV-011`, `RN-MV-025`, `RN-SP-049`).
 *
 * <p><b>{@code MANDATORY} y no {@code REQUIRED}</b>, por lo mismo que {@code
 * PublishedMembershipGrant}: la norma de `architecture.md` §15.2.1 dice que una escritura publicada
 * se une a la transacción del que llama, y con {@code REQUIRED} eso sería una convención que un
 * caso de uso sin {@code @Transactional} rompería sin que nada avisara — el vínculo quedaría
 * confirmado en su propia transacción y la venta podría deshacerse después, dejando a un cliente
 * atribuido a un vendedor por una compra que no existe.
 *
 * <p><b>No comprueba nada antes de escribir</b>, y esa ausencia es deliberada: ni que el cliente
 * exista, ni que el vendedor exista, ni que el vínculo esté. Lo primero y lo segundo los garantizan
 * las claves foráneas —y llegar aquí con un identificador inventado es un fallo del sistema, no un
 * {@code 4xx}—; lo tercero lo resuelve la clave primaria. Una comprobación previa solo añadiría una
 * carrera.
 */
@Service
public class PublishedClientSellerBond implements ClientSellerBond {

  private static final String MODULO = "SP";
  private static final String ENTIDAD = "client_sellers";

  private final ClientSellerRepository vinculos;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public PublishedClientSellerBond(ClientSellerRepository vinculos, AuditWriter auditoria) {
    this(vinculos, auditoria, Clock.systemUTC());
  }

  PublishedClientSellerBond(ClientSellerRepository vinculos, AuditWriter auditoria, Clock reloj) {
    this.vinculos = vinculos;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean bind(BondOrder orden) {
    OffsetDateTime ahora = OffsetDateTime.now(reloj);

    boolean nacio =
        vinculos.attachByHotlink(orden.clientId(), orden.sellerId(), orden.movementId(), ahora);

    // EL ASIENTO SOLO SI NACIÓ. Auditar un INSERT que no insertó nada llenaría
    // el registro de hechos que no ocurrieron, y haría indistinguible «este
    // cliente pasó a ser de este vendedor» de «volvió a comprarle».
    if (nacio) {
      Map<String, Object> cambios = new LinkedHashMap<>();
      cambios.put("before", null);
      Map<String, Object> despues = new LinkedHashMap<>();
      despues.put("seller_id", String.valueOf(orden.sellerId()));
      despues.put("origin", "HOTLINK");
      despues.put("first_movement_id", String.valueOf(orden.movementId()));
      cambios.put("after", despues);
      auditoria.recordChange(
          new ChangeEvent(MODULO, ENTIDAD, orden.clientId(), ChangeAction.CREATE, cambios));
    }

    return nacio;
  }
}
