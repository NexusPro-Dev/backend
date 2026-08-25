package com.factech.nexus.shared.security;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Política mínima de contraseña (`security.md` §3.2).
 *
 * <p><b>Vive en `shared` y no en cada requerimiento</b>, y no por comodidad: `RF-SP-024`,
 * `RF-SP-037`, `RF-SP-038` y `RF-SP-040` fijan credenciales, y los cuatro tienen que verificar
 * <b>exactamente lo mismo</b>. Cuatro copias divergen a la primera corrección que alguien aplique
 * en una sola.
 *
 * <p><b>La prohibición de contener el nombre de usuario o la parte local del correo</b> se añadió
 * al aprobarse el plan de `RF-SP-024`, y cubre un hueco concreto: sin ella, {@code jperez2026} era
 * una credencial válida para {@code jperez} con solo cumplir la longitud — y es la primera que un
 * atacante prueba.
 *
 * <p>La longitud mínima y la lista de contraseñas comunes se declaran en configuración, no aquí:
 * son parámetros que se revisan, no decisiones de este código.
 */
@Component
public class PasswordPolicy {

  /**
   * Lista mínima incorporada.
   *
   * <p>No pretende ser exhaustiva —una lista de verdad tiene millones de entradas y vive en un
   * recurso, no en el código—; cubre las que aparecen en cualquier volcado y deja el gancho puesto.
   * Sustituirla por una lista completa no cambia ninguna firma.
   */
  private static final Set<String> COMUNES =
      Set.of(
          "123456",
          "12345678",
          "123456789",
          "password",
          "contrasena",
          "contraseña",
          "qwerty",
          "abc123",
          "111111",
          "iloveyou",
          "admin",
          "welcome",
          "monkey",
          "dragon",
          "letmein",
          // Y algunas lo bastante largas para superar el mínimo por sí solas:
          // sin ellas, la regla de longitud taparía la de contraseña común y esta
          // no sería comprobable de forma independiente.
          "123456789012",
          "1234567890123",
          "qwertyuiop123",
          "administrator",
          "contrasena123",
          "passwordpassword",
          "iloveyouiloveyou");

  private final int longitudMinima;

  public PasswordPolicy(@Value("${nexus.security.password.min-length:12}") int longitudMinima) {
    this.longitudMinima = longitudMinima;
  }

  /**
   * Verifica la credencial contra las cuatro reglas de `security.md` §3.2.
   *
   * <p><b>Las incumplidas se devuelven TODAS juntas</b>, no la primera: son independientes entre
   * sí, y devolverlas de a una obliga a corregir la contraseña a ciegas y por tanteo.
   *
   * <p><b>El mensaje nunca repite la contraseña</b>, ni entera ni en parte. Acabaría en el cuerpo
   * de la respuesta y, con ella, en cualquier registro que lo capture.
   *
   * @param username nombre de usuario de la cuenta, para la comprobación de contención
   * @param email correo de la cuenta; se usa su parte local
   */
  public void verificar(String contrasena, String username, String email) {
    List<FieldError> incumplidas = new java.util.ArrayList<>();

    if (contrasena == null || contrasena.length() < longitudMinima) {
      incumplidas.add(
          new FieldError(
              "password",
              "VAL-008",
              "La contraseña debe tener al menos " + longitudMinima + " caracteres."));
    }

    if (contrasena != null) {
      String enMinusculas = contrasena.toLowerCase(Locale.ROOT);

      if (COMUNES.contains(enMinusculas)) {
        incumplidas.add(new FieldError("password", "VAL-008", "La contraseña es demasiado común."));
      }
      if (contiene(enMinusculas, username)) {
        incumplidas.add(
            new FieldError(
                "password", "VAL-008", "La contraseña no puede contener el nombre de usuario."));
      }
      if (contiene(enMinusculas, parteLocal(email))) {
        incumplidas.add(
            new FieldError(
                "password",
                "VAL-008",
                "La contraseña no puede contener la parte local del correo."));
      }
    }

    if (!incumplidas.isEmpty()) {
      throw new ValidationException(
          "VAL-008", "La contraseña no cumple la política de seguridad.", incumplidas);
    }
  }

  /** Sin distinguir mayúsculas, y solo cuando el valor tiene entidad suficiente para importar. */
  private static boolean contiene(String contrasenaEnMinusculas, String valor) {
    if (valor == null || valor.length() < 3) {
      return false;
    }
    return contrasenaEnMinusculas.contains(valor.toLowerCase(Locale.ROOT));
  }

  private static String parteLocal(String email) {
    if (email == null) {
      return null;
    }
    int arroba = email.indexOf('@');
    return arroba > 0 ? email.substring(0, arroba) : email;
  }
}
