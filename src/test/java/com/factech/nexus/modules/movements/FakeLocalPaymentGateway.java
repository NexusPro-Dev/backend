package com.factech.nexus.modules.movements;

import com.factech.nexus.modules.movements.domain.service.LocalPaymentGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * <b>El doble de la pasarela local</b> (`RF-MV-048` · `plan.md` §11): ninguna prueba llama a
 * PayRetailers. Abre cobros en memoria, contesta a la consulta lo que cada prueba le diga, cuenta
 * las consultas —para probar que un aviso ajeno no consulta nada— y puede fallar a voluntad. La
 * lectura del aviso imita la del adaptador: {@code trackingId}, {@code uid} y {@code status}.
 *
 * <p><b>Lo importa {@code IntegrationTestBase} para todas las suites, APAGADO</b>, como {@link
 * FakeCardGateway}: la suite que lo usa lo enciende y lo devuelve apagado.
 */
public class FakeLocalPaymentGateway implements LocalPaymentGateway {

  private final ObjectMapper json = new ObjectMapper();
  private final List<LocalChargeOrder> abiertos = new ArrayList<>();
  private final Map<UUID, LocalTransaction> respuestas = new ConcurrentHashMap<>();
  private final List<UUID> consultas = new ArrayList<>();
  private final java.util.Set<UUID> sinRespuesta = ConcurrentHashMap.newKeySet();
  private final AtomicInteger contador = new AtomicInteger();

  private volatile boolean encendida = false;
  private volatile boolean caida = false;
  private volatile String rechazo = null;

  /** Vuelve al estado inicial: APAGADA, sin cobros, sin respuestas y respondiendo. */
  public synchronized void reiniciar() {
    abiertos.clear();
    respuestas.clear();
    consultas.clear();
    contador.set(0);
    encendida = false;
    caida = false;
    rechazo = null;
  }

  public void encender(boolean valor) {
    encendida = valor;
  }

  /** Con {@code true}, toda llamada falla como si la pasarela no respondiera. */
  public void caer(boolean valor) {
    caida = valor;
  }

  /** La consulta de ESE pago falla como si la pasarela no respondiera; las demás, no. */
  public void sinRespuestaPara(UUID pago) {
    sinRespuesta.add(pago);
  }

  /** Con un motivo, abrir el cobro se rechaza con él; con nulo, se abre. */
  public void rechazar(String motivo) {
    rechazo = motivo;
  }

  /** Lo que contestará la consulta del pago. */
  public void responder(UUID pago, Outcome desenlace, String estado, Long importe, String moneda) {
    respuestas.put(pago, new LocalTransaction("tx-" + pago, desenlace, estado, importe, moneda));
  }

  public synchronized List<LocalChargeOrder> abiertos() {
    return List.copyOf(abiertos);
  }

  public synchronized List<UUID> consultas() {
    return List.copyOf(consultas);
  }

  @Override
  public boolean enabled() {
    return encendida;
  }

  @Override
  public synchronized LocalCharge open(LocalChargeOrder orden) {
    if (caida) {
      throw new Unavailable("La pasarela de prueba está caída.", null);
    }
    if (rechazo != null) {
      throw new Rejected(rechazo);
    }
    abiertos.add(orden);
    int n = contador.incrementAndGet();
    return new LocalCharge("pw_" + n, "https://pago.prueba/" + n);
  }

  @Override
  public synchronized Optional<LocalTransaction> findByTracking(UUID paymentId) {
    if (caida) {
      throw new Unavailable("La pasarela de prueba está caída.", null);
    }
    consultas.add(paymentId);
    return Optional.ofNullable(respuestas.get(paymentId));
  }

  @Override
  public Optional<Notice> parse(String payload) {
    try {
      JsonNode aviso = json.readTree(payload);
      if (!aviso.hasNonNull("trackingId") || !aviso.hasNonNull("uid")) {
        return Optional.empty();
      }
      UUID pago;
      try {
        pago = UUID.fromString(aviso.get("trackingId").asText());
      } catch (IllegalArgumentException noEsNuestro) {
        pago = null;
      }
      String estado = aviso.hasNonNull("status") ? aviso.get("status").asText() : null;
      return Optional.of(new Notice(pago, aviso.get("uid").asText() + ":" + estado, estado));
    } catch (Exception ilegible) {
      return Optional.empty();
    }
  }

  @TestConfiguration
  public static class Config {
    @Bean
    @Primary
    public FakeLocalPaymentGateway fakeLocalPaymentGateway() {
      return new FakeLocalPaymentGateway();
    }
  }
}
