package com.factech.nexus.modules.system.users.application;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/**
 * El cuerpo del registro por enlace (`RF-SP-045`).
 *
 * <p><b>Cuatro campos de referencia y ninguno es un identificador, salvo el broker</b>: el producto
 * va por código o identificador, el vendedor por nombre de usuario, el país por código ISO alfa-3 y
 * el tipo de documento por abreviación. Es un formulario <b>público</b> al que no se le puede pedir
 * que conozca los UUID del sistema — salvo el del broker, que acaba de leer del catálogo.
 *
 * <p><b>Los dos campos del broker NO llevan {@code @NotBlank}</b> y son condicionalmente
 * obligatorios (`RN-SP-042`): se exigen cuando el producto del enlace es {@code FREE → FREE}, y eso
 * <b>no cabe en una anotación</b> porque depende de un dato que hay que ir a buscar. Lo comprueba
 * el caso de uso después de resolver el producto.
 */
public record SelfRegistrationRequest(
    @NotBlank(message = "VAL-001: Debe indicar el producto del enlace.") String product,
    @NotBlank(message = "VAL-002: Debe indicar quién le compartió el enlace.") String referrer,
    @NotBlank(message = "VAL-003: El nombre y los apellidos son obligatorios.") String firstName,
    @NotBlank(message = "VAL-003: El nombre y los apellidos son obligatorios.") String lastName,
    @NotBlank(message = "VAL-004: El nombre de usuario no es válido.") String username,
    @NotBlank(message = "VAL-005: El correo indicado no es válido.")
        @Email(message = "VAL-005: El correo indicado no es válido.")
        String email,
    @NotBlank(message = "VAL-006: La contraseña no cumple la política.") String password,
    @NotBlank(message = "VAL-009: El país indicado no es válido.") String countryCode,
    @NotBlank(message = "VAL-010: El documento indicado no es válido.") String documentType,
    @NotBlank(message = "VAL-010: El documento indicado no es válido.") String documentNumber,
    @NotBlank(message = "VAL-011: El teléfono indicado no es válido.") String phone,
    String addressLine1,
    String addressLine2,
    String city,
    UUID brokerId,
    String brokerAccountId) {}
