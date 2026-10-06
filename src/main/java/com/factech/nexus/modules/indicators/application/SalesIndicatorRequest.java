package com.factech.nexus.modules.indicators.application;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Lo que comparten los indicadores de ventas (`RF-IN-001` §6.1): el periodo en días de Bogotá y los
 * dos filtros. Cualquiera puede ser nulo.
 *
 * @param from primer día, incluido
 * @param to último día, incluido entero
 * @param currencyId solo lo vendido en esa moneda
 * @param sellerId solo lo vendido por esa persona, si está en el alcance
 */
public record SalesIndicatorRequest(LocalDate from, LocalDate to, UUID currencyId, UUID sellerId) {}
