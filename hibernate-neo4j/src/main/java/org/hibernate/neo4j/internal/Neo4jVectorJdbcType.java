/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.neo4j.internal;

import jakarta.annotation.Nullable;
import org.hibernate.HibernateException;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.Size;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.sql.spi.SqlAppender;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.BasicPluralJavaType;
import org.hibernate.type.descriptor.java.ByteJavaType;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.java.PrimitiveByteArrayJavaType;
import org.hibernate.type.descriptor.jdbc.AggregateJdbcType;
import org.hibernate.type.descriptor.jdbc.BasicBinder;
import org.hibernate.type.descriptor.jdbc.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

public class Neo4jVectorJdbcType extends Neo4jArrayJdbcType {

	private final boolean nativeSupport;
	private final int sqlType;

	public Neo4jVectorJdbcType(JdbcType elementJdbcType, boolean nativeSupport, int sqlType) {
		super( elementJdbcType );
		this.nativeSupport = nativeSupport;
		this.sqlType = sqlType;
	}

	@Override
	public int getDefaultSqlTypeCode() {
		return sqlType;
	}

	@Override
	public @Nullable String castFromPattern(JdbcMapping sourceMapping, @Nullable Size size) {
		return sourceMapping.getJdbcType().isStringLike() ? "vector(?1," + getVectorParameters( size ) + ")" : null;
	}

	@Override
	public @Nullable String castToPattern(JdbcMapping targetJdbcMapping, @Nullable Size size) {
		return targetJdbcMapping.getJdbcType().isStringLike() ? "replace(replace(replace(apoc.convert.toYaml(?1),'---\\n- ','['),'\\n- ',','),'\\n',']')" : null;
	}

	@Override
	public void appendWriteExpression(
			String writeExpression,
			@Nullable Size size,
			SqlAppender appender,
			Dialect dialect) {
		if ( nativeSupport ) {
			appender.append( "vector(" );
			appender.append( writeExpression );
			appender.append( ',' );
			appender.append( getVectorParameters( size ) );
			appender.append( ')' );
		}
		else {
			appender.append( writeExpression );
		}
	}

	String getVectorParameters(@Nullable Size size) {
		assert size != null;
		final int length = size.getArrayLength() != null ? size.getArrayLength() : size.getLength() != null ? size.getLength().intValue() : 0;
		return length + "," + switch ( getElementJdbcType().getDefaultSqlTypeCode() ) {
			case Types.TINYINT -> "integer8";
			case Types.SMALLINT -> "integer16";
			case Types.INTEGER -> "integer32";
			case Types.BIGINT -> "integer64";
			case Types.REAL, Types.FLOAT -> "float32";
			case Types.DOUBLE -> "float64";
			default -> throw new UnsupportedOperationException( "Unsupported type " + getElementJdbcType() );
		};
	}

	@Override
	public <X> ValueBinder<X> getBinder(final JavaType<X> javaTypeDescriptor) {
		final JavaType<?> elementJavaType;
		if ( javaTypeDescriptor instanceof PrimitiveByteArrayJavaType ) {
			elementJavaType = ByteJavaType.INSTANCE;
		}
		else if ( javaTypeDescriptor instanceof BasicPluralJavaType<?> pluralJavaType ) {
			elementJavaType = pluralJavaType.getElementJavaType();
		}
		else {
			throw new UnsupportedOperationException( "Unsupported JavaType: " + javaTypeDescriptor );
		}
		return new Binder<>( javaTypeDescriptor, elementJavaType );
	}

	private class Binder<X,E> extends BasicBinder<X> {
		private final JavaType<E> elementJavaType;

		private Binder(JavaType<X> javaType, JavaType<E> elementJavaType) {
			super( javaType, Neo4jVectorJdbcType.this );
			this.elementJavaType = elementJavaType;
		}

		@Override
		protected void doBind(PreparedStatement st, X value, int index, WrapperOptions options)
				throws SQLException {
			st.setArray( index, getArray( value, options ) );
		}

		@Override
		protected void doBind(CallableStatement st, X value, String name, WrapperOptions options)
				throws SQLException {
			final var array = getArray( value, options );
			try {
				st.setObject( name, array, Types.ARRAY );
			}
			catch (SQLException ex) {
				throw new HibernateException(
						"JDBC driver does not support named parameters for setArray. Use positional.", ex );
			}
		}

		@Override
		public Object[] getBindValue(X value, WrapperOptions options) throws SQLException {
			final var elementBinder = getElementJdbcType().getBinder( elementJavaType );
			return convertToArray( this, elementBinder, elementJavaType, value, options );
		}

		private java.sql.Array getArray(X value, WrapperOptions options) throws SQLException {
			final var session = options.getSession();
			return session.getJdbcCoordinator().getLogicalConnection().getPhysicalConnection()
					.createArrayOf( getElementTypeName( getJavaType(), session ),
							getBindValue( value, options ) );
		}
	}

	protected <T,E> Object[] convertToArray(
			BasicBinder<T> binder,
			ValueBinder<E> elementBinder,
			JavaType<E> elementJavaType,
			T value,
			WrapperOptions options)
					throws SQLException {
		final var elementJdbcType = this.getElementJdbcType();
		final var javaType = binder.getJavaType();
		if ( elementJdbcType instanceof AggregateJdbcType ) {
			final var domainObjects = javaType.unwrap( value, Object[].class, options );
			final var objects = new Object[domainObjects.length];
			for ( int i = 0; i < domainObjects.length; i++ ) {
				if ( domainObjects[i] != null ) {
					final E element = elementJavaType.cast( domainObjects[i] );
					objects[i] = elementBinder.getBindValue( element, options );
				}
			}
			return objects;
		}
		else {
			final var arrayClass =
					(Class<? extends Object[]>)
							elementJdbcJavaTypeClass( options, elementJdbcType )
									.arrayType();
			return javaType.unwrap( value, arrayClass, options );
		}
	}

	private static Class<?> elementJdbcJavaTypeClass(WrapperOptions options, JdbcType elementJdbcType) {
		final var typeConfiguration = options.getTypeConfiguration();
		final var preferredJavaTypeClass = elementJdbcType.getPreferredJavaTypeClass( options );
		return preferredJavaTypeClass != null
				? preferredJavaTypeClass
				: typeConfiguration.getJdbcTypeRegistry()
						.getDescriptor( elementJdbcType.getDefaultSqlTypeCode() )
						.getRecommendedJavaType( null, null, typeConfiguration )
						.getJavaTypeClass();
	}
}
