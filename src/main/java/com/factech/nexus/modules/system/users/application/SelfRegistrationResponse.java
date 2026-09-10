package com.factech.nexus.modules.system.users.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/**
 * Lo que el registro por enlace devuelve (`RF-SP-045`).
 *
 * <p><b>No lleva credenciales de sesión</b> (`CA-SP-521`): registrarse no es iniciar sesión. Quien
 * acaba de crear su cuenta pasa por `RF-SP-034` como todo el mundo — devolver un token aquí
 * duplicaría la emisión de sesiones en un endpoint público.
 *
 * <p><b>Sí lleva el estado, y con él lo que falta para operar.</b> Una respuesta que solo dijera
 * «creado» dejaría a quien se registra sin saber por qué no puede hacer nada. Y desde el 09-09-2026
 * el estado <b>ya no es siempre el mismo</b>: el enlace gratuito nace {@code FTD_PENDIENTE} —a la
 * espera del primer depósito— y el de pago nace {@code ACTIVO}, de modo que {@code pending} <b>solo
 * tiene texto cuando falta algo</b>.
 *
 * <p><b>Y lleva el código de la venta</b> ({@code sale}), porque desde el 09-09-2026 todo registro
 * anota un movimiento. Va {@code PENDIENTE} —el agregado de `MV` no admite otro estado al nacer—, y
 * es lo que quien se registra necesita para que le confirmen el pago.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record SelfRegistrationResponse(
    UUID id, String username, String status, String pending, String sale) {

  /** A la espera del primer depósito: el camino {@code BECA → BECA}. */
  private static final String FALTA_FTD =
      "Su cuenta quedará operativa cuando se confirme su primer depósito.";

  /**
   * Pagado pero sin confirmar: la membresía comprada llega al confirmarse la venta (`RN-MV-020`).
   */
  private static final String FALTA_PAGO =
      "Su membresía se activará cuando se confirme el pago de su compra.";

  /**
   * Construye la respuesta a partir de <b>lo que la cuenta acabó siendo</b>.
   *
   * <p>El texto de {@code pending} <b>se deriva del estado y no se recibe</b>: son dos formas de
   * decir lo mismo, y dejar que el llamador las eligiera por separado permitiría una respuesta que
   * dijera {@code ACTIVO} y a la vez que falta el depósito.
   */
  public static SelfRegistrationResponse de(UUID id, String username, String status, String sale) {
    return new SelfRegistrationResponse(
        id, username, status, "FTD_PENDIENTE".equals(status) ? FALTA_FTD : FALTA_PAGO, sale);
  }
}
