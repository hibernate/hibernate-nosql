/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.jdbc;

import io.milvus.v2.common.IndexParam;

import java.util.List;
import java.util.Map;

public sealed interface MilvusSearchRequest permits MilvusHybridAnnSearch, MilvusSearch {

	String getAnnsField();
	void setAnnsField(String annsField);

	int getTopK();
	void setTopK(int topK);

	Map<String, MilvusTypedValue> getSearchParams();
	void setSearchParams(Map<String, MilvusTypedValue> searchParams);

	List<MilvusTypedValue> getData();
	void setData(List<MilvusTypedValue> data);

	IndexParam.MetricType getMetricType();
	void setMetricType(IndexParam.MetricType metricType);
}
