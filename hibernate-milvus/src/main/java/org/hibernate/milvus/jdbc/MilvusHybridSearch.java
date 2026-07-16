/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.jdbc;

import java.util.ArrayList;
import java.util.List;

public final class MilvusHybridSearch extends AbstractMilvusQuery implements MilvusStatementDefinition {
	private int roundDecimal;
	private MilvusRanker ranker;
	private List<MilvusHybridAnnSearch> searches;

	private String groupByFieldName;
	private Integer groupSize;
	private Boolean strictGroupSize;

	public MilvusHybridSearch() {
	}

	public MilvusHybridSearch(MilvusSearch original) {
		this((AbstractMilvusQuery) original );
		this.setLimit( original.getTopK() );
		this.roundDecimal = original.getRoundDecimal();
		this.groupByFieldName = original.getGroupByFieldName();
		this.groupSize = original.getGroupSize();
		this.strictGroupSize = original.getStrictGroupSize();
		// Default ranker
		this.ranker = new MilvusRRFRanker( 60 );
	}

	public MilvusHybridSearch(AbstractMilvusQuery original) {
		super( original );
		if (original.getOffset() != 0) {
			throw new IllegalArgumentException("Can't apply offset to hybrid query");
		}
		this.searches = new ArrayList<>( 1 );
		if ( original instanceof MilvusSearch search ) {
			this.searches.add( new MilvusHybridAnnSearch( search ) );
		}
		else {
			this.searches.add( new MilvusHybridAnnSearch( original ) );
		}
	}

	@Override
	public int parameterCount() {
		int count = super.parameterCount();
		if ( searches != null ) {
			for ( MilvusHybridAnnSearch search : searches ) {
				count += search.parameterCount();
			}
		}
		return count;
	}

	public int getRoundDecimal() {
		return roundDecimal;
	}

	public void setRoundDecimal(int roundDecimal) {
		this.roundDecimal = roundDecimal;
	}

	public MilvusRanker getRanker() {
		return ranker;
	}

	public void setRanker(MilvusRanker ranker) {
		this.ranker = ranker;
	}

	public List<MilvusHybridAnnSearch> getSearches() {
		return searches;
	}

	public void setSearches(List<MilvusHybridAnnSearch> searches) {
		this.searches = searches;
	}

	public String getGroupByFieldName() {
		return groupByFieldName;
	}

	public void setGroupByFieldName(String groupByFieldName) {
		this.groupByFieldName = groupByFieldName;
	}

	public Integer getGroupSize() {
		return groupSize;
	}

	public void setGroupSize(Integer groupSize) {
		this.groupSize = groupSize;
	}

	public Boolean getStrictGroupSize() {
		return strictGroupSize;
	}

	public void setStrictGroupSize(Boolean strictGroupSize) {
		this.strictGroupSize = strictGroupSize;
	}
}
