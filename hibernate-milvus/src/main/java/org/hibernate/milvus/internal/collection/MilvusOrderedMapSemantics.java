/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.internal.collection;

import java.util.Map;
import org.hibernate.collection.spi.AbstractMapSemantics;
import org.hibernate.collection.spi.CollectionInitializerProducer;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.collection.spi.PersistentMap;
import org.hibernate.engine.FetchTiming;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.internal.util.collections.CollectionHelper;
import org.hibernate.metamodel.CollectionClassification;
import org.hibernate.metamodel.mapping.CollectionPart;
import org.hibernate.metamodel.mapping.PluralAttributeMapping;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.spi.NavigablePath;
import org.hibernate.sql.results.graph.DomainResultCreationState;
import org.hibernate.sql.results.graph.Fetch;
import org.hibernate.sql.results.graph.FetchParent;
import org.hibernate.sql.results.graph.collection.internal.MapInitializerProducer;

import java.util.Iterator;
import java.util.LinkedHashMap;

public class MilvusOrderedMapSemantics<K, V> extends AbstractMapSemantics<LinkedHashMap<K, V>, K, V> {
	/**
	 * Singleton access
	 */
	public static final MilvusOrderedMapSemantics<?, ?> INSTANCE = new MilvusOrderedMapSemantics<>();

	private MilvusOrderedMapSemantics() {
	}

	@Override
	public CollectionInitializerProducer createInitializerProducer(
			NavigablePath navigablePath,
			PluralAttributeMapping attributeMapping,
			FetchParent fetchParent,
			boolean selected,
			String resultVariable,
			DomainResultCreationState creationState) {
		final Fetch indexFetch = fetchParent.generateFetchableFetch(
				attributeMapping.getIndexDescriptor(),
				navigablePath.append( CollectionPart.Nature.INDEX.getName() ),
				FetchTiming.IMMEDIATE,
				selected,
				null,
				creationState
		);
		final Fetch elementFetch = MilvusFetchHelper.createElementFetch( navigablePath, attributeMapping, fetchParent, selected, creationState );
		return new MapInitializerProducer( attributeMapping, indexFetch, elementFetch );
	}

	@Override
	public CollectionClassification getCollectionClassification() {
		return CollectionClassification.ORDERED_MAP;
	}

	@Override
	public LinkedHashMap<K,V> instantiateRaw(
			int anticipatedSize,
			CollectionPersister collectionDescriptor) {
		return anticipatedSize < 1 ? CollectionHelper.linkedMap() : CollectionHelper.linkedMapOfSize( anticipatedSize );
	}

	@Override
	public PersistentCollection<V> instantiateWrapper(
			Object key,
			CollectionPersister collectionDescriptor,
			SharedSessionContractImplementor session) {
		return new PersistentMap<>( session );
	}

	@Override
	public PersistentCollection<V> wrap(
			LinkedHashMap<K,V> rawCollection,
			CollectionPersister collectionDescriptor,
			SharedSessionContractImplementor session) {
		return new PersistentMap<>( session, rawCollection );
	}

	@Override
	public Iterator<V> getElementIterator(LinkedHashMap<K,V> rawCollection) {
		return rawCollection.values().iterator();
	}

	@Override
	public <KK, VV> Map<KK, VV> instantiateWithElements(
			int anticipatedSize,
			CollectionPersister collectionDescriptor,
			Map<? extends KK, ? extends VV> entries) {
		final Map<KK, VV> map = anticipatedSize < 1
				? CollectionHelper.linkedMap()
				: CollectionHelper.linkedMapOfSize( anticipatedSize );
		map.putAll( entries );
		return map;
	}
}
