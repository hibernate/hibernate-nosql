/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.jdbc;

import io.milvus.v2.common.DataType;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.sql.Types;

import static org.hibernate.internal.util.NullnessUtil.castNonNull;

public final class MilvusHelper {

	public static final String DISTANCE_FIELD = "<>";

	private MilvusHelper() {
	}

	public static DataType determineType(String sqlType) {
		return switch ( sqlType ) {
			case "string", "varchar" -> DataType.VarChar;
			case "boolean" -> DataType.Bool;
			case "tinyint" -> DataType.Int8;
			case "smallint" -> DataType.Int16;
			case "integer" -> DataType.Int32;
			case "bigint" -> DataType.Int64;
			case "float", "real" -> DataType.Float;
			case "double precision" -> DataType.Double;
			case "json" -> DataType.JSON;
			case "binary_vector" -> DataType.BinaryVector;
			case "float_vector" -> DataType.FloatVector;
			case "float16_vector" -> DataType.Float16Vector;
			case "bfloat16_vector" -> DataType.BFloat16Vector;
			case "sparse_float_vector" -> DataType.SparseFloatVector;
			default -> throw new IllegalArgumentException( "Unsupported sql type: " + sqlType );
		};
	}

	public static String toSqlType(DataType dataType, @Nullable DataType elementType) {
		return switch ( dataType ) {
			case Array -> toSqlType( castNonNull( elementType ) , null ) + " array";
			case VarChar -> "varchar";
			case Bool -> "boolean";
			case Int8 ->"tinyint";
			case Int16 ->"smallint";
			case Int32 ->"integer";
			case Int64 ->"bigint";
			case Float -> "float";
			case Double -> "double precision";
			case JSON -> "json";
			case BinaryVector -> "binary_vector";
			case FloatVector -> "float_vector";
			case Float16Vector -> "float16_vector";
			case BFloat16Vector -> "bfloat16_vector";
			case SparseFloatVector -> "sparse_float_vector";
			default -> throw new IllegalArgumentException( "Unsupported data type: " + dataType );
		};
	}

	public static String toJavaType(DataType dataType) {
		return switch ( dataType ) {
			case Array -> "java.sql.Array";
			case VarChar -> "java.lang.String";
			case Bool -> "java.lang.Boolean";
			case Int8 ->"java.lang.Byte";
			case Int16 ->"java.lang.Short";
			case Int32 ->"java.lang.Integer";
			case Int64 ->"java.lang.Long";
			case Float -> "java.lang.Float";
			case Double -> "java.lang.Double";
			case JSON -> "java.lang.Object";
			case BinaryVector, Float16Vector, BFloat16Vector -> "[B";
			case FloatVector, SparseFloatVector -> "[F";
			default -> throw new IllegalArgumentException( "Unsupported data type: " + dataType );
		};
	}

	public static int toJdbcType(DataType dataType) {
		return switch ( dataType ) {
			case Array, FloatVector, SparseFloatVector -> Types.ARRAY;
			case VarChar -> Types.VARCHAR;
			case Bool -> Types.BOOLEAN;
			case Int8 -> Types.TINYINT;
			case Int16 -> Types.SMALLINT;
			case Int32 -> Types.INTEGER;
			case Int64 -> Types.BIGINT;
			case Float -> Types.FLOAT;
			case Double -> Types.DOUBLE;
			case JSON -> Types.OTHER;
			case BinaryVector, Float16Vector, BFloat16Vector -> Types.BINARY;
			default -> throw new IllegalArgumentException( "Unsupported data type: " + dataType );
		};
	}
}
