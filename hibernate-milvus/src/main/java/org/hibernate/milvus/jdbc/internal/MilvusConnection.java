/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.jdbc.internal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.milvus.common.utils.JsonUtils;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.vector.request.AnnSearchReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.HybridSearchReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.BaseVector;
import io.milvus.v2.service.vector.request.data.BinaryVec;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.request.ranker.RRFRanker;
import io.milvus.v2.service.vector.request.ranker.WeightedRanker;
import io.milvus.v2.service.vector.response.DeleteResp;
import io.milvus.v2.service.vector.response.InsertResp;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;
import io.milvus.v2.service.vector.response.UpsertResp;
import org.hibernate.internal.util.QuotingHelper;
import org.hibernate.milvus.jdbc.MilvusBooleanValue;
import org.hibernate.milvus.jdbc.MilvusCreateCollection;
import org.hibernate.milvus.jdbc.MilvusDelete;
import org.hibernate.milvus.jdbc.MilvusDropCollection;
import org.hibernate.milvus.jdbc.MilvusFloatVectorValue;
import org.hibernate.milvus.jdbc.MilvusHelper;
import org.hibernate.milvus.jdbc.MilvusHybridAnnSearch;
import org.hibernate.milvus.jdbc.MilvusHybridSearch;
import org.hibernate.milvus.jdbc.MilvusInsert;
import org.hibernate.milvus.jdbc.MilvusNumberValue;
import org.hibernate.milvus.jdbc.MilvusParameterValue;
import org.hibernate.milvus.jdbc.MilvusQuery;
import org.hibernate.milvus.jdbc.MilvusRRFRanker;
import org.hibernate.milvus.jdbc.MilvusSearch;
import org.hibernate.milvus.jdbc.MilvusStringValue;
import org.hibernate.milvus.jdbc.MilvusTypedValue;
import org.hibernate.milvus.jdbc.MilvusUpsert;
import org.hibernate.milvus.jdbc.MilvusWeightedRanker;
import org.jetbrains.annotations.NotNull;

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

public class MilvusConnection implements Connection {

	private final static char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

	final MilvusClientV2 client;
	final String url;
	final String userName;

	public MilvusConnection(MilvusClientV2 client, String url, String userName) {
		this.client = client;
		this.url = url;
		this.userName = userName;
	}

	void createCollection(MilvusCreateCollection createCollection) {
		CreateCollectionReq.CreateCollectionReqBuilder builder = CreateCollectionReq.builder();
		CreateCollectionReq.CollectionSchema schema = client.createSchema();
		List<IndexParam> indexParams = new ArrayList<>();
		MilvusCreateCollection.Schema collectionSchema = createCollection.collectionSchema();

		for ( MilvusCreateCollection.FieldSchema fieldSchema : collectionSchema.fieldSchemaList() ) {
			AddFieldReq.AddFieldReqBuilder<?> fieldBuilder = AddFieldReq.builder();
			if ( fieldSchema.name() != null ) {
				fieldBuilder.fieldName( fieldSchema.name() );
			}
			if ( fieldSchema.description() != null ) {
				fieldBuilder.description( fieldSchema.description() );
			}
			if ( fieldSchema.dataType() != null ) {
				fieldBuilder.dataType( fieldSchema.dataType() );
			}
			if ( fieldSchema.dimension() != null ) {
				fieldBuilder.dimension( fieldSchema.dimension() );
			}
			if ( fieldSchema.isPrimaryKey() != null ) {
				fieldBuilder.isPrimaryKey( fieldSchema.isPrimaryKey() );
			}
			if ( fieldSchema.isPartitionKey() != null ) {
				fieldBuilder.isPartitionKey( fieldSchema.isPartitionKey() );
			}
			if ( fieldSchema.isClusteringKey() != null ) {
				fieldBuilder.isClusteringKey( fieldSchema.isClusteringKey() );
			}
			if ( fieldSchema.autoID() != null ) {
				fieldBuilder.autoID( fieldSchema.autoID() );
			}
			if ( fieldSchema.elementType() != null ) {
				fieldBuilder.elementType( fieldSchema.elementType() );
			}
			if ( fieldSchema.maxCapacity() != null ) {
				fieldBuilder.maxCapacity( fieldSchema.maxCapacity() );
			}
			if ( fieldSchema.isNullable() != null ) {
				fieldBuilder.isNullable( fieldSchema.isNullable() );
			}
			if ( fieldSchema.defaultValue() != null ) {
				fieldBuilder.defaultValue( fieldSchema.defaultValue() );
			}
			if ( fieldSchema.enableAnalyzer() != null ) {
				fieldBuilder.enableAnalyzer( fieldSchema.enableAnalyzer() );
			}
			if ( fieldSchema.analyzerParams() != null ) {
				fieldBuilder.analyzerParams( fieldSchema.analyzerParams() );
			}
			if ( fieldSchema.enableMatch() != null ) {
				fieldBuilder.enableMatch( fieldSchema.enableMatch() );
			}
			schema.addField( fieldBuilder.build() );
		}

		if ( createCollection.indexParams() != null ) {
			for ( MilvusCreateCollection.IndexParam indexParam : createCollection.indexParams() ) {
				IndexParam.IndexParamBuilder indexParamBuilder = IndexParam.builder();
				if ( indexParam.fieldName() != null ) {
					indexParamBuilder.fieldName( indexParam.fieldName() );
				}
				if ( indexParam.indexName() != null ) {
					indexParamBuilder.indexName( indexParam.indexName() );
				}
				if ( indexParam.indexType() != null ) {
					indexParamBuilder.indexType( indexParam.indexType() );
				}
				if ( indexParam.metricType() != null ) {
					indexParamBuilder.metricType( indexParam.metricType() );
				}
				if ( indexParam.extraParams() != null ) {
					indexParamBuilder.extraParams( indexParam.extraParams() );
				}
				indexParams.add( indexParamBuilder.build() );
			}
		}

		if ( createCollection.collectionName() != null ) {
			builder.collectionName( createCollection.collectionName() );
		}
		if ( createCollection.description() != null ) {
			builder.description( createCollection.description() );
		}
		if ( createCollection.dimension() != null ) {
			builder.dimension( createCollection.dimension() );
		}
		if ( createCollection.primaryFieldName() != null ) {
			builder.primaryFieldName( createCollection.primaryFieldName() );
		}
		if ( createCollection.idType() != null ) {
			builder.idType( createCollection.idType() );
		}
		if ( createCollection.maxLength() != null ) {
			builder.maxLength( createCollection.maxLength() );
		}
		if ( createCollection.vectorFieldName() != null ) {
			builder.vectorFieldName( createCollection.vectorFieldName() );
		}
		if ( createCollection.metricType() != null ) {
			builder.metricType( createCollection.metricType() );
		}
		if ( createCollection.autoID() != null ) {
			builder.autoID( createCollection.autoID() );
		}
		if ( createCollection.enableDynamicField() != null ) {
			builder.enableDynamicField( createCollection.enableDynamicField() );
		}
		if ( createCollection.numShards() != null ) {
			builder.numShards( createCollection.numShards() );
		}
		builder.collectionSchema( schema );
		if ( !indexParams.isEmpty() ) {
			builder.indexParams( indexParams );
		}
		if ( createCollection.numPartitions() != null ) {
			builder.numPartitions( createCollection.numPartitions() );
		}
		if ( createCollection.consistencyLevel() != null ) {
			builder.consistencyLevel( createCollection.consistencyLevel() );
		}
		if ( createCollection.properties() != null ) {
			builder.properties( createCollection.properties() );
		}

		client.createCollection( builder.build() );


		CreateIndexReq.CreateIndexReqBuilder indexBuilder;
		IndexParam.IndexParamBuilder indexParamBuilder;
		for ( MilvusCreateCollection.FieldSchema fieldSchema : createCollection.collectionSchema().fieldSchemaList() ) {
			if ( fieldSchema.isPrimaryKey() ) {
				indexBuilder = CreateIndexReq.builder();
				indexParamBuilder = IndexParam.builder();
				indexParamBuilder.fieldName( fieldSchema.name() );
				indexParamBuilder.indexName( fieldSchema.name() + "_idx" );
				indexBuilder.collectionName( createCollection.collectionName() );
				indexBuilder.indexParams( List.of( indexParamBuilder.build() ) );
				client.createIndex( indexBuilder.build() );
			}
			switch ( fieldSchema.dataType() ) {
				case FloatVector, BFloat16Vector, Float16Vector, SparseFloatVector:
					indexBuilder = CreateIndexReq.builder();
					indexBuilder.collectionName( createCollection.collectionName() );
					indexParamBuilder = IndexParam.builder();
					indexParamBuilder.fieldName( fieldSchema.name() );

					// todo (milvus): Unfortunately, Milvus only supports a single index on a field,
					//  so for now we create the inner product,
					//  because that seems to be the only one that works

//					indexParamBuilder.indexName( fieldSchema.name() + "_cosine" );
//					indexParamBuilder.metricType( IndexParam.MetricType.COSINE );
//					indexBuilder.indexParams( List.of( indexParamBuilder.build() ) );
//					client.createIndex( indexBuilder.build() );

					indexParamBuilder.indexName( fieldSchema.name() + "_ip" );
					indexParamBuilder.metricType( IndexParam.MetricType.IP );
					indexBuilder.indexParams( List.of( indexParamBuilder.build() ) );
					client.createIndex( indexBuilder.build() );
//
//					indexParamBuilder.indexName( fieldSchema.name() + "_l2" );
//					indexParamBuilder.metricType( IndexParam.MetricType.L2 );
//					indexBuilder.indexParams( List.of( indexParamBuilder.build() ) );
//					client.createIndex( indexBuilder.build() );
					break;
				case BinaryVector:
					indexBuilder = CreateIndexReq.builder();
					indexBuilder.collectionName( createCollection.collectionName() );
					indexParamBuilder = IndexParam.builder();
					indexParamBuilder.fieldName( fieldSchema.name() );

					indexParamBuilder.indexName( fieldSchema.name() + "_hamming" );
					indexParamBuilder.metricType( IndexParam.MetricType.HAMMING );
					indexBuilder.indexParams( List.of( indexParamBuilder.build() ) );
					client.createIndex( indexBuilder.build() );
					break;
			}
		}

		client.loadCollection( LoadCollectionReq.builder().collectionName( createCollection.collectionName() ).build() );
	}

	void dropCollection(MilvusDropCollection dropCollection) {
		DropCollectionReq.DropCollectionReqBuilder builder = DropCollectionReq.builder();

		if ( dropCollection.collectionName() != null ) {
			builder.collectionName( dropCollection.collectionName() );
		}
		if ( dropCollection.async() != null ) {
			builder.async( dropCollection.async() );
		}
		if ( dropCollection.timeout() != null ) {
			builder.timeout( dropCollection.timeout() );
		}

		client.dropCollection( builder.build() );
	}

	InsertResp executeInsert(MilvusInsert query, Object[] parameterValues) throws SQLException {
		InsertReq.InsertReqBuilder builder = InsertReq.builder();
		if ( query.getCollectionName() != null ) {
			builder.collectionName( query.getCollectionName() );
		}
		if ( query.getPartitionName() != null ) {
			builder.partitionName( query.getPartitionName() );
		}
		List<JsonObject> data = new ArrayList<>();
		for ( Map<String, MilvusTypedValue> datum : query.getData() ) {
			JsonObject jsonObject = new JsonObject();
			for ( Map.Entry<String, MilvusTypedValue> entry : datum.entrySet() ) {
				jsonObject.add( entry.getKey(), determineJsonValue( entry.getValue(), parameterValues ) );
			}
			data.add( jsonObject );
		}
		builder.data( data );

		return client.insert( builder.build() );
	}

	UpsertResp executeUpsert(MilvusUpsert query, Object[] parameterValues) throws SQLException {
		UpsertReq.UpsertReqBuilder builder = UpsertReq.builder();
		if ( query.getCollectionName() != null ) {
			builder.collectionName( query.getCollectionName() );
		}
		if ( query.getPartitionName() != null ) {
			builder.partitionName( query.getPartitionName() );
		}
		List<JsonObject> data = new ArrayList<>();
		for ( Map<String, MilvusTypedValue> datum : query.getData() ) {
			JsonObject jsonObject = new JsonObject();
			for ( Map.Entry<String, MilvusTypedValue> entry : datum.entrySet() ) {
				jsonObject.add( entry.getKey(), determineJsonValue( entry.getValue(), parameterValues ) );
			}
			data.add( jsonObject );
		}
		builder.data( data );

		return client.upsert( builder.build() );
	}

	DeleteResp executeDelete(MilvusDelete query, Object[] parameterValues) throws SQLException {
		DeleteReq.DeleteReqBuilder builder = DeleteReq.builder();
		if ( query.getCollectionName() != null ) {
			builder.collectionName( query.getCollectionName() );
		}
		if ( query.getPartitionName() != null ) {
			builder.partitionName( query.getPartitionName() );
		}
		if ( query.getIds() != null ) {
			builder.ids( determineIdValues( query.getIds(), parameterValues ) );
		}
		if ( query.getFilter() != null ) {
			builder.filter( query.getFilter() );
		}
		if ( query.getFilterTemplateValues() != null ) {
			builder.filterTemplateValues( determineValueMap( query.getFilterTemplateValues(), parameterValues ) );
		}

		return client.delete( builder.build() );
	}

	private Map<String, Object> determineValueMap(Map<String, MilvusTypedValue> filterTemplateValues, Object[] parameterValues) throws SQLException {
		final Map<String, Object> finalValues = new LinkedHashMap<>();
		for ( Map.Entry<String, MilvusTypedValue> entry : filterTemplateValues.entrySet() ) {
			finalValues.put( entry.getKey(), determineValue( entry.getValue(), parameterValues ) );
		}
		return finalValues;
	}

	private Object determineValue(MilvusTypedValue value, Object[] parameterValues) throws SQLException {
		if ( value instanceof MilvusParameterValue parameterValue ) {
			return determineTemplateValue( parameterValues[parameterValue.position()] );
		}
		else if ( value instanceof MilvusStringValue stringValue ) {
			return stringValue.value();
		}
		else if ( value instanceof MilvusNumberValue numberValue ) {
			return numberValue.value();
		}
		else if ( value instanceof MilvusBooleanValue booleanValue ) {
			return booleanValue.value();
		}
		else if ( value instanceof MilvusFloatVectorValue floatVectorValue ) {
			final ArrayList<Object> arrayList = new ArrayList<>( floatVectorValue.value().length );
			for ( float f : floatVectorValue.value() ) {
				arrayList.add( f );
			}
			return arrayList;
		}
		else {
			throw new SQLException( "Unsupported type: " + value.getClass().getName() );
		}
	}

	private JsonElement determineJsonValue(MilvusTypedValue value, Object[] parameterValues) throws SQLException {
		if ( value instanceof MilvusParameterValue parameterValue ) {
			return determineJsonValue( parameterValues[parameterValue.position()] );
		}
		else if ( value instanceof MilvusStringValue stringValue ) {
			return new JsonPrimitive( stringValue.value() );
		}
		else if ( value instanceof MilvusNumberValue numberValue ) {
			return new JsonPrimitive( numberValue.value() );
		}
		else if ( value instanceof MilvusBooleanValue booleanValue ) {
			return new JsonPrimitive( booleanValue.value() );
		}
		else if ( value instanceof MilvusFloatVectorValue floatVectorValue ) {
			final JsonArray jsonArray = new JsonArray( floatVectorValue.value().length );
			for ( float f : floatVectorValue.value() ) {
				jsonArray.add( f );
			}
			return jsonArray;
		}
		else {
			throw new SQLException( "Unsupported type: " + value.getClass().getName() );
		}
	}

	private JsonElement determineJsonValue(Object parameterValue) throws SQLException {
		if ( parameterValue == null ) {
			return JsonNull.INSTANCE;
		}
		else if ( parameterValue instanceof String string ) {
			return new JsonPrimitive( string );
		}
		else if ( parameterValue instanceof Number number ) {
			return new JsonPrimitive( number );
		}
		else if ( parameterValue instanceof Boolean bool ) {
			return new JsonPrimitive( bool );
		}
		else if ( parameterValue instanceof MilvusArray array ) {
			final Object[] objects = (Object[]) array.getArray();
			final JsonArray jsonArray = new JsonArray( objects.length );
			switch ( array.getBaseDataType() ) {
				case VarChar -> {
					for ( Object o : objects ) {
						jsonArray.add( (String) o );
					}
				}
				case Bool -> {
					for ( Object o : objects ) {
						jsonArray.add( (Boolean) o );
					}
				}
				case Float, Double, Int8, Int16, Int32, Int64 -> {
					for ( Object o : objects ) {
						jsonArray.add( (Number) o );
					}
				}
				default -> throw new SQLException( "Unsupported vector type: " + array.getArray().getClass().getName() );
			}
			return jsonArray;
		}
		else if ( parameterValue instanceof boolean[] booleans ) {
			final JsonArray array = new JsonArray( booleans.length );
			for ( boolean b : booleans ) {
				array.add( new JsonPrimitive( b ) );
			}
			return array;
		}
		else if ( parameterValue instanceof byte[] bytes ) {
			final JsonArray array = new JsonArray( bytes.length );
			for ( byte b : bytes ) {
				array.add( new JsonPrimitive( b ) );
			}
			return array;
		}
		else if ( parameterValue instanceof short[] shorts ) {
			final JsonArray array = new JsonArray( shorts.length );
			for ( short s : shorts ) {
				array.add( new JsonPrimitive( s ) );
			}
			return array;
		}
		else if ( parameterValue instanceof int[] ints ) {
			final JsonArray array = new JsonArray( ints.length );
			for ( int i : ints ) {
				array.add( new JsonPrimitive( i ) );
			}
			return array;
		}
		else if ( parameterValue instanceof long[] longs ) {
			final JsonArray array = new JsonArray( longs.length );
			for ( long l : longs ) {
				array.add( new JsonPrimitive( l ) );
			}
			return array;
		}
		else if ( parameterValue instanceof float[] floats ) {
			final JsonArray array = new JsonArray( floats.length );
			for ( float f : floats ) {
				array.add( new JsonPrimitive( f ) );
			}
			return array;
		}
		else if ( parameterValue instanceof double[] doubles ) {
			final JsonArray array = new JsonArray( doubles.length );
			for ( double d : doubles ) {
				array.add( new JsonPrimitive( d ) );
			}
			return array;
		}
		else if ( parameterValue instanceof char[] chars ) {
			return new JsonPrimitive( new String( chars ) );
		}
		else if ( parameterValue instanceof Object[] objects ) {
			final JsonArray array = new JsonArray( objects.length );
			for ( Object o : objects ) {
				array.add( determineJsonValue( o ) );
			}
			return array;
		}
		else {
			throw new SQLException( "Unsupported parameter type: " + parameterValue.getClass().getName() );
		}
	}

	private Object determineTemplateValue(Object parameterValue) throws SQLException {
		if ( parameterValue == null
			|| parameterValue instanceof Boolean
			|| parameterValue instanceof String
			|| parameterValue instanceof Integer
			|| parameterValue instanceof Long
			|| parameterValue instanceof Float
			|| parameterValue instanceof Double ) {
			return parameterValue;
		}
		else if ( parameterValue instanceof Byte number ) {
			return number.intValue();
		}
		else if ( parameterValue instanceof Short number ) {
			return number.intValue();
		}
		else if ( parameterValue instanceof Number number ) {
			return number.doubleValue();
		}
		else if ( parameterValue instanceof MilvusArray array ) {
			return switch ( array.getBaseDataType() ) {
				case Float, VarChar, Bool, Double, Int8, Int16, Int32, Int64 -> Arrays.asList( (Object[]) array.getArray() );
				default -> throw new SQLException( "Unsupported vector type: " + array.getArray().getClass().getName() );
			};
		}
		else if ( parameterValue instanceof boolean[] booleans ) {
			final ArrayList<Object> arrayList = new ArrayList<>( booleans.length );
			for ( boolean b : booleans ) {
				arrayList.add( b );
			}
			return arrayList;
		}
		else if ( parameterValue instanceof byte[] bytes ) {
			final ArrayList<Object> arrayList = new ArrayList<>( bytes.length );
			for ( byte b : bytes ) {
				arrayList.add( (int) b );
			}
			return arrayList;
		}
		else if ( parameterValue instanceof short[] shorts ) {
			final ArrayList<Object> arrayList = new ArrayList<>( shorts.length );
			for ( short s : shorts ) {
				arrayList.add( (int) s );
			}
			return arrayList;
		}
		else if ( parameterValue instanceof int[] ints ) {
			final ArrayList<Object> arrayList = new ArrayList<>( ints.length );
			for ( int i : ints ) {
				arrayList.add( i );
			}
			return arrayList;
		}
		else if ( parameterValue instanceof long[] longs ) {
			final ArrayList<Object> arrayList = new ArrayList<>( longs.length );
			for ( long l : longs ) {
				arrayList.add( l );
			}
			return arrayList;
		}
		else if ( parameterValue instanceof float[] floats ) {
			final ArrayList<Object> arrayList = new ArrayList<>( floats.length );
			for ( float f : floats ) {
				arrayList.add( f );
			}
			return arrayList;
		}
		else if ( parameterValue instanceof double[] doubles ) {
			final ArrayList<Object> arrayList = new ArrayList<>( doubles.length );
			for ( double d : doubles ) {
				arrayList.add( d );
			}
			return arrayList;
		}
		else if ( parameterValue instanceof char[] chars ) {
			return new String( chars );
		}
		else if ( parameterValue instanceof Object[] objects ) {
			final ArrayList<Object> arrayList = new ArrayList<>( objects.length );
			for ( Object o : objects ) {
				arrayList.add( determineTemplateValue( o ) );
			}
			return arrayList;
		}
		else {
			throw new SQLException( "Unsupported parameter type: " + parameterValue.getClass().getName() );
		}
	}

	SearchResp executeSearch(MilvusSearch query, Object[] parameterValues) throws SQLException {
		SearchReq.SearchReqBuilder builder = SearchReq.builder();
		if ( query.getCollectionName() != null ) {
			builder.collectionName( query.getCollectionName() );
		}
		if ( query.getPartitionNames() != null ) {
			builder.partitionNames( query.getPartitionNames() );
		}
		if ( query.getOutputFields() != null ) {
			builder.outputFields( query.getOutputFields() );
		}
		if ( query.getFilter() != null ) {
			builder.filter( query.getFilter() );
		}
		if ( query.getConsistencyLevel() != null ) {
			builder.consistencyLevel( query.getConsistencyLevel() );
		}
		builder.offset( query.getOffset() );
		builder.limit( query.getLimit() );
		if ( query.getFilterTemplateValues() != null ) {
			builder.filterTemplateValues( determineValueMap( query.getFilterTemplateValues(), parameterValues ) );
		}

		// Search specific
		if ( query.getAnnsField() != null ) {
			// When an annsField is given, we do vector search, but we have to remove "distance" from the output fields
			if ( query.getOutputFields() != null && query.getOutputFields().contains( MilvusHelper.DISTANCE_FIELD ) ) {
				final ArrayList<String> outputFields = new ArrayList<>( query.getOutputFields().size() - 1 );
				for ( String outputField : query.getOutputFields() ) {
					if ( !MilvusHelper.DISTANCE_FIELD.equals( outputField ) ) {
						outputFields.add( outputField );
					}
				}
				builder.outputFields( outputFields );
			}
			builder.annsField( query.getAnnsField() );
		}
		if ( query.getMetricType() != null ) {
			builder.metricType( query.getMetricType() );
		}
		if ( query.getTopK() != 0 ) {
			builder.topK( query.getTopK() );
		}
		if ( query.getRoundDecimal() != 0 ) {
			builder.roundDecimal( query.getRoundDecimal() );
		}
		if ( query.getSearchParams() != null ) {
			builder.searchParams( determineValueMap( query.getSearchParams(), parameterValues ) );
		}
		if ( query.getData() != null ) {
			builder.data( determineVectors( query.getData(), parameterValues ) );
		}
		if ( query.getGuaranteeTimestamp() != 0L ) {
			builder.guaranteeTimestamp( query.getGuaranteeTimestamp() );
		}
		if ( query.getGracefulTime() != null ) {
			builder.gracefulTime( query.getGracefulTime() );
		}
		if ( query.isIgnoreGrowing() ) {
			builder.ignoreGrowing( query.isIgnoreGrowing() );
		}
		if ( query.getGroupByFieldName() != null ) {
			builder.groupByFieldName( query.getGroupByFieldName() );
		}
		if ( query.getGroupSize() != null ) {
			builder.groupSize( query.getGroupSize() );
		}
		if ( query.getStrictGroupSize() != null ) {
			builder.strictGroupSize( query.getStrictGroupSize() );
		}

		return client.search( builder.build() );
	}

	private static List<BaseVector> determineVectors(List<MilvusTypedValue> data, Object[] parameterValues) throws SQLException {
		final List<BaseVector> vectors = new ArrayList<>( data.size() );
		for ( MilvusTypedValue value : data ) {
			final BaseVector vector;
			if ( value instanceof MilvusParameterValue parameter ) {
				final Object parameterValue = parameterValues[parameter.position()];
				if ( parameterValue instanceof float[] floats ) {
					vector = new FloatVec( floats );
				}
				else if ( parameterValue instanceof byte[] bytes ) {
					vector = new BinaryVec( bytes );
				}
				else if ( parameterValue instanceof double[] doubles ) {
					final ArrayList<Float> vectorData = new ArrayList<>( doubles.length );
					for ( double d : doubles ) {
						vectorData.add( (float) d );
					}
					vector = new FloatVec( vectorData );
				}
				else if ( parameterValue instanceof MilvusArray array ) {
					vector = switch ( array.getBaseDataType() ) {
						case Float -> new FloatVec( Arrays.asList( (Float[]) array.getArray() ) );
						case BinaryVector -> new BinaryVec( (byte[]) array.getArray() );
						default -> throw new SQLException( "Unsupported vector type: " + array.getArray().getClass().getName() );
					};
				}
				else {
					throw new SQLException( "Unsupported vector type: " + parameterValue.getClass().getName() );
				}
			}
			else if ( value instanceof MilvusFloatVectorValue floatVectorValue ) {
				vector = new FloatVec( floatVectorValue.value() );
			}
			else {
				throw new SQLException( "Unsupported vector type: " + value.getClass().getName() );
			}

			vectors.add( vector );
		}
		return vectors;
	}

	SearchResp executeHybridSearch(MilvusHybridSearch query, Object[] parameterValues) throws SQLException {
		final HybridSearchReq.HybridSearchReqBuilder builder = HybridSearchReq.builder();
		if ( query.getCollectionName() != null ) {
			builder.collectionName( query.getCollectionName() );
		}
		if ( query.getPartitionNames() != null ) {
			builder.partitionNames( query.getPartitionNames() );
		}
		if ( query.getSearches() != null ) {
			final String filter = query.getFilter() == null
					? null
					: inlineValues( query.getFilter(), query.getFilterTemplateValues(), parameterValues );
			final ArrayList<AnnSearchReq> searchRequests = new ArrayList<>( query.getSearches().size() );
			for ( MilvusHybridAnnSearch search : query.getSearches() ) {
				final AnnSearchReq.AnnSearchReqBuilder reqBuilder = AnnSearchReq.builder();
				if ( search.getAnnsField() != null ) {
					reqBuilder.vectorFieldName( search.getAnnsField() );
				}
				if ( filter != null ) {
					reqBuilder.expr( filter );
				}
				if ( search.getTopK() != 0 ) {
					reqBuilder.topK( search.getTopK() );
				}
				if ( search.getSearchParams() != null ) {
					reqBuilder.params( JsonUtils.toJson( determineValueMap( search.getSearchParams(), parameterValues ) ) );
				}
				if ( search.getData() != null ) {
					reqBuilder.vectors( determineVectors( search.getData(), parameterValues ) );
				}
				if ( search.getMetricType() != null ) {
					reqBuilder.metricType( search.getMetricType() );
				}

				searchRequests.add( reqBuilder.build() );
			}
			builder.searchRequests( searchRequests );
		}
		if ( query.getRanker() != null ) {
			if ( query.getRanker() instanceof MilvusRRFRanker rrfRanker ) {
				builder.ranker( new RRFRanker( rrfRanker.k() ) );
			}
			else if ( query.getRanker() instanceof MilvusWeightedRanker weightedRanker ) {
				builder.ranker( new WeightedRanker( weightedRanker.weights() ) );
			}
			else {
				throw new SQLException( "Unsupported ranker type: " + query.getRanker().getClass().getName() );
			}
		}
		if ( query.getLimit() != 0L ) {
			builder.topK( (int) query.getLimit() );
		}
		if ( query.getOutputFields() != null ) {
			builder.outFields( query.getOutputFields() );
		}
		if ( query.getConsistencyLevel() != null ) {
			builder.consistencyLevel( query.getConsistencyLevel() );
		}
		if ( query.getRoundDecimal() != 0 ) {
			builder.roundDecimal( query.getRoundDecimal() );
		}
		if ( query.getGroupByFieldName() != null ) {
			builder.groupByFieldName( query.getGroupByFieldName() );
		}
		if ( query.getGroupSize() != null ) {
			builder.groupSize( query.getGroupSize() );
		}
		if ( query.getStrictGroupSize() != null ) {
			builder.strictGroupSize( query.getStrictGroupSize() );
		}

		return client.hybridSearch( builder.build() );
	}

	private String inlineValues(String filter, Map<String, MilvusTypedValue> filterTemplateValues, Object[] parameterValues) throws SQLException {
		if ( filter == null || filter.isEmpty() || filterTemplateValues == null || filterTemplateValues.isEmpty() ) {
			return filter;
		}
		final Map<String, Object> valueMap = determineValueMap( filterTemplateValues, parameterValues );
		for ( Map.Entry<String, Object> entry : valueMap.entrySet() ) {
			final String searchPattern = "{"+ entry.getKey() + "}";
			if ( !filter.contains( searchPattern ) ) {
				continue;
			}
			final Object value = entry.getValue();
			if ( value instanceof Boolean bool ) {
				filter = filter.replace( searchPattern, bool.toString() );
			}
			else if ( value instanceof Number number ) {
				filter = filter.replace( searchPattern, number.toString() );
			}
			else if ( value instanceof String string ) {
				filter = filter.replace( searchPattern, doubleQuoteEscapedString( string ) );
			}
			else {
				throw new SQLException( "Unsupported value type: " + value.getClass().getName() );
			}
		}

		return filter;
	}

	private String doubleQuoteEscapedString(String string) {
		final StringBuilder sb = new StringBuilder(string.length());
		QuotingHelper.appendDoubleQuoteEscapedString( sb, string );
		return sb.toString();
	}

	QueryResp executeQuery(MilvusQuery query, Object[] parameterValues) throws SQLException {
		QueryReq.QueryReqBuilder builder = QueryReq.builder();
		if ( query.getCollectionName() != null ) {
			builder.collectionName( query.getCollectionName() );
		}
		if ( query.getPartitionNames() != null ) {
			builder.partitionNames( query.getPartitionNames() );
		}
		if ( query.getOutputFields() != null ) {
			builder.outputFields( query.getOutputFields() );
		}
		if ( query.getFilter() != null ) {
			builder.filter( query.getFilter() );
			if ( query.getFilterTemplateValues() != null ) {
				builder.filterTemplateValues( determineValueMap( query.getFilterTemplateValues(), parameterValues ) );
			}
		}
		if ( query.getConsistencyLevel() != null ) {
			builder.consistencyLevel( query.getConsistencyLevel() );
		}
		builder.offset( query.getOffset() );
		builder.limit( query.getLimit() );

		// Query specific
		if ( query.getIds() != null ) {
			builder.ids( determineIdValues( query.getIds(), parameterValues ) );
		}

		return client.query( builder.build() );
	}

	@NotNull
	private ArrayList<Object> determineIdValues(List<MilvusTypedValue> ids, Object[] parameterValues) throws SQLException {
		final ArrayList<Object> arrayList = new ArrayList<>( ids.size() );
		for ( MilvusTypedValue value : ids ) {
			if ( value  instanceof MilvusParameterValue parameterValue ) {
				final Object idValue = determineTemplateValue( parameterValues[parameterValue.position()] );
				if ( idValue instanceof List<?> idValues ) {
					arrayList.addAll( idValues );
				}
				else {
					arrayList.add( idValue );
				}
			}
			else if ( value instanceof MilvusStringValue stringValue ) {
				arrayList.add( stringValue.value() );
			}
			else if ( value instanceof MilvusNumberValue numberValue ) {
				arrayList.add( numberValue.value() );
			}
			else if ( value instanceof MilvusBooleanValue booleanValue ) {
				arrayList.add( booleanValue.value() );
			}
			else {
				throw new SQLException( "Unsupported type: " + value.getClass().getName() );
			}
		}
		return arrayList;
	}

	void checkClosed() throws SQLException {
		if ( isClosed() ) {
			throw new SQLException("Already closed");
		}
	}

	@Override
	public Statement createStatement() throws SQLException {
		checkClosed();
		return new MilvusStatement( this );
	}

	@Override
	public PreparedStatement prepareStatement(String sql) throws SQLException {
		checkClosed();
		return new MilvusPreparedStatement( this, sql );
	}

	@Override
	public CallableStatement prepareCall(String sql) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
		checkClosed();
		return createStatement();
	}

	@Override
	public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency)
			throws SQLException {
		checkClosed();
		return prepareStatement( sql );
	}

	@Override
	public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability)
			throws SQLException {
		checkClosed();
		return createStatement();
	}

	@Override
	public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
			throws SQLException {
		checkClosed();
		return prepareStatement( sql );
	}

	@Override
	public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
			throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
		checkClosed();
		return prepareStatement( sql );
	}

	@Override
	public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
		checkClosed();
		return prepareStatement( sql );
	}

	@Override
	public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
		checkClosed();
		return prepareStatement( sql );
	}

	@Override
	public String nativeSQL(String sql) throws SQLException {
		checkClosed();
		return sql;
	}

	@Override
	public void setAutoCommit(boolean autoCommit) throws SQLException {
		checkClosed();
		// todo (milvus): figure out how to deal with auto-commit
//		if ( !autoCommit ) {
//			throw new SQLException( "Cannot set auto-commit to false" );
//		}
	}

	@Override
	public boolean getAutoCommit() throws SQLException {
		checkClosed();
		return true;
	}

	@Override
	public void commit() throws SQLException {
		checkClosed();
	}

	@Override
	public void rollback() throws SQLException {
		checkClosed();
	}

	@Override
	public void close() {
		if ( isClosed() ) {
			return;
		}
		client.close();
	}

	@Override
	public boolean isClosed() {
		return !client.clientIsReady();
	}

	@Override
	public DatabaseMetaData getMetaData() throws SQLException {
		checkClosed();
		return new MilvusDatabaseMetaData( this );
	}

	@Override
	public void setReadOnly(boolean readOnly) throws SQLException {
		checkClosed();
	}

	@Override
	public boolean isReadOnly() throws SQLException {
		checkClosed();
		return false;
	}

	@Override
	public void setCatalog(String catalog) throws SQLException {
		checkClosed();
		// todo (milvus): use this to configure database?
	}

	@Override
	public String getCatalog() throws SQLException {
		checkClosed();
		return "";
	}

	@Override
	public void setSchema(String schema) throws SQLException {
		checkClosed();
		// todo (milvus): use this to configure database?
	}

	@Override
	public String getSchema() throws SQLException {
		checkClosed();
		return "";
	}

	@Override
	public void setTransactionIsolation(int level) throws SQLException {
		checkClosed();
	}

	@Override
	public int getTransactionIsolation() throws SQLException {
		checkClosed();
		return Connection.TRANSACTION_NONE;
	}

	@Override
	public SQLWarning getWarnings() throws SQLException {
		checkClosed();
		return null;
	}

	@Override
	public void clearWarnings() throws SQLException {
		checkClosed();
	}

	@Override
	public Map<String, Class<?>> getTypeMap() throws SQLException {
		checkClosed();
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
		checkClosed();
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void setHoldability(int holdability) throws SQLException {
		checkClosed();
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public int getHoldability() throws SQLException {
		checkClosed();
		return ResultSet.HOLD_CURSORS_OVER_COMMIT;
	}

	@Override
	public Savepoint setSavepoint() throws SQLException {
		checkClosed();
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public Savepoint setSavepoint(String name) throws SQLException {
		checkClosed();
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void rollback(Savepoint savepoint) throws SQLException {
		checkClosed();
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void releaseSavepoint(Savepoint savepoint) throws SQLException {
		checkClosed();
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public Clob createClob() throws SQLException {
		checkClosed();
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public Blob createBlob() throws SQLException {
		checkClosed();
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public NClob createNClob() throws SQLException {
		checkClosed();
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public SQLXML createSQLXML() throws SQLException {
		checkClosed();
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public boolean isValid(int timeout) throws SQLException {
		checkClosed();
		return client.clientIsReady();
	}

	@Override
	public void setClientInfo(String name, String value) throws SQLClientInfoException {
		throw new SQLClientInfoException();
	}

	@Override
	public void setClientInfo(Properties properties) throws SQLClientInfoException {
		throw new SQLClientInfoException();
	}

	@Override
	public String getClientInfo(String name) throws SQLException {
		checkClosed();
		return "";
	}

	@Override
	public Properties getClientInfo() throws SQLException {
		checkClosed();
		return null;
	}

	@Override
	public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
		checkClosed();
		return new MilvusArray( typeName, elements );
	}

	@Override
	public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
		checkClosed();
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void abort(Executor executor) throws SQLException {
		if ( executor == null ) {
			throw new SQLException("Executor is null");
		}
		if ( isClosed() ) {
			return;
		}
		executor.execute( this::close );
	}

	@Override
	public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
		checkClosed();
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public int getNetworkTimeout() throws SQLException {
		checkClosed();
		return 0;
	}

	@Override
	public <T> T unwrap(Class<T> iface) throws SQLException {
		return iface.cast( this );
	}

	@Override
	public boolean isWrapperFor(Class<?> iface) throws SQLException {
		return iface.isInstance( this );
	}
}
