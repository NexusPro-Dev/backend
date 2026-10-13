package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.LiveSessionDetailResponse;
import com.factech.nexus.modules.academy.domain.models.AcademyImage;
import com.factech.nexus.modules.academy.domain.models.HasCover;
import com.factech.nexus.modules.academy.domain.repository.AcademyImageRepository;
import com.factech.nexus.modules.academy.domain.repository.LiveSessionQueryRepository.LiveSessionRow;
import com.factech.nexus.modules.academy.domain.repository.LiveSessionRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.images.CambioDePortada;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso `RF-AC-058` a `RF-AC-061`: poner y quitar la portada de una clase en vivo, de
 * cualquiera o de una propia (`RN-AC-032`, `RN-AC-004`).
 *
 * <p><b>Poner es {@link CoverUploader}</b>, el de la categoría y el curso: el archivo se valida
 * antes de bloquear nada, y luego, con la fila bloqueada, se guarda la imagen, se apunta la clase,
 * se borra la anterior y se audita. La clase no es una entidad JPA, así que el {@link HasCover} se
 * hace aquí, con el {@code UPDATE} nativo. <b>Quitar</b> deja la columna nula, borra la imagen y
 * audita, como {@code products:remove-cover}; sin portada, no hace nada. <b>Cancelada o terminada,
 * {@code 409}</b> (`RN-AC-029`); la propia sobre una ajena, {@code 404} (`RN-AC-028`).
 */
@Service
public class LiveSessionCoverService {

  private final LiveSessionSupport apoyo;
  private final LiveSessionRepository escrituras;
  private final CoverUploader portadas;
  private final AcademyImageRepository imagenes;
  private final AuditWriter auditoria;

  public LiveSessionCoverService(
      LiveSessionSupport apoyo,
      LiveSessionRepository escrituras,
      CoverUploader portadas,
      AcademyImageRepository imagenes,
      AuditWriter auditoria) {
    this.apoyo = apoyo;
    this.escrituras = escrituras;
    this.portadas = portadas;
    this.imagenes = imagenes;
    this.auditoria = auditoria;
  }

  /** `RF-AC-058`. */
  @Transactional
  public LiveSessionDetailResponse upload(UUID id, byte[] bytes) {
    return poner(id, bytes, null);
  }

  /** `RF-AC-060`. */
  @Transactional
  public LiveSessionDetailResponse uploadOwn(UUID id, byte[] bytes) {
    return poner(id, bytes, apoyo.actorId());
  }

  /** `RF-AC-059`. */
  @Transactional
  public LiveSessionDetailResponse remove(UUID id) {
    return quitar(id, null);
  }

  /** `RF-AC-061`. */
  @Transactional
  public LiveSessionDetailResponse removeOwn(UUID id) {
    return quitar(id, apoyo.actorId());
  }

  private LiveSessionDetailResponse poner(UUID id, byte[] bytes, UUID instructor) {
    AcademyImage nueva = portadas.preparar(bytes);
    LiveSessionRow clase = bloquear(id, instructor);
    portadas.colocar(new Portada(clase), nueva, () -> {}, LiveSessionSupport.ENTIDAD);
    return apoyo.detalle(id);
  }

  private LiveSessionDetailResponse quitar(UUID id, UUID instructor) {
    LiveSessionRow clase = bloquear(id, instructor);
    UUID anterior = clase.coverImageId();
    if (anterior != null) {
      // La columna deja de señalarla antes de borrarla: la clave foránea no tiene ON DELETE.
      escrituras.setCover(id, null);
      imagenes.deleteById(anterior);
      auditoria.recordChange(
          new ChangeEvent(
              LiveSessionSupport.MODULO,
              LiveSessionSupport.ENTIDAD,
              id,
              ChangeAction.UPDATE,
              CambioDePortada.de(anterior, null).cambios()));
    }
    return apoyo.detalle(id);
  }

  private LiveSessionRow bloquear(UUID id, UUID instructor) {
    LiveSessionRow clase = escrituras.findForUpdate(id).orElseThrow(LiveSessionSupport::noExiste);
    if (instructor != null) {
      apoyo.exigirPropia(clase, instructor);
    }
    apoyo.exigirModificable(clase, "EX-003");
    return clase;
  }

  /** La clase como {@link HasCover}: apunta la portada con el {@code UPDATE} nativo. */
  private final class Portada implements HasCover {

    private final LiveSessionRow clase;

    private Portada(LiveSessionRow clase) {
      this.clase = clase;
    }

    @Override
    public UUID getId() {
      return clase.id();
    }

    @Override
    public CambioDePortada asignarPortada(UUID nueva, OffsetDateTime ahora) {
      escrituras.setCover(clase.id(), nueva);
      return CambioDePortada.de(clase.coverImageId(), nueva);
    }
  }
}
