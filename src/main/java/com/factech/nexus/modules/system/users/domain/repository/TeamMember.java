package com.factech.nexus.modules.system.users.domain.repository;

import java.util.UUID;

/**
 * Una persona del equipo directo (`RF-SP-042`).
 *
 * <p>Lleva <b>lo justo para nombrarla y ver si sigue operando</b>, y nada más. No es un perfil: no
 * hay correo, ni fechas, ni membresía. La restricción es deliberada y es lo que impide que este
 * endpoint se convierta en un listado de usuarios con otro nombre y otro permiso — `RF-SP-025` ya
 * existe para eso.
 *
 * <p>{@code roleCode} es el rol <b>comercial</b> de mayor rango, que es el único que explica su
 * posición en la estructura.
 */
public record TeamMember(
    UUID id, String username, String firstName, String lastName, String roleCode, String status) {}
