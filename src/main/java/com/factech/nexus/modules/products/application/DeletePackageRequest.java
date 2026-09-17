package com.factech.nexus.modules.products.application;

/** Cuerpo del retiro de un paquete (`RF-PM-022`): el motivo, que {@code DeletionReason} valida. */
public record DeletePackageRequest(String reason) {}
