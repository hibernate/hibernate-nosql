/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.neo4j;

import jakarta.annotation.Nullable;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.tool.schema.extract.spi.ColumnTypeInformation;
import org.hibernate.type.BasicType;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.AggregateJdbcType;
import org.hibernate.type.descriptor.jdbc.ArrayJdbcType;
import org.hibernate.type.descriptor.jdbc.BasicExtractor;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.JdbcTypeConstructor;
import org.hibernate.type.spi.TypeConfiguration;

import java.sql.Array;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Locale;

public class Neo4jArrayJdbcType extends ArrayJdbcType {
	public Neo4jArrayJdbcType(JdbcType elementJdbcType) {
		super( elementJdbcType );
	}

	@Override
	protected String getElementTypeName(JavaType<?> javaType, SharedSessionContractImplementor session) {
		// Neo4j's jdbc driver expects the element type name to be in uppercase
		// see https://github.com/neo4j/neo4j-jdbc/blob/b0f5eabdbde60c5f03695f02f9df4a8f21beb95f/neo4j-jdbc/src/main/java/org/neo4j/jdbc/ArrayImpl.java#L64
		return super.getElementTypeName( javaType, session ).toUpperCase( Locale.ROOT );
	}

	@Override
	protected <X> X getArray(BasicExtractor<X> extractor, Array array, WrapperOptions options) throws SQLException {
		final var javaType = extractor.getJavaType();
		return array != null
			&& getElementJdbcType() instanceof AggregateJdbcType aggregateJdbcType
			&& aggregateJdbcType.getEmbeddableMappingType() != null
				? super.getArray( extractor, array, options )
				: javaType.wrap( toJavaArray( array ), options );
	}

	private static Object toJavaArray(@Nullable java.sql.Array array) throws SQLException {
		// The Neo4j driver seems to produce a long[] for integral arrays, regardless of the type of the written value,
		// so we try to do some coercion in here
		if ( array == null ) {
			return null;
		}
		final Object javaArray = array.getArray();
		if ( javaArray instanceof long[] longs ) {
			final Object[] objects = new Object[longs.length];
			for ( int i = 0; i < longs.length; i++ ) {
				objects[i] = longs[i];
			}
			return objects;
		}
		else {
			return javaArray;
		}
	}


	public static class Neo4jArrayJdbcTypeConstructor implements JdbcTypeConstructor {
		public static final Neo4jArrayJdbcTypeConstructor INSTANCE = new Neo4jArrayJdbcTypeConstructor();

		public JdbcType resolveType(
				TypeConfiguration typeConfiguration,
				Dialect dialect,
				BasicType<?> elementType,
				ColumnTypeInformation columnTypeInformation) {
			return resolveType( typeConfiguration, dialect, elementType.getJdbcType(), columnTypeInformation );
		}

		public JdbcType resolveType(
				TypeConfiguration typeConfiguration,
				Dialect dialect,
				JdbcType elementType,
				ColumnTypeInformation columnTypeInformation) {
			return new Neo4jArrayJdbcType( elementType );
		}

		@Override
		public int getDefaultSqlTypeCode() {
			return Types.ARRAY;
		}
	}
}
