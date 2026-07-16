/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.jdbc;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;


@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT, property = "@type")
@JsonSubTypes({
		@JsonSubTypes.Type(value = MilvusRRFRanker.class),
		@JsonSubTypes.Type(value = MilvusWeightedRanker.class),
})
public sealed interface MilvusRanker
		permits MilvusRRFRanker, MilvusWeightedRanker {
}
