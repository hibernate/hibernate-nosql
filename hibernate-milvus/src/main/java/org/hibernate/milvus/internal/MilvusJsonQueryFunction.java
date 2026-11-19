/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.internal;

import org.hibernate.QueryException;
import org.hibernate.dialect.function.json.JsonQueryFunction;
import org.hibernate.metamodel.model.domain.ReturnableType;
import org.hibernate.sql.ast.spi.translation.SqlAstTranslator;
import org.hibernate.sql.spi.SqlAppender;
import org.hibernate.sql.ast.spi.query.expression.JsonQueryEmptyBehavior;
import org.hibernate.sql.ast.spi.query.expression.JsonQueryErrorBehavior;
import org.hibernate.sql.ast.spi.query.expression.JsonQueryWrapMode;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * Milvus json_query function.
 */
public class MilvusJsonQueryFunction extends JsonQueryFunction {

	public MilvusJsonQueryFunction(TypeConfiguration typeConfiguration) {
		super( typeConfiguration, false, true );
	}

	@Override
	protected void render(
			SqlAppender sqlAppender,
			JsonQueryArguments arguments,
			ReturnableType<?> returnType,
			SqlAstTranslator<?> walker) {
		// Json dereference errors by default if the JSON is invalid
		if ( arguments.errorBehavior() != null && arguments.errorBehavior() != JsonQueryErrorBehavior.ERROR ) {
			throw new QueryException( "Can't emulate on error clause on Milvus" );
		}
		if ( arguments.emptyBehavior() != null && arguments.emptyBehavior() != JsonQueryEmptyBehavior.NULL ) {
			throw new QueryException( "Can't emulate on empty clause on Milvus" );
		}
		if ( arguments.wrapMode() != null && arguments.wrapMode() != JsonQueryWrapMode.WITHOUT_WRAPPER ) {
			throw new QueryException( "Can't emulate on wrap mode clause on Milvus" );
		}
		final String jsonPath;
		try {
			jsonPath = walker.getLiteralValue( arguments.jsonPath() );
		}
		catch (Exception ex) {
			throw new QueryException( "H2 json_value only support literal json paths, but got " + arguments.jsonPath() );
		}
		arguments.jsonDocument().accept( walker );
		MilvusJsonValueFunction.renderJsonPath(
				sqlAppender,
				walker,
				jsonPath,
				arguments.passingClause()
		);
	}
}
