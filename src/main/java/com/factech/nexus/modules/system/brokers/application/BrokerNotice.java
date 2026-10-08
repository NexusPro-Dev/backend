package com.factech.nexus.modules.system.brokers.application;

import java.util.List;
import java.util.Map;

/**
 * Un aviso de un broker <b>tal como llegó</b> (`RF-SP-078`), sin tipos del servlet.
 *
 * @param method {@code GET} o {@code POST}
 * @param rawQuery la cadena de consulta sin descodificar, con el {@code token} dentro; nula si no
 *     vino ninguna
 * @param headers las cabeceras, nombre → valores, sin filtrar
 * @param body el cuerpo en bytes; vacío si no vino
 * @param contentType el tipo de contenido declarado, o nulo
 * @param ipAddress el origen ya resuelto contra los proxies de confianza, o nulo
 */
public record BrokerNotice(
    String method,
    String rawQuery,
    Map<String, List<String>> headers,
    byte[] body,
    String contentType,
    String ipAddress) {}
