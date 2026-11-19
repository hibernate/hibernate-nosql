/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.internal;

import org.hibernate.dialect.function.array.ArrayContainsUnnestFunction;
import org.hibernate.internal.log.DeprecationLogger;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.metamodel.mapping.JdbcMappingContainer;
import org.hibernate.metamodel.model.domain.ReturnableType;
import org.hibernate.sql.ast.spi.translation.SqlAstTranslator;
import org.hibernate.sql.spi.SqlAppender;
import org.hibernate.sql.ast.spi.SqlAstNode;
import org.hibernate.sql.ast.spi.query.expression.Expression;
import org.hibernate.type.BasicPluralType;
import org.hibernate.type.spi.TypeConfiguration;
import org.jboss.logging.Logger;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Locale;

/**
 * Special array includes implementation that uses the Milvus {@code array_contains} operator.
 */
public class MilvusArrayContainsFunction extends ArrayContainsUnnestFunction {

	private static final DeprecationLogger LOG = Logger.getMessageLogger( MethodHandles.lookup(), DeprecationLogger.class, MilvusArrayContainsFunction.class.getName(), Locale.ROOT );

	public MilvusArrayContainsFunction(boolean nullable, TypeConfiguration typeConfiguration) {
		super( nullable, typeConfiguration );
	}

	@Override
	public void render(
			SqlAppender sqlAppender,
			List<? extends SqlAstNode> sqlAstArguments,
			ReturnableType<?> returnType,
			SqlAstTranslator<?> walker) {
		final Expression haystackExpression = (Expression) sqlAstArguments.get( 0 );
		final Expression needleExpression = (Expression) sqlAstArguments.get( 1 );
		final JdbcMappingContainer needleTypeContainer = needleExpression.getExpressionType();
		final JdbcMapping needleType = needleTypeContainer == null ? null : needleTypeContainer.getSingleJdbcMapping();
		if ( needleType == null || needleType instanceof BasicPluralType<?, ?> ) {
			LOG.deprecatedArrayContainsWithArray();
			if ( !nullable ) {
				sqlAppender.append( "(not array_contains(" );
				needleExpression.accept( walker );
				sqlAppender.append( ",null)) and " );
			}
			sqlAppender.append( "array_contains_all(" );
			haystackExpression.accept( walker );
			sqlAppender.append( ',' );
			needleExpression.accept( walker );
			sqlAppender.append( ')' );
		}
		else {
			if ( !nullable ) {
				needleExpression.accept( walker );
				sqlAppender.append( " is not null and " );
			}
			sqlAppender.append( "array_contains(" );
			haystackExpression.accept( walker );
			sqlAppender.append( ',' );
			needleExpression.accept( walker );
			sqlAppender.append( ')' );
		}
	}

}
