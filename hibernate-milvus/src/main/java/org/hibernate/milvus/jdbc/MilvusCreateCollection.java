/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.jdbc;

import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;

import java.util.List;
import java.util.Map;

public record MilvusCreateCollection(
		String collectionName,
		String description,
		Integer dimension,
		String primaryFieldName,
		DataType idType,
		Integer maxLength,
		String vectorFieldName,
		String metricType,
		Boolean autoID,
		Boolean enableDynamicField,
		Integer numShards,
		Schema collectionSchema,
		List<IndexParam> indexParams,
		Integer numPartitions,
		ConsistencyLevel consistencyLevel,
		Map<String, String> properties
) implements MilvusStatementDefinition {

	@Override
	public int parameterCount() {
		return 0;
	}

	public record Schema(
			List<FieldSchema> fieldSchemaList
	) {

	}

	public record FieldSchema(
			String name,
			String description,
			DataType dataType,
			Integer maxLength,
			Integer dimension,
			Boolean isPrimaryKey,
			Boolean isPartitionKey,
			Boolean isClusteringKey,
			Boolean autoID,
			DataType elementType,
			Integer maxCapacity,
			Boolean isNullable,
			Object defaultValue,
			Boolean enableAnalyzer,
			Map<String, Object> analyzerParams,
			Boolean enableMatch
	) {

	}
	public record IndexParam(
			String fieldName,
			String indexName,
			io.milvus.v2.common.IndexParam.IndexType indexType,
			io.milvus.v2.common.IndexParam.MetricType metricType,
			Map<String, Object> extraParams
	) {

	}
}
