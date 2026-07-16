/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.neo4j;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.model.relational.SqlStringGenerationContext;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Table;
import org.hibernate.tool.schema.spi.Exporter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Neo4jTableExporter implements Exporter<Table> {
	public static final Neo4jTableExporter INSTANCE = new Neo4jTableExporter();

	@Override
	public String[] getSqlCreateStrings(Table table, Metadata metadata, SqlStringGenerationContext context) {
		// Neo4j does not have a concept of pre-defined schemas, but we can create
		// constraints on labels (which are similar to tables in relational databases)
		// and properties (which are similar to columns in relational databases).

		final String label = table.getQualifiedTableName().getTableName().toString();
		final Collection<Column> columns = table.getColumns();

		final List<String> statements = new ArrayList<>();

		if ( table.hasPrimaryKey() ) {
			// Create a unique constraint for the primary key column(s)
			// https://neo4j.com/docs/cypher-manual/current/constraints/managing-constraints/#create-property-uniqueness-constraints
			final List<Column> pkCols = table.getPrimaryKey().getColumns();
			final String uniqueConstraint = String.format( "create constraint %s_pk for (n:%s) require %s is unique",
					label,
					label,
					pkCols.size() == 1
							? "n." + pkCols.get(0).getName()
							: "(" + pkCols.stream().map( c -> "n." + c.getName() ).reduce( (a, b) -> a + ", " + b )
									.orElseThrow() + ")"
			);
			statements.add( uniqueConstraint );
		}

		// Neo4j Community Edition only supports identity constraints
		if ( ((Neo4jDialect) context.getDialect()).isEnterpriseEdition() ) {
			for ( Column column : columns ) {
				final String propertyName = column.getName();

				if ( !column.isNullable() ) {
					// Create property existence constraints for non-nullable columns
					// https://neo4j.com/docs/cypher-manual/current/constraints/managing-constraints/#create-property-existence-constraints
					statements.add( String.format(
							"create constraint %s_%s_non_null for (n:%s) require n.%s is not null",
							label,
							propertyName,
							label,
							propertyName
					) );
				}

				if ( context.getDialect().getVersion().isSameOrAfter( 5, 9 ) ) {
					// Create property type constraints
					// https://neo4j.com/docs/cypher-manual/current/constraints/managing-constraints/#create-property-type-constraints
					final String sqlType = column.getSqlType( metadata );
					if ( !sqlType.startsWith( "list" ) || context.getDialect().getVersion().isSameOrAfter( 5, 10 ) ) {
						statements.add( String.format(
								"create constraint %s_%s_type for (n:%s) require n.%s is :: %s",
								label,
								propertyName,
								label,
								propertyName,
								sqlType
						) );
					}
				}
			}
		}

		return statements.toArray( new String[0] );
	}

	@Override
	public String[] getSqlDropStrings(Table table, Metadata metadata, SqlStringGenerationContext context) {
		final String label = table.getQualifiedTableName().getTableName().toString();
		final List<String> statements = new ArrayList<>();

		if ( table.hasPrimaryKey() ) {
			statements.add( String.format(
					"drop constraint %s_pk if exists",
					table.getQualifiedTableName().getTableName().toString()
			) );
		}

		// Neo4j Community Edition only supports identity constraints
		if ( ((Neo4jDialect) context.getDialect()).isEnterpriseEdition() ) {
			for ( Column column : table.getColumns() ) {
				final String propertyName = column.getName();

				if ( !column.isNullable() ) {
					statements.add( String.format(
							"drop constraint %s_%s_non_null if exists",
							label,
							propertyName
					) );
				}

				if ( context.getDialect().getVersion().isSameOrAfter( 5, 9 ) ) {
					statements.add( String.format(
							"drop constraint %s_%s_type if exists",
							label,
							propertyName
					) );
				}
			}
		}

		return statements.toArray( new String[0] );
	}
}
