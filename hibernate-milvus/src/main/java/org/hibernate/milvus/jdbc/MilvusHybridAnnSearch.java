/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.jdbc;

import io.milvus.v2.common.IndexParam;

import java.util.List;
import java.util.Map;

public final class MilvusHybridAnnSearch implements MilvusSearchRequest {
	private String annsField;
	private int topK;
	private Map<String, MilvusTypedValue> searchParams;
	private List<MilvusTypedValue> data;
	private IndexParam.MetricType metricType;

	public MilvusHybridAnnSearch() {
	}

	public MilvusHybridAnnSearch(MilvusSearch search) {
		this((AbstractMilvusQuery) search);
		this.annsField = search.getAnnsField();
		this.metricType = search.getMetricType();
		this.topK = search.getTopK();
		this.searchParams = search.getSearchParams();
		this.data = search.getData();
	}

	public MilvusHybridAnnSearch(AbstractMilvusQuery query) {
		this.topK = (int) query.getLimit();
		if (query.getOffset() != 0) {
			throw new IllegalArgumentException("Can't apply offset to hybrid query");
		}
	}

	public int parameterCount() {
		int count = 0;
		if ( searchParams != null ) {
			for ( MilvusTypedValue value : searchParams.values() ) {
				if ( value instanceof MilvusParameterValue ) {
					count++;
				}
			}
		}
		if ( data != null ) {
			for ( MilvusTypedValue value : data ) {
				if ( value instanceof MilvusParameterValue ) {
					count++;
				}
			}
		}
		return count;
	}

	@Override
	public String getAnnsField() {
		return annsField;
	}

	@Override
	public void setAnnsField(String annsField) {
		this.annsField = annsField;
	}

	@Override
	public int getTopK() {
		return topK;
	}

	@Override
	public void setTopK(int topK) {
		this.topK = topK;
	}

	@Override
	public Map<String, MilvusTypedValue> getSearchParams() {
		return searchParams;
	}

	@Override
	public void setSearchParams(Map<String, MilvusTypedValue> searchParams) {
		this.searchParams = searchParams;
	}

	@Override
	public List<MilvusTypedValue> getData() {
		return data;
	}

	@Override
	public void setData(List<MilvusTypedValue> data) {
		this.data = data;
	}

	@Override
	public IndexParam.MetricType getMetricType() {
		return metricType;
	}

	@Override
	public void setMetricType(IndexParam.MetricType metricType) {
		this.metricType = metricType;
	}
}
