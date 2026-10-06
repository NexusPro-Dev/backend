package com.factech.nexus.modules.system.roles.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * El rol con su marca, y a quién afecta (`RF-SP-077`, `CA-SP-888`).
 *
 * @param activeHolders cuántas personas activas lo portan
 * @param holdersWithoutMfa cuántas de ellas no tienen el segundo factor activo: las que quedarán
 *     retenidas en su siguiente renovación si el rol lo exige
 */
@Schema(name = "RoleMfaRequirementResponse")
public record RoleMfaRequirementResponse(
    UUID id, String code, boolean requiresMfa, long activeHolders, long holdersWithoutMfa) {}
