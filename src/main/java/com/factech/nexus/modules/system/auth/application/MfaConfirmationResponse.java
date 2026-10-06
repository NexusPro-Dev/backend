package com.factech.nexus.modules.system.auth.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * El factor activo y sus diez códigos de recuperación (`RF-SP-071`, `RN-SP-061`).
 *
 * <p><b>Es la única vez que los códigos existen en claro.</b> El servidor guarda su resumen y no
 * puede volver a mostrarlos: solo regenerarlos.
 */
@Schema(name = "MfaConfirmationResponse")
public record MfaConfirmationResponse(
    OffsetDateTime enabledAt, boolean replacedPrevious, List<String> recoveryCodes) {}
