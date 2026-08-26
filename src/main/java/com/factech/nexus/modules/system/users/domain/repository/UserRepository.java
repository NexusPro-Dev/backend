package com.factech.nexus.modules.system.users.domain.repository;

import com.factech.nexus.modules.system.users.domain.models.Email;
import com.factech.nexus.modules.system.users.domain.models.User;
import com.factech.nexus.modules.system.users.domain.models.Username;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia de las personas y de su estructura (`RF-SP-024`, `RF-SP-030`,
 * `RF-SP-031`).
 */
public interface UserRepository {

  User save(User usuario);

  /**
   * Vuelca los cambios pendientes del agregado, <b>traduciendo la violación de unicidad</b>.
   *
   * <p><b>Hace falta porque {@link #save} no cubre la edición.</b> Al modificar una persona ya
   * cargada no se llama a {@code save}: el agregado está gestionado y el {@code UPDATE} sale solo,
   * <b>en el commit</b> — es decir, fuera de cualquier {@code try} del adaptador. Una violación de
   * {@code uq_users_email} escapaba entonces sin traducir y llegaba al cliente como {@code 500} en
   * lugar de como el {@code 409} de `RN-SP-016`.
   *
   * <p>La comprobación previa de `RF-SP-027` no lo evita: <b>existe para el mensaje</b>, y entre
   * leerla y escribir hay una ventana que dos ediciones simultáneas hacia el mismo correo
   * atraviesan las dos. La garantía la da el índice único; esto es lo que la convierte en una
   * respuesta que quien consume la API pueda entender.
   *
   * <p>Se llama de forma explícita y no se confía al commit por eso mismo: dentro del caso de uso
   * hay quien traduzca, y después ya no.
   */
  void flushChanges();

  boolean existsUsername(Username username);

  boolean existsEmail(Email email);

  /**
   * La persona, si existe, no está eliminada y está <b>ACTIVA</b>.
   *
   * <p>Se usa donde el estado importa: nadie inactivo puede quedar a cargo de otra persona.
   */
  Optional<User> findUsableById(UUID id);

  /**
   * La persona, si existe y no está eliminada — <b>sin exigir que esté activa</b>.
   *
   * <p>Es lo que `RF-SP-030` §4 y `RF-SP-031` §4 declaran en su paso 2, y la diferencia es
   * deliberada: administrar los roles de una cuenta desactivada es legítimo y frecuente —se prepara
   * su vuelta, o se le retira lo que ya no le corresponde—. Exigir `ACTIVO` aquí convertiría una
   * cuenta suspendida en una cuenta imposible de administrar.
   */
  Optional<User> findNotDeletedById(UUID id);

  // ---------------------------------------------------------------------------
  // Roles
  // ---------------------------------------------------------------------------

  /**
   * Agrega asignaciones, <b>declarando el conflicto como esperado</b>.
   *
   * <p>Baja a sentencia nativa con {@code ON CONFLICT DO NOTHING} porque el {@code persist} de JPA
   * no sabe expresarlo, y sin él dos peticiones simultáneas con el mismo rol terminan una en {@code
   * 200} y otra en {@code 500} (`RF-SP-030` §2).
   */
  int addRoles(UUID userId, Collection<UUID> roleIds);

  /** Retira asignaciones. Las que no existen afectan cero filas y no son un error (`FA-001`). */
  int removeRoles(UUID userId, Collection<UUID> roleIds);

  // ---------------------------------------------------------------------------
  // Membresía
  // ---------------------------------------------------------------------------

  void assignMembership(
      UUID userId, UUID membershipId, OffsetDateTime endsAt, OffsetDateTime ahora);

  Optional<UserMembership> findMembership(UUID userId);

  /**
   * Retira la membresía. Es un {@code DELETE} y no un cierre por fecha: `RN-SP-015` dice que quien
   * deja de ser consumidor <b>no tiene</b> membresía, no que tuviera una que terminó.
   */
  void removeMembership(UUID userId);

  // ---------------------------------------------------------------------------
  // Superior comercial
  // ---------------------------------------------------------------------------

  void assignSupervisor(UUID id, UUID userId, UUID supervisorId, OffsetDateTime ahora);

  Optional<UserSupervisor> findActiveSupervisor(UUID userId);

  /**
   * Cierra la asignación vigente poblando {@code ended_at}.
   *
   * <p><b>Nunca un {@code DELETE}.</b> La fila cerrada dice a quién se atribuía cada resultado
   * comercial en cada periodo, y borrarla reescribiría la historia de las comisiones (`V21`).
   */
  void endSupervisor(UUID userId, OffsetDateTime ahora);

  /**
   * Cuántas personas tienen a esta como superior <b>vigente</b> (`RN-SP-022`, `RF-SP-042`).
   *
   * <p><b>«Vigente» califica a la ASIGNACIÓN, no al estado de la persona.</b> Cuenta a quien está
   * inactivo y no cuenta a quien está eliminado. Es lo que `RF-SP-042` · `CA-SP-447` exige: el
   * número que devuelve el equipo y el que informa el rechazo de `RN-SP-022` tienen que ser el
   * mismo, y salen de aquí. Excluir a los inactivos haría que las dos operaciones dijeran cosas
   * distintas sobre el mismo equipo — y una cuenta suspendida sigue teniendo un superior al que hay
   * que reasignarla antes de que ese superior pueda dejar la fuerza comercial.
   *
   * <p>A quien pregunta por `RN-SP-022` se le devuelve <b>el número y nunca la lista</b>: quién
   * forma ese equipo se consulta con `RF-SP-042`, que tiene su propio permiso.
   */
  int countSupervisees(UUID supervisorId);

  /**
   * El equipo directo, paginado y con un orden estable.
   *
   * <p>Estable no es cosmético: sin un desempate determinista, dos páginas consecutivas pueden
   * repetir a una persona y omitir a otra sin que nada falle.
   */
  List<TeamMember> findTeam(UUID supervisorId, int offset, int limit);

  /**
   * La persona, bloqueada para escritura.
   *
   * <p>Es lo que serializa dos reasignaciones simultáneas del mismo subordinado. Sin él, la
   * unicidad parcial {@code uq_user_supervisors_vigente} hace fallar a la segunda con {@code 23505}
   * — un {@code 500} en lugar de una espera (`RF-SP-041` · `T-05`).
   *
   * <p><b>Toda operación que cambie los roles o la membresía de una persona DEBE tomarlo</b>, y no
   * solo las que reescriben su fila. `RN-SP-018` —consumidor ⟺ membresía— es un invariante que
   * abarca {@code user_roles} y {@code user_memberships}, y las cuatro operaciones que lo pueden
   * romper leían sin bloqueo: en {@code READ COMMITTED} cada una validaba contra el estado que la
   * otra estaba a punto de cambiar, las dos concluían que podían proceder, y la persona acababa
   * <b>portando un rol de consumidor sin nivel</b>. No falla ninguna de las dos: el invariante se
   * rompe en silencio. Corregido el 26-08-2026, tras tres apariciones intermitentes en CI.
   *
   * <p>Las cuatro bloquean <b>la misma fila</b> —la de la persona—, de modo que se serializan sin
   * riesgo de abrazo mortal. Tomarlo <b>antes</b> de leer roles y membresía es la otra mitad: en
   * {@code READ COMMITTED} cada sentencia posterior toma instantánea nueva y ve lo que la
   * transacción anterior confirmó.
   */
  Optional<User> findNotDeletedByIdForUpdate(UUID id);

  // ---------------------------------------------------------------------------
  // Estado y eliminación
  // ---------------------------------------------------------------------------

  /**
   * El momento hasta el que la cuenta está bloqueada, o vacío si no lo está.
   *
   * <p>Se lee por separado y no desde el agregado porque { User} <b>no mapea</b> las tres columnas
   * de control de acceso: las creó `RF-SP-034` para el inicio de sesión, y añadirlas al agregado
   * obligaría a que toda operación sobre una persona las cargara y las arrastrara.
   *
   * <p>Es lo que hace observable la diferencia entre el bloqueo <b>manual</b> —sin expiración— y el
   * <b>automático</b>, sin ampliar el esquema con un campo que lo diga.
   */
  Optional<OffsetDateTime> lockedUntilOf(UUID userId);

  /**
   * Aplica el estado, y limpia el bloqueo y el contador cuando corresponde.
   *
   * <p>{ limpiarAcceso} en verdadero pone { locked_until} a nulo y { failed_attempts} a cero: es lo
   * que ocurre al <b>devolver</b> el acceso y también al bloquear <b>a mano</b> —ahí el nulo es la
   * marca de que ese bloqueo no expira solo—.
   */
  void applyStatus(UUID userId, String estado, boolean limpiarAcceso, OffsetDateTime ahora);

  /**
   * Marca la eliminación lógica.
   *
   * <p><b>{ status} NO se toca</b>, y podría parecer natural dejarlo en inactivo. Sería un error:
   * el estado guardado en el registro de eliminación dejaría de decir en qué situación estaba la
   * persona cuando se la eliminó, que es parte de lo que el Art. V.13 exige conservar. Quien no
   * puede entrar es cualquiera con { deleted_at} informado, y eso lo comprueba el inicio de sesión
   * sin mirar el estado.
   */
  void markDeleted(UUID userId, OffsetDateTime ahora);

  /** Retira <b>todas</b> sus asignaciones de rol. */
  int removeAllRoles(UUID userId);
}
