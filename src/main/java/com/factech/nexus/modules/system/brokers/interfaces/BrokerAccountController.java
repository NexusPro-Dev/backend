package com.factech.nexus.modules.system.brokers.interfaces;

import com.factech.nexus.modules.system.brokers.application.ListBrokerAccountsRequest;
import com.factech.nexus.modules.system.brokers.application.TeamBrokerAccountItem;
import com.factech.nexus.modules.system.brokers.domain.service.ListBrokerAccountsService;
import com.factech.nexus.shared.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
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

  public BrokerAccountController(ListBrokerAccountsService listado) {
    this.listado = listado;
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
