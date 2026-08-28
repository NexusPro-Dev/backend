package com.factech.nexus.modules.system.users.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Qué rol de tipo vendedor porta una persona (**D-25**).
 *
 * <p>Nace con `RF-CM-001` · `T-05`. Responde <b>una sola pregunta</b>, y esa es la razón de que sea
 * una interfaz aparte de {@link UserCatalog}: una interfaz por lectura y no una fachada. Con una
 * sola, añadir un método cambiaría el contrato de todos sus consumidores y de sus dobles de prueba.
 *
 * <h2>Por qué basta esta pregunta para dos reglas</h2>
 *
 * <p>`CM` necesita saber dos cosas: si una persona <b>porta un rol concreto</b> (`RN-CM-003`) y
 * <b>cuál es su rol vendedor</b> (`RF-CM-005`). Con `RN-SP-025` —una persona no puede portar dos
 * roles de tipo vendedor— **las dos son la misma pregunta**, y por eso hay una interfaz y no dos.
 *
 * <h2>Y por qué lanza en lugar de elegir</h2>
 *
 * <p><b>`RN-SP-025` todavía no está implementada</b>, de modo que hoy una persona puede portar dos
 * roles vendedores. Ante ese caso este puerto <b>lanza</b> {@link AmbiguousSellerRoleException}, y
 * es una decisión y no un descuido: de las tres salidas posibles, devolver uno cualquiera daría un
 * porcentaje <b>plausible</b> —el error que no se ve— y devolver vacío diría «no comisiona», que es
 * falso. Fallar de forma visible es la única que no miente.
 */
public interface SellerRoleCatalog {

  /**
   * El rol de tipo vendedor que porta la persona, si porta alguno.
   *
   * @param userId identificador de la persona; un valor nulo devuelve vacío en lugar de fallar
   * @return vacío si no porta ninguno — que significa «esta persona no vende», no «falta declarar
   *     su tarifa»
   * @throws AmbiguousSellerRoleException si porta más de uno, mientras `RN-SP-025` no exista
   */
  Optional<UUID> sellerRoleOf(UUID userId);

  /**
   * Una persona porta más de un rol de tipo vendedor.
   *
   * <p>Es la señal de que `RN-SP-025` no se está cumpliendo. No se traduce a un {@code 4xx}: no es
   * un dato mal enviado por quien consulta, es un estado del sistema que no debería existir.
   */
  class AmbiguousSellerRoleException extends RuntimeException {
    public AmbiguousSellerRoleException(UUID userId) {
      super(
          "La persona %s porta más de un rol de tipo VENDEDOR, y `RN-SP-025` lo prohíbe. La"
              + " comisión no se puede resolver sin elegir de forma arbitraria.".formatted(userId));
    }
  }
}
