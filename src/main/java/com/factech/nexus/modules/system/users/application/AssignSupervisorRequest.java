package com.factech.nexus.modules.system.users.application;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Cuerpo de {@code PATCH /api/v1/users/{id}/supervisor} (`RF-SP-041`).
 *
 * <p><b>No admite fecha de inicio.</b> La asignación rige desde que se ejecuta, siempre. Declararla
 * obligaría a especificar solapamientos, huecos entre tramos y correcciones retroactivas sobre
 * periodos ya liquidados, y hoy ningún requerimiento consume esas fechas.
 *
 * <p><b>No admite retirar el superior.</b> No hay forma de enviar {@code supervisorId} en nulo: el
 * estado «vendedor sin superior» no existe, y la única salida es dejar de portar rol comercial.
 *
 * <p>El <b>motivo</b> se valida como valor de dominio y no con una anotación: la regla —recorta,
 * exige contenido -- es la misma que usan el cambio de estado y la eliminación, y escrita como
 * {@code @NotBlank} en tres DTO acabaría divergiendo en el tercero.
 */
public record AssignSupervisorRequest(
    @NotNull(message = "VAL-001: El superior comercial es obligatorio.") UUID supervisorId,
    String reason) {}
