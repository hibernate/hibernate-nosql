/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus;

import org.hibernate.milvus.jdbc.AbstractMilvusQuery;
import org.hibernate.milvus.jdbc.MilvusStatementDefinition;
import io.milvus.v2.common.ConsistencyLevel;
import java.util.Locale;
import org.hibernate.milvus.internal.MilvusTableDelete;
import org.hibernate.sql.ast.spi.model.TableDeleteStandard;
import java.util.List;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.dialect.array.spi.ArraySupport;
import org.hibernate.dialect.identifier.spi.IdentifierHelperBuildRequest;
import org.hibernate.dialect.schema.spi.TruncateMode;
import org.hibernate.dialect.schema.spi.TruncateRequest;
import org.hibernate.dialect.sql.ast.spi.SqlAstTranslationRequest;
import org.hibernate.dialect.type.spi.TypeSizingProfile;
import io.milvus.v2.common.DataType;
import jakarta.persistence.GenerationType;
import org.hibernate.LockOptions;
import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.TypeContributions;
import org.hibernate.cfg.FetchSettings;
import org.hibernate.dialect.DatabaseVersion;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.SimpleDatabaseVersion;
import org.hibernate.dialect.type.spi.TimeZoneSupport;
import org.hibernate.dialect.function.CommonFunctionFactory;
import org.hibernate.dialect.function.array.ArrayArgumentValidator;
import org.hibernate.dialect.function.array.ArrayConstructorFunction;
import org.hibernate.dialect.identity.spi.IdentityColumnSupport;
import org.hibernate.dialect.lock.internal.NoLockingSupport;
import org.hibernate.dialect.lock.spi.LockingSupport;
import org.hibernate.engine.config.spi.ConfigurationService;
import org.hibernate.engine.config.spi.StandardConverters;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.engine.jdbc.env.spi.IdentifierCaseStrategy;
import org.hibernate.engine.jdbc.env.spi.IdentifierHelper;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.mapping.ForeignKey;
import org.hibernate.mapping.Index;
import org.hibernate.mapping.Table;
import org.hibernate.milvus.internal.MilvusArrayContainsFunction;
import org.hibernate.milvus.internal.MilvusArrayGetFunction;
import org.hibernate.milvus.internal.MilvusArrayIncludesFunction;
import org.hibernate.milvus.internal.MilvusArrayIntersectsFunction;
import org.hibernate.milvus.internal.MilvusArrayJdbcType;
import org.hibernate.milvus.internal.MilvusBinaryVectorJdbcType;
import org.hibernate.milvus.internal.MilvusByteVectorJdbcType;
import org.hibernate.milvus.internal.MilvusFloat16VectorJdbcType;
import org.hibernate.milvus.internal.MilvusFloat32VectorJdbcType;
import org.hibernate.milvus.internal.MilvusForeignKeyExporter;
import org.hibernate.milvus.internal.MilvusIdentityColumnSupport;
import org.hibernate.milvus.internal.MilvusIndexExporter;
import org.hibernate.milvus.internal.MilvusJsonArrayFunction;
import org.hibernate.milvus.internal.MilvusJsonExistsFunction;
import org.hibernate.milvus.internal.MilvusJsonJdbcType;
import org.hibernate.milvus.internal.MilvusJsonQueryFunction;
import org.hibernate.milvus.internal.MilvusJsonValueFunction;
import org.hibernate.milvus.internal.MilvusSparseFloat32VectorJdbcTypeConstructor;
import org.hibernate.milvus.internal.MilvusSqlAstTranslator;
import org.hibernate.milvus.internal.MilvusTableExporter;
import org.hibernate.milvus.internal.TimeUtcAsJdbcTimeJdbcType;
import org.hibernate.milvus.internal.UuidAsVarcharJdbcType;
import org.hibernate.milvus.jdbc.MilvusJsonHelper;
import org.hibernate.milvus.jdbc.MilvusTruncateCollection;
import org.hibernate.query.sqm.function.SqmFunctionRegistry;
import org.hibernate.query.sqm.produce.function.StandardArgumentsValidators;
import org.hibernate.query.sqm.produce.function.StandardFunctionReturnTypeResolvers;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.sql.ast.spi.translation.SqlAstTranslator;
import org.hibernate.dialect.sql.ast.spi.SqlAstTranslatorFactory;
import org.hibernate.dialect.lock.spi.LockingClauseStrategy;
import org.hibernate.sql.spi.SqlAppender;
import org.hibernate.dialect.sql.ast.spi.StandardSqlAstTranslatorFactory;
import org.hibernate.sql.ast.spi.Statement;
import org.hibernate.sql.ast.spi.query.select.QuerySpec;
import org.hibernate.sql.exec.spi.JdbcOperation;
import org.hibernate.tool.schema.spi.Exporter;
import org.hibernate.type.BasicArrayType;
import org.hibernate.type.BasicType;
import org.hibernate.type.BasicTypeRegistry;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.StandardBasicTypes;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.java.spi.JavaTypeRegistry;
import org.hibernate.type.descriptor.jdbc.ArrayJdbcType;
import org.hibernate.type.descriptor.jdbc.spi.JdbcTypeRegistry;
import org.hibernate.type.descriptor.sql.internal.CapacityDependentDdlType;
import org.hibernate.type.descriptor.sql.internal.DdlTypeImpl;
import org.hibernate.type.descriptor.sql.spi.DdlTypeRegistry;
import org.hibernate.type.spi.TypeConfiguration;
import org.hibernate.vector.internal.SparseFloatVectorJavaType;
import org.hibernate.vector.internal.VectorArgumentTypeResolver;
import org.hibernate.vector.internal.VectorArgumentValidator;

import java.sql.SQLException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Integer.parseInt;
import static org.hibernate.query.sqm.produce.function.FunctionParameterType.NUMERIC;
import static org.hibernate.dialect.lock.internal.NonLockingClauseStrategy.NON_CLAUSE_STRATEGY;
import static org.hibernate.type.SqlTypes.*;
import static org.hibernate.type.SqlTypes.CHAR;
import static org.hibernate.type.SqlTypes.CLOB;
import static org.hibernate.type.SqlTypes.DATE;
import static org.hibernate.type.SqlTypes.DECIMAL;
import static org.hibernate.type.SqlTypes.FLOAT;
import static org.hibernate.type.SqlTypes.NCHAR;
import static org.hibernate.type.SqlTypes.NCLOB;
import static org.hibernate.type.SqlTypes.NVARCHAR;
import static org.hibernate.type.SqlTypes.REAL;
import static org.hibernate.type.SqlTypes.TIME;
import static org.hibernate.type.SqlTypes.TIMESTAMP;
import static org.hibernate.type.SqlTypes.TIMESTAMP_UTC;
import static org.hibernate.type.SqlTypes.TIMESTAMP_WITH_TIMEZONE;
import static org.hibernate.type.SqlTypes.TIME_WITH_TIMEZONE;
import static org.hibernate.type.SqlTypes.VARCHAR;

/**
 * An SQL dialect for Milvus.
 */
public class MilvusDialect extends Dialect {

	private static final Pattern VERSION_PATTERN = Pattern.compile( "\\d+\\.\\d+(\\.\\d+)?-.+" );
	private static final DatabaseVersion MINIMUM_VERSION = DatabaseVersion.make( 2, 5 );

	private final boolean supportsText;
	private volatile boolean addMissingVectorField;

	@SuppressWarnings("unused")
	public MilvusDialect() {
		this( MINIMUM_VERSION );
	}

	public MilvusDialect(DialectResolutionInfo info) {
		this( determineVersion( info ) );
	}

	public MilvusDialect(DatabaseVersion version) {
		super( version );
		boolean supportsText = true;
		try {
			DataType.valueOf( "Text" );
		}
		catch (IllegalArgumentException e) {
			supportsText = false;
		}
		this.supportsText = supportsText;
	}

	public boolean isAddMissingVectorField() {
		return addMissingVectorField;
	}

	@Override
	protected DatabaseVersion getMinimumSupportedVersion() {
		return MINIMUM_VERSION;
	}

	@Override
	public DatabaseVersion determineDatabaseVersion(DialectResolutionInfo info) {
		return determineVersion( info );
	}

	protected static DatabaseVersion determineVersion(DialectResolutionInfo info) {
		String versionString = null;
		if ( info.getDatabaseMetadata() != null ) {
			try {
				versionString = info.getDatabaseMetadata().getDatabaseProductVersion();
			}
			catch (SQLException ex) {
				// Ignore
			}
		}
		return versionString != null ? parseVersion( versionString ) : info.makeCopyOrDefault( MINIMUM_VERSION );
	}

	public static DatabaseVersion parseVersion(String versionString) {
		DatabaseVersion databaseVersion = null;
		final Matcher matcher = VERSION_PATTERN.matcher( versionString == null ? "" : versionString );
		if ( matcher.matches() ) {
			final int dashPosition = versionString.indexOf( '-' );
			final String plainVersion = dashPosition == -1
					? versionString
					: versionString.substring( 0, dashPosition );
			final String[] versionParts = StringHelper.split( ".", plainVersion );
			// if we got to this point, there is at least a major version, so no need to check [].length > 0
			int majorVersion = parseInt( versionParts[0] );
			int minorVersion = versionParts.length > 1 ? parseInt( versionParts[1] ) : 0;
			int microVersion = versionParts.length > 2 ? parseInt( versionParts[2] ) : 0;
			databaseVersion = new SimpleDatabaseVersion( majorVersion, minorVersion, microVersion );
		}
		if ( databaseVersion == null ) {
			databaseVersion = MINIMUM_VERSION;
		}
		return databaseVersion;
	}

	@Override
	protected void contributeDefaultProperties(java.util.Properties properties) {
		super.contributeDefaultProperties( properties );
		// This essentially disables joins in regular find queries
		properties.setProperty( FetchSettings.MAX_FETCH_DEPTH, "-1" );
	}


	@Override
	public void contributeTypes(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
		super.contributeTypes( typeContributions, serviceRegistry );
		addMissingVectorField = serviceRegistry.getService( ConfigurationService.class )
				.getSetting( MilvusSettings.ADD_MISSING_VECTOR_FIELD, StandardConverters.BOOLEAN, false );

		final TypeConfiguration typeConfiguration = typeContributions.getTypeConfiguration();
		final JavaTypeRegistry javaTypeRegistry = typeConfiguration.getJavaTypeRegistry();
		final JdbcTypeRegistry jdbcTypeRegistry = typeConfiguration.getJdbcTypeRegistry();
		final DdlTypeRegistry ddlTypeRegistry = typeConfiguration.getDdlTypeRegistry();
		final BasicTypeRegistry basicTypeRegistry = typeConfiguration.getBasicTypeRegistry();

		typeContributions.contributeJdbcType( UuidAsVarcharJdbcType.INSTANCE );
		typeContributions.contributeJdbcType( MilvusJsonJdbcType.INSTANCE );

		final ArrayJdbcType genericVectorJdbcType = new MilvusFloat32VectorJdbcType(
				jdbcTypeRegistry.getDescriptor( FLOAT ),
				VECTOR
		);
		typeContributions.contributeJdbcType( genericVectorJdbcType );
		final ArrayJdbcType floatVectorJdbcType = new MilvusFloat32VectorJdbcType(
				jdbcTypeRegistry.getDescriptor( FLOAT ),
				VECTOR_FLOAT32
		);
		typeContributions.contributeJdbcType( floatVectorJdbcType );
		final ArrayJdbcType float16VectorJdbcType = new MilvusFloat16VectorJdbcType(
				jdbcTypeRegistry.getDescriptor( FLOAT ),
				VECTOR_FLOAT16
		);
		typeContributions.contributeJdbcType( float16VectorJdbcType );
		final ArrayJdbcType byteVectorJdbcType = new MilvusByteVectorJdbcType(
				jdbcTypeRegistry.getDescriptor( TINYINT ),
				VECTOR_INT8
		);
		typeContributions.contributeJdbcType( byteVectorJdbcType );
		final ArrayJdbcType binaryVectorJdbcType = new MilvusBinaryVectorJdbcType(
				jdbcTypeRegistry.getDescriptor( TINYINT ),
				VECTOR_BINARY
		);
		typeContributions.contributeJdbcType( binaryVectorJdbcType );
		jdbcTypeRegistry.addTypeConstructor( new MilvusSparseFloat32VectorJdbcTypeConstructor() );

		javaTypeRegistry.addDescriptor( SparseFloatVectorJavaType.INSTANCE );

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
						floatBasicType,
						float16VectorJdbcType,
						floatArrayJavaType
				),
				StandardBasicTypes.VECTOR_FLOAT16.getName()
		);
		basicTypeRegistry.register(
				new BasicArrayType<>(
						byteBasicType,
						byteVectorJdbcType,
						byteArrayJavaType
				),
				StandardBasicTypes.VECTOR_INT8.getName()
		);
		basicTypeRegistry.register(
				new BasicArrayType<>(
						byteBasicType,
						binaryVectorJdbcType,
						byteArrayJavaType
				),
				StandardBasicTypes.VECTOR_BINARY.getName()
		);
		ddlTypeRegistry.addDescriptor(
				new DdlTypeImpl( VECTOR, "float_vector", this )
		);
		ddlTypeRegistry.addDescriptor(
				new DdlTypeImpl( VECTOR_FLOAT32, "float_vector", this )
		);
		ddlTypeRegistry.addDescriptor(
				new DdlTypeImpl( VECTOR_INT8, "byte_vector", this )
		);
		ddlTypeRegistry.addDescriptor(
				new DdlTypeImpl( VECTOR_BINARY, "binary_vector", this )
		);
		ddlTypeRegistry.addDescriptor(
				new DdlTypeImpl( VECTOR_FLOAT16, "float16_vector", this )
		);
		ddlTypeRegistry.addDescriptor(
				new DdlTypeImpl( SPARSE_VECTOR_FLOAT32, "sparse_float_vector", this )
		);
		ddlTypeRegistry.addDescriptor(
				new DdlTypeImpl( JSON, "json", this )
		);
		ddlTypeRegistry.addDescriptor(
				CapacityDependentDdlType.builder( FLOAT, columnType( DOUBLE ), this )
						.withTypeCapacity( 24, "float" )
						.build()
		);
		// todo (milvus): geometry type

		// todo: fix upstream, this is just temporary
		jdbcTypeRegistry.addDescriptor( TimeUtcAsJdbcTimeJdbcType.INSTANCE );

		// Since we represent binary data as hex coded string, we have to properly handle it when reading from JDBC
		jdbcTypeRegistry.addTypeConstructor( MilvusArrayJdbcType.MilvusArrayJdbcTypeConstructor.INSTANCE );
	}

	@Override
	public void initializeFunctionRegistry(FunctionContributions functionContributions) {
		super.initializeFunctionRegistry( functionContributions );
		final TypeConfiguration typeConfiguration = functionContributions.getTypeConfiguration();
		final BasicTypeRegistry basicTypeRegistry = typeConfiguration.getBasicTypeRegistry();
		final SqmFunctionRegistry functionRegistry = functionContributions.getFunctionRegistry();
		final BasicType<Integer> integerType = basicTypeRegistry.resolve( StandardBasicTypes.INTEGER );
		final BasicType<Double> doubleType = basicTypeRegistry.resolve( StandardBasicTypes.DOUBLE );
		final CommonFunctionFactory functionFactory = new CommonFunctionFactory( functionContributions );
		registerVectorDistanceFunction( functionRegistry, "cosine_distance", doubleType );
		registerVectorDistanceFunction( functionRegistry, "euclidean_distance", doubleType );
		registerVectorDistanceFunction( functionRegistry, "euclidean_squared_distance", doubleType );
		functionRegistry.registerAlternateKey( "l2_distance", "euclidean_distance" );

//		registerVectorDistanceFunction( functionRegistry, "l1_distance", doubleType );
//		functionRegistry.registerAlternateKey( "taxicab_distance", "l1_distance" );

		registerVectorDistanceFunction( functionRegistry, "negative_inner_product", doubleType );
		registerVectorDistanceFunction( functionRegistry, "inner_product", doubleType );
		registerVectorDistanceFunction( functionRegistry, "hamming_distance", doubleType );
		registerVectorDistanceFunction( functionRegistry, "jaccard_distance", doubleType );

		functionFactory.mod_operator();
		functionRegistry.patternDescriptorBuilder( "power", "(?1**?2)" )
				.setInvariantType(doubleType)
				.setExactArgumentCount( 2 )
				.setParameterTypes(NUMERIC, NUMERIC)
				.register();

		functionRegistry.register( "json_array", new MilvusJsonArrayFunction( typeConfiguration ) );
		functionRegistry.register( "json_value", new MilvusJsonValueFunction( typeConfiguration ) );
		functionRegistry.register( "json_query", new MilvusJsonQueryFunction( typeConfiguration ) );
		functionRegistry.register( "json_exists", new MilvusJsonExistsFunction( typeConfiguration ) );
		functionRegistry.register( "array", new ArrayConstructorFunction( false, false ) );
		functionRegistry.register( "array_list", new ArrayConstructorFunction( true, false ) );
		functionRegistry.namedDescriptorBuilder( "array_length" )
				.setReturnTypeResolver( StandardFunctionReturnTypeResolvers.invariant( integerType ) )
				.setArgumentsValidator(
						StandardArgumentsValidators.composite(
								StandardArgumentsValidators.exactly( 1 ),
								ArrayArgumentValidator.DEFAULT_INSTANCE
						)
				)
				.setArgumentListSignature( "(ARRAY array)" )
				.register();

		functionRegistry.register( "array_get", new MilvusArrayGetFunction() );
		functionRegistry.register( "array_contains", new MilvusArrayContainsFunction( false, typeConfiguration ) );
		functionRegistry.register( "array_contains_nullable", new MilvusArrayContainsFunction( true, typeConfiguration ) );
		functionRegistry.register( "array_includes", new MilvusArrayIncludesFunction( false, typeConfiguration ) );
		functionRegistry.register( "array_includes_nullable", new MilvusArrayIncludesFunction( true, typeConfiguration ) );
		functionRegistry.register( "array_intersects", new MilvusArrayIntersectsFunction( false, typeConfiguration ) );
		functionRegistry.register( "array_intersects_nullable", new MilvusArrayIntersectsFunction( true, typeConfiguration ) );

		// todo (milvus): geometry functions?
	}

	private void registerVectorDistanceFunction(
			SqmFunctionRegistry functionRegistry,
			String functionName,
			BasicType<?> returnType) {

		functionRegistry.namedDescriptorBuilder( functionName )
				.setArgumentsValidator( StandardArgumentsValidators.composite(
						StandardArgumentsValidators.exactly( 2 ),
						VectorArgumentValidator.INSTANCE
				) )
				.setArgumentTypeResolver( VectorArgumentTypeResolver.INSTANCE )
				.setReturnTypeResolver( StandardFunctionReturnTypeResolvers.invariant( returnType ) )
				.register();
	}

	@Override
	public LockingClauseStrategy getLockingClauseStrategy(QuerySpec querySpec, LockOptions lockOptions) {
		return NON_CLAUSE_STRATEGY;
	}



	@Override
	public int getMaxIdentifierLength() {
		return 255;
	}





	@Override
	protected String columnType(int sqlTypeCode) {
		return switch ( sqlTypeCode ) {
			case REAL -> "float";
			case FLOAT, DOUBLE -> "double";
			case CHAR, VARCHAR, NCHAR, NVARCHAR -> "varchar";
			case CLOB, NCLOB -> supportsText ? "text" : "varchar";
			// Milvus has no support for arbitrary precision numeric types
			case SqlTypes.NUMERIC, DECIMAL -> columnType( DOUBLE );
			case DATE, TIME, TIMESTAMP, TIMESTAMP_UTC, TIME_WITH_TIMEZONE, TIMESTAMP_WITH_TIMEZONE -> "timestamptz";
			// Milvus has no binary types, so use varchar instead
			case BINARY, VARBINARY -> "varchar";
			case BLOB -> supportsText ? "text" : "varchar";
			default -> super.columnType( sqlTypeCode );
		};
	}

	@Override
	public String getArrayTypeName(String javaElementTypeName, String elementTypeName, Integer maxLength) {
		return elementTypeName + " array";
	}

	@Override
	public TimeZoneSupport getTimeZoneSupport() {
		return TimeZoneSupport.NORMALIZE;
	}

	@Override
	public void appendLiteral(SqlAppender appender, String literal) {
		appender.appendDoubleQuoteEscapedString( literal );
	}

	@Override
	public void appendBinaryLiteral(SqlAppender appender, byte[] bytes) {
		throw new UnsupportedOperationException("Milvus does not support binary literals");
	}

	@Override
	public void appendUUIDLiteral(SqlAppender appender, UUID literal) {
		throw new UnsupportedOperationException("Milvus does not support UUID literals");
	}

	@Override
	public Exporter<Table> getTableExporter() {
		return MilvusTableExporter.INSTANCE;
	}

	@Override
	public Exporter<Index> getIndexExporter() {
		return MilvusIndexExporter.INSTANCE;
	}

	@Override
	public Exporter<ForeignKey> getForeignKeyExporter() {
		return MilvusForeignKeyExporter.INSTANCE;
	}

	@Override
	public IdentityColumnSupport getIdentityColumnSupport() {
		return MilvusIdentityColumnSupport.INSTANCE;
	}

	@Override
	public GenerationType getNativeValueGenerationStrategy() {
		return GenerationType.IDENTITY;
	}


	@Override
	public LockingSupport getLockingSupport() {
		return NoLockingSupport.NO_LOCKING_SUPPORT;
	}

	@Override
	public IdentifierHelper buildIdentifierHelper(IdentifierHelperBuildRequest request) {
		final var builder = request.builder();
		final var jdbcMetadata = request.jdbcMetadata();
		final var keywordSupport = request.keywordSupport();
		builder.setUnquotedCaseStrategy( IdentifierCaseStrategy.MIXED );
		builder.setQuotedCaseStrategy( jdbcMetadata.getQuotedIdentifierCaseStrategy() );
		builder.applyReservedWords( keywordSupport.getKeywords() );
		builder.applyReservedWords(
				jdbcMetadata.getSqlKeywords().stream()
						.filter( keywordSupport::acceptsJdbcKeyword )
						.toList()
		);
		builder.setNameQualifierSupport( request.nameQualifierSupport() );
		return builder.build();
	}

	@Override
	public TruncateMode truncateMode() {
		return TruncateMode.PER_TABLE;
	}

	@Override
	public List<String> renderCommands(TruncateRequest request) {
		return request.tableNames().stream()
				.map( tableName -> MilvusJsonHelper.serializeDefinition( new MilvusTruncateCollection( tableName ) ) )
				.toList();
	}

	@Override
	public TypeSizingProfile getTypeSizingProfile() {
		final int maxVarcharLength = (1 << 16) - 1;
		return TypeSizingProfile.builder( super.getTypeSizingProfile() )
				.maxVarcharLength( maxVarcharLength )
				.maxNVarcharLength( maxVarcharLength )
				.maxVarbinaryLength( maxVarcharLength )
				.build();
	}

	@Override
	public String getQueryHintString(String query, List<String> hintList) {
		if ( hintList == null || hintList.isEmpty() ) {
			return query;
		}
		final MilvusStatementDefinition definition = MilvusJsonHelper.parseDefinition( query );
		for ( String hint : hintList ) {
			final int assignmentIndex = hint.indexOf( '=' );
			if ( assignmentIndex != -1 ) {
				if ( MilvusDatabaseHints.CONSISTENCY_LEVEL.equals( hint.substring( 0, assignmentIndex ) )
					&& definition instanceof AbstractMilvusQuery milvusQuery ) {
					milvusQuery.setConsistencyLevel(
							ConsistencyLevel.valueOf( hint.substring( assignmentIndex + 1 ).toUpperCase( Locale.ROOT ) )
					);
				}
			}
		}
		return MilvusJsonHelper.serializeDefinition( definition );
	}

	@Override
	public ArraySupport getArraySupport() {
		return ArraySupport.STANDARD;
	}

	@Override
	public boolean supportsOnDeleteAction(OnDeleteAction action) {
		return action != OnDeleteAction.CASCADE && super.supportsOnDeleteAction( action );
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
							new MilvusTableDelete( tableDelete )
					);
					return new MilvusSqlAstTranslator<>( deleteRequest );
				}
				return new MilvusSqlAstTranslator<>( request );
			}
		};
	}
}
