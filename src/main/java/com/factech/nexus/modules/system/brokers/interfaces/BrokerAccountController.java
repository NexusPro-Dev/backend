package com.factech.nexus.modules.system.brokers.interfaces;

import com.factech.nexus.modules.system.brokers.application.ListBrokerAccountsRequest;
import com.factech.nexus.modules.system.brokers.application.NetworkIndicatorsResponse;
import com.factech.nexus.modules.system.brokers.application.TeamBrokerAccountItem;
import com.factech.nexus.modules.system.brokers.domain.service.GetNetworkIndicatorsService;
import com.factech.nexus.modules.system.brokers.domain.service.ListBrokerAccountsService;
import com.factech.nexus.shared.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las cuentas de broker, vistas por quien administra (`RF-SP-057`).
 *
 * <p><b>Controlador propio y no {@code UserController}</b>, al revés que `RF-SP-055` y `RF-SP-056`
 * — y por el mismo criterio: <b>manda la ruta</b>. Aquellas cuelgan de una persona y esta es de
 * primer nivel, porque pregunta por el conjunto y no por el de alguien.
 */
@RestController
@RequestMapping("/api/v1/broker-accounts")
@Tag(
    name = "Cuentas de broker",
    description =
        "Todas las cuentas de broker del sistema, para quien administra. Las del propio equipo se"
            + " consultan sin permiso en GET /api/v1/users/me/team/broker-accounts.")
public class BrokerAccountController {

  private final ListBrokerAccountsService listado;
  private final GetNetworkIndicatorsService indicadores;

  public BrokerAccountController(
      ListBrokerAccountsService listado, GetNetworkIndicatorsService indicadores) {
    this.listado = listado;
    this.indicadores = indicadores;
  }

  @GetMapping("/indicators")
  @PreAuthorize("hasAuthority('broker-accounts:read')")
  @Operation(
      summary = "Consultar los indicadores de la red comercial",
      description =
          """
          Devuelve **el árbol de la fuerza comercial**, cada nodo con dos
          bloques de números: `own` —lo que cuelga **directamente** de esa
          persona— y `network` —esa persona **y todo lo que cuelga de ella**, a
          cualquier profundidad—.

          **La regla es una sola en todos los niveles**: el `network` de alguien
          es su `own` más la suma de los `network` de sus hijos. Un agente
          recibe lo de sus clientes directos; un director, lo suyo más lo de sus
          agentes; un manager, lo suyo más lo de sus directores.

          **NO SUME LA COLUMNA `network` DE UNA LISTA.** El `network` de un
          director **ya contiene** el de sus agentes, de modo que sumar totales
          de distintos niveles cuenta dos veces. Para agregar, sume `own`; para
          leer el total de una rama, use su `network`.

          **Qué cuenta como uno:** `accounts` cuenta **cuentas** y `consumers`
          cuenta **personas**, y no tienen por qué coincidir — un cliente con
          dos cuentas es una persona y dos cuentas. **Solo cuentan las cuentas
          de consumidores**: la cuenta personal de un vendedor no entra en
          ningún indicador.

          **Los consumidores no son nodos.** Aportan el número y no aparecen: el
          árbol es de la fuerza comercial.

          **`conversion` es `ftd / accounts`, un cociente entre 0 y 1 —no un
          porcentaje— y es NULA cuando no hay cuentas**, no cero: cero se lee
          como «nadie convirtió» y la verdad sería «no hay nada que convertir».
          Se recalcula en cada nodo sobre sus propios totales; **no es el
          promedio de las de sus hijos**.

          **`unassigned` es lo que hace que los números cuadren**: las cuentas
          de consumidores que no cuelgan de ningún vendedor no entran en ningún
          nodo. Se cumple que `totals` + `unassigned` es igual al
          `totalElements` de `GET /api/v1/broker-accounts` sin filtros. Se omite
          cuando se pide una rama con `rootId`, donde no significa nada.

          **`rootId` acota el árbol a esa rama e INCLUYE a la persona como
          raíz**, al revés que el `supervisorId` de `GET /api/v1/broker-accounts`:
          allí se piden las cuentas de su red —y las suyas no son de su red— y
          aquí se pide el nodo que lleva sus números.

          **Hoy `ftd` será cero en todas partes** y el embudo se verá entero en
          `pending`: quien mueve una cuenta a `FIRST_DEPOSIT` es el webhook del
          broker, que todavía no existe. Es el estado real, no un fallo del
          indicador.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description =
            "El árbol, con los totales y lo no atribuido. `nodes` vacío si no hay fuerza"
                + " comercial."),
    @ApiResponse(responseCode = "400", description = "`rootId` malformado (`VAL-001`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin `broker-accounts:read` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "404",
        description =
            "`rootId` no designa a nadie de la fuerza comercial, o está eliminado (`VAL-002`)"),
    @ApiResponse(responseCode = "500", description = "Fallo no controlado (`ERR-500`)")
  })
  public NetworkIndicatorsResponse indicadoresDeLaRed(
      @Parameter(description = "Acota el árbol a la rama de esa persona, ella incluida.")
          @RequestParam(required = false)
          UUID rootId) {
    return indicadores.of(rootId);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('broker-accounts:read')")
  @Operation(
      summary = "Consultar y filtrar todas las cuentas de broker",
      description =
          """
          Devuelve **todas** las cuentas de broker del sistema, paginadas y con
          su titular en cada fila. **Sin ningún filtro devuelve el sistema
          entero**: es lo que `broker-accounts:read` significa.

          **`supervisorId` devuelve LA RED ENTERA de esa persona, en
          profundidad** (`RN-SP-047`): sus subordinados, los subordinados de
          estos, y así hasta abajo. **No es un solo nivel**, al revés que
          `GET /api/v1/users/{id}/team` y que
          `GET /api/v1/users/me/team/broker-accounts` — aquellos se autorizan
          por la estructura y por eso se acotan; este se autoriza por permiso,
          que ya alcanza a todos, de modo que la profundidad **ahorra recorrer
          el árbol y no concede nada**.

          **La raíz NO se incluye en su red.** Preguntar por la red de alguien
          devuelve la de los suyos; sus propias cuentas se piden con `userId`, y
          **los dos filtros se combinan** — juntos responden «de la red de este
          vendedor, las cuentas de esta persona».

          **Solo la estructura vigente.** Quien dejó la red ayer no aparece, ni
          los que colgaban de él por esa vía. Y una persona **eliminada** no
          aparece **sin que su rama se corte**: quienes dependían de ella siguen
          saliendo.

          **Filtros, todos opcionales y combinables con Y:**

          - `supervisorId` — la red, en profundidad.
          - `userId` — una persona concreta.
          - `status` — `REGISTER` o `FIRST_DEPOSIT`. **Cualquier otro valor es
            `400`**, no una página vacía.
          - `brokerId` — un broker del catálogo.
          - `search` — fragmento de **número de cuenta**, nombre de usuario,
            correo o nombre completo. Sin acentos y sin distinguir mayúsculas.
          - `from` / `to` — **cuándo se declaró la cuenta**. Son **instantes con
            zona**, no fechas sueltas, y el rango es **semiabierto**: incluye
            `from`, excluye `to`. `from` posterior a `to` es `400`.

          **Un identificador inexistente devuelve la página vacía sin error** —
          `supervisorId`, `userId` y `brokerId` no se validan contra su tabla—,
          al revés que `status` y que el rango: aquellos son preguntas legítimas
          con respuesta vacía y estos son preguntas mal escritas.

          `totalElements` **cuenta lo filtrado**.

          **La fila es idéntica campo por campo a la de
          `GET /api/v1/users/me/team/broker-accounts`**, a propósito: las dos
          pantallas se pintan con el mismo componente.

          **Hoy todas las cuentas están en `REGISTER`**: quien mueve una a
          `FIRST_DEPOSIT` es el webhook del broker, que todavía no existe.

          **Esta ruta no sustituye a `GET /api/v1/users/{id}/broker-accounts`**,
          que existe para **el superior comercial sin permiso**.
          """)
  @ApiResponses({
    // SIN `@Schema(implementation = …)`: ese anotado publicaría la envoltura
    // CRUDA —`content` sin tipo— y el cliente generado no sabría qué hay en
    // cada fila. Dejando el tipo de retorno, springdoc emite
    // `PageResponseTeamBrokerAccountItem`.
    @ApiResponse(
        responseCode = "200",
        description =
            "Página de cuentas, ordenada por titular, broker e identificador de cuenta. Vacía si"
                + " ningún registro cumple el filtro."),
    @ApiResponse(
        responseCode = "400",
        description =
            "`status` fuera de `REGISTER`/`FIRST_DEPOSIT`, `from` posterior a `to`, identificador"
                + " malformado (`VAL-001`) o paginación fuera de límites (`VAL-003`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin `broker-accounts:read` (`AUTH-002`)"),
    @ApiResponse(responseCode = "500", description = "Fallo no controlado (`ERR-500`)")
  })
  public PageResponse<TeamBrokerAccountItem> listar(
      // `@ParameterObject` EXPLOTA el registro en sus siete parámetros de
      // consulta. Sin él, el contrato publica UNO solo llamado `filtros` y el
      // frontend no ve ninguno de los filtros que esta prosa describe.
      @org.springdoc.core.annotations.ParameterObject @ModelAttribute
          ListBrokerAccountsRequest filtros) {
    return listado.list(filtros);
  }
}
