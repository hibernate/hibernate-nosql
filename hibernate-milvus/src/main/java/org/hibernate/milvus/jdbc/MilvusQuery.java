/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.jdbc;

import java.util.List;

public final class MilvusQuery extends AbstractMilvusQuery implements MilvusStatementDefinition {
	private List<MilvusTypedValue> ids;

	public MilvusQuery() {
	}

	@Override
	public int parameterCount() {
		int count = super.parameterCount();
		if ( ids != null ) {
			for ( MilvusTypedValue id : ids ) {
				if ( id instanceof MilvusParameterValue ) {
					count++;
				}
			}
		}
		return count;
	}

	public List<MilvusTypedValue> getIds() {
		return ids;
	}

	public void setIds(List<MilvusTypedValue> ids) {
		this.ids = ids;
	}
}
