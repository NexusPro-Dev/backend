package com.factech.nexus.modules.system.brokers.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/**
 * Un broker del catálogo (`RF-SP-052`).
 *
 * <p><b>Tres campos, y el de negocio es uno solo</b>: el nombre. Es la decisión del responsable del
 * proyecto del 08-09-2026 —«de momento el nombre»— y de ella sale que el nombre <b>sea la clave de
 * negocio</b>, con índice único funcional en el esquema.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record BrokerItem(UUID id, String name, boolean isActive) {}
