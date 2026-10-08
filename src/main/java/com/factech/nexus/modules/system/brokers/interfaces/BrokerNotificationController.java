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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Los avisos de los brokers (`RF-SP-078`). <b>No la llama el frontend</b>: la llama el broker, con
 * su secreto en la dirección.
 *
 * <p><b>Ni {@code @RequestBody} ni {@code @RequestParam}</b> (`plan.md` §1): con un formulario,
 * Spring reconstruye el cuerpo a partir de los parámetros —mezclando los de la dirección— y {@code
 * getParameter} lo consume. El cuerpo se lee del flujo, con su tope, y la consulta se pasa cruda.
 */
@Tag(
    name = "Avisos de los brokers",
    description =
        "Lo que avisa cada broker (RF-SP-078). Solo lo llaman los brokers, con su secreto; el"
            + " frontend no.")
@RestController
@RequestMapping("/api/v1/brokers/{name}/notifications")
public class BrokerNotificationController {

  private static final String DESCRIPCION =
      """
      **La llama el broker, no el frontend** (`RF-SP-078`). Ruta **pública**: la autentica el
      secreto propio de ese broker, en el parámetro `token` de la dirección (`RN-SP-066`). El
      aviso se guarda **entero y sin interpretar** —método, parámetros de la dirección salvo
      `token`, cabeceras salvo las de credenciales, cuerpo tal cual, tipo de contenido y origen—
      y se responde `200` sin cuerpo. **No cambia nada más**: ni cuentas de broker, ni personas.
      Dos avisos iguales se guardan dos veces. `{name}` es el nombre del broker en el catálogo
      (`GET /api/v1/brokers`), sin distinguir mayúsculas: `iqoption`, `exnova`, `exoption`.
      """;

  private final ReceiveBrokerNotificationService recepcion;

  public BrokerNotificationController(ReceiveBrokerNotificationService recepcion) {
    this.recepcion = recepcion;
  }

  @GetMapping
  @Operation(
      summary = "Recibir un aviso de un broker (datos en la dirección)",
      description = DESCRIPCION)
  @Parameter(
      name = ReceiveBrokerNotificationService.TOKEN,
      in = ParameterIn.QUERY,
      required = true,
      description = "El secreto de este broker. No se guarda.",
      schema = @Schema(type = "string"))
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Guardado; cuerpo vacío.", content = @Content),
    @ApiResponse(
        responseCode = "400",
        description = "Cuerpo de más de 64 KiB (`EX-004`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description =
            "Sin `token`, con `token` repetido o con uno que no es el de este broker (`EX-001`)."
                + " No se guarda nada",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "El broker no existe o no está activo (`EX-003`). No se guarda nada",
        content = @Content),
    @ApiResponse(
        responseCode = "503",
        description =
            "Este broker no tiene secreto configurado en este entorno (`EX-002`). No se guarda"
                + " nada",
        content = @Content)
  })
  public ResponseEntity<Void> brokerNotificationByQuery(
      @PathVariable String name, HttpServletRequest peticion) throws IOException {
    return recibir(name, peticion);
  }

  @PostMapping(consumes = "*/*")
  @Operation(
      summary = "Recibir un aviso de un broker (datos en el cuerpo)",
      description =
          DESCRIPCION
              + "\nAdmite **cualquier tipo de contenido**, de hasta 64 KiB; un formulario se"
              + " guarda tal cual, sin desarmarlo.")
  @Parameter(
      name = ReceiveBrokerNotificationService.TOKEN,
      in = ParameterIn.QUERY,
      required = true,
      description = "El secreto de este broker. No se guarda.",
      schema = @Schema(type = "string"))
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Guardado; cuerpo vacío.", content = @Content),
    @ApiResponse(
        responseCode = "400",
        description = "Cuerpo de más de 64 KiB (`EX-004`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description =
            "Sin `token`, con `token` repetido o con uno que no es el de este broker (`EX-001`)."
                + " No se guarda nada",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "El broker no existe o no está activo (`EX-003`). No se guarda nada",
        content = @Content),
    @ApiResponse(
        responseCode = "503",
        description =
            "Este broker no tiene secreto configurado en este entorno (`EX-002`). No se guarda"
                + " nada",
        content = @Content)
  })
  public ResponseEntity<Void> brokerNotificationByBody(
      @PathVariable String name, HttpServletRequest peticion) throws IOException {
    return recibir(name, peticion);
  }

  private ResponseEntity<Void> recibir(String name, HttpServletRequest peticion)
      throws IOException {
    recepcion.receive(
        name,
        new BrokerNotice(
            peticion.getMethod(),
            peticion.getQueryString(),
            cabeceras(peticion),
            leer(peticion),
            peticion.getContentType(),
            RequestContext.current().map(RequestContext::ipAddress).orElse(null)));
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
