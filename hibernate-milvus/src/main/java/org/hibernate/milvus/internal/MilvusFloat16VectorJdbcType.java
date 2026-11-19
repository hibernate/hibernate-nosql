/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.internal;

import io.milvus.common.utils.Float16Utils;
import io.milvus.v2.service.vector.request.data.Float16Vec;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.ArrayJdbcType;
import org.hibernate.type.descriptor.jdbc.BasicBinder;
import org.hibernate.type.descriptor.jdbc.BasicExtractor;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.spi.TypeConfiguration;

import java.nio.ByteBuffer;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MilvusFloat16VectorJdbcType extends ArrayJdbcType {

	private final int sqlType;

	public MilvusFloat16VectorJdbcType(JdbcType elementJdbcType, int sqlType) {
		super( elementJdbcType );
		this.sqlType = sqlType;
	}

	@Override
	public int getDefaultSqlTypeCode() {
		return sqlType;
	}

	@Override
	public JavaType<?> getRecommendedJavaType(Integer precision, Integer scale, TypeConfiguration typeConfiguration) {
		return typeConfiguration.getJavaTypeRegistry().resolveDescriptor( float[].class );
	}

	@Override
	public <X> ValueExtractor<X> getExtractor(JavaType<X> javaTypeDescriptor) {
		return new BasicExtractor<>( javaTypeDescriptor, this ) {
			@Override
			protected X doExtract(ResultSet rs, int paramIndex, WrapperOptions options) throws SQLException {
				return getValue( rs.getObject( paramIndex, Float16Vec.class ), options );
			}

			@Override
			protected X doExtract(CallableStatement statement, int index, WrapperOptions options) throws SQLException {
				return getValue( statement.getObject( index, Float16Vec.class ), options );
			}

			@Override
			protected X doExtract(CallableStatement statement, String name, WrapperOptions options) throws SQLException {
				return getValue( statement.getObject( name, Float16Vec.class ), options );
			}

			private X getValue(Float16Vec vector, WrapperOptions options) {
				if ( vector == null ) {
					return null;
				}

				return getJavaType().wrap( Float16Utils.fp16BufferToVector( (ByteBuffer) vector.getData() ), options );
			}

		};
	}

	@Override
	public <X> ValueBinder<X> getBinder(final JavaType<X> javaTypeDescriptor) {
		return new BasicBinder<>( javaTypeDescriptor, this ) {

			@Override
			protected void doBind(PreparedStatement st, X value, int index, WrapperOptions options) throws SQLException {
				st.setObject( index, getBindValue( value, options ) );
			}

			@Override
			protected void doBind(CallableStatement st, X value, String name, WrapperOptions options)
					throws SQLException {
				st.setObject( name, getBindValue( value, options ), java.sql.Types.ARRAY );
			}

			@Override
			public Object getBindValue(X value, WrapperOptions options) {
				if ( value == null ) {
					return null;
				}
				final float[] floats = getJavaType().unwrap( value, float[].class, options );
				final List<Float> floatList = new ArrayList<>( floats.length );
				for ( float f : floats ) {
					floatList.add( f );
				}
				return new Float16Vec( floatList );
			}
		};
	}
}
