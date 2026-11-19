/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.internal;

import org.hibernate.metamodel.model.domain.ReturnableType;
import org.hibernate.query.sqm.function.AbstractSqmSelfRenderingFunctionDescriptor;
import org.hibernate.query.sqm.function.FunctionKind;
import org.hibernate.query.sqm.produce.function.StandardFunctionReturnTypeResolvers;
import org.hibernate.sql.ast.spi.translation.SqlAstNodeRenderingMode;
import org.hibernate.sql.ast.spi.translation.SqlAstTranslator;
import org.hibernate.sql.spi.SqlAppender;
import org.hibernate.sql.ast.spi.SqlAstNode;
import org.hibernate.sql.ast.spi.query.expression.JsonNullBehavior;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.spi.TypeConfiguration;

import java.util.List;

/**
 * Milvus json_array function.
 */
public class MilvusJsonArrayFunction extends AbstractSqmSelfRenderingFunctionDescriptor {

	public MilvusJsonArrayFunction(TypeConfiguration typeConfiguration) {
		super(
				"json_array",
				FunctionKind.NORMAL,
				null,
				StandardFunctionReturnTypeResolvers.invariant(
						typeConfiguration.getBasicTypeRegistry().resolve( String.class, SqlTypes.JSON )
				),
				null
		);
	}

	@Override
	public void render(
			SqlAppender sqlAppender,
			List<? extends SqlAstNode> sqlAstArguments,
			ReturnableType<?> returnType,
			SqlAstTranslator<?> walker) {
		char separator = '[';
		if ( sqlAstArguments.isEmpty() ) {
			sqlAppender.appendSql( separator );
		}
		else {
			final SqlAstNode lastArgument = sqlAstArguments.get( sqlAstArguments.size() - 1 );
			final JsonNullBehavior nullBehavior;
			final int argumentsCount;
			if ( lastArgument instanceof JsonNullBehavior jsonNullBehavior ) {
				nullBehavior = jsonNullBehavior;
				argumentsCount = sqlAstArguments.size() - 1;
			}
			else {
				nullBehavior = JsonNullBehavior.ABSENT;
				argumentsCount = sqlAstArguments.size();
			}
			for ( int i = 0; i < argumentsCount; i++ ) {
				sqlAppender.appendSql( separator );
				renderValue( sqlAppender, sqlAstArguments.get( i ), walker );
				separator = ',';
			}
			if ( nullBehavior != JsonNullBehavior.NULL ) {
				throw new UnsupportedOperationException( "Can't emulate absent on null on Milvus" );
			}
		}
		sqlAppender.appendSql( ']' );
	}

	protected void renderValue(SqlAppender sqlAppender, SqlAstNode value, SqlAstTranslator<?> walker) {
		walker.render( value, SqlAstNodeRenderingMode.INLINE_ALL_PARAMETERS );
	}
}
