package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.MyProductResponse;
import com.factech.nexus.modules.movements.application.MyProductsRequest;
import com.factech.nexus.modules.movements.application.PurchasedProductState;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.MyProductRow;
import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.pagination.PageResponse;
import com.factech.nexus.shared.pagination.Pagination;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los productos que compró quien pregunta, con su estado (`RF-MV-014`).
 *
 * <p>Hereda de {@link ListMyMovementsService} todo lo que importa —el actor sale de la credencial,
 * el alcance va dentro de la sentencia, total exacto— y cambia una cosa: <b>la fila es la línea, no
 * la venta</b>, y el alcance es solo el sujeto: un vendedor no «tiene» lo que colocó.
 *
 * <p><b>El reloj entra en la consulta como parámetro</b>, y es lo que permite probar el vencimiento
 * sin esperar: «vencido» es una comparación contra ahora, y ahora lo pone este servicio.
 */
@Service
public class ListMyProductsService {

  private final MovementRepository movimientos;
  private final AuthenticatedActor actor;
  private final Pagination paginacion;
  private final Clock reloj;

  @Autowired
  public ListMyProductsService(
      MovementRepository movimientos, AuthenticatedActor actor, Pagination paginacion) {
    this(movimientos, actor, paginacion, Clock.systemUTC());
  }

  ListMyProductsService(
      MovementRepository movimientos,
      AuthenticatedActor actor,
      Pagination paginacion,
      Clock reloj) {
    this.movimientos = movimientos;
    this.actor = actor;
    this.paginacion = paginacion;
    this.reloj = reloj;
  }

  @Transactional(readOnly = true)
  public PageResponse<MyProductResponse> list(MyProductsRequest peticion) {
    String estado = validarEstado(peticion.state());
    Pagination.Slice pagina = paginacion.resolver(peticion.page(), peticion.size());
    OffsetDateTime ahora = OffsetDateTime.now(reloj);

    List<MyProductRow> filas =
        movimientos.findMyProducts(actor.id(), estado, ahora, pagina.offset(), pagina.size());
    long total = movimientos.countMyProducts(actor.id(), estado, ahora);

    List<MyProductResponse> contenido = new ArrayList<>(filas.size());
    for (MyProductRow fila : filas) {
      contenido.add(
          new MyProductResponse(
              fila.movementId(),
              fila.movementCode(),
              fila.movementStatus(),
              new MyProductResponse.ProductRef(
                  fila.productId(), fila.productCode(), fila.productName()),
              fila.quantity(),
              fila.implementation(),
              PurchasedProductState.valueOf(fila.state()),
              fila.purchasedAt(),
              fila.deliveredAt(),
              fila.validUntil(),
              fila.deliveryNote()));
    }
    return PageResponse.de(contenido, total, pagina.page(), pagina.size());
  }

  /** `VAL-002`, contra el enumerado y no contra una lista escrita a mano. */
  private static String validarEstado(String estado) {
    if (estado == null) {
      return null;
    }
    boolean valido =
        Arrays.stream(PurchasedProductState.values()).anyMatch(v -> v.name().equals(estado));
    if (valido) {
      return estado;
    }
    String mensaje =
        "El estado '"
            + estado
            + "' no existe. Valores admitidos: "
            + Arrays.stream(PurchasedProductState.values()).map(Enum::name).toList()
            + ".";
    throw new ValidationException(
        "VAL-002", mensaje, List.of(new FieldError("state", "VAL-002", mensaje)));
  }
}
