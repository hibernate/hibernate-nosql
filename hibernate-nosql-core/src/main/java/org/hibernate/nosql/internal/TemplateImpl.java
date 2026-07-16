/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.nosql.internal;

import jakarta.nosql.QueryMapper;
import jakarta.persistence.PersistenceException;
import org.hibernate.StatelessSession;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.nosql.spi.TemplateImplementor;
import org.hibernate.persister.entity.EntityPersister;

import java.time.Duration;
import java.util.Optional;

public class TemplateImpl implements TemplateImplementor {

	private final StatelessSession session;

	public TemplateImpl(StatelessSession session) {
		this.session = session;
	}

	@Override
	public <T> T unwrap(Class<T> type) {
		if ( type.isInstance( session ) ) {
			return type.cast( session );
		}

		throw new PersistenceException(
				"Hibernate cannot unwrap '" + getClass().getName() + "' as '" + type.getName() + "'" );
	}

	@Override
	public <T> T insert(T entity) {
		session.insert( entity );
		return entity;
	}

	@Override
	public <T> T insert(T entity, Duration ttl) {
		throw new UnsupportedOperationException();
	}

	@Override
	public <T> Iterable<T> insert(Iterable<T> entities) {
		session.insertMultiple( new IterableList<>( entities ) );
		return entities;
	}

	@Override
	public <T> Iterable<T> insert(Iterable<T> entities, Duration ttl) {
		throw new UnsupportedOperationException();
	}

	@Override
	public <T> T update(T entity) {
		session.update( entity );
		return entity;
	}

	@Override
	public <T> Iterable<T> update(Iterable<T> entities) {
		session.updateMultiple( new IterableList<>( entities ) );
		return entities;
	}

	@Override
	public <T, K> Optional<T> find(Class<T> type, K id) {
		return Optional.ofNullable( session.get( type, id ) );
	}

	@Override
	public <T, K> void delete(Class<T> type, K id) {
		final SessionFactoryImplementor factory = (SessionFactoryImplementor) session.getFactory();
		final EntityPersister entityDescriptor = factory.getMappingMetamodel().getEntityDescriptor( type );
		session.delete( entityDescriptor.instantiate( id, session.unwrap( SharedSessionContractImplementor.class ) ) );
	}

	@Override
	public <T> QueryMapper.MapperFrom select(Class<T> type) {
		throw new UnsupportedOperationException();
	}

	@Override
	public <T> QueryMapper.MapperDeleteFrom delete(Class<T> type) {
		throw new UnsupportedOperationException();
	}
}
