/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.jdbc.internal;

import io.milvus.v2.service.vector.response.QueryResp;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;

public class MilvusQueryResultSet extends AbstractMilvusResultSet {

	private final QueryResp queryResp;

	public MilvusQueryResultSet(MilvusStatement statement, QueryResp queryResp, String collectionName, @Nullable List<String> fields) {
		super(statement, collectionName, fields);
		this.queryResp = queryResp;
	}

	@Override
	protected int resultSize() {
		return queryResp.getQueryResults().size();
	}

	@Override
	protected Object getField(int position, String field) {
		final QueryResp.QueryResult queryResult = queryResp.getQueryResults().get( position );
		return queryResult.getEntity().get( field );
	}
}
