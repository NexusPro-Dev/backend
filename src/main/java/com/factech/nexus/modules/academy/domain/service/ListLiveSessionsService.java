package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.LiveSessionDetailResponse;
import com.factech.nexus.modules.academy.application.LiveSessionRequests.ListFilter;
import com.factech.nexus.modules.academy.application.LiveSessionResponses.Item;
import com.factech.nexus.modules.academy.domain.repository.LiveSessionQueryRepository;
import com.factech.nexus.modules.academy.domain.repository.LiveSessionQueryRepository.LiveSessionFilter;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.pagination.PageResponse;
import com.factech.nexus.shared.pagination.Pagination;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso `RF-AC-042`, `RF-AC-043` y `RF-AC-048`: las clases en vivo para administración y
 * para el instructor.
 *
 * <p>El listado del instructor es el de administración con <b>la propiedad en el predicado</b>
 * (`RN-AC-028`): las clases de los cursos que dicta. <b>Dos sentencias fijas</b>, la página y el
 * total; {@code ended} se calcula con la hora de fin.
 */
@Service
public class ListLiveSessionsService {

  private final LiveSessionSupport apoyo;
  private final LiveSessionQueryRepository consultas;
  private final Pagination paginacion;

  public ListLiveSessionsService(
      LiveSessionSupport apoyo, LiveSessionQueryRepository consultas, Pagination paginacion) {
    this.apoyo = apoyo;
    this.consultas = consultas;
    this.paginacion = paginacion;
  }

  @Transactional(readOnly = true)
  public PageResponse<Item> list(ListFilter filtros) {
    return listar(filtros, null);
  }

  @Transactional(readOnly = true)
  public PageResponse<Item> listOwn(ListFilter filtros) {
    return listar(filtros, apoyo.actorId());
  }

  @Transactional(readOnly = true)
  public LiveSessionDetailResponse get(UUID id) {
    return apoyo.detalle(id);
  }

  private PageResponse<Item> listar(ListFilter filtros, UUID instructor) {
    Pagination.Slice trozo = paginacion.resolver(filtros.page(), filtros.size());
    String estado =
        filtros.status() == null || filtros.status().isBlank() ? null : filtros.status().trim();
    if (estado != null && !estado.equals("PROGRAMADA") && !estado.equals("CANCELADA")) {
      String mensaje = "El estado debe ser PROGRAMADA o CANCELADA.";
      throw new ValidationException(
          "VAL-003", mensaje, List.of(new FieldError("status", "VAL-003", mensaje)));
    }
    Instant ahora = apoyo.ahora();
    LiveSessionFilter filtro =
        new LiveSessionFilter(
            instructor,
            filtros.courseId(),
            estado,
            filtros.ended(),
            filtros.from(),
            filtros.to(),
            ahora);
    long total = consultas.count(filtro);
    List<Item> pagina =
        total == 0
            ? List.of()
            : consultas.search(filtro, trozo.offset(), trozo.size()).stream()
                .map(fila -> Item.from(fila, !ahora.isBefore(fila.endsAt().toInstant())))
                .toList();
    return PageResponse.de(pagina, total, trozo.page(), trozo.size());
  }
}
