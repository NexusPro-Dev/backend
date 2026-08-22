package com.factech.nexus.shared.error;

/**
 * Entrada del arreglo {@code errors} del formato de error (`architecture.md` §7.3).
 *
 * <p>Existe para que una respuesta pueda señalar <b>qué campo</b> falló y no solo que algo falló.
 * Varias excepciones de este módulo enumeran más de un infractor —`EX-003`, `EX-004` y `EX-005`
 * devuelven todos los permisos que incumplen, no el primero—, porque devolverlos de a uno convierte
 * una corrección en varias vueltas.
 *
 * @param field campo del cuerpo de la petición al que se refiere el error
 * @param code código de la causa concreta: serie {@code VAL-nnn}, {@code RN-XXX-nnn}, {@code
 *     EX-nnn}, {@code AUTH-nnn}, {@code INT-nnn} o {@code ERR-nnn} (`architecture.md` §7.3)
 * @param message mensaje en español, comprensible y sin detalle interno (Art. VI.5)
 */
public record FieldError(String field, String code, String message) {}
