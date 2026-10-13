package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.LiveSessionDetailResponse;
import com.factech.nexus.modules.academy.application.LiveSessionDetailResponse.LiveSessionInstructorRef;
import com.factech.nexus.modules.academy.application.LiveSessionRequests.ListFilter;
import com.factech.nexus.modules.academy.application.LiveSessionResponses.Item;
import com.factech.nexus.modules.academy.domain.repository.LiveSessionQueryRepository;
import com.factech.nexus.modules.academy.domain.repository.LiveSessionQueryRepository.LiveSessionFilter;
import com.factech.nexus.modules.academy.domain.repository.LiveSessionQueryRepository.LiveSessionItemRow;
import com.factech.nexus.modules.academy.domain.repository.LiveSessionQueryRepository.LiveSessionRow;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.pagination.PageResponse;
import com.factech.nexus.shared.pagination.Pagination;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

  /** `RF-AC-056`: el detalle, solo de una clase de la que es instructor; lo ajeno, `404`. */
  @Transactional(readOnly = true)
  public LiveSessionDetailResponse getOwn(UUID id) {
    LiveSessionRow clase = consultas.find(id).orElseThrow(LiveSessionSupport::noExiste);
    apoyo.exigirPropia(clase, apoyo.actorId());
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
            // Lo propio manda sobre el filtro: el instructor ve solo lo suyo (`RN-AC-028`).
            instructor != null ? instructor : filtros.instructorId(),
            estado,
            filtros.ended(),
            filtros.from(),
            filtros.to(),
            ahora);
    long total = consultas.count(filtro);
    if (total == 0) {
      return PageResponse.de(List.of(), total, trozo.page(), trozo.size());
    }
    List<LiveSessionItemRow> filas = consultas.search(filtro, trozo.offset(), trozo.size());
    Map<UUID, List<LiveSessionInstructorRef>> instructores =
        apoyo.instructoresDe(
            consultas.findInstructorsOf(filas.stream().map(LiveSessionItemRow::id).toList()));
    List<Item> pagina =
        filas.stream()
            .map(
                fila ->
                    Item.from(
                        fila,
                        !ahora.isBefore(fila.seriesEndsAt().toInstant()),
                        apoyo.siguiente(fila.serie(), fila.status()),
                        instructores.getOrDefault(fila.id(), List.of())))
            .toList();
    return PageResponse.de(pagina, total, trozo.page(), trozo.size());
  }
}
