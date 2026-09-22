package com.factech.nexus.modules.system.documenttypes.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/**
 * Un tipo de documento del catálogo (`RF-SP-051`).
 *
 * <p><b>El identificador viaja además del nombre y la abreviación</b>, y no es redundante: es lo
 * que `RF-SP-024` y `RF-SP-027` reciben en su cuerpo. Sin él, el cliente tendría que resolver el
 * tipo por su abreviación en cada alta, que es mezclar dos espacios de identificación en la misma
 * petición.
 *
 * <p><b>La abreviación es el código</b> y no hay un campo {@code code} además: tener los dos daría
 * tres identificadores para el mismo concepto y obligaría a decidir cuál manda.
 *
 * <p><b>{@code isActive} se devuelve siempre</b>, incluso cuando solo se piden los activos y por
 * tanto vale {@code true} en todas las filas. Es el criterio que `RF-SP-025` aplica a {@code
 * deletedAt}: un campo que aparece y desaparece obliga al cliente a tratar dos formas del mismo
 * recurso.
 *
 * <p><b>Lo que no lleva:</b> ni {@code createdAt} ni {@code updatedAt}. Cuándo se sembró la fila de
 * un catálogo no responde ninguna pregunta de negocio; quien lo necesite mira la migración.
 *
 * <p><b>Y no lleva ninguna marca de «acredita mayoría de edad»</b>, que es lo que da sentido a todo
 * el requerimiento: el catálogo <b>solo contiene</b> documentos de adulto, de modo que una columna
 * que lo dijera valdría {@code true} en todas las filas y sugeriría que puede haber alguna en
 * {@code false} (`RN-SP-035`).
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record DocumentTypeItem(UUID id, String abbreviation, String name, boolean isActive) {}
