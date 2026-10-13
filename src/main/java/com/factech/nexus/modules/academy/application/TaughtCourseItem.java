package com.factech.nexus.modules.academy.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Un curso que dicta quien pregunta (`RF-AC-057`): lo justo para elegirlo al programar. */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "TaughtCourseItem")
public record TaughtCourseItem(
    UUID id, String title, @Schema(description = "ACTIVO o INACTIVO.") String status) {}
