/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvius;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Tuple;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.query.spi.NativeQueryInterpreter;
import org.hibernate.milvus.MilvusDialect;
import org.hibernate.milvus.MilvusNativeQueryInterpreter;
import org.hibernate.milvus.jdbc.MilvusJsonHelper;
import org.hibernate.milvus.jdbc.MilvusNumberValue;
import org.hibernate.milvus.jdbc.MilvusQuery;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.FailureExpected;
import org.hibernate.testing.orm.junit.RequiresDialect;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.Setting;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Christian Beikov
 */
@DomainModel(annotatedClasses = MilvusTest.VectorEntity.class)
@SessionFactory
@ServiceRegistry(
		settings = {
				@Setting(name = AvailableSettings.NATIVE_IGNORE_JDBC_PARAMETERS, value = "true")
		},
		services = {
				@ServiceRegistry.Service( role = NativeQueryInterpreter.class, impl = MilvusNativeQueryInterpreter.class)
		}
)
@RequiresDialect(value = MilvusDialect.class)
public class MilvusTest {

	private static final float[] V1 = new float[]{ 1, 2, 3 };
	private static final float[] V2 = new float[]{ 4, 5, 6 };
	private static final byte[] BV1 = new byte[]{ 1, 2, 3 };
	private static final byte[] BV2 = new byte[]{ 4, 5, 6 };

	@BeforeEach
	public void prepareData(SessionFactoryScope scope) {
		scope.inTransaction( em -> {
			em.persist( new VectorEntity( 1L, V1, BV1 ) );
			em.persist( new VectorEntity( 2L, V2, BV2 ) );
		} );
		// Wait a bit, since Milvus need some time until committed changes are visible
		try {
			Thread.sleep( 1000 );
		}
		catch (InterruptedException e) {
			throw new RuntimeException( e );
		}
	}

	@AfterEach
	public void cleanup(SessionFactoryScope scope) {
		scope.inTransaction( em -> {
			em.createSelectionQuery( "from VectorEntity" ).list().forEach( em::remove );
		} );
	}

	@Test
	public void testRead(SessionFactoryScope scope) {
		scope.inTransaction( em -> {
			VectorEntity tableRecord;
			tableRecord = em.find( VectorEntity.class, 1L );
			assertArrayEquals( new float[]{ 1, 2, 3 }, tableRecord.getTheVector(), 0 );

			tableRecord = em.find( VectorEntity.class, 2L );
			assertArrayEquals( new float[]{ 4, 5, 6 }, tableRecord.getTheVector(), 0 );
		} );
	}

	@Test
	public void testNativeQuery(SessionFactoryScope scope) {
		scope.inTransaction( em -> {
			final MilvusQuery query = new MilvusQuery();
			query.setCollectionName( "VectorEntity" );
			query.setIds( List.of( new MilvusNumberValue( 1L ) ) );
			final List<VectorEntity> results =
					em.createNativeQuery( MilvusJsonHelper.serializeDefinition( query ), VectorEntity.class )
							.getResultList();
			assertEquals( 1, results.size() );
			assertEquals( 1L, results.get( 0 ).getId() );
		} );
	}

	@Test
	@FailureExpected(reason = "Milvus supports only one index per field and cosine distance produces unexpected values, so skip for now")
	public void testCosineDistance(SessionFactoryScope scope) {
		scope.inTransaction( em -> {
			final float[] vector = new float[]{ 1, 1, 1 };
			final List<Tuple> results = em.createSelectionQuery( "select e.id, cosine_distance(e.theVector, :vec) from VectorEntity e", Tuple.class )
					.setParameter( "vec", vector )
					.getResultList();
			results.sort( Comparator.comparingLong( o -> o.get( 0, Long.class ) ) );
			assertEquals( 2, results.size() );
			assertEquals( 1L, results.get( 0 ).get( 0 ) );
			assertEquals( cosineDistance( V1, vector ), results.get( 0 ).get( 1, Double.class ), 0.0000000000000002D );
			assertEquals( 2L, results.get( 1 ).get( 0 ) );
			assertEquals( cosineDistance( V2, vector ), results.get( 1 ).get( 1, Double.class ), 0.0000000000000002D );
		} );
	}

	@Test
	@FailureExpected(reason = "Milvus supports only one index per field and l2 distance produces unexpected values, so skip for now")
	public void testEuclideanDistance(SessionFactoryScope scope) {
		scope.inTransaction( em -> {
			//tag::euclidean-distance-example[]
			final float[] vector = new float[]{ 1, 1, 1 };
			final List<Tuple> results = em.createSelectionQuery( "select e.id, euclidean_distance(e.theVector, :vec) from VectorEntity e", Tuple.class )
					.setParameter( "vec", vector )
					.getResultList();
			//end::euclidean-distance-example[]
			results.sort( Comparator.comparingLong( o -> o.get( 0, Long.class ) ) );
			assertEquals( 2, results.size() );
			assertEquals( 1L, results.get( 0 ).get( 0 ) );
			assertEquals( euclideanDistance( V1, vector ), results.get( 0 ).get( 1, Double.class ), 0D );
			assertEquals( 2L, results.get( 1 ).get( 0 ) );
			assertEquals( euclideanDistance( V2, vector ), results.get( 1 ).get( 1, Double.class ), 0D );
		} );
	}
//
//	@Test
//	public void testTaxicabDistance(SessionFactoryScope scope) {
//		scope.inTransaction( em -> {
//			//tag::taxicab-distance-example[]
//			final float[] vector = new float[]{ 1, 1, 1 };
//			final List<Tuple> results = em.createSelectionQuery( "select e.id, taxicab_distance(e.theVector, :vec) from VectorEntity e", Tuple.class )
//					.setParameter( "vec", vector )
//					.getResultList();
//			//end::taxicab-distance-example[]
//			results.sort( Comparator.comparingLong( o -> o.get( 0, Long.class ) ) );
//			assertEquals( 2, results.size() );
//			assertEquals( 1L, results.get( 0 ).get( 0 ) );
//			assertEquals( taxicabDistance( V1, vector ), results.get( 0 ).get( 1, Double.class ), 0D );
//			assertEquals( 2L, results.get( 1 ).get( 0 ) );
//			assertEquals( taxicabDistance( V2, vector ), results.get( 1 ).get( 1, Double.class ), 0D );
//		} );
//	}
//
	@Test
	public void testInnerProduct(SessionFactoryScope scope) {
		scope.inTransaction( em -> {
			final float[] vector = new float[]{ 1, 1, 1 };
			final List<Tuple> results = em.createSelectionQuery( "select e.id, inner_product(e.theVector, :vec) from VectorEntity e order by 2 desc", Tuple.class )
					.setParameter( "vec", vector )
					.getResultList();
			results.sort( Comparator.comparingLong( o -> o.get( 0, Long.class ) ) );
			assertEquals( 2, results.size() );
			assertEquals( 1L, results.get( 0 ).get( 0 ) );
			assertEquals( innerProduct( V1, vector ), results.get( 0 ).get( 1, Double.class ), 0D );
			assertEquals( 2L, results.get( 1 ).get( 0 ) );
			assertEquals( innerProduct( V2, vector ), results.get( 1 ).get( 1, Double.class ), 0D );
		} );
	}

	@Test
	public void testInnerProductOrderAndFilter(SessionFactoryScope scope) {
		scope.inTransaction( em -> {
			final float[] vector = new float[]{ 1, 1, 1 };
			final List<Tuple> results = em.createSelectionQuery( """
				select e.id, inner_product(e.theVector, :vec) as distance
				from VectorEntity e
				where inner_product(e.theVector, :vec) between 4 and 6
				order by distance desc
				""", Tuple.class )
					.setParameter( "vec", vector )
					.getResultList();
			results.sort( Comparator.comparingLong( o -> o.get( 0, Long.class ) ) );
			assertEquals( 1, results.size() );
			assertEquals( 1L, results.get( 0 ).get( 0 ) );
		} );
	}

	@Test
	public void testHammingDistance(SessionFactoryScope scope) {
		scope.inTransaction( em -> {
			final byte[] vector = new byte[]{ 1, 1, 1 };
			final List<Tuple> results = em.createSelectionQuery( "select e.id, hamming_distance(e.theBinaryVector, :vec) from VectorEntity e", Tuple.class )
					.setParameter( "vec", vector )
					.getResultList();
			results.sort( Comparator.comparingLong( o -> o.get( 0, Long.class ) ) );
			assertEquals( 2, results.size() );
			assertEquals( 1L, results.get( 0 ).get( 0 ) );
			assertEquals( hammingDistance( BV1, vector ), results.get( 0 ).get( 1, Double.class ), 0D );
			assertEquals( 2L, results.get( 1 ).get( 0 ) );
			assertEquals( hammingDistance( BV2, vector ), results.get( 1 ).get( 1, Double.class ), 0D );
		} );
	}
//
//	@Test
//	public void testVectorDims(SessionFactoryScope scope) {
//		scope.inTransaction( em -> {
//			//tag::vector-dims-example[]
//			final List<Tuple> results = em.createSelectionQuery( "select e.id, vector_dims(e.theVector) from VectorEntity e", Tuple.class )
//					.getResultList();
//			//end::vector-dims-example[]
//			results.sort( Comparator.comparingLong( o -> o.get( 0, Long.class ) ) );
//			assertEquals( 2, results.size() );
//			assertEquals( 1L, results.get( 0 ).get( 0 ) );
//			assertEquals( V1.length, results.get( 0 ).get( 1 ) );
//			assertEquals( 2L, results.get( 1 ).get( 0 ) );
//			assertEquals( V2.length, results.get( 1 ).get( 1 ) );
//		} );
//	}
//
//	@Test
//	public void testVectorNorm(SessionFactoryScope scope) {
//		scope.inTransaction( em -> {
//			//tag::vector-norm-example[]
//			final List<Tuple> results = em.createSelectionQuery( "select e.id, vector_norm(e.theVector) from VectorEntity e", Tuple.class )
//					.getResultList();
//			//end::vector-norm-example[]
//			results.sort( Comparator.comparingLong( o -> o.get( 0, Long.class ) ) );
//			assertEquals( 2, results.size() );
//			assertEquals( 1L, results.get( 0 ).get( 0 ) );
//			assertEquals( euclideanNorm( V1 ), results.get( 0 ).get( 1, Double.class ), 0D );
//			assertEquals( 2L, results.get( 1 ).get( 0 ) );
//			assertEquals( euclideanNorm( V2 ), results.get( 1 ).get( 1, Double.class ), 0D );
//		} );
//	}

	private static double cosineDistance(float[] f1, float[] f2) {
		return 1D - innerProduct( f1, f2 ) / ( euclideanNorm( f1 ) * euclideanNorm( f2 ) );
	}

	private static double hammingDistance(byte[] f1, byte[] f2) {
		assert f1.length == f2.length;
		long distance = 0L;
		for ( int i = 0; i < f1.length; i++ ) {
			distance += Integer.bitCount( f1[i] ^ f2[i] );
		}
		return distance;
	}

	private static double euclideanDistance(float[] f1, float[] f2) {
		assert f1.length == f2.length;
		double result = 0;
		for ( int i = 0; i < f1.length; i++ ) {
			result += Math.pow( (double) f1[i] - f2[i], 2 );
		}
		return Math.sqrt( result );
	}

	private static double taxicabDistance(float[] f1, float[] f2) {
		return norm( f1 ) - norm( f2 );
	}

	private static double innerProduct(float[] f1, float[] f2) {
		assert f1.length == f2.length;
		double result = 0;
		for ( int i = 0; i < f1.length; i++ ) {
			result += ( (double) f1[i] ) * ( (double) f2[i] );
		}
		return result;
	}

	private static double euclideanNorm(float[] f) {
		double result = 0;
		for ( float v : f ) {
			result += Math.pow( v, 2 );
		}
		return Math.sqrt( result );
	}

	private static double norm(float[] f) {
		double result = 0;
		for ( float v : f ) {
			result += Math.abs( v );
		}
		return result;
	}

	@Entity( name = "VectorEntity" )
	public static class VectorEntity {

		@Id
		private Long id;

		@Column( name = "the_vector", nullable = false )
		@JdbcTypeCode(SqlTypes.VECTOR)
		@Array(length = 3)
		private float[] theVector;
		@Column( name = "the_binary_vector", nullable = false )
		@JdbcTypeCode(SqlTypes.VECTOR_INT8)
		@Array(length = 3)
		private byte[] theBinaryVector;

		public VectorEntity() {
		}

		public VectorEntity(Long id, float[] theVector, byte[] theBinaryVector) {
			this.id = id;
			this.theVector = theVector;
			this.theBinaryVector = theBinaryVector;
		}

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public float[] getTheVector() {
			return theVector;
		}

		public void setTheVector(float[] theVector) {
			this.theVector = theVector;
		}

		public byte[] getTheBinaryVector() {
			return theBinaryVector;
		}

		public void setTheBinaryVector(byte[] theBinaryVector) {
			this.theBinaryVector = theBinaryVector;
		}
	}
}
