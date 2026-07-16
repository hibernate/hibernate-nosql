/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.neo4j;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.RequiresDialect;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DomainModel(
		annotatedClasses = {Neo4jTest.TestEntity.class, Neo4jTest.ChildEntity.class, Neo4jTest.CollectionEntity.class, Neo4jTest.ArrayEntity.class,})
@SessionFactory
@RequiresDialect(value = Neo4jDialect.class)
public class Neo4jTest {

	@Test
	public void persistAndUpdateTest(SessionFactoryScope scope) {
		final ZonedDateTime birthday = ZonedDateTime.of( LocalDate.of( 1996, 10, 15 ), LocalTime.of( 20, 32 ),
				ZoneId.systemDefault() );
		scope.inTransaction( session -> {
			final ChildEntity child = new ChildEntity();
			child.id = "child_1";
			child.birthday = birthday;
			session.persist( child );
			final TestEntity testEntity = new TestEntity();
			testEntity.id = 1L;
			testEntity.name = "test_1";
			testEntity.child = child;
			session.persist( testEntity );
		} );

		scope.inTransaction( session -> {
			TestEntity testEntity = session.find( TestEntity.class, 1L );
			assertThat( testEntity.name ).isEqualTo( "test_1" );
			assertThat( testEntity.child.id ).isEqualTo( "child_1" );
			assertThat( testEntity.child.birthday ).isEqualTo( birthday );
			testEntity.name = "test_1_updated";
		} );

		scope.inSession( session -> {
			final String result = session.createQuery( "select t.name from TestEntity t where id = ?1", String.class )
					.setParameter( 1, 1L ).getSingleResult();
			assertThat( result ).isEqualTo( "test_1_updated" );
		} );
	}

	@Test
	public void persistNoChildAndRemoveTest(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			final TestEntity testEntity = new TestEntity();
			testEntity.id = 2L;
			testEntity.name = "test_2";
			session.persist( testEntity );
		} );

		scope.inTransaction( session -> {
			TestEntity testEntity = session.find( TestEntity.class, 2L );
			session.remove( testEntity );
		} );

		scope.inSession( session -> {
			TestEntity testEntity = session.find( TestEntity.class, 2L );
			assertThat( testEntity ).isNull();
		} );
	}

	@Test
	public void dmlQueriesTest(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			// insert-selects not supported
			final TestEntity t1 = new TestEntity();
			t1.id = 3L;
			t1.name = "test_3";
			session.persist( t1 );
		} );

		scope.inTransaction( session -> {
			// update
			assertThat( session.createMutationQuery(
					"update TestEntity t set t.name = 'test_3_updated' where t.id = 3"
			).executeUpdate() ).isEqualTo( 1 );
			assertThat( session.createQuery( "select t.name from TestEntity t where t.id = 3", String.class )
					.getSingleResult() ).isEqualTo( "test_3_updated" );
			// delete
			assertThat( session.createMutationQuery(
					"delete from TestEntity t where t.id = 3"
			).executeUpdate() ).isEqualTo( 1 );
			assertThat( session.createQuery( "select count(t) from TestEntity t where t.id = 3", Long.class )
					.getSingleResult() ).isEqualTo( 0L );
		} );
	}

	@Test
	public void testToMany(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			final CollectionEntity collectionEntity = new CollectionEntity();
			collectionEntity.id = 1L;

			final ChildEntity child1 = new ChildEntity();
			child1.id = "cc_1";
			session.persist( child1 );

			final ChildEntity child2 = new ChildEntity();
			child2.id = "cc_2";
			session.persist( child2 );

			collectionEntity.children = List.of( child1, child2 );
			session.persist( collectionEntity );
		} );

		scope.inTransaction( session -> {
			final CollectionEntity collectionEntity = session.find( CollectionEntity.class, 1L );
			assertThat( collectionEntity.children ).hasSize( 2 );

			final ChildEntity child3 = new ChildEntity();
			child3.id = "cc_3";
			session.persist( child3 );

			collectionEntity.children.add( child3 );
		} );

		scope.inSession( session -> {
			final List<ChildEntity> resultList = session.createQuery(
					"select c from CollectionEntity e left join e.children c", ChildEntity.class ).getResultList();
			assertThat( resultList ).hasSize( 3 );
			assertThat( resultList.stream().map( c -> c.id ).toList() ).containsExactlyInAnyOrder( "cc_1", "cc_2",
					"cc_3" );
		} );
	}

	@Test
	public void testStandardArrays(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			final ArrayEntity arrayEntity = new ArrayEntity();
			arrayEntity.id = 1L;
			arrayEntity.stringArray = new String[] {"a", "b", "c"};
			arrayEntity.integerArray = new Integer[] {1, 2, 3, 4, 5};
			arrayEntity.byteArray = new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
			session.persist( arrayEntity );
		} );

		scope.inSession( session -> {
			final ArrayEntity arrayEntity = session.find( ArrayEntity.class, 1L );
			assertThat( arrayEntity.stringArray ).containsExactly( "a", "b", "c" );
			assertThat( arrayEntity.integerArray ).containsExactly( 1, 2, 3, 4, 5 );
			assertThat( arrayEntity.byteArray ).containsExactly( (byte) 1, (byte) 2, (byte) 3, (byte) 4, (byte) 5,
					(byte) 6, (byte) 7, (byte) 8, (byte) 9, (byte) 10 );
		} );
	}

	@Test
	public void testAggregateFunctions(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			session.createQuery( "select count(*) from TestEntity", Long.class ).getSingleResult();
			session.createQuery( "select max(t.id) from TestEntity t", Long.class ).getSingleResult();
			session.createQuery( "select sum(t.id) from TestEntity t", Long.class ).getSingleResult();
			session.createQuery( "select avg(t.id) from TestEntity t", Double.class ).getSingleResult();
			session.createQuery( "select percentile_cont(t.id, 0.5) from TestEntity t", Double.class )
					.getSingleResult();
			session.createQuery( "select percentile_disc(t.id, 0.5) from TestEntity t", Double.class )
					.getSingleResult();
			session.createQuery( "select stDev(t.id) from TestEntity t", Double.class ).getSingleResult();
			session.createQuery( "select stDevP(t.id) from TestEntity t", Double.class ).getSingleResult();
		} );
	}

	@AfterAll
	public void tearDown(SessionFactoryScope scope) {
		scope.getSessionFactory().getSchemaManager().truncateMappedObjects();
	}

	@Entity(name = "TestEntity")
	static class TestEntity {
		@Id
		private Long id;

		@Column(name = "name_property", nullable = false)
		private String name;

		@ManyToOne
		private ChildEntity child;
	}

	@Entity(name = "ChildEntity")
	static class ChildEntity {
		@Id
		private String id;

		private ZonedDateTime birthday;
	}

	@Entity(name = "CollectionEntity")
	static class CollectionEntity {
		@Id
		private Long id;

		@OneToMany
		private List<ChildEntity> children;
	}

	@Entity(name = "ArrayEntity")
	static class ArrayEntity {
		@Id
		private Long id;

		@Column(name = "string_array", nullable = false)
		@JdbcTypeCode(SqlTypes.ARRAY)
		@Array(length = 3)
		private String[] stringArray;

		@Column(name = "integer_array")
		@JdbcTypeCode(SqlTypes.ARRAY)
		@Array(length = 33)
		private Integer[] integerArray;

		@Column(name = "byte_array")
		@JdbcTypeCode(SqlTypes.VECTOR)
		@Array(length = 100)
		private byte[] byteArray;
	}
}
