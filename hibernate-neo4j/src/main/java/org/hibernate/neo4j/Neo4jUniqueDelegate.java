/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.neo4j;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.model.relational.SqlStringGenerationContext;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.unique.AlterTableUniqueDelegate;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.UniqueKey;

import java.util.List;

public class Neo4jUniqueDelegate extends AlterTableUniqueDelegate {
	/**
	 * @param dialect The dialect for which we are handling unique constraints
	 */
	public Neo4jUniqueDelegate(Dialect dialect) {
		super( dialect );
	}

	@Override
	public String getAlterTableToAddUniqueKeyCommand(UniqueKey uniqueKey, Metadata metadata, SqlStringGenerationContext context) {
		final String tableName = context.format( uniqueKey.getTable().getQualifiedTableName() );
		final String constraintName = dialect.quote( uniqueKey.getName() );
		return "create constraint " + constraintName + " for (n:" + tableName + ") require " + uniqueConstraintSql( uniqueKey );
	}

	@Override
	protected String uniqueConstraintSql(UniqueKey uniqueKey) {
		final List<Column> columns = uniqueKey.getColumns();
		final StringBuilder fragment = new StringBuilder();
		if ( columns.size() > 1 ) {
			fragment.append( "(" );
		}
		String separator = "";
		for ( Column column : columns ) {
			fragment.append( separator ).append( "n." ).append( column.getQuotedName( dialect ) );
			separator = ", ";
		}
		if ( columns.size() > 1 ) {
			fragment.append( ')' );
		}
		fragment.append( " is unique" );
		return fragment.toString();
	}

	@Override
	public String getAlterTableToDropUniqueKeyCommand(UniqueKey uniqueKey, Metadata metadata, SqlStringGenerationContext context) {
		return "drop constraint " + dialect.quote( uniqueKey.getName() ) + " if exists";
	}
}
