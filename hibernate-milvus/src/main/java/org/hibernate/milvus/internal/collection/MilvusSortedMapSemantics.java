/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.internal.collection;

import java.util.Map;
import org.hibernate.collection.spi.AbstractMapSemantics;
import org.hibernate.collection.spi.CollectionInitializerProducer;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.collection.spi.PersistentSortedMap;
import org.hibernate.engine.FetchTiming;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.metamodel.CollectionClassification;
import org.hibernate.metamodel.mapping.CollectionPart;
import org.hibernate.metamodel.mapping.PluralAttributeMapping;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.spi.NavigablePath;
import org.hibernate.sql.results.graph.DomainResultCreationState;
import org.hibernate.sql.results.graph.Fetch;
import org.hibernate.sql.results.graph.FetchParent;
import org.hibernate.sql.results.graph.collection.internal.MapInitializerProducer;

import java.util.Comparator;
import java.util.SortedMap;
import java.util.TreeMap;

public class MilvusSortedMapSemantics<K, V> extends AbstractMapSemantics<SortedMap<K, V>, K, V> {
	/**
	 * Singleton access
	 */
	public static final MilvusSortedMapSemantics<?, ?> INSTANCE = new MilvusSortedMapSemantics<>();

	private MilvusSortedMapSemantics() {
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
		return CollectionClassification.SORTED_MAP;
	}

	@Override
	public Class<SortedMap> getCollectionJavaType() {
		return SortedMap.class;
	}

	@Override
	public TreeMap<K,V> instantiateRaw(
			int anticipatedSize,
			CollectionPersister collectionDescriptor) {
		return new TreeMap<K,V>(
				collectionDescriptor == null ? null : (Comparator) collectionDescriptor.getSortingComparator()
		);
	}

	@Override
	public PersistentCollection<V> instantiateWrapper(
			Object key,
			CollectionPersister collectionDescriptor,
			SharedSessionContractImplementor session) {
		//noinspection unchecked
		return new PersistentSortedMap<>( session, (Comparator<K>) collectionDescriptor.getSortingComparator() );
	}

	@Override
	public PersistentCollection<V> wrap(
			SortedMap<K,V> rawCollection,
			CollectionPersister collectionDescriptor,
			SharedSessionContractImplementor session) {
		return new PersistentSortedMap<>( session, rawCollection );
	}

	@Override
	@SuppressWarnings({"unchecked", "rawtypes"})
	public <KK, VV> SortedMap<KK, VV> instantiateWithElements(
			int anticipatedSize,
			CollectionPersister collectionDescriptor,
			Map<? extends KK, ? extends VV> entries) {
		final SortedMap<KK, VV> map = new TreeMap<>(
				collectionDescriptor == null ? null : (Comparator) collectionDescriptor.getSortingComparator()
		);
		map.putAll( entries );
		return map;
	}
}
