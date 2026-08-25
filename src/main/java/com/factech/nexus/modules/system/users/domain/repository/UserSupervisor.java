package com.factech.nexus.modules.system.users.domain.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * El superior comercial vigente de una persona.
 *
 * <p>{@code since} es el inicio de <b>esta</b> asignación, no el de la relación: al reasignar se
 * cierra un tramo y se abre otro, de modo que esta fecha responde «desde cuándo reporta a este»,
 * que es la pregunta que la interfaz hace.
 */
public record UserSupervisor(
    UUID supervisorId,
    String username,
    String firstName,
    String lastName,
    String roleCode,
    String status,
    OffsetDateTime since) {}
