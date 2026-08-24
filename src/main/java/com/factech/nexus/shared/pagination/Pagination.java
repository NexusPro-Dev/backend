package com.factech.nexus.shared.pagination;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resuelve y valida la paginación de una petición (`architecture.md` §7.4).
 *
 * <p><b>Una petición que excede el máximo se RECHAZA, no se recorta.</b> Recortarla en silencio
 * haría que quien pide doscientos elementos reciba cien y crea que solo hay cien — un fallo que no
 * se manifiesta como error sino como un dato incompleto, que es la peor forma de equivocarse en un
 * listado.
 *
 * <p>Los valores viven en configuración y no en cada endpoint, por la misma razón que la forma de
 * la página: dos topes distintos obligan a documentar dos y a que alguien elija mal el segundo.
 */
@Component
public class Pagination {

  private final int tamanoPorOmision;
  private final int tamanoMaximo;

  public Pagination(
      @Value("${nexus.pagination.default-size:20}") int tamanoPorOmision,
      @Value("${nexus.pagination.max-size:100}") int tamanoMaximo) {
    this.tamanoPorOmision = tamanoPorOmision;
    this.tamanoMaximo = tamanoMaximo;
  }

  /**
   * @param pagina base cero; nula significa la primera
   * @param tamano nulo significa el valor por omisión
   */
  public Slice resolver(Integer pagina, Integer tamano) {
    List<FieldError> problemas = new ArrayList<>();

    int paginaResuelta = pagina == null ? 0 : pagina;
    if (paginaResuelta < 0) {
      problemas.add(new FieldError("page", "VAL-003", "La página no puede ser negativa."));
    }

    int tamanoResuelto = tamano == null ? tamanoPorOmision : tamano;
    if (tamanoResuelto < 1) {
      problemas.add(new FieldError("size", "VAL-003", "El tamaño de página debe ser al menos 1."));
    } else if (tamanoResuelto > tamanoMaximo) {
      problemas.add(
          new FieldError(
              "size", "VAL-003", "El tamaño de página no puede exceder " + tamanoMaximo + "."));
    }

    if (!problemas.isEmpty()) {
      throw new ValidationException("VAL-003", "La paginación solicitada no es válida.", problemas);
    }
    return new Slice(paginaResuelta, tamanoResuelto);
  }

  /** Página y tamaño ya validados, con el desplazamiento que la consulta necesita. */
  public record Slice(int page, int size) {
    public int offset() {
      return page * size;
    }
  }
}
