package com.factech.nexus.modules.system.roles.application;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Cuerpo de {@code PATCH /api/v1/roles/{id}/parent} (`RF-SP-008`).
 *
 * <p><b>El rol padre es obligatorio y no admite nulo</b> (`VAL-001`, `EX-003`): dejar un rol sin
 * padre lo convertiría en una segunda raíz, y `RN-SEG-007` exige que exista <b>exactamente una</b>.
 * El nulo no es «quítale el padre», es una jerarquía con dos cimas.
 *
 * <p><b>La clasificación del nuevo padre es indiferente</b>: un rol comercial puede colgar de uno
 * funcionario (`CA-SP-160`), porque el rol padre acota privilegios y no clasifica.
 */
public record ChangeRoleParentRequest(
    @NotNull(message = "VAL-001: El rol padre es obligatorio.") UUID parentRoleId) {}
