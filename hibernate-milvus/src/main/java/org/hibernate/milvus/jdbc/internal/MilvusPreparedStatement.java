/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.jdbc.internal;

import org.hibernate.milvus.jdbc.MilvusStatementDefinition;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Calendar;

public class MilvusPreparedStatement extends MilvusStatement implements PreparedStatement {

	private final MilvusStatementDefinition statement;
	private final Object[] params;

	public MilvusPreparedStatement(MilvusConnection connection, String sql) throws SQLException {
		super( connection );
		this.statement = statementDefinition( sql );
		this.params = new Object[statement.parameterCount()];
	}

	protected void checkIndex(int index) throws SQLException {
		if ( index <= 0 || index > params.length ) {
			throw new SQLException( "Parameter index out of bounds: " + index );
		}
	}

	@Override
	public ResultSet executeQuery() throws SQLException {
		return executeQuery( statement, params );
	}

	@Override
	public int executeUpdate() throws SQLException {
		return executeUpdate( statement, params );
	}

	@Override
	public void clearParameters() throws SQLException {
		Arrays.fill( params, null );
	}

	@Override
	public boolean execute() throws SQLException {
		return execute( statement, params );
	}

	@Override
	public void addBatch(String sql) throws SQLException {
		// Can't be supported
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void addBatch() throws SQLException {
		// todo (milvus): implement
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void clearBatch() throws SQLException {
		// todo (milvus): implement
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public int[] executeBatch() throws SQLException {
		// todo (milvus): implement
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public ParameterMetaData getParameterMetaData() throws SQLException {
		return null;
	}

	@Override
	public ResultSetMetaData getMetaData() throws SQLException {
		return executeQuery().getMetaData();
	}

	// todo (milvus): implement conversion

	@Override
	public void setNull(int parameterIndex, int sqlType) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = null;
	}

	@Override
	public void setBoolean(int parameterIndex, boolean x) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = x;
	}

	@Override
	public void setByte(int parameterIndex, byte x) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = x;
	}

	@Override
	public void setShort(int parameterIndex, short x) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = x;
	}

	@Override
	public void setInt(int parameterIndex, int x) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = x;
	}

	@Override
	public void setLong(int parameterIndex, long x) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = x;
	}

	@Override
	public void setFloat(int parameterIndex, float x) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = x;
	}

	@Override
	public void setDouble(int parameterIndex, double x) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = x;
	}

	@Override
	public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = x;
	}

	@Override
	public void setString(int parameterIndex, String x) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = x;
	}

	@Override
	public void setBytes(int parameterIndex, byte[] x) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = x;
	}

	@Override
	public void setDate(int parameterIndex, Date x) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = x;
	}

	@Override
	public void setTime(int parameterIndex, Time x) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = x;
	}

	@Override
	public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = x;
	}

	@Override
	public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = x;
	}

	@Override
	public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = x;
	}

	@Override
	public void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = x;
	}

	@Override
	public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = x;
	}

	@Override
	public void setObject(int parameterIndex, Object x) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = x;
	}

	@Override
	public void setCharacterStream(int parameterIndex, Reader reader, int length) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = reader;
	}

	@Override
	public void setRef(int parameterIndex, Ref x) throws SQLException {
		checkIndex( parameterIndex );
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void setBlob(int parameterIndex, Blob x) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = x;
	}

	@Override
	public void setClob(int parameterIndex, Clob x) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = x;
	}

	@Override
	public void setArray(int parameterIndex, Array x) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = x;
	}

	@Override
	public void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = x;
	}

	@Override
	public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = x;
	}

	@Override
	public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = x;
	}

	@Override
	public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = null;
	}

	@Override
	public void setURL(int parameterIndex, URL x) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = x;
	}

	@Override
	public void setRowId(int parameterIndex, RowId x) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = x;
	}

	@Override
	public void setNString(int parameterIndex, String value) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = value;
	}

	@Override
	public void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = value;
	}

	@Override
	public void setNClob(int parameterIndex, NClob value) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = value;
	}

	@Override
	public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = reader;
	}

	@Override
	public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = inputStream;
	}

	@Override
	public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = reader;
	}

	@Override
	public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = x;
	}

	@Override
	public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = x;
	}

	@Override
	public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = x;
	}

	@Override
	public void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = reader;
	}

	@Override
	public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = x;
	}

	@Override
	public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = x;
	}

	@Override
	public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = reader;
	}

	@Override
	public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = value;
	}

	@Override
	public void setClob(int parameterIndex, Reader reader) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = reader;
	}

	@Override
	public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = inputStream;
	}

	@Override
	public void setNClob(int parameterIndex, Reader reader) throws SQLException {
		checkIndex( parameterIndex );
		params[parameterIndex - 1] = reader;
	}
}
