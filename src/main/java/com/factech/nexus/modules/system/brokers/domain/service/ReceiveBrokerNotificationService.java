package com.factech.nexus.modules.system.brokers.domain.service;

import com.factech.nexus.modules.system.brokers.application.BrokerNotice;
import com.factech.nexus.modules.system.brokers.domain.repository.BrokerAccountWriter;
import com.factech.nexus.modules.system.brokers.domain.repository.BrokerNotificationRepository;
import com.factech.nexus.modules.system.brokers.infrastructure.BrokerNotificationSettings;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.ServiceUnavailableException;
import com.factech.nexus.shared.error.UnauthorizedException;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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

  /** El campo del aviso que dice de qué broker es (`RN-SP-069`). */
  public static final String ADVERTISER = "advertiser";

  /** El parámetro de la dirección que lleva el secreto. Nunca se guarda. */
  public static final String TOKEN = "token";

  /** Las cabeceras que llevan credenciales: no se guardan. */
  private static final Set<String> CABECERAS_CON_CREDENCIALES =
      Set.of("authorization", "proxy-authorization", "cookie");

  private final BrokerNotificationRepository avisos;
  private final BrokerNotificationSettings secretos;
  private final UuidV7Generator ids;
  private final ObjectMapper json;
  private final BrokerAccountWriter cuentas;
  private final AuditWriter auditoria;

  public ReceiveBrokerNotificationService(
      BrokerNotificationRepository avisos,
      BrokerNotificationSettings secretos,
      UuidV7Generator ids,
      ObjectMapper json,
      BrokerAccountWriter cuentas,
      AuditWriter auditoria) {
    this.avisos = avisos;
    this.secretos = secretos;
    this.ids = ids;
    this.json = json;
    this.cuentas = cuentas;
    this.auditoria = auditoria;
  }

  /**
   * La dirección común (`RN-SP-069`): un secreto para todos, y el broker según el {@code
   * advertiser} del aviso.
   *
   * <p><b>El {@code advertiser} se mira lo ÚLTIMO</b>, después del secreto y del tope: mirarlo
   * antes dejaría a quien no tiene el secreto distinguir un {@code 404} de un {@code 401} y
   * averiguar qué brokers hay en el catálogo.
   */
  @Transactional
  public void receiveCommon(BrokerNotice aviso) {
    String esperado =
        secretos
            .commonToken()
            .orElseThrow(
                () ->
                    new ServiceUnavailableException(
                        "EX-002",
                        "La dirección común de los avisos no tiene secreto configurado."));
    Map<String, List<String>> consulta = autenticado(aviso, esperado);
    byte[] cuerpo = cuerpo(aviso);

    UUID brokerId =
        anunciante(consulta, cuerpo, aviso.contentType())
            .flatMap(avisos::findActiveByAdvertiser)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-003",
                        "El aviso no dice de qué broker es, o ese broker no está activo."));
    guardar(brokerId, aviso, consulta, cuerpo);
  }

  @Transactional
  public void receive(String brokerName, BrokerNotice aviso) {
    UUID brokerId =
        avisos
            .findActiveByName(brokerName)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-003", "El broker no existe o no está activo."));
    String esperado =
        secretos
            .tokenOf(brokerId)
            .orElseThrow(
                () ->
                    new ServiceUnavailableException(
                        "EX-002", "Este broker no tiene secreto configurado en este entorno."));

    Map<String, List<String>> consulta = autenticado(aviso, esperado);
    guardar(brokerId, aviso, consulta, cuerpo(aviso));
  }

  /**
   * La consulta sin el {@code token}, si el que trae es exactamente uno y el esperado (`EX-001`).
   */
  private static Map<String, List<String>> autenticado(BrokerNotice aviso, String esperado) {
    Map<String, List<String>> consulta = desarmar(aviso.rawQuery());
    List<String> tokens = consulta.remove(TOKEN);
    if (tokens == null || tokens.size() != 1 || !iguales(tokens.get(0), esperado)) {
      throw new UnauthorizedException("EX-001", "El aviso no trae el secreto de este broker.");
    }
    return consulta;
  }

  /** El cuerpo, si no pasa del tope (`EX-004`). */
  private static byte[] cuerpo(BrokerNotice aviso) {
    byte[] cuerpo = aviso.body() == null ? new byte[0] : aviso.body();
    if (cuerpo.length > MAX_BODY_BYTES) {
      String mensaje = "El cuerpo pasa de " + MAX_BODY_BYTES + " bytes.";
      throw new ValidationException(
          "EX-004", mensaje, List.of(new FieldError("body", "VAL-002", mensaje)));
    }
    return cuerpo;
  }

  /**
   * El {@code advertiser} del aviso: en la dirección y, si no está, en un cuerpo de formulario o en
   * el primer nivel de un JSON. Exactamente uno y no vacío; si no, vacío. Solo se LEE: el aviso se
   * guarda tal cual llegó.
   */
  private Optional<String> anunciante(
      Map<String, List<String>> consulta, byte[] cuerpo, String tipo) {
    return campo(ADVERTISER, consulta, cuerpo, tipo);
  }

  /**
   * Un campo del aviso: en la dirección y, si no está, en un cuerpo de formulario o en el primer
   * nivel de un JSON. Exactamente uno y no vacío; si no, vacío.
   */
  private Optional<String> campo(
      String nombre, Map<String, List<String>> consulta, byte[] cuerpo, String tipo) {
    List<String> valores = consulta.get(nombre);
    if (valores == null && cuerpo.length > 0 && tipo != null) {
      String texto = new String(cuerpo, StandardCharsets.UTF_8);
      String medio = tipo.toLowerCase(Locale.ROOT);
      if (medio.startsWith("application/x-www-form-urlencoded")) {
        valores = desarmar(texto).get(nombre);
      } else if (medio.startsWith("application/json")) {
        try {
          JsonNode nodo = json.readTree(texto).get(nombre);
          valores = nodo != null && nodo.isValueNode() ? List.of(nodo.asText()) : null;
        } catch (JsonProcessingException | RuntimeException malformado) {
          valores = null;
        }
      }
    }
    if (valores == null || valores.size() != 1 || valores.get(0).isBlank()) {
      return Optional.empty();
    }
    return Optional.of(valores.get(0).trim());
  }

  /**
   * El aviso de registro (`RN-SP-070`, `RN-SP-072`): crea la cuenta `CONSUMIDOR` sin titular, con
   * origen en la `VENDEDOR` de su `afftrack`; si el número ya existía sin origen, se lo pone.
   * <b>Nunca falla el aviso</b>: lo que no se entiende se queda solo guardado.
   */
  private void interpretar(
      UUID brokerId, Map<String, List<String>> consulta, byte[] cuerpo, String tipo) {
    BrokerNotificationSettings.Fields nombres = secretos.fields();
    Optional<String> registro = secretos.registration();
    if (registro.isEmpty()
        || !campo(nombres.event(), consulta, cuerpo, tipo)
            .map(evento -> evento.equalsIgnoreCase(registro.get()))
            .orElse(false)) {
      return;
    }
    Optional<String> numero = campo(nombres.account(), consulta, cuerpo, tipo);
    if (numero.isEmpty() || numero.get().length() > 80) {
      return;
    }
    UUID origen =
        campo(nombres.afftrack(), consulta, cuerpo, tipo)
            .flatMap(afftrack -> cuentas.vendorAccountByAfftrack(brokerId, afftrack))
            .orElse(null);

    Optional<UUID> creada = cuentas.insertFromBroker(ids.next(), brokerId, numero.get(), origen);
    if (creada.isPresent()) {
      Map<String, Object> despues = new LinkedHashMap<>();
      despues.put("user_id", null);
      despues.put("broker_id", brokerId.toString());
      despues.put("external_id", numero.get());
      despues.put("status", "REGISTER");
      despues.put("kind", "CONSUMIDOR");
      despues.put("referrer_account_id", origen == null ? null : origen.toString());
      auditoria.recordChange(
          new ChangeEvent(
              "SP", "user_brokers", creada.get(), ChangeAction.CREATE, Map.of("after", despues)));
      return;
    }
    if (origen != null) {
      UUID completada = origen;
      cuentas
          .fillReferrer(brokerId, numero.get(), origen)
          .ifPresent(
              id -> {
                Map<String, Object> cambio = new LinkedHashMap<>();
                cambio.put("before", null);
                cambio.put("after", completada.toString());
                Map<String, Object> cambios = new LinkedHashMap<>();
                cambios.put("referrer_account_id", cambio);
                auditoria.recordChange(
                    new ChangeEvent("SP", "user_brokers", id, ChangeAction.UPDATE, cambios));
              });
    }
  }

  private void guardar(
      UUID brokerId, BrokerNotice aviso, Map<String, List<String>> consulta, byte[] cuerpo) {
    avisos.insert(
        ids.next(),
        brokerId,
        aviso.method(),
        aJson(consulta),
        aJson(cabeceras(aviso.headers())),
        cuerpo.length == 0 ? null : new String(cuerpo, StandardCharsets.UTF_8),
        recortar(aviso.contentType(), 200),
        recortar(aviso.ipAddress(), 45));
    interpretar(brokerId, consulta, cuerpo, aviso.contentType());
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
