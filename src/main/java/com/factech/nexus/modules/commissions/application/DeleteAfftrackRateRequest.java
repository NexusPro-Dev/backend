package com.factech.nexus.modules.commissions.application;

import io.swagger.v3.oas.annotations.media.Schema;

/** Cuerpo de los retiros de escalones afftrack, de rol y de persona: el motivo (Art. V.13). */
@Schema(name = "DeleteAfftrackRateRequest")
public record DeleteAfftrackRateRequest(String reason) {}
