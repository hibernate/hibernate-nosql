/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.jdbc.internal;

import io.milvus.v2.common.DataType;
import org.hibernate.milvus.jdbc.MilvusHelper;
import org.hibernate.type.SqlTypes;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Map;

public final class MilvusArray implements Array {

	private final String baseTypeName;
	private final DataType baseDataType;
	private final Object array;

	public MilvusArray(String baseTypeName, Object array) {
		this.baseDataType = MilvusHelper.determineType( baseTypeName );
		this.baseTypeName = baseTypeName;
		this.array = array;
	}

	public DataType getBaseDataType() {
		return baseDataType;
	}

	@Override
	public String getBaseTypeName() throws SQLException {
		return baseTypeName;
	}

	@Override
	public int getBaseType() throws SQLException {
		return switch ( baseDataType ) {
			case VarChar -> SqlTypes.VARCHAR;
			case Int64 -> SqlTypes.BIGINT;
			case Int32 -> SqlTypes.INTEGER;
			case Int16 -> SqlTypes.SMALLINT;
			case Int8 -> SqlTypes.TINYINT;
			case Bool -> SqlTypes.BOOLEAN;
			case Double -> SqlTypes.DOUBLE;
			case Float -> SqlTypes.FLOAT;
			default -> SqlTypes.OTHER;
		};
	}

	@Override
	public Object getArray() throws SQLException {
		return array;
	}

	@Override
	public Object getArray(Map<String, Class<?>> map) throws SQLException {
		return array;
	}

	@Override
	public Object getArray(long index, int count) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public Object getArray(long index, int count, Map<String, Class<?>> map) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public ResultSet getResultSet() throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public ResultSet getResultSet(Map<String, Class<?>> map) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public ResultSet getResultSet(long index, int count) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public ResultSet getResultSet(long index, int count, Map<String, Class<?>> map) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void free() throws SQLException {
	}
}
