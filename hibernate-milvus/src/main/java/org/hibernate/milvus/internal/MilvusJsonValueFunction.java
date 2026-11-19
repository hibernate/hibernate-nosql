/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.internal;

import jakarta.annotation.Nullable;
import org.hibernate.QueryException;
import org.hibernate.dialect.function.json.JsonPathHelper;
import org.hibernate.dialect.function.json.JsonValueFunction;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.metamodel.model.domain.ReturnableType;
import org.hibernate.sql.ast.spi.translation.SqlAstTranslator;
import org.hibernate.sql.spi.SqlAppender;
import org.hibernate.sql.ast.spi.query.expression.Expression;
import org.hibernate.sql.ast.spi.query.expression.JsonPathPassingClause;
import org.hibernate.sql.ast.spi.query.expression.JsonValueEmptyBehavior;
import org.hibernate.sql.ast.spi.query.expression.JsonValueErrorBehavior;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.spi.TypeConfiguration;

import java.util.List;

import static org.hibernate.milvus.internal.QuotingHelper.appendJsonString;


/**
 * Milvus json_value function.
 */
public class MilvusJsonValueFunction extends JsonValueFunction {

	public MilvusJsonValueFunction(TypeConfiguration typeConfiguration) {
		super( typeConfiguration, false, false );
	}

	@Override
	protected void render(
			SqlAppender sqlAppender,
			JsonValueArguments arguments,
			ReturnableType<?> returnType,
			SqlAstTranslator<?> walker) {
		// Json dereference errors by default if the JSON is invalid
		if ( arguments.errorBehavior() != null && arguments.errorBehavior() != JsonValueErrorBehavior.ERROR ) {
			throw new QueryException( "Can't emulate on error clause on Milvus" );
		}
		if ( arguments.emptyBehavior() == JsonValueEmptyBehavior.ERROR ) {
			throw new QueryException( "Can't emulate error on empty clause on Milvus" );
		}
		if ( arguments.emptyBehavior() != null && arguments.emptyBehavior().getDefaultExpression() != null ) {
			throw new QueryException( "Can't emulate default on empty clause on Milvus" );
		}
		if ( arguments.returningType() != null && !isSupportedType( arguments.returningType().getJdbcMapping() ) ) {
			throw new QueryException( "Returning clause is only supported for number, string and boolean types on Milvus" );
		}
		final String jsonPath;
		try {
			jsonPath = walker.getLiteralValue( arguments.jsonPath() );
		}
		catch (Exception ex) {
			throw new QueryException( "H2 json_value only support literal json paths, but got " + arguments.jsonPath() );
		}

		arguments.jsonDocument().accept( walker );
		renderJsonPath(
				sqlAppender,
				walker,
				jsonPath,
				arguments.passingClause()
		);
	}

	public static boolean isSupportedType(JdbcMapping jdbcMapping) {
		final JdbcType jdbcType = jdbcMapping.getJdbcType();
		return jdbcType.isBoolean() || jdbcType.isStringLike() || jdbcType.isNumber();
	}

	public static void renderJsonPath(
			SqlAppender sqlAppender,
			SqlAstTranslator<?> walker,
			String jsonPath,
			@Nullable JsonPathPassingClause passingClause) {
		if ( "$".equals( jsonPath ) ) {
			return;
		}
		final List<JsonPathHelper.JsonPathElement> jsonPathElements = JsonPathHelper.parseJsonPathElements( jsonPath );
		for ( int i = 0; i < jsonPathElements.size(); i++ ) {
			final JsonPathHelper.JsonPathElement jsonPathElement = jsonPathElements.get( i );
			if ( jsonPathElement instanceof JsonPathHelper.JsonAttribute attribute ) {
				sqlAppender.appendSql( '[' );
				appendJsonString( sqlAppender, attribute.attribute() );
				sqlAppender.appendSql( ']' );
			}
			else if ( jsonPathElement instanceof JsonPathHelper.JsonParameterIndexAccess parameterIndexAccess ) {
				assert passingClause != null;
				final String parameterName = parameterIndexAccess.parameterName();
				final Expression expression = passingClause.getPassingExpressions().get( parameterName );
				if ( expression == null ) {
					throw new QueryException( "JSON path [" + jsonPath + "] uses parameter [" + parameterName + "] that is not passed" );
				}

				sqlAppender.appendSql( '[' );
				expression.accept( walker );
				sqlAppender.appendSql( "+1]" );
			}
			else {
				sqlAppender.appendSql( '[' );
				sqlAppender.appendSql( ( (JsonPathHelper.JsonIndexAccess) jsonPathElement ).index() );
				sqlAppender.appendSql( ']' );
			}
		}
	}
}
