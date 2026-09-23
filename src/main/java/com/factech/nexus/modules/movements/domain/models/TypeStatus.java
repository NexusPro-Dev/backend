package com.factech.nexus.modules.movements.domain.models;

import java.util.UUID;

/**
 * Un estado del catálogo {@code movement_type_statuses}, ya resuelto (`RN-MV-033`).
 *
 * <p>Lleva el <b>identificador</b> porque es lo que se escribe —la clave compuesta de {@code
 * movements} lo ata al tipo— y el <b>código</b> porque es lo que el caso de uso razona y lo que se
 * publica.
 */
public record TypeStatus(UUID id, String code) {}
