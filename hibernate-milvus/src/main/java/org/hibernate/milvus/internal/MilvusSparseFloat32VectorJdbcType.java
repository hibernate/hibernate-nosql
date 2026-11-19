/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.internal;

import io.milvus.v2.service.vector.request.data.SparseFloatVec;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.ArrayJdbcType;
import org.hibernate.type.descriptor.jdbc.BasicBinder;
import org.hibernate.type.descriptor.jdbc.BasicExtractor;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.spi.TypeConfiguration;
import org.hibernate.vector.SparseFloatVector;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.SortedMap;
import java.util.TreeMap;

public class MilvusSparseFloat32VectorJdbcType extends ArrayJdbcType {

	private final int size;

	public MilvusSparseFloat32VectorJdbcType(JdbcType elementJdbcType, int size) {
		super( elementJdbcType );
		this.size = size;
	}

	@Override
	public int getDefaultSqlTypeCode() {
		return SqlTypes.SPARSE_VECTOR_FLOAT32;
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
				return getValue( rs.getObject( paramIndex, SparseFloatVec.class ), options );
			}

			@Override
			protected X doExtract(CallableStatement statement, int index, WrapperOptions options) throws SQLException {
				return getValue( statement.getObject( index, SparseFloatVec.class ), options );
			}

			@Override
			protected X doExtract(CallableStatement statement, String name, WrapperOptions options) throws SQLException {
				return getValue( statement.getObject( name, SparseFloatVec.class ), options );
			}

			private X getValue(SparseFloatVec vector, WrapperOptions options) {
				if ( vector == null ) {
					return null;
				}

				@SuppressWarnings("unchecked")
				final SortedMap<Long, Float> sparseFloats = (SortedMap<Long, Float>) vector.getData();
				final int[] indices = new int[sparseFloats.size()];
				final float[] values = new float[sparseFloats.size()];
				int i = 0;
				for ( var entry : sparseFloats.entrySet() ) {
					indices[i] = Math.toIntExact( entry.getKey() );
					values[i] = entry.getValue();
					i++;
				}
				return getJavaType().wrap( new SparseFloatVector( ((MilvusSparseFloat32VectorJdbcType) getJdbcType()).size, indices, values ), options );
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
				final SparseFloatVector sparseFloatVector = getJavaType().unwrap( value, SparseFloatVector.class, options );
				final TreeMap<Long, Float> sparseFloats = new TreeMap<>();
				final int[] indices = sparseFloatVector.indices();
				final float[] floats = sparseFloatVector.floats();
				for ( int i = 0; i < indices.length; i++ ) {
					sparseFloats.put( (long) indices[i], floats[i] );
				}
				return new SparseFloatVec( sparseFloats );
			}
		};
	}
}
