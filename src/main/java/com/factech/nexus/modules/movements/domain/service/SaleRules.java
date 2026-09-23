package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.domain.models.SaleTypeStatus;
import com.factech.nexus.modules.movements.domain.models.TypeStatus;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.MovementTypeView;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.PaymentMethodView;
import com.factech.nexus.modules.products.application.ProductCatalog.SaleView;
import com.factech.nexus.modules.system.users.application.ClientCatalog.ClientView;
import com.factech.nexus.modules.system.users.application.CurrentMembershipLookup;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Las reglas de <b>vender</b> que no dependen de qué se vende (`RN-MV-006`, `RN-MV-008`,
 * `RN-MV-022`, `EX-010` de `RF-MV-001`).
 *
 * <p>Nace el 17-09-2026 con `RF-MV-012`, <b>sacada de {@link RegisterSaleService}</b> y no copiada:
 * la compra de un paquete es un caso de uso propio —resuelve un paquete y no una lista de líneas,
 * `plan.md` §3.3— pero <b>que la cuenta pueda operar, que el upgrade no baje de nivel y que el
 * método de pago cuadre con el importe</b> son las mismas tres comprobaciones sobre los mismos
 * datos. Duplicarlas duplicaría la clase de defecto que no falla: la copia que se quedara atrás
 * seguiría registrando ventas, solo que sin comprobar algo.
 *
 * <p>No es un bean de Spring: cada caso de uso la construye con sus propios puertos, de modo que
 * las pruebas que simulan los puertos siguen valiendo tal cual.
 */
final class SaleRules {

  /** El código del pago gratuito. Literal a propósito: `V78` lo siembra en todos los entornos. */
  private static final String CODIGO_GRATUITO = "GRATIS";

  /** Hoy el único tipo del catálogo. Se busca por código y no se constantea su identificador. */
  private static final String TIPO_VENTA = "VENTA";

  /**
   * El estado que `RF-SP-045` estrena (`RN-SP-026`): la cuenta autentica y <b>no opera</b> hasta
   * que se confirme su depósito.
   */
  private static final String FTD_PENDIENTE = "FTD_PENDIENTE";

  private final MovementRepository movimientos;
  private final CurrentMembershipLookup membresias;

  SaleRules(MovementRepository movimientos, CurrentMembershipLookup membresias) {
    this.movimientos = movimientos;
    this.membresias = membresias;
  }

  // ---------------------------------------------------------------------------
  // Quién compra
  // ---------------------------------------------------------------------------

  /**
   * `RN-MV-008`: a una cuenta en {@code FTD_PENDIENTE} no se le vende.
   *
   * <p>El mensaje dice <b>qué le falta</b>, y no solo que no se puede: quien intenta vender
   * necesita saber que la salida es confirmar el depósito, no reintentar.
   *
   * @param codigo el `EX-nnn` del caso de uso que llama, porque cada spec numera el suyo
   * @param campo el campo al que se ata el error: {@code userId} cuando el cliente viene en el
   *     cuerpo; {@code status} cuando el cliente es el actor y lo que falla es su estado
   */
  void verificarQueOpera(ClientView cliente, String codigo, String campo) {
    if (FTD_PENDIENTE.equals(cliente.status())) {
      String mensaje =
          "Esa cuenta todavía no puede operar: le falta la confirmación de su depósito.";
      throw new BusinessRuleException(
          codigo, mensaje, List.of(new FieldError(campo, codigo, mensaje)));
    }
  }

  // ---------------------------------------------------------------------------
  // El nivel
  // ---------------------------------------------------------------------------

  /**
   * `RN-MV-006`: <b>no se baja de nivel</b>, y renovar el mismo <b>sí</b> se admite.
   *
   * <p><b>Se comprueba aunque la oferta ya lo garantice hoy</b>, y eso no es redundancia por exceso
   * de celo: la oferta es una decisión de `PM` y puede ampliarse —el día que se vendan renovaciones
   * del mismo nivel, por ejemplo—, mientras que «una venta no baja a nadie de nivel» es una regla
   * de `MV` que no puede depender de que otro módulo siga decidiendo lo mismo. Es lo que mantiene
   * el rechazo alcanzable: hoy no se llega por la oferta, y se llegaría el día siguiente a que `PM`
   * la ampliara.
   *
   * <p><b>Se rechaza al registrar y no al confirmar</b>, que es lo único que evita cobrarle a
   * alguien por algo que no le da nada.
   *
   * <p>La cadena crece hacia abajo: {@code 1} es la cima, de modo que <b>nivel superior es número
   * menor</b> (`requirements/sp.md` §10.4).
   *
   * @param codigo el `EX-nnn` del caso de uso que llama
   */
  void verificarQueSube(UUID clienteId, SaleView upgrade, String codigo) {
    Optional<Integer> nivelActual =
        membresias.currentMembershipOf(clienteId).map(m -> (Integer) m.level());

    // Sin membresía no hay nada por debajo de lo que subir, y no es un rechazo:
    // cualquier destino está por encima de no tener nivel. Que la oferta se lo
    // haya ofrecido ya es la decisión de `PM`.
    if (nivelActual.isEmpty()) {
      return;
    }
    Integer destino = upgrade.targetMembershipLevel();
    // MAYOR ESTRICTO, y el cambio del 07-09-2026 está en ese símbolo. Era
    // `>=`, que rechazaba también la MISMA membresía; desde que `PM` admite un
    // `X → X` eso es una RENOVACIÓN —se paga tiempo, no nivel— y registrarla es
    // legítimo (`requirements/pm.md` §5.2.3).
    //
    // Lo que se queda es la mitad que protege a quien paga: UNA VENTA NO BAJA A
    // NADIE DE NIVEL. El nulo se sigue rechazando — un upgrade sin destino no
    // debería existir, y si llega aquí es que algo se rompió antes.
    if (destino == null || destino > nivelActual.get()) {
      String mensaje =
          "El producto «%s» lleva a una membresía inferior a la que esa persona ya tiene."
              .formatted(upgrade.code());
      throw new BusinessRuleException(
          codigo, mensaje, List.of(new FieldError("lines", codigo, mensaje)));
    }
  }

  // ---------------------------------------------------------------------------
  // Con qué se paga
  // ---------------------------------------------------------------------------

  /**
   * `RN-MV-022` — importe cero y pago gratuito son lo mismo, <b>en los dos sentidos</b>.
   *
   * <p><b>Qué cierra.</b> `RN-PM-006` admite el precio cero desde el 08-09-2026 —lo tumbó la
   * renovación `BECA → BECA`— y {@code movements.payment_method_id} es {@code NOT NULL}. Desde
   * entonces, toda compra gratuita estaba <b>obligada a declarar tarjeta, PSE o puntos</b>, y las
   * tres son falsas. No fallaba nada: el padrón dejaba de poder decir qué se cobró, y `CM`
   * comisionaría sobre un cobro que nunca ocurrió.
   *
   * <p><b>La segunda mitad es la que menos se ve y la más grave.</b> Una venta <b>cobrada</b> que
   * declarara pago gratuito diría que no se cobró nada — y esa mentira va en la dirección en la que
   * alguien gana algo. Por eso el rechazo es en los dos sentidos y no solo en uno.
   *
   * @param pedido el método que llegó en el cuerpo, o {@code null} si no vino
   * @param total el importe ya calculado sobre las líneas copiadas
   */
  PaymentMethodView resolverMetodoDePago(UUID pedido, BigDecimal total) {
    if (total.signum() == 0) {
      // NO SE ADMITE ENVIARLO, y no es rigidez: el método gratuito no es
      // descubrible —`RF-MV-009` no lo devuelve (`RN-MV-023`)—, de modo que
      // cualquier valor que llegue aquí es necesariamente uno equivocado.
      if (pedido != null) {
        String mensaje =
            "Una venta de importe cero no admite método de pago: se registra como gratuita.";
        throw new BusinessRuleException(
            "RN-MV-022", mensaje, List.of(new FieldError("paymentMethodId", "RN-MV-022", mensaje)));
      }
      return metodoGratuito();
    }

    if (pedido == null) {
      String mensaje = "El método de pago es obligatorio cuando la venta tiene importe.";
      throw new BusinessRuleException(
          "RN-MV-022", mensaje, List.of(new FieldError("paymentMethodId", "RN-MV-022", mensaje)));
    }

    PaymentMethodView metodo = verificarMetodoDePago(pedido);

    // La otra mitad de `RN-MV-022`. En la práctica nadie puede llegar aquí con
    // el gratuito —el catálogo no lo publica—, y la comprobación existe igual:
    // la regla no se sostiene en que el catálogo lo esconda, sino en que el caso
    // de uso lo rechace. Esconder es una defensa; rechazar es la regla.
    if (CODIGO_GRATUITO.equals(metodo.code())) {
      String mensaje = "El pago gratuito solo vale para ventas de importe cero.";
      throw new BusinessRuleException(
          "RN-MV-022", mensaje, List.of(new FieldError("paymentMethodId", "RN-MV-022", mensaje)));
    }
    return metodo;
  }

  /**
   * El pago gratuito, por <b>código</b> y no por identificador.
   *
   * <p>Mismo criterio que {@code MembershipCatalog.floor()} con `BECA`: el literal vive en un solo
   * sitio, {@code uq_payment_methods_code} lo hace único y `V78` lo siembra en todos los entornos.
   *
   * <p><b>No devuelve vacío.</b> Que falte no es un caso de negocio que quien llama deba resolver:
   * es una base mal construida, y `V78` levanta excepción al aplicarse justamente para que no
   * llegue a ocurrir.
   */
  private PaymentMethodView metodoGratuito() {
    return movimientos
        .findPaymentMethodByCode(CODIGO_GRATUITO)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "RN-MV-022: falta el método de pago " + CODIGO_GRATUITO + " en el catálogo."));
  }

  /**
   * `EX-010`, con los dos casos separados: inexistente es {@code 422} y desactivado es {@code 409}.
   *
   * <p><b>Un método desactivado no invalida lo ya vendido con él</b> (`RN-MV-018`) — las ventas
   * viejas lo siguen referenciando y se leen con normalidad—, pero no sirve para vender hoy.
   */
  private PaymentMethodView verificarMetodoDePago(UUID id) {
    PaymentMethodView metodo =
        movimientos
            .findPaymentMethod(id)
            .orElseThrow(
                () ->
                    new UnprocessableEntityException(
                        "EX-010",
                        "El método de pago indicado no existe.",
                        List.of(
                            new FieldError(
                                "paymentMethodId",
                                "EX-010",
                                "El método de pago indicado no existe."))));

    if (!metodo.active()) {
      String mensaje = "El método de pago indicado está desactivado.";
      throw new BusinessRuleException(
          "EX-010", mensaje, List.of(new FieldError("paymentMethodId", "EX-010", mensaje)));
    }
    return metodo;
  }

  // ---------------------------------------------------------------------------
  // El tipo
  // ---------------------------------------------------------------------------

  MovementTypeView tipoDeVenta() {
    return movimientos
        .findTypeByCode(TIPO_VENTA)
        .orElseThrow(
            // No es un error del cliente: la siembra de `V54` lo garantiza, y
            // su ausencia significa que el catálogo del módulo está roto.
            () ->
                new IllegalStateException(
                    "El tipo de movimiento «%s» no está en el catálogo.".formatted(TIPO_VENTA)));
  }

  /**
   * El estado de la venta en el catálogo (`RN-MV-033`), resuelto a su identificador, que es lo que
   * se escribe.
   */
  TypeStatus estadoDeVenta(MovementTypeView tipo, SaleTypeStatus estado) {
    return movimientos
        .findTypeStatus(tipo.id(), estado.name())
        .orElseThrow(
            // Lo siembra `V36`: su ausencia es un catálogo roto, no un error del cliente.
            () ->
                new IllegalStateException(
                    "El estado «%s» de «%s» no está en el catálogo."
                        .formatted(estado, tipo.code())));
  }
}
