/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.jdbc;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT, property = "@type")
@JsonSubTypes({
		@JsonSubTypes.Type(value = MilvusCreateCollection.class),
		@JsonSubTypes.Type(value = MilvusQuery.class),
		@JsonSubTypes.Type(value = MilvusSearch.class),
		@JsonSubTypes.Type(value = MilvusHybridSearch.class),
		@JsonSubTypes.Type(value = MilvusDropCollection.class),
		@JsonSubTypes.Type(value = MilvusInsert.class),
		@JsonSubTypes.Type(value = MilvusUpsert.class),
		@JsonSubTypes.Type(value = MilvusDelete.class)
})
public sealed interface MilvusStatementDefinition
		permits MilvusCreateCollection, MilvusDelete, MilvusDropCollection, MilvusHybridSearch, MilvusInsert, MilvusQuery, MilvusSearch, MilvusUpsert {

	int parameterCount();
}
