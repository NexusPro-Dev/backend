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

  /**
   * Dónde está la persona (`RN-SP-034`).
   *
   * <p><b>Es un identificador y no una asociación hacia {@code Country}</b>, por el mismo criterio
   * con el que {@code roleIds} lo es: el país pertenece a otro agregado y tiene su propio ciclo de
   * vida. Una asociación entre entidades permitiría escribir en {@code countries} desde aquí, que
   * es justo lo que `RN-SP-009` prohíbe.
   *
   * <p><b>Y es una columna y no una tabla puente</b>, al contrario que la membresía y el superior
   * comercial. Aquellas dos llevan tabla porque <b>tienen vigencia</b> —se conceden, vencen, se
   * sustituyen, y hay que poder decir cuál regía <i>entonces</i>—; el país no tiene periodo y nadie
   * pregunta dónde estaba alguien el mes pasado. El rastro de cada cambio lo guarda {@code
   * audit_change_log}.
   *
   * <p><b>Que el país esté activo NO se comprueba aquí.</b> El agregado no consulta el catálogo; lo
   * verifica el caso de uso antes de construirlo, y solo <b>en el momento de asignarlo</b> — quien
   * ya lo tenía lo conserva aunque se desactive después (`RF-SP-022`).
   */
  @Column(name = "country_id", nullable = false)
  private UUID countryId;

  /**
   * Con qué se identifica la persona (`RN-SP-035`).
   *
   * <p><b>Nulable en el esquema y obligatorio en la API</b>, y la asimetría con {@link #countryId}
   * es deliberada: un país de relleno es una afirmación neutra, y un <b>número de documento</b> de
   * relleno es una afirmación <b>falsa sobre la identidad de una persona</b>. `V22` siembra un
   * superadministrador que no tiene ninguno, y el nulo es la verdad sobre esa fila.
   *
   * <p><b>Que el documento acredite mayoría de edad NO se comprueba aquí ni en ningún sitio.</b> El
   * catálogo al que apunta solo contiene documentos de adulto, de modo que no hay identificador que
   * poner que signifique lo contrario.
   */
  @Column(name = "document_type_id")
  private UUID documentTypeId;

  /**
   * El número, ya normalizado — recortado y en mayúsculas.
   *
   * <p><b>Va inseparablemente unido al tipo</b> (`ck_users_document_pair`): no existe el número sin
   * decir de qué documento es, ni el tipo sin número. Y el par es <b>único entre todas las
   * personas, incluidas las eliminadas</b>, con el mismo criterio que el nombre de usuario: liberar
   * un documento permitiría que la actividad de dos personas quedara bajo la misma identidad.
   */
  @Column(name = "document_number", length = 30)
  private String documentNumber;

  /** Línea principal de la dirección postal. Opcional (`RN-SP-037`). */
  @Column(name = "address_line1", length = 150)
  private String addressLine1;

  /**
   * Complemento: apartamento, torre, referencia.
   *
   * <p>Es el único de los seis campos que es opcional <b>por naturaleza y no por transición</b>:
   * una dirección puede no tener complemento, y eso no es un dato que falte.
   */
  @Column(name = "address_line2", length = 150)
  private String addressLine2;

  /**
   * Ciudad de residencia, como <b>texto libre</b>.
   *
   * <p>No hay catálogo de ciudades y no se abre aquí: exigiría decidir su relación con el país y su
   * unicidad, y ningún requerimiento lo respalda.
   */
  @Column(name = "city", length = 100)
  private String city;

  /**
   * Vía de contacto, normalizada a dígitos con un {@code +} opcional (`RN-SP-037`).
   *
   * <p><b>Obligatorio en la API y nulable en el esquema</b>, por lo mismo que el documento. <b>No
   * se valida contra el país</b>: eso exigiría un catálogo de prefijos que nadie ha pedido.
   */
  @Column(name = "phone", length = 20)
  private String phone;

  /**
   * El teléfono de la empresa (`RN-SP-037`, 10-09-2026).
   *
   * <p><b>Opcional siempre, y su nulo es un hecho y no un dato pendiente</b>: esa persona no tiene
   * teléfono de empresa. Es la diferencia con {@link #phone} y con {@link #documentNumber}, cuyo
   * nulo solo existe en las filas anteriores a la regla que los exige. Por eso es el único dato de
   * contacto que <b>se puede vaciar de vuelta</b> una vez escrito.
   */
  @Column(name = "company_phone", length = 20)
  private String companyPhone;

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
  // `role_type` LO PONE LA SENTENCIA, LEYÉNDOLO DE `roles`, y no el agregado
  // (`RN-SP-025`, `V52`). Es una copia desnormalizada que existe para que la
  // regla viva en el motor, y la clave foránea compuesta impide que mienta.
  //
  // Se sobreescribe el `INSERT` en lugar de añadir el tipo a la colección
  // porque **este agregado no tiene por qué conocerlo**: `roleIds` es un
  // conjunto de identificadores, y meterle el tipo dentro daría al dominio un
  // dato que podría declarar mal — la clave foránea lo rechazaría, sí, pero el
  // fallo saldría como `500` en vez de ser imposible de cometer.
  //
  // El orden de los parámetros es el que Hibernate usa para una colección de
  // elementos: primero la clave del dueño, después el elemento.
  @org.hibernate.annotations.SQLInsert(
      sql =
          "INSERT INTO user_roles (user_id, role_id, role_type)"
              + " SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?")
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
   * @param documento tipo y número (`RN-SP-035`). <b>Los dos juntos o ninguno</b>: no hay forma de
   *     construir media identidad. Que el tipo exista y esté activo lo verifica el caso de uso; que
   *     acredite mayoría de edad <b>no lo verifica nadie</b>, porque el catálogo solo ofrece los
   *     que la acreditan
   * @param contacto teléfono y dirección (`RN-SP-037`). El teléfono es obligatorio en la API y los
   *     tres campos de dirección son opcionales
   * @param countryId país de la persona (`RN-SP-034`). <b>Obligatorio y sin valor por defecto</b>:
   *     no hay país de reserva, porque uno cableado aquí acabaría asignándose en silencio a quien
   *     olvidara declararlo. Que exista y esté activo lo verifica el caso de uso
   */
  public static User create(
      UUID id,
      Username username,
      Email email,
      String firstName,
      String lastName,
      String passwordHash,
      UUID countryId,
      DocumentIdentity documento,
      ContactDetails contacto,
      Collection<UUID> roleIds,
      OffsetDateTime ahora) {

    if (countryId == null) {
      throw new IllegalArgumentException("RN-SP-034: toda persona pertenece a un país.");
    }

    User usuario = new User();
    usuario.id = id;
    usuario.username = username.value();
    usuario.email = email.value();
    usuario.firstName = firstName == null ? null : firstName.trim();
    usuario.lastName = lastName == null ? null : lastName.trim();
    usuario.countryId = countryId;
    usuario.aplicarDocumento(documento);
    usuario.aplicarContacto(contacto);
    usuario.passwordHash = passwordHash;
    usuario.mustChangePassword = true;
    usuario.status = UserStatus.ACTIVO;
    usuario.createdAt = ahora;
    usuario.updatedAt = ahora;
    usuario.deletedAt = null;
    usuario.roleIds.addAll(roleIds);
    return usuario;
  }

  /**
   * Quien se registra por sí mismo desde un enlace (`RF-SP-045`).
   *
   * <p><b>Se separa de {@link #create} en lugar de añadirle dos parámetros</b>, y no es una
   * preferencia de estilo: lo que cambia son <b>dos invariantes</b>, y las dos hacia el lado
   * peligroso. Un parámetro booleano de más en el alta administrativa —llamada desde varios sitios—
   * es una llamada de distancia de crear cuentas que no caducan la contraseña o que nacen pudiendo
   * operar sin depósito.
   *
   * <ul>
   *   <li><b>Nace en {@code FTD_PENDIENTE} y no en {@code ACTIVO}</b>: autentica y <b>no opera</b>
   *       hasta que haya un primer depósito confirmado.
   *   <li><b>NO queda marcada para cambio obligatorio</b>: la contraseña <b>la eligió su
   *       titular</b> y nadie más la conoce. Es la misma distinción que `RF-SP-040` hizo frente al
   *       restablecimiento por un administrador — se marca lo que fijó otra persona, no lo que fijó
   *       uno mismo.
   * </ul>
   */
  public static User selfRegister(
      UUID id,
      Username username,
      Email email,
      String firstName,
      String lastName,
      String passwordHash,
      UUID countryId,
      DocumentIdentity documento,
      ContactDetails contacto,
      Collection<UUID> roleIds,
      UserStatus estadoInicial,
      OffsetDateTime ahora) {

    User usuario =
        create(
            id,
            username,
            email,
            firstName,
            lastName,
            passwordHash,
            countryId,
            documento,
            contacto,
            roleIds,
            ahora);

    usuario.mustChangePassword = false;
    usuario.status = estadoInicial;
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

  public UUID getCountryId() {
    return countryId;
  }

  public UUID getDocumentTypeId() {
    return documentTypeId;
  }

  public String getDocumentNumber() {
    return documentNumber;
  }

  public String getAddressLine1() {
    return addressLine1;
  }

  public String getAddressLine2() {
    return addressLine2;
  }

  public String getCity() {
    return city;
  }

  public String getPhone() {
    return phone;
  }

  public String getCompanyPhone() {
    return companyPhone;
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
   * Cambia el país (`RF-SP-027`, `RN-SP-034`).
   *
   * <p>Mismo contrato que {@link #rename} y {@link #changeEmail}: devuelve si hubo cambio de
   * verdad, y que lo decida el agregado es lo que impide que reenviar el mismo país deje un evento
   * de auditoría describiendo algo que no ocurrió.
   *
   * <p>Un argumento nulo significa «no se envió», <b>nunca «bórralo»</b>. La columna es {@code NOT
   * NULL} y el estado «persona sin país» no existe, de modo que el nulo explícito del cuerpo se
   * rechaza en el DTO —con {@code 400}— antes de llegar aquí. Es la misma distinción que
   * `RF-SP-027` hace con el nombre y el correo.
   *
   * <p><b>No se comprueba que el país exista ni que esté activo.</b> El agregado no consulta el
   * catálogo; y la comprobación que el caso de uso hace es sobre el país <b>de destino</b>, nunca
   * sobre el actual — si exigiera que el vigente estuviera activo, no se podría sacar a nadie de un
   * país recién desactivado, que es justo para lo que esta operación hace falta.
   */
  public boolean changeCountry(UUID pais, OffsetDateTime ahora) {
    if (pais == null || pais.equals(countryId)) {
      return false;
    }
    this.countryId = pais;
    this.updatedAt = ahora;
    return true;
  }

  /**
   * Cambia la identidad documental (`RF-SP-027`, `RN-SP-035`).
   *
   * <p>Mismo contrato que {@link #rename}: devuelve si hubo cambio de verdad, para que reenviar el
   * mismo documento no deje un evento de auditoría describiendo algo que no ocurrió.
   *
   * <p><b>El tipo y el número se cambian a la vez o no se cambian.</b> No hay forma de mover uno
   * solo, y no es una comodidad: {@code ck_users_document_pair} lo impide en el motor, y admitirlo
   * aquí produciría una violación de integridad traducida a {@code 500} en lugar del {@code 400}
   * que corresponde.
   *
   * <p><b>Un argumento nulo significa «no se envió», nunca «bórralo»</b>: `RN-SP-035` no admite
   * dejar a alguien sin documento, y el nulo explícito del cuerpo se rechaza en el DTO.
   *
   * <p><b>El documento anterior NO queda libre.</b> Corregir una errata es legítimo y la unicidad
   * sigue siendo total: un documento identifica a una persona en el mundo real, y liberarlo
   * permitiría que otra ficha lo tomara. Es la diferencia deliberada con el correo, que sí se
   * libera al cambiarlo.
   */
  public boolean changeDocument(DocumentIdentity documento, OffsetDateTime ahora) {
    if (documento == null || !documento.completa()) {
      return false;
    }
    if (documento.typeId().equals(documentTypeId) && documento.number().equals(documentNumber)) {
      return false;
    }
    aplicarDocumento(documento);
    this.updatedAt = ahora;
    return true;
  }

  /**
   * Cambia los datos de contacto (`RF-SP-027`, `RF-SP-044`, `RN-SP-037`).
   *
   * <p><b>Es la única operación del agregado donde un nulo puede ser una orden</b>, y por eso no
   * recibe los campos sueltos sino un {@link ContactDetails} que ya distingue «no se envió» de «se
   * envió vacío». La dirección, el complemento y la ciudad son opcionales: «ya no vive ahí» es un
   * hecho que hay que poder registrar, y rechazar el vaciado dejaría la dirección vieja pegada para
   * siempre.
   *
   * <p><b>El teléfono es la excepción dentro de la excepción</b>: `RN-SP-037` lo hace obligatorio,
   * de modo que su nulo explícito se rechaza en el DTO y aquí solo llega como «no se envió».
   */
  public boolean changeContact(ContactDetails contacto, OffsetDateTime ahora) {
    if (contacto == null || !contacto.informaAlgo()) {
      return false;
    }
    String telefonoNuevo = contacto.phone().orElse(phone);
    String telefonoEmpresaNuevo = contacto.companyPhone().resuelto(companyPhone);
    String linea1Nueva = contacto.addressLine1().resuelto(addressLine1);
    String linea2Nueva = contacto.addressLine2().resuelto(addressLine2);
    String ciudadNueva = contacto.city().resuelto(city);

    boolean cambia =
        !java.util.Objects.equals(telefonoNuevo, phone)
            || !java.util.Objects.equals(telefonoEmpresaNuevo, companyPhone)
            || !java.util.Objects.equals(linea1Nueva, addressLine1)
            || !java.util.Objects.equals(linea2Nueva, addressLine2)
            || !java.util.Objects.equals(ciudadNueva, city);

    if (!cambia) {
      return false;
    }
    this.phone = telefonoNuevo;
    this.companyPhone = telefonoEmpresaNuevo;
    this.addressLine1 = linea1Nueva;
    this.addressLine2 = linea2Nueva;
    this.city = ciudadNueva;
    this.updatedAt = ahora;
    return true;
  }

  private void aplicarDocumento(DocumentIdentity documento) {
    if (documento == null || !documento.completa()) {
      return;
    }
    this.documentTypeId = documento.typeId();
    this.documentNumber = documento.number();
  }

  private void aplicarContacto(ContactDetails contacto) {
    if (contacto == null) {
      return;
    }
    this.phone = contacto.phone().orElse(null);
    this.companyPhone = contacto.companyPhone().resuelto(null);
    this.addressLine1 = contacto.addressLine1().resuelto(null);
    this.addressLine2 = contacto.addressLine2().resuelto(null);
    this.city = contacto.city().resuelto(null);
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
