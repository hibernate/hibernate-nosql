/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.hibernate.engine.query.spi.NativeQueryInterpreter;
import org.hibernate.milvus.jdbc.MilvusJsonHelper;
import org.hibernate.milvus.jdbc.MilvusStatementDefinition;
import org.hibernate.query.sql.spi.ParameterRecognizer;


public class MilvusNativeQueryInterpreter implements NativeQueryInterpreter {
	@Override
	public void recognizeParameters(String nativeQuery, ParameterRecognizer recognizer) {
		try {
			final MilvusStatementDefinition milvusStatementDefinition = MilvusJsonHelper.parseDefinition( nativeQuery );
			final int parameterCount = milvusStatementDefinition.parameterCount();
			nativeQuery.chars().forEach( c -> recognizer.other( (char) c ) );
			for ( int i = 0; i < parameterCount; i++ ) {
				recognizer.ordinalParameter( -1 );
			}
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException( e );
		}
	}
}
