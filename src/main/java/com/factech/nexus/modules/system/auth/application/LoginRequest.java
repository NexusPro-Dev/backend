package com.factech.nexus.modules.system.auth.application;

import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo del inicio de sesión (`RF-SP-034`).
 *
 * <p><b>Un solo campo para el identificador y no dos.</b> El cliente no tiene que declarar si se
 * presenta con su nombre de usuario o con su correo: la prohibición del {@code @} en el nombre de
 * usuario hace que ningún valor sea ambiguo, y el sistema busca por ambas columnas sabiendo que a
 * lo sumo una resuelve.
 */
public record LoginRequest(
    @NotBlank(message = "VAL-001: El identificador es obligatorio.") String identifier,
    @NotBlank(message = "VAL-002: La contraseña es obligatoria.") String password) {}
