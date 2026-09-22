package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.ListSalesRequest;
import com.factech.nexus.modules.movements.application.MovementResponse;
import com.factech.nexus.modules.movements.domain.models.MovementStatus;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.MovementRow;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.SalesFilter;
import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import com.factech.nexus.modules.system.users.application.CommercialReach;
import com.factech.nexus.modules.system.users.application.CommercialReach.Reach;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.pagination.BoundedCount;
import com.factech.nexus.shared.pagination.PageResponse;
import com.factech.nexus.shared.pagination.Pagination;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las ventas de mi alcance (`RF-MV-015`, `RN-MV-031`).
 *
 * <p><b>Es `RF-MV-006` con el alcance puesto por quien pregunta</b>, y la diferencia que importa
 * está en una sola línea: el alcance <b>no se calcula aquí</b>. Lo resuelve `SP` por {@link
 * CommercialReach} —todo, mi red con mi dentro, o solo yo— porque quién manda a quién y de qué tipo
 * es cada rol son suyos, y este servicio lo <b>aplica</b>. Es la primera lectura del sistema que
 * recibe su alcance de un puerto en lugar de deducirlo (`architecture.md` §15.2, `ADR-005`).
 *
 * <p><b>Fuera del alcance se responde vacío SIN CONSULTAR, y no es una optimización: es la
 * regla.</b> Si el {@code userId} pedido no está en mi red —o no soy yo, cuando el alcance es
 * propio—, la respuesta es la página vacía <b>por definición</b> (`plan.md` §4.4): escribirlo como
 * corte y no como predicado deja cerrado el oráculo de la estructura aunque alguien cambie la
 * sentencia. Y es vacío y no {@code 403} ni {@code 404} porque cualquiera de los dos le diría a un
 * vendedor, probando identificadores, quién cuelga de quién (`spec.md` §10).
 *
 * <p>La fila, los vendedores y el conteo acotado son los de {@link ListMovementsService}: la spec
 * decidió que un director y un administrador miran <b>la misma venta</b>.
 */
@Service
public class ListSalesService {

  private final MovementRepository movimientos;
  private final CommercialReach alcance;
  private final AuthenticatedActor actor;
  private final Pagination paginacion;

  public ListSalesService(
      MovementRepository movimientos,
      CommercialReach alcance,
      AuthenticatedActor actor,
      Pagination paginacion) {
    this.movimientos = movimientos;
    this.alcance = alcance;
    this.actor = actor;
    this.paginacion = paginacion;
  }

  @Transactional(readOnly = true)
  public PageResponse<MovementResponse> list(ListSalesRequest peticion) {
    Pagination.Slice pagina = verificar(peticion);

    Reach hastaDonde = alcance.reachOf(actor.id());
    if (!dentroDelAlcance(hastaDonde, peticion.userId())) {
      return PageResponse.de(
          List.of(), BoundedCount.de(0, paginacion.techoDelConteo()), pagina.page(), pagina.size());
    }

    SalesFilter filtro =
        new SalesFilter(
            hastaDonde.kind() == CommercialReach.Kind.EVERYTHING,
            hastaDonde.kind() == CommercialReach.Kind.NETWORK ? hastaDonde.sellers() : null,
            hastaDonde.kind() == CommercialReach.Kind.OWN ? actor.id() : null,
            peticion.userId(),
            peticion.status(),
            peticion.paymentMethodId(),
            peticion.code(),
            peticion.from(),
            peticion.to());

    List<MovementRow> filas = movimientos.findSales(filtro, pagina.offset(), pagina.size());
    BoundedCount total = movimientos.countSales(filtro, paginacion.techoDelConteo());

    Map<UUID, List<MovementResponse.Party>> vendedores =
        ListMovementsService.vendedoresDe(movimientos, filas);
    List<MovementResponse> contenido = new ArrayList<>(filas.size());
    for (MovementRow fila : filas) {
      contenido.add(ListMovementsService.de(fila, vendedores.getOrDefault(fila.id(), List.of())));
    }
    return PageResponse.de(contenido, total, pagina.page(), pagina.size());
  }

  /**
   * Si la persona por la que se acota está dentro del alcance. Sin persona, siempre; con todo el
   * libro, siempre; con la red, si está en ella; con solo yo, si es yo.
   */
  private boolean dentroDelAlcance(Reach hastaDonde, UUID persona) {
    if (persona == null) {
      return true;
    }
    return switch (hastaDonde.kind()) {
      case EVERYTHING -> true;
      case NETWORK -> hastaDonde.sellers().contains(persona);
      case OWN -> persona.equals(actor.id());
    };
  }

  /** Los problemas juntos, como en `RF-MV-006`; el estado contra {@link MovementStatus}. */
  private Pagination.Slice verificar(ListSalesRequest peticion) {
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
}
