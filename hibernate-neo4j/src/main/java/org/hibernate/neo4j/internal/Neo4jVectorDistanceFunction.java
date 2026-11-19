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
import org.hibernate.type.spi.TypeConfiguration;
import org.hibernate.vector.internal.VectorArgumentTypeResolver;
import org.hibernate.vector.internal.VectorArgumentValidator;

import java.util.List;

public class Neo4jVectorDistanceFunction extends AbstractSqmSelfRenderingFunctionDescriptor {

	private final String metric;
	private final String suffix;

	public Neo4jVectorDistanceFunction(String name, TypeConfiguration typeConfiguration) {
		this( name, null, typeConfiguration );
	}

	public Neo4jVectorDistanceFunction(String name, String suffix, TypeConfiguration typeConfiguration) {
		super(
				name + "_distance",
				FunctionKind.NORMAL,
				StandardArgumentsValidators.composite(
						StandardArgumentsValidators.exactly( 2 ),
						VectorArgumentValidator.DISTANCE_INSTANCE
				),
				StandardFunctionReturnTypeResolvers.invariant(
						typeConfiguration.standardBasicTypeForJavaType( Double.class ) ),
				VectorArgumentTypeResolver.DISTANCE_INSTANCE
		);
		this.metric = name;
		this.suffix = suffix;
	}

	@Override
	public void render(
			SqlAppender sqlAppender,
			List<? extends SqlAstNode> sqlAstArguments,
			ReturnableType<?> returnType,
			SqlAstTranslator<?> walker) {
		final Expression vector1 = (Expression) sqlAstArguments.get( 0 );
		final Expression vector2 = (Expression) sqlAstArguments.get( 1 );
		final SqlTypedMapping sqlTypedMapping;
		if ( vector1.getExpressionType() instanceof SqlTypedMapping typedMapping ) {
			sqlTypedMapping = typedMapping;
		}
		else if ( vector2.getExpressionType() instanceof SqlTypedMapping typedMapping ) {
			sqlTypedMapping = typedMapping;
		}
		else {
			throw new IllegalArgumentException( "Unsupported vector argument typed: [" + vector1.getExpressionType() + ", " + vector2.getExpressionType() + "]" );
		}
		final Neo4jVectorJdbcType vectorJdbcType = (Neo4jVectorJdbcType) sqlTypedMapping.getJdbcMapping().getJdbcType();
		final String vectorParameters = vectorJdbcType.getVectorParameters( sqlTypedMapping.toSize() );

		sqlAppender.appendSql( "vector_distance(vector(" );
		walker.render( vector1, SqlAstNodeRenderingMode.DEFAULT );
		sqlAppender.appendSql( ',' );
		sqlAppender.appendSql( vectorParameters );
		sqlAppender.appendSql( "),vector(" );
		walker.render( vector2, SqlAstNodeRenderingMode.DEFAULT );
		sqlAppender.appendSql( ',' );
		sqlAppender.appendSql( vectorParameters );
		sqlAppender.appendSql( ")," );
		sqlAppender.appendSql( metric );
		sqlAppender.appendSql( ')' );
		if ( suffix != null ) {
			sqlAppender.appendSql( suffix );
		}
	}
}
