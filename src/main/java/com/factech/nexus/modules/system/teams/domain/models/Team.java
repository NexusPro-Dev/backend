package com.factech.nexus.modules.system.teams.domain.models;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.patch.Patchable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Un equipo de la fuerza comercial (`RF-SP-063`): el cajón en el que se organiza la <b>cúspide</b>.
 *
 * <p><b>Agrupa, no manda.</b> Quién está a cargo de quién lo dice `user_supervisors`; esto
 * particiona las raíces de ese bosque, y por eso no tiene rol, ni jefe, ni padre: encima de la
 * cúspide no hay nada que ordenar (`requirements/sp.md` §10.20).
 *
 * <p><b>Sin código</b>, al contrario que un rol o una membresía: nada del sistema referencia a un
 * equipo por un nombre estable, y un código que nadie usa es una columna más que mantener única. Lo
 * que identifica es el nombre, único entre los no eliminados sin distinguir mayúsculas ni acentos
 * (`RN-SP-050`), y **por eso mismo sí se corrige** (`RF-SP-066`): si no hubiera forma de cambiarlo,
 * una errata del alta sería permanente.
 *
 * <p><b>Nace vacío y `ACTIVO`.</b> Los miembros entran con `RF-SP-069`, que tiene reglas propias;
 * mezclarlo con el alta haría que un nombre repetido y una persona que no es manager salieran por
 * el mismo `409` sin decir cuál de las dos cosas falló.
 */
@Entity
@Table(name = "teams")
public class Team {

  private static final int NOMBRE_MAXIMO = 100;
  private static final int DESCRIPCION_MAXIMA = 500;

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "description")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private TeamStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;

  /** Exigido por JPA. */
  protected Team() {}

  /**
   * Registra un equipo vacío y activo.
   *
   * <p>La forma de los dos campos se comprueba aquí <b>además</b> de en el DTO: el DTO devuelve los
   * errores juntos (`CA-SP-733`), y el agregado es la red para cualquier otra vía de construcción
   * —una semilla, una prueba, un caso de uso futuro.
   */
  public static Team create(UUID id, String name, String description, OffsetDateTime ahora) {
    Team equipo = new Team();
    equipo.id = id;
    equipo.name = verificarNombre(name);
    equipo.description = verificarDescripcion(description);
    equipo.status = TeamStatus.ACTIVO;
    equipo.createdAt = ahora;
    equipo.updatedAt = ahora;
    return equipo;
  }

  /**
   * Corrige el nombre y la descripción (`RF-SP-066`), y devuelve <b>el diff</b> de lo que de verdad
   * cambió.
   *
   * <p><b>Devolver el diff y no un booleano</b> es lo que permite que la auditoría registre QUÉ
   * cambió y no solo QUE hubo una edición: el mapa vacío significa que la petición era válida y no
   * movió nada —renombrar al mismo nombre, por ejemplo—, y entonces no se emite fila. Es la forma
   * que dejó escrita `RF-AC-004`.
   *
   * <p><b>Ausente no es nulo.</b> Un campo que no viene se queda como estaba; una descripción en
   * nulo explícito se borra. Sin esa distinción no habría forma de vaciar un campo opcional sin
   * inventarle un endpoint propio.
   *
   * <p><b>El nombre nulo NO borra</b>, al contrario que la descripción: un equipo sin nombre no
   * existe (`RN-SP-050`), de modo que `name: null` es un dato inválido y lo rechaza el caso de uso
   * antes de llegar aquí.
   */
  public Map<String, Object> update(
      Patchable<String> nuevoNombre, Patchable<String> nuevaDescripcion, OffsetDateTime ahora) {
    Map<String, Object> cambios = new LinkedHashMap<>();
    if (nuevoNombre.presente() && nuevoNombre.valor() != null) {
      String valor = verificarNombre(nuevoNombre.valor());
      if (!Objects.equals(valor, name)) {
        cambios.put("name", Map.of("before", texto(name), "after", texto(valor)));
        name = valor;
      }
    }
    if (nuevaDescripcion.presente()) {
      String valor = verificarDescripcion(nuevaDescripcion.valor());
      if (!Objects.equals(valor, description)) {
        cambios.put("description", Map.of("before", texto(description), "after", texto(valor)));
        description = valor;
      }
    }
    // La marca de tiempo avanza AUNQUE el diff salga vacío: la petición se
    // atendió, y `CA-SP-756` exige que `updatedAt` deje de ser igual a
    // `createdAt`. Lo que no se emite sin cambios es la fila de auditoría.
    updatedAt = ahora;
    return cambios;
  }

  /** Un nulo no viaja bien dentro del diff en JSON: se escribe como ausencia legible. */
  private static Object texto(String valor) {
    return valor == null ? "" : valor;
  }

  /**
   * Reactiva el equipo (`RF-SP-067`) y devuelve <b>si hubo cambio</b>.
   *
   * <p><b>Devolver el booleano es lo que sostiene la idempotencia.</b> El caso de uso audita solo
   * cuando cambió algo, y la marca de tiempo no avanza por una petición que pedía el estado que ya
   * se tenía: sin eso, cada reintento de un cliente con mala red dejaría una fila de auditoría y
   * movería `updatedAt`, que es cómo una auditoría deja de poder leerse.
   *
   * <p><b>Aquí `updatedAt` avanza solo si cambió</b>, al contrario que en {@link #update}, donde
   * avanza aunque el diff salga vacío. No es una incoherencia: una corrección con los mismos
   * valores es una petición que se atendió sobre los datos —`CA-SP-756` lo exige—, mientras que
   * pedir el estado que ya se tiene no es una escritura, es un reintento.
   */
  public boolean activate(OffsetDateTime ahora) {
    if (status == TeamStatus.ACTIVO) {
      return false;
    }
    status = TeamStatus.ACTIVO;
    updatedAt = ahora;
    return true;
  }

  /**
   * Suspende el equipo (`RF-SP-067`) y devuelve <b>si hubo cambio</b>.
   *
   * <p><b>No toca una sola pertenencia</b> (`RN-SP-053`): `INACTIVO` significa que el equipo no
   * recibe a nadie más, no que se vacíe. Cerrar aquí las pertenencias movería la atribución de toda
   * una red —cada manager arrastra a sus directores y a los agentes de estos— sin que nadie lo
   * hubiera decidido, y las comisiones leerán ese historial para repartir. Vaciar es otra
   * operación, miembro a miembro y con motivo (`RF-SP-070`).
   *
   * <p><b>Y no comprueba nada</b>: que un equipo suspendido no reciba miembros lo aplica quien
   * intenta entrar (`RF-SP-069`). La regla vive en un solo sitio a propósito.
   */
  public boolean deactivate(OffsetDateTime ahora) {
    if (status == TeamStatus.INACTIVO) {
      return false;
    }
    status = TeamStatus.INACTIVO;
    updatedAt = ahora;
    return true;
  }

  /**
   * Elimina el equipo (`RF-SP-068`) y devuelve <b>si hubo cambio</b>: la baja es lógica y <b>nada
   * más de la fila cambia</b>.
   *
   * <p><b>`status` se conserva a propósito.</b> Un equipo eliminado guarda el estado que tenía, y
   * el registro de baja lo cuenta: apagarlo al eliminar inventaría un hecho que nadie decidió y
   * haría indistinguible «se suspendió y luego se eliminó» de «se eliminó estando activo».
   *
   * <p><b>Las pertenencias no se tocan</b> (`RN-SP-052`): las cerradas se conservan como historial
   * y las vigentes no existen aquí, porque el caso de uso rechaza la baja si hay alguna
   * (`RN-SP-054`).
   */
  public boolean delete(OffsetDateTime ahora) {
    if (deletedAt != null) {
      return false;
    }
    deletedAt = ahora;
    return true;
  }

  public boolean estaEliminado() {
    return deletedAt != null;
  }

  public boolean estaActivo() {
    return status == TeamStatus.ACTIVO;
  }

  /**
   * La instantánea para la auditoría: la misma para la creación y para la baja, de modo que el
   * registro de creación y el de eliminación describan el mismo equipo con las mismas claves. El
   * retiro le añade a quienes pasaron por él (`RF-SP-068`).
   */
  public Map<String, Object> instantanea() {
    Map<String, Object> estado = new LinkedHashMap<>();
    estado.put("name", name);
    estado.put("description", description);
    estado.put("status", status.name());
    return estado;
  }

  private static String verificarNombre(String valor) {
    String recortado = recortar(valor);
    if (recortado == null || recortado.length() > NOMBRE_MAXIMO) {
      String mensaje = "El nombre es obligatorio y no puede superar los 100 caracteres.";
      throw new ValidationException(
          "VAL-001", mensaje, List.of(new FieldError("name", "VAL-001", mensaje)));
    }
    return recortado;
  }

  /** Una descripción de solo espacios se guarda <b>nula</b>: es una errata, no un valor. */
  private static String verificarDescripcion(String valor) {
    String recortado = recortar(valor);
    if (recortado != null && recortado.length() > DESCRIPCION_MAXIMA) {
      String mensaje = "La descripción no puede exceder 500 caracteres.";
      throw new ValidationException(
          "VAL-002", mensaje, List.of(new FieldError("description", "VAL-002", mensaje)));
    }
    return recortado;
  }

  private static String recortar(String valor) {
    if (valor == null) {
      return null;
    }
    String recortado = valor.trim();
    return recortado.isEmpty() ? null : recortado;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public TeamStatus getStatus() {
    return status;
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
}
