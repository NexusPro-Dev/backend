package com.factech.nexus.modules.system.auth.domain.service;

import com.factech.nexus.modules.system.auth.domain.models.MfaFactor;
import com.factech.nexus.modules.system.auth.domain.models.RecoveryCode;
import com.factech.nexus.modules.system.auth.domain.repository.MfaFactorRepository;
import com.factech.nexus.modules.system.auth.infrastructure.MfaSecrets;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.security.PasswordHasher;
import com.factech.nexus.shared.security.Totp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.OptionalLong;
import org.springframework.stereotype.Component;

/**
 * Comprueba un código del segundo factor contra el factor <b>activo</b> de una persona
 * (`RN-SP-060`, `RN-SP-061`). Lo usan el segundo paso del inicio de sesión (`RF-SP-072`) y la
 * reverificación (`RF-SP-073`).
 *
 * <p>Quien llama trae el factor <b>bloqueado</b>: aceptar un código escribe {@code last_used_step}
 * o {@code used_at}, y dos peticiones simultáneas con el mismo código tienen que ordenarse en la
 * fila para que solo pase una.
 *
 * <p><b>No cuenta el fallo</b>: eso es de {@link CredentialFailures}, que sabe de la cuenta. Esta
 * clase solo sabe si el código vale.
 */
@Component
public class SecondFactorVerifier {

  private final MfaFactorRepository factores;
  private final MfaSecrets secretos;
  private final PasswordHasher hasher;

  public SecondFactorVerifier(
      MfaFactorRepository factores, MfaSecrets secretos, PasswordHasher hasher) {
    this.factores = factores;
    this.secretos = secretos;
    this.hasher = hasher;
  }

  /**
   * Exactamente uno de los dos, con forma reconocible: si no, error de formato <b>antes</b> de
   * gastar nada (`VAL-002` de `RF-SP-072`, `VAL-001` de `RF-SP-073`).
   */
  public static void exigirUnCodigo(String codigo, String codigoDeRecuperacion, String error) {
    boolean hayCodigo = codigo != null && !codigo.isBlank();
    boolean hayRecuperacion = codigoDeRecuperacion != null && !codigoDeRecuperacion.isBlank();
    boolean valido =
        hayCodigo != hayRecuperacion
            && (hayCodigo
                ? codigo.matches("\\d{6}")
                : RecoveryCodeIssuer.pareceCodigoDeRecuperacion(codigoDeRecuperacion));
    if (!valido) {
      String mensaje = "Indique el código de su aplicación o un código de recuperación.";
      throw new ValidationException(
          error, mensaje, List.of(new FieldError("code", error, mensaje)));
    }
  }

  /**
   * ¿Vale? Si vale, lo deja usado. Las dos formas ya pasaron {@link #exigirUnCodigo}.
   *
   * <p>Con un código de recuperación se comparan <b>todos</b> los vigentes del factor —diez como
   * mucho— aunque el primero case: cortar diría, por el tiempo de respuesta, cuántos quedan antes
   * del que casó.
   */
  public Resultado verificar(
      MfaFactor factor, String codigo, String codigoDeRecuperacion, OffsetDateTime ahora) {

    if (codigo != null && !codigo.isBlank()) {
      String secreto = secretos.descifrar(factor.getSecretCiphertext(), factor.getUserId());
      OptionalLong periodo =
          Totp.periodoQueCasa(secreto, codigo, ahora.toInstant(), factor.getLastUsedStep());
      if (periodo.isEmpty()) {
        return Resultado.NO_VALE;
      }
      factor.usarPeriodo(periodo.getAsLong(), ahora);
      factores.sincronizar();
      return new Resultado(true, null);
    }

    String normalizado = RecoveryCodeIssuer.normalizar(codigoDeRecuperacion);
    List<RecoveryCode> vigentes = factores.codigosVigentesParaActualizar(factor.getId());
    RecoveryCode casado = null;
    for (RecoveryCode candidato : vigentes) {
      if (hasher.matches(normalizado, candidato.getCodeHash()) && casado == null) {
        casado = candidato;
      }
    }
    if (casado == null) {
      return Resultado.NO_VALE;
    }
    casado.usar(ahora);
    factores.sincronizar();
    return new Resultado(true, vigentes.size() - 1);
  }

  /**
   * @param codigosRestantes cuántos códigos de recuperación quedan, si se usó uno; nulo si se usó
   *     el de la app
   */
  public record Resultado(boolean vale, Integer codigosRestantes) {
    static final Resultado NO_VALE = new Resultado(false, null);

    public boolean conCodigoDeRecuperacion() {
      return codigosRestantes != null;
    }
  }
}
