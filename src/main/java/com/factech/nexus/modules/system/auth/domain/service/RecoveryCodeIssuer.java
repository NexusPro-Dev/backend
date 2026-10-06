package com.factech.nexus.modules.system.auth.domain.service;

import com.factech.nexus.modules.system.auth.domain.models.RecoveryCode;
import com.factech.nexus.modules.system.auth.domain.repository.MfaFactorRepository;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import com.factech.nexus.shared.security.PasswordHasher;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Los diez códigos de recuperación de un factor (`RN-SP-061`; `071` · `plan.md` §3).
 *
 * <p><b>Diez caracteres del alfabeto de Crockford</b> —sin {@code I}, {@code L}, {@code O} ni
 * {@code U}, que se confunden al copiarlos a mano—, presentados como {@code XXXXX-XXXXX}. Cincuenta
 * bits por código: con cinco intentos por desafío, adivinar uno no es un ataque.
 *
 * <p><b>Se resumen con Argon2id</b>, como la contraseña: un código vale lo mismo que el teléfono.
 * El valor en claro se devuelve <b>una sola vez</b> a quien llama y no queda en ningún otro sitio.
 */
@Component
public class RecoveryCodeIssuer {

  public static final int CUANTOS = 10;

  private static final char[] CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
  private static final SecureRandom AZAR = new SecureRandom();

  private final MfaFactorRepository factores;
  private final PasswordHasher hasher;
  private final UuidV7Generator ids;

  public RecoveryCodeIssuer(
      MfaFactorRepository factores, PasswordHasher hasher, UuidV7Generator ids) {
    this.factores = factores;
    this.hasher = hasher;
    this.ids = ids;
  }

  /** Emite y guarda diez códigos nuevos del factor; devuelve los valores en claro. */
  public List<String> emitir(UUID factorId, OffsetDateTime ahora) {
    List<String> claros = new ArrayList<>(CUANTOS);
    List<RecoveryCode> filas = new ArrayList<>(CUANTOS);
    for (int i = 0; i < CUANTOS; i++) {
      String codigo = nuevo();
      claros.add(codigo);
      filas.add(RecoveryCode.emitir(ids.next(), factorId, hasher.hash(normalizar(codigo)), ahora));
    }
    factores.guardarCodigos(filas);
    return claros;
  }

  /**
   * ¿Tiene forma de código de recuperación? Lo distingue del código de seis dígitos de la app sin
   * mirar la base, que es lo que permite rechazar un formato desconocido antes de gastar nada.
   */
  public static boolean pareceCodigoDeRecuperacion(String valor) {
    if (valor == null) {
      return false;
    }
    String limpio = normalizar(valor);
    return limpio.length() == 10 && limpio.chars().allMatch(c -> indice((char) c) >= 0);
  }

  /**
   * Mayúsculas, sin guiones ni espacios, y los caracteres que Crockford declara equivalentes —
   * {@code I} y {@code L} valen {@code 1}, {@code O} vale {@code 0}— en su forma canónica. Quien lo
   * copia a mano no tiene que acertar con cuál de los dos parecidos escribió.
   */
  public static String normalizar(String valor) {
    return valor
        .toUpperCase(Locale.ROOT)
        .replace("-", "")
        .replace(" ", "")
        .replace('I', '1')
        .replace('L', '1')
        .replace('O', '0');
  }

  private static String nuevo() {
    StringBuilder codigo = new StringBuilder(11);
    for (int i = 0; i < 10; i++) {
      if (i == 5) {
        codigo.append('-');
      }
      codigo.append(CROCKFORD[AZAR.nextInt(CROCKFORD.length)]);
    }
    return codigo.toString();
  }

  private static int indice(char c) {
    for (int i = 0; i < CROCKFORD.length; i++) {
      if (CROCKFORD[i] == c) {
        return i;
      }
    }
    return -1;
  }
}
