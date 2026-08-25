package com.factech.nexus.modules.system.memberships.application;

/**
 * Parámetros de {@code GET /api/v1/memberships} (`RF-SP-017`).
 *
 * <p><b>Un solo campo, y eso es la implementación de una decisión.</b> No hay {@code page}, {@code
 * size} ni {@code sort}: `spec.md` §6.1 lo decide de forma explícita, y Spring ignora en silencio
 * los parámetros de consulta que un DTO no declara. Un cliente que envíe {@code ?page=2} recibe la
 * cadena entera, que es la respuesta correcta a una petición que pide algo que este recurso no
 * ofrece.
 *
 * <p>No se admite ordenamiento arbitrario, y aquí el motivo es más fuerte que en el catálogo de
 * permisos: <b>el orden es la información</b>. Ofrecer {@code sort=name,asc} produciría una lista
 * alfabética de niveles, que es un artefacto sin significado.
 *
 * @param search sobre código y nombre; recortado, y en blanco equivale a ausente
 */
public record ListMembershipsRequest(String search) {}
