package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.domain.models.DeliveryStatus;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.DeliveryLineRow;
import com.factech.nexus.modules.system.users.application.CurrentMembershipLookup;
import com.factech.nexus.modules.system.users.application.CurrentMembershipLookup.CurrentMembershipView;
import com.factech.nexus.modules.system.users.application.MembershipGrant;
import com.factech.nexus.modules.system.users.application.MembershipGrant.GrantOrder;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Entregar <b>una línea</b>: lo que confirmar hace con una automática (`RF-MV-003`) y activar con
 * una manual (`RF-MV-010`).
 *
 * <p><b>Vive aparte para que la regla de no bajar de nivel se escriba una vez</b>. Hasta el
 * 28-09-2026 estaba dentro de {@link ConfirmSaleService}, que era el único que entregaba; con dos
 * operaciones que entregan, copiarla habría dado dos ocasiones de que diverjan en el sentido que
 * <b>no falla, entrega</b>.
 *
 * <p><b>No decide SI se entrega</b> —que la línea sea automática, que la venta esté confirmada, que
 * quien activa sea el comprador—: eso lo resuelve quien llama. Aquí se decide <b>cómo</b>: retener
 * si baja de nivel, y si no, conceder por la escritura de `SP` y marcar la línea entregada.
 *
 * <p>Sin {@code @Transactional} propio: corre dentro de la transacción de quien llama, que es la
 * que tiene que deshacerlo todo si conceder falla.
 */
@Component
public class LineDelivery {

  private final MovementRepository movimientos;
  private final CurrentMembershipLookup membresias;
  private final MembershipGrant concesion;

  public LineDelivery(
      MovementRepository movimientos,
      CurrentMembershipLookup membresias,
      MembershipGrant concesion) {
    this.movimientos = movimientos;
    this.membresias = membresias;
    this.concesion = concesion;
  }

  /**
   * Entrega la línea a {@code sujeto} en {@code ahora}, o la retiene si es un upgrade que bajaría
   * de nivel (`RN-MV-029`).
   *
   * @return lo que se decidió, para el asiento de auditoría
   */
  public Map<String, Object> deliver(DeliveryLineRow linea, UUID sujeto, OffsetDateTime ahora) {
    Map<String, Object> asiento = new LinkedHashMap<>();
    asiento.put("product_code", linea.productCode());

    if (linea.upgrade()) {
      Optional<String> motivo = motivoParaRetener(linea, sujeto);
      if (motivo.isPresent()) {
        movimientos.markRetained(linea.lineId(), motivo.get());
        asiento.put("delivery_status", DeliveryStatus.RETENIDA.name());
        asiento.put("delivery_note", motivo.get());
        return asiento;
      }
      asiento.put("membership_code", linea.targetMembershipCode());
    }

    // TODA LÍNEA QUE SE ENTREGA DEJA ESCRITO LO QUE LA PERSONA PASA A TENER
    // (`RN-MV-036`), y no solo las de upgrade: hasta el 23-09-2026 un bot
    // entregado no dejaba constancia de posesión en ninguna parte, de modo que el
    // sistema sabía qué se le había vendido a alguien y no qué tenía.
    //
    // La membresía va SOLO si el producto la concede; con ella nula, la escritura
    // publicada anota la posesión y no toca el nivel de nadie. Y la vigencia es la
    // copiada en la línea, contada DESDE LA ENTREGA: la confirmación para lo
    // automático (`RN-MV-020`), la activación para lo manual (`RN-MV-048`).
    //
    // La línea viaja dentro de la orden y va ÚNICA en el esquema: es lo que hace
    // idempotente la entrega, sin que este componente tenga que comprobar antes si
    // ya la entregó — comprobarlo sería una carrera.
    concesion.grant(
        new GrantOrder(
            sujeto,
            linea.productId(),
            linea.upgrade() ? linea.targetMembershipId() : null,
            linea.lineId(),
            linea.validityDays(),
            ahora));

    movimientos.markDelivered(linea.lineId(), ahora);
    asiento.put("delivery_status", DeliveryStatus.ENTREGADA.name());
    return asiento;
  }

  /**
   * `RN-MV-029`: <b>entregar no baja de nivel a nadie</b>. Si la membresía comprada es inferior a
   * la vigente en este instante, la línea se retiene y el motivo se escribe para una persona.
   *
   * <p>Renovar el <b>mismo</b> nivel concede. Sin membresía vigente —la suya venció— no hay nada
   * por debajo de lo que bajar, y concede. La cadena crece hacia abajo: {@code 1} es la cima, de
   * modo que <b>inferior es número mayor</b> (`requirements/sp.md` §10.4), igual que en {@code
   * SaleRules.verificarQueSube}.
   */
  private Optional<String> motivoParaRetener(DeliveryLineRow linea, UUID sujeto) {
    if (linea.targetMembershipId() == null || linea.targetMembershipLevel() == null) {
      // Un upgrade sin destino no debería existir; si llega aquí, algo se rompió
      // antes, y entregarlo a ciegas sería peor que retenerlo con constancia.
      return Optional.of("El producto ya no declara la membresía que concede.");
    }
    Optional<CurrentMembershipView> vigente = membresias.currentMembershipOf(sujeto);
    if (vigente.isEmpty() || linea.targetMembershipLevel() <= vigente.get().level()) {
      return Optional.empty();
    }
    return Optional.of(
        "La membresía comprada (%s) es inferior a la vigente (%s)."
            .formatted(linea.targetMembershipCode(), vigente.get().code()));
  }
}
