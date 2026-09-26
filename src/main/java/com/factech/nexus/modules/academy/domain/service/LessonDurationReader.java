package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import com.factech.nexus.shared.video.VideoDurationLookup;
import com.factech.nexus.shared.video.VideoDurationUnavailable;
import com.factech.nexus.shared.video.VideoLink;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * La duración de una lección {@code VIDEO}, leída de su proveedor (`RN-AC-017`, `RF-AC-028` y
 * `RF-AC-029` {@code EX-003}).
 *
 * <p>Lo que la lección sabe de la consulta: <b>que puede fallar, y cómo se le dice a
 * administración</b>. El fallo del proveedor —video privado, borrado, sin respuesta a tiempo, sin
 * clave— se convierte en un {@code 422} que nombra el proveedor y el motivo y pide la duración a
 * mano, que es como se guarda un video que no se deja leer. Quien llama ya validó el enlace: aquí
 * solo llegan enlaces reconocidos.
 */
@Component
public class LessonDurationReader {

  private final VideoDurationLookup proveedores;

  public LessonDurationReader(VideoDurationLookup proveedores) {
    this.proveedores = proveedores;
  }

  public int leer(String enlace) {
    VideoLink video =
        VideoLink.reconocer(enlace)
            .orElseThrow(
                () -> new IllegalStateException("Se pidió la duración de un enlace no válido."));
    try {
      return proveedores.segundosDe(video);
    } catch (VideoDurationUnavailable fallo) {
      String mensaje =
          "No se pudo obtener la duración del video de %s: %s. Envíe durationSeconds."
              .formatted(fallo.proveedor().nombre(), fallo.motivo());
      throw new UnprocessableEntityException(
          "EX-003", mensaje, List.of(new FieldError("durationSeconds", "EX-003", mensaje)));
    }
  }
}
