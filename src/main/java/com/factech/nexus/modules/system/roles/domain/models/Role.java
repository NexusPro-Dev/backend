package com.factech.nexus.modules.system.roles.domain.models;

import com.factech.nexus.modules.system.permissions.application.PermissionItem;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Agregado de rol (`RF-SP-001` · `T-11`, `T-12`).
 *
 * <p><b>Es a la vez agregado y modelo persistente.</b> `plan.md` §3 pedía separarlos —un {@code
 * Role} sin anotaciones y un {@code RoleEntity} con ellas, unidos por un {@code RoleJpaMapper}—,
 * pero `architecture.md` §5.1 cambió el 22-08-2026 y sitúa el modelo persistente en {@code
 * domain/models}, declarando de forma expresa que los planes de `RF-SP-001` a `RF-SP-009` quedaron
 * escritos sobre la disposición anterior y que la contradicción debe resolverse al aprobar sus
 * tareas. Se resuelve por la vía que el documento transversal admite y que el código ya implantado
 * de `RF-SP-010` sigue. Lo que se paga está escrito allí: las reglas de este agregado ya no se
 * pueden probar sin JPA en el classpath.
 *
 * <p><b>Dónde vive cada regla.</b> `RN-SEG-001` (unicidad) la decide el índice único parcial del
 * esquema; `RN-SEG-008` (no dejar huérfanos) la clave foránea. Aquí viven las que ninguna
 * restricción declarativa puede expresar: `RN-SP-002`, `RN-SP-003`, `RN-SEG-003` y `RN-SEG-010`.
 */
@Entity
@Table(name = "roles")
public class Role {

  private static final int LONGITUD_MAXIMA_NOMBRE = 100;

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Convert(converter = RoleCodeConverter.class)
  @Column(name = "code", nullable = false, length = 50, updatable = false)
  private RoleCode code;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "description")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "role_type", nullable = false, length = 20)
  private RoleType roleType;

  /**
   * Identificador del padre y no una referencia {@code @ManyToOne}.
   *
   * <p>Una asociación cargaría la cadena de ancestros entera al recorrerla, y este agregado no la
   * necesita: `RN-SEG-004` obliga a validar contra el padre <b>inmediato</b> y prohíbe recorrer la
   * cadena. La razón es aritmética —la contención es transitiva, de modo que validar contra el
   * padre inmediato garantiza el invariante en toda la cadena— y recorrerla solo añadiría
   * consultas.
   */
  @Column(name = "parent_role_id")
  private UUID parentRoleId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private RoleStatus status;

  @Column(name = "is_system", nullable = false)
  private boolean isSystem;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;

  /**
   * Permisos declarados, como colección de identificadores.
   *
   * <p>{@code @ElementCollection} y no {@code @ManyToMany} hacia {@code Permission}: la asociación
   * pertenece a este agregado —`RN-SP-005` la retira físicamente con él— mientras que el catálogo
   * es de otro agregado y es inmutable por API. Una asociación entre entidades acoplaría los dos
   * ciclos de vida y permitiría escribir en {@code permissions} desde aquí.
   *
   * <p>{@code created_at} de {@code role_permissions} no se mapea: lo escribe el {@code DEFAULT
   * now()} del esquema y ninguna consulta de este requerimiento lo lee.
   */
  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(
      name = "role_permissions",
      joinColumns = @JoinColumn(name = "role_id", nullable = false))
  @Column(name = "permission_id", nullable = false)
  private Set<UUID> permissionIds = new LinkedHashSet<>();

  /** Exigido por JPA. */
  protected Role() {}

  /**
   * Registra un rol nuevo, aplicando las reglas que no son expresables en el esquema.
   *
   * <p><b>El estado no es un argumento.</b> El rol nace {@code ACTIVO} siempre (`CA-SP-146`), y
   * {@code isSystem} nace {@code false}: un rol creado por la API nunca es de sistema, y solo la
   * migración de poblado lo pone en {@code true}.
   *
   * <p><b>El orden de las comprobaciones importa</b> y es el de `plan.md` §4: primero la contención
   * en el padre y después la contención en el actor. No es preferencia, es dependencia — ninguna de
   * las dos es evaluable sin haber resuelto antes el padre y los permisos.
   *
   * @param id identificador ya generado (Art. V.11: nunca lo genera la base de datos)
   * @param parent rol padre, ya verificado como existente y activo por el caso de uso
   * @param solicitados permisos declarados, ya resueltos contra el catálogo y sin duplicados
   * @param permisosDelActor permisos efectivos de quien ejecuta el alta, por código
   * @param ahora instante del alta, inyectado para que la prueba pueda fijarlo
   */
  public static Role create(
      UUID id,
      RoleCode code,
      String name,
      String description,
      RoleType roleType,
      Role parent,
      Collection<PermissionItem> solicitados,
      Set<String> permisosDelActor,
      OffsetDateTime ahora) {

    // RN-SP-002: rol padre obligatorio salvo en el rol raíz, que esta vía no
    // puede crear. Que llegue nulo aquí es un defecto de programación y no una
    // entrada inválida: VAL-004 ya lo rechazó en el DTO.
    if (parent == null) {
      throw new IllegalArgumentException("El rol padre es obligatorio (RN-SP-002).");
    }
    // RN-SP-003: todo rol se clasifica. Igual que arriba: VAL-003 lo cubre antes.
    if (roleType == null) {
      throw new IllegalArgumentException("La clasificación del rol es obligatoria (RN-SP-003).");
    }

    verificarContencionEnElPadre(solicitados, parent);
    verificarContencionEnElActor(solicitados, permisosDelActor);

    Role rol = new Role();
    rol.id = id;
    rol.code = code;
    rol.name = recortar(name);
    rol.description = recortar(description);
    rol.roleType = roleType;
    rol.parentRoleId = parent.getId();
    rol.status = RoleStatus.ACTIVO;
    rol.isSystem = false;
    rol.createdAt = ahora;
    rol.updatedAt = ahora;
    rol.deletedAt = null;
    solicitados.forEach(permiso -> rol.permissionIds.add(permiso.id()));
    return rol;
  }

  // ---------------------------------------------------------------------------
  // Escrituras posteriores al alta (`RF-SP-004` a `RF-SP-009`)
  //
  // Todas devuelven SI HUBO CAMBIO EFECTIVO, y ninguna toca `updatedAt` cuando
  // no lo hay: `FA-001` de cuatro requerimientos exige que reenviar el valor
  // actual no registre evento, y una marca de modificación movida sin cambio
  // dejaría la auditoría diciendo que alguien tocó algo que nadie tocó.
  // ---------------------------------------------------------------------------

  /**
   * Cambia el nombre (`RF-SP-004`).
   *
   * <p>El código NO se toca y no hay método para ello: es estable por diseño, porque cambiarlo
   * rompería cualquier referencia externa. Tampoco la clasificación, que determina si el rol puede
   * llevar membresía: cambiarla dejaría membresías colgando de un rol que ya no es consumidor
   * (`CA-SP-151`). Un rol mal clasificado se sustituye, no se corrige.
   */
  public boolean rename(String nuevo, OffsetDateTime ahora) {
    String recortado = recortar(nuevo);
    if (recortado == null || recortado.equals(name)) {
      return false;
    }
    name = recortado;
    updatedAt = ahora;
    return true;
  }

  /**
   * Cambia la descripción (`RF-SP-004`).
   *
   * <p><b>Admite dejarla en nulo</b>, y por eso quien llama debe haber distinguido antes «no la
   * envié» de «bórrala»: la columna es nulable y vaciarla es una orden legítima (`spec.md` §13). Es
   * la diferencia con el nombre, que no puede quedar vacío.
   */
  public boolean redescribe(String nueva, OffsetDateTime ahora) {
    String recortada = recortar(nueva);
    if (java.util.Objects.equals(recortada, description)) {
      return false;
    }
    description = recortada;
    updatedAt = ahora;
    return true;
  }

  /**
   * Activa o desactiva el rol (`RF-SP-007`).
   *
   * <p><b>Las asignaciones a personas se conservan intactas</b> (`CA-SP-051`): desactivar no es
   * retirar. Lo que cambia es que un rol inactivo deja de conceder permisos de inmediato
   * (`RN-SEG-002`), y eso ocurre solo, sin tocar nada más, porque los permisos efectivos se
   * resuelven contra los roles vigentes en cada petición.
   */
  public boolean changeStatus(RoleStatus nuevo, OffsetDateTime ahora) {
    if (nuevo == status) {
      return false;
    }
    status = nuevo;
    updatedAt = ahora;
    return true;
  }

  /**
   * Reubica el rol bajo otro padre (`RF-SP-008`, `RN-SEG-013`).
   *
   * <p><b>Revalida la contención contra el nuevo padre y NO retira nada</b> (`CA-SP-162`): si el
   * rol declara permisos que el nuevo padre no posee, la operación se rechaza entera enumerándolos,
   * para que el actor pueda retirarlos con `RF-SP-006` y reintentar. Recortarlos en silencio
   * dejaría al rol concediendo menos de lo que su titular cree.
   *
   * <p><b>Los hijos acompañan al rol y no hay que revisarlos</b>: si este cabe en el nuevo padre,
   * ellos caben en este por transitividad. Ese es exactamente el motivo por el que la contención se
   * valida contra el padre inmediato y nunca recorriendo la cadena (`RN-SEG-004`).
   *
   * <p>El ciclo (`RN-SEG-006`) no se comprueba aquí: exige recorrer la descendencia, que es una
   * consulta y no un invariante del agregado cargado.
   */
  public boolean changeParent(Role nuevoPadre, OffsetDateTime ahora) {
    if (nuevoPadre == null) {
      throw new IllegalArgumentException("El rol padre es obligatorio (RN-SP-002).");
    }
    if (nuevoPadre.getId().equals(parentRoleId)) {
      return false;
    }

    List<UUID> fuera =
        permissionIds.stream().filter(id -> !nuevoPadre.permissionIds.contains(id)).toList();

    if (!fuera.isEmpty()) {
      throw new BusinessRuleException(
          "RN-SEG-013",
          "El rol padre indicado no concede uno o más de los permisos del rol.",
          fuera.stream()
              .map(
                  id ->
                      new FieldError(
                          "parentRoleId",
                          "RN-SEG-013",
                          "El nuevo rol padre no posee el permiso '" + id + "'."))
              .toList());
    }

    parentRoleId = nuevoPadre.getId();
    updatedAt = ahora;
    return true;
  }

  /**
   * Agrega permisos (`RF-SP-005`).
   *
   * <p><b>La operación es idempotente y nunca retira nada</b> (`CA-SP-034`, `CA-SP-153`): los que
   * el rol ya declaraba se ignoran, y lo que devuelve es <b>lo realmente agregado</b>, que es lo
   * que la auditoría debe registrar. Si no queda nada por agregar, no hay cambio y no hay evento.
   *
   * <p><b>Sin rol padre no hay cota superior</b> (`CA-SP-035`, `FA-002`): el rol raíz omite
   * `RN-SEG-003` y conserva `RN-SEG-010`. Nadie otorga lo que no posee, ni siquiera en la raíz.
   *
   * @param padre rol padre inmediato, o {@code null} si es la raíz
   */
  public List<PermissionItem> grant(
      Collection<PermissionItem> solicitados,
      Role padre,
      Set<String> permisosDelActor,
      OffsetDateTime ahora) {

    if (padre != null) {
      verificarContencionEnElPadre(solicitados, padre);
    }
    verificarContencionEnElActor(solicitados, permisosDelActor);

    List<PermissionItem> agregados =
        solicitados.stream().filter(p -> !permissionIds.contains(p.id())).toList();

    if (agregados.isEmpty()) {
      return List.of();
    }
    agregados.forEach(p -> permissionIds.add(p.id()));
    updatedAt = ahora;
    return agregados;
  }

  /**
   * Retira permisos (`RF-SP-006`).
   *
   * <p>Idempotente igual que {@link #grant}: los que el rol no declaraba se ignoran, y devuelve
   * <b>lo realmente retirado</b>. La asociación se elimina <b>físicamente</b> (`RN-SP-005`,
   * `CA-SP-046`): no es una entidad de negocio y no lleva borrado lógico ni motivo.
   *
   * <p>`RN-SEG-005` —ningún rol hijo puede quedar declarando lo que el padre pierde— no se
   * comprueba aquí: exige consultar la descendencia.
   */
  public List<PermissionItem> revoke(Collection<PermissionItem> solicitados, OffsetDateTime ahora) {
    List<PermissionItem> retirados =
        solicitados.stream().filter(p -> permissionIds.contains(p.id())).toList();

    if (retirados.isEmpty()) {
      return List.of();
    }
    retirados.forEach(p -> permissionIds.remove(p.id()));
    updatedAt = ahora;
    return retirados;
  }

  /**
   * Marca el rol como eliminado (`RF-SP-009`).
   *
   * <p><b>Lógico y no físico</b>: el rol desaparece de las consultas y deja de poder asignarse,
   * pero la auditoría conserva qué era. Sus índices únicos son parciales sobre {@code deleted_at IS
   * NULL}, de modo que el código y el nombre quedan libres para un rol nuevo (`CA-SP-069`).
   *
   * <p><b>Los permisos declarados NO se borran de la fila</b>: dejan de tener efecto porque el rol
   * no está vigente, y conservarlos es lo que permite que el estado guardado en la auditoría de
   * eliminación diga qué concedía el rol en el momento de borrarse.
   */
  public void delete(OffsetDateTime ahora) {
    deletedAt = ahora;
    updatedAt = ahora;
  }

  /**
   * `RN-SEG-003`: los permisos son un subconjunto de los del rol padre.
   *
   * <p>Se enumeran <b>todos</b> los infractores y no el primero: la especificación exige informar
   * qué permisos lo incumplen, y devolverlos de a uno convierte una corrección en varias vueltas.
   */
  private static void verificarContencionEnElPadre(
      Collection<PermissionItem> solicitados, Role parent) {

    List<PermissionItem> fuera =
        solicitados.stream().filter(p -> !parent.permissionIds.contains(p.id())).toList();

    if (!fuera.isEmpty()) {
      throw new BusinessRuleException(
          "RN-SEG-003",
          "Un rol no puede declarar permisos que su rol padre no posee.",
          fuera.stream()
              .map(
                  p ->
                      new FieldError(
                          "permissionIds",
                          "RN-SEG-003",
                          "El rol padre no posee el permiso '" + p.code() + "'."))
              .toList());
    }
  }

  /**
   * `RN-SEG-010`: nadie otorga permisos que no posee.
   *
   * <p>No se resuelve con el permiso de acceso: {@code roles:create} habilita a crear roles, no a
   * decidir con qué alcance. El techo lo pone el conjunto de permisos efectivos del actor.
   *
   * <p>La comparación va por <b>código</b> y no por identificador porque así es como el actor porta
   * sus permisos efectivos; el identificador solo existe del lado del catálogo.
   */
  private static void verificarContencionEnElActor(
      Collection<PermissionItem> solicitados, Set<String> permisosDelActor) {

    List<PermissionItem> fuera =
        solicitados.stream().filter(p -> !permisosDelActor.contains(p.code())).toList();

    if (!fuera.isEmpty()) {
      throw new BusinessRuleException(
          "RN-SEG-010",
          "No puede otorgar permisos que usted no posee.",
          fuera.stream()
              .map(
                  p ->
                      new FieldError(
                          "permissionIds",
                          "RN-SEG-010",
                          "Usted no posee el permiso '" + p.code() + "'."))
              .toList());
    }
  }

  /**
   * Recorta espacios al inicio y al final.
   *
   * <p>Sin este recorte, {@code "Contabilidad "} y {@code "Contabilidad"} serían dos nombres
   * distintos para {@code uq_roles_name} y la unicidad se burlaría con un espacio. El código no se
   * toca: se rechaza si no cumple el formato.
   */
  private static String recortar(String valor) {
    if (valor == null) {
      return null;
    }
    String recortado = valor.trim();
    return recortado.isEmpty() ? null : recortado;
  }

  /** Longitud máxima del nombre, publicada para que el DTO no la duplique con otro número. */
  public static int longitudMaximaNombre() {
    return LONGITUD_MAXIMA_NOMBRE;
  }

  public UUID getId() {
    return id;
  }

  public RoleCode getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public RoleType getRoleType() {
    return roleType;
  }

  public UUID getParentRoleId() {
    return parentRoleId;
  }

  public RoleStatus getStatus() {
    return status;
  }

  public boolean isSystem() {
    return isSystem;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public OffsetDateTime getDeletedAt() {
    return deletedAt;
  }

  /** Copia defensiva: el conjunto de permisos solo cambia por las operaciones del agregado. */
  public Set<UUID> getPermissionIds() {
    return Set.copyOf(permissionIds);
  }

  /** Un rol eliminado lógicamente se trata como inexistente (`spec.md` §13). */
  public boolean isDeleted() {
    return deletedAt != null;
  }

  /** Activo y no eliminado: es lo que `EX-002` exige de un rol para poder ser padre. */
  public boolean isUsableAsParent() {
    return !isDeleted() && status == RoleStatus.ACTIVO;
  }

  /**
   * El rol raíz es el único sin padre (`RN-SEG-007`).
   *
   * <p>Se pregunta por la ausencia de padre y no por el código: la raíz se nombra de forma
   * explícita en las prohibiciones de `RF-SP-007` y `RF-SP-009` <b>aunque hoy sea de sistema</b>,
   * porque un rol raíz inactivo o eliminado dejaría al sistema sin su última vía de administración
   * — y esa protección no debe depender de que alguien recuerde marcarlo como de sistema.
   */
  public boolean isRoot() {
    return parentRoleId == null;
  }
}
