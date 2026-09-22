package com.factech.nexus.modules.system.users.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * El cuerpo del registro por enlace (`RF-SP-045`).
 *
 * <p><b>El país va por código ISO alfa-3 y el tipo de documento por abreviación</b>: es un
 * formulario <b>público</b> al que no se le puede pedir que conozca los UUID del sistema, salvo los
 * de lo que <b>acaba de leer de un catálogo</b> — el broker y, desde el 09-09-2026, el producto.
 *
 * <p><b>Ya no lleva {@code product} ni {@code referrer}</b> (09-09-2026, por decisión del
 * responsable del proyecto): decían <b>exactamente lo mismo</b> que {@code movement.productId} y
 * {@code movement.sellerUsername}. Dos campos para un dato no son una redundancia inofensiva — son
 * <b>dos valores que pueden discrepar</b>, y el formulario necesitaba dos comprobaciones —`VAL-016`
 * y la mitad de `EX-010`— cuya única razón de ser era vigilar esa discrepancia. Quitando el
 * duplicado se quitan las dos: <b>lo que no se puede expresar no hay que comprobarlo</b>.
 *
 * <p><b>{@code brokerAccounts} es una LISTA</b> (09-09-2026, por decisión del responsable del
 * proyecto): una persona puede operar con varios brokers, y declararlos todos en el registro evita
 * que tenga que volver por otra ruta que además <b>no existe</b> — `RF-SP-053` sigue sin decidirse
 * para todo lo que no sea este formulario.
 *
 * <p><b>No lleva {@code @NotEmpty} y es condicionalmente obligatoria</b> (`RN-SP-042`): se exige al
 * menos una cuando el producto del enlace es {@code BECA → BECA}, y eso <b>no cabe en una
 * anotación</b> porque depende de un dato que hay que ir a buscar. Lo comprueba el caso de uso
 * después de resolver el producto.
 */
public record SelfRegistrationRequest(
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
    List<BrokerAccount> brokerAccounts,
    // `@NotNull` Y ADEMÁS comprobado en el caso de uso, y no es duplicar por
    // duplicar: la anotación existe para que el CONTRATO diga la verdad —sin
    // ella springdoc publica `movement` como opcional y el cliente generado del
    // frontend lo declara opcional—, y la del caso de uso porque el servicio se
    // puede llamar sin pasar por el controlador. Al revés que `brokerAccounts`,
    // que es CONDICIONAL de verdad (`RN-SP-042`) y por eso no puede llevarla.
    @NotNull(message = "VAL-015: Debe indicar los datos del movimiento.") @Valid
        Movement movement) {

  /** Nunca nula: una lista ausente y una vacía significan lo mismo. */
  public List<BrokerAccount> cuentas() {
    return brokerAccounts == null ? List.of() : brokerAccounts;
  }

  /**
   * Una cuenta en un broker.
   *
   * @param brokerId el broker, <b>por identificador</b>: se elige de un desplegable que el
   *     formulario acaba de leer del catálogo público, de modo que ya lo tiene en la mano. Y es lo
   *     correcto por lo que el catálogo declara de sí mismo — el nombre es su clave de negocio y
   *     renombrar un broker es una migración
   * @param accountId el identificador de la persona <b>en ese broker</b>: el número de cuenta
   */
  @Schema(name = "RegistrationBrokerAccount")
  public record BrokerAccount(UUID brokerId, String accountId) {}

  /**
   * El movimiento que el registro deja anotado (09-09-2026).
   *
   * <h2>Se publica como {@code RegistrationMovement} y NO como {@code Movement}</h2>
   *
   * <p>En el contrato los esquemas viven en un espacio de nombres <b>plano y compartido por todos
   * los módulos</b>, y {@code Movement} es <b>el nombre del agregado de `MV`</b>. El día que `MV`
   * publique el suyo, springdoc emitiría {@code Movement} y {@code Movement_1} <b>sin garantizar
   * cuál es cuál</b>: el cliente generado del frontend cambiaría de tipo sin que nada fallara. Es
   * el mismo cuidado que `RF-MV-001` tuvo con su «línea», publicada como {@code SaleLineRequest}, y
   * aquí el riesgo es <b>mayor</b> porque la colisión no es hipotética — el otro nombre ya existe
   * en el sistema.
   *
   * <p><b>Se envía SIEMPRE, también en el enlace gratuito</b>, por decisión del responsable del
   * proyecto: todo alta deja rastro de qué se vendió.
   *
   * <p><b>Y es DE DÓNDE SALE EL ENLACE</b>, no solo lo que este anota: desde que {@code product} y
   * {@code referrer} desaparecieron del primer nivel, el producto del enlace es {@code productId} y
   * quien lo compartió es {@code sellerUsername}. Son <b>obligatorios</b>, y por eso llevan
   * `VAL-001` y `VAL-002` — los códigos que tenían los campos que sustituyen.
   *
   * <p><b>Y nace {@code PENDIENTE}, que no es configurable</b>: el agregado de `MV` no admite otro
   * estado. De ahí sale lo que más se nota — `RN-MV-004` dice que registrar <b>no concede nada</b>
   * y `RN-MV-020` que <b>confirmar sí</b>, de modo que quien paga recibe su membresía cuando se
   * confirme el pago y no al registrarse.
   *
   * @param productId <b>el producto del enlace</b>, y lo que se vende — que desde el 09-09-2026 son
   *     el mismo dato y no dos. Va <b>por identificador</b>, como {@code brokerId} y por lo mismo:
   *     el formulario tiene que leer el producto antes de pintarse —para decir qué se compra y a
   *     qué nivel lleva—, de modo que <b>ya lo tiene en la mano</b> cuando envía. El código sigue
   *     siendo lo que viaja en el enlace; lo que ya no viaja en el cuerpo es una segunda forma de
   *     nombrar el mismo producto
   * @param sellerUsername <b>quien compartió el enlace</b>, por nombre de usuario. Va por nombre y
   *     no por identificador porque `RN-SP-016` lo hace inmutable —un enlace impreso en un folleto
   *     sigue resolviendo dentro de dos años—, es legible, y no expone el identificador interno de
   *     un empleado en una dirección que se comparte por redes. De él salen <b>las dos cosas a la
   *     vez</b>: el superior comercial que el registro asigna y, por `RN-MV-003`, el vendedor de la
   *     venta
   * @param paymentMethodId con qué se paga, y es el <b>único campo opcional del bloque</b>
   *     (`RN-MV-022`): <b>se omite</b> cuando el producto del enlace vale cero —esa venta se anota
   *     con el pago gratuito, que asigna el sistema y que el catálogo público no devuelve— y es
   *     <b>obligatorio</b> cuando tiene importe. Quien lo decide es `MV`, mirando el importe; el
   *     registro lo reenvía tal cual llegó. Comprobarlo también aquí daría dos definiciones de
   *     cuándo hace falta pagar, y la de aquí miraría las membresías del producto y no su precio
   * @param movementTypeCode el tipo, por código. Hoy solo {@code VENTA}
   */
  @Schema(name = "RegistrationMovement")
  public record Movement(
      @NotNull(message = "VAL-001: Debe indicar el producto del enlace.") UUID productId,
      UUID paymentMethodId,
      @NotBlank(message = "VAL-002: Debe indicar quién le compartió el enlace.")
          String sellerUsername,
      String movementTypeCode) {}
}
