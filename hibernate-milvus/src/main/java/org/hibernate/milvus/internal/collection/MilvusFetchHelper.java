/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.internal.collection;

import org.hibernate.engine.FetchTiming;
import org.hibernate.engine.spi.LoadQueryInfluencers;
import org.hibernate.metamodel.mapping.CollectionPart;
import org.hibernate.metamodel.mapping.EntityMappingType;
import org.hibernate.metamodel.mapping.PluralAttributeMapping;
import org.hibernate.metamodel.mapping.internal.ManyToManyCollectionPart;
import org.hibernate.milvus.internal.graph.EntityFetchSelectImpl;
import org.hibernate.spi.NavigablePath;
import org.hibernate.sql.ast.spi.query.from.PluralTableGroup;
import org.hibernate.sql.ast.spi.query.from.TableGroup;
import org.hibernate.sql.results.graph.DomainResult;
import org.hibernate.sql.results.graph.DomainResultCreationState;
import org.hibernate.sql.results.graph.Fetch;
import org.hibernate.sql.results.graph.FetchParent;

public class MilvusFetchHelper {

	public static Fetch createElementFetch(
			NavigablePath navigablePath,
			PluralAttributeMapping attributeMapping,
			FetchParent fetchParent,
			boolean selected,
			DomainResultCreationState creationState) {
		if ( attributeMapping.getElementDescriptor() instanceof ManyToManyCollectionPart entityCollectionPart ) {
			final var foreignKey = entityCollectionPart.getForeignKeyDescriptor();
			final var fromClauseAccess = creationState.getSqlAstCreationState().getFromClauseAccess();
			final var parentTableGroup = (PluralTableGroup) fromClauseAccess.getTableGroup( navigablePath );
			final TableGroup tableGroup = parentTableGroup.getElementTableGroup();
			final DomainResult<?> keyResult = foreignKey.createKeyDomainResult(
					navigablePath,
					tableGroup,
					fetchParent,
					creationState
			);

			final EntityMappingType associatedEntityMappingType = entityCollectionPart.getAssociatedEntityMappingType();
			final boolean isSelectByUniqueKey = !foreignKey.getNavigableRole()
					.equals( associatedEntityMappingType.getIdentifierMapping().getNavigableRole() );
			final LoadQueryInfluencers loadQueryInfluencers = creationState.getSqlAstCreationState()
					.getLoadQueryInfluencers();
			final boolean isAffectedByEnabledFilters =
					associatedEntityMappingType.isAffectedByEnabledFilters( loadQueryInfluencers, true );
			return new EntityFetchSelectImpl(
					fetchParent,
					entityCollectionPart,
					navigablePath,
					keyResult,
					isSelectByUniqueKey,
					isAffectedByEnabledFilters,
					creationState
			);
		}
		else {
			return fetchParent.generateFetchableFetch(
					attributeMapping.getElementDescriptor(),
					navigablePath.append( CollectionPart.Nature.ELEMENT.getName() ),
					FetchTiming.IMMEDIATE,
					selected,
					null,
					creationState
			);
		}
	}

	private MilvusFetchHelper() {
	}
}
