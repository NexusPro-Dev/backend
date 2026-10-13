package com.factech.nexus.modules.system.brokers.interfaces;

import com.factech.nexus.modules.system.brokers.application.BrokerNotice;
import com.factech.nexus.modules.system.brokers.domain.service.ReceiveBrokerNotificationService;
import com.factech.nexus.shared.observability.RequestContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Los avisos de los brokers (`RF-SP-078`). <b>No la llama el frontend</b>: la llama el broker, con
 * el secreto en la dirección.
 *
 * <p><b>Ni {@code @RequestBody} ni {@code @RequestParam}</b> (`plan.md` §1): con un formulario,
 * Spring reconstruye el cuerpo a partir de los parámetros —mezclando los de la dirección— y {@code
 * getParameter} lo consume. El cuerpo se lee del flujo, con su tope, y la consulta se pasa cruda.
 *
 * <p><b>Una sola dirección</b> desde el 13-10-2026 (`RN-SP-076`): la ruta con el nombre del broker
 * se retiró.
 */
@Tag(
    name = "Avisos de los brokers",
    description =
        "Lo que avisa cada broker (RF-SP-078). Solo lo llaman los brokers, con el secreto; el"
            + " frontend no.")
@RestController
@RequestMapping("/api/v1/brokers")
public class BrokerNotificationController {

  private static final String DESCRIPCION =
      """
      **La llama el broker, no el frontend** (`RF-SP-078`). **La única dirección de los avisos**,
      la misma para todos los brokers (`RN-SP-069`). Ruta **pública**: la autentica **un solo
      secreto**, en el parámetro `token` de la dirección (`RN-SP-066`).

      **El broker lo dice el propio aviso** en `advertiser` —`iq_option` en los de IQ Option—: en
      la dirección o, si no viene ahí, en un cuerpo de formulario o en el primer nivel de un JSON,
      sin distinguir mayúsculas. **Si no es el de ningún broker activo, o falta, o viene repetido,
      el aviso se guarda igual, sin broker y sin interpretar** (`RN-SP-076`); se le asocia y se
      interpreta al arrancar, cuando una migración cargue ese `advertiser` en el catálogo.

      El aviso se guarda **entero y sin interpretar** —método, parámetros de la dirección salvo
      `token`, cabeceras salvo las de credenciales, cuerpo tal cual, tipo de contenido, origen y
      su `advertiser`— y se responde `200` sin cuerpo. Dos avisos iguales se guardan dos veces.
      """;

  private final ReceiveBrokerNotificationService recepcion;

  public BrokerNotificationController(ReceiveBrokerNotificationService recepcion) {
    this.recepcion = recepcion;
  }

  @GetMapping("/notifications")
  @Operation(
      summary = "Recibir un aviso de cualquier broker (datos en la dirección)",
      description = DESCRIPCION)
  @Parameter(
      name = ReceiveBrokerNotificationService.TOKEN,
      in = ParameterIn.QUERY,
      required = true,
      description = "El secreto de los avisos. No se guarda.",
      schema = @Schema(type = "string"))
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Guardado; cuerpo vacío.", content = @Content),
    @ApiResponse(
        responseCode = "400",
        description = "Cuerpo de más de 64 KiB (`EX-004`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Sin `token`, con `token` repetido o con otro (`EX-001`). No se guarda nada",
        content = @Content),
    @ApiResponse(
        responseCode = "503",
        description = "El secreto de los avisos no está configurado (`EX-002`). No se guarda nada",
        content = @Content)
  })
  public ResponseEntity<Void> commonBrokerNotificationByQuery(HttpServletRequest peticion)
      throws IOException {
    return recibir(peticion);
  }

  @PostMapping(path = "/notifications", consumes = "*/*")
  @Operation(
      summary = "Recibir un aviso de cualquier broker (datos en el cuerpo)",
      description =
          DESCRIPCION
              + "\nAdmite **cualquier tipo de contenido**, de hasta 64 KiB; un formulario se"
              + " guarda tal cual, sin desarmarlo.")
  @Parameter(
      name = ReceiveBrokerNotificationService.TOKEN,
      in = ParameterIn.QUERY,
      required = true,
      description = "El secreto de los avisos. No se guarda.",
      schema = @Schema(type = "string"))
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Guardado; cuerpo vacío.", content = @Content),
    @ApiResponse(
        responseCode = "400",
        description = "Cuerpo de más de 64 KiB (`EX-004`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Sin `token`, con `token` repetido o con otro (`EX-001`). No se guarda nada",
        content = @Content),
    @ApiResponse(
        responseCode = "503",
        description = "El secreto de los avisos no está configurado (`EX-002`). No se guarda nada",
        content = @Content)
  })
  public ResponseEntity<Void> commonBrokerNotificationByBody(HttpServletRequest peticion)
      throws IOException {
    return recibir(peticion);
  }

  private ResponseEntity<Void> recibir(HttpServletRequest peticion) throws IOException {
    BrokerNotice aviso =
        new BrokerNotice(
            peticion.getMethod(),
            peticion.getQueryString(),
            cabeceras(peticion),
            leer(peticion),
            peticion.getContentType(),
            RequestContext.current().map(RequestContext::ipAddress).orElse(null));
    recepcion.receiveCommon(aviso);
    return ResponseEntity.ok().build();
  }

  /** Hasta un byte más que el tope: lo justo para saber que se pasó, sin leer lo que sobra. */
  private static byte[] leer(HttpServletRequest peticion) throws IOException {
    try (InputStream flujo = peticion.getInputStream()) {
      return flujo.readNBytes(ReceiveBrokerNotificationService.MAX_BODY_BYTES + 1);
    }
  }

  private static Map<String, List<String>> cabeceras(HttpServletRequest peticion) {
    Map<String, List<String>> todas = new LinkedHashMap<>();
    for (String nombre : Collections.list(peticion.getHeaderNames())) {
      todas.put(nombre, Collections.list(peticion.getHeaders(nombre)));
    }
    return todas;
  }
}
