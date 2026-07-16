/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MilvusJsonHelper {
	private static final ObjectMapper MAPPER = new ObjectMapper();

	public static MilvusStatementDefinition parseDefinition(String json) throws JsonProcessingException {
		return MAPPER.readValue( json, MilvusStatementDefinition.class );
	}

	public static String serializeDefinition(MilvusStatementDefinition definition) {
		try {
			return MAPPER.writeValueAsString( definition );
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException( "Couldn't serialize statement", e );
		}
	}
}
