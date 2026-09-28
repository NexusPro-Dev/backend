package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.MyProductResponse;
import com.factech.nexus.modules.movements.application.MyProductsRequest;
import com.factech.nexus.modules.movements.application.PurchasedProductState;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.MyProductRow;
import com.factech.nexus.modules.products.application.OfferItem;
import com.factech.nexus.modules.products.application.ProductCatalog;
import com.factech.nexus.modules.products.application.ProductLinkResponse;
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

    // Los enlaces de entrega de la pagina en UNA llamada a `PM`, y SOLO de los
    // productos cuyas lineas estan entregadas: al de una linea pendiente ni
    // siquiera se pregunta (`RN-MV-032`, `CA-MV-141`, `CA-MV-142`).
    Map<UUID, List<ProductLinkResponse>> entregados =
        catalogo.deliveredLinksOf(
            filas.stream()
                .filter(f -> PurchasedProductState.valueOf(f.state()).estaEntregado())
                .map(MyProductRow::productId)
                .distinct()
                .toList());

    // El producto en la forma de la oferta, de toda la pagina en UNA llamada a
    // `PM`: pedirlo fila a fila es la N+1 que no se ve.
    Map<UUID, OfferItem> productos =
        catalogo.offerItemsOf(filas.stream().map(MyProductRow::productId).distinct().toList());

    List<MyProductResponse> contenido = new ArrayList<>(filas.size());
    for (MyProductRow fila : filas) {
      contenido.add(aRespuesta(fila, productos, entregados));
    }
    return PageResponse.de(contenido, total, pagina.page(), pagina.size());
  }

  /**
   * Una línea propia, como la trae el listado: la respuesta de activarla (`RF-MV-010`).
   *
   * <p>Sin {@code readOnly}: corre dentro de la transacción de quien activa, y tiene que leer lo
   * que esa transacción acaba de escribir.
   *
   * @throws IllegalStateException si la línea no es del actor; quien llama ya lo comprobó
   */
  @Transactional
  public MyProductResponse get(UUID lineId) {
    MyProductRow fila =
        movimientos
            .findMyProduct(actor.id(), lineId, OffsetDateTime.now(reloj))
            .orElseThrow(() -> new IllegalStateException("La línea " + lineId + " desapareció."));
    Map<UUID, List<ProductLinkResponse>> entregados =
        PurchasedProductState.valueOf(fila.state()).estaEntregado()
            ? catalogo.deliveredLinksOf(List.of(fila.productId()))
            : Map.of();
    return aRespuesta(fila, catalogo.offerItemsOf(List.of(fila.productId())), entregados);
  }

  /**
   * La fila, con <b>la lista de enlaces de su línea</b>: el mismo producto trae todos sus enlaces
   * en una línea entregada y los de la oferta en una pendiente (`CA-MV-285`).
   */
  private static MyProductResponse aRespuesta(
      MyProductRow fila,
      Map<UUID, OfferItem> productos,
      Map<UUID, List<ProductLinkResponse>> entregados) {
    PurchasedProductState estadoLinea = PurchasedProductState.valueOf(fila.state());
    OfferItem producto = productos.get(fila.productId());
    // El mapa solo tiene a los entregados, pero la condicion se repite aqui a
    // proposito: leerla junto a la lista es lo que hace que nadie la pierda al
    // tocar la consulta de arriba.
    if (producto != null && estadoLinea.estaEntregado()) {
      producto = producto.conEnlaces(entregados.getOrDefault(fila.productId(), List.of()));
    }
    return new MyProductResponse(
        fila.lineId(),
        fila.movementId(),
        fila.movementCode(),
        fila.movementStatus(),
        producto,
        fila.productName(),
        fila.quantity(),
        fila.implementation(),
        estadoLinea,
        fila.purchasedAt(),
        fila.deliveredAt(),
        fila.validUntil(),
        fila.deliveryNote());
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
