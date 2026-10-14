package com.factech.nexus.modules.academy.domain.models;

import com.factech.nexus.shared.error.FieldError;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Una señal de trading (`RF-AC-067` a `RF-AC-072`, `RN-AC-035`): par, hora, tipo y suscripción.
 *
 * <p><b>No se corrige</b>: nace, se muestra hasta {@code visibleUntil} y se retira. {@code
 * visibleUntil} es la próxima vez que llega {@code signalTime} en la zona del negocio después de
 * crearse —creada a las 11:00 con hora 10:00, hasta mañana a las 10:00—, y como la señal no cambia,
 * se calcula una vez y se guarda.
 *
 * <p>Las comprobaciones de cada campo son estáticas y <b>acumulan</b> en una lista en lugar de
 * lanzar: registrar varias a la vez devuelve todos los errores de todas las filas de una vez.
 */
@Entity
@Table(name = "signals")
public class Signal {

  private static final int PAR_MAXIMO = 20;
  private static final Pattern PAR = Pattern.compile("[A-Z0-9][A-Z0-9/._-]*");
  private static final Pattern HORA = Pattern.compile("([01]?\\d|2[0-3]):[0-5]\\d");
  private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "currency_pair", nullable = false, length = 20, updatable = false)
  private String currencyPair;

  @Column(name = "signal_time", nullable = false, updatable = false)
  private LocalTime signalTime;

  @Column(name = "signal_type", nullable = false, updatable = false)
  private int signalType;

  @Enumerated(EnumType.STRING)
  @Column(name = "subscription_type", nullable = false, length = 20, updatable = false)
  private SubscriptionType subscriptionType;

  @Column(name = "visible_until", nullable = false, updatable = false)
  private OffsetDateTime visibleUntil;

  @Column(name = "created_by", updatable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;

  /** Exigido por JPA. */
  protected Signal() {}

  /** Con los valores ya comprobados; {@code visibleUntil} sale de la hora y del momento. */
  public static Signal create(
      UUID id,
      String currencyPair,
      LocalTime signalTime,
      int signalType,
      SubscriptionType subscriptionType,
      UUID createdBy,
      OffsetDateTime ahora,
      ZoneId zona) {
    Signal senal = new Signal();
    senal.id = id;
    senal.currencyPair = currencyPair;
    senal.signalTime = signalTime;
    senal.signalType = signalType;
    senal.subscriptionType = subscriptionType;
    senal.createdBy = createdBy;
    senal.createdAt = ahora;
    senal.visibleUntil = visibleUntil(signalTime, ahora, zona);
    return senal;
  }

  /**
   * La próxima vez que llega {@code hora} en {@code zona}, <b>estrictamente</b> después de {@code
   * creada}: hoy si aún no ha llegado, mañana si ya pasó o es justo ahora.
   */
  public static OffsetDateTime visibleUntil(LocalTime hora, OffsetDateTime creada, ZoneId zona) {
    ZonedDateTime local = creada.atZoneSameInstant(zona);
    ZonedDateTime hoy = local.toLocalDate().atTime(hora).atZone(zona);
    ZonedDateTime hasta = hoy.isAfter(local) ? hoy : hoy.plusDays(1);
    return hasta.toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);
  }

  /** Retira la señal; devuelve si hubo cambio. */
  public boolean delete(OffsetDateTime ahora) {
    if (deletedAt != null) {
      return false;
    }
    deletedAt = ahora;
    return true;
  }

  public boolean estaRetirada() {
    return deletedAt != null;
  }

  /** Lo que se audita al crear y al retirar. */
  public Map<String, Object> instantanea(List<UUID> membresias) {
    Map<String, Object> estado = new LinkedHashMap<>();
    estado.put("currency_pair", currencyPair);
    estado.put("signal_time", horaTexto());
    estado.put("signal_type", signalType);
    estado.put("subscription_type", subscriptionType.name());
    estado.put("membership_ids", membresias.stream().map(UUID::toString).sorted().toList());
    estado.put("visible_until", visibleUntil.toString());
    return estado;
  }

  public String horaTexto() {
    return signalTime.format(HH_MM);
  }

  // ---------------------------------------------------------------------------
  // Comprobaciones: devuelven nulo y anotan el error en lugar de lanzar.
  // ---------------------------------------------------------------------------

  /** `VAL-001`: obligatorio, hasta 20 caracteres de letras, cifras y {@code / . _ -}. */
  public static String par(String valor, String campo, List<FieldError> errores) {
    String par = valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
    if (par.isEmpty() || par.length() > PAR_MAXIMO || !PAR.matcher(par).matches()) {
      errores.add(
          new FieldError(
              campo,
              "VAL-001",
              "El par es obligatorio, de hasta 20 caracteres: letras, cifras y / . _ -, como"
                  + " EUR/USD."));
      return null;
    }
    return par;
  }

  /** `VAL-002`: obligatoria, {@code HH:mm} de 00:00 a 23:59. */
  public static LocalTime hora(String valor, String campo, List<FieldError> errores) {
    String hora = valor == null ? "" : valor.trim();
    if (!HORA.matcher(hora).matches()) {
      errores.add(
          new FieldError(
              campo, "VAL-002", "La hora es obligatoria, en formato HH:mm de 00:00 a 23:59."));
      return null;
    }
    String[] partes = hora.split(":");
    return LocalTime.of(Integer.parseInt(partes[0]), Integer.parseInt(partes[1]));
  }

  /** `VAL-003`: obligatorio, entero mayor o igual que cero. */
  public static Integer tipo(Integer valor, String campo, List<FieldError> errores) {
    if (valor == null || valor < 0) {
      errores.add(
          new FieldError(
              campo, "VAL-003", "El tipo es obligatorio, un entero mayor o igual que cero."));
      return null;
    }
    return valor;
  }

  /** {@link #tipo(Integer, String, List)} desde el texto de un CSV. */
  public static Integer tipo(String valor, String campo, List<FieldError> errores) {
    Integer numero = null;
    if (valor != null && valor.trim().matches("\\d{1,9}")) {
      numero = Integer.valueOf(valor.trim());
    }
    return tipo(numero, campo, errores);
  }

  /** `VAL-004`: obligatoria, FREE, PREMIUM o PERSONALIZED, sin distinguir mayúsculas. */
  public static SubscriptionType suscripcion(String valor, String campo, List<FieldError> errores) {
    String texto = valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
    for (SubscriptionType tipo : SubscriptionType.values()) {
      if (tipo.name().equals(texto)) {
        return tipo;
      }
    }
    errores.add(
        new FieldError(
            campo, "VAL-004", "La suscripción es obligatoria: FREE, PREMIUM o PERSONALIZED."));
    return null;
  }

  /**
   * `VAL-005` y `VAL-006`: una {@code PERSONALIZED} lleva al menos una membresía, y las otras dos
   * ninguna.
   */
  public static void membresias(
      SubscriptionType tipo, int cuantas, String campo, List<FieldError> errores) {
    if (tipo == SubscriptionType.PERSONALIZED && cuantas == 0) {
      errores.add(
          new FieldError(
              campo, "VAL-005", "Una señal PERSONALIZED necesita al menos una membresía."));
    } else if (tipo != null && tipo != SubscriptionType.PERSONALIZED && cuantas > 0) {
      errores.add(
          new FieldError(campo, "VAL-006", "Solo una señal PERSONALIZED lleva membresías."));
    }
  }

  public UUID getId() {
    return id;
  }

  public String getCurrencyPair() {
    return currencyPair;
  }

  public LocalTime getSignalTime() {
    return signalTime;
  }

  public int getSignalType() {
    return signalType;
  }

  public SubscriptionType getSubscriptionType() {
    return subscriptionType;
  }

  public OffsetDateTime getVisibleUntil() {
    return visibleUntil;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getDeletedAt() {
    return deletedAt;
  }
}
