package com.factech.nexus.modules.system.roles.application;

import io.swagger.v3.oas.annotations.media.Schema;

/** Exigir o no el segundo factor a los portadores de un rol (`RF-SP-077`). */
@Schema(name = "RoleMfaRequirementRequest")
public record RoleMfaRequirementRequest(
    @Schema(description = "Si sus portadores están obligados a usar el authenticator.")
        Boolean required) {}
