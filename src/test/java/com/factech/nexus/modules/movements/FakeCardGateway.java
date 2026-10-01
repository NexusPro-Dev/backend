package com.factech.nexus.modules.movements;

import com.factech.nexus.modules.movements.domain.service.CardGateway;
import com.factech.nexus.modules.movements.infrastructure.StripeEvents;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * <b>El doble de la pasarela de la tarjeta</b> (`RF-MV-040` · `plan.md` §11): ninguna prueba llama
 * a Stripe. Abre cobros en memoria —idempotentes por la clave, como Stripe—, puede fallar a
 * voluntad, y verifica una «firma» de juguete: la cadena {@link #FIRMA}. La interpretación de la
 * notificación es la real ({@link StripeEvents}).
 *
 * <p>Procesa las notificaciones <b>en el hilo que las recibe</b>: lo procesado es determinista.
 *
 * <p><b>Lo importa {@code IntegrationTestBase} para todas las suites, APAGADO</b>: así no crea un
 * contexto de Spring aparte. La suite que lo usa lo enciende y lo devuelve apagado.
 */
public class FakeCardGateway implements CardGateway {

  public static final String FIRMA = "firma-de-prueba";

  private final ObjectMapper json = new ObjectMapper();
  private final Map<String, Charge> porClave = new ConcurrentHashMap<>();
  private final Map<String, Charge> porReferencia = new ConcurrentHashMap<>();
  private final List<ChargeOrder> abiertos = new ArrayList<>();
  private final List<String> cancelados = new ArrayList<>();
  private final AtomicInteger contador = new AtomicInteger();

  private volatile boolean encendida = false;
  private volatile boolean caida = false;

  /** Vuelve al estado inicial: APAGADA, sin cobros y respondiendo. */
  public synchronized void reiniciar() {
    porClave.clear();
    porReferencia.clear();
    abiertos.clear();
    cancelados.clear();
    contador.set(0);
    encendida = false;
    caida = false;
  }

  public void encender(boolean valor) {
    encendida = valor;
  }

  /** Con {@code true}, toda llamada a la pasarela falla como si no respondiera. */
  public void caer(boolean valor) {
    caida = valor;
  }

  public synchronized List<ChargeOrder> abiertos() {
    return List.copyOf(abiertos);
  }

  public synchronized List<String> cancelados() {
    return List.copyOf(cancelados);
  }

  /** Marca un cobro como cobrado, como si la persona hubiera pagado en la app. */
  public void cobrar(String referencia) {
    Charge actual = porReferencia.get(referencia);
    porReferencia.put(referencia, new Charge(referencia, actual.clientSecret(), "succeeded"));
  }

  @Override
  public boolean enabled() {
    return encendida;
  }

  @Override
  public String name() {
    return "STRIPE";
  }

  @Override
  public synchronized Charge open(ChargeOrder orden) {
    if (caida) {
      throw new Unavailable("La pasarela de prueba está caída.", null);
    }
    Charge ya = porClave.get(orden.idempotencyKey());
    if (ya != null) {
      return porReferencia.get(ya.reference());
    }
    String referencia = "pi_prueba_" + contador.incrementAndGet();
    Charge nuevo = new Charge(referencia, referencia + "_secret_prueba", "requires_payment_method");
    porClave.put(orden.idempotencyKey(), nuevo);
    porReferencia.put(referencia, nuevo);
    abiertos.add(orden);
    return nuevo;
  }

  @Override
  public Charge retrieve(String reference) {
    if (caida) {
      throw new Unavailable("La pasarela de prueba está caída.", null);
    }
    Charge cobro = porReferencia.get(reference);
    if (cobro == null) {
      throw new Unavailable("No existe el cobro " + reference, null);
    }
    return cobro;
  }

  @Override
  public synchronized CancelResult cancel(String reference) {
    if (caida) {
      throw new Unavailable("La pasarela de prueba está caída.", null);
    }
    Charge cobro = porReferencia.get(reference);
    if (cobro != null && "succeeded".equals(cobro.status())) {
      return CancelResult.YA_COBRADO;
    }
    if (cobro != null) {
      porReferencia.put(reference, new Charge(reference, cobro.clientSecret(), "canceled"));
    }
    cancelados.add(reference);
    return CancelResult.CANCELADO;
  }

  @Override
  public GatewayEvent verify(byte[] body, String signature) {
    if (!FIRMA.equals(signature)) {
      throw new InvalidSignature("La firma no verifica.");
    }
    return parse(new String(body, StandardCharsets.UTF_8));
  }

  @Override
  public GatewayEvent parse(String payload) {
    return StripeEvents.interpretar(json, payload);
  }

  @Override
  public boolean processInline() {
    return true;
  }

  /** Se importa en las suites de la pasarela: el doble gana sobre el adaptador real. */
  @TestConfiguration
  public static class Config {
    @Bean
    @Primary
    public FakeCardGateway fakeCardGateway() {
      return new FakeCardGateway();
    }
  }
}
