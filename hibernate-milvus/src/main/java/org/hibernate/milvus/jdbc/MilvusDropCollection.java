/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.jdbc;

public record MilvusDropCollection(
		String collectionName,
		Boolean async,
		Long timeout
) implements MilvusStatementDefinition {
	@Override
	public int parameterCount() {
		return 0;
	}
}
