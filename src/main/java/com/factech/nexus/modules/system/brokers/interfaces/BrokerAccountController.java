package com.factech.nexus.modules.system.brokers.interfaces;

import com.factech.nexus.modules.system.brokers.application.AssignBrokerAccountHolderRequest;
import com.factech.nexus.modules.system.brokers.application.BrokerAccountsPage;
import com.factech.nexus.modules.system.brokers.application.ListBrokerAccountsRequest;
import com.factech.nexus.modules.system.brokers.application.TeamBrokerAccountItem;
import com.factech.nexus.modules.system.brokers.domain.service.ListBrokerAccountsService;
import com.factech.nexus.modules.system.brokers.domain.service.ManageBrokerAccountsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
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
  private final ManageBrokerAccountsService gestion;

  public BrokerAccountController(
      ListBrokerAccountsService listado, ManageBrokerAccountsService gestion) {
    this.listado = listado;
    this.gestion = gestion;
  }

  @PatchMapping("/{brokerAccountId}/holder")
  @PreAuthorize("hasAuthority('broker-accounts:assign-user')")
  @Operation(
      summary = "Asignar titular a una cuenta de broker sin él",
      description =
          """
          **Permiso requerido:** `broker-accounts:assign-user` (`RF-SP-082`).

          Da titular a una cuenta que **llegó del broker antes que la persona**:
          el aviso de registro la creó como `CONSUMIDOR` sin titular, ligada a la
          cuenta del vendedor de su `afftrack` (`RN-SP-072`). Se encuentran en
          `GET /api/v1/broker-accounts?hasHolder=false`.

          **Solo una cuenta sin titular** —reasignar quitaría la cuenta a alguien—
          y **solo a un consumidor**. El origen no cambia. Queda auditado.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "La cuenta, ya con su titular"),
    @ApiResponse(responseCode = "400", description = "Sin `userId` (`VAL-012`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin `broker-accounts:assign-user` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "404",
        description = "La cuenta o la persona no existen, o la persona está eliminada (`VAL-002`)"),
    @ApiResponse(responseCode = "409", description = "La cuenta ya tiene titular (`EX-013`)"),
    @ApiResponse(responseCode = "422", description = "La persona no es consumidor (`EX-011`)"),
    @ApiResponse(responseCode = "500", description = "Fallo no controlado (`ERR-500`)")
  })
  public TeamBrokerAccountItem asignarTitular(
      @PathVariable UUID brokerAccountId, @RequestBody AssignBrokerAccountHolderRequest peticion) {
    return gestion.assignHolder(brokerAccountId, peticion);
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
          - `kind` — `VENDEDOR` o `CONSUMIDOR` (`RN-SP-068`): la cuenta de un
            vendedor o la de un consumidor. Cualquier otro valor es `400`.
          - `hasHolder` — `false`, las cuentas **sin titular**, que llegaron del
            broker antes que la persona (`RN-SP-072`); `true`, las demás. Sin
            titular, `user` va nulo.
          - `brokerId` — un broker del catálogo.
          - `search` — fragmento de **número de cuenta**, de **nombre de usuario
            en el broker**, o del usuario, correo o nombre completo del titular.
            Sin acentos y sin distinguir mayúsculas. Con `?kind=CONSUMIDOR`, solo
            las cuentas de consumidor.

          **Cada fila trae su titular (`user`) y su origen (`referrer`)**: la
          cuenta `VENDEDOR` que la originó, su `afftrack` y el vendedor —
          identificador, usuario, nombre y apellido—. El vendedor del `afftrack`
          tiene que ser el principal del titular (`RN-SP-072`).
          - `from` / `to` — **cuándo se declaró la cuenta**. Son **instantes con
            zona**, no fechas sueltas, y el rango es **semiabierto**: incluye
            `from`, excluye `to`. `from` posterior a `to` es `400`.

          **Un identificador inexistente devuelve la página vacía sin error** —
          `supervisorId`, `userId` y `brokerId` no se validan contra su tabla—,
          al revés que `status` y que el rango: aquellos son preguntas legítimas
          con respuesta vacía y estos son preguntas mal escritas.

          `totalElements` **cuenta lo filtrado**.

          **La respuesta lleva además un `summary` con TRES totales y sus
          desgloses por broker**, los tres con la misma forma —un `total` y un
          `byBroker`—:

          - `summary.accounts` — cuántos registros cumplen el filtro. Su
            `total` es **el mismo número que `totalElements`**, y sale de la
            misma consulta: no pueden discrepar.
          - `summary.register` — cuántos de esos están en `REGISTER`.
          - `summary.firstDeposit` — cuántos de esos están en `FIRST_DEPOSIT`.

          **`accounts` es siempre `register` + `firstDeposit`**, total a total y
          broker a broker. La redundancia es deliberada: van los dos estados
          para no obligar a restar, y va el total porque es la pregunta que más
          se hace.

          **El resumen respeta TODOS los filtros, incluido `status`**, y de ahí
          sale lo único que hay que saber antes de pintarlo: **con
          `?status=REGISTER`, `summary.firstDeposit.total` vale SIEMPRE cero**,
          y con `?status=FIRST_DEPOSIT` vale siempre el total. **Ese cero no
          significa «nadie ha depositado»: significa «no pediste ninguno».** Un
          tablero que lo enseñe sin decirlo miente; para ver el embudo completo,
          consulte **sin** el filtro `status`.

          **`byBroker` trae TODOS los brokers del catálogo**, ordenado por
          nombre y **con cero donde el filtro no deja ninguna**: su longitud
          **no cambia** al filtrar, de modo que las columnas de una tabla se
          arman una vez y no bailan. Un broker **desactivado sigue apareciendo**
          — apagarlo no borra lo que ya se declaró en él. Los tres desgloses
          **suman exactamente** su `total`.

          **Con la página vacía el resumen va en ceros**, no ausente: el
          `byBroker` sigue trayendo el catálogo entero, todo a cero.

          **La fila es idéntica campo por campo a la de
          `GET /api/v1/users/me/team/broker-accounts`**, a propósito: las dos
          pantallas se pintan con el mismo componente. **Lo que aquella no
          lleva es el `summary`**: el resumen se pidió para el listado de
          administración.

          **Hoy todas las cuentas están en `REGISTER`**: quien mueve una a
          `FIRST_DEPOSIT` es el webhook del broker, que todavía no existe.

          **Esta ruta no sustituye a `GET /api/v1/users/{id}/broker-accounts`**,
          que existe para **el superior comercial sin permiso**.
          """)
  @ApiResponses({
    // SIN `@Schema(implementation = …)`: ese anotado publicaría la envoltura
    // CRUDA —`content` sin tipo— y el cliente generado no sabría qué hay en
    // cada fila. Dejando el tipo de retorno, springdoc emite
    // `BrokerAccountsPage` con la fila y el resumen dentro.
    @ApiResponse(
        responseCode = "200",
        description =
            "Página de cuentas, ordenada por titular, broker e identificador de cuenta, **con su"
                + " resumen**. Vacía —y el resumen en ceros— si ningún registro cumple el filtro."),
    @ApiResponse(
        responseCode = "400",
        description =
            "`status` fuera de `REGISTER`/`FIRST_DEPOSIT`, `kind` fuera de `VENDEDOR`/`CONSUMIDOR`,"
                + " `from` posterior a `to`, identificador"
                + " malformado (`VAL-001`) o paginación fuera de límites (`VAL-003`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin `broker-accounts:read` (`AUTH-002`)"),
    @ApiResponse(responseCode = "500", description = "Fallo no controlado (`ERR-500`)")
  })
  public BrokerAccountsPage listar(
      // `@ParameterObject` EXPLOTA el registro en sus siete parámetros de
      // consulta. Sin él, el contrato publica UNO solo llamado `filtros` y el
      // frontend no ve ninguno de los filtros que esta prosa describe.
      @org.springdoc.core.annotations.ParameterObject @ModelAttribute
          ListBrokerAccountsRequest filtros) {
    return listado.list(filtros);
  }
}
