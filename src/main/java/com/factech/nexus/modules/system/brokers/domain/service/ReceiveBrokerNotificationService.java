package com.factech.nexus.modules.system.brokers.domain.service;

import com.factech.nexus.modules.system.brokers.application.BrokerNotice;
import com.factech.nexus.modules.system.brokers.domain.repository.BrokerNotificationRepository;
import com.factech.nexus.modules.system.brokers.infrastructure.BrokerNotificationSettings;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.ServiceUnavailableException;
import com.factech.nexus.shared.error.UnauthorizedException;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>Recibir</b> un aviso de un broker y guardarlo tal como llegó (`RF-SP-078` · `T-04`,
 * `RN-SP-066`).
 *
 * <p><b>Nada se interpreta</b>: no se busca la cuenta ni la persona, ni se mueve ningún estado. Eso
 * es `RF-SP-054`, que se escribirá con estos avisos delante. Aquí solo se comprueba que el aviso es
 * del broker —en el orden de la spec §8, y <b>antes de guardar nada</b>— y se guarda entero, sin el
 * secreto.
 */
@Service
public class ReceiveBrokerNotificationService {

  /** `VAL-002`: el tope del cuerpo. */
  public static final int MAX_BODY_BYTES = 64 * 1024;

  /** El parámetro de la dirección que lleva el secreto. Nunca se guarda. */
  public static final String TOKEN = "token";

  /** Las cabeceras que llevan credenciales: no se guardan. */
  private static final Set<String> CABECERAS_CON_CREDENCIALES =
      Set.of("authorization", "proxy-authorization", "cookie");

  private final BrokerNotificationRepository avisos;
  private final BrokerNotificationSettings secretos;
  private final UuidV7Generator ids;
  private final ObjectMapper json;

  public ReceiveBrokerNotificationService(
      BrokerNotificationRepository avisos,
      BrokerNotificationSettings secretos,
      UuidV7Generator ids,
      ObjectMapper json) {
    this.avisos = avisos;
    this.secretos = secretos;
    this.ids = ids;
    this.json = json;
  }

  @Transactional
  public void receive(UUID brokerId, BrokerNotice aviso) {
    if (!avisos.isActive(brokerId)) {
      throw new ResourceNotFoundException("EX-003", "El broker no existe o no está activo.");
    }
    String esperado =
        secretos
            .tokenOf(brokerId)
            .orElseThrow(
                () ->
                    new ServiceUnavailableException(
                        "EX-002", "Este broker no tiene secreto configurado en este entorno."));

    Map<String, List<String>> consulta = desarmar(aviso.rawQuery());
    List<String> tokens = consulta.remove(TOKEN);
    if (tokens == null || tokens.size() != 1 || !iguales(tokens.get(0), esperado)) {
      throw new UnauthorizedException("EX-001", "El aviso no trae el secreto de este broker.");
    }

    byte[] cuerpo = aviso.body() == null ? new byte[0] : aviso.body();
    if (cuerpo.length > MAX_BODY_BYTES) {
      String mensaje = "El cuerpo pasa de " + MAX_BODY_BYTES + " bytes.";
      throw new ValidationException(
          "EX-004", mensaje, List.of(new FieldError("body", "VAL-002", mensaje)));
    }

    avisos.insert(
        ids.next(),
        brokerId,
        aviso.method(),
        aJson(consulta),
        aJson(cabeceras(aviso.headers())),
        cuerpo.length == 0 ? null : new String(cuerpo, StandardCharsets.UTF_8),
        recortar(aviso.contentType(), 200),
        recortar(aviso.ipAddress(), 45));
  }

  /**
   * La cadena de consulta, desarmada a mano y en el orden de llegada (`plan.md` §4): {@code
   * getParameter} vaciaría el cuerpo de un formulario.
   */
  static Map<String, List<String>> desarmar(String crudo) {
    Map<String, List<String>> pares = new LinkedHashMap<>();
    if (crudo == null || crudo.isEmpty()) {
      return pares;
    }
    for (String par : crudo.split("&")) {
      if (par.isEmpty()) {
        continue;
      }
      int igual = par.indexOf('=');
      String nombre = descodificar(igual < 0 ? par : par.substring(0, igual));
      String valor = igual < 0 ? "" : descodificar(par.substring(igual + 1));
      pares.computeIfAbsent(nombre, n -> new ArrayList<>()).add(valor);
    }
    return pares;
  }

  private static String descodificar(String texto) {
    try {
      return URLDecoder.decode(texto, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException malformado) {
      // Un `%` suelto: se guarda tal cual llegó, que es la regla.
      return texto;
    }
  }

  private static Map<String, List<String>> cabeceras(Map<String, List<String>> crudas) {
    Map<String, List<String>> limpias = new LinkedHashMap<>();
    if (crudas == null) {
      return limpias;
    }
    crudas.forEach(
        (nombre, valores) -> {
          String clave = nombre.toLowerCase(Locale.ROOT);
          if (!CABECERAS_CON_CREDENCIALES.contains(clave)) {
            limpias.computeIfAbsent(clave, c -> new ArrayList<>()).addAll(valores);
          }
        });
    return limpias;
  }

  /** `VAL-001`, en tiempo constante. */
  private static boolean iguales(String recibido, String esperado) {
    return MessageDigest.isEqual(
        recibido.getBytes(StandardCharsets.UTF_8), esperado.getBytes(StandardCharsets.UTF_8));
  }

  private String aJson(Map<String, List<String>> mapa) {
    try {
      return json.writeValueAsString(mapa);
    } catch (JsonProcessingException imposible) {
      throw new IllegalStateException("No se pudo serializar el aviso", imposible);
    }
  }

  private static String recortar(String texto, int maximo) {
    return texto == null || texto.length() <= maximo ? texto : texto.substring(0, maximo);
  }
}
