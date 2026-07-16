/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.jdbc;

public record MilvusFloatVectorValue(float[] value) implements MilvusTypedValue {
}
