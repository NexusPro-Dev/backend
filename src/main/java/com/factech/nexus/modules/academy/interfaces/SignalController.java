package com.factech.nexus.modules.academy.interfaces;

import com.factech.nexus.modules.academy.application.SignalRequests.Delete;
import com.factech.nexus.modules.academy.application.SignalRequests.ListFilter;
import com.factech.nexus.modules.academy.application.SignalRequests.Register;
import com.factech.nexus.modules.academy.application.SignalResponses.AvailableSignal;
import com.factech.nexus.modules.academy.application.SignalResponses.SignalResponse;
import com.factech.nexus.modules.academy.domain.service.SignalService;
import com.factech.nexus.shared.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Las señales de trading (`RF-AC-067` a `RF-AC-072`, `RN-AC-035`). Una ruta por operación, un
 * permiso por ruta (`RN-SEG-014`).
 */
@RestController
@RequestMapping("/api/v1/signals")
@Tag(
    name = "Señales",
    description =
        "Registrar señales de trading —una, varias o desde un CSV—, consultarlas, retirarlas y"
            + " ver las vigentes que abre la membresía de quien mira.")
public class SignalController {

  private static final String REGLAS =
      """
      Cada señal: `currencyPair` —hasta 20 caracteres de letras, cifras y `/ . _ -`, se guarda en
      mayúsculas (`VAL-001`)—, `signalTime` —`HH:mm` en America/Bogota (`VAL-002`)—,
      `signalType` —entero mayor o igual que cero (`VAL-003`)—, `direction` —`UP` si sube o
      `DOWN` si baja; también `SUBE`/`BAJA`, `CALL`/`PUT` o `BUY`/`SELL`, y se guarda `UP` o
      `DOWN` (`VAL-011`)— y `subscriptionType` —`FREE`,
      `PREMIUM` o `PERSONALIZED`, sin distinguir mayúsculas (`VAL-004`)—. Una `PERSONALIZED`
      lleva al menos una membresía (`VAL-005`) y las otras dos ninguna (`VAL-006`); una
      membresía que no existe, `422` `EX-002`. **Se muestra hasta `visibleUntil`**: la próxima
      vez que llega `signalTime` después de crearse —creada a las 11:00 con hora 10:00, hasta
      mañana a las 10:00—. No se corrige. Se audita cada una.
      """;

  private final SignalService senales;

  public SignalController(SignalService senales) {
    this.senales = senales;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('signals:list')")
  @Operation(
      summary = "Consultar las señales",
      description =
          """
          **Todas**, las más nuevas primero y paginadas, con sus membresías, `visibleUntil`,
          `visible` —si se muestra ahora—, `createdAt` y `deletedAt`. Filtros:
          `subscriptionType`, `visible` —`true`, las que se muestran; `false`, las que ya no— e
          `includeDeleted` —las retiradas no salen salvo con `true`—.

          Exige `signals:list`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "La página."),
    @ApiResponse(
        responseCode = "400",
        description = "Paginación (`VAL-003`) o suscripción (`VAL-004`) inválidas"),
    @ApiResponse(responseCode = "401", description = "Sin sesión (`AUTH-001`)"),
    @ApiResponse(responseCode = "403", description = "Sin el permiso (`AUTH-002`)")
  })
  public PageResponse<SignalResponse> listarSenales(
      @org.springdoc.core.annotations.ParameterObject @ModelAttribute ListFilter filtros) {
    return senales.list(filtros);
  }

  @GetMapping("/available")
  @PreAuthorize("hasAuthority('signals:view')")
  @Operation(
      summary = "Ver mis señales vigentes",
      description =
          """
          Las que **se muestran ahora** —sin retirar y antes de su `visibleUntil`— y abre la
          membresía vigente de quien mira (`RN-AC-035`): las `FREE`, con cualquier membresía;
          las `PREMIUM`, con una por encima de BECA; las `PERSONALIZED`, con una de las suyas.
          Sin membresía vigente, ninguna. Sin paginar, por `visibleUntil`, cada una con su
          `direction`.

          Exige `signals:view`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Las señales."),
    @ApiResponse(responseCode = "401", description = "Sin sesión (`AUTH-001`)"),
    @ApiResponse(responseCode = "403", description = "Sin el permiso (`AUTH-002`)")
  })
  public List<AvailableSignal> verSenalesVigentes() {
    return senales.available();
  }

  @PostMapping
  @PreAuthorize("hasAuthority('signals:create')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Registrar una señal", description = REGLAS + "\nExige `signals:create`.\n")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "La señal registrada."),
    @ApiResponse(
        responseCode = "400",
        description = "Datos inválidos (`VAL-001` a `VAL-006`, `VAL-011`)"),
    @ApiResponse(responseCode = "401", description = "Sin sesión (`AUTH-001`)"),
    @ApiResponse(responseCode = "403", description = "Sin el permiso (`AUTH-002`)"),
    @ApiResponse(responseCode = "422", description = "Una membresía no existe (`EX-002`)")
  })
  public SignalResponse registrarSenal(@RequestBody(required = false) Register peticion) {
    return senales.register(peticion);
  }

  @PostMapping("/batch")
  @PreAuthorize("hasAuthority('signals:create-batch')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Registrar varias señales a la vez",
      description =
          """
          Un **arreglo** de señales, de 1 a 500 (`VAL-007`, `VAL-008`). **Todas o ninguna**: si
          alguna falla no se registra ninguna, y la respuesta trae **todos** los errores, cada uno
          con su posición en el campo —`[3].signalTime`—. Responde las registradas en el orden
          recibido.

          """
              + REGLAS
              + "\nExige `signals:create-batch`.\n")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Las señales registradas."),
    @ApiResponse(
        responseCode = "400",
        description = "Datos inválidos (`VAL-001` a `VAL-008`, `VAL-011`)"),
    @ApiResponse(responseCode = "401", description = "Sin sesión (`AUTH-001`)"),
    @ApiResponse(responseCode = "403", description = "Sin el permiso (`AUTH-002`)"),
    @ApiResponse(responseCode = "422", description = "Una membresía no existe (`EX-002`)")
  })
  public List<SignalResponse> registrarVariasSenales(
      @RequestBody(required = false) List<Register> peticiones) {
    return senales.registerBatch(peticiones);
  }

  @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAuthority('signals:import')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Importar señales desde un CSV",
      description =
          """
          `multipart/form-data` con una parte `file`: un CSV de hasta **1 MB** y **500 señales**
          (`VAL-009`, `VAL-008`), en UTF-8 o Windows-1252, separado por comas o por punto y
          coma. **Cabecera obligatoria** con `currency_pair`, `signal_time`, `signal_type`,
          `direction` y `subscription_type`, en cualquier orden (`VAL-010`), y `memberships`
          opcional: los
          **códigos** de las membresías separados por `|`, como `ORO|VIP`. Sin filas,
          `VAL-007`. **Todas o ninguna**, como el registro de varias; cada error dice su línea
          —`lines[4].signal_time`—.

          """
              + REGLAS
              + "\nExige `signals:import`.\n")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Las señales registradas."),
    @ApiResponse(
        responseCode = "400",
        description = "Archivo o datos inválidos (`VAL-001` a `VAL-011`)"),
    @ApiResponse(responseCode = "401", description = "Sin sesión (`AUTH-001`)"),
    @ApiResponse(responseCode = "403", description = "Sin el permiso (`AUTH-002`)"),
    @ApiResponse(responseCode = "422", description = "Un código de membresía no existe (`EX-002`)")
  })
  public List<SignalResponse> importarSenales(
      @RequestPart(value = "file", required = false) MultipartFile file) {
    return senales.importCsv(bytesDe(file));
  }

  @PostMapping("/{id}/deletion")
  @PreAuthorize("hasAuthority('signals:delete')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Retirar una señal",
      description =
          """
          Baja lógica con `reason`: deja de mostrarse y guarda `deletedAt`. Se audita la
          eliminación.

          Exige `signals:delete`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Retirada."),
    @ApiResponse(
        responseCode = "400",
        description = "Sin motivo, o motivo inválido (`VAL-002`, `VAL-003`)"),
    @ApiResponse(responseCode = "401", description = "Sin sesión (`AUTH-001`)"),
    @ApiResponse(responseCode = "403", description = "Sin el permiso (`AUTH-002`)"),
    @ApiResponse(responseCode = "404", description = "No existe (`EX-001`)"),
    @ApiResponse(responseCode = "409", description = "Ya está retirada (`EX-002`)")
  })
  public void retirarSenal(@PathVariable UUID id, @RequestBody(required = false) Delete peticion) {
    senales.delete(id, peticion);
  }

  private static byte[] bytesDe(MultipartFile file) {
    if (file == null) {
      return null;
    }
    try {
      return file.getBytes();
    } catch (IOException fallo) {
      throw new UncheckedIOException("No se pudo leer el archivo de la petición.", fallo);
    }
  }
}
