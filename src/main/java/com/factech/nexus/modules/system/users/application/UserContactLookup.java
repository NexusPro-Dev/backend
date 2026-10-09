package com.factech.nexus.modules.system.users.application;

import java.util.Optional;
import java.util.UUID;

/**
 * El correo y el nombre de una persona, para registrarla en un servicio externo (**D-25**).
 *
 * <p>La pide el aula en vivo de `AC` (`RF-AC-054`, `RN-AC-027`): Zoom registra a cada asistente por
 * su correo y su nombre. <b>Una interfaz propia y no un campo más en {@link UserCatalog}</b>: la
 * identidad que aquella publica se enseña en listados, y el correo no debe viajar a ninguno.
 */
public interface UserContactLookup {

  /** La persona, si existe y no está eliminada. */
  Optional<UserContact> contactOf(UUID id);

  record UserContact(UUID id, String email, String firstName, String lastName) {}
}
