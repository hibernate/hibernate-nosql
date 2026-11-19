/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.neo4j.internal;

import org.hibernate.metamodel.mapping.SqlTypedMapping;
import org.hibernate.metamodel.model.domain.ReturnableType;
import org.hibernate.query.sqm.function.AbstractSqmSelfRenderingFunctionDescriptor;
import org.hibernate.query.sqm.function.FunctionKind;
import org.hibernate.query.sqm.produce.function.StandardArgumentsValidators;
import org.hibernate.query.sqm.produce.function.StandardFunctionReturnTypeResolvers;
import org.hibernate.sql.ast.spi.translation.SqlAstNodeRenderingMode;
import org.hibernate.sql.ast.spi.translation.SqlAstTranslator;
import org.hibernate.sql.spi.SqlAppender;
import org.hibernate.sql.ast.spi.SqlAstNode;
import org.hibernate.sql.ast.spi.query.expression.Expression;
import org.hibernate.type.BasicType;
import org.hibernate.vector.internal.VectorArgumentTypeResolver;
import org.hibernate.vector.internal.VectorArgumentValidator;

import java.util.List;

public class Neo4jVectorFunction extends AbstractSqmSelfRenderingFunctionDescriptor {

	private final String suffix;

	public Neo4jVectorFunction(String name, BasicType<?> returnType) {
		this( name, returnType, null );
	}

	public Neo4jVectorFunction(String name, BasicType<?> returnType, String suffix) {
		super(
				name,
				FunctionKind.NORMAL,
				StandardArgumentsValidators.composite(
						StandardArgumentsValidators.exactly( 1 ),
						VectorArgumentValidator.INSTANCE
				),
				StandardFunctionReturnTypeResolvers.invariant( returnType ),
				VectorArgumentTypeResolver.INSTANCE
		);
		this.suffix = suffix;
	}

	@Override
	public void render(
			SqlAppender sqlAppender,
			List<? extends SqlAstNode> sqlAstArguments,
			ReturnableType<?> returnType,
			SqlAstTranslator<?> walker) {
		final Expression vector1 = (Expression) sqlAstArguments.get( 0 );
		final SqlTypedMapping sqlTypedMapping;
		if ( vector1.getExpressionType() instanceof SqlTypedMapping typedMapping ) {
			sqlTypedMapping = typedMapping;
		}
		else {
			throw new IllegalArgumentException( "Unsupported vector argument type: [" + vector1.getExpressionType() + "]" );
		}
		final Neo4jVectorJdbcType vectorJdbcType = (Neo4jVectorJdbcType) sqlTypedMapping.getJdbcMapping().getJdbcType();
		final String vectorParameters = vectorJdbcType.getVectorParameters( sqlTypedMapping.toSize() );

		sqlAppender.appendSql( getName() );
		sqlAppender.appendSql( "(vector(" );
		walker.render( vector1, SqlAstNodeRenderingMode.DEFAULT );
		sqlAppender.appendSql( ',' );
		sqlAppender.appendSql( vectorParameters );
		sqlAppender.appendSql( ")" );
		if ( suffix != null ) {
			sqlAppender.appendSql( suffix );
		}
		sqlAppender.appendSql( ')' );
	}
}
