/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.jdbc.internal;

import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractMilvusResultSet extends AbstractResultSet<MilvusStatement> {

	final String collectionName;
	final List<String> fields;

	private CreateCollectionReq.CollectionSchema collectionSchema;
	private MilvusResultSetMetaData resultSetMetaData;

	public AbstractMilvusResultSet(MilvusStatement statement, String collectionName, @Nullable List<String> fields) {
		super(statement);
		this.collectionName = collectionName;
		if ( fields == null ) {
			final CreateCollectionReq.CollectionSchema schema = getCollectionSchema( statement, collectionName );
			final List<CreateCollectionReq.FieldSchema> fieldSchemas = schema.getFieldSchemaList();
			final ArrayList<String> fetchedFields = new ArrayList<>( fieldSchemas.size() );
			for ( CreateCollectionReq.FieldSchema fieldSchema : fieldSchemas ) {
				fetchedFields.add( fieldSchema.getName() );
			}
			this.fields = fetchedFields;
			this.resultSetMetaData = new MilvusResultSetMetaData( collectionName, schema.getFieldSchemaList() );
		}
		else {
			this.fields = fields;
		}
	}

	CreateCollectionReq.CollectionSchema getCollectionSchema(MilvusStatement statement, String collectionName) {
		if ( collectionSchema == null ) {
			final DescribeCollectionResp response = statement.connection.client
					.describeCollection( DescribeCollectionReq.builder().collectionName( collectionName ).build() );
			collectionSchema = response.getCollectionSchema();
		}
		return collectionSchema;
	}

	@Override
	public void close() throws SQLException {
		resultSetMetaData = null;
		super.close();
	}

	@Override
	public ResultSetMetaData getMetaData() throws SQLException {
		checkClosed();
		if (resultSetMetaData == null) {
			final var fieldSchemaList = getCollectionSchema( statement, collectionName )
					.getFieldSchemaList();
			final var resultSetMetadataFields = new ArrayList<CreateCollectionReq.FieldSchema>( fields.size() );
			for ( String field : fields ) {
				CreateCollectionReq.FieldSchema foundFieldSchema = null;
				for ( CreateCollectionReq.FieldSchema fieldSchema : fieldSchemaList ) {
					if ( fieldSchema.getName().equals( field ) ) {
						foundFieldSchema = fieldSchema;
						break;
					}
				}
				if ( foundFieldSchema == null ) {
					throw new IllegalStateException( "Could not find field schema for field: " + field );
				}
				resultSetMetadataFields.add( foundFieldSchema );
			}
			resultSetMetaData = new MilvusResultSetMetaData( collectionName, resultSetMetadataFields );
		}
		return resultSetMetaData;
	}

	@Override
	public int findColumn(String columnLabel) throws SQLException {
		checkClosed();
		return fields.indexOf( columnLabel ) + 1;
	}

	protected int getColumnIndex(String columnLabel) throws SQLException {
		checkClosed();
		int index = fields.indexOf( columnLabel );
		if ( index == -1 ) {
			throw new SQLException("Column not found: " + columnLabel);
		}
		return index;
	}

	protected abstract Object getField(int position, String field);

	@Override
	protected Object getValue(int columnIndex) throws SQLException {
		checkIndex( columnIndex, fields.size() );
		wasNull = false;
		String field = fields.get( columnIndex - 1 );
		return getField( position, field );
	}

	@Override
	protected Object getValue(String columnLabel) throws SQLException {
		int index = getColumnIndex( columnLabel );
		wasNull = false;
		String field = fields.get( index );
		return getField( position, field );
	}
}
