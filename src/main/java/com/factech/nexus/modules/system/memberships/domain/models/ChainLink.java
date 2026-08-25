package com.factech.nexus.modules.system.memberships.domain.models;

import java.util.UUID;

/**
 * Un eslabón de la cadena, con lo justo para decidir dónde entra una membresía nueva.
 *
 * <p>No es la entidad: {@link MembershipChain} razona sobre estos tres datos y nada más, y eso es
 * lo que permite probar `RN-SP-006` y `RN-SP-007` con listas en memoria, sin Spring y sin
 * PostgreSQL (Art. VI.3). El código y el nombre no intervienen en la decisión, de modo que no
 * viajan.
 *
 * @param parentId membresía de nivel jerárquico mayor —{@code level} menor—; nulo solo en la
 *     superior
 * @param level distancia hasta la cima: {@code 1} es la superior
 */
public record ChainLink(UUID id, UUID parentId, int level) {}
