package com.factech.nexus.modules.commissions.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * Los lotes que Finanzas elige pagar de una vez (`RF-CM-025`): al menos uno y sin repetir. <b>Sin
 * tope</b>, por decisión del responsable del proyecto.
 */
@Schema(name = "PayCommissionBatchesRequest")
public record PayCommissionBatchesRequest(List<UUID> batchIds) {}
