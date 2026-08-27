/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus;

public interface MilvusDatabaseHints {

	/**
	 * A database hint for specifying {@link io.milvus.v2.common.ConsistencyLevel} to use for a query.
	 *
	 * @see org.hibernate.query.Query#setHint(String, Object)
	 * @see org.hibernate.jpa.HibernateHints#HINT_QUERY_DATABASE
	 */
	String CONSISTENCY_LEVEL = "consistencyLevel";

}
