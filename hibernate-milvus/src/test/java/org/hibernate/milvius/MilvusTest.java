/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvius;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Tuple;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.query.spi.NativeQueryInterpreter;
import org.hibernate.jpa.HibernateHints;
import org.hibernate.milvus.MilvusDatabaseHints;
import org.hibernate.milvus.MilvusDialect;
import org.hibernate.milvus.MilvusNativeQueryInterpreter;
import org.hibernate.milvus.jdbc.MilvusJsonHelper;
import org.hibernate.milvus.jdbc.MilvusNumberValue;
import org.hibernate.milvus.jdbc.MilvusQuery;
import org.hibernate.nosql.testing.EventualConsistentTestHelper;
import org.hibernate.testing.orm.junit.DomainModel;
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

import static org.hibernate.nosql.testing.VectorTestHelper.cosineDistance;
import static org.hibernate.nosql.testing.VectorTestHelper.euclideanDistance;
import static org.hibernate.nosql.testing.VectorTestHelper.euclideanSquaredDistance;
import static org.hibernate.nosql.testing.VectorTestHelper.innerProduct;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

	@BeforeEach
	public void prepareData(SessionFactoryScope scope) {
		scope.inTransaction( em -> {
			em.persist( new VectorEntity( 1L, V1 ) );
			em.persist( new VectorEntity( 2L, V2 ) );
		} );
		EventualConsistentTestHelper.awaitExisting( scope, VectorEntity.class, 1L );
		EventualConsistentTestHelper.awaitExisting( scope, VectorEntity.class, 2L );
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
	public void testCosineDistance(SessionFactoryScope scope) {
		scope.inTransaction( em -> {
			final float[] vector = new float[]{ 1, 1, 1 };
			final List<Tuple> results = em.createSelectionQuery( "select e.id, cosine_distance(e.theCosineVector, :vec) from VectorEntity e", Tuple.class )
					.setParameter( "vec", vector )
					.getResultList();
			results.sort( Comparator.comparingLong( o -> o.get( 0, Long.class ) ) );
			assertEquals( 2, results.size() );
			assertEquals( 1L, results.get( 0 ).get( 0 ) );
			assertEquals( cosineDistance( V1, vector ), results.get( 0 ).get( 1, Double.class ), 0.0000001D );
			assertEquals( 2L, results.get( 1 ).get( 0 ) );
			assertEquals( cosineDistance( V2, vector ), results.get( 1 ).get( 1, Double.class ), 0.0000001D );
		} );
	}

	@Test
	public void testEuclideanDistance(SessionFactoryScope scope) {
		scope.inTransaction( em -> {
			final float[] vector = new float[]{ 1, 1, 1 };
			final List<Tuple> results = em.createSelectionQuery( "select e.id, euclidean_distance(e.theL2Vector, :vec) from VectorEntity e", Tuple.class )
					.setParameter( "vec", vector )
					.getResultList();
			results.sort( Comparator.comparingLong( o -> o.get( 0, Long.class ) ) );
			assertEquals( 2, results.size() );
			assertEquals( 1L, results.get( 0 ).get( 0 ) );
			assertEquals( euclideanDistance( V1, vector ), results.get( 0 ).get( 1, Double.class ), 0D );
			assertEquals( 2L, results.get( 1 ).get( 0 ) );
			assertEquals( euclideanDistance( V2, vector ), results.get( 1 ).get( 1, Double.class ), 0D );
		} );
	}

	@Test
	public void testEuclideanSquaredDistance(SessionFactoryScope scope) {
		scope.inTransaction( em -> {
			final float[] vector = new float[]{ 1, 1, 1 };
			final List<Tuple> results = em.createSelectionQuery( "select e.id, euclidean_squared_distance(e.theL2Vector, :vec) from VectorEntity e", Tuple.class )
					.setParameter( "vec", vector )
					.getResultList();
			results.sort( Comparator.comparingLong( o -> o.get( 0, Long.class ) ) );
			assertEquals( 2, results.size() );
			assertEquals( 1L, results.get( 0 ).get( 0 ) );
			assertEquals( euclideanSquaredDistance( V1, vector ), results.get( 0 ).get( 1, Double.class ), 0D );
			assertEquals( 2L, results.get( 1 ).get( 0 ) );
			assertEquals( euclideanSquaredDistance( V2, vector ), results.get( 1 ).get( 1, Double.class ), 0D );
		} );
	}

	@Test
	public void testInnerProduct(SessionFactoryScope scope) {
		scope.inTransaction( em -> {
			final float[] vector = new float[]{ 1, 1, 1 };
			final List<Tuple> results = em.createSelectionQuery( "select e.id, inner_product(e.theIpVector, :vec) from VectorEntity e order by 2 desc", Tuple.class )
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
				select e.id, inner_product(e.theIpVector, :vec) as distance
				from VectorEntity e
				where inner_product(e.theIpVector, :vec) between 4 and 6
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
	public void testInListPredicateTransformation(SessionFactoryScope scope) {
		scope.inTransaction( em -> {
			final List<Tuple> results = em.createSelectionQuery(
							"select e.id from VectorEntity e where e.theLong in :ids and e.theString in :theStrings",
							Tuple.class
					)
					.setParameter( "ids", List.of( 1L, 2L ) )
					.setParameter( "theStrings", List.of( "vector1", "vector2" ) )
					.getResultList();
			assertEquals( 2, results.size() );
			assertEquals( 1L, results.get( 0 ).get( 0 ) );
			assertEquals( 2L, results.get( 1 ).get( 0 ) );
		} );
	}

	@Test
	@RequiresDialect(value = MilvusDialect.class, majorVersion = 3)
	public void testCount(SessionFactoryScope scope) {
		scope.inTransaction( em -> {
			final Long count = em.createSelectionQuery(
							"select count(*) from VectorEntity e where e.theLong in :ids",
							Long.class
					)
					.setParameter( "ids", List.of( 1L, 2L ) )
					.getSingleResult();
			assertEquals( 2L, count );
		} );
	}

	@Test
	public void testDatabaseHint(SessionFactoryScope scope) {
		scope.inTransaction( em -> {
			final VectorEntity result = em.createSelectionQuery(
							"from VectorEntity e where e.theLong = :id",
							VectorEntity.class
					)
					.setParameter( "id", 1L )
					.setHint( HibernateHints.HINT_QUERY_DATABASE, MilvusDatabaseHints.CONSISTENCY_LEVEL + "=STRONG" )
					.getSingleResult();
			assertNotNull( result );
		} );
	}

	@Entity( name = "VectorEntity" )
	@Table(indexes = {
			@Index( name = "VectorEntity_ip", columnList = "the_ip_vector", options = "metric=ip"),
			@Index( name = "VectorEntity_cosine", columnList = "the_cosine_vector", options = "metric=cosine"),
			@Index( name = "VectorEntity_l2", columnList = "the_l2_vector", options = "metric=l2")
	})
	public static class VectorEntity {

		@Id
		private Long id;

		private byte theByte;
		private short theShort;
		private int theInt;
		private long theLong;
		private String theString;

		@Column( name = "the_ip_vector", nullable = false )
		@JdbcTypeCode(SqlTypes.VECTOR)
		@Array(length = 3)
		private float[] theIpVector;
		@Column( name = "the_cosine_vector", nullable = false )
		@JdbcTypeCode(SqlTypes.VECTOR)
		@Array(length = 3)
		private float[] theCosineVector;
		@Column( name = "the_l2_vector", nullable = false )
		@JdbcTypeCode(SqlTypes.VECTOR)
		@Array(length = 3)
		private float[] theL2Vector;

		public VectorEntity() {
		}

		public VectorEntity(Long id, float[] theVector) {
			this.id = id;
			this.theByte = id.byteValue();
			this.theShort = id.shortValue();
			this.theInt = id.intValue();
			this.theLong = id.longValue();
			this.theString = "vector" + id;
			this.theIpVector = theVector;
			this.theCosineVector = theVector;
			this.theL2Vector = theVector;
		}

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public float[] getTheVector() {
			return theIpVector;
		}

		public void setTheVector(float[] theVector) {
			this.theIpVector = theVector;
		}
	}
}
