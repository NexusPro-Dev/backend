package com.factech.nexus.modules.indicators.interfaces;

import com.factech.nexus.modules.indicators.application.BrokerNetworkIndicatorsResponse;
import com.factech.nexus.modules.indicators.domain.service.GetBrokerNetworkIndicatorsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Los indicadores de cuentas de broker (`RF-IN-009`), mudados de `RF-SP-058` el 10-10-2026. */
@Tag(
    name = "Indicadores",
    description =
        "Cifras agregadas de la plataforma. Cada indicador tiene su permiso, y sus cifras se"
            + " acotan al alcance de quien pregunta.")
@RestController
@RequestMapping("/api/v1/indicators")
public class BrokerAccountIndicatorsController {

  private final GetBrokerNetworkIndicatorsService red;

  public BrokerAccountIndicatorsController(GetBrokerNetworkIndicatorsService red) {
    this.red = red;
  }

  @GetMapping("/broker-accounts/network")
  @PreAuthorize("hasAuthority('indicators:read-broker-accounts-network')")
  @Operation(
      summary = "Consultar los indicadores de cuentas de broker de la red",
      description =
          """
          **El árbol de la fuerza comercial con las cuentas de broker que originó
          cada vendedor** (`RF-IN-009`, `RN-IN-015`). Sustituye a
          `GET /api/v1/broker-accounts/indicators`, retirada el 10-10-2026.

          **Cada cuenta de consumidor suma en el dueño de su `afftrack`** —la
          cuenta `VENDEDOR` que la originó—, no en el vendedor principal de su
          titular; por eso **cuentan también las que no tienen titular**. La de
          un titular eliminado no cuenta.

          **Cada nodo trae dos bloques**: `own`, lo que originó su propia cuenta,
          y `network`, él y todo lo que cuelga de él. `network` **ya contiene**
          lo de sus hijos: sumar esa columna cuenta dos veces.

          **Cada cifra con su fecha**:

          - `accounts` — cuentas **creadas** en el periodo; `pending`, las de
            esas que siguen sin depósito; `conversion`, la parte de esas que ya
            depositó, entre 0 y 1, **nula sin cuentas**; `withoutHolder` y
            `consumers`, también de esas.
          - `ftd` — **primeros depósitos llegados** en el periodo, aunque la
            cuenta sea anterior. Sin fechas, todas las cuentas en
            `FIRST_DEPOSIT`.
          - `activeAccounts` — cuentas cuyo **último aviso de operación** cae en
            el periodo; `operations`, lo que esas llevan **acumulado**.
          - `byBroker` — las mismas cifras por broker, salvo `consumers`, con
            **todos** los brokers del catálogo.

          **Alcance** (`RN-IN-002`): administración ve **el árbol entero** y
          `unassigned` —lo que no tiene origen o cuyo origen no es fuerza
          comercial—; un vendedor, **su rama**, él como raíz, y `unassigned`
          nulo. `sellerId` enraíza el árbol en un vendedor del alcance; uno de
          fuera devuelve los nodos vacíos y las cifras en cero.

          **Periodo** (`RN-IN-010`): `from` y `to` son días del calendario del
          negocio, `to` incluido entero; sin ninguno, toda la historia.
          **`granularity`** —`DAY`, `WEEK` o `MONTH`— añade a los totales las
          cuentas creadas y los FTD por tramo, todos los tramos presentes.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "El árbol, los totales y lo no atribuido."),
    @ApiResponse(
        responseCode = "400",
        description =
            "`from` posterior a `to` (`VAL-002`), `granularity` desconocida (`VAL-005`) o un"
                + " parámetro malformado",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin `indicators:read-broker-accounts-network` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public BrokerNetworkIndicatorsResponse red(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @Parameter(description = "Un vendedor del alcance: el árbol se enraíza en él.")
          @RequestParam(required = false)
          UUID sellerId,
      @Parameter(description = "DAY, WEEK o MONTH: tramos en los totales.")
          @RequestParam(required = false)
          String granularity) {
    return red.get(from, to, sellerId, granularity);
  }
}
