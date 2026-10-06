package com.factech.nexus.modules.system.auth.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Los diez códigos de recuperación nuevos (`RF-SP-074`, `RN-SP-061`).
 *
 * <p><b>Es la única vez que existen en claro.</b> Los anteriores ya no sirven.
 */
@Schema(name = "RecoveryCodesResponse")
public record RecoveryCodesResponse(OffsetDateTime generatedAt, List<String> recoveryCodes) {}
