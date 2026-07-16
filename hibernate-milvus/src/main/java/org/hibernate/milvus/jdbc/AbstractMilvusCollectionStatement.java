/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.jdbc;

public abstract class AbstractMilvusCollectionStatement {
	private String collectionName;

	public AbstractMilvusCollectionStatement() {
	}

	public AbstractMilvusCollectionStatement(AbstractMilvusCollectionStatement original) {
		this.collectionName = original.getCollectionName();
	}

	public String getCollectionName() {
		return collectionName;
	}

	public void setCollectionName(String collectionName) {
		this.collectionName = collectionName;
	}

}
