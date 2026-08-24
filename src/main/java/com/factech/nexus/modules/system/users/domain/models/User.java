package com.factech.nexus.modules.system.users.domain.models;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Una persona del sistema (`RF-SP-024`).
 *
 * <p>Es el agregado que <b>crea el sujeto del módulo</b>: hasta él, `SP` tenía roles, permisos y
 * catálogos, y el {@code actor_id} de los cuatro registros de auditoría no resolvía a ninguna fila.
 *
 * <p><b>Nace {@code ACTIVO} y marcado para cambio obligatorio</b>, y ninguno de los dos se recibe
 * como argumento. La marca se activa siempre que <b>alguien que no es el titular</b> fija la
 * credencial, y su razón de ser es acotar a un solo inicio de sesión la ventana en que dos personas
 * conocen la misma contraseña: sin ella, la auditoría no puede atribuir con certeza lo que ocurra
 * en esa cuenta.
 *
 * <p><b>La contraseña no entra aquí en claro en ningún momento.</b> El agregado recibe el resumen
 * ya calculado; quien lo produce es {@code PasswordHasher}, y así esta clase no tiene forma de
 * exponerla ni de registrarla por descuido.
 */
@Entity
@Table(name = "users")
public class User {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  /** Inmutable (`RN-SP-016`): no hay operación que lo cambie, de ahí {@code updatable = false}. */
  @Column(name = "username", nullable = false, length = 50, updatable = false)
  private String username;

  /** Editable por `RF-SP-027`, y su cambio emite evento de seguridad por ser una vía de acceso. */
  @Column(name = "email", nullable = false, length = 255)
  private String email;

  @Column(name = "first_name", nullable = false, length = 100)
  private String firstName;

  @Column(name = "last_name", nullable = false, length = 100)
  private String lastName;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Column(name = "must_change_password", nullable = false)
  private boolean mustChangePassword;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private UserStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  /** La escribe únicamente `RF-SP-029`; el resto de requerimientos solo la leen. */
  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;

  /**
   * Hasta cuándo vale la credencial que <b>otra persona</b> fijó (`RF-SP-038`).
   *
   * <p>Va atada a {@code mustChangePassword} por {@code ck_users_provisional_expiry}: las dos
   * describen el mismo hecho —«esta contraseña no es suya»— y separarlas admitiría dos estados que
   * no significan nada.
   */
  @Column(name = "provisional_password_expires_at")
  private OffsetDateTime provisionalPasswordExpiresAt;

  /**
   * Roles que porta, como colección de identificadores.
   *
   * <p>{@code @ElementCollection} y no una asociación hacia {@code Role}: la asignación pertenece a
   * este agregado, mientras que el rol es de otro y tiene su propio ciclo de vida. Una asociación
   * entre entidades permitiría escribir en {@code roles} desde aquí.
   */
  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(
      name = "user_roles",
      joinColumns = @JoinColumn(name = "user_id", nullable = false))
  @Column(name = "role_id", nullable = false)
  private Set<UUID> roleIds = new LinkedHashSet<>();

  /** Exigido por JPA. */
  protected User() {}

  /**
   * Registra una persona.
   *
   * <p><b>Sin estado ni marca como argumentos</b> (`CA-SP-198`): la cuenta nace {@code ACTIVO} y
   * marcada. No admitirlos es lo que deja un solo camino hacia cada valor —`RF-SP-028` para el
   * estado, `RF-SP-037` para la marca— y un solo lugar donde auditarlo.
   *
   * @param passwordHash resumen ya calculado; la contraseña en claro nunca llega a este agregado
   */
  public static User create(
      UUID id,
      Username username,
      Email email,
      String firstName,
      String lastName,
      String passwordHash,
      Collection<UUID> roleIds,
      OffsetDateTime ahora) {

    User usuario = new User();
    usuario.id = id;
    usuario.username = username.value();
    usuario.email = email.value();
    usuario.firstName = firstName == null ? null : firstName.trim();
    usuario.lastName = lastName == null ? null : lastName.trim();
    usuario.passwordHash = passwordHash;
    usuario.mustChangePassword = true;
    usuario.status = UserStatus.ACTIVO;
    usuario.createdAt = ahora;
    usuario.updatedAt = ahora;
    usuario.deletedAt = null;
    usuario.roleIds.addAll(roleIds);
    return usuario;
  }

  public UUID getId() {
    return id;
  }

  public String getUsername() {
    return username;
  }

  public String getEmail() {
    return email;
  }

  public String getFirstName() {
    return firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public boolean isMustChangePassword() {
    return mustChangePassword;
  }

  public UserStatus getStatus() {
    return status;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }

  /** Copia defensiva: el conjunto de roles solo cambia por las operaciones del agregado. */
  public Set<UUID> getRoleIds() {
    return Set.copyOf(roleIds);
  }

  /**
   * Cambia el nombre y los apellidos (`RF-SP-027`).
   *
   * <p>Recibe los valores <b>ya normalizados</b> —recortados— y devuelve si hubo cambio de verdad.
   * Que lo decida el agregado y no el caso de uso es lo que hace que `FA-001` —reenviar lo mismo—
   * no pueda dejar un evento de auditoría describiendo algo que no ocurrió.
   *
   * <p>Un argumento nulo significa «no se envió», no «bórralo»: la columna es {@code NOT NULL} y
   * `ck_users_names_not_blank` impide además el blanco, de modo que el nulo explícito del cuerpo se
   * rechaza antes de llegar aquí.
   */
  public boolean rename(String nombre, String apellido, OffsetDateTime ahora) {
    boolean cambiaNombre = nombre != null && !nombre.equals(firstName);
    boolean cambiaApellido = apellido != null && !apellido.equals(lastName);

    if (!cambiaNombre && !cambiaApellido) {
      return false;
    }
    if (cambiaNombre) {
      this.firstName = nombre;
    }
    if (cambiaApellido) {
      this.lastName = apellido;
    }
    this.updatedAt = ahora;
    return true;
  }

  /**
   * Cambia el correo (`RF-SP-027`).
   *
   * <p>Compara contra el valor <b>ya normalizado</b>. Sin eso, enviar el correo propio en
   * mayúsculas parecería un cambio: dispararía la consulta de unicidad y produciría un conflicto de
   * la persona consigo misma, además de un evento de auditoría de algo que no cambió.
   */
  public boolean changeEmail(String correo, OffsetDateTime ahora) {
    if (correo == null || correo.equals(email)) {
      return false;
    }
    this.email = correo;
    this.updatedAt = ahora;
    return true;
  }

  /**
   * Otra persona fija la credencial (`RF-SP-038`).
   *
   * <p><b>No toca el estado ni el bloqueo.</b> Restablecer no es reactivar: una cuenta desactivada
   * sigue desactivada después de que le fijen una contraseña nueva, y una bloqueada sigue
   * bloqueada. Confundirlos convertiría esta operación en una vía lateral para devolver el acceso
   * sin pasar por la que existe para eso — y sin su motivo obligatorio.
   *
   * <p>La marca de cambio obligatorio y la caducidad se ponen <b>juntas</b>: describen el mismo
   * hecho, y el esquema rechaza una sin la otra.
   */
  public void resetPasswordBy(String passwordHash, OffsetDateTime caduca, OffsetDateTime ahora) {
    this.passwordHash = passwordHash;
    this.mustChangePassword = true;
    this.provisionalPasswordExpiresAt = caduca;
    this.updatedAt = ahora;
  }

  public OffsetDateTime getProvisionalPasswordExpiresAt() {
    return provisionalPasswordExpiresAt;
  }

  /**
   * El resumen de la credencial.
   *
   * <p>Se expone porque `RF-SP-034` tiene que compararlo, y no se expone la contraseña porque nunca
   * la hubo aquí. Ningún DTO de salida lo referencia, y `CA-SP-196` verifica esa ausencia.
   */
  public String getPasswordHash() {
    return passwordHash;
  }
}
