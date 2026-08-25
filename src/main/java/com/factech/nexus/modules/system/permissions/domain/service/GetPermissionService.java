package com.factech.nexus.modules.system.permissions.domain.service;

import com.factech.nexus.modules.system.permissions.application.PermissionResponse;
import com.factech.nexus.modules.system.permissions.domain.repository.PermissionQueryRepository;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Detalle de un permiso del catálogo (`RF-SP-015`).
 *
 * <p>Una sola sentencia y ninguna decisión de negocio: el catálogo es inmutable por API
 * (`RN-SP-004`) y esta consulta no lo altera. `RN-SP-004` <b>no se cumple con código que
 * rechace</b> sino porque no hay ningún manejador de escritura al que llamar.
 *
 * <p><b>No se recibe un objeto de consulta.</b> El caso de uso recibe un identificador y nada más:
 * un envoltorio para un único argumento sin invariante propia sería ceremonia. El catálogo sí lo
 * necesita, porque tiene tres criterios que normalizar.
 */
@Service
public class GetPermissionService {

  private final PermissionQueryRepository consultas;

  public GetPermissionService(PermissionQueryRepository consultas) {
    this.consultas = consultas;
  }

  @Transactional(readOnly = true)
  public PermissionResponse detail(UUID id) {
    return consultas
        .findById(id)
        .map(PermissionResponse::from)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "EX-001", "No existe un permiso con ese identificador."));
  }
}
