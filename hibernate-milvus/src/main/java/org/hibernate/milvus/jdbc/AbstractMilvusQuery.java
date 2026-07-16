/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.jdbc;

import io.milvus.v2.common.ConsistencyLevel;

import java.util.List;

public abstract class AbstractMilvusQuery extends AbstractMilvusFilterableStatement {
	private List<String> outputFields;
	private List<String> partitionNames;
	private ConsistencyLevel consistencyLevel;
	private long offset;
	private long limit;

	public AbstractMilvusQuery() {
	}

	protected AbstractMilvusQuery(AbstractMilvusQuery original) {
		super( original );
		this.outputFields = original.outputFields;
		this.partitionNames = original.partitionNames;
		this.consistencyLevel = original.consistencyLevel;
		this.offset = original.offset;
		this.limit = original.limit;
	}

	public List<String> getOutputFields() {
		return outputFields;
	}

	public void setOutputFields(List<String> outputFields) {
		this.outputFields = outputFields;
	}

	public List<String> getPartitionNames() {
		return partitionNames;
	}

	public void setPartitionNames(List<String> partitionNames) {
		this.partitionNames = partitionNames;
	}

	public ConsistencyLevel getConsistencyLevel() {
		return consistencyLevel;
	}

	public void setConsistencyLevel(ConsistencyLevel consistencyLevel) {
		this.consistencyLevel = consistencyLevel;
	}

	public long getOffset() {
		return offset;
	}

	public void setOffset(long offset) {
		this.offset = offset;
	}

	public long getLimit() {
		return limit;
	}

	public void setLimit(long limit) {
		this.limit = limit;
	}
}
