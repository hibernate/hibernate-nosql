/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.neo4j.internal;

import org.hibernate.sql.ast.spi.SqlAstWalker;
import org.hibernate.sql.ast.spi.model.TableDeleteStandard;

/**
 * A {@link TableDeleteStandard} which is rendered as a Cypher statement.
 * <p>
 * The core translator offers no extension point for the rendering of standard table deletes,
 * so this statement dispatches to {@link Neo4jSqlAstTranslator} directly.
 */
public class Neo4jTableDelete extends TableDeleteStandard {

	public Neo4jTableDelete(TableDeleteStandard delete) {
		super(
				delete.getMutatingTable(),
				delete.getMutationTarget(),
				delete.getMutationComment(),
				delete.getKeyBindings(),
				delete.getOptimisticLockBindings(),
				delete.getParameters(),
				delete.getWhereFragment()
		);
	}

	@Override
	public void accept(SqlAstWalker walker) {
		if ( walker instanceof Neo4jSqlAstTranslator<?> translator ) {
			translator.renderStandardTableDelete( this );
		}
		else {
			super.accept( walker );
		}
	}
}
