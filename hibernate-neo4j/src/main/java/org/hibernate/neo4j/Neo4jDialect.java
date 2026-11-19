/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.neo4j;

import org.hibernate.neo4j.internal.Neo4jTableDelete;
import org.hibernate.sql.ast.spi.model.TableDeleteStandard;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.hibernate.dialect.array.spi.ArraySupport;
import org.hibernate.dialect.lock.spi.LockingClauseRequest;
import org.hibernate.dialect.schema.spi.TruncateMode;
import org.hibernate.dialect.schema.spi.TruncateRequest;
import org.hibernate.dialect.sql.ast.spi.MutationKind;
import org.hibernate.dialect.sql.ast.spi.MutationSyntaxCapability;
import org.hibernate.dialect.sql.ast.spi.MutationSyntaxSupport;
import org.hibernate.dialect.sql.ast.spi.SqlAstTranslationRequest;
import org.hibernate.dialect.temporaltype.spi.CurrentTemporalSupport;
import jakarta.persistence.TemporalType;
import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.TypeContributions;
import org.hibernate.dialect.DatabaseVersion;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.sql.ast.spi.DmlTargetColumnQualifierSupport;
import org.hibernate.dialect.lock.spi.RowLockStrategy;
import org.hibernate.dialect.function.CommonFunctionFactory;
import org.hibernate.dialect.lock.PessimisticLockStyle;
import org.hibernate.dialect.lock.internal.LockingSupportSimple;
import org.hibernate.dialect.lock.spi.ConnectionLockTimeoutStrategy;
import org.hibernate.dialect.lock.spi.LockTimeoutType;
import org.hibernate.dialect.lock.spi.LockingSupport;
import org.hibernate.dialect.lock.spi.OuterJoinLockingType;
import org.hibernate.dialect.unique.spi.UniqueDelegate;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.exception.LockTimeoutException;
import org.hibernate.exception.spi.SQLExceptionConversionDelegate;
import org.hibernate.mapping.Table;
import org.hibernate.neo4j.internal.InetAsVarcharJdbcType;
import org.hibernate.neo4j.internal.IsoDateTimeUtils;
import org.hibernate.neo4j.internal.Neo4jArrayJdbcType;
import org.hibernate.neo4j.internal.Neo4jDecimalJdbcType;
import org.hibernate.neo4j.internal.Neo4jDurationJdbcType;
import org.hibernate.neo4j.internal.Neo4jNumericJdbcType;
import org.hibernate.neo4j.internal.Neo4jServerConfiguration;
import org.hibernate.neo4j.internal.Neo4jSqlAstTranslator;
import org.hibernate.neo4j.internal.Neo4jTableExporter;
import org.hibernate.neo4j.internal.Neo4jUniqueDelegate;
import org.hibernate.neo4j.internal.Neo4jVectorDdlType;
import org.hibernate.neo4j.internal.Neo4jVectorDistanceFunction;
import org.hibernate.neo4j.internal.Neo4jVectorFunction;
import org.hibernate.neo4j.internal.Neo4jVectorJdbcType;
import org.hibernate.neo4j.internal.TimeUtcAsOffsetTimeJdbcType;
import org.hibernate.neo4j.internal.UuidAsVarcharJdbcType;
import org.hibernate.query.sqm.CastType;
import org.hibernate.query.sqm.function.SqmFunctionRegistry;
import org.hibernate.query.sqm.produce.function.FunctionParameterType;
import org.hibernate.query.sqm.produce.function.StandardFunctionArgumentTypeResolvers;
import org.hibernate.query.sqm.produce.function.StandardFunctionReturnTypeResolvers;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.sql.ast.spi.translation.SqlAstNodeRenderingMode;
import org.hibernate.sql.ast.spi.translation.SqlAstTranslator;
import org.hibernate.dialect.sql.ast.spi.SqlAstTranslatorFactory;
import org.hibernate.sql.spi.SqlAppender;
import org.hibernate.dialect.sql.ast.spi.StandardSqlAstTranslatorFactory;
import org.hibernate.sql.ast.spi.Statement;
import org.hibernate.sql.exec.spi.JdbcOperation;
import org.hibernate.tool.schema.spi.Exporter;
import org.hibernate.type.BasicArrayType;
import org.hibernate.type.BasicType;
import org.hibernate.type.BasicTypeRegistry;
import org.hibernate.type.StandardBasicTypes;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.java.spi.JavaTypeRegistry;
import org.hibernate.type.descriptor.jdbc.ArrayJdbcType;
import org.hibernate.type.descriptor.jdbc.BlobJdbcType;
import org.hibernate.type.descriptor.jdbc.ClobJdbcType;
import org.hibernate.type.descriptor.jdbc.NClobJdbcType;
import org.hibernate.type.descriptor.jdbc.spi.JdbcTypeRegistry;
import org.hibernate.type.descriptor.sql.internal.DdlTypeImpl;
import org.hibernate.type.descriptor.sql.spi.DdlTypeRegistry;
import org.hibernate.type.spi.TypeConfiguration;
import org.hibernate.vector.internal.VectorFunctionFactory;
import org.neo4j.jdbc.internal.shaded.bolt.exception.BoltFailureException;

import java.sql.SQLException;
import java.sql.Types;
import java.time.temporal.TemporalAccessor;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;

import static org.hibernate.jdbc.spi.JdbcExceptionHelper.extractSqlState;
import static org.hibernate.type.SqlTypes.BIGINT;
import static org.hibernate.type.SqlTypes.BOOLEAN;
import static org.hibernate.type.SqlTypes.CHAR;
import static org.hibernate.type.SqlTypes.CLOB;
import static org.hibernate.type.SqlTypes.DATE;
import static org.hibernate.type.SqlTypes.DECIMAL;
import static org.hibernate.type.SqlTypes.DOUBLE;
import static org.hibernate.type.SqlTypes.DURATION;
import static org.hibernate.type.SqlTypes.FLOAT;
import static org.hibernate.type.SqlTypes.INTEGER;
import static org.hibernate.type.SqlTypes.LONG32NVARCHAR;
import static org.hibernate.type.SqlTypes.LONG32VARCHAR;
import static org.hibernate.type.SqlTypes.NCHAR;
import static org.hibernate.type.SqlTypes.NCLOB;
import static org.hibernate.type.SqlTypes.NUMERIC;
import static org.hibernate.type.SqlTypes.NVARCHAR;
import static org.hibernate.type.SqlTypes.REAL;
import static org.hibernate.type.SqlTypes.SMALLINT;
import static org.hibernate.type.SqlTypes.TIME;
import static org.hibernate.type.SqlTypes.TIMESTAMP;
import static org.hibernate.type.SqlTypes.TIMESTAMP_UTC;
import static org.hibernate.type.SqlTypes.TIMESTAMP_WITH_TIMEZONE;
import static org.hibernate.type.SqlTypes.TIME_UTC;
import static org.hibernate.type.SqlTypes.TIME_WITH_TIMEZONE;
import static org.hibernate.type.SqlTypes.TINYINT;
import static org.hibernate.type.SqlTypes.VARCHAR;
import static org.hibernate.type.SqlTypes.VECTOR;
import static org.hibernate.type.SqlTypes.VECTOR_FLOAT32;
import static org.hibernate.type.SqlTypes.VECTOR_FLOAT64;
import static org.hibernate.type.SqlTypes.VECTOR_INT8;
import static org.hibernate.type.descriptor.DateTimeUtils.appendAsDate;
import static org.hibernate.type.descriptor.DateTimeUtils.appendAsLocalTime;
import static org.hibernate.type.descriptor.DateTimeUtils.appendAsTime;

/**
 * An SQL dialect for Neo4j.
 */
public class Neo4jDialect extends Dialect {

	private static final DatabaseVersion MINIMUM_VERSION = DatabaseVersion.make( 5, 26 );
	private static final LockingSupport LOCKING_SUPPORT = new LockingSupportSimple(
			PessimisticLockStyle.CLAUSE,
			RowLockStrategy.TABLE,
			LockTimeoutType.NONE,
			OuterJoinLockingType.FULL,
			ConnectionLockTimeoutStrategy.NONE
	) {
		@Override
		public String render(LockingClauseRequest request) {
			final StringBuilder sb = new StringBuilder();
			final Set<String> aliases = new LinkedHashSet<>();
			for ( LockingClauseRequest.Target target : request.targets() ) {
				if ( target instanceof LockingClauseRequest.TableTarget tableTarget ) {
					aliases.add( tableTarget.tableAlias() );
				}
				else if ( target instanceof LockingClauseRequest.ColumnTarget columnTarget ) {
					aliases.add( columnTarget.tableAlias() );
				}
			}
			for ( String alias : aliases ) {
				sb.append( " set " )
						.append( alias )
						.append( "._dummy_=true remove " )
						.append( alias )
						.append( "._dummy_" );
			}
			return sb.toString();
		}
	};

	private final UniqueDelegate uniqueDelegate = new Neo4jUniqueDelegate( this );

	private final boolean enterpriseEdition;

	@SuppressWarnings("unused")
	public Neo4jDialect() {
		this( MINIMUM_VERSION );
	}

	public Neo4jDialect(DatabaseVersion version) {
		super( version );
		this.enterpriseEdition = false;
	}

	@SuppressWarnings("unused")
	public Neo4jDialect(DialectResolutionInfo info) {
		this( info, Neo4jServerConfiguration.fromDialectResolutionInfo( info ) );
	}

	public Neo4jDialect(DialectResolutionInfo info, Neo4jServerConfiguration serverConfiguration) {
		super( serverConfiguration.getDatabaseVersion() );
		this.enterpriseEdition = serverConfiguration.isEnterpriseEdition();
	}

	@Override
	public void contributeTypes(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
		super.contributeTypes( typeContributions, serviceRegistry );

		final TypeConfiguration typeConfiguration = typeContributions.getTypeConfiguration();
		final JavaTypeRegistry javaTypeRegistry = typeConfiguration.getJavaTypeRegistry();
		final JdbcTypeRegistry jdbcTypeRegistry = typeConfiguration.getJdbcTypeRegistry();
		final DdlTypeRegistry ddlTypeRegistry = typeConfiguration.getDdlTypeRegistry();
		final BasicTypeRegistry basicTypeRegistry = typeConfiguration.getBasicTypeRegistry();

		// No BLOB/CLOB/NCLOB support on the Neo4j JDBC driver
		jdbcTypeRegistry.addDescriptor( Types.BLOB, BlobJdbcType.MATERIALIZED );
		jdbcTypeRegistry.addDescriptor( Types.CLOB, ClobJdbcType.MATERIALIZED );
		jdbcTypeRegistry.addDescriptor( Types.NCLOB, NClobJdbcType.MATERIALIZED );

		// generic array type constructor
		jdbcTypeRegistry.addTypeConstructor( Neo4jArrayJdbcType.Neo4jArrayJdbcTypeConstructor.INSTANCE );

		// vector type support
		final boolean nativeVectorSupport = isEnterpriseEdition();
		final ArrayJdbcType genericVectorJdbcType = new Neo4jVectorJdbcType( jdbcTypeRegistry.getDescriptor( FLOAT ), nativeVectorSupport, VECTOR );
		jdbcTypeRegistry.addDescriptor( genericVectorJdbcType );
		final ArrayJdbcType floatVectorJdbcType = new Neo4jVectorJdbcType( jdbcTypeRegistry.getDescriptor( FLOAT ), nativeVectorSupport, VECTOR_FLOAT32 );
		jdbcTypeRegistry.addDescriptor( floatVectorJdbcType );
		final ArrayJdbcType doubleVectorJdbcType = new Neo4jVectorJdbcType( jdbcTypeRegistry.getDescriptor( DOUBLE ), nativeVectorSupport, VECTOR_FLOAT64 );
		jdbcTypeRegistry.addDescriptor( doubleVectorJdbcType );
		final ArrayJdbcType byteVectorJdbcType = new Neo4jVectorJdbcType( jdbcTypeRegistry.getDescriptor( TINYINT ), nativeVectorSupport, VECTOR_INT8 );
		jdbcTypeRegistry.addDescriptor( byteVectorJdbcType );

		final BasicType<Float> floatBasicType = basicTypeRegistry.resolve( StandardBasicTypes.FLOAT );
		final BasicType<Byte> byteBasicType = basicTypeRegistry.resolve( StandardBasicTypes.BYTE );
		final JavaType<float[]> floatArrayJavaType = javaTypeRegistry.resolveDescriptor( float[].class );
		final JavaType<byte[]> byteArrayJavaType = javaTypeRegistry.resolveDescriptor( byte[].class );

		basicTypeRegistry.register(
				new BasicArrayType<>(
						floatBasicType,
						genericVectorJdbcType,
						floatArrayJavaType
				),
				StandardBasicTypes.VECTOR.getName()
		);
		basicTypeRegistry.register(
				new BasicArrayType<>(
						floatBasicType,
						floatVectorJdbcType,
						floatArrayJavaType
				),
				StandardBasicTypes.VECTOR_FLOAT32.getName()
		);
		basicTypeRegistry.register(
				new BasicArrayType<>(
						byteBasicType,
						byteVectorJdbcType,
						byteArrayJavaType
				),
				StandardBasicTypes.VECTOR_INT8.getName()
		);
		if ( nativeVectorSupport ) {
			ddlTypeRegistry.addDescriptor(
					new Neo4jVectorDdlType( VECTOR, "vector<float32>($a)", this )
			);
			ddlTypeRegistry.addDescriptor(
					new Neo4jVectorDdlType( VECTOR_FLOAT32, "vector<float32>($a)", this )
			);
			ddlTypeRegistry.addDescriptor(
					new Neo4jVectorDdlType( VECTOR_FLOAT64, "vector<float64>($a)", this )
			);
			ddlTypeRegistry.addDescriptor(
					new Neo4jVectorDdlType( VECTOR_INT8, "vector<integer8>($a)", this )
			);
		}
		else {
			ddlTypeRegistry.addDescriptor(
					new DdlTypeImpl( VECTOR, "list<integer | float>", this )
			);
			ddlTypeRegistry.addDescriptor(
					new DdlTypeImpl( VECTOR_FLOAT32, "list<float32>", this )
			);
			ddlTypeRegistry.addDescriptor(
					new DdlTypeImpl( VECTOR_FLOAT64, "list<float64>", this )
			);
			ddlTypeRegistry.addDescriptor(
					new DdlTypeImpl( VECTOR_INT8, "list<integer8>", this )
			);
		}

		// todo neo4j : json type support ?

		jdbcTypeRegistry.addDescriptor( TimeUtcAsOffsetTimeJdbcType.INSTANCE );
		jdbcTypeRegistry.addDescriptor( Neo4jDecimalJdbcType.INSTANCE );
		jdbcTypeRegistry.addDescriptor( Neo4jNumericJdbcType.INSTANCE );
		jdbcTypeRegistry.addDescriptor( Neo4jDurationJdbcType.INSTANCE );
		// Since arrays of (byte) arrays are not supported on Neo4j...
		// Encode UUID and INET as Varchar to allow handling array
		jdbcTypeRegistry.addDescriptor( UuidAsVarcharJdbcType.INSTANCE );
		jdbcTypeRegistry.addDescriptor( InetAsVarcharJdbcType.INSTANCE );
	}

	public boolean isEnterpriseEdition() {
		return enterpriseEdition;
	}

	@Override
	public void initializeFunctionRegistry(FunctionContributions functionContributions) {
		super.initializeFunctionRegistry( functionContributions );

		final TypeConfiguration typeConfiguration = functionContributions.getTypeConfiguration();
		final BasicTypeRegistry basicTypeRegistry = typeConfiguration.getBasicTypeRegistry();
		final SqmFunctionRegistry functionRegistry = functionContributions.getFunctionRegistry();
		final BasicType<Double> doubleType = basicTypeRegistry.resolve( StandardBasicTypes.DOUBLE );
		final CommonFunctionFactory functionFactory = new CommonFunctionFactory( functionContributions );

		// aggregating functions https://neo4j.com/docs/cypher-manual/current/functions/aggregating/
		functionRegistry.namedAggregateDescriptorBuilder( "percentileCont" )
				.setArgumentRenderingMode( SqlAstNodeRenderingMode.DEFAULT )
				.setExactArgumentCount( 2 )
				.setParameterTypes( FunctionParameterType.NUMERIC, FunctionParameterType.NUMERIC )
				.setReturnTypeResolver( StandardFunctionReturnTypeResolvers.invariant( doubleType ) )
				.register();
		functionRegistry.registerAlternateKey( "percentile_cont", "percentileCont" );
		functionRegistry.namedAggregateDescriptorBuilder( "percentileDisc" )
				.setArgumentRenderingMode( SqlAstNodeRenderingMode.DEFAULT )
				.setExactArgumentCount( 2 )
				.setParameterTypes( FunctionParameterType.NUMERIC, FunctionParameterType.NUMERIC )
				.setArgumentTypeResolver( StandardFunctionArgumentTypeResolvers.ARGUMENT_OR_IMPLIED_RESULT_TYPE )
				.setReturnTypeResolver( StandardFunctionReturnTypeResolvers.invariant( doubleType ) )
				.register();
		functionRegistry.registerAlternateKey( "percentile_disc", "percentileDisc" );
		functionRegistry.namedAggregateDescriptorBuilder( "stDev" )
				.setArgumentRenderingMode( SqlAstNodeRenderingMode.DEFAULT )
				.setExactArgumentCount( 1 )
				.setParameterTypes( FunctionParameterType.NUMERIC )
				.setReturnTypeResolver( StandardFunctionReturnTypeResolvers.invariant( doubleType ) )
				.register();
		functionRegistry.namedAggregateDescriptorBuilder( "stDevP" )
				.setArgumentRenderingMode( SqlAstNodeRenderingMode.DEFAULT )
				.setExactArgumentCount( 1 )
				.setParameterTypes( FunctionParameterType.NUMERIC )
				.setReturnTypeResolver( StandardFunctionReturnTypeResolvers.invariant( doubleType ) )
				.register();

		// mathematical functions https://neo4j.com/docs/cypher-manual/current/functions/mathematical-logarithmic/
		functionFactory.ln_log();
		functionFactory.log_loglog();
		functionFactory.log10();
		functionFactory.concat_pipeOperator();

		if ( getVersion().isSameOrAfter( 2025, 10 ) ) {
			final VectorFunctionFactory vectorFunctionFactory = new VectorFunctionFactory( functionContributions );
			final BasicType<Integer> integerType = basicTypeRegistry.resolve( StandardBasicTypes.INTEGER );
			final boolean nativeVectorSupport = isEnterpriseEdition();

			if ( nativeVectorSupport ) {
				vectorFunctionFactory.cosineDistance( "vector_distance(?1,?2,COSINE)" );
				vectorFunctionFactory.euclideanDistance( "vector_distance(?1,?2,EUCLIDEAN)" );
				vectorFunctionFactory.euclideanSquaredDistance( "vector_distance(?1,?2,EUCLIDEAN_SQUARED)" );
				vectorFunctionFactory.l1Distance( "vector_distance(?1,?2,MANHATTAN)" );
				vectorFunctionFactory.hammingDistance( "vector_distance(?1,?2,HAMMING)" );

				vectorFunctionFactory.innerProduct( "vector_distance(?1,?2,DOT)*-1" );
				vectorFunctionFactory.negativeInnerProduct( "vector_distance(?1,?2,DOT)" );

				vectorFunctionFactory.registerNamedVectorFunction( "vector_dimension_count", integerType, 1 );
				functionContributions.getFunctionRegistry().registerAlternateKey( "vector_dims", "vector_dimension_count" );
				vectorFunctionFactory.registerPatternVectorFunction( "vector_norm", "vector_norm(?1,EUCLIDEAN)", doubleType, 1 );
			}
			else {
				functionRegistry.register( "cosine_distance", new Neo4jVectorDistanceFunction( "cosine", typeConfiguration ) );
				functionRegistry.register( "euclidean_distance", new Neo4jVectorDistanceFunction( "euclidean", typeConfiguration ) );
				functionRegistry.registerAlternateKey( "l2_distance", "euclidean_distance" );
				functionRegistry.register( "euclidean_squared_distance", new Neo4jVectorDistanceFunction( "euclidean_squared", typeConfiguration ) );
				functionRegistry.registerAlternateKey( "l2_squared_distance", "euclidean_squared_distance" );
				functionRegistry.register( "l1_distance", new Neo4jVectorDistanceFunction( "manhattan", typeConfiguration ) );
				functionRegistry.registerAlternateKey( "taxicab_distance", "l1_distance" );
				functionRegistry.register( "hamming_distance", new Neo4jVectorDistanceFunction( "hamming", typeConfiguration ) );

				functionRegistry.register( "inner_product", new Neo4jVectorDistanceFunction( "dot", "*-1", typeConfiguration ) );
				functionRegistry.register( "negative_inner_product", new Neo4jVectorDistanceFunction( "dot", typeConfiguration ) );

				functionRegistry.register( "vector_dimension_count", new Neo4jVectorFunction( "vector_dimension_count", integerType ) );
				functionContributions.getFunctionRegistry().registerAlternateKey( "vector_dims", "vector_dimension_count" );
				functionRegistry.register( "vector_norm", new Neo4jVectorFunction( "vector_norm", doubleType, ",euclidean" ) );
			}

		}

		// todo neo4j : a lot of functions are added by the apoc plugin https://neo4j.com/labs/apoc/
		//  would be great to detect it at startup and register them here
	}

	@Override
	public int getMaxIdentifierLength() {
		return 255;
	}

	@Override
	public String getArrayTypeName(String javaElementTypeName, String elementTypeName, Integer maxLength) {
		return "list<" + elementTypeName + ">";
	}

	@Override
	protected String columnType(int sqlTypeCode) {
		// https://neo4j.com/docs/cypher-manual/current/values-and-types/property-structural-constructed/#types-synonyms
		return switch ( sqlTypeCode ) {
			case CHAR, VARCHAR, NCHAR, NVARCHAR, CLOB, NCLOB, LONG32VARCHAR, LONG32NVARCHAR -> "string";
			case BOOLEAN -> "boolean";
			case TINYINT, SMALLINT, INTEGER, BIGINT -> "integer";
			case FLOAT, REAL, DOUBLE, NUMERIC, DECIMAL -> "float";

			case DATE -> "date";
			case TIME, TIME_UTC -> "local time";
			case TIME_WITH_TIMEZONE -> "zoned time";
			case TIMESTAMP, TIMESTAMP_UTC -> "local datetime";
			case TIMESTAMP_WITH_TIMEZONE -> "zoned datetime";
			case DURATION -> "duration";

			default -> super.columnType( sqlTypeCode );
		};
	}

	@Override
	public void appendLiteral(SqlAppender appender, String literal) {
		appender.appendDoubleQuoteEscapedString( literal );
	}

	@Override
	public Exporter<Table> getTableExporter() {
		return Neo4jTableExporter.INSTANCE;
	}

	@Override
	public LockingSupport getLockingSupport() {
		return LOCKING_SUPPORT;
	}

	@Override
	public ArraySupport getArraySupport() {
		return ArraySupport.STANDARD;
	}

	@Override
	public boolean supportsAlterTableConstraints() {
		return false;
	}

	@Override
	public MutationSyntaxSupport getMutationSyntaxSupport() {
		return MutationSyntaxSupport.of( MutationKind.UPDATE, MutationSyntaxCapability.FROM_CLAUSE );
	}

	@Override
	public TruncateMode truncateMode() {
		return TruncateMode.MULTI_TABLE;
	}

	@Override
	public List<String> renderCommands(TruncateRequest request) {
		if ( request.tableNames().isEmpty() ) {
			return List.of();
		}
		return List.of( "match(n:" + String.join( "|", request.tableNames() ) + ") detach delete n" );
	}

	@Override
	public CurrentTemporalSupport getCurrentTemporalSupport() {
		return new CurrentTemporalSupport() {
			@Override
			public String currentDate() {
				return "date()";
			}

			@Override
			public String currentTime() {
				return "localtime()";
			}

			@Override
			public String currentTimestamp() {
				return "localdatetime()";
			}

			@Override
			public String currentTimestampWithTimeZone() {
				return "datetime()";
			}
		};
	}

	@Override
	public SqlAstTranslatorFactory getSqlAstTranslatorFactory() {
		return new StandardSqlAstTranslatorFactory() {
			@Override
			protected <S extends Statement, O extends JdbcOperation> SqlAstTranslator<O> createTranslator(
					SqlAstTranslationRequest<S, O> request) {
				if ( request instanceof SqlAstTranslationRequest.ModelMutation<?> modelMutation
						&& modelMutation.statement() instanceof TableDeleteStandard tableDelete ) {
					// The core translator has no extension point to render standard deletes
					@SuppressWarnings({"unchecked", "rawtypes"})
					final SqlAstTranslationRequest<S, O> deleteRequest = (SqlAstTranslationRequest) new SqlAstTranslationRequest.ModelMutation<>(
							modelMutation.sessionFactory(),
							new Neo4jTableDelete( tableDelete )
					);
					return new Neo4jSqlAstTranslator<>( deleteRequest );
				}
				return new Neo4jSqlAstTranslator<>( request );
			}
		};
	}

	@Override
	public SQLExceptionConversionDelegate buildSQLExceptionConversionDelegate() {
		return (sqlException, message, sql) -> {
			if ( sqlException.getCause() instanceof BoltFailureException boltException ) {
				switch ( boltException.code() ) {
					case "Neo.TransientError.Transaction.LockAcquisitionTimeout":
						return new LockTimeoutException( message, sqlException, sql );
				}
			}

			return null;
		};
	}

	@Override
	public boolean causesRollback(SQLException sqlException) {
		return "25N16".equals( extractSqlState( sqlException ) );
	}

	@Override
	public DmlTargetColumnQualifierSupport getDmlTargetColumnQualifierSupport() {
		return DmlTargetColumnQualifierSupport.TABLE_ALIAS;
	}

	@Override
	public UniqueDelegate getUniqueDelegate() {
		return uniqueDelegate;
	}

	@Override
	public boolean supportsOrdinalSelectItemReference() {
		return false;
	}

	@Override
	public String castPattern(CastType from, CastType to) {
		return switch ( to ) {
			case JSON:
			case XML:
			case CLOB:
			case STRING:
				switch ( from ) {
					case INTEGER_BOOLEAN:
						yield "case ?1 when 1 then 'true' when 0 then 'false' else null end";
					case YN_BOOLEAN:
						yield "case ?1 when 'Y' then 'true' when 'N' then 'false' else null end";
					case TF_BOOLEAN:
						yield "case ?1 when 'T' then 'true' when 'F' then 'false' else null end";
				}
				yield "toString(?1)";
			case INTEGER:
			case LONG:
				switch ( from ) {
					case YN_BOOLEAN:
						yield "case ?1 when 'Y' then 1 when 'N' then 0 else null end";
					case TF_BOOLEAN:
						yield "case ?1 when 'T' then 1 when 'F' then 0 else null end";
					case BOOLEAN:
						yield "case ?1 when true then 1 when false then 0 else null end";
				}
				yield "toInteger(?1)";
			case INTEGER_BOOLEAN:
				switch ( from ) {
					case INTEGER:
					case LONG:
						yield "abs(sign(?1))";
					case YN_BOOLEAN:
						yield "case ?1 when 'Y' then 1 when 'N' then 0 else null end";
					case TF_BOOLEAN:
						yield "case ?1 when 'T' then 1 when 'F' then 0 else null end";
					case BOOLEAN:
						yield "case ?1 when true then 1 when false then 0 else null end";
				}
			case YN_BOOLEAN:
				switch ( from ) {
					case INTEGER_BOOLEAN:
						yield "case ?1 when 1 then 'Y' when 0 then 'N' else null end";
					case INTEGER:
					case LONG:
						yield "case abs(sign(?1)) when 1 then 'Y' when 0 then 'N' else null end";
					case TF_BOOLEAN:
						yield "case ?1 when 'T' then 'Y' when 'F' then 'N' else null end";
					case BOOLEAN:
						yield "case ?1 when true then 'Y' when false then 'N' else null end";
				}
			case TF_BOOLEAN:
				switch ( from ) {
					case INTEGER_BOOLEAN:
						yield "case ?1 when 1 then 'T' when 0 then 'F' else null end";
					case INTEGER:
					case LONG:
						yield "case abs(sign(?1)) when 1 then 'T' when 0 then 'F' else null end";
					case YN_BOOLEAN:
						yield "case ?1 when 'Y' then 'T' when 'N' then 'F' else null end";
					case BOOLEAN:
						yield "case ?1 when true then 'T' when false then 'F' else null end";
				}
			case BOOLEAN:
				switch ( from ) {
					case INTEGER_BOOLEAN:
					case INTEGER:
					case LONG:
						yield "(?1<>0)";
					case YN_BOOLEAN:
						yield "(?1<>'N')";
					case TF_BOOLEAN:
						yield "(?1<>'F')";
				}
				yield "toBoolean(?1)";
			case FLOAT:
			case DOUBLE:
			case FIXED:
				yield "toFloat(?1)";
			default:
				throw new IllegalArgumentException(
						"Unsupported cast from " + from + " to " + to
				);
		};
	}

	@Override
	public void appendBinaryLiteral(SqlAppender appender, byte[] bytes) {
		throw new UnsupportedOperationException("Neo4j does not support binary literals");
	}

	@Override
	public void appendUUIDLiteral(SqlAppender appender, UUID literal) {
		throw new UnsupportedOperationException("Neo4j does not support UUID literals");
	}

	@Override
	public void appendDateTimeLiteral(
			SqlAppender appender,
			TemporalAccessor temporalAccessor,
			@SuppressWarnings("deprecation")
			TemporalType precision,
			TimeZone jdbcTimeZone) {
		switch ( precision ) {
			case DATE:
				appender.appendSql( "date(\"" );
				appendAsDate( appender, temporalAccessor );
				appender.appendSql( "\")" );
				break;
			case TIME:
				appender.appendSql( "local_time(\"" );
				appendAsTime( appender, temporalAccessor, false, jdbcTimeZone );
				appender.appendSql( "\")" );
				break;
			case TIMESTAMP:
				appender.appendSql( "datetime(\"" );
				IsoDateTimeUtils.appendAsTimestampWithNanos( appender, temporalAccessor, false, jdbcTimeZone );
				appender.appendSql( "\")" );
				break;
			default:
				throw new IllegalArgumentException();
		}
	}

	@Override
	public void appendDateTimeLiteral(
			SqlAppender appender,
			Date date,
			@SuppressWarnings("deprecation")
			TemporalType precision,
			TimeZone jdbcTimeZone) {
		switch ( precision ) {
			case DATE:
				appender.appendSql( "date(\"" );
				appendAsDate( appender, date );
				appender.appendSql( "\")" );
				break;
			case TIME:
				appender.appendSql( "local_time(\"" );
				appendAsLocalTime( appender, date );
				appender.appendSql( "\")" );
				break;
			case TIMESTAMP:
				appender.appendSql( "datetime(\"" );
				IsoDateTimeUtils.appendAsTimestampWithNanos( appender, date, jdbcTimeZone );
				appender.appendSql( "\")" );
				break;
			default:
				throw new IllegalArgumentException();
		}
	}

	@Override
	public void appendDateTimeLiteral(
			SqlAppender appender,
			Calendar calendar,
			@SuppressWarnings("deprecation")
			TemporalType precision,
			TimeZone jdbcTimeZone) {
		switch ( precision ) {
			case DATE:
				appender.appendSql( "date(\"" );
				appendAsDate( appender, calendar );
				appender.appendSql( "\")" );
				break;
			case TIME:
				appender.appendSql( "local_time(\"" );
				appendAsLocalTime( appender, calendar );
				appender.appendSql( "\")" );
				break;
			case TIMESTAMP:
				appender.appendSql( "datetime(\"" );
				IsoDateTimeUtils.appendAsTimestampWithMillis( appender, calendar, jdbcTimeZone );
				appender.appendSql( "\")" );
				break;
			default:
				throw new IllegalArgumentException();
		}
	}
}
