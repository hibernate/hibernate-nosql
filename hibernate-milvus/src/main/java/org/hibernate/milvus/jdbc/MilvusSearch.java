/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.jdbc;

import io.milvus.v2.common.IndexParam;

import java.util.List;
import java.util.Map;

public final class MilvusSearch extends AbstractMilvusQuery implements MilvusStatementDefinition, MilvusSearchRequest {
	private String annsField;
	private IndexParam.MetricType metricType;
	private int topK;
	private int roundDecimal;
	private Map<String, MilvusTypedValue> searchParams;
	private List<MilvusTypedValue> data;

	private long guaranteeTimestamp;
	private Long gracefulTime;
	private boolean ignoreGrowing;
	private String groupByFieldName;
	private Integer groupSize;
	private Boolean strictGroupSize;

	public MilvusSearch() {
	}

	public MilvusSearch(AbstractMilvusQuery original) {
		super( original );
	}

	@Override
	public int parameterCount() {
		int count = super.parameterCount();
		if ( data != null ) {
			for ( MilvusTypedValue data : data ) {
				if ( data instanceof MilvusParameterValue ) {
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
	public IndexParam.MetricType getMetricType() {
		return metricType;
	}

	@Override
	public void setMetricType(IndexParam.MetricType metricType) {
		this.metricType = metricType;
	}

	@Override
	public int getTopK() {
		return topK;
	}

	@Override
	public void setTopK(int topK) {
		this.topK = topK;
	}

	public int getRoundDecimal() {
		return roundDecimal;
	}

	public void setRoundDecimal(int roundDecimal) {
		this.roundDecimal = roundDecimal;
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

	public long getGuaranteeTimestamp() {
		return guaranteeTimestamp;
	}

	public void setGuaranteeTimestamp(long guaranteeTimestamp) {
		this.guaranteeTimestamp = guaranteeTimestamp;
	}

	public Long getGracefulTime() {
		return gracefulTime;
	}

	public void setGracefulTime(Long gracefulTime) {
		this.gracefulTime = gracefulTime;
	}

	public boolean isIgnoreGrowing() {
		return ignoreGrowing;
	}

	public void setIgnoreGrowing(boolean ignoreGrowing) {
		this.ignoreGrowing = ignoreGrowing;
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
