package com.factech.nexus.modules.system.countries.application;

import java.util.UUID;

/**
 * Modelo de lectura de un país (`RF-SP-021`).
 *
 * <p>Cuatro campos y ninguno más: no hay prefijo telefónico ni moneda, y tampoco marcas temporales.
 * El nombre se devuelve <b>tal como se registró</b>, en un solo idioma: traducir un catálogo es
 * parte de una decisión de internacionalización que alcanza a toda la interfaz.
 */
public record CountryItem(UUID id, String code, String name, boolean isActive) {}
