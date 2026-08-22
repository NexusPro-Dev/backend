package com.factech.nexus.modules.system.permissions.application;

/**
 * Parámetros de consulta del catálogo (`RF-SP-010` · `T-08`).
 *
 * <p><b>Exactamente tres campos, y ninguno más.</b> No hay {@code page}, {@code size} ni {@code
 * sort}: `spec.md` §6.1 decide que el catálogo no se pagina. Que el DTO no los declare es lo que lo
 * hace verificable, porque {@code FAIL_ON_UNKNOWN_PROPERTIES} no alcanza aquí —Spring ignora en
 * silencio los parámetros de consulta desconocidos—. Un cliente que envíe {@code ?page=2} recibe el
 * catálogo entero, que es la respuesta correcta a una petición que pide algo que este recurso no
 * ofrece.
 *
 * <p><b>Sin Bean Validation</b>, porque `spec.md` §11 no declara ninguna validación: los tres
 * filtros son opcionales y cualquier valor es admisible, incluido uno que no corresponda a ningún
 * permiso. Es la diferencia con `RF-SP-002`, donde {@code status} y {@code roleType} tienen dominio
 * cerrado; aquí el dominio de {@code resource} y {@code action} <b>es</b> el contenido de la tabla,
 * y consultarlo es justamente lo que hace este endpoint.
 */
public record ListPermissionsRequest(String resource, String action, String search) {

  /** Traduce los parámetros de HTTP a los criterios que entiende el dominio. */
  public ListPermissionsQuery toQuery() {
    return ListPermissionsQuery.of(resource, action, search);
  }
}
