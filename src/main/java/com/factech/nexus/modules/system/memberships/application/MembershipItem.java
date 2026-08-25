package com.factech.nexus.modules.system.memberships.application;

import java.util.UUID;

/**
 * Modelo de lectura de una membresía dentro de la cadena (`RF-SP-017`).
 *
 * <p><b>Lleva los dos vecinos como identificadores.</b> {@code childMembershipId} es redundante con
 * la cadena —se deduce mirando quién apunta a quién— pero el cliente no debería tener que
 * reconstruirlo: viene gratis en la misma sentencia y evita que cada consumidor implemente ese
 * cruce a su manera.
 *
 * <p><b>Sin {@code createdAt} ni {@code updatedAt}.</b> `spec.md` §6.2 no los pide y son ruido en
 * un listado cuyo eje es la posición. {@code updatedAt} diría además algo confuso: cambia cuando
 * <i>otra</i> membresía se insertó por encima, no cuando esta cambió.
 *
 * <p><b>Sin cuántas personas la tienen.</b> No hay cruce con {@code user_memberships}, y es
 * deliberado: una membresía ni se elimina ni se desactiva (`RN-SP-008`), de modo que ese número no
 * condiciona ninguna decisión tomable desde aquí. Es la asimetría con `RF-SP-003`, donde el conteo
 * de usuarios sí se aceptó porque decidía si el rol podía eliminarse.
 *
 * @param level distancia hasta la cima: {@code 1} es la superior
 */
public record MembershipItem(
    UUID id,
    String code,
    String name,
    String description,
    int level,
    UUID parentMembershipId,
    UUID childMembershipId) {}
