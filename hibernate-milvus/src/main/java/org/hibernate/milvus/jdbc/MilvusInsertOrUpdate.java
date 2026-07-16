/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.jdbc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class MilvusInsertOrUpdate extends AbstractMilvusCollectionStatement {
	private String partitionName;
	private List<Map<String, MilvusTypedValue>> data = new ArrayList<>();

	public List<Map<String, MilvusTypedValue>> getData() {
		return data;
	}

	public void setData(List<Map<String, MilvusTypedValue>> data) {
		this.data = data;
	}

	public String getPartitionName() {
		return partitionName;
	}

	public void setPartitionName(String partitionName) {
		this.partitionName = partitionName;
	}

	public int parameterCount() {
		int count = 0;
		for ( Map<String, MilvusTypedValue> datum : data ) {
			for ( Map.Entry<String, MilvusTypedValue> entry : datum.entrySet() ) {
				if ( entry.getValue() instanceof MilvusParameterValue ) {
					count++;
				}
			}

		}
		return count;
	}
}
