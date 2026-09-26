package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.domain.models.AcademyImage;
import com.factech.nexus.modules.academy.domain.models.HasCover;
import com.factech.nexus.modules.academy.domain.repository.AcademyImageRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.images.CambioDePortada;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Subir una portada de academia, escrito una vez (`RF-AC-014` `plan.md` §1): la secuencia de {@code
 * UploadPackageCoverService} sin saber de qué entidad se trata.
 *
 * <p>En dos pasos, y el corte es la razón de que existan dos métodos: {@link #preparar} valida el
 * archivo <b>antes</b> de tocar la base —los tres {@code VAL} salen sin haber bloqueado nada—, y
 * {@link #colocar} hace lo demás <b>con la fila ya bloqueada</b> por el caso de uso: insertar la
 * imagen, apuntar la entidad, <b>volcar</b>, borrar la anterior y auditar. El orden de las tres
 * últimas es el de la clave foránea: al revés, muerde.
 */
@Component
public class CoverUploader {

  private final AcademyImageRepository imagenes;
  private final AuditWriter auditoria;
  private final UuidV7Generator ids;
  private final Clock reloj;

  @Autowired
  public CoverUploader(
      AcademyImageRepository imagenes, AuditWriter auditoria, UuidV7Generator ids) {
    this(imagenes, auditoria, ids, Clock.systemUTC());
  }

  CoverUploader(
      AcademyImageRepository imagenes, AuditWriter auditoria, UuidV7Generator ids, Clock reloj) {
    this.imagenes = imagenes;
    this.auditoria = auditoria;
    this.ids = ids;
    this.reloj = reloj;
  }

  /** La imagen nueva, validada y sin guardar; o el rechazo del archivo. */
  public AcademyImage preparar(byte[] bytes) {
    return AcademyImage.de(ids.next(), bytes, OffsetDateTime.now(reloj));
  }

  /**
   * Pone la imagen como portada de {@code dueno}, que el caso de uso ya bloqueó.
   *
   * @param volcar vuelca el {@code UPDATE} de la entidad antes de borrar la imagen anterior
   * @param entidad la tabla de la entidad, para la fila {@code UPDATE} de la auditoría
   */
  public void colocar(HasCover dueno, AcademyImage nueva, Runnable volcar, String entidad) {
    imagenes.save(nueva);
    CambioDePortada cambio = dueno.asignarPortada(nueva.getId(), OffsetDateTime.now(reloj));
    // El UPDATE llega a la base antes que el DELETE, o la clave foránea muerde.
    volcar.run();
    if (cambio.anterior() != null) {
      imagenes.deleteById(cambio.anterior());
    }
    // Siempre hay cambio: cada subida estrena identificador.
    auditoria.recordChange(
        new ChangeEvent(
            CourseDetailReader.MODULO,
            entidad,
            dueno.getId(),
            ChangeAction.UPDATE,
            cambio.cambios()));
  }
}
