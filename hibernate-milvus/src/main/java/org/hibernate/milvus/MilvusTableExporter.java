/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus;

import io.milvus.v2.common.DataType;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.model.relational.SqlStringGenerationContext;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Table;
import org.hibernate.milvus.jdbc.MilvusCreateCollection;
import org.hibernate.milvus.jdbc.MilvusDropCollection;
import org.hibernate.milvus.jdbc.MilvusHelper;
import org.hibernate.milvus.jdbc.MilvusJsonHelper;
import org.hibernate.tool.schema.spi.Exporter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MilvusTableExporter implements Exporter<Table> {

	public static final MilvusTableExporter INSTANCE = new MilvusTableExporter();

	@Override
	public String[] getSqlCreateStrings(Table exportable, Metadata metadata, SqlStringGenerationContext context) {
		final String collectionName = exportable.getQualifiedTableName().getTableName().toString();
		final Collection<Column> columns = exportable.getColumns();

		final List<MilvusCreateCollection.FieldSchema> fields = new ArrayList<>( columns.size() );
		boolean hasVector = false;
		for ( Column column : columns ) {
			final boolean primaryKey = exportable.isPrimaryKey( column );
			final String sqlType = column.getSqlType( metadata );
			final DataType dataType;
			final DataType elementType;
			final Integer dimension;
			if ( sqlType.endsWith( " array" ) ) {
				dataType = DataType.Array;
				elementType = MilvusHelper.determineType( sqlType );
				dimension = column.getArrayLength();
			}
			else {
				elementType = null;
				final DataType baseDataType = MilvusHelper.determineType( sqlType );
				if ( primaryKey ) {
					dataType = switch ( baseDataType ) {
						// Milvus requires Int64 for the primary key
						case Int8, Int16, Int32 -> DataType.Int64;
						default -> baseDataType;
					};
					dimension = column.getArrayLength();
				}
				else {
					dataType = baseDataType;
					hasVector = hasVector || switch ( dataType ) {
						case FloatVector, Float16Vector, BFloat16Vector, BinaryVector, SparseFloatVector -> true;
						default -> false;
					};
					dimension = switch ( dataType ) {
						case FloatVector, Float16Vector, BFloat16Vector, SparseFloatVector -> column.getArrayLength();
						case BinaryVector -> column.getArrayLength() * 8;
						default -> null;
					};
				}
			}
			final MilvusCreateCollection.FieldSchema field = new MilvusCreateCollection.FieldSchema(
					column.getName(),
					null,
					dataType,
					column.getLength() == null ? null : column.getLength().intValue(),
					dimension,
					primaryKey,
					false,
					false,
					column.isIdentity(),
					elementType,
					null,
					column.isNullable(),
					column.getDefaultValue(),
					false,
					null,
					null
			);
			fields.add( field );
		}

		if ( !hasVector && context.getDialect().getVersion().isSameOrAfter( 2, 4, 2 ) ) {
			// As of 2.4.2 a schema requires at least one vector field: https://github.com/milvus-io/milvus/issues/33853
			fields.add( new MilvusCreateCollection.FieldSchema(
					"embedding",
					null,
					DataType.FloatVector,
					null,
					2,
					false,
					false,
					false,
					false,
					null,
					null,
					false,
					null,
					false,
					null,
					null
			) );
		}

		final MilvusCreateCollection.Schema schema = new MilvusCreateCollection.Schema( fields );
		final MilvusCreateCollection collection = new MilvusCreateCollection(
				collectionName,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				schema,
				null,
				null,
				null,
				null
		);
		return new String[] {MilvusJsonHelper.serializeDefinition( collection )};
	}

	@Override
	public String[] getSqlDropStrings(Table exportable, Metadata metadata, SqlStringGenerationContext context) {
		String collectionName = exportable.getQualifiedTableName().getTableName().toString();
		MilvusDropCollection collection = new MilvusDropCollection(
				collectionName,
				null,
				null
		);
		return new String[] {MilvusJsonHelper.serializeDefinition( collection )};
	}
}
