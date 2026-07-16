/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.jdbc;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;


@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT, property = "@type")
@JsonSubTypes({
		@JsonSubTypes.Type(value = MilvusStringValue.class),
		@JsonSubTypes.Type(value = MilvusNumberValue.class),
		@JsonSubTypes.Type(value = MilvusBooleanValue.class),
		@JsonSubTypes.Type(value = MilvusFloatVectorValue.class),
		@JsonSubTypes.Type(value = MilvusParameterValue.class),
})
public sealed interface MilvusTypedValue
		permits MilvusBooleanValue, MilvusFloatVectorValue, MilvusNumberValue, MilvusParameterValue, MilvusStringValue {
}
