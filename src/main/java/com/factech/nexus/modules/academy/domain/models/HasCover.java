package com.factech.nexus.modules.academy.domain.models;

import com.factech.nexus.shared.images.CambioDePortada;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Lo que tiene portada en academia: categoría y curso hoy, módulo cuando llegue `RF-AC-026`
 * (`RF-AC-014` `plan.md` §1).
 *
 * <p>Existe para que <b>subir una portada se escriba una vez</b> ({@code CoverUploader}) y no tres
 * veces igual: el colaborador no sabe de qué entidad se trata, solo que puede cambiarle la portada
 * y devolver el diff.
 */
public interface HasCover {

  UUID getId();

  /** Pone la portada nueva y devuelve cuál había, para borrarla después, y el diff de auditoría. */
  CambioDePortada asignarPortada(UUID nueva, OffsetDateTime ahora);
}
