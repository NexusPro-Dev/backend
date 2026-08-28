package com.factech.nexus.modules.system.auth.application;

/**
 * Cuerpo de la respuesta a la solicitud de recuperación (`RF-SP-040`).
 *
 * <p><b>Es idéntico exista o no la identidad</b>, y ahí está todo el requerimiento: un cuerpo que
 * variara —aunque fuera en una palabra— convertiría el endpoint en un verificador de qué cuentas
 * existen. Por eso no lleva ningún campo que dependa de la cuenta: ni el correo enmascarado, ni
 * cuándo caduca, ni si se envió.
 */
public record PasswordRecoveryResponse(String message) {}
