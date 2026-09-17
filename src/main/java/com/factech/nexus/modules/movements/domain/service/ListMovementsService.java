package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.ListMovementsRequest;
import com.factech.nexus.modules.movements.application.MovementResponse;
import com.factech.nexus.modules.movements.domain.models.MovementStatus;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.MovementFilter;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.MovementRow;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.MovementSellerRow;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.pagination.BoundedCount;
import com.factech.nexus.shared.pagination.PageResponse;
import com.factech.nexus.shared.pagination.Pagination;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Todos los movimientos del libro, acotados por lo que se pida (`RF-MV-006`).
 *
 * <p><b>Este servicio no sabe quién pregunta, y es a propósito.</b> Al revés que {@link
 * ListMyMovementsService}, no resuelve al actor: la puerta es {@code movements:read} en el
 * controlador, y desde aquí el sujeto y el vendedor son <b>filtros</b> que llegan en la petición.
 * Lo que se decide en este servicio es qué peticiones están mal escritas y cómo se cuenta.
 *
 * <p><b>El total es acotado</b> —{@link BoundedCount}, el de los cuatro registros de auditoría— y
 * no exacto como en el listado propio: allí se cuenta el conjunto de una persona; aquí, la tabla
 * entera, y contarla exacta en cada página es un recorrido completo que crece con el libro.
 */
@Service
public class ListMovementsService {

  private final MovementRepository movimientos;
  private final Pagination paginacion;

  public ListMovementsService(MovementRepository movimientos, Pagination paginacion) {
    this.movimientos = movimientos;
    this.paginacion = paginacion;
  }

  @Transactional(readOnly = true)
  public PageResponse<MovementResponse> list(ListMovementsRequest peticion) {
    Pagination.Slice pagina = verificar(peticion);

    MovementFilter filtro =
        new MovementFilter(
            peticion.status(),
            peticion.userId(),
            peticion.sellerId(),
            peticion.paymentMethodId(),
            peticion.code(),
            peticion.from(),
            peticion.to());

    List<MovementRow> filas = movimientos.findAll(filtro, pagina.offset(), pagina.size());
    BoundedCount total = movimientos.countAll(filtro, paginacion.techoDelConteo());

    Map<UUID, List<MovementResponse.Party>> vendedores = vendedoresDe(filas);
    List<MovementResponse> contenido = new ArrayList<>(filas.size());
    for (MovementRow fila : filas) {
      contenido.add(de(fila, vendedores.getOrDefault(fila.id(), List.of())));
    }
    return PageResponse.de(contenido, total, pagina.page(), pagina.size());
  }

  /**
   * Los problemas se devuelven <b>juntos</b>, como en los listados de `SP` y de auditoría: quien
   * escribió mal tres parámetros no tiene que corregir la petición tres veces.
   *
   * <p>El estado se valida contra {@link MovementStatus} y no contra una lista escrita a mano: un
   * estado nuevo queda admitido sin que nadie tenga que acordarse. Y es un <b>error</b> y no una
   * página vacía, al revés que un sujeto o un método inexistentes: los estados son un dominio
   * cerrado que el sistema declara, y pedir uno inventado es una pregunta mal escrita.
   */
  private Pagination.Slice verificar(ListMovementsRequest peticion) {
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
      String mensaje =
          "El estado '"
              + peticion.status()
              + "' no existe. Valores admitidos: "
              + Arrays.stream(MovementStatus.values()).map(Enum::name).toList()
              + ".";
      problemas.add(new FieldError("status", "VAL-002", mensaje));
    }

    if (peticion.from() != null
        && peticion.to() != null
        && peticion.from().isAfter(peticion.to())) {
      String mensaje = "La fecha inicial no puede ser posterior a la final.";
      problemas.add(new FieldError("from", "VAL-004", mensaje));
    }

    if (!problemas.isEmpty()) {
      throw new ValidationException(
          problemas.get(0).code(), "La consulta solicitada no es válida.", problemas);
    }
    return pagina;
  }

  /** Los vendedores de la página, por movimiento y sin repetir: la misma segunda consulta. */
  private Map<UUID, List<MovementResponse.Party>> vendedoresDe(List<MovementRow> filas) {
    List<UUID> ids = new ArrayList<>(filas.size());
    for (MovementRow fila : filas) {
      ids.add(fila.id());
    }
    Map<UUID, List<MovementResponse.Party>> porMovimiento = new LinkedHashMap<>();
    for (MovementSellerRow vendedor : movimientos.findSellersOf(ids)) {
      porMovimiento
          .computeIfAbsent(vendedor.movementId(), id -> new ArrayList<>())
          .add(
              new MovementResponse.Party(
                  vendedor.sellerId(),
                  vendedor.username(),
                  ListMyMovementsService.nombreCompleto(
                      vendedor.firstName(), vendedor.lastName())));
    }
    return porMovimiento;
  }

  private static MovementResponse de(MovementRow fila, List<MovementResponse.Party> vendedores) {
    return new MovementResponse(
        fila.id(),
        fila.code(),
        fila.type(),
        fila.status(),
        new MovementResponse.Party(
            fila.userId(),
            fila.userUsername(),
            ListMyMovementsService.nombreCompleto(fila.userFirstName(), fila.userLastName())),
        // VACÍA Y PRESENTE cuando el movimiento no tiene vendedor.
        List.copyOf(vendedores),
        new MovementResponse.Money(fila.currencyId(), fila.currencyCode()),
        fila.paymentMethod(),
        fila.totalAmount(),
        fila.discountAmount(),
        fila.payableAmount(),
        fila.occurredAt(),
        // NULO Y PRESENTE en todo lo que no está confirmado (`RN-MV-004`).
        fila.confirmedAt());
  }
}
