/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.jdbc.internal;

import io.milvus.v2.common.DataType;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hibernate.milvus.jdbc.MilvusHelper;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;

public class MilvusResultSetMetaData implements ResultSetMetaData {

	private final @Nullable String collectionName;
	private final List<CreateCollectionReq.FieldSchema> fields;

	public MilvusResultSetMetaData() {
		this(null, List.of());
	}

	public MilvusResultSetMetaData(@Nullable String collectionName, List<CreateCollectionReq.FieldSchema> fields) {
		this.collectionName = collectionName;
		this.fields = fields;
	}

	private void checkIndex(int column) throws SQLException {
		if ( column <= 0 || column > fields.size() ) {
			throw new SQLException( "Column index out of bounds: " + column );
		}
	}

	private CreateCollectionReq.FieldSchema getField(int column) throws SQLException {
		checkIndex(column);
		return fields.get( column - 1 );
	}

	@Override
	public int getColumnCount() throws SQLException {
		return fields.size();
	}

	@Override
	public boolean isAutoIncrement(int column) throws SQLException {
		return getField( column ).getAutoID() == Boolean.TRUE;
	}

	@Override
	public boolean isCaseSensitive(int column) throws SQLException {
		checkIndex( column );
		return true;
	}

	@Override
	public boolean isSearchable(int column) throws SQLException {
		checkIndex( column );
		return true;
	}

	@Override
	public boolean isCurrency(int column) throws SQLException {
		checkIndex( column );
		return false;
	}

	@Override
	public int isNullable(int column) throws SQLException {
		return getField( column ).getIsNullable()
				? ResultSetMetaData.columnNullable
				: ResultSetMetaData.columnNoNulls;
	}

	@Override
	public boolean isSigned(int column) throws SQLException {
		checkIndex( column );
		return true;
	}

	@Override
	public int getColumnDisplaySize(int column) throws SQLException {
		final CreateCollectionReq.FieldSchema field = getField( column );
		return switch ( field.getDataType() ) {
			case Array -> field.getDimension();
			case VarChar, String -> field.getMaxLength();
			case Bool -> 1;
			case Int8 -> 4;
			case Int16 -> 6;
			case Int32 -> 11;
			case Int64 -> 20;
			case Float -> 15;
			case Double -> 25;
			case Timestamptz -> 30;
			case None, Struct, Geometry, JSON, BinaryVector, Int8Vector, FloatVector, BFloat16Vector, Float16Vector, SparseFloatVector -> Integer.MAX_VALUE;
		};
	}

	@Override
	public String getColumnLabel(int column) throws SQLException {
		return getColumnName( column );
	}

	@Override
	public String getColumnName(int column) throws SQLException {
		return getField( column ).getName();
	}

	@Override
	public int getPrecision(int column) throws SQLException {
		final CreateCollectionReq.FieldSchema field = getField( column );
		final DataType dataType = field.getDataType() == DataType.Array ? field.getElementType() : field.getDataType();
		return switch ( dataType ) {
			case VarChar, String -> field.getMaxLength();
			case Bool -> 1;
			case Int8, BinaryVector, Int8Vector -> 3;
			case Int16 -> 5;
			case Int32 -> 10;
			case Int64 -> 19;
			case Float, FloatVector, BFloat16Vector, Float16Vector, SparseFloatVector -> 8;
			case Double -> 17;
			case Timestamptz -> 0;
			case None, Array, JSON, Struct, Geometry -> Integer.MAX_VALUE;
		};
	}

	@Override
	public int getScale(int column) throws SQLException {
		final CreateCollectionReq.FieldSchema field = getField( column );
		return switch ( field.getDataType() ) {
			case Float, FloatVector, BFloat16Vector, Float16Vector, SparseFloatVector -> 8;
			default -> 0;
		};
	}

	@Override
	public String getTableName(int column) throws SQLException {
		checkIndex( column );
		return collectionName;
	}

	@Override
	public String getSchemaName(int column) throws SQLException {
		checkIndex( column );
		return "";
	}

	@Override
	public String getCatalogName(int column) throws SQLException {
		checkIndex( column );
		return "";
	}

	@Override
	public int getColumnType(int column) throws SQLException {
		return MilvusHelper.toJdbcType( getField( column ).getDataType() );
	}

	@Override
	public String getColumnTypeName(int column) throws SQLException {
		final CreateCollectionReq.FieldSchema field = getField( column );
		return MilvusHelper.toSqlType( field.getDataType(), field.getElementType() );
	}

	@Override
	public String getColumnClassName(int column) throws SQLException {
		return MilvusHelper.toJavaType( getField( column ).getDataType() );
	}

	@Override
	public boolean isReadOnly(int column) throws SQLException {
		checkIndex( column );
		return false;
	}

	@Override
	public boolean isWritable(int column) throws SQLException {
		checkIndex( column );
		return false;
	}

	@Override
	public boolean isDefinitelyWritable(int column) throws SQLException {
		checkIndex( column );
		return false;
	}

	@Override
	public <T> T unwrap(Class<T> iface) throws SQLException {
		return iface.cast( this );
	}

	@Override
	public boolean isWrapperFor(Class<?> iface) throws SQLException {
		return iface.isInstance( this );
	}
}
