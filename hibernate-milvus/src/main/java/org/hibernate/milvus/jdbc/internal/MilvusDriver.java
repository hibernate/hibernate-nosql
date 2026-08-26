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
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

public class MilvusDriver implements Driver {

	private static Driver registeredDriver;

	static {
		try {
			// moved the registerDriver from the constructor to here
			// because some clients call the driver themselves (I know, as
			// my early jdbc work did - and that was based on other examples).
			// Placing it here, means that the driver is registered once only.
			register();
		}
		catch (SQLException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

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
		Properties props = new Properties();
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
				else {
					props.setProperty( key, value );
				}
			}
		}
		props.putAll( info );

		String host = uri.getHost();
		int port = uri.getPort();
		if ( port == -1 ) {
			port = 19530;
		}
		ConnectConfig.ConnectConfigBuilder builder = ConnectConfig.builder()
				.uri( (secure == Boolean.TRUE ? "https" : "http") + "://" + host + ":" + port );
		if ( userName != null ) {
			builder.username( userName );
		}
		if ( password != null ) {
			builder.password( password );
		}
		ConnectConfig connectConfig = builder.dbName( database )
				.secure( secure == Boolean.TRUE )
				.build();
		return new MilvusConnection( new MilvusClientV2( connectConfig ), url, userName, props );
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

	public static void register() throws SQLException {
		if ( isRegistered() ) {
			throw new IllegalStateException(
					"Driver is already registered. It can only be registered once." );
		}
		MilvusDriver registeredDriver = new MilvusDriver();
		DriverManager.registerDriver( registeredDriver );
		MilvusDriver.registeredDriver = registeredDriver;
	}

	/**
	 * According to JDBC specification, this driver is registered against {@link DriverManager} when
	 * the class is loaded. To avoid leaks, this method allow unregistering the driver so that the
	 * class can be gc'ed if necessary.
	 *
	 * @throws IllegalStateException if the driver is not registered
	 * @throws SQLException if deregistering the driver fails
	 */
	public static void deregister() throws SQLException {
		if ( registeredDriver == null ) {
			throw new IllegalStateException(
					"Driver is not registered (or it has not been registered using MilvusDriver.register() method)" );
		}
		DriverManager.deregisterDriver( registeredDriver );
		registeredDriver = null;
	}

	/**
	 * @return {@code true} if the driver is registered against {@link DriverManager}
	 */
	public static boolean isRegistered() {
		return registeredDriver != null;
	}
}
