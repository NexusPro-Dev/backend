package com.factech.nexus.modules.system.teams.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo del alta de un equipo (`RF-SP-063` §11).
 *
 * <p><b>Sin {@code status}, sin {@code members}, sin {@code code}</b>, y con {@code
 * FAIL_ON_UNKNOWN_PROPERTIES} activo: enviar cualquiera de los tres devuelve {@code 400}
 * (`VAL-003`, `CA-SP-734`) y no se ignora en silencio. El estado nace `ACTIVO` y se cambia con
 * `RF-SP-067`, los miembros entran con `RF-SP-069`, y el código no existe.
 *
 * <p>Las dos validaciones de forma se devuelven <b>juntas</b> (`CA-SP-733`): quien se equivocó en
 * las dos corrige una vez.
 */
public record RegisterTeamRequest(
    @NotBlank(message = "VAL-001: El nombre es obligatorio y no puede superar los 100 caracteres.")
        @Size(
            max = 100,
            message = "VAL-001: El nombre es obligatorio y no puede superar los 100 caracteres.")
        String name,
    @Size(max = 500, message = "VAL-002: La descripción no puede exceder 500 caracteres.")
        String description) {

  public RegisterTeamRequest {
    name = name == null ? null : name.trim();
    description = description == null ? null : description.trim();
  }
}
