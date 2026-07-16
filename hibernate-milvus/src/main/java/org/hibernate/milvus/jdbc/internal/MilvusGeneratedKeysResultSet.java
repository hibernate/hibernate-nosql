/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.jdbc.internal;

import io.milvus.v2.common.DataType;
import io.milvus.v2.service.collection.request.CreateCollectionReq;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;

public class MilvusGeneratedKeysResultSet extends AbstractResultSet<MilvusStatement> {

	private static final List<CreateCollectionReq.FieldSchema> COLUMN_NAMES = List.of(
			CreateCollectionReq.FieldSchema.builder()
					.name( "C0" )
					.isPrimaryKey( true )
					.autoID( true )
					.dataType( DataType.Int64 )
					.isNullable( false )
					.build()
	);
	private final String collectionName;
	private final List<Object> generatedKeys;

	private MilvusResultSetMetaData resultSetMetaData;

	public MilvusGeneratedKeysResultSet(MilvusStatement statement, String collectionName, List<Object> generatedKeys) {
		super(statement);
		this.collectionName = collectionName;
		this.generatedKeys = generatedKeys;
	}

	@Override
	protected int resultSize() {
		return generatedKeys.size();
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
			resultSetMetaData = new MilvusResultSetMetaData( collectionName, COLUMN_NAMES );
		}
		return resultSetMetaData;
	}

	@Override
	public int findColumn(String columnLabel) throws SQLException {
		checkClosed();
		return COLUMN_NAMES.indexOf( columnLabel ) + 1;
	}

	@Override
	protected Object getValue(int columnIndex) throws SQLException {
		checkIndex( columnIndex, 1 );
		wasNull = false;
		return generatedKeys.get( position );
	}

	@Override
	protected Object getValue(String columnLabel) throws SQLException {
		checkClosed();
		wasNull = false;
		if ( !COLUMN_NAMES.get( 0 ).equals( columnLabel ) ) {
			throw new SQLException("Column not found: " + columnLabel);
		}
		return generatedKeys.get( position );
	}
}
