package com.factech.nexus.modules.system.auth.domain.models;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Progresión del bloqueo por intentos fallidos, <b>con techo</b> (`security.md` §3.2).
 *
 * <p><b>El techo no es opcional</b>, y es lo único que separa una defensa de una denegación de
 * servicio: sin él, alguien puede mantener la cuenta de otra persona bloqueada indefinidamente
 * provocando fallos a propósito. La progresión castiga al atacante; el techo protege al titular.
 *
 * <p>Existe como tipo propio —y no dentro del inicio de sesión— porque <b>dos operaciones consumen
 * el mismo contador</b>: autenticarse y cambiar la propia contraseña. `RF-SP-037` · `T-03` lo exige
 * de forma expresa, y con la progresión escrita dos veces la segunda copia acabaría teniendo otro
 * techo — o ninguno.
 *
 * <p>Cierra en parte `RF-SP-034` · `T-03`, que pedía justamente este componente.
 */
public record LockoutPolicy(int intentosParaBloquear, Duration base, Duration techo) {

  /**
   * Tope de duplicaciones. Sin él, el desplazamiento de bits desborda y el techo deja de aplicar.
   */
  private static final int DUPLICACIONES_MAXIMAS = 20;

  /**
   * Hasta cuándo queda bloqueada la cuenta tras el intento número {@code intentos}.
   *
   * @return vacío mientras no se alcance el umbral: los primeros fallos solo cuentan
   */
  public Optional<OffsetDateTime> bloqueoTras(int intentos, OffsetDateTime ahora) {
    if (intentos < intentosParaBloquear) {
      return Optional.empty();
    }
    int excedente = intentos - intentosParaBloquear;
    // Progresión geométrica acotada: 1×, 2×, 4×… hasta el techo.
    long factor = 1L << Math.min(excedente, DUPLICACIONES_MAXIMAS);
    Duration espera = base.multipliedBy(factor);

    return Optional.of(ahora.plus(espera.compareTo(techo) > 0 ? techo : espera));
  }
}
