package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.SaleLineItem;
import com.factech.nexus.modules.movements.application.SaleLinesRequest;
import com.factech.nexus.modules.movements.domain.models.DeliveryStatus;
import com.factech.nexus.modules.movements.domain.models.MovementStatus;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.SaleLineRow;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.SaleLinesFilter;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.pagination.BoundedCount;
import com.factech.nexus.shared.pagination.PageResponse;
import com.factech.nexus.shared.pagination.Pagination;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las líneas de las ventas, para administración (`RF-MV-017`).
 *
 * <p><b>Responde «qué se ha vendido», que ningún listado de hoy contesta.</b> `RF-MV-006` y
 * `RF-MV-015` devuelven una fila por venta con sus importes agregados: para saber qué productos se
 * vendieron hay que abrir cada venta. Aquí la fila <b>es</b> la línea.
 *
 * <p><b>Este servicio no sabe quién pregunta, y es a propósito</b>, igual que {@link
 * ListMovementsService}: la puerta es {@code movements:list-sale-lines} en el controlador y con él
 * se ve todo el libro. El alcance por estructura de `RN-MV-031` gobierna {@link ListSalesService} y
 * <b>solo</b> eso; darle alcance a este permiso lo convertiría en dos cosas a la vez y un vendedor
 * con él vería la empresa entera.
 *
 * <p><b>Dos sentencias, con una fila o con veinte</b> (`CA-MV-178`): la página —con todo lo que
 * publica dentro, porque la línea tiene un vendedor y no varios— y el conteo acotado.
 */
@Service
public class ListSaleLinesService {

  private final MovementRepository movimientos;
  private final Pagination paginacion;

  public ListSaleLinesService(MovementRepository movimientos, Pagination paginacion) {
    this.movimientos = movimientos;
    this.paginacion = paginacion;
  }

  @Transactional(readOnly = true)
  public PageResponse<SaleLineItem> list(SaleLinesRequest peticion) {
    Pagination.Slice pagina = verificar(peticion);

    SaleLinesFilter filtro =
        new SaleLinesFilter(
            peticion.movementId(),
            peticion.userId(),
            peticion.sellerId(),
            peticion.productId(),
            peticion.status(),
            peticion.deliveryStatus(),
            peticion.typeStatus(),
            peticion.code(),
            peticion.from(),
            peticion.to());

    List<SaleLineRow> filas = movimientos.findSaleLines(filtro, pagina.offset(), pagina.size());
    BoundedCount total = movimientos.countSaleLines(filtro, paginacion.techoDelConteo());

    List<SaleLineItem> contenido = new ArrayList<>(filas.size());
    for (SaleLineRow fila : filas) {
      contenido.add(de(fila));
    }
    return PageResponse.de(contenido, total, pagina.page(), pagina.size());
  }

  /**
   * Los seis problemas se devuelven <b>juntos</b>, como en `RF-MV-006` y `RF-MV-015`: quien
   * escribió mal dos filtros corrige una vez.
   *
   * <p><b>Los estados son error y las personas no</b>, y la asimetría es la que el módulo ya fijó:
   * los estados son dominios <b>cerrados</b> que el sistema declara, de modo que pedir uno
   * inventado es una pregunta mal escrita; preguntar por una persona que no existe es una pregunta
   * legítima con respuesta vacía, y responder `404` convertiría el filtro en un oráculo de quién
   * existe.
   *
   * <p>Los dos se validan contra el enumerado y no contra una lista escrita a mano: un estado nuevo
   * queda admitido sin que nadie tenga que acordarse de este método.
   */
  private Pagination.Slice verificar(SaleLinesRequest peticion) {
    List<FieldError> problemas = new ArrayList<>();

    Pagination.Slice pagina = null;
    try {
      pagina = paginacion.resolver(peticion.page(), peticion.size());
    } catch (ValidationException paginacionInvalida) {
      problemas.addAll(paginacionInvalida.errors());
    }

    if (peticion.status() != null
        && Arrays.stream(MovementStatus.values())
            .noneMatch(valor -> valor.name().equals(peticion.status()))) {
      problemas.add(
          new FieldError(
              "status",
              "VAL-002",
              "El estado '"
                  + peticion.status()
                  + "' no existe. Valores admitidos: "
                  + Arrays.stream(MovementStatus.values()).map(Enum::name).toList()
                  + "."));
    }

    if (peticion.deliveryStatus() != null
        && Arrays.stream(DeliveryStatus.values())
            .noneMatch(valor -> valor.name().equals(peticion.deliveryStatus()))) {
      problemas.add(
          new FieldError(
              "deliveryStatus",
              "VAL-003",
              "El estado de entrega '"
                  + peticion.deliveryStatus()
                  + "' no existe. Valores admitidos: "
                  + Arrays.stream(DeliveryStatus.values()).map(Enum::name).toList()
                  + "."));
    }

    // El estado del tipo (0.2.0), contra el catálogo que `V36` siembra y que no
    // se edita por API. Es el único de los tres que no se valida contra un
    // enumerado: los estados por tipo son FILAS, de modo que preguntarlo al
    // repositorio es lo único que no envejece cuando alguien siembra otro.
    //
    // VIAJA CON `VAL-005`, que es el código que `RF-MV-015` devuelve para este
    // mismo error (`spec.md` §11): al cliente le importa que el mismo filtro mal
    // escrito se llame igual en los dos listados, y en esta especificación el
    // `VAL-005` de la tabla ya lo tenía la paginación, que viaja con el
    // `VAL-003` del sistema.
    if (peticion.typeStatus() != null && !movimientos.existsTypeStatusCode(peticion.typeStatus())) {
      problemas.add(
          new FieldError(
              "typeStatus",
              "VAL-005",
              "El estado del tipo '" + peticion.typeStatus() + "' no existe."));
    }

    if (peticion.from() != null
        && peticion.to() != null
        && peticion.from().isAfter(peticion.to())) {
      problemas.add(
          new FieldError("from", "VAL-004", "La fecha inicial no puede ser posterior a la final."));
    }

    if (!problemas.isEmpty()) {
      throw new ValidationException(
          problemas.get(0).code(),
          problemas.size() == 1
              ? problemas.get(0).message()
              : "La consulta trae " + problemas.size() + " parámetros inválidos.",
          problemas);
    }
    return pagina;
  }

  /**
   * <b>El vendedor viaja presente y NULO</b> cuando la línea no lo tiene (`CA-MV-165`), y no se
   * omite: una clave ausente se leería como que esta versión no registraba el dato.
   */
  private static SaleLineItem de(SaleLineRow fila) {
    return new SaleLineItem(
        fila.lineId(),
        fila.movementId(),
        fila.movementCode(),
        fila.movementStatus(),
        fila.occurredAt(),
        new SaleLineItem.Party(
            fila.clientId(),
            fila.clientUsername(),
            ListMyMovementsService.nombreCompleto(fila.clientFirstName(), fila.clientLastName())),
        fila.sellerId() == null
            ? null
            : new SaleLineItem.Party(
                fila.sellerId(),
                fila.sellerUsername(),
                ListMyMovementsService.nombreCompleto(
                    fila.sellerFirstName(), fila.sellerLastName())),
        new SaleLineItem.ProductRef(fila.productId(), fila.productCode(), fila.productName()),
        fila.quantity(),
        fila.unitPrice(),
        fila.lineDiscount(),
        fila.lineAmount(),
        fila.validityDays(),
        fila.currencyCode(),
        fila.implementation(),
        fila.deliveryStatus(),
        fila.deliveredAt(),
        fila.deliveryNote());
  }
}
