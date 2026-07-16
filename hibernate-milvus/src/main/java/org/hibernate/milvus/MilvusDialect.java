/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.hibernate.LockOptions;
import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.TypeContributions;
import org.hibernate.dialect.DatabaseVersion;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.SimpleDatabaseVersion;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.engine.jdbc.env.spi.IdentifierCaseStrategy;
import org.hibernate.engine.jdbc.env.spi.IdentifierHelper;
import org.hibernate.engine.jdbc.env.spi.IdentifierHelperBuilder;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.mapping.Table;
import org.hibernate.query.sqm.function.SqmFunctionRegistry;
import org.hibernate.query.sqm.produce.function.StandardArgumentsValidators;
import org.hibernate.query.sqm.produce.function.StandardFunctionReturnTypeResolvers;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.SqlAstTranslatorFactory;
import org.hibernate.sql.ast.spi.LockingClauseStrategy;
import org.hibernate.sql.ast.spi.SqlAppender;
import org.hibernate.sql.ast.spi.StandardSqlAstTranslatorFactory;
import org.hibernate.sql.ast.tree.Statement;
import org.hibernate.sql.ast.tree.select.QuerySpec;
import org.hibernate.sql.exec.spi.JdbcOperation;
import org.hibernate.tool.schema.spi.Exporter;
import org.hibernate.type.BasicArrayType;
import org.hibernate.type.BasicType;
import org.hibernate.type.BasicTypeRegistry;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.StandardBasicTypes;
import org.hibernate.type.descriptor.java.spi.JavaTypeRegistry;
import org.hibernate.type.descriptor.jdbc.ArrayJdbcType;
import org.hibernate.type.descriptor.jdbc.spi.JdbcTypeRegistry;
import org.hibernate.type.descriptor.sql.internal.DdlTypeImpl;
import org.hibernate.type.descriptor.sql.spi.DdlTypeRegistry;
import org.hibernate.type.spi.TypeConfiguration;
import org.hibernate.vector.internal.VectorArgumentTypeResolver;
import org.hibernate.vector.internal.VectorArgumentValidator;

import java.lang.reflect.Type;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Integer.parseInt;
import static org.hibernate.sql.ast.internal.NonLockingClauseStrategy.NON_CLAUSE_STRATEGY;
import static org.hibernate.type.SqlTypes.CHAR;
import static org.hibernate.type.SqlTypes.CLOB;
import static org.hibernate.type.SqlTypes.NCHAR;
import static org.hibernate.type.SqlTypes.NCLOB;
import static org.hibernate.type.SqlTypes.NVARCHAR;
import static org.hibernate.type.SqlTypes.VARCHAR;

/**
 * An SQL dialect for Milvus.
 */
public class MilvusDialect extends Dialect {

	private static final Pattern VERSION_PATTERN = Pattern.compile( "[\\d]+\\.[\\d]+\\.[\\d]+" );
	private static final DatabaseVersion MINIMUM_VERSION = DatabaseVersion.make( 2, 5 );
	private static final Type[] VECTOR_JAVA_TYPES = {
			Float[].class,
			float[].class
	};
	private static final Type[] BINARY_VECTOR_JAVA_TYPES = {
			Byte[].class,
			byte[].class
	};

	@SuppressWarnings("unused")
	public MilvusDialect() {
		this( MINIMUM_VERSION );
	}

	public MilvusDialect(DialectResolutionInfo info) {
		this( determineVersion( info ) );
		registerKeywords( info );
	}

	public MilvusDialect(DatabaseVersion version) {
		super( version );
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
			final String[] versionParts = StringHelper.split( ".", versionString );
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
	public void contributeTypes(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
		super.contributeTypes( typeContributions, serviceRegistry );

		final TypeConfiguration typeConfiguration = typeContributions.getTypeConfiguration();
		final JavaTypeRegistry javaTypeRegistry = typeConfiguration.getJavaTypeRegistry();
		final JdbcTypeRegistry jdbcTypeRegistry = typeConfiguration.getJdbcTypeRegistry();
		final DdlTypeRegistry ddlTypeRegistry = typeConfiguration.getDdlTypeRegistry();
		final BasicTypeRegistry basicTypeRegistry = typeConfiguration.getBasicTypeRegistry();
		final BasicType<Float> floatBasicType = basicTypeRegistry.resolve( StandardBasicTypes.FLOAT );
		final BasicType<Byte> byteBasicType = basicTypeRegistry.resolve( StandardBasicTypes.BYTE );
		final ArrayJdbcType vectorJdbcType = new MilvusVectorJdbcType( jdbcTypeRegistry.getDescriptor( SqlTypes.FLOAT ) );
		final ArrayJdbcType binaryVectorJdbcType = new MilvusBinaryVectorJdbcType( jdbcTypeRegistry.getDescriptor( SqlTypes.TINYINT ) );
		jdbcTypeRegistry.addDescriptor( SqlTypes.VECTOR, vectorJdbcType );
		jdbcTypeRegistry.addDescriptor( SqlTypes.VECTOR_INT8, binaryVectorJdbcType );
		for ( Type vectorJavaType : VECTOR_JAVA_TYPES ) {
			basicTypeRegistry.register(
					new BasicArrayType<>(
							floatBasicType,
							vectorJdbcType,
							javaTypeRegistry.getDescriptor( vectorJavaType )
					),
					StandardBasicTypes.VECTOR.getName()
			);
		}
		for ( Type vectorJavaType : BINARY_VECTOR_JAVA_TYPES ) {
			basicTypeRegistry.register(
					new BasicArrayType<>(
							byteBasicType,
							binaryVectorJdbcType,
							javaTypeRegistry.getDescriptor( vectorJavaType )
					),
					StandardBasicTypes.VECTOR_INT8.getName()
			);
		}
		ddlTypeRegistry.addDescriptor(
				new DdlTypeImpl( SqlTypes.VECTOR, "float_vector", this )
		);
		ddlTypeRegistry.addDescriptor(
				new DdlTypeImpl( SqlTypes.VECTOR_INT8, "binary_vector", this )
		);
		// todo (milvus): json type?
	}

	@Override
	public void initializeFunctionRegistry(FunctionContributions functionContributions) {
		super.initializeFunctionRegistry( functionContributions );
		final BasicTypeRegistry basicTypeRegistry = functionContributions.getTypeConfiguration().getBasicTypeRegistry();
		final SqmFunctionRegistry functionRegistry = functionContributions.getFunctionRegistry();
		final BasicType<Double> doubleType = basicTypeRegistry.resolve( StandardBasicTypes.DOUBLE );
		registerVectorDistanceFunction( functionRegistry, "cosine_distance", doubleType );
		registerVectorDistanceFunction( functionRegistry, "euclidean_distance", doubleType );
		functionRegistry.registerAlternateKey( "l2_distance", "euclidean_distance" );

//		registerVectorDistanceFunction( functionRegistry, "l1_distance", doubleType );
//		functionRegistry.registerAlternateKey( "taxicab_distance", "l1_distance" );

//		registerVectorDistanceFunction( functionRegistry, "negative_inner_product", doubleType );
		registerVectorDistanceFunction( functionRegistry, "inner_product", doubleType );
		registerVectorDistanceFunction( functionRegistry, "hamming_distance", doubleType );

		// todo (milvus): array and json functions?
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
	public IdentifierHelper buildIdentifierHelper(IdentifierHelperBuilder builder, @Nullable DatabaseMetaData metadata)
			throws SQLException {
		builder.setUnquotedCaseStrategy( IdentifierCaseStrategy.MIXED );
		return super.buildIdentifierHelper( builder, metadata );
	}

	@Override
	public int getMaxIdentifierLength() {
		return 255;
	}

	@Override
	public boolean supportsStandardArrays() {
		return true;
	}

	@Override
	protected String columnType(int sqlTypeCode) {
		return switch ( sqlTypeCode ) {
			case CHAR,VARCHAR, NCHAR, NVARCHAR, CLOB, NCLOB -> "varchar";
			default -> super.columnType( sqlTypeCode );
		};
	}

	@Override
	public void appendLiteral(SqlAppender appender, String literal) {
		appender.appendDoubleQuoteEscapedString( literal );
	}

	@Override
	public Exporter<Table> getTableExporter() {
		return MilvusTableExporter.INSTANCE;
	}

	@Override
	public SqlAstTranslatorFactory getSqlAstTranslatorFactory() {
		return new StandardSqlAstTranslatorFactory() {
			@Override
			protected <T extends JdbcOperation> SqlAstTranslator<T> buildTranslator(
					SessionFactoryImplementor sessionFactory, Statement statement) {
				return new MilvusSqlAstTranslator<>( sessionFactory, statement );
			}
		};
	}
}
