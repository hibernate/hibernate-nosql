/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.internal;

import org.hibernate.dialect.function.array.ArrayIntersectsUnnestFunction;
import org.hibernate.metamodel.model.domain.ReturnableType;
import org.hibernate.sql.ast.spi.translation.SqlAstTranslator;
import org.hibernate.sql.spi.SqlAppender;
import org.hibernate.sql.ast.spi.SqlAstNode;
import org.hibernate.sql.ast.spi.query.expression.Expression;
import org.hibernate.type.spi.TypeConfiguration;

import java.util.List;

/**
 * Array intersects function that uses the Milvus {@code array_contains_any} operator.
 */
public class MilvusArrayIntersectsFunction extends ArrayIntersectsUnnestFunction {

	public MilvusArrayIntersectsFunction(boolean nullable, TypeConfiguration typeConfiguration) {
		super( nullable, typeConfiguration );
	}

	@Override
	public void render(
			SqlAppender sqlAppender,
			List<? extends SqlAstNode> sqlAstArguments,
			ReturnableType<?> returnType, SqlAstTranslator<?> walker) {
		final Expression haystackExpression = (Expression) sqlAstArguments.get( 0 );
		final Expression needleExpression = (Expression) sqlAstArguments.get( 1 );
		if ( !nullable ) {
			sqlAppender.append( "(not array_contains(" );
			needleExpression.accept( walker );
			sqlAppender.append( ",null)) and " );
		}
		sqlAppender.append( "array_contains_any(" );
		haystackExpression.accept( walker );
		sqlAppender.append( ',' );
		needleExpression.accept( walker );
		sqlAppender.append( ')' );
	}
}
