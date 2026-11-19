/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.internal;

import org.hibernate.dialect.function.array.AbstractArrayGetFunction;
import org.hibernate.metamodel.model.domain.ReturnableType;
import org.hibernate.sql.ast.spi.translation.SqlAstTranslator;
import org.hibernate.sql.spi.SqlAppender;
import org.hibernate.sql.ast.spi.SqlAstNode;
import org.hibernate.sql.ast.spi.query.expression.Expression;

import java.util.List;

/**
 * Implement the array get function by using {@code []} (bracket) syntax.
 */
public class MilvusArrayGetFunction extends AbstractArrayGetFunction {

	@Override
	public void render(
			SqlAppender sqlAppender,
			List<? extends SqlAstNode> sqlAstArguments,
			ReturnableType<?> returnType,
			SqlAstTranslator<?> walker) {
		final Expression arrayExpression = (Expression) sqlAstArguments.get( 0 );
		final Expression indexExpression = (Expression) sqlAstArguments.get( 1 );
		arrayExpression.accept( walker );
		sqlAppender.append( '[' );
		indexExpression.accept( walker );
		// Milvus arrays have 0-based indexed, so we have to adapt the 1-based array_get index
		sqlAppender.append( "-1" );
		sqlAppender.append( ']' );
	}
}
