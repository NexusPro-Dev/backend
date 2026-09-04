package com.factech.nexus.modules.system.users.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Lo que `SP` publica de un <b>cliente</b> para que se le pueda vender (**D-25**, `RF-MV-001` ·
 * `T-07`).
 *
 * <p>Es la cuarta interfaz de este submódulo, y la primera que nace para un módulo que
 * <b>escribe</b> a partir de lo que lee. Las tres anteriores alimentaban consultas; de esta salen
 * dos datos que se <b>congelan</b> en una venta y que nadie vuelve a mirar: a quién se le vendió y
 * a quién se le atribuye.
 *
 * <h2>Publica DOS lecturas y no tres: el nivel se pide a {@link CurrentMembershipLookup}</h2>
 *
 * <p><b>`RF-MV-001` · `plan.md` §3.2 asignaba a esta interfaz también el nivel de membresía vigente
 * del cliente.</b> No lo lleva, y es una enmienda deliberada (Art. I.7): ese puerto <b>ya
 * existe</b> desde `RF-PM-007` · `T-01`, con su borde fijado por prueba —una fecha exactamente
 * igual al instante consultado ya no está vigente— y con la definición de «vigente» en un solo
 * sitio. Declararlo otra vez aquí habría creado la segunda, que es exactamente el defecto que aquel
 * puerto existe para evitar: no falla, devuelve resultados plausibles durante meses y solo se
 * separa en el borde.
 *
 * <h2>El vendedor NO se pide: se deduce, y por eso vive aquí</h2>
 *
 * <p>`RN-MV-003` dice que el vendedor sale del cliente y se congela. Que `MV` no lo reciba por
 * parámetro es lo que impide atribuirse la venta de otro; que salga de <b>esta</b> interfaz y no de
 * una consulta propia es lo que impide que `MV` conozca {@code user_supervisors}, que es de `SP`.
 *
 * <p><b>Devuelve el superior comercial vigente, sea quien sea.</b> Que ese superior porte un rol
 * `VENDEDOR` lo exige `RN-SP-020` <b>al colgarlo</b>, y comprobarlo otra vez al vender convertiría
 * una estructura mal formada en una venta rechazada en lugar de en una estructura que hay que
 * arreglar. `MV` solo necesita saber si hay alguien a quien atribuir (`EX-003`).
 *
 * <p>Ver `architecture.md` §15.2.
 */
public interface ClientCatalog {

  /**
   * La persona a nombre de quien se vende, si existe y <b>no está eliminada</b>.
   *
   * <p><b>No exige que esté {@code ACTIVO}</b>, y la diferencia decide `EX-002`. Desde `RF-SP-045`
   * una cuenta puede autenticar sin estar activa —{@code FTD_PENDIENTE}—, de modo que filtrar por
   * estado aquí colapsaría «no existe» con «existe y no puede operar»: los dos casos volverían
   * vacíos y `RF-MV-001` no podría distinguir `EX-001` de `EX-002`, que es justo lo que `CA-MV-009`
   * exige. El estado viaja en la respuesta y lo interpreta quien pregunta.
   *
   * @param id identificador de la persona; un valor nulo devuelve vacío en lugar de fallar
   */
  Optional<ClientView> findClient(UUID id);

  /**
   * De qué vendedor cuelga hoy esa persona, si de alguno.
   *
   * <p>Vacío significa que <b>la venta no se puede atribuir</b> (`EX-003`). `RN-SP-027` promete que
   * esto no ocurre —ningún cliente se registra sin vendedor—, y esta lectura existe porque una
   * promesa de otro módulo no es una comprobación de este: el día que falle, la venta tiene que
   * negarse a existir en lugar de nacer sin dueño.
   *
   * @param id identificador del cliente; un valor nulo devuelve vacío en lugar de fallar
   */
  Optional<SellerView> sellerOf(UUID id);

  /**
   * Lo que cruza la frontera: datos planos, sin comportamiento y sin entidad.
   *
   * <p><b>{@code status} viaja como cadena y no como enumerado</b>, por lo mismo que {@code
   * UserSupervisor.status}: el catálogo de estados es de `SP` y cambia con sus requerimientos
   * —`RF-SP-045` sustituye {@code PENDIENTE} por {@code FTD_PENDIENTE}—, y exportar el enumerado
   * obligaría a recompilar a cada consumidor por un valor que no le concierne.
   */
  record ClientView(UUID id, String username, String firstName, String lastName, String status) {}

  /**
   * El vendedor al que se atribuye la venta.
   *
   * <p><b>Lleva el nombre y no solo el identificador</b> porque `RF-MV-001` · §6.2 lo devuelve
   * resuelto en la respuesta: quien registra la venta no lo eligió, y ese es el único momento en
   * que puede ver a quién acaba de atribuirse lo que vendió.
   */
  record SellerView(UUID id, String username, String firstName, String lastName) {}
}
