package com.factech.nexus.modules.system.teams.application;

/**
 * Cuerpo de la eliminación de un equipo (`RF-SP-068` §6.1): <b>el motivo</b>, que {@link
 * com.factech.nexus.shared.audit.DeletionReason} valida con los dos códigos que el resto del
 * sistema ya publica —`VAL-002` el ausente, `VAL-003` el largo—.
 *
 * <p><b>El motivo es obligatorio porque la eliminación es lo irreversible</b> (Art. V.13), y es la
 * diferencia con el cambio de estado (`RF-SP-067`), que no lo admite: suspender se deshace con una
 * petición y eliminar no se deshace con ninguna — no hay operación de restauración, se crea otro
 * equipo y el nombre está libre.
 */
public record DeleteTeamRequest(String reason) {}
