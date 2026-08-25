package com.factech.nexus.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Cifrado y comparación de credenciales con <b>Argon2id</b> (`security.md` §3.2, decisión D-15).
 *
 * <p><b>El sistema no puede recuperar una contraseña, solo restablecerla.</b> Argon2id es una
 * función de derivación con sal y coste, no un cifrado: no existe la operación inversa, y esa
 * ausencia es la propiedad que se busca.
 *
 * <p><b>La comparación es resistente a ataques de temporización</b> por construcción — la
 * implementación compara el resumen completo en tiempo constante—, que es lo que `security.md` §3.2
 * exige y lo que impide deducir cuántos caracteres iniciales acertó un intento.
 *
 * <p><b>Los parámetros de coste se declaran en configuración y se revisan.</b> Endurecerlos no
 * invalida los resúmenes ya guardados: Argon2 los codifica dentro del propio resumen, de modo que
 * una contraseña cifrada con parámetros antiguos sigue verificándose y solo se recifra cuando su
 * titular la cambia.
 */
@Component
public class PasswordHasher {

  private final PasswordEncoder encoder;

  public PasswordHasher(
      @Value("${nexus.security.password.argon2.salt-length:16}") int longitudSal,
      @Value("${nexus.security.password.argon2.hash-length:32}") int longitudResumen,
      @Value("${nexus.security.password.argon2.parallelism:1}") int paralelismo,
      @Value("${nexus.security.password.argon2.memory-kb:16384}") int memoriaKb,
      @Value("${nexus.security.password.argon2.iterations:2}") int iteraciones) {
    this.encoder =
        new Argon2PasswordEncoder(
            longitudSal, longitudResumen, paralelismo, memoriaKb, iteraciones);
  }

  /**
   * Cifra la contraseña.
   *
   * <p>La cadena devuelta lleva dentro la sal y los parámetros, de modo que es autosuficiente para
   * verificarse después. No hay que guardar nada más.
   */
  public String hash(String contrasena) {
    return encoder.encode(contrasena);
  }

  /**
   * ¿Coincide la contraseña con el resumen guardado?
   *
   * @param resumen el valor de {@code password_hash}; si es nulo o no tiene el formato de Argon2,
   *     la comprobación devuelve {@code false} sin lanzar — un dato corrupto en esa columna no debe
   *     convertirse en un {@code 500} durante un inicio de sesión
   */
  public boolean matches(String contrasena, String resumen) {
    if (contrasena == null || resumen == null || resumen.isBlank()) {
      return false;
    }
    try {
      return encoder.matches(contrasena, resumen);
    } catch (IllegalArgumentException resumenCorrupto) {
      return false;
    }
  }
}
