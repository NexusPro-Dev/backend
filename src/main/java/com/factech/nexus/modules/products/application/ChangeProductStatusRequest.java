package com.factech.nexus.modules.products.application;

import jakarta.validation.constraints.NotBlank;

/**
 * El cuerpo de {@code PATCH /api/v1/products/{id}/status` (`RF-PM-005`).
 *
 * <p><b>Un recurso propio y no un campo del `PATCH` general</b>, por lo mismo que `RF-SP-007`
 * separó el estado de un rol: publicar y corregir son decisiones distintas, con permisos que algún
 * día podrán serlo también. Mezclarlas haría que una corrección de texto pudiera poner algo a la
 * venta.
 *
 * <p>{@code status} es texto y no el enumerado: enlazarlo como enumerado dejaría que Jackson
 * rechazara el valor fuera de dominio con su propio mensaje, en inglés y sin decir cuáles se
 * admiten.
 */
public record ChangeProductStatusRequest(
    @NotBlank(message = "El estado es obligatorio.") String status) {}
