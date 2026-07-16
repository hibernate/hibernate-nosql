/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.nosql.test;

import jakarta.nosql.Column;
import jakarta.nosql.Entity;
import jakarta.nosql.Id;
import org.hibernate.nosql.test.junit.Template;
import org.hibernate.nosql.test.junit.TemplateScope;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

@Template
@SessionFactory
@DomainModel(annotatedClasses = CrudTest.SimpleEntity.class)
public class CrudTest {

	@Test
	public void testInsert(TemplateScope scope) {
		scope.inTransaction( template -> {
			final SimpleEntity simpleEntity = new SimpleEntity();
			simpleEntity.id = "123";
			simpleEntity.name = "test";
			template.insert( simpleEntity );
		} );
		scope.inTransaction( template -> {
			SimpleEntity simpleEntity = findExisting(template, SimpleEntity.class, "123" );
			assertNotNull( simpleEntity );
			assertEquals( "123", simpleEntity.id );
			assertEquals( "test", simpleEntity.name );
		} );
	}

	@Test
	public void testUpdate(TemplateScope scope) {
		scope.inTransaction( template -> {
			final SimpleEntity simpleEntity = new SimpleEntity();
			simpleEntity.id = "456";
			simpleEntity.name = "test";
			template.insert( simpleEntity );
		} );
		scope.inTransaction( template -> {
			SimpleEntity simpleEntity = findExisting(template, SimpleEntity.class, "456" );
			assertNotNull( simpleEntity );
			assertEquals( "456", simpleEntity.id );
			assertEquals( "test", simpleEntity.name );

			simpleEntity.name = "abc";
			template.update( simpleEntity );
		} );
		scope.inTransaction( template -> {
			SimpleEntity simpleEntity = findUntil(template, SimpleEntity.class, "456", optional -> optional.isPresent() && optional.get().name.equals( "abc" ) );
			assertNotNull( simpleEntity );
			assertEquals( "456", simpleEntity.id );
			assertEquals( "abc", simpleEntity.name );
		} );
	}

	@Test
	public void testDelete(TemplateScope scope) {
		scope.inTransaction( template -> {
			final SimpleEntity simpleEntity = new SimpleEntity();
			simpleEntity.id = "789";
			simpleEntity.name = "test";
			template.insert( simpleEntity );
		} );
		scope.inTransaction( template -> {
			SimpleEntity simpleEntity = findExisting(template, SimpleEntity.class, "789" );
			assertNotNull( simpleEntity );
			assertEquals( "789", simpleEntity.id );
			assertEquals( "test", simpleEntity.name );
		} );
		scope.inTransaction( template -> {
			template.delete( SimpleEntity.class, "789" );
		} );
		scope.inTransaction( template -> {
			SimpleEntity simpleEntity = findNonExisting( template, SimpleEntity.class, "789" );
			assertNull( simpleEntity );
		} );
	}

	@Test
	public void testFindNonExisting(TemplateScope scope) {
		scope.inTransaction( template -> {
			SimpleEntity simpleEntity = findNonExisting( template, SimpleEntity.class, "0" );
			assertNull( simpleEntity );
		} );
	}

	private <X> X findExisting(jakarta.nosql.Template template, Class<X> clazz, Object id) {
		return findUntil( template, clazz, id, Optional::isPresent );
	}

	private <X> X findNonExisting(jakarta.nosql.Template template, Class<X> clazz, Object id) {
		return findUntil( template, clazz, id, Optional::isEmpty );
	}

	private <X> X findUntil(jakarta.nosql.Template template, Class<X> clazz, Object id, Predicate<Optional<X>> acceptancePredicate) {
		int retries = 3;
		while ( 0 < retries-- ) {
			Optional<X> optional = template.find( clazz, id );
			if ( acceptancePredicate.test( optional ) ) {
				return optional.orElse( null );
			}
			// Wait a bit, since some NoSQL databases need some time until committed changes are visible
			try {
				Thread.sleep( 200 );
			}
			catch (InterruptedException e) {
				throw new RuntimeException( e );
			}
		}
		return null;
	}

	@Entity("SimpleEntity")
	public static class SimpleEntity {
		@Id("id")
		String id;
		@Column
		String name;
	}
}
