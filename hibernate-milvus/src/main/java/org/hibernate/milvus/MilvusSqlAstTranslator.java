/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus;

import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.IndexParam;
import org.hibernate.LockMode;
import org.hibernate.Locking;
import org.hibernate.dialect.SelectItemReferenceStrategy;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.internal.util.MathHelper;
import org.hibernate.internal.util.collections.Stack;
import org.hibernate.metamodel.mapping.EntityIdentifierMapping;
import org.hibernate.metamodel.mapping.EntityMappingType;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.metamodel.mapping.JdbcMappingContainer;
import org.hibernate.metamodel.mapping.ModelPartContainer;
import org.hibernate.metamodel.mapping.SelectableMapping;
import org.hibernate.milvus.jdbc.AbstractMilvusCollectionStatement;
import org.hibernate.milvus.jdbc.AbstractMilvusQuery;
import org.hibernate.milvus.jdbc.MilvusBooleanValue;
import org.hibernate.milvus.jdbc.MilvusDelete;
import org.hibernate.milvus.jdbc.MilvusFloatVectorValue;
import org.hibernate.milvus.jdbc.MilvusHelper;
import org.hibernate.milvus.jdbc.MilvusHybridAnnSearch;
import org.hibernate.milvus.jdbc.MilvusHybridSearch;
import org.hibernate.milvus.jdbc.MilvusInsert;
import org.hibernate.milvus.jdbc.MilvusInsertOrUpdate;
import org.hibernate.milvus.jdbc.MilvusJsonHelper;
import org.hibernate.milvus.jdbc.MilvusNumberValue;
import org.hibernate.milvus.jdbc.MilvusParameterValue;
import org.hibernate.milvus.jdbc.MilvusQuery;
import org.hibernate.milvus.jdbc.MilvusSearch;
import org.hibernate.milvus.jdbc.MilvusSearchRequest;
import org.hibernate.milvus.jdbc.MilvusStatementDefinition;
import org.hibernate.milvus.jdbc.MilvusStringValue;
import org.hibernate.milvus.jdbc.MilvusTypedValue;
import org.hibernate.milvus.jdbc.MilvusUpsert;
import org.hibernate.persister.internal.SqlFragmentPredicate;
import org.hibernate.query.SortDirection;
import org.hibernate.query.common.FetchClauseType;
import org.hibernate.query.spi.QueryOptions;
import org.hibernate.query.sqm.ComparisonOperator;
import org.hibernate.query.sqm.sql.internal.BasicValuedPathInterpretation;
import org.hibernate.query.sqm.sql.internal.SqmParameterInterpretation;
import org.hibernate.query.sqm.tree.expression.Conversion;
import org.hibernate.query.sqm.tuple.internal.AnonymousTupleTableGroupProducer;
import org.hibernate.sql.ast.Clause;
import org.hibernate.sql.ast.SqlAstNodeRenderingMode;
import org.hibernate.sql.ast.spi.AbstractSqlAstTranslator;
import org.hibernate.sql.ast.tree.MutationStatement;
import org.hibernate.sql.ast.tree.SqlAstNode;
import org.hibernate.sql.ast.tree.Statement;
import org.hibernate.sql.ast.tree.cte.CteContainer;
import org.hibernate.sql.ast.tree.delete.DeleteStatement;
import org.hibernate.sql.ast.tree.expression.*;
import org.hibernate.sql.ast.tree.from.FromClause;
import org.hibernate.sql.ast.tree.from.FunctionTableReference;
import org.hibernate.sql.ast.tree.from.NamedTableReference;
import org.hibernate.sql.ast.tree.from.QueryPartTableReference;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.ast.tree.from.TableReferenceJoin;
import org.hibernate.sql.ast.tree.from.ValuesTableReference;
import org.hibernate.sql.ast.tree.insert.InsertSelectStatement;
import org.hibernate.sql.ast.tree.insert.Values;
import org.hibernate.sql.ast.tree.predicate.BetweenPredicate;
import org.hibernate.sql.ast.tree.predicate.BooleanExpressionPredicate;
import org.hibernate.sql.ast.tree.predicate.ComparisonPredicate;
import org.hibernate.sql.ast.tree.predicate.ExistsPredicate;
import org.hibernate.sql.ast.tree.predicate.FilterPredicate;
import org.hibernate.sql.ast.tree.predicate.InArrayPredicate;
import org.hibernate.sql.ast.tree.predicate.InListPredicate;
import org.hibernate.sql.ast.tree.predicate.InSubQueryPredicate;
import org.hibernate.sql.ast.tree.predicate.LikePredicate;
import org.hibernate.sql.ast.tree.predicate.Predicate;
import org.hibernate.sql.ast.tree.predicate.ThruthnessPredicate;
import org.hibernate.sql.ast.tree.select.QueryGroup;
import org.hibernate.sql.ast.tree.select.QuerySpec;
import org.hibernate.sql.ast.tree.select.SelectClause;
import org.hibernate.sql.ast.tree.select.SelectStatement;
import org.hibernate.sql.ast.tree.select.SortSpecification;
import org.hibernate.sql.ast.tree.update.Assignment;
import org.hibernate.sql.ast.tree.update.UpdateStatement;
import org.hibernate.sql.exec.ExecutionException;
import org.hibernate.sql.exec.internal.AbstractJdbcParameter;
import org.hibernate.sql.exec.internal.JdbcParameterImpl;
import org.hibernate.sql.exec.internal.SqlTypedMappingJdbcParameter;
import org.hibernate.sql.exec.spi.ExecutionContext;
import org.hibernate.sql.exec.spi.JdbcOperation;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;
import org.hibernate.sql.exec.spi.JdbcParameterBinding;
import org.hibernate.sql.exec.spi.JdbcParameterBindings;
import org.hibernate.sql.model.MutationTarget;
import org.hibernate.sql.model.ast.AbstractTableMutation;
import org.hibernate.sql.model.ast.ColumnValueParameter;
import org.hibernate.sql.model.ast.ColumnWriteFragment;
import org.hibernate.sql.model.internal.OptionalTableUpdate;
import org.hibernate.sql.model.internal.TableDeleteCustomSql;
import org.hibernate.sql.model.internal.TableDeleteStandard;
import org.hibernate.sql.model.internal.TableInsertCustomSql;
import org.hibernate.sql.model.internal.TableInsertStandard;
import org.hibernate.sql.model.internal.TableUpdateCustomSql;
import org.hibernate.sql.model.internal.TableUpdateStandard;
import org.hibernate.tool.schema.extract.spi.ColumnTypeInformation;
import org.hibernate.type.BasicPluralType;
import org.hibernate.type.BasicType;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.java.spi.JavaTypeRegistry;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.spi.TypeConfiguration;

import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public class MilvusSqlAstTranslator<T extends JdbcOperation> extends AbstractSqlAstTranslator<T> {

	public static final String DEFAULT_EMBEDDING_FIELD = "embedding";

	private static final Set<String> SUPPORTED_AGGREGATE_FUNCTIONS = Set.of( "count", "sum", "avg", "min", "max" );
	private static final String TRUE_CONSTANT = "1=1";

	private MilvusStatementDefinition milvusStatement;
	private List<String> databaseHints;

	protected MilvusSqlAstTranslator(SessionFactoryImplementor sessionFactory, Statement statement) {
		super(sessionFactory, statement);
	}

	@Override
	public String getSql() {
		return MilvusJsonHelper.serializeDefinition( milvusStatement );
	}

	@Override
	public void renderNamedSetReturningFunction(String functionName, List<? extends SqlAstNode> sqlAstArguments, AnonymousTupleTableGroupProducer tupleType, String tableIdentifierVariable, SqlAstNodeRenderingMode argumentRenderingMode) {
		throw new UnsupportedOperationException();
	}

	@Override
	public T translate(JdbcParameterBindings jdbcParameterBindings, QueryOptions queryOptions) {
		try {
			databaseHints = queryOptions.getDatabaseHints();
			return super.translate( jdbcParameterBindings, queryOptions );
		}
		finally {
			databaseHints = null;
		}
	}

	@Override
	public void visitSelectStatement(SelectStatement statement) {
		final QuerySpec querySpec = statement.getQuerySpec();
		final List<Expression> groupByClauseExpressions = querySpec.getGroupByClauseExpressions();
		// To do group by, one must use a "search" instead of "query"
		milvusStatement = groupByClauseExpressions != null && !groupByClauseExpressions.isEmpty()
				? new MilvusSearch()
				: new MilvusQuery();
		super.visitSelectStatement( statement );
		applyHints();
	}

	private void applyHints() {
		if (databaseHints != null && !databaseHints.isEmpty()) {
			for ( String databaseHint : databaseHints ) {
				final int assignmentIndex = databaseHint.indexOf( '=' );
				if ( assignmentIndex != -1 ) {
					switch (databaseHint.substring( 0, assignmentIndex ) ) {
						case MilvusDatabaseHints.CONSISTENCY_LEVEL -> applyConsistencyLevelHint( milvusStatement,
								databaseHint.substring( assignmentIndex + 1 ) );
					}
				}
			}
		}
	}

	private void applyConsistencyLevelHint(MilvusStatementDefinition milvusStatement, String hintValue) {
		final ConsistencyLevel consistencyLevel = ConsistencyLevel.valueOf( hintValue.toUpperCase( Locale.ROOT ) );
		if ( milvusStatement instanceof AbstractMilvusQuery query ) {
			query.setConsistencyLevel( consistencyLevel );
		}
	}

	@Override
	public void visitCteContainer(CteContainer cteContainer) {
		if (!cteContainer.getCteObjects().isEmpty()) {
			throw new UnsupportedOperationException( "CTEs are not supported by Milvus" );
		}
	}

	@Override
	public void visitDeleteStatement(DeleteStatement statement) {
		milvusStatement = new MilvusDelete();
		super.visitDeleteStatement( statement );
		applyHints();
	}

	@Override
	protected void visitDeleteStatementOnly(DeleteStatement statement) {
		renderDeleteClause( statement );
		if ( !hasNonTrivialFromClause( statement.getFromClause() ) ) {
			visitWhereClausePredicate( statement.getRestriction() );
		}
		else {
			visitWhereClausePredicate( determineWhereClauseRestrictionWithJoinEmulation( statement ) );
		}
		if ( !statement.getReturningColumns().isEmpty() ) {
			throw new UnsupportedOperationException( "Returning columns in deletes are not supported by Milvus" );
		}
	}

	@Override
	protected void renderDeleteClause(DeleteStatement statement) {
		final Stack<Clause> clauseStack = getClauseStack();
		try {
			clauseStack.push( Clause.DELETE );
			renderDmlTargetTableExpression( statement.getTargetTable() );
		}
		finally {
			clauseStack.pop();
		}
	}

	@Override
	public void visitUpdateStatement(UpdateStatement statement) {
		milvusStatement = new MilvusUpsert();
		visitUpdateStatementOnly( statement );
		applyHints();
	}

	@Override
	protected void visitUpdateStatementOnly(UpdateStatement statement) {
		renderUpdateClause( statement );
		renderSetClause( statement.getAssignments() );
		visitWhereClausePredicate( statement.getRestriction() );
		if ( !statement.getReturningColumns().isEmpty() ) {
			throw new UnsupportedOperationException( "Returning columns in updates are not supported by Milvus" );
		}
	}

	@Override
	protected void renderUpdateClause(UpdateStatement updateStatement) {
		final Stack<Clause> clauseStack = getClauseStack();
		try {
			clauseStack.push( Clause.UPDATE );
			renderDmlTargetTableExpression( updateStatement.getTargetTable() );
		}
		finally {
			clauseStack.pop();
		}
	}

	@Override
	public void visitInsertStatement(InsertSelectStatement statement) {
		if (statement.getSourceSelectStatement() != null) {
			throw new UnsupportedOperationException();
		}
		getCurrentClauseStack().push( Clause.INSERT );
		try {
			MilvusInsert insert = new MilvusInsert();
			milvusStatement = insert;

			insert.setCollectionName( statement.getMutationTarget().getIdentifierTableName() );
			registerAffectedTable( statement.getTargetTable() );

			getCurrentClauseStack().push( Clause.VALUES );
			try {
				final List<ColumnReference> targetColumns = statement.getTargetColumns();
				for ( Values values : statement.getValuesList() ) {
					final Map<String, MilvusTypedValue> valueMap = new LinkedHashMap<>();
					final List<Expression> expressions = values.getExpressions();
					for ( int i = 0; i < expressions.size(); i++ ) {
						final Expression valueExpression = expressions.get( i );
						if ( !isParameter( valueExpression ) ) {
							throw new UnsupportedOperationException( "Need a parameter expression for inserts" );
						}
						valueMap.put( targetColumns.get( i ).getColumnReference().getColumnExpression(),
								renderValue( valueExpression ) );
					}
					addDefaultEmbeddingDataIfNeeded( valueMap, statement );
					insert.getData().add( valueMap );
				}
			}
			finally {
				getCurrentClauseStack().pop();
			}

			if ( !statement.getReturningColumns().isEmpty() ) {
				visitReturningColumns( statement::getReturningColumns );
			}
		}
		finally {
			getCurrentClauseStack().pop();
		}
		applyHints();
	}

	private void addDefaultEmbeddingDataIfNeeded(Map<String, MilvusTypedValue> valueMap, MutationStatement statement) {
		addDefaultEmbeddingDataIfNeeded( valueMap, statement.getMutationTarget() );
	}

	private void addDefaultEmbeddingDataIfNeeded(Map<String, MilvusTypedValue> valueMap, AbstractTableMutation<?> mutation) {
		addDefaultEmbeddingDataIfNeeded( valueMap, mutation.getMutationTarget() );
	}

	private void addDefaultEmbeddingDataIfNeeded(Map<String, MilvusTypedValue> valueMap, MutationTarget<?> mutationTarget) {
		// todo (milvus): as of version 3.0, the vector can be null in the beginning
		if ( !hasVector( mutationTarget.getTargetPart() ) ) {
			valueMap.put( DEFAULT_EMBEDDING_FIELD, new MilvusFloatVectorValue( new float[]{ 0, 0 } ) );
		}
	}

	@Override
	public void visitAssignment(Assignment assignment) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitQueryGroup(QueryGroup queryGroup) {
		throw new UnsupportedOperationException("QueryGroups are not supported by Milvus");
	}

	@Override
	public void visitQuerySpec(QuerySpec querySpec) {
		try {
			getQueryPartStack().push( querySpec );
			final Predicate havingClauseRestrictions = querySpec.getHavingClauseRestrictions();
			if ( havingClauseRestrictions != null && !havingClauseRestrictions.isEmpty() ) {
				throw new UnsupportedOperationException( "Having clause is not supported by Milvus" );
			}
			final List<SortSpecification> sortSpecifications = querySpec.getSortSpecifications();
			if ( sortSpecifications != null && !sortSpecifications.isEmpty() ) {
				final SortSpecification sortSpecification = sortSpecifications.get( 0 );
				final Expression sortExpression = resolveAliasedExpression( sortSpecification.getSortExpression() );

				final DistanceFunctionKind sortDistanceFunctionKind;
				if ( sortSpecifications.size() == 1
					&& sortExpression instanceof FunctionExpression sortFunction
					&& (sortDistanceFunctionKind = getDistanceFunctionKind( sortFunction )) != null ) {
					if ( sortDistanceFunctionKind.getDefaultOrder() != sortSpecification.getSortOrder() ) {
						throw new UnsupportedOperationException(
								"Milvus only supports ordering by vector distance in the natural order. For " + sortDistanceFunctionKind + " " + sortDistanceFunctionKind.getDefaultOrder() + " is expected but got " + sortSpecification.getSortOrder() );
					}
				}
				else {
					// todo (milvus): version 3.0 supports order_by for query and order_by_fields for search methods
					throw new UnsupportedOperationException( "Milvus only supports ordering by vector distance" );
				}
			}

			visitSelectClause( querySpec.getSelectClause() );
			visitFromClause( querySpec.getFromClause() );
			visitWhereClausePredicate( querySpec.getWhereClauseRestrictions() );
			visitGroupByClause( querySpec );
			visitOffsetFetchClause( querySpec );
			visitForUpdateClause( querySpec );
		}
		finally {
			getQueryPartStack().pop();
		}
	}

	protected void visitWhereClausePredicate(Predicate whereClauseRestrictions) {
		assert getSqlBuffer().isEmpty();

		if ( milvusStatement instanceof MilvusUpsert upsert ) {
			final Predicate predicate = Predicate.combinePredicates( whereClauseRestrictions, getAdditionalWherePredicate() );
			if ( predicate == null || predicate.isEmpty() ) {
				throw new UnsupportedOperationException( "Milvus only supports updates by primary key" );
			}
			final List<Expression> idsValues = determineIdValues( predicate, true );
			if ( idsValues.size() > 1 || !idsValues.isEmpty() && isArrayExpression( idsValues.get( 0 ) ) ) {
				throw new UnsupportedOperationException("Milvus does not support updates for multiple ids");
			}
			upsert.getData().get( 0 ).put( getPrimaryKey().getSelectionExpression(), renderValue( idsValues.get( 0 ) ) );
		}
		else {
			if ( milvusStatement instanceof MilvusQuery query ) {
				final List<Expression> idsValues = determineIdValues( whereClauseRestrictions, false );
				if ( idsValues != null ) {
					final List<MilvusTypedValue> typedValues = new ArrayList<>( idsValues.size() );
					for ( Expression idsValue : idsValues ) {
						typedValues.add( renderValue( idsValue ) );
					}
					query.setIds( typedValues );
					return;
				}
			}
			else if ( milvusStatement instanceof MilvusDelete delete ) {
				final List<Expression> idsValues = determineIdValues( whereClauseRestrictions, false );
				if ( idsValues != null ) {
					final List<MilvusTypedValue> typedValues = new ArrayList<>( idsValues.size() );
					for ( Expression idsValue : idsValues ) {
						typedValues.add( renderValue( idsValue ) );
					}
					delete.setIds( typedValues );
					return;
				}
			}
			visitWhereClause( whereClauseRestrictions );
			getSqlBuffer().replace( 0, " where ".length(), "" );

			if ( milvusStatement instanceof AbstractMilvusQuery query ) {
				final String filter = determineFilter();
				if ( filter.isEmpty() ) {
					// On an empty filter, default the limit to the maximum possible value
					query.setLimit( 16384L );
				}
				query.setFilter( filter );
			}
			else if ( milvusStatement instanceof MilvusDelete delete ) {
				final String filter = determineFilter();
				if ( filter.isEmpty() ) {
					// Delete without filter and ids list doesn't work, and always true constants also don't work,
					// so we have to use a primary key not null predicate instead
					delete.setFilter( getPrimaryKey().getSelectionExpression() + " is not null" );
				}
				else {
					delete.setFilter( filter );
				}
			}
			else {
				throw new UnsupportedOperationException( "MilvusStatement is not supported by Milvus" );
			}
			getSqlBuffer().setLength( 0 );
		}
	}

	private Predicate getAdditionalWherePredicate() {
		// todo: temporary, until we add the getter to ORM
		try {
			Field additionalWherePredicate = AbstractSqlAstTranslator.class.getField( "additionalWherePredicate" );
			additionalWherePredicate.setAccessible( true );
			return (Predicate) additionalWherePredicate.get( this );
		}
		catch (Exception e) {
			throw new RuntimeException( e );
		}
	}

	private String determineFilter() {
		// Remove the constants
		replaceAll( getSqlBuffer(), " and " + TRUE_CONSTANT, "" );
		replaceAll( getSqlBuffer(), TRUE_CONSTANT + " and " , "" );
		replaceAll( getSqlBuffer(), " and (" + TRUE_CONSTANT + ")", "" );
		replaceAll( getSqlBuffer(), "(" + TRUE_CONSTANT + ") and " , "" );
		replaceAll( getSqlBuffer(), " or " + TRUE_CONSTANT, "" );
		replaceAll( getSqlBuffer(), TRUE_CONSTANT + " or " , "" );
		replaceAll( getSqlBuffer(), " or (" + TRUE_CONSTANT + ")", "" );
		replaceAll( getSqlBuffer(), "(" + TRUE_CONSTANT + ") or " , "" );
		final String filter = getSqlBuffer().toString();
		return TRUE_CONSTANT.equals( filter ) ? "" : filter;
	}

	private static void replaceAll(StringBuilder sb, String search, String replacement) {
		int start = 0;
		int position;
		while ( (position = sb.indexOf( search, start ) ) != -1 ) {
			sb.replace( position, position + search.length(), replacement );
			start = position + replacement.length() + 1;
		}
	}

	@Override
	protected void renderComparison(Expression lhs, ComparisonOperator operator, Expression rhs) {
		DistanceFunctionKind distanceFunctionKind;
		if ( lhs instanceof FunctionExpression functionExpression
			&& (distanceFunctionKind = getDistanceFunctionKind( functionExpression )) != null ) {
			addVectorSearch( distanceFunctionKind, functionExpression );
			addVectorSearchPredicate( rhs, operator, distanceFunctionKind );
			appendSql( TRUE_CONSTANT );
		}
		else if ( rhs instanceof FunctionExpression functionExpression
			&& (distanceFunctionKind = getDistanceFunctionKind( functionExpression )) != null ) {
			addVectorSearch( distanceFunctionKind, functionExpression );
			addVectorSearchPredicate( lhs, operator.invert(), distanceFunctionKind );
			appendSql( TRUE_CONSTANT );
		}
		else {
			lhs.accept( this );
			if ( operator == ComparisonOperator.NOT_EQUAL ) {
				appendSql( "!=" );
			}
			else if ( operator == ComparisonOperator.EQUAL ) {
				appendSql( "==" );
			}
			else {
				appendSql( operator.sqlText() );
			}
			rhs.accept( this );
		}
	}

	private MilvusTypedValue renderValue(Expression expression) {
		if ( isParameter( expression ) || expression instanceof ColumnWriteFragment ) {
			final String oldBuffer = getSqlBuffer().toString();
			getSqlBuffer().setLength( 0 );

			expression.accept( this );

			getSqlBuffer().setLength( 0 );
			getSqlBuffer().append( oldBuffer );
			return new MilvusParameterValue( getParameterBinders().size() );
		}
		final Object literalValue = getLiteralValue( expression );
		if ( literalValue instanceof Number number ) {
			return new MilvusNumberValue( number );
		}
		else if ( literalValue instanceof Boolean bool ) {
			return new MilvusBooleanValue( bool );
		}
		else {
			return new MilvusStringValue( literalValue.toString() );
		}
	}

	@Override
	protected void renderCasted(Expression expression) {
		visitParameterAsParameter( (JdbcParameter) expression );
	}

	@Override
	protected void renderWrappedParameter(JdbcParameter jdbcParameter) {
		visitParameterAsParameter( jdbcParameter );
	}

	@Override
	protected void visitParameterAsParameter(JdbcParameter jdbcParameter) {
		super.visitParameterAsParameter( jdbcParameter );
		addParameterRegistration( getParameterBinders().size() );
	}

	private void addParameterRegistration(int position) {
		Map<String, MilvusTypedValue> parameterRegistrations;
		if ( milvusStatement instanceof AbstractMilvusQuery query ) {
			parameterRegistrations = query.getFilterTemplateValues();
			if ( parameterRegistrations == null ) {
				query.setFilterTemplateValues( parameterRegistrations = new LinkedHashMap<>() );
			}
		}
		else if ( milvusStatement instanceof MilvusDelete delete ) {
			parameterRegistrations = delete.getFilterTemplateValues();
			if ( parameterRegistrations == null ) {
				delete.setFilterTemplateValues( parameterRegistrations = new LinkedHashMap<>() );
			}
		}
		else if ( milvusStatement instanceof MilvusInsertOrUpdate ) {
			// Insert/Update parameter positions are captured in #renderValue
			return;
		}
		else {
			throw new UnsupportedOperationException( "Unsupported statement: " + milvusStatement );
		}
		parameterRegistrations.put( "p" + position, new MilvusParameterValue( position ) );
	}

	@Override
	protected void renderParameterAsParameter(int position, JdbcParameter jdbcParameter) {
		appendSql( "{p" );
		appendSql( position );
		appendSql( '}' );
	}

	@Override
	public void visitColumnWriteFragment(ColumnWriteFragment columnWriteFragment) {
		final Collection<ColumnValueParameter> parameters = columnWriteFragment.getParameters();
		if ( parameters.isEmpty() ) {
			super.visitColumnWriteFragment( columnWriteFragment );
		}
		else if ( parameters.size() > 1 || !columnWriteFragment.getFragment().equals( "?" )) {
			throw new UnsupportedOperationException( "Milvus only supports simple column write fragments, but got: " + columnWriteFragment.getFragment() );
		}
		else {
			simpleColumnWriteFragmentRendering( columnWriteFragment );
		}
	}

	@Override
	public void visitInListPredicate(InListPredicate inListPredicate) {
		final List<Expression> listExpressions = inListPredicate.getListExpressions();
		if ( listExpressions.isEmpty() ) {
			appendSql( "1=" + ( inListPredicate.isNegated() ? "1" : "0" ) );
			return;
		}

		Function<Expression, Expression> itemAccessor = Function.identity();
		final SqlTuple lhsTuple;
		if ( ( lhsTuple = SqlTupleContainer.getSqlTuple( inListPredicate.getTestExpression() ) ) != null ) {
			if ( lhsTuple.getExpressions().size() == 1 ) {
				// Special case for tuples with arity 1 as any DBMS supports scalar IN predicates
				itemAccessor = listExpression -> SqlTupleContainer.getSqlTuple( listExpression ).getExpressions().get( 0 );
			}
			else {
				final ComparisonOperator comparisonOperator = inListPredicate.isNegated() ?
						ComparisonOperator.NOT_EQUAL :
						ComparisonOperator.EQUAL;
				String separator = NO_SEPARATOR;
				appendSql( OPEN_PARENTHESIS );
				for ( Expression expression : listExpressions ) {
					appendSql( separator );
					emulateTupleComparison(
							lhsTuple.getExpressions(),
							SqlTupleContainer.getSqlTuple( expression ).getExpressions(),
							comparisonOperator,
							true
					);
					separator = " or ";
				}
				appendSql( CLOSE_PARENTHESIS );
				return;
			}
		}

		int bindValueCount = listExpressions.size();
		int bindValueCountWithPadding = bindValueCount;

		int inExprLimit = Integer.MAX_VALUE;

		if ( getSessionFactory().getSessionFactoryOptions().inClauseParameterPaddingEnabled() ) {
			bindValueCountWithPadding = addPadding( bindValueCount, inExprLimit );
		}

		final boolean parenthesis = !inListPredicate.isNegated()
									&& inExprLimit > 0 && listExpressions.size() > inExprLimit;
		if ( parenthesis ) {
			appendSql( OPEN_PARENTHESIS );
		}

		inListPredicate.getTestExpression().accept( this );
		if ( inListPredicate.isNegated() ) {
			appendSql( " not" );
		}
		appendSql( " in " );
		if ( isAllParameters( listExpressions ) ) {
			// Milvus does not support parameters in the array of the IN predicate i.e. `x in [{p0}]`,
			// so we must transform the listExpressions, if they are all parameters, into a single array parameter
			final JdbcParameter firstParameter = getJdbcParameter( listExpressions.get( 0 ) );
			final BasicType<?> elementType = (BasicType<?>) firstParameter.getExpressionType().getSingleJdbcMapping();
			final TypeConfiguration typeConfiguration = getSessionFactory().getTypeConfiguration();
			final JavaTypeRegistry javaTypeRegistry = typeConfiguration.getJavaTypeRegistry();
			@SuppressWarnings("unchecked") final JavaType<Object[]> arrayJavaType =
					javaTypeRegistry.resolveArrayDescriptor( (Class<Object>) elementType.getJavaType() );
			final JdbcType arrayJdbcType = typeConfiguration.getJdbcTypeRegistry().resolveTypeConstructorDescriptor(
					SqlTypes.ARRAY,
					elementType.getJdbcType(),
					ColumnTypeInformation.EMPTY
			);
			final BasicType<Object[]> arrayType =
					typeConfiguration.getBasicTypeRegistry().resolve( arrayJavaType, arrayJdbcType );
			final JdbcParameter arrayParameter = new JdbcParameterImpl( arrayType );
			final int parameterPosition = addParameterBinder(
					arrayParameter,
					(statement, startPosition, jdbcParameterBindings, executionContext) -> {
						final Object[] array = new Object[listExpressions.size()];
						for ( int i = 0; i < listExpressions.size(); i++ ) {
							final JdbcParameter parameter = getJdbcParameter( listExpressions.get( i ) );
							final JdbcParameterBinding binding = jdbcParameterBindings.getBinding( parameter );
							if ( binding == null ) {
								throw new ExecutionException( "JDBC parameter value not bound - " + parameter );
							}
							array[i] = binding.getBindValue();
						}
						arrayType.getJdbcValueBinder().bind( statement, array, startPosition, executionContext.getSession() );
					}
			);
			renderParameterAsParameter( parameterPosition, arrayParameter );
			addParameterRegistration( parameterPosition );
		}
		else {
			appendSql( '[' );
			String separator = NO_SEPARATOR;

			final Iterator<Expression> iterator = listExpressions.iterator();
			Expression listExpression = null;
			int clauseItemNumber = 0;
			for ( int i = 0; i < bindValueCountWithPadding; i++, clauseItemNumber++ ) {
				if ( inExprLimit > 0 && inExprLimit == clauseItemNumber ) {
					clauseItemNumber = 0;
					appendInClauseSeparator( inListPredicate );
					separator = NO_SEPARATOR;
				}

				if ( iterator.hasNext() ) { // If the iterator is exhausted, reuse the last expression for padding.
					listExpression = itemAccessor.apply( iterator.next() );
				}
				// The only way for expression to be null is if listExpressions is empty,
				// but if that is the case the code takes an early exit.
				assert listExpression != null;

				appendSql( separator );
				listExpression.accept( this );
				separator = COMMA_SEPARATOR;

				// If we encounter an expression that is not a parameter or literal, we reset the inExprLimit and
				// bindValueMaxCount and just render through the in list expressions as they are without padding/splitting
				if ( !(listExpression instanceof JdbcParameter || listExpression instanceof SqmParameterInterpretation || listExpression instanceof Literal) ) {
					inExprLimit = 0;
					bindValueCountWithPadding = bindValueCount;
				}
			}

			appendSql( ']' );
		}
		if ( parenthesis ) {
			appendSql( CLOSE_PARENTHESIS );
		}
	}

	private static boolean isAllParameters(List<Expression> listExpressions) {
		for ( Expression listExpression : listExpressions ) {
			if ( !isParameter( listExpression ) ) {
				return false;
			}
		}
		return true;
	}

	private static JdbcParameter getJdbcParameter(Expression expression) {
		return expression instanceof SqmParameterInterpretation parameterInterpretation
				? (JdbcParameter) parameterInterpretation.getResolvedExpression()
				: (JdbcParameter) expression;
	}

	private void appendInClauseSeparator(InListPredicate inListPredicate) {
		appendSql( ']' );
		appendSql( inListPredicate.isNegated() ? " and " : " or " );
		inListPredicate.getTestExpression().accept( this );
		if ( inListPredicate.isNegated() ) {
			appendSql( " not" );
		}
		appendSql( " in [" );
	}

	private static int addPadding(int bindValueCount, int inExprLimit) {
		final int ceilingPowerOfTwo = MathHelper.ceilingPowerOfTwo( bindValueCount );
		if ( inExprLimit <= 0 || ceilingPowerOfTwo <= inExprLimit ) {
			return ceilingPowerOfTwo;
		}
		else {
			int numberOfInClauses = MathHelper.divideRoundingUp( bindValueCount, inExprLimit );
			int numberOfInClausesWithPadding = MathHelper.ceilingPowerOfTwo( numberOfInClauses );
			return numberOfInClausesWithPadding * inExprLimit;
		}
	}

	@Override
	public void visitInArrayPredicate(InArrayPredicate inArrayPredicate) {
		inArrayPredicate.accept( this );
		appendSql( " in " );
		inArrayPredicate.getArrayParameter().accept( this );
	}

	@Override
	public void visitLikePredicate(LikePredicate likePredicate) {
		if (!likePredicate.isCaseSensitive()) {
			throw new UnsupportedOperationException( "Case-insensitive LIKE is not supported by Milvus" );
		}
		likePredicate.getMatchExpression().accept( this );
		if ( likePredicate.isNegated() ) {
			appendSql( " not" );
		}
		appendSql( " like " );
		renderBackslashEscapedLikePattern(
				likePredicate.getPattern(),
				likePredicate.getEscapeCharacter(),
				false
		);
	}

	@Override
	public void visitBinaryArithmeticExpression(BinaryArithmeticExpression arithmeticExpression) {
		appendSql( OPEN_PARENTHESIS );
		visitArithmeticOperand( arithmeticExpression.getLeftHandOperand() );
		appendSql( arithmeticExpression.getOperator().getOperatorSqlTextString() );
		visitArithmeticOperand( arithmeticExpression.getRightHandOperand() );
		appendSql( CLOSE_PARENTHESIS );
	}

	@Override
	public void visitBetweenPredicate(BetweenPredicate betweenPredicate) {
		final Expression expression = betweenPredicate.getExpression();
		final DistanceFunctionKind distanceFunctionKind;
		if ( expression instanceof FunctionExpression functionExpression
			&& (distanceFunctionKind = getDistanceFunctionKind( functionExpression )) != null ) {
			addVectorSearch( distanceFunctionKind, functionExpression );
			addVectorSearchPredicate(
					betweenPredicate.getLowerBound(),
					ComparisonOperator.GREATER_THAN_OR_EQUAL,
					distanceFunctionKind
			);
			addVectorSearchPredicate(
					betweenPredicate.getUpperBound(),
					ComparisonOperator.LESS_THAN_OR_EQUAL,
					distanceFunctionKind
			);
			appendSql( TRUE_CONSTANT );
			return;
		}

		if ( betweenPredicate.isNegated() ) {
			appendSql( "not(" );
		}
		expression.accept( this );
		appendSql( ">=" );
		betweenPredicate.getLowerBound().accept( this );
		appendSql( " and " );
		expression.accept( this );
		appendSql( "<=" );
		betweenPredicate.getUpperBound().accept( this );
		if ( betweenPredicate.isNegated() ) {
			appendSql( CLOSE_PARENTHESIS );
		}
	}

	private boolean isArrayExpression( Expression expression ) {
		return expression instanceof JdbcParameter parameter && parameter.getExpressionType()
				.getSingleJdbcMapping() instanceof BasicPluralType<?, ?>;
	}

	private List<Expression> determineIdValues(Predicate predicate, boolean required) {
		final List<Expression> idsValues;
		final Expression lhs;
		if ( predicate instanceof ComparisonPredicate comparisonPredicate ) {
			lhs = comparisonPredicate.getLeftHandExpression();
			idsValues = List.of( comparisonPredicate.getRightHandExpression() );
		}
		else if ( predicate instanceof InListPredicate inListPredicate ) {
			lhs = inListPredicate.getTestExpression();
			idsValues = inListPredicate.getListExpressions();
		}
		else if ( predicate instanceof InArrayPredicate inArrayPredicate ) {
			lhs = inArrayPredicate.getTestExpression();
			idsValues = List.of( inArrayPredicate.getArrayParameter() );
		}
		else {
			if ( !required ) {
				return null;
			}
			throw new UnsupportedOperationException( "Unsupported predicate type for by id predicate: " + predicate );
		}
		final ColumnReference columnReference = lhs.getColumnReference();
		if ( columnReference == null || !isPrimaryKey( columnReference ) ){
			if ( required ) {
				throw new UnsupportedOperationException(
						"Unsupported LHS expression type for by id predicate: " + predicate );
			}
			else {
				return null;
			}
		}
		else {
			return idsValues;
		}
	}

	private boolean isPrimaryKey(ColumnReference columnReference) {
		final Statement statement = getStatementStack().getCurrent();
		final SelectableMapping primaryKey;
		if ( statement instanceof SelectStatement selectStatement ) {
			final List<TableGroup> roots = selectStatement.getQuerySpec().getFromClause().getRoots();
			assert roots.size() == 1;
			primaryKey = roots.get( 0 ).findTableReference( columnReference.getQualifier() ) != null
					? getPrimaryKey()
					: null;
		}
		else if ( statement instanceof MutationStatement mutationStatement ) {
			final String targetAlias = mutationStatement.getTargetTable().getIdentificationVariable();
			primaryKey = targetAlias.equals( columnReference.getQualifier() )
					? getPrimaryKey()
					: null;
		}
		else {
			throw new UnsupportedOperationException( "Unsupported statement type: " + statement );
		}
		return primaryKey != null && primaryKey.getSelectionExpression().equals( columnReference.getColumnExpression() );
	}

	private SelectableMapping getPrimaryKey() {
		final Statement statement = getStatementStack().getCurrent();
		if ( statement instanceof SelectStatement selectStatement ) {
			final List<TableGroup> roots = selectStatement.getQuerySpec().getFromClause().getRoots();
			assert roots.size() == 1;
			final TableGroup tableGroup = roots.get( 0 );
			final EntityIdentifierMapping identifierMapping = tableGroup.getModelPart().asEntityMappingType()
					.getIdentifierMapping();
			assert identifierMapping.getJdbcTypeCount() == 1;
			return identifierMapping.getSelectable( 0 );
		}
		else if ( statement instanceof MutationStatement mutationStatement ) {
			final EntityIdentifierMapping identifierMapping = mutationStatement.getMutationTarget().getTargetPart()
					.asEntityMappingType()
					.getIdentifierMapping();
			assert identifierMapping.getJdbcTypeCount() == 1;
			return identifierMapping.getSelectable( 0 );
		}
		else {
			throw new UnsupportedOperationException( "Unsupported statement type: " + statement );
		}
	}

	protected void visitGroupByClause(QuerySpec querySpec) {
		final List<Expression> partitionExpressions = querySpec.getGroupByClauseExpressions();
		if ( !partitionExpressions.isEmpty() ) {
			if ( partitionExpressions.size() != 1 ) {
				throw new UnsupportedOperationException( "Multiple GROUP BY clause elements are not supported by Milvus" );
			}
			try {
				getClauseStack().push( Clause.GROUP );
				visitPartitionExpressions( partitionExpressions, SelectItemReferenceStrategy.EXPRESSION );
			}
			finally {
				getClauseStack().pop();
			}
		}
	}

	@Override
	protected void renderPartitionItem(Expression expression) {
		final MilvusSearch milvusSearch = (MilvusSearch) milvusStatement;
		final ColumnReference columnReference = expression.getColumnReference();
		if ( columnReference != null ) {
			milvusSearch.setGroupByFieldName( columnReference.getColumnExpression() );
		}
		else {
			throw new UnsupportedOperationException("Only column references are supported by Milvus in the GROUP BY clause");
		}
	}

	@Override
	public void visitSortSpecification(SortSpecification sortSpecification) {
		throw new UnsupportedOperationException();
	}

	@Override
	protected void renderOffset(Expression offsetExpression, boolean renderOffsetRowsKeyword) {
		getClauseStack().push( Clause.OFFSET );
		try {
			renderOffsetExpression( offsetExpression );
		}
		finally {
			getClauseStack().pop();
		}
	}

	@Override
	protected void renderFetch(Expression fetchExpression, Expression offsetExpressionToAdd, FetchClauseType fetchClauseType) {
		if ( fetchClauseType != null && fetchClauseType != FetchClauseType.ROWS_ONLY ) {
			throw new UnsupportedOperationException( "Fetch clause " + fetchClauseType + " is not supported by Milvus" );
		}
		getClauseStack().push( Clause.FETCH );
		try {
			renderFetchExpression( fetchExpression );
		}
		finally {
			getClauseStack().pop();
		}
	}

	@Override
	protected void renderOffsetExpression(Expression offsetExpression) {
		final AbstractMilvusQuery milvusQuery = (AbstractMilvusQuery) milvusStatement;
		final Number offset = getLiteralValue( offsetExpression );
		milvusQuery.setOffset( offset.longValue() );
	}

	@Override
	protected void renderFetchExpression(Expression fetchExpression) {
		final AbstractMilvusQuery milvusQuery = (AbstractMilvusQuery) milvusStatement;
		final Number limit = getLiteralValue( fetchExpression );
		milvusQuery.setLimit( limit.longValue() );
	}

	@Override
	protected LockStrategy determineLockingStrategy(QuerySpec querySpec, Locking.FollowOn followOnStrategy) {
		// Force the locking clause so we can error out early
		return LockStrategy.CLAUSE;
	}

	@Override
	public void visitSelectClause(SelectClause selectClause) {
		getClauseStack().push( Clause.SELECT );

		try {
			if ( selectClause.isDistinct() ) {
				throw new UnsupportedOperationException( "Distinct clause is not supported by Milvus" );
			}
			visitSqlSelections( selectClause );
			renderVirtualSelections( selectClause );
			// Clear out the commas
			getSqlBuffer().setLength( 0 );
		}
		finally {
			getClauseStack().pop();
		}
	}

	@Override
	protected void renderSelectExpression(Expression expression) {
		// Clear out the commas
		getSqlBuffer().setLength( 0 );

		final AbstractMilvusQuery milvusQuery = (AbstractMilvusQuery) milvusStatement;
		final ColumnReference columnReference = expression.getColumnReference();
		final DistanceFunctionKind distanceFunctionKind;
		if ( columnReference != null ) {
//			final QuerySpec querySpec = (QuerySpec) getCurrentQueryPart();
//			final TableGroup tableGroup = querySpec.getFromClause().getRoots().get( 0 );
//			if ( tableGroup.findTableReference( columnReference.getQualifier() ) == null ) {
//				throw new UnsupportedOperationException(
//						"Only column references are supported by Milvus in the SELECT clause" );
//			}
			if ( milvusQuery.getOutputFields() == null ) {
				milvusQuery.setOutputFields( new ArrayList<>() );
			}
			milvusQuery.getOutputFields().add( columnReference.getColumnExpression() );
		}
		else if ( expression instanceof FunctionExpression functionExpression
				&& (distanceFunctionKind = getDistanceFunctionKind( functionExpression )) != null ) {
			final QuerySpec querySpec = (QuerySpec) getCurrentQueryPart();
			if ( querySpec.hasSortSpecifications() ) {
				for ( SortSpecification sortSpecification : querySpec.getSortSpecifications() ) {
					final Expression sortExpression = resolveAliasedExpression( sortSpecification.getSortExpression() );

					final DistanceFunctionKind sortDistanceFunctionKind;
					if ( sortExpression instanceof FunctionExpression sortFunction
						&& (sortDistanceFunctionKind = getDistanceFunctionKind( sortFunction )) != null ) {
						if ( sortDistanceFunctionKind != distanceFunctionKind ) {
							throw new UnsupportedOperationException("Only one distance function may be used by a query in Milvus");
						}
					}
				}
			}
			if ( milvusQuery.getOutputFields() == null ) {
				milvusQuery.setOutputFields( new ArrayList<>() );
			}
			if ( distanceFunctionKind == DistanceFunctionKind.COSINE ) {
				milvusQuery.getOutputFields().add( MilvusHelper.COSINE_DISTANCE_FIELD );
			}
			else if ( distanceFunctionKind == DistanceFunctionKind.EUCLIDEAN ) {
				milvusQuery.getOutputFields().add( MilvusHelper.EUCLIDEAN_DISTANCE_FIELD );
			}
			else if ( distanceFunctionKind == DistanceFunctionKind.NEGATIVE_INNER_PRODUCT ) {
				milvusQuery.getOutputFields().add( MilvusHelper.NEGATIVE_IP_DISTANCE_FIELD );
			}
			else {
				milvusQuery.getOutputFields().add( MilvusHelper.DISTANCE_FIELD );
			}
			addVectorSearch( distanceFunctionKind, functionExpression );
		}
		else if ( getDialect().getVersion().isSameOrAfter( 3 )
				&& expression instanceof AggregateFunctionExpression functionExpression
				&& SUPPORTED_AGGREGATE_FUNCTIONS.contains( functionExpression.getFunctionName() ) ) {

			if ( milvusQuery.getOutputFields() == null ) {
				milvusQuery.setOutputFields( new ArrayList<>() );
			}
			expression.accept( this );
			milvusQuery.getOutputFields().add( getSqlBuffer().toString() );
			getSqlBuffer().setLength( 0 );
		}
		else {
			throw new UnsupportedOperationException("Only column references are supported by Milvus in the SELECT clause");
		}
	}

	private void addVectorSearch(DistanceFunctionKind distanceFunctionKind, FunctionExpression functionExpression) {
		ColumnReference vectorColumn;
		if ( !(functionExpression.getArguments().get( 0 ) instanceof BasicValuedPathInterpretation<?> basicPath)
			|| ( vectorColumn = basicPath.getColumnReference() ) == null ) {
			throw new UnsupportedOperationException( "Vector search only works on column in Milvus" );
		}
		final Expression vectorParameter = (Expression) functionExpression.getArguments().get( 1 );
		final MilvusTypedValue vectorTypedValue = renderValue( vectorParameter );
		final MilvusSearchRequest milvusSearch;
		if ( milvusStatement instanceof MilvusSearch search ) {
			if ( search.getAnnsField() != null ) {
				if ( vectorColumn.getColumnExpression().equals( search.getAnnsField() )
					&& isVectorEqual( search.getData().get( 0 ), vectorTypedValue ) ) {
					// Already applied this vector search
					return;
				}
				else {
					// todo (milvus): actually, this isn't really safe. need hoisting
					//  with hoisting, support for partition searches can also be easily implemented
					final MilvusHybridSearch hybridSearch = new MilvusHybridSearch( search );
					final MilvusHybridAnnSearch searchRequest = new MilvusHybridAnnSearch();
					hybridSearch.getSearches().add( searchRequest );
					milvusSearch = searchRequest;
					milvusStatement = hybridSearch;
				}
			}
			else {
				milvusSearch = search;
			}
		}
		else if ( milvusStatement instanceof MilvusHybridSearch search ) {
			final MilvusHybridAnnSearch searchRequest = new MilvusHybridAnnSearch();
			search.getSearches().add( searchRequest );
			milvusSearch = searchRequest;
		}
		else if ( milvusStatement instanceof MilvusQuery query ) {
			assert query.getIds() == null || query.getIds().isEmpty();
			milvusSearch = new MilvusSearch( query );
			milvusStatement = (MilvusStatementDefinition) milvusSearch;
		}
		else {
			throw new UnsupportedOperationException( "Vector search only works for select queries in Milvus" );
		}

		if ( vectorTypedValue instanceof MilvusParameterValue parameterValue ) {
			// There is no need for adding the vector as filter template value,
			// since it won't appear in the filter anyway
			((AbstractMilvusQuery) milvusStatement).getFilterTemplateValues()
					.remove( "p" + parameterValue.position() );
		}
		milvusSearch.setMetricType( distanceFunctionKind.getMetricType() );
		milvusSearch.setAnnsField( vectorColumn.getColumnExpression() );
		milvusSearch.setData( List.of( vectorTypedValue ) );
		// Default the topK to the maximum possible value since it is required for ANN search
		milvusSearch.setTopK( 16384 );
	}

	private boolean isVectorEqual(MilvusTypedValue existingTypedValue, MilvusTypedValue newTypedValue) {
		// Only support parameters for now
		final MilvusParameterValue existingParameter = (MilvusParameterValue) existingTypedValue;
		final MilvusParameterValue newParameter = (MilvusParameterValue) newTypedValue;
		// The binder also is the parameter
		final JdbcParameter existingJdbcParameter = (JdbcParameter) getParameterBinders().get( existingParameter.index() );
		final JdbcParameter newJdbcParameter = (JdbcParameter) getParameterBinders().get( newParameter.index() );
		return existingJdbcParameter.getParameterId() != null
			&& existingJdbcParameter.getParameterId().equals( newJdbcParameter.getParameterId() );
	}

	private void addVectorSearchPredicate(Expression expression, ComparisonOperator operator, DistanceFunctionKind distanceFunctionKind) {
		final MilvusSearchRequest searchRequest;
		if ( milvusStatement instanceof MilvusHybridSearch hybridSearch ) {
			searchRequest = hybridSearch.getSearches().get( hybridSearch.getSearches().size() - 1 );
		}
		else {
			searchRequest = (MilvusSearch) milvusStatement;
		}
		Map<String, MilvusTypedValue> searchParams = searchRequest.getSearchParams();
		if ( searchParams == null ) {
			searchRequest.setSearchParams( searchParams = new HashMap<>( 2 ) );
		}
		final MilvusTypedValue expressionTypedValue = renderValue( expression );
		// Have to invert the operator for COSINE distance, because Milvus uses COSINE similarity which is the opposite
		// of COSINE distance i.e. similarity 0 in the range [0..2] has a score of 1,
		// whereas distance 1 in the range [-1..1] has a score of 1.
		final ComparisonOperator op = distanceFunctionKind == DistanceFunctionKind.COSINE ? operator.invert() : operator;
		final MilvusTypedValue radius =
				distanceFunctionKind.determineRadius( expressionTypedValue, op, getParameterBinders() );
		final MilvusTypedValue rangeFilter =
				distanceFunctionKind.determineRangeFilter( expressionTypedValue, op, getParameterBinders() );
		if ( radius != null ) {
			searchParams.put( "radius", radius );
		}
		else {
			searchParams.put( "radius", new MilvusNumberValue( switch ( distanceFunctionKind ) {
				case COSINE, INNER_PRODUCT, NEGATIVE_INNER_PRODUCT -> -Double.MAX_VALUE;
				default -> Double.MAX_VALUE;
			} ) );
		}
		if ( rangeFilter != null ) {
			searchParams.put( "range_filter", rangeFilter );
		}
	}

	private DistanceFunctionKind getDistanceFunctionKind(FunctionExpression functionExpression) {
		return switch ( functionExpression.getFunctionName() ) {
			case "cosine_distance" -> DistanceFunctionKind.COSINE;
			case "euclidean_distance" -> DistanceFunctionKind.EUCLIDEAN;
			case "euclidean_squared_distance" -> DistanceFunctionKind.EUCLIDEAN_SQUARED;
			case "inner_product" -> DistanceFunctionKind.INNER_PRODUCT;
			case "negative_inner_product" -> DistanceFunctionKind.NEGATIVE_INNER_PRODUCT;
			case "hamming_distance" -> DistanceFunctionKind.HAMMING;
			case "jaccard_distance" -> DistanceFunctionKind.JACCARD;
			default -> null;
		};
	}

	enum DistanceFunctionKind {
		COSINE,
		EUCLIDEAN,
		EUCLIDEAN_SQUARED,
		INNER_PRODUCT,
		NEGATIVE_INNER_PRODUCT,
		HAMMING,
		JACCARD;

		public SortDirection getDefaultOrder() {
			return switch ( this ) {
				case INNER_PRODUCT -> SortDirection.DESCENDING;
				// Actually, Milvus returns cosine similarity in descending order,
				// but we work with cosine distance in HQL, which has a better score the lower the value,
				// hence ascending order. Note that the driver has to calculate the distance based on similarity later
				case EUCLIDEAN, EUCLIDEAN_SQUARED, HAMMING, JACCARD, COSINE, NEGATIVE_INNER_PRODUCT -> SortDirection.ASCENDING;
			};
		}

		public IndexParam.MetricType getMetricType() {
			return switch ( this ) {
				case COSINE -> IndexParam.MetricType.COSINE;
				case EUCLIDEAN, EUCLIDEAN_SQUARED -> IndexParam.MetricType.L2;
				case HAMMING -> IndexParam.MetricType.HAMMING;
				case INNER_PRODUCT, NEGATIVE_INNER_PRODUCT -> IndexParam.MetricType.IP;
				case JACCARD -> IndexParam.MetricType.JACCARD;
			};
		}

		private Function<Double, Float> distanceFilterValueTransformer(FloatOffset offset) {
			// For some metrics, the radius or range_filter values must be transformed to match the Milvus value range,
			// since they are passed in with the distance value range of the respective metric.
			return switch ( this ) {
				// Cosine distance is in the range [0..2] with 0 being a perfect match,
				// whereas Milvus requires a similarity value in the range [-1..1] with 1 being a perfect match,
				// so transform the distance to similarity with `1 - distance`
				case COSINE -> distance -> offset.apply( 1D - distance );
				// Emulate negative inner product by flipping the sign
				case NEGATIVE_INNER_PRODUCT -> distance -> -(offset.apply(distance ));
				// In Milvus, the euclidean distance is always squared, so square the square root distance value
				case EUCLIDEAN -> distance -> offset.apply( Math.pow(distance, 2D) );
				default -> offset::apply;
			};
		}

		private JdbcParameterBinder wrapDistanceFilterParameterBinder(JdbcParameterBinder jdbcParameterBinder, Function<Double, Float> valueTransformer) {
			if ( valueTransformer != null ) {
				if ( jdbcParameterBinder instanceof SqlTypedMappingJdbcParameter parameter ) {
					return new TransformingSqlTypedMappingJdbcParameter( parameter, valueTransformer );
				}
				else {
					return new TransformingJdbcParameter( (JdbcParameter) jdbcParameterBinder, valueTransformer );
				}
			}
			else {
				return jdbcParameterBinder;
			}
		}

		public MilvusTypedValue determineRadius(MilvusTypedValue expressionTypedValue, ComparisonOperator operator, List<JdbcParameterBinder> parameterBinders) {
			// See https://milvus.io/docs/range-search.md
			final FloatOffset offset = switch ( operator ) {
				case NOT_EQUAL, DISTINCT_FROM -> throw new UnsupportedOperationException("Milvus does not support unequal operator for distance");
				case EQUAL, NOT_DISTINCT_FROM -> switch ( this ) {
					case INNER_PRODUCT, NEGATIVE_INNER_PRODUCT, COSINE -> FloatOffset.PREVIOUS;
					default -> FloatOffset.NEXT;
				};
				case GREATER_THAN -> switch ( this ) {
					case INNER_PRODUCT, NEGATIVE_INNER_PRODUCT, COSINE -> FloatOffset.ZERO;
					default -> null;
				};
				case GREATER_THAN_OR_EQUAL -> switch ( this ) {
					case INNER_PRODUCT, NEGATIVE_INNER_PRODUCT, COSINE -> FloatOffset.PREVIOUS;
					default -> null;
				};
				case LESS_THAN -> switch ( this ) {
					case INNER_PRODUCT, NEGATIVE_INNER_PRODUCT, COSINE -> null;
					default -> FloatOffset.ZERO;
				};
				case LESS_THAN_OR_EQUAL -> switch ( this ) {
					case INNER_PRODUCT, NEGATIVE_INNER_PRODUCT, COSINE -> null;
					default -> FloatOffset.PREVIOUS;
				};
			};
			if ( offset != null ) {
				final Function<Double, Float> distanceFilterValueTransformer = distanceFilterValueTransformer( offset );
				if ( expressionTypedValue instanceof MilvusParameterValue parameterValue ) {
					JdbcParameterBinder jdbcParameterBinder = parameterBinders.get( parameterValue.index() );
					parameterBinders.set(
							parameterValue.index(),
							wrapDistanceFilterParameterBinder( jdbcParameterBinder, distanceFilterValueTransformer )
					);
					return expressionTypedValue;
				}
				else {
					final MilvusNumberValue numberValue = (MilvusNumberValue) expressionTypedValue;
					return new MilvusNumberValue(
							distanceFilterValueTransformer.apply( numberValue.value().doubleValue() )
					);
				}
			}
			else {
				return null;
			}
		}

		public MilvusTypedValue determineRangeFilter(MilvusTypedValue expressionTypedValue, ComparisonOperator operator, List<JdbcParameterBinder> parameterBinders) {
			// See https://milvus.io/docs/range-search.md
			final FloatOffset offset = switch ( operator ) {
				case NOT_EQUAL, DISTINCT_FROM -> throw new UnsupportedOperationException("Milvus does not support unequal operator for distance");
				case EQUAL, NOT_DISTINCT_FROM -> FloatOffset.ZERO;
				case GREATER_THAN -> switch ( this ) {
					case INNER_PRODUCT, NEGATIVE_INNER_PRODUCT, COSINE -> null;
					default -> FloatOffset.NEXT;
				};
				case GREATER_THAN_OR_EQUAL -> switch ( this ) {
					case INNER_PRODUCT, NEGATIVE_INNER_PRODUCT, COSINE -> null;
					default -> FloatOffset.ZERO;
				};
				case LESS_THAN -> switch ( this ) {
					case INNER_PRODUCT, NEGATIVE_INNER_PRODUCT, COSINE -> FloatOffset.NEXT;
					default -> null;
				};
				case LESS_THAN_OR_EQUAL -> switch ( this ) {
					case INNER_PRODUCT, NEGATIVE_INNER_PRODUCT, COSINE -> FloatOffset.ZERO;
					default -> null;
				};
			};
			if ( offset != null ) {
				final Function<Double, Float> distanceFilterValueTransformer = distanceFilterValueTransformer( offset );
				if ( expressionTypedValue instanceof MilvusParameterValue parameterValue ) {
					final JdbcParameterBinder jdbcParameterBinder = parameterBinders.get( parameterValue.index() );
					final JdbcParameterBinder wrappedBinder =
							wrapDistanceFilterParameterBinder( jdbcParameterBinder, distanceFilterValueTransformer );
					if ( jdbcParameterBinder instanceof TransformingJdbcParameter
							|| jdbcParameterBinder instanceof TransformingSqlTypedMappingJdbcParameter ) {
						parameterBinders.add( parameterValue.index(), wrappedBinder );
					}
					else {
						parameterBinders.set( parameterValue.index(), wrappedBinder );
					}
					return expressionTypedValue;
				}
				else {
					final MilvusNumberValue numberValue = (MilvusNumberValue) expressionTypedValue;
					return new MilvusNumberValue(
							distanceFilterValueTransformer.apply( numberValue.value().doubleValue() )
					);
				}
			}
			else {
				return null;
			}
		}

		enum FloatOffset {
			ZERO,
			NEXT,
			PREVIOUS;

			public float apply(double f) {
				return switch ( this ) {
					case ZERO -> (float) f;
					case NEXT -> Math.nextUp( (float) f );
					case PREVIOUS -> Math.nextDown( (float) f );
				};
			}
		}

		private static class TransformingJdbcParameter extends AbstractJdbcParameter {
			private final JdbcParameter parameter;
			private final Function<Double, Float> valueTransformer;

			public TransformingJdbcParameter(JdbcParameter parameter, Function<Double, Float> valueTransformer) {
				super( parameter.getExpressionType().getSingleJdbcMapping(), parameter.getParameterId() );
				this.parameter = parameter;
				this.valueTransformer = valueTransformer;
			}

			@Override
			public void bindParameterValue(
					PreparedStatement statement,
					int startPosition,
					JdbcParameterBindings jdbcParamBindings,
					ExecutionContext executionContext) throws SQLException {
				final var binding = jdbcParamBindings.getBinding( parameter );
				if ( binding == null ) {
					throw new ExecutionException( "JDBC parameter value not bound - " + parameter );
				}

				final var jdbcMapping = jdbcMapping( executionContext, binding );
				//noinspection unchecked
				jdbcMapping.getJdbcValueBinder().bind(
						statement,
						(double) valueTransformer.apply( ((Number) binding.getBindValue()).doubleValue() ),
						startPosition,
						executionContext.getSession()
				);
			}

			private JdbcMapping jdbcMapping(ExecutionContext executionContext, JdbcParameterBinding binding) {
				JdbcMapping jdbcMapping = binding.getBindType();

				if ( jdbcMapping == null ) {
					jdbcMapping = getJdbcMapping();
				}

				// If the parameter type is not known from the context i.e. null or Object, infer it from the bind value
				if ( jdbcMapping == null || jdbcMapping.getMappedJavaType().getJavaTypeClass() == Object.class ) {
					jdbcMapping = guessBindType( executionContext, binding.getBindValue(), jdbcMapping );
				}

				if ( jdbcMapping == null ) {
					throw new ExecutionException( "No JDBC mapping could be inferred for parameter - " + this );
				}

				return jdbcMapping;
			}

			private JdbcMapping guessBindType(ExecutionContext executionContext, Object bindValue, JdbcMapping jdbcMapping) {
				if ( bindValue == null && jdbcMapping != null ) {
					return jdbcMapping;
				}
				else {
					final var parameterType =
							executionContext.getSession().getFactory().getMappingMetamodel()
									.resolveParameterBindType( bindValue );
					return parameterType instanceof JdbcMapping ? (JdbcMapping) parameterType : null;
				}
			}
		}

		private static class TransformingSqlTypedMappingJdbcParameter extends SqlTypedMappingJdbcParameter {
			private final SqlTypedMappingJdbcParameter parameter;
			private final Function<Double, Float> valueTransformer;

			public TransformingSqlTypedMappingJdbcParameter(SqlTypedMappingJdbcParameter parameter, Function<Double, Float> valueTransformer) {
				super( parameter.getSqlTypedMapping(), parameter.getParameterId() );
				this.parameter = parameter;
				this.valueTransformer = valueTransformer;
			}

			@Override
			public void bindParameterValue(
					PreparedStatement statement,
					int startPosition,
					JdbcParameterBindings jdbcParamBindings,
					ExecutionContext executionContext) throws SQLException {
				final var binding = jdbcParamBindings.getBinding( parameter );
				if ( binding == null ) {
					throw new ExecutionException( "JDBC parameter value not bound - " + parameter );
				}

				final var jdbcMapping = jdbcMapping( executionContext, binding );
				//noinspection unchecked
				jdbcMapping.getJdbcValueBinder().bind(
						statement,
						(double) valueTransformer.apply( ((Number) binding.getBindValue()).doubleValue() ),
						startPosition,
						executionContext.getSession()
				);
			}

			private JdbcMapping jdbcMapping(ExecutionContext executionContext, JdbcParameterBinding binding) {
				JdbcMapping jdbcMapping = binding.getBindType();

				if ( jdbcMapping == null ) {
					jdbcMapping = getJdbcMapping();
				}

				// If the parameter type is not known from the context i.e. null or Object, infer it from the bind value
				if ( jdbcMapping == null || jdbcMapping.getMappedJavaType().getJavaTypeClass() == Object.class ) {
					jdbcMapping = guessBindType( executionContext, binding.getBindValue(), jdbcMapping );
				}

				if ( jdbcMapping == null ) {
					throw new ExecutionException( "No JDBC mapping could be inferred for parameter - " + this );
				}

				return jdbcMapping;
			}

			private JdbcMapping guessBindType(ExecutionContext executionContext, Object bindValue, JdbcMapping jdbcMapping) {
				if ( bindValue == null && jdbcMapping != null ) {
					return jdbcMapping;
				}
				else {
					final var parameterType =
							executionContext.getSession().getFactory().getMappingMetamodel()
									.resolveParameterBindType( bindValue );
					return parameterType instanceof JdbcMapping ? (JdbcMapping) parameterType : null;
				}
			}
		}
	}
	@Override
	public void visitFromClause(FromClause fromClause) {
		if ( fromClause == null || fromClause.getRoots().isEmpty() ) {
			throw new UnsupportedOperationException( "Milvus doesn't support an empty from clause" );
		}
		else {
			renderFromClauseSpaces( fromClause );
		}
	}

	@Override
	protected boolean renderNamedTableReference(NamedTableReference tableReference, LockMode lockMode) {
		final AbstractMilvusQuery milvusQuery = (AbstractMilvusQuery) milvusStatement;
		if ( milvusQuery.getCollectionName() != null ) {
			if ( milvusQuery.getCollectionName().equals( tableReference.getTableId() ) ) {
				throw new UnsupportedOperationException( "Milvus doesn't support multiple from clause items" );
			}
		}
		milvusQuery.setCollectionName( tableReference.getTableId() );
		registerAffectedTable( tableReference );
		return false;
	}

	@Override
	protected void renderDmlTargetTableExpression(NamedTableReference tableReference) {
		final AbstractMilvusCollectionStatement milvusQuery = (AbstractMilvusCollectionStatement) milvusStatement;
		milvusQuery.setCollectionName( tableReference.getTableId() );
		registerAffectedTable( tableReference );
	}

	@Override
	protected String determineColumnReferenceQualifier(ColumnReference columnReference) {
		// no aliases exist, so no qualifier
		return null;
	}

	@Override
	public void visitValuesTableReference(ValuesTableReference tableReference) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitQueryPartTableReference(QueryPartTableReference tableReference) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitFunctionTableReference(FunctionTableReference tableReference) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitTableReferenceJoin(TableReferenceJoin tableReferenceJoin) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitNestedColumnReference(NestedColumnReference nestedColumnReference) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitAggregateColumnWriteExpression(AggregateColumnWriteExpression aggregateColumnWriteExpression) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitExtractUnit(ExtractUnit extractUnit) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitFormat(Format format) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitDistinct(Distinct distinct) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitOverflow(Overflow overflow) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitTrimSpecification(TrimSpecification trimSpecification) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitCastTarget(CastTarget castTarget) {
		throw new UnsupportedOperationException();
	}

	@Override
	protected void visitAnsiCaseSearchedExpression(CaseSearchedExpression caseSearchedExpression, Consumer<Expression> resultRenderer) {
		throw new UnsupportedOperationException();
	}

	@Override
	protected void visitAnsiCaseSimpleExpression(CaseSimpleExpression caseSimpleExpression, Consumer<Expression> resultRenderer) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitAny(Any any) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitEvery(Every every) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitSummarization(Summarization every) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitOver(Over<?> over) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitSqlSelectionExpression(SqlSelectionExpression expression) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitEntityTypeLiteral(EntityTypeLiteral expression) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitEmbeddableTypeLiteral(EmbeddableTypeLiteral expression) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitTuple(SqlTuple tuple) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitCollation(Collation collation) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitUnaryOperationExpression(UnaryOperation unaryOperationExpression) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitModifiedSubQueryExpression(ModifiedSubQueryExpression expression) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitBooleanExpressionPredicate(BooleanExpressionPredicate booleanExpressionPredicate) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitFilterPredicate(FilterPredicate filterPredicate) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitFilterFragmentPredicate(FilterPredicate.FilterFragmentPredicate fragmentPredicate) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitSqlFragmentPredicate(SqlFragmentPredicate predicate) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitInSubQueryPredicate(InSubQueryPredicate inSubQueryPredicate) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitExistsPredicate(ExistsPredicate existsPredicate) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitThruthnessPredicate(ThruthnessPredicate predicate) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitDurationUnit(DurationUnit durationUnit) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitDuration(Duration duration) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitConversion(Conversion conversion) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitStandardTableInsert(TableInsertStandard tableInsert) {
		getCurrentClauseStack().push( Clause.INSERT );
		try {
			MilvusInsert insert = new MilvusInsert();
			milvusStatement = insert;

			insert.setCollectionName( tableInsert.getMutatingTable().getTableName() );
			registerAffectedTable( tableInsert.getMutatingTable().getTableName() );

			getCurrentClauseStack().push( Clause.VALUES );
			try {
				Map<String, MilvusTypedValue> values = new LinkedHashMap<>();
				tableInsert.forEachValueBinding( (columnPosition, columnValueBinding) -> {
					if (!columnValueBinding.getValueExpression().getFragment().equals( "?" )) {
						throw new UnsupportedOperationException( "Need a simple parameter expression for inserts");
					}
					if (columnValueBinding.getValueExpression().getParameters().size() != 1) {
						throw new UnsupportedOperationException( "Need a parameter expression for inserts");
					}
					values.put(columnValueBinding.getColumnReference().getColumnExpression(), renderValue( columnValueBinding.getValueExpression() ) );
				} );
				addDefaultEmbeddingDataIfNeeded( values, tableInsert );
				insert.getData().add( values );
			}
			finally {
				getCurrentClauseStack().pop();
			}

			if ( tableInsert.getNumberOfReturningColumns() > 0 ) {
				visitReturningColumns( tableInsert::getReturningColumns );
			}
		}
		finally {
			getCurrentClauseStack().pop();
		}
		applyHints();
	}

	private boolean hasVector(ModelPartContainer modelPartContainer) {
		if ( modelPartContainer instanceof EntityMappingType entityMappingType ) {
			final var attributeMappings = entityMappingType.getAttributeMappings();
			final var attributeCount = entityMappingType.getNumberOfAttributeMappings();
			for ( int i = 0; i < attributeCount; i++ ) {
				if ( hasVector( attributeMappings.get( i ) ) ) {
					return true;
				}
			}
			return false;
		}
		else {
			return hasVector( (JdbcMappingContainer) modelPartContainer );
		}
	}

	private boolean hasVector(JdbcMappingContainer modelPartContainer) {
		final int jdbcTypeCount = modelPartContainer.getJdbcTypeCount();
		for ( int i = 0; i < jdbcTypeCount; i++ ) {
			if ( isVector( modelPartContainer.getJdbcMapping( i ).getJdbcType().getDdlTypeCode() ) ) {
				return true;
			}
		}
		return false;
	}

	static boolean isVector(int jdbcTypeCode) {
		return switch ( jdbcTypeCode ) {
			case SqlTypes.VECTOR, SqlTypes.VECTOR_BINARY, SqlTypes.VECTOR_INT8, SqlTypes.VECTOR_FLOAT16,
				SqlTypes.VECTOR_FLOAT32, SqlTypes.VECTOR_FLOAT64, SqlTypes.SPARSE_VECTOR_INT8,
				SqlTypes.SPARSE_VECTOR_FLOAT32, SqlTypes.SPARSE_VECTOR_FLOAT64 -> true;
			default -> false;
		};
	}

	@Override
	public void visitCustomTableInsert(TableInsertCustomSql tableInsert) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitStandardTableDelete(TableDeleteStandard tableDelete) {
		if ( tableDelete.getNumberOfOptimisticLockBindings() > 0 ) {
			throw new UnsupportedOperationException( "Optimistic locking not supported on Milvus" );
		}

		getCurrentClauseStack().push( Clause.DELETE );
		try {
			MilvusDelete delete = new MilvusDelete();
			milvusStatement = delete;

			delete.setCollectionName( tableDelete.getMutatingTable().getTableName() );
			registerAffectedTable( tableDelete.getMutatingTable().getTableName() );

			getCurrentClauseStack().push( Clause.WHERE );
			try {
				if ( tableDelete.getWhereFragment() == null ) {
					final ArrayList<MilvusTypedValue> values = new ArrayList<>( 1 );
					tableDelete.forEachKeyBinding( (columnPosition, columnValueBinding) -> {
						values.add( renderValue( columnValueBinding.getValueExpression() ) );
					} );
					delete.setIds( values );
				}
				else {
					tableDelete.forEachKeyBinding( (columnPosition, columnValueBinding) -> {
						appendSql( columnValueBinding.getColumnReference().getColumnExpression() );
						appendSql( "==" );
						columnValueBinding.getValueExpression().accept( this );

						if ( columnPosition < tableDelete.getNumberOfKeyBindings() - 1 ) {
							appendSql( " and " );
						}
					} );

					appendSql( " and (" );
					appendSql( tableDelete.getWhereFragment() );
					appendSql( ")" );

					delete.setFilter( determineFilter() );
					getSqlBuffer().setLength( 0 );
				}
			}
			finally {
				getCurrentClauseStack().pop();
			}
		}
		finally {
			getCurrentClauseStack().pop();
		}
		applyHints();
	}

	@Override
	public void visitCustomTableDelete(TableDeleteCustomSql tableDelete) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitStandardTableUpdate(TableUpdateStandard tableUpdate) {
		if ( tableUpdate.getNumberOfOptimisticLockBindings() > 0 ) {
			throw new UnsupportedOperationException( "Optimistic locking not supported on Milvus" );
		}
		if ( tableUpdate.getWhereFragment() != null ) {
			throw new UnsupportedOperationException( "Non-id predicates are not supported for updates on Milvus" );
		}
		if ( tableUpdate.getNumberOfReturningColumns() > 0 ) {
			throw new UnsupportedOperationException( "Returning clause is not supported for updates on Milvus" );
		}

		getCurrentClauseStack().push( Clause.UPDATE );
		try {
			MilvusUpsert update = new MilvusUpsert();
			milvusStatement = update;

			update.setCollectionName( tableUpdate.getMutatingTable().getTableName() );
			registerAffectedTable( tableUpdate.getMutatingTable().getTableName() );

			Map<String, MilvusTypedValue> data = new LinkedHashMap<>();
			update.setData( List.of( data ) );

			getCurrentClauseStack().push( Clause.SET );
			try {
				tableUpdate.forEachValueBinding( (columnPosition, columnValueBinding) -> {
					data.put( columnValueBinding.getColumnReference().getColumnExpression(), renderValue( columnValueBinding.getValueExpression() ) );
				} );
				addDefaultEmbeddingDataIfNeeded( data, tableUpdate );
			}
			finally {
				getCurrentClauseStack().pop();
			}

			getCurrentClauseStack().push( Clause.WHERE );
			try {
				tableUpdate.forEachKeyBinding( (position, columnValueBinding) -> {
					data.put( columnValueBinding.getColumnReference().getColumnExpression(), renderValue( columnValueBinding.getValueExpression() ) );
				} );
			}
			finally {
				getCurrentClauseStack().pop();
			}
		}
		finally {
			getCurrentClauseStack().pop();
		}
		applyHints();
	}

	@Override
	public void visitOptionalTableUpdate(OptionalTableUpdate tableUpdate) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitCustomTableUpdate(TableUpdateCustomSql tableUpdate) {
		throw new UnsupportedOperationException();
	}
}
