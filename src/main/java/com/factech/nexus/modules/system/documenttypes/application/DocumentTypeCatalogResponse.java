package com.factech.nexus.modules.system.documenttypes.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * El catálogo de tipos de documento (`RF-SP-051`).
 *
 * <p><b>Envoltura {@code content} y no un array desnudo</b>, con el mismo criterio que el catálogo
 * de monedas y el de países: una respuesta que hoy es una lista y mañana necesita un metadato
 * obliga a cambiar la forma para todos los clientes si nació desnuda.
 *
 * <p><b>Sin {@code totalElements} ni {@code totalPages}</b>: no se pagina, y publicar contadores de
 * una colección que siempre viene completa invita a construir sobre ellos una paginación que no
 * existe.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record DocumentTypeCatalogResponse(List<DocumentTypeItem> content) {}
