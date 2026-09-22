package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.MyProductResponse;
import com.factech.nexus.modules.movements.application.MyProductsRequest;
import com.factech.nexus.modules.movements.application.PurchasedProductState;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.MyProductRow;
import com.factech.nexus.modules.products.application.ProductCatalog;
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
import java.util.Map;
import java.util.UUID;
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
  private final ProductCatalog catalogo;
  private final Clock reloj;

  @Autowired
  public ListMyProductsService(
      MovementRepository movimientos,
      AuthenticatedActor actor,
      Pagination paginacion,
      ProductCatalog catalogo) {
    this(movimientos, actor, paginacion, catalogo, Clock.systemUTC());
  }

  ListMyProductsService(
      MovementRepository movimientos,
      AuthenticatedActor actor,
      Pagination paginacion,
      ProductCatalog catalogo,
      Clock reloj) {
    this.movimientos = movimientos;
    this.actor = actor;
    this.paginacion = paginacion;
    this.catalogo = catalogo;
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

    // Los cupones de la pagina en UNA llamada a `PM`, y SOLO de los productos
    // cuyas lineas estan entregadas: al de una linea pendiente ni siquiera se
    // pregunta, de modo que no hay nada que filtrar despues (`RN-MV-032`,
    // `CA-MV-141`, `CA-MV-142`).
    Map<UUID, String> cupones =
        catalogo.couponLinksOf(
            filas.stream()
                .filter(f -> PurchasedProductState.valueOf(f.state()).estaEntregado())
                .map(MyProductRow::productId)
                .distinct()
                .toList());

    List<MyProductResponse> contenido = new ArrayList<>(filas.size());
    for (MyProductRow fila : filas) {
      PurchasedProductState estadoLinea = PurchasedProductState.valueOf(fila.state());
      contenido.add(
          new MyProductResponse(
              fila.movementId(),
              fila.movementCode(),
              fila.movementStatus(),
              new MyProductResponse.ProductRef(
                  fila.productId(), fila.productCode(), fila.productName()),
              fila.quantity(),
              fila.implementation(),
              estadoLinea,
              fila.purchasedAt(),
              fila.deliveredAt(),
              fila.validUntil(),
              fila.deliveryNote(),
              // El mapa solo tiene a los entregados, pero la condicion se repite
              // aqui a proposito: leerla junto al campo es lo que hace que
              // nadie la pierda al tocar la linea de arriba.
              estadoLinea.estaEntregado() ? cupones.get(fila.productId()) : null));
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
