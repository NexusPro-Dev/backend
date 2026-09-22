package com.factech.nexus.shared.notification;

/**
 * Qué clase de mensaje es, para que el canal pueda darle forma (`architecture.md` §15.1).
 *
 * <p><b>Es un nombre del dominio y no un identificador del proveedor.</b> La plantilla con estilos
 * vive alojada en Resend y se elige por configuración; si aquí figurase su identificador, cambiar
 * de proveedor —o de plantilla— obligaría a tocar el servicio que pide el envío, que es justo lo
 * que el puerto existe para evitar. Lo que viaja es «esto es una recuperación de contraseña»; qué
 * plantilla le corresponde lo decide el adaptador.
 *
 * <p><b>El código es estable y se usa como clave de configuración</b>, de modo que renombrarlo
 * rompe el despliegue en silencio: la variable de entorno deja de casar y el envío cae al texto
 * plano sin que nada falle. Si algún día hay que cambiarlo, hay que cambiar también
 * `application.yml` y las variables de todos los entornos.
 */
public enum NotificationKind {

  /** El correo de `RF-SP-040`, con el permiso para restablecer la propia contraseña. */
  PASSWORD_RECOVERY("password-recovery");

  private final String codigo;

  NotificationKind(String codigo) {
    this.codigo = codigo;
  }

  /** La clave con la que este tipo se busca en la configuración de plantillas. */
  public String codigo() {
    return codigo;
  }
}
