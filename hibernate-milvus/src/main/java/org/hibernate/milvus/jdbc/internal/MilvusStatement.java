/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.jdbc.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.milvus.v2.service.vector.response.DeleteResp;
import io.milvus.v2.service.vector.response.InsertResp;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;
import io.milvus.v2.service.vector.response.UpsertResp;
import org.hibernate.milvus.jdbc.MilvusCreateCollection;
import org.hibernate.milvus.jdbc.MilvusDelete;
import org.hibernate.milvus.jdbc.MilvusDropCollection;
import org.hibernate.milvus.jdbc.MilvusHybridSearch;
import org.hibernate.milvus.jdbc.MilvusInsert;
import org.hibernate.milvus.jdbc.MilvusJsonHelper;
import org.hibernate.milvus.jdbc.MilvusQuery;
import org.hibernate.milvus.jdbc.MilvusSearch;
import org.hibernate.milvus.jdbc.MilvusStatementDefinition;
import org.hibernate.milvus.jdbc.MilvusUpsert;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.List;

public class MilvusStatement implements Statement {

	private static final Object[] NO_ARGS = new Object[0];

	final MilvusConnection connection;

	boolean closed;
	ResultSet resultSet;
	ResultSet generatedResultSet;
	int updateCount = -1;

	public MilvusStatement(MilvusConnection connection) {
		this.connection = connection;
	}

	MilvusStatementDefinition statementDefinition(String sql) throws SQLException {
		checkClosed();
		resultSet = null;
		generatedResultSet = null;
		updateCount = -1;
		try {
			return MilvusJsonHelper.parseDefinition( sql );
		}
		catch (JsonProcessingException e) {
			throw new SQLException( "Invalid statement", e );
		}
	}

	void checkClosed() throws SQLException {
		if ( closed ) {
			throw new SQLException( "Statement has been closed" );
		}
	}

	@Override
	public ResultSet executeQuery(String sql) throws SQLException {
		MilvusStatementDefinition statement = statementDefinition( sql );
		return executeQuery( statement, NO_ARGS );
	}

	protected ResultSet executeQuery(MilvusStatementDefinition statement, Object[] params) throws SQLException {
		if ( statement instanceof MilvusQuery query ) {
			QueryResp queryResp = connection.executeQuery( query, params );
			return resultSet = new MilvusQueryResultSet( this, queryResp, query.getCollectionName(), query.getOutputFields() );
		}
		else if ( statement instanceof MilvusSearch search ) {
			SearchResp queryResp = connection.executeSearch( search, params );
			return resultSet = new MilvusSearchResultSet( this, queryResp, search.getCollectionName(), search.getOutputFields() );
		}
		else if ( statement instanceof MilvusHybridSearch search ) {
			SearchResp queryResp = connection.executeHybridSearch( search, params );
			return resultSet = new MilvusSearchResultSet( this, queryResp, search.getCollectionName(), search.getOutputFields() );
		}
		else {
			throw new SQLException( "Invalid statement type, expected query but got: " + statement.getClass().getName() );
		}
	}

	@Override
	public int executeUpdate(String sql) throws SQLException {
		MilvusStatementDefinition statement = statementDefinition( sql );
		return executeUpdate( statement, NO_ARGS );
	}

	int executeUpdate(MilvusStatementDefinition statement, Object[] parameterValues) throws SQLException {
		if ( statement instanceof MilvusCreateCollection createCollection ) {
			connection.createCollection( createCollection );
			updateCount = 1;
		}
		else if ( statement instanceof MilvusDropCollection dropCollection ) {
			connection.dropCollection( dropCollection );
			updateCount = 1;
		}
		else if ( statement instanceof MilvusInsert insert ) {
			InsertResp insertResp = connection.executeInsert( insert, parameterValues );
			updateCount = (int) insertResp.getInsertCnt();
			List<Object> primaryKeys = insertResp.getPrimaryKeys();
			generatedResultSet = new MilvusGeneratedKeysResultSet( this, insert.getCollectionName(), primaryKeys );
		}
		else if ( statement instanceof MilvusUpsert upsert ) {
			UpsertResp upsertResp = connection.executeUpsert( upsert, parameterValues );
			updateCount = (int) upsertResp.getUpsertCnt();
		}
		else if ( statement instanceof MilvusDelete delete ) {
			DeleteResp deleteResp = connection.executeDelete( delete, parameterValues );
			updateCount = (int) deleteResp.getDeleteCnt();
		}
		else {
			throw new SQLException( "Unsupported statement type for executeUpdate: " + statement.getClass().getName() );
		}
		return updateCount;
	}

	boolean execute(MilvusStatementDefinition statement, Object[] parameterValues) throws SQLException {
		if ( statement instanceof MilvusQuery query ) {
			executeQuery( query, parameterValues );
			return true;
		}
		else if ( statement instanceof MilvusSearch search ) {
			executeQuery( search, parameterValues );
			return true;
		}
		else {
			executeUpdate( statement, parameterValues );
			return false;
		}
	}

	@Override
	public ResultSet getGeneratedKeys() throws SQLException {
		checkClosed();
		if ( generatedResultSet == null ) {
			generatedResultSet = new MilvusEmptyResultSet( this );
		}
		return generatedResultSet;
	}

	@Override
	public boolean execute(String sql) throws SQLException {
		MilvusStatementDefinition statement = statementDefinition(sql);
		return execute( statement, NO_ARGS );
	}

	@Override
	public ResultSet getResultSet() throws SQLException {
		checkClosed();
		return resultSet;
	}

	@Override
	public int getUpdateCount() throws SQLException {
		checkClosed();
		return updateCount;
	}

	@Override
	public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
		return executeUpdate( sql );
	}

	@Override
	public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
		return executeUpdate( sql );
	}

	@Override
	public int executeUpdate(String sql, String[] columnNames) throws SQLException {
		return executeUpdate( sql );
	}

	@Override
	public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
		return execute( sql );
	}

	@Override
	public boolean execute(String sql, int[] columnIndexes) throws SQLException {
		return execute( sql );
	}

	@Override
	public boolean execute(String sql, String[] columnNames) throws SQLException {
		return execute( sql );
	}

	@Override
	public void close() throws SQLException {
		closed = true;
	}

	@Override
	public int getMaxFieldSize() throws SQLException {
		checkClosed();
		return 0;
	}

	@Override
	public void setMaxFieldSize(int max) throws SQLException {
		checkClosed();
	}

	@Override
	public int getMaxRows() throws SQLException {
		checkClosed();
		return 0;
	}

	@Override
	public void setMaxRows(int max) throws SQLException {
		checkClosed();
	}

	@Override
	public void setEscapeProcessing(boolean enable) throws SQLException {
		checkClosed();
	}

	@Override
	public int getQueryTimeout() throws SQLException {
		checkClosed();
		return 0;
	}

	@Override
	public void setQueryTimeout(int seconds) throws SQLException {
		checkClosed();
	}

	@Override
	public void cancel() throws SQLException {
		checkClosed();
	}

	@Override
	public SQLWarning getWarnings() throws SQLException {
		checkClosed();
		return null;
	}

	@Override
	public void clearWarnings() throws SQLException {
		checkClosed();
	}

	@Override
	public void setCursorName(String name) throws SQLException {
		checkClosed();
	}

	@Override
	public boolean getMoreResults() throws SQLException {
		checkClosed();
		return false;
	}

	@Override
	public void setFetchDirection(int direction) throws SQLException {
		checkClosed();
	}

	@Override
	public int getFetchDirection() throws SQLException {
		checkClosed();
		return 0;
	}

	@Override
	public void setFetchSize(int rows) throws SQLException {
		checkClosed();
	}

	@Override
	public int getFetchSize() throws SQLException {
		checkClosed();
		return 0;
	}

	@Override
	public int getResultSetConcurrency() throws SQLException {
		return ResultSet.CONCUR_READ_ONLY;
	}

	@Override
	public int getResultSetType() throws SQLException {
		return ResultSet.TYPE_FORWARD_ONLY;
	}

	@Override
	public void addBatch(String sql) throws SQLException {
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
	public Connection getConnection() throws SQLException {
		return connection;
	}

	@Override
	public boolean getMoreResults(int current) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public int getResultSetHoldability() throws SQLException {
		return ResultSet.HOLD_CURSORS_OVER_COMMIT;
	}

	@Override
	public boolean isClosed() throws SQLException {
		return closed;
	}

	@Override
	public void setPoolable(boolean poolable) throws SQLException {
		checkClosed();
	}

	@Override
	public boolean isPoolable() throws SQLException {
		checkClosed();
		return false;
	}

	@Override
	public void closeOnCompletion() throws SQLException {
		checkClosed();
	}

	@Override
	public boolean isCloseOnCompletion() throws SQLException {
		checkClosed();
		return false;
	}

	@Override
	public <T> T unwrap(Class<T> iface) throws SQLException {
		return iface.cast(this);
	}

	@Override
	public boolean isWrapperFor(Class<?> iface) throws SQLException {
		return iface.isInstance(this);
	}
}
