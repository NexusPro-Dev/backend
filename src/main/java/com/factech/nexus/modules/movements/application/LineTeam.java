package com.factech.nexus.modules.movements.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * <b>La oficina donde se vendió</b> una línea (`RN-MV-078`): el equipo del director de la cadena
 * del vendedor el día de la venta.
 *
 * <p><b>Una oficina, una forma</b>: la misma la publican la fila del listado ({@code teams}), la
 * línea del detalle ({@code SaleLineResponse.team}) y la fila de líneas de venta ({@code
 * SaleLineItem.team}). El {@code @Schema(name)} explícito es porque springdoc funde en un esquema
 * los registros con el mismo nombre simple.
 *
 * <p><b>Lo congelado es CUÁL oficina, no su nombre</b>: el nombre se lee de {@code teams} al
 * responder, de modo que renombrar un equipo corrige lo que se muestra sin mudar lo vendido. Un
 * equipo eliminado lógicamente se sigue nombrando.
 */
@Schema(name = "LineTeam")
public record LineTeam(
    @Schema(description = "El equipo (oficina) donde se vendió.") UUID id,
    @Schema(description = "Su nombre de hoy: lo congelado es cuál, no cómo se llama.")
        String name) {}
