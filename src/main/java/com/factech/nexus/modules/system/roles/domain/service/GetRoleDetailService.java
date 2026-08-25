package com.factech.nexus.modules.system.roles.domain.service;

import com.factech.nexus.modules.system.permissions.application.PermissionResponse;
import com.factech.nexus.modules.system.roles.application.RoleDetailResponse;
import com.factech.nexus.modules.system.roles.application.RoleSummaryResponse;
import com.factech.nexus.modules.system.roles.domain.repository.RoleQueryRepository;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Detalle de un rol (`RF-SP-003`).
 *
 * <p><b>`RN-SEG-004` se cumple por ausencia de recorrido, no por código.</b> No hay ningún método
 * que verifique que no se recorre la cadena de ancestros: lo que hay es que ninguna de las dos
 * sentencias la recorre. Por eso se prueba <b>contando sentencias</b> y no ejercitando una función
 * — una prueba que llamara a algo estaría comprobando la existencia de lo que precisamente no debe
 * existir.
 *
 * <p><b>El orden de las dos sentencias importa.</b> Primero el rol, con su padre y sus dos conteos
 * en la misma pasada: si no existe, se devuelve {@code 404} <b>sin leer sus permisos</b>. Un rol
 * inexistente no cuesta ni una subconsulta de más.
 *
 * <p>Un rol eliminado devuelve <b>el mismo</b> {@code 404} y el mismo mensaje que uno inexistente,
 * sin ninguna pista de que existió (`CA-SP-020`). Reconstruir qué era corresponde a la auditoría de
 * eliminación, que conserva su estado y tiene su propio permiso (Art. V.13).
 */
@Service
public class GetRoleDetailService {

  private final RoleQueryRepository consultas;

  public GetRoleDetailService(RoleQueryRepository consultas) {
    this.consultas = consultas;
  }

  @Transactional(readOnly = true)
  public RoleDetailResponse detail(UUID id) {
    RoleQueryRepository.RoleDetailRow fila =
        consultas
            .findDetail(id)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", "No existe un rol con ese identificador."));

    return new RoleDetailResponse(
        fila.id(),
        fila.code(),
        fila.name(),
        fila.description(),
        fila.roleType(),
        fila.status(),
        fila.isSystem(),
        fila.tienePadre()
            ? new RoleSummaryResponse(fila.parentId(), fila.parentCode(), fila.parentName())
            : null,
        // Vacía y no nula cuando el rol no declara ninguno (`CA-SP-018`): un rol
        // que existe y no concede nada es un estado válido, y distinguirlo con
        // la ausencia del campo obligaría al cliente a tratar dos formas.
        consultas.findDeclaredPermissions(id).stream().map(PermissionResponse::from).toList(),
        fila.childRoleCount(),
        fila.assignedUserCount(),
        fila.createdAt(),
        fila.updatedAt());
  }
}
