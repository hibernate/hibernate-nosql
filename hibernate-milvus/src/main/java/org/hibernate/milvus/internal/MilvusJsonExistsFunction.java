/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.internal;

import org.hibernate.QueryException;
import org.hibernate.dialect.function.json.JsonExistsFunction;
import org.hibernate.metamodel.model.domain.ReturnableType;
import org.hibernate.sql.ast.spi.translation.SqlAstTranslator;
import org.hibernate.sql.spi.SqlAppender;
import org.hibernate.sql.ast.spi.query.expression.JsonExistsErrorBehavior;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * json_exists function that uses the Milvus {@code json_contains} operator.
 */
public class MilvusJsonExistsFunction extends JsonExistsFunction {

	public MilvusJsonExistsFunction(TypeConfiguration typeConfiguration) {
		super( typeConfiguration, true, true );
	}

	@Override
	protected void render(
			SqlAppender sqlAppender,
			JsonExistsArguments arguments,
			ReturnableType<?> returnType,
			SqlAstTranslator<?> walker) {
		// Json dereference errors by default if the JSON is invalid
		if ( arguments.errorBehavior() != null && arguments.errorBehavior() != JsonExistsErrorBehavior.ERROR ) {
			throw new QueryException( "Can't emulate on error clause on Milvus" );
		}
		final String jsonPath;
		try {
			jsonPath = walker.getLiteralValue( arguments.jsonPath() );
		}
		catch (Exception ex) {
			throw new QueryException( "Milvus json_value only support literal json paths, but got " + arguments.jsonPath() );
		}
		arguments.jsonDocument().accept( walker );
		sqlAppender.appendSql( " is not null and " );
		arguments.jsonDocument().accept( walker );
		MilvusJsonValueFunction.renderJsonPath(
				sqlAppender,
				walker,
				jsonPath,
				arguments.passingClause()
		);
		sqlAppender.appendSql( " is not null" );
	}
}
