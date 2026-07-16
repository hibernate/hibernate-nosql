/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.neo4j;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.TypeContributions;
import org.hibernate.dialect.DatabaseVersion;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.DmlTargetColumnQualifierSupport;
import org.hibernate.dialect.function.CommonFunctionFactory;
import org.hibernate.dialect.unique.UniqueDelegate;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.mapping.Table;
import org.hibernate.query.sqm.CastType;
import org.hibernate.query.sqm.function.SqmFunctionRegistry;
import org.hibernate.query.sqm.produce.function.FunctionParameterType;
import org.hibernate.query.sqm.produce.function.StandardFunctionArgumentTypeResolvers;
import org.hibernate.query.sqm.produce.function.StandardFunctionReturnTypeResolvers;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.sql.ast.SqlAstNodeRenderingMode;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.SqlAstTranslatorFactory;
import org.hibernate.sql.ast.spi.SqlAppender;
import org.hibernate.sql.ast.spi.StandardSqlAstTranslatorFactory;
import org.hibernate.sql.ast.tree.Statement;
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

/**
 * An SQL dialect for Neo4j.
 */
public class Neo4jDialect extends Dialect {

	private static final DatabaseVersion MINIMUM_VERSION = DatabaseVersion.make( 5, 0 );

	private static final Class<?>[] VECTOR_JAVA_TYPES = {
			Float[].class,
			float[].class,
			Integer[].class,
			int[].class,
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
		registerKeywords( info );
	}

	@Override
	public void contributeTypes(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
		super.contributeTypes( typeContributions, serviceRegistry );

		final TypeConfiguration typeConfiguration = typeContributions.getTypeConfiguration();
		final JavaTypeRegistry javaTypeRegistry = typeConfiguration.getJavaTypeRegistry();
		final JdbcTypeRegistry jdbcTypeRegistry = typeConfiguration.getJdbcTypeRegistry();
		final DdlTypeRegistry ddlTypeRegistry = typeConfiguration.getDdlTypeRegistry();
		final BasicTypeRegistry basicTypeRegistry = typeConfiguration.getBasicTypeRegistry();

		// generic array type constructor
		jdbcTypeRegistry.addTypeConstructor( Neo4jArrayJdbcType.Neo4jArrayJdbcTypeConstructor.INSTANCE );

		// vector type support
		final BasicType<Float> floatBasicType = basicTypeRegistry.resolve( StandardBasicTypes.FLOAT );
		final BasicType<Integer> integerBasicType = basicTypeRegistry.resolve( StandardBasicTypes.INTEGER );
		final ArrayJdbcType vectorJdbcType = new Neo4jArrayJdbcType( jdbcTypeRegistry.getDescriptor( FLOAT ) );
		for ( Class<?> vectorJavaType : VECTOR_JAVA_TYPES ) {
			final BasicType<?> basicType = vectorJavaType == float[].class || vectorJavaType == Float[].class ?
					floatBasicType :
					integerBasicType;
			basicTypeRegistry.register(
					new BasicArrayType<>(
							basicType,
							vectorJdbcType,
							javaTypeRegistry.getDescriptor( vectorJavaType )
					),
					StandardBasicTypes.VECTOR.getName()
			);
		}
		ddlTypeRegistry.addDescriptor(
				new DdlTypeImpl( SqlTypes.VECTOR, "list<integer | float>", this )
		);

		// todo neo4j : json type support ?
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
		final BasicType<Double> doubleBasicType = basicTypeRegistry.resolve( StandardBasicTypes.DOUBLE );
		final CommonFunctionFactory functionFactory = new CommonFunctionFactory( functionContributions );

		// aggregating functions https://neo4j.com/docs/cypher-manual/current/functions/aggregating/
		functionRegistry.namedAggregateDescriptorBuilder( "percentileCont" )
				.setArgumentRenderingMode( SqlAstNodeRenderingMode.DEFAULT )
				.setExactArgumentCount( 2 )
				.setParameterTypes( FunctionParameterType.NUMERIC, FunctionParameterType.NUMERIC )
				.setReturnTypeResolver( StandardFunctionReturnTypeResolvers.invariant( doubleBasicType ) )
				.register();
		functionRegistry.registerAlternateKey( "percentile_cont", "percentileCont" );
		functionRegistry.namedAggregateDescriptorBuilder( "percentileDisc" )
				.setArgumentRenderingMode( SqlAstNodeRenderingMode.DEFAULT )
				.setExactArgumentCount( 2 )
				.setParameterTypes( FunctionParameterType.NUMERIC, FunctionParameterType.NUMERIC )
				.setArgumentTypeResolver( StandardFunctionArgumentTypeResolvers.ARGUMENT_OR_IMPLIED_RESULT_TYPE )
				.setReturnTypeResolver( StandardFunctionReturnTypeResolvers.invariant( doubleBasicType ) )
				.register();
		functionRegistry.registerAlternateKey( "percentile_disc", "percentileDisc" );
		functionRegistry.namedAggregateDescriptorBuilder( "stDev" )
				.setArgumentRenderingMode( SqlAstNodeRenderingMode.DEFAULT )
				.setExactArgumentCount( 1 )
				.setParameterTypes( FunctionParameterType.NUMERIC )
				.setReturnTypeResolver( StandardFunctionReturnTypeResolvers.invariant( doubleBasicType ) )
				.register();
		functionRegistry.namedAggregateDescriptorBuilder( "stDevP" )
				.setArgumentRenderingMode( SqlAstNodeRenderingMode.DEFAULT )
				.setExactArgumentCount( 1 )
				.setParameterTypes( FunctionParameterType.NUMERIC )
				.setReturnTypeResolver( StandardFunctionReturnTypeResolvers.invariant( doubleBasicType ) )
				.register();

		// mathematical functions https://neo4j.com/docs/cypher-manual/current/functions/mathematical-logarithmic/
		functionFactory.ln_log();
		functionFactory.log_loglog();
		functionFactory.log10();
		functionFactory.concat_pipeOperator();

		// todo neo4j : a lot of functions are added by the apoc plugin https://neo4j.com/labs/apoc/
		//  would be great to detect it at startup and register them here
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
	public SqlAstTranslatorFactory getSqlAstTranslatorFactory() {
		return new StandardSqlAstTranslatorFactory() {
			@Override
			protected <T extends JdbcOperation> SqlAstTranslator<T> buildTranslator(
					SessionFactoryImplementor sessionFactory, Statement statement) {
				return new Neo4jSqlAstTranslator<>( sessionFactory, statement );
			}
		};
	}

	@Override
	public DmlTargetColumnQualifierSupport getDmlTargetColumnQualifierSupport() {
		return DmlTargetColumnQualifierSupport.TABLE_ALIAS;
	}

	@Override
	public String[] getTruncateTableStatements(String[] tableNames) {
		return new String[] {"match(n:" + String.join( "|", tableNames ) + ") detach delete n"};
	}

	@Override
	public boolean hasAlterTable() {
		return false;
	}

	@Override
	public boolean supportsFromClauseInUpdate() {
		return true;
	}

	@Override
	public UniqueDelegate getUniqueDelegate() {
		return uniqueDelegate;
	}

	@Override
	public String currentDate() {
		return "date()";
	}

	public String currentTime() {
		return "localtime()";
	}

	public String currentTimestamp() {
		return "localdatetime()";
	}

	public String currentTimestampWithTimeZone() {
		return "datetime()";
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
}
