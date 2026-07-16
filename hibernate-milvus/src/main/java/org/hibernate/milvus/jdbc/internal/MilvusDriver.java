/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.jdbc.internal;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;

import java.net.URI;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

public class MilvusDriver implements Driver {

	@Override
	public Connection connect(String url, Properties info) throws SQLException {
		if (!acceptsURL(url)) {
			throw new SQLException("Not a valid URL: " + url);
		}
		URI uri = URI.create( url.substring( "jdbc:".length() ) );
		String userName = info.getProperty("user");
		String password = info.getProperty("password");
		String database = info.getProperty("database");
		String secureProperty = info.getProperty( "secure" );
		Boolean secure = secureProperty == null ? null : Boolean.parseBoolean( secureProperty );
		if ( database == null || database.isEmpty() ) {
			database = uri.getPath();
			if ( database != null && database.startsWith( "/" ) ) {
				database = database.substring( 1 );
			}
		}
		String query = uri.getQuery();
		if ( query != null && !query.isEmpty() ) {
			String[] queryParts = query.split( "&" );
			for ( int i = 0; i < queryParts.length; i++ ) {
				String[] keyValue = queryParts[i].split( "=" );
				if ( keyValue.length != 2 ) {
					throw new SQLException( "Malformed query: " + uri );
				}
				String key = keyValue[0];
				String value = keyValue[1];
				if ( key.equals( "secure" ) ) {
					secure = Boolean.parseBoolean( value );
				}
			}
		}
		String host = uri.getHost();
		int port = uri.getPort();
		if ( port == -1 ) {
			port = 19530;
		}

		ConnectConfig connectConfig = ConnectConfig.builder()
				.uri( (secure == Boolean.TRUE ? "https" : "http" ) + "://" + host + ":" + port )
				.username( userName )
				.password( password )
				.dbName( database )
				.secure( secure == Boolean.TRUE )
				.build();
		return new MilvusConnection( new MilvusClientV2( connectConfig ), url, userName );
	}

	@Override
	public boolean acceptsURL(String url) throws SQLException {
		return url.startsWith( "jdbc:milvus" );
	}

	@Override
	public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
		return new DriverPropertyInfo[0];
	}

	@Override
	public int getMajorVersion() {
		return 0;
	}

	@Override
	public int getMinorVersion() {
		return 0;
	}

	@Override
	public boolean jdbcCompliant() {
		return false;
	}

	@Override
	public Logger getParentLogger() throws SQLFeatureNotSupportedException {
		return null;
	}
}
