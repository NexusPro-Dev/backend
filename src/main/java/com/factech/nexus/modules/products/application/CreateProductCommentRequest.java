package com.factech.nexus.modules.products.application;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * El cuerpo del alta de una reseña (`RF-PM-009`).
 *
 * <p><b>Ningún campo de persona.</b> El autor es quien porta el token, y un {@code userId} en el
 * cuerpo es un campo desconocido que {@code fail-on-unknown-properties} rechaza — la ambigüedad no
 * existe siquiera. Las dos entradas son obligatorias (`RN-PM-025`) y las valida el dominio, con los
 * códigos de la spec; aquí solo se declara la forma.
 */
public record CreateProductCommentRequest(
    @Schema(
            description = "Puntuación entera de 1 a 5. Un decimal se rechaza, no se redondea.",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minimum = "1",
            maximum = "5")
        @JsonDeserialize(using = RatingDeserializer.class)
        Integer rating,
    @Schema(
            description =
                "El texto de la reseña, de 1 a 1000 caracteres sin contar los espacios de los"
                    + " extremos. Se guarda tal cual y NO se sanea: el front debe escaparlo al"
                    + " pintarlo, porque la lista es pública.",
            requiredMode = Schema.RequiredMode.REQUIRED,
            maxLength = 1000)
        String comment) {}
