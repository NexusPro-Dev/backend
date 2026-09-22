package com.factech.nexus.modules.system.auth.application;

import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(
            description =
                "Nombre de usuario o correo, indistintamente. La cuenta inicial de todo"
                    + " despliegue es `superadmin`; en local, con la semilla activada, también"
                    + " valen `admin1`, `manager1`, `director1`, `agente1` o `cliente1`.",
            example = "superadmin")
        @NotBlank(message = "VAL-001: El identificador es obligatorio.")
        String identifier,
    // Sin `example`: el ejemplo de un campo de contraseña se publica en el
    // contrato, que es un archivo PÚBLICO (`api/index.md` §1), y una credencial
    // real ahí deja de serlo. La contraseña la pone cada despliegue y la
    // descripción dice de dónde sale.
    @Schema(
            description =
                "La que declaró el despliegue en `SUPERADMIN_PASSWORD_HASH`. **No hay valor por"
                    + " defecto**: `V9` se niega a arrancar sin esa variable en vez de sembrar una"
                    + " credencial conocida (Art. IX.5).",
            format = "password")
        @NotBlank(message = "VAL-002: La contraseña es obligatoria.")
        String password) {}
