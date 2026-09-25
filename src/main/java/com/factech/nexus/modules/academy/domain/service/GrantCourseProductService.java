package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.CourseDetailResponse;
import com.factech.nexus.modules.academy.application.GrantCourseProductRequest;
import com.factech.nexus.modules.academy.domain.models.Course;
import com.factech.nexus.modules.academy.domain.repository.CourseProductRepository;
import com.factech.nexus.modules.academy.domain.repository.CourseRepository;
import com.factech.nexus.modules.academy.domain.repository.JpaCourseProductRepository;
import com.factech.nexus.modules.products.application.ProductCatalog;
import com.factech.nexus.modules.products.application.ProductCatalog.KindView;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-AC-037`: dar visibilidad de un curso a un servicio.
 *
 * <p>La clasificación (`RF-AC-016`) con el producto en lugar de la categoría y <b>el puerto de `PM`
 * en lugar del repositorio propio</b>: el curso se bloquea y el producto no —nada de él cambia—, la
 * pareja se comprueba bajo el bloqueo y la clave primaria es la red de la carrera. La escritura es
 * de {@link CourseAccessWriter}, que comparte con el alta del curso.
 *
 * <p><b>Dos `422` distintos</b>: el producto que no existe o está retirado (`EX-002`) y el que
 * existe pero no es un servicio (`EX-003`). Quien eligió un upgrade no se equivocó de identificador
 * sino de producto, y el mensaje le dice cuál eligió. <b>Un servicio {@code INACTIVO} se
 * admite</b>: la lista se arma antes de poner el servicio a la venta (`RN-AC-020`).
 */
@Service
public class GrantCourseProductService {

  static final String ENTIDAD_FILA = CourseAccessWriter.ENTIDAD_SERVICIOS;

  private final CourseRepository cursos;
  private final ProductCatalog productos;
  private final CourseProductRepository filas;
  private final CourseAccessWriter llaves;
  private final CourseDetailReader detalle;

  public GrantCourseProductService(
      CourseRepository cursos,
      ProductCatalog productos,
      CourseProductRepository filas,
      CourseAccessWriter llaves,
      CourseDetailReader detalle) {
    this.cursos = cursos;
    this.productos = productos;
    this.filas = filas;
    this.llaves = llaves;
    this.detalle = detalle;
  }

  @Transactional
  public CourseDetailResponse grant(UUID courseId, GrantCourseProductRequest peticion) {
    Course curso = ClassifyCourseService.cursoVivo(cursos, courseId);

    KindView producto =
        productos
            .findKind(peticion.productId())
            .filter(p -> !p.retired())
            .orElseThrow(
                () -> {
                  String mensaje = "El producto indicado no existe o está retirado.";
                  return new UnprocessableEntityException(
                      "EX-002", mensaje, List.of(new FieldError("productId", "EX-002", mensaje)));
                });
    if (!producto.bot()) {
      String mensaje =
          "Solo un servicio abre un curso: el producto %s es un upgrade de membresía."
              .formatted(producto.code());
      throw new UnprocessableEntityException(
          "EX-003", mensaje, List.of(new FieldError("productId", "EX-003", mensaje)));
    }

    if (filas.find(curso.getId(), producto.id()).isPresent()) {
      throw JpaCourseProductRepository.yaAbre(producto.code());
    }

    llaves.darServicio(curso.getId(), producto);
    return detalle.leer(curso.getId());
  }
}
