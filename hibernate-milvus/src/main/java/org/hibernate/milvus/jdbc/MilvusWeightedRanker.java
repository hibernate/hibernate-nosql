/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.jdbc;

import java.util.List;

public record MilvusWeightedRanker(List<Float> weights) implements MilvusRanker {
}
