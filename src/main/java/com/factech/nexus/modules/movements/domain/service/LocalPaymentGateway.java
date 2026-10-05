package com.factech.nexus.modules.movements.domain.service;

import java.util.Optional;
import java.util.UUID;

/**
 * <b>La pasarela local</b> —hoy PayRetailers— (`architecture.md` §15.5, `requirements/mv.md`
 * §4.10): cobra en moneda local en su propia página de pago.
 *
 * <p><b>Un puerto propio y no {@link CardGateway}</b>: la tarjeta se confirma en la app y se
 * notifica firmada; aquí el cliente se va a otra página y <b>los avisos no se firman</b>. Por eso
 * no hay {@code verify}: lo que se cree es {@link #findByTracking}, una consulta autenticada con
 * nuestras credenciales (`RN-MV-064`).
 *
 * <p><b>Una tienda por país</b>: PayRetailers da un {@code shopId} con su clave por país. Viven en
 * la conversión del país (`RN-MV-063`) y quien llama los pasa en cada llamada: abrir con la tienda
 * de la conversión vigente, consultar con la de la conversión con que se abrió el cobro.
 */
public interface LocalPaymentGateway {

  /** El nombre con que se marca el método que esta pasarela cobra. */
  String NOMBRE = "PAYRETAILERS";

  /**
   * Si hay pasarela local en este entorno: la Subscription Key de la API y la llave que descifra
   * las claves de las tiendas. Que un país cobre depende, además, de que su conversión tenga
   * tienda.
   */
  boolean enabled();

  /**
   * Abre el cobro en la tienda y devuelve su identificador y la dirección de la página de pago.
   *
   * @throws Unavailable si la pasarela no respondió
   * @throws Rejected si la pasarela rechazó el cobro, con su motivo
   */
  LocalCharge open(Shop tienda, LocalChargeOrder orden);

  /**
   * La transacción del pago, buscada por <b>nuestra</b> referencia —el identificador del pago—.
   * Vacía si el cliente todavía no eligió método en la página: aún no hay transacción.
   *
   * @param tienda la que abrió el cobro
   * @throws Unavailable si la pasarela no respondió
   */
  Optional<LocalTransaction> findByTracking(UUID paymentId, Shop tienda);

  /**
   * Del aviso, <b>solo</b> la referencia propia que trae —el identificador del pago— y su estado
   * anunciado, que no se cree. Vacío si el aviso no se puede leer.
   */
  Optional<Notice> parse(String payload);

  /**
   * La tienda de un país, con su clave <b>en claro</b>: solo vive lo que dura la llamada. Su {@code
   * toString} no la muestra, para que ningún registro la escriba.
   */
  record Shop(String shopId, String secretKey) {
    @Override
    public String toString() {
      return "Shop[shopId=" + shopId + ", secretKey=***]";
    }
  }

  /**
   * Lo que se pide a la pasarela.
   *
   * @param amountMinor el importe en la unidad mínima de la moneda local (centavos)
   */
  record LocalChargeOrder(
      UUID paymentId, String movementCode, long amountMinor, String currency, Payer payer) {}

  /** Quien paga, tal como está en su ficha. El país, en ISO 3166-1 alfa-3. */
  record Payer(
      String firstName,
      String lastName,
      String email,
      String personalId,
      String phone,
      String countryAlpha3) {}

  record LocalCharge(String reference, String checkoutUrl) {}

  /** Lo que la pasarela dice, en cuatro desenlaces y con lo que cobró. */
  record LocalTransaction(
      String uid, Outcome outcome, String rawStatus, Long amountMinor, String currency) {}

  /** El aviso, sin creerlo. */
  record Notice(UUID paymentId, String externalId, String announcedStatus) {}

  enum Outcome {
    APROBADO,
    /** Fallido, rechazado, cancelado o caducado: ese cobro no se reintenta. */
    FINAL_NO_APROBADO,
    PENDIENTE
  }

  final class Unavailable extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public Unavailable(String mensaje, Throwable causa) {
      super(mensaje, causa);
    }
  }

  final class Rejected extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public Rejected(String mensaje) {
      super(mensaje);
    }
  }
}
