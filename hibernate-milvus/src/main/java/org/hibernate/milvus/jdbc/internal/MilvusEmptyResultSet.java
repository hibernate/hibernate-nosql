/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.jdbc.internal;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;

public class MilvusEmptyResultSet extends AbstractResultSet<MilvusStatement> {

	private ResultSetMetaData resultSetMetaData;

	public MilvusEmptyResultSet(MilvusStatement statement) {
		super(statement);
	}

	@Override
	protected int resultSize() {
		return 0;
	}

	@Override
	public void close() throws SQLException {
		resultSetMetaData = null;
		super.close();
	}

	@Override
	public ResultSetMetaData getMetaData() throws SQLException {
		checkClosed();
		if (resultSetMetaData == null) {
			resultSetMetaData = new MilvusResultSetMetaData();
		}
		return resultSetMetaData;
	}

	@Override
	public int findColumn(String columnLabel) throws SQLException {
		checkClosed();
		return 0;
	}

	@Override
	protected Object getValue(int columnIndex) throws SQLException {
		checkClosed();
		throw new SQLException( "Column index out of bounds: " + columnIndex );
	}

	@Override
	protected Object getValue(String columnLabel) throws SQLException {
		checkClosed();
		throw new SQLException("Column not found: " + columnLabel);
	}
}
