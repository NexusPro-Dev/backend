package com.factech.nexus.modules.movements.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/** Lo que se publica del comprobante de un ajuste (`RN-MV-077`): todo menos el archivo. */
@Schema(name = "PointsReceiptInfo")
public record PointsReceiptInfo(
    @Schema(description = "El nombre con que se subió, sin ruta.") String fileName,
    @Schema(description = "application/pdf, image/png o image/jpeg: el que dicen sus bytes.")
        String contentType,
    long sizeBytes,
    @Schema(description = "El resumen SHA-256 del archivo, en hexadecimal.") String sha256,
    OffsetDateTime uploadedAt) {}
