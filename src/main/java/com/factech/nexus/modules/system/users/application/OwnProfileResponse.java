package com.factech.nexus.modules.system.users.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * El perfil de quien pregunta (`RF-SP-039`).
 *
 * <p><b>DTO propio y no el detalle de `RF-SP-026`</b>, aunque se parezcan: aquel es la ficha
 * administrativa de un tercero y este es lo que alguien necesita saber <b>de sí mismo</b>.
 * Compartir el registro habría atado dos contratos que evolucionan por motivos distintos, y habría
 * arrastrado aquí campos que en esta pantalla no significan nada — las fechas de creación y
 * modificación, o la expiración del bloqueo, que quien está autenticado no tiene.
 *
 * <p><b>{@code permissions} es lo que hace útil este endpoint</b> y la razón de que exista: cierra
 * el hallazgo `DF-04` del frontend. Sin él, la interfaz tenía que deducir del listado de roles qué
 * puede hacer la persona, duplicando en el navegador una regla que vive en el servidor — y la copia
 * del navegador se quedaba atrás.
 *
 * <p><b>No se pagina.</b> Es el perfil de una sola persona; partirlo obligaría a pedirlo en trozos
 * para poder pintar un menú.
 *
 * <p><b>Lleva el identificador desde el 04-09-2026</b>, y la historia de por qué no lo llevaba vale
 * la pena. El motivo escrito era «quien pregunta ya sabe quién es»: suena razonable y <b>es
 * falso</b>. Quien pregunta sabe su nombre de usuario; el {@code uuid} viaja <b>dentro del
 * token</b>, y leerlo desde una interfaz obliga a descomponer un JWT — que es justo lo que un
 * cliente no debe hacer con una credencial.
 *
 * <p>Lo destapó el frontend al construir la compra propia (`R-28`): {@code POST /api/v1/movements}
 * exige {@code clientId}, de modo que <b>quien compraba para sí mismo no podía decir quién era</b>.
 * El rodeo disponible —buscarse en el listado de usuarios— exige {@code users:read}, que un cliente
 * no tiene.
 *
 * <p><b>No abre alcance</b>: es el identificador <b>del propio actor</b>, se resuelve del token
 * como todo lo demás de esta respuesta, y la operación sigue sin admitir parámetros — no hay forma
 * de señalar a otra persona (`CA-SP-434`, intacto).
 *
 * <p>No lleva equipo a cargo — eso es la consulta de estructura comercial, con su propio permiso y
 * su propia paginación.
 *
 * <p>{@code membership} y {@code supervisor} van <b>ausentes</b>, no en nulo, cuando no aplican: la
 * interfaz distingue «no tiene» de «no se pudo resolver».
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OwnProfileResponse(
    UUID id,
    String username,
    String email,
    String firstName,
    String lastName,
    String status,
    List<RoleRef> roles,
    List<String> permissions,
    CountryRef country,
    MembershipRef membership,
    OffsetDateTime lastLoginAt,
    SupervisorRef supervisor,
    boolean mustChangePassword) {

  /**
   * El país del actor (`RN-SP-034`, 07-09-2026), y <b>nunca ausente</b>.
   *
   * <p>La inclusión {@code NON_NULL} de este registro hace que un campo nulo <b>desaparezca en
   * silencio</b> en lugar de fallar, de modo que conviene decir por qué aquí eso no puede ocurrir:
   * la columna es {@code NOT NULL}. Esa es la única razón por la que la interfaz puede leer {@code
   * country} sin comprobar si existe.
   *
   * <p><b>Entra por la misma clase de necesidad que el identificador</b>, y la simetría vale la
   * pena verla: aquel entró porque la compra propia exige {@code clientId} y el cliente no podía
   * decir quién era; este entra porque `RN-MV-019` decide <b>qué medios de pago se le ofrecen</b>
   * según dónde esté. Los dos son datos del propio actor y ninguno abre alcance.
   *
   * <p><b>Se publica y no se puede cambiar desde aquí.</b> Ni esta consulta, que es de solo
   * lectura, ni `RF-SP-044`, que edita el perfil propio y no admite el campo: el país lo corrige un
   * administrador por `RF-SP-027`, porque si decide los medios de pago, cambiárselo uno mismo sería
   * cambiarse de mercado.
   */
  public record CountryRef(UUID id, String code, String name) {}

  /** Con su estado: es lo que explica que un rol asignado no aparezca en {@code permissions}. */
  public record RoleRef(String code, String name, String status) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record MembershipRef(String code, short level, OffsetDateTime endsAt) {}

  /**
   * <b>Solo el superior, nunca el equipo.</b>
   *
   * <p>A quién reporta uno es un dato del propio actor; quiénes dependen de uno es un conjunto de
   * terceros. Es la distinción que sostiene la reserva de <b>D-22</b>, y devolver el equipo aquí la
   * adelantaría sin que nadie la haya decidido.
   */
  public record SupervisorRef(
      String username, String firstName, String lastName, String roleCode) {}
}
