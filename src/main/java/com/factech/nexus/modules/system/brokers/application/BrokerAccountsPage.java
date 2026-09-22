package com.factech.nexus.modules.system.brokers.application;

import com.factech.nexus.modules.system.brokers.application.BrokerAccountItem.BrokerRef;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * La página del listado de administración <b>con su resumen</b> (`RF-SP-057`, 10-09-2026).
 *
 * <p><b>Los campos de la página se llaman igual que en {@code PageResponse} y están en el mismo
 * sitio</b>: para un cliente que lea el JSON, esto es un campo <b>añadido</b> y nada más. Lo que sí
 * cambia es el nombre del esquema publicado, de modo que un cliente generado del contrato hay que
 * regenerarlo — se acepta porque el endpoint se publicó el mismo día.
 *
 * <p><b>{@code GET /api/v1/users/me/team/broker-accounts} no usa este tipo</b>: sigue devolviendo
 * la página sin resumen. El resumen se pidió para el listado de administración.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record BrokerAccountsPage(
    List<TeamBrokerAccountItem> content,
    long totalElements,
    int totalPages,
    int page,
    int size,
    boolean totalIsExact,
    Summary summary) {

  /**
   * Los tres totales de lo filtrado, cada uno con su desglose por broker.
   *
   * <p><b>Van los DOS estados y no solo el depósito</b> (10-09-2026): el embudo tiene dos lados, y
   * obligar al cliente a restar —{@code accounts − firstDeposit}— es pedirle que <b>rehaga una
   * cuenta que el servidor ya tiene hecha</b>. Con dos valores posibles la resta es trivial
   * <b>hoy</b>; el día que `RN-SP-045` admita un tercer estado dejaría de serlo, y quien la hubiera
   * escrito no se enteraría.
   *
   * <p><b>{@link #accounts()} es siempre {@link #register()} + {@link #firstDeposit()}</b>, total a
   * total y broker a broker. La redundancia es deliberada: el total es la pregunta que más se hace
   * y no debería costar una suma.
   *
   * <p><b>Respeta TODOS los filtros, incluido {@code status}</b>, por decisión expresa del
   * responsable del proyecto (10-09-2026), tomada <b>contra la recomendación técnica</b>: se
   * ofreció que el conteo de {@code FIRST_DEPOSIT} ignorase el filtro de estado —para que los dos
   * números informaran a la vez— y se eligió la <b>coherencia</b>, que el resumen describa
   * exactamente lo devuelto.
   *
   * <p><b>La consecuencia, escrita aquí porque es fácil leerla mal</b>: con {@code
   * ?status=REGISTER}, {@link #firstDeposit()} vale <b>siempre cero</b>. Ese cero <b>no significa
   * «nadie ha depositado»: significa «no pediste ninguno»</b>.
   */
  @Schema(name = "BrokerAccountsSummary")
  public record Summary(Totals accounts, Totals register, Totals firstDeposit) {}

  /**
   * Un total y su desglose.
   *
   * <p><b>{@code accounts.total} es el mismo entero que {@code totalElements}</b>, y sale de la
   * misma consulta: calcularlos por separado sería tener dos fuentes de verdad sobre el mismo
   * número, y el día que una divergiera nadie sabría cuál creer.
   *
   * <p><b>{@code byBroker} trae TODOS los brokers del catálogo</b>, ordenados por nombre y <b>con
   * cero donde el filtro no deja ninguna</b> (10-09-2026, corrigiendo la decisión del mismo día).
   * Nació al revés —solo los que tenían alguna— y se invirtió por lo que cuesta en la pantalla: un
   * arreglo cuya longitud depende del filtro <b>obliga a rearmar las columnas en cada consulta</b>,
   * y una columna que desaparece se lee como un dato que falta, no como un cero.
   *
   * <p><b>Consecuencia declarada</b>: un broker desactivado <b>sigue apareciendo</b>. Es lo
   * correcto —apagarlo no borra lo declarado en él— al precio de una columna muerta el día que se
   * retire alguno.
   */
  @Schema(name = "BrokerAccountsTotals")
  public record Totals(long total, List<ByBroker> byBroker) {}

  /** Cuántas cuentas aporta un broker al total de al lado. */
  @Schema(name = "BrokerAccountsByBroker")
  public record ByBroker(BrokerRef broker, long total) {}
}
