/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.internal;

import org.hibernate.sql.ast.spi.SqlAstWalker;
import org.hibernate.sql.ast.spi.model.TableDeleteStandard;

/**
 * A {@link TableDeleteStandard} which is translated to a Milvus delete.
 * <p>
 * The core translator offers no extension point for the rendering of standard table deletes,
 * so this statement dispatches to {@link MilvusSqlAstTranslator} directly.
 */
public class MilvusTableDelete extends TableDeleteStandard {

	public MilvusTableDelete(TableDeleteStandard delete) {
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
		if ( walker instanceof MilvusSqlAstTranslator<?> translator ) {
			translator.renderStandardTableDelete( this );
		}
		else {
			super.accept( walker );
		}
	}
}
