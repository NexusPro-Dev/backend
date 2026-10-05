package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.CountryConversionRateResponse;
import com.factech.nexus.modules.movements.application.SetCountryConversionRateRequest;
import com.factech.nexus.modules.movements.domain.repository.CountryConversionRateRepository;
import com.factech.nexus.modules.movements.domain.repository.CountryConversionRateRepository.ConversionRow;
import com.factech.nexus.modules.system.countries.application.CountryCatalog;
import com.factech.nexus.modules.system.countries.application.CountryCatalog.CountryView;
import com.factech.nexus.modules.system.currencies.application.CurrencyCatalog;
import com.factech.nexus.modules.system.currencies.application.CurrencyCatalog.CurrencyView;
import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ServiceUnavailableException;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import com.factech.nexus.shared.error.ValidationException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fijar (`RF-MV-046`) y consultar (`RF-MV-047`) la conversión de cada país, con su precio de cobro
 * y su precio de retiro (`RN-MV-062`). Calcado de {@link PointsRateService}: fijar inserta una fila
 * nueva y la anterior no se toca.
 *
 * <p><b>La tienda de la pasarela local va en la conversión</b> (`RN-MV-063`): PayRetailers da una
 * por país. Se fija con los precios, se hereda de la vigente si no se manda, y su clave secreta se
 * guarda cifrada ({@link ShopSecrets}) y no se devuelve ni se audita nunca.
 */
@Service
public class CountryConversionRateService {

  private static final String MODULO = "MV";
  private static final String ENTIDAD = "country_conversion_rates";

  /** Los de la columna, `numeric(14,4)`. */
  private static final int DECIMALES = 4;

  private static final int ENTEROS = 10;

  /** Los de la columna, `varchar(64)`. */
  private static final int LARGO_TIENDA = 64;

  private static final int LARGO_CLAVE = 512;

  private final CountryConversionRateRepository conversiones;
  private final CountryCatalog paises;
  private final CurrencyCatalog monedas;
  private final AuthenticatedActor actor;
  private final AuditWriter auditoria;
  private final ShopSecrets secretos;
  private final Clock reloj;

  @Autowired
  public CountryConversionRateService(
      CountryConversionRateRepository conversiones,
      CountryCatalog paises,
      CurrencyCatalog monedas,
      AuthenticatedActor actor,
      AuditWriter auditoria,
      ShopSecrets secretos) {
    this(conversiones, paises, monedas, actor, auditoria, secretos, Clock.systemUTC());
  }

  CountryConversionRateService(
      CountryConversionRateRepository conversiones,
      CountryCatalog paises,
      CurrencyCatalog monedas,
      AuthenticatedActor actor,
      AuditWriter auditoria,
      ShopSecrets secretos,
      Clock reloj) {
    this.conversiones = conversiones;
    this.paises = paises;
    this.monedas = monedas;
    this.actor = actor;
    this.auditoria = auditoria;
    this.secretos = secretos;
    this.reloj = reloj;
  }

  public record SetResult(CountryConversionRateResponse rate, boolean created) {}

  // ---------------------------------------------------------------------------
  // `RF-MV-046` — fijar
  // ---------------------------------------------------------------------------

  @Transactional
  public SetResult set(SetCountryConversionRateRequest peticion) {
    // 1. Los datos, con los errores juntos, antes de consultar nada.
    UUID paisId = peticion == null ? null : peticion.countryId();
    UUID monedaId = peticion == null ? null : peticion.currencyId();
    BigDecimal cobro = peticion == null ? null : peticion.payInPrice();
    BigDecimal retiro = peticion == null ? null : peticion.payoutPrice();
    String tiendaPedida =
        peticion == null || peticion.shopId() == null ? null : peticion.shopId().trim();
    String clavePedida = peticion == null ? null : peticion.secretKey();
    List<FieldError> errores = new ArrayList<>();
    if (paisId == null) {
      errores.add(new FieldError("countryId", "VAL-001", "El país es obligatorio."));
    }
    if (monedaId == null) {
      errores.add(new FieldError("currencyId", "VAL-002", "La moneda local es obligatoria."));
    }
    String problema = problemaDelPrecio(cobro, "de cobro");
    if (problema != null) {
      errores.add(new FieldError("payInPrice", "VAL-003", problema));
    }
    problema = problemaDelPrecio(retiro, "de retiro");
    if (problema != null) {
      errores.add(new FieldError("payoutPrice", "VAL-004", problema));
    }
    if (tiendaPedida != null && (tiendaPedida.isEmpty() || tiendaPedida.length() > LARGO_TIENDA)) {
      errores.add(
          new FieldError(
              "shopId",
              "VAL-005",
              "La tienda no puede ir vacía y admite hasta " + LARGO_TIENDA + " caracteres."));
    }
    if (clavePedida != null && (clavePedida.isBlank() || clavePedida.length() > LARGO_CLAVE)) {
      errores.add(
          new FieldError(
              "secretKey",
              "VAL-006",
              "La clave secreta no puede ir vacía y admite hasta " + LARGO_CLAVE + " caracteres."));
    }
    if (!errores.isEmpty()) {
      throw new ValidationException(errores.get(0).code(), errores.get(0).message(), errores);
    }

    // 2. El país y la moneda: existen (`EX-002`) y están activos (`EX-003`).
    CountryView pais =
        paises.find(paisId).orElseThrow(() -> noExiste("countryId", "El país indicado no existe."));
    CurrencyView moneda =
        monedas
            .find(monedaId)
            .orElseThrow(() -> noExiste("currencyId", "La moneda indicada no existe."));
    if (!pais.active()) {
      throw conflicto(
          "EX-003", "countryId", "El país " + pais.code() + " está inactivo: no opera.");
    }
    if (!moneda.active()) {
      throw conflicto(
          "EX-003", "currencyId", "La moneda " + moneda.code() + " está inactiva: no opera.");
    }

    // 3. La base es la moneda por omisión de este momento, y no puede ser la local (`EX-004`).
    CurrencyView base =
        monedas
            .findDefault()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No hay moneda por omisión: la conversión no tiene de qué convertir."));
    if (base.id().equals(moneda.id())) {
      throw conflicto(
          "EX-004",
          "currencyId",
          "La moneda local no puede ser la moneda base (" + base.code() + ").");
    }

    // 4. La tienda (`RN-MV-063`): la pedida o la vigente, y su clave, la pedida o la vigente de
    // esa misma tienda. Una tienda nueva necesita su clave, y una clave necesita una tienda.
    OffsetDateTime ahora = OffsetDateTime.now(reloj);
    Optional<ConversionRow> vigente = conversiones.current(pais.id(), ahora);
    String tiendaVigente = vigente.map(ConversionRow::shopId).orElse(null);
    String tienda = tiendaPedida != null ? tiendaPedida : tiendaVigente;
    boolean mismaTienda = tienda != null && tienda.equals(tiendaVigente);
    if (clavePedida != null && tienda == null) {
      throw invalida("secretKey", "Una clave secreta sin tienda no sirve: indique `shopId`.");
    }
    if (clavePedida == null && tienda != null && !mismaTienda) {
      throw invalida(
          "secretKey", "La tienda " + tienda + " es nueva en este país: indique su clave secreta.");
    }
    if (clavePedida != null && !secretos.ready()) {
      String mensaje =
          "Falta la llave que cifra las claves de las tiendas (PAYRETAILERS_ENCRYPTION_KEY):"
              + " no se puede guardar la clave.";
      throw new ServiceUnavailableException(
          "EX-006", mensaje, List.of(new FieldError("secretKey", "EX-006", mensaje)));
    }

    // 5. `FA-001`: la que rige ya es esa. Nada que escribir ni que auditar.
    if (vigente.isPresent()
        && esLaMisma(vigente.get(), moneda.id(), cobro, retiro)
        && mismaOSinTienda(tienda, tiendaVigente)
        && (clavePedida == null || esLaMismaClave(vigente.get(), clavePedida))) {
      return new SetResult(CountryConversionRateResponse.from(vigente.get()), false);
    }
    String clave =
        clavePedida != null
            ? secretos.encrypt(clavePedida, pais.id())
            : mismaTienda ? vigente.get().shopSecretKey() : null;
    boolean claveCambiada = clavePedida != null;

    // 6. La nueva, que rige desde ahora. La anterior no se toca (`RN-MV-062`).
    UUID id = UUID.randomUUID();
    BigDecimal cobroEscalado = cobro.setScale(DECIMALES);
    BigDecimal retiroEscalado = retiro.setScale(DECIMALES);
    conversiones.insert(
        id,
        pais.id(),
        moneda.id(),
        base.id(),
        cobroEscalado,
        retiroEscalado,
        tienda,
        clave,
        ahora,
        actor.id());

    Map<String, Object> antes = new LinkedHashMap<>();
    antes.put("currency_id", vigente.map(r -> r.currencyId().toString()).orElse(null));
    antes.put("pay_in_price", vigente.map(r -> r.payInPrice().toPlainString()).orElse(null));
    antes.put("payout_price", vigente.map(r -> r.payoutPrice().toPlainString()).orElse(null));
    antes.put("shop_id", tiendaVigente);
    Map<String, Object> despues = new LinkedHashMap<>();
    despues.put("country_id", pais.id().toString());
    despues.put("currency_id", moneda.id().toString());
    despues.put("base_currency_id", base.id().toString());
    despues.put("pay_in_price", cobroEscalado.toPlainString());
    despues.put("payout_price", retiroEscalado.toPlainString());
    despues.put("shop_id", tienda);
    // La clave, NUNCA: ni en claro ni cifrada. Solo si cambió.
    despues.put("shop_secret_key_changed", claveCambiada);
    despues.put("valid_from", ahora.toString());
    Map<String, Object> cambios = new LinkedHashMap<>();
    cambios.put("before", antes);
    cambios.put("after", despues);
    auditoria.recordChange(new ChangeEvent(MODULO, ENTIDAD, id, ChangeAction.CREATE, cambios));

    return new SetResult(
        new CountryConversionRateResponse(
            id,
            new CountryConversionRateResponse.CountryRef(pais.id(), pais.code(), pais.name()),
            new CountryConversionRateResponse.CurrencyRef(
                moneda.id(), moneda.code(), moneda.decimalPlaces()),
            new CountryConversionRateResponse.CurrencyRef(
                base.id(), base.code(), base.decimalPlaces()),
            cobroEscalado,
            retiroEscalado,
            tienda,
            clave != null,
            ahora),
        true);
  }

  private static boolean mismaOSinTienda(String tienda, String tiendaVigente) {
    return tienda == null ? tiendaVigente == null : tienda.equals(tiendaVigente);
  }

  /** Si la clave pedida es la guardada. Si la guardada no se descifra, no lo es: se reescribe. */
  private boolean esLaMismaClave(ConversionRow vigente, String clave) {
    if (vigente.shopSecretKey() == null) {
      return false;
    }
    try {
      return clave.equals(secretos.decrypt(vigente.shopSecretKey(), vigente.countryId()));
    } catch (IllegalStateException ilegible) {
      return false;
    }
  }

  private static UnprocessableEntityException invalida(String campo, String mensaje) {
    return new UnprocessableEntityException(
        "EX-005", mensaje, List.of(new FieldError(campo, "EX-005", mensaje)));
  }

  /** Misma moneda local y mismos dos precios, comparados por valor y no por escala. */
  private static boolean esLaMisma(
      ConversionRow vigente, UUID moneda, BigDecimal cobro, BigDecimal retiro) {
    return vigente.currencyId().equals(moneda)
        && vigente.payInPrice().compareTo(cobro) == 0
        && vigente.payoutPrice().compareTo(retiro) == 0;
  }

  private static String problemaDelPrecio(BigDecimal valor, String cual) {
    if (valor == null) {
      return "El precio " + cual + " es obligatorio.";
    }
    if (valor.signum() <= 0) {
      return "El precio " + cual + " tiene que ser mayor que cero.";
    }
    BigDecimal limpio = valor.stripTrailingZeros();
    if (limpio.scale() > DECIMALES) {
      return "El precio " + cual + " admite hasta " + DECIMALES + " decimales.";
    }
    if (limpio.precision() - limpio.scale() > ENTEROS) {
      return "El precio " + cual + " admite hasta " + ENTEROS + " cifras enteras.";
    }
    return null;
  }

  private static UnprocessableEntityException noExiste(String campo, String mensaje) {
    return new UnprocessableEntityException(
        "EX-002", mensaje, List.of(new FieldError(campo, "EX-002", mensaje)));
  }

  private static BusinessRuleException conflicto(String codigo, String campo, String mensaje) {
    return new BusinessRuleException(
        codigo, mensaje, List.of(new FieldError(campo, codigo, mensaje)));
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-047` — consultar las vigentes
  // ---------------------------------------------------------------------------

  @Transactional(readOnly = true)
  public List<CountryConversionRateResponse> current(UUID countryId) {
    return conversiones.currentOfActiveCountries(OffsetDateTime.now(reloj), countryId).stream()
        .map(CountryConversionRateResponse::from)
        .toList();
  }
}
