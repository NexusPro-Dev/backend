package com.factech.nexus.modules.system.currencies.application;

import java.util.UUID;

/**
 * Modelo de lectura de una moneda (`RF-SP-019`).
 *
 * <p><b>Sin {@code createdAt} ni {@code updatedAt}.</b> No son información de negocio para quien
 * compone una operación financiera, y {@code createdAt} diría cuándo se aplicó la migración de
 * siembra — distinto en cada entorno.
 *
 * <p><b>Sin tasas de cambio ni conversión.</b> No hay campo, no hay tabla y no hay integración: es
 * un requerimiento que no existe (`spec.md` §4.2).
 *
 * @param decimalPlaces el campo más importante de la respuesta. Cero es un valor legítimo y
 *     distinto de «no se sabe», y por eso nunca es nulo
 * @param symbol puede venir vacío; el contrato lo devuelve como {@code null}, nunca omitido
 */
public record CurrencyItem(
    UUID id,
    String code,
    String name,
    String symbol,
    int decimalPlaces,
    boolean isDefault,
    boolean isActive) {}
