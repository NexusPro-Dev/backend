package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.MovementRole;
import com.factech.nexus.modules.movements.application.MyMovementResponse;
import com.factech.nexus.modules.movements.application.MyMovementsRequest;
import com.factech.nexus.modules.movements.domain.models.MovementStatus;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.MovementSellerRow;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.MyMovementRow;
import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.pagination.PageResponse;
import com.factech.nexus.shared.pagination.Pagination;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los movimientos en los que participa quien pregunta (`RF-MV-008`).
 *
 * <p><b>El identificador de esa persona no entra por la petición</b>, y ahí está el requerimiento:
 * sale de {@link AuthenticatedActor}, el mismo componente que resuelve el actor en `RF-PM-007` y
 * `RF-SP-039`. Un parámetro que dijera sobre quién sería el agujero que esta operación existe para
 * no abrir; consultar las de un tercero es `RF-MV-006`, con su permiso.
 *
 * <p><b>El alcance lo aplica la sentencia</b> y no este servicio. Traer de más y descartar aquí
 * haría que el total contase movimientos ajenos, y dejaría el filtro en un sitio donde moverlo no
 * rompe nada visible.
 */
@Service
public class ListMyMovementsService {

  private final MovementRepository movimientos;
  private final AuthenticatedActor actor;
  private final Pagination paginacion;

  public ListMyMovementsService(
      MovementRepository movimientos, AuthenticatedActor actor, Pagination paginacion) {
    this.movimientos = movimientos;
    this.actor = actor;
    this.paginacion = paginacion;
  }

  @Transactional(readOnly = true)
  public PageResponse<MyMovementResponse> list(MyMovementsRequest peticion) {
    String estado = validarEstado(peticion.status());
    Pagination.Slice pagina = paginacion.resolver(peticion.page(), peticion.size());

    List<MyMovementRow> filas =
        movimientos.findMine(actor.id(), estado, pagina.offset(), pagina.size());

    // EL TOTAL ES EXACTO, y no el conteo acotado de los listados de auditoría:
    // esto es el conjunto de UNA persona, no una tabla que crezca sin límite.
    long total = movimientos.countMine(actor.id(), estado);

    Map<UUID, List<MyMovementResponse.Party>> vendedores = vendedoresDe(filas);
    List<MyMovementResponse> contenido = new ArrayList<>(filas.size());
    for (MyMovementRow fila : filas) {
      contenido.add(de(fila, vendedores.getOrDefault(fila.id(), List.of())));
    }
    return PageResponse.de(contenido, total, pagina.page(), pagina.size());
  }

  /**
   * `VAL-003`. Se valida contra el dominio y no contra una lista escrita a mano: un estado nuevo en
   * {@link MovementStatus} queda admitido aquí sin que nadie tenga que acordarse.
   */
  private static String validarEstado(String estado) {
    if (estado == null) {
      return null;
    }
    for (MovementStatus valor : MovementStatus.values()) {
      if (valor.name().equals(estado)) {
        return estado;
      }
    }
    String mensaje = "El estado indicado no existe.";
    throw new ValidationException(
        "VAL-003", mensaje, List.of(new FieldError("status", "VAL-003", mensaje)));
  }

  /**
   * Los vendedores de la página, por movimiento y sin repetir: una segunda consulta y no un
   * agregado en la paginada, para que esta siga devolviendo una fila por movimiento.
   */
  private Map<UUID, List<MyMovementResponse.Party>> vendedoresDe(List<MyMovementRow> filas) {
    List<UUID> ids = new ArrayList<>(filas.size());
    for (MyMovementRow fila : filas) {
      ids.add(fila.id());
    }
    Map<UUID, List<MyMovementResponse.Party>> porMovimiento = new LinkedHashMap<>();
    for (MovementSellerRow vendedor : movimientos.findSellersOf(ids)) {
      porMovimiento
          .computeIfAbsent(vendedor.movementId(), id -> new ArrayList<>())
          .add(
              new MyMovementResponse.Party(
                  vendedor.sellerId(),
                  vendedor.username(),
                  nombreCompleto(vendedor.firstName(), vendedor.lastName())));
    }
    return porMovimiento;
  }

  static MyMovementResponse de(MyMovementRow fila, List<MyMovementResponse.Party> vendedores) {
    return new MyMovementResponse(
        fila.id(),
        fila.code(),
        fila.status(),
        MovementRole.valueOf(fila.role()),
        new MyMovementResponse.Party(
            fila.userId(),
            fila.userUsername(),
            nombreCompleto(fila.userFirstName(), fila.userLastName())),
        // VACÍA Y PRESENTE cuando el movimiento no tiene vendedor (`FA-003`),
        // que desde el 16-09-2026 no es el caso de ninguna venta.
        List.copyOf(vendedores),
        new MyMovementResponse.Money(fila.currencyId(), fila.currencyCode()),
        fila.paymentMethod(),
        fila.totalAmount(),
        fila.discountAmount(),
        fila.payableAmount(),
        fila.occurredAt());
  }

  static String nombreCompleto(String nombre, String apellido) {
    String completo =
        ((nombre == null ? "" : nombre) + " " + (apellido == null ? "" : apellido)).trim();
    return completo.isEmpty() ? null : completo;
  }
}
