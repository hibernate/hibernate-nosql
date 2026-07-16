/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.neo4j;

import org.hibernate.dialect.DatabaseVersion;
import org.hibernate.dialect.SimpleDatabaseVersion;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.internal.util.StringHelper;

import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Integer.parseInt;

public class Neo4jServerConfiguration {
	private static final Pattern VERSION_PATTERN = Pattern.compile( "[\\d]+\\.[\\d]+\\.[\\d]+" );

	private boolean enterpriseEdition;
	private DatabaseVersion databaseVersion;

	public Neo4jServerConfiguration(boolean enterpriseEdition, DatabaseVersion databaseVersion) {
		this.enterpriseEdition = enterpriseEdition;
		this.databaseVersion = databaseVersion;
	}

	public static Neo4jServerConfiguration fromDialectResolutionInfo(DialectResolutionInfo info) {
		try {
			// Example: "Neo4j Kernel-community-2025.04.0"
			final String productName = info.getDatabaseMetadata().getDatabaseProductName();
			final String[] split = productName.split( "-" );
			assert split.length == 3;
			return new Neo4jServerConfiguration( "enterprise".equalsIgnoreCase( split[1] ), parseVersion( split[2] ) );
		}
		catch (SQLException e) {
			return new Neo4jServerConfiguration( false, null );
		}
	}

	private static DatabaseVersion parseVersion(String versionString) {
		DatabaseVersion databaseVersion = null;
		final Matcher matcher = VERSION_PATTERN.matcher( versionString );
		if ( matcher.matches() ) {
			final String[] versionParts = StringHelper.split( ".", versionString );
			// if we got to this point, there is at least a major version, so no need to check [].length > 0
			int majorVersion = parseInt( versionParts[0] );
			int minorVersion = versionParts.length > 1 ? parseInt( versionParts[1] ) : 0;
			int microVersion = versionParts.length > 2 ? parseInt( versionParts[2] ) : 0;
			databaseVersion = new SimpleDatabaseVersion( majorVersion, minorVersion, microVersion );
		}
		return databaseVersion;
	}

	public boolean isEnterpriseEdition() {
		return enterpriseEdition;
	}

	public DatabaseVersion getDatabaseVersion() {
		return databaseVersion;
	}
}
