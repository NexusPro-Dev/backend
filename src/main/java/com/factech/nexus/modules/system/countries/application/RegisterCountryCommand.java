package com.factech.nexus.modules.system.countries.application;

/**
 * Entrada del caso de uso de alta de país (`RF-SP-020`).
 *
 * <p><b>Sin estado.</b> El país nace activo y no admitirlo como argumento es lo que hace
 * verificable que no exista camino hacia el estado inactivo desde el alta.
 */
public record RegisterCountryCommand(String code, String name) {}
