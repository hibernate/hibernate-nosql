/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus;

import io.milvus.v2.common.IndexParam;
import org.hibernate.LockMode;
import org.hibernate.Locking;
import org.hibernate.dialect.SelectItemReferenceStrategy;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.internal.util.MathHelper;
import org.hibernate.internal.util.collections.Stack;
import org.hibernate.metamodel.mapping.EntityIdentifierMapping;
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
import org.hibernate.query.sqm.ComparisonOperator;
import org.hibernate.query.sqm.sql.internal.BasicValuedPathInterpretation;
import org.hibernate.query.sqm.sql.internal.SqmParameterInterpretation;
import org.hibernate.query.sqm.tree.expression.Conversion;
import org.hibernate.query.sqm.tuple.internal.AnonymousTupleTableGroupProducer;
import org.hibernate.sql.ast.Clause;
import org.hibernate.sql.ast.SqlAstNodeRenderingMode;
import org.hibernate.sql.ast.spi.AbstractSqlAstTranslator;
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
import org.hibernate.sql.ast.tree.predicate.SelfRenderingPredicate;
import org.hibernate.sql.ast.tree.predicate.ThruthnessPredicate;
import org.hibernate.sql.ast.tree.select.QueryGroup;
import org.hibernate.sql.ast.tree.select.QuerySpec;
import org.hibernate.sql.ast.tree.select.SelectClause;
import org.hibernate.sql.ast.tree.select.SelectStatement;
import org.hibernate.sql.ast.tree.select.SortSpecification;
import org.hibernate.sql.ast.tree.update.Assignment;
import org.hibernate.sql.ast.tree.update.UpdateStatement;
import org.hibernate.sql.exec.spi.JdbcOperation;
import org.hibernate.sql.model.ast.ColumnValueParameter;
import org.hibernate.sql.model.ast.ColumnWriteFragment;
import org.hibernate.sql.model.internal.OptionalTableUpdate;
import org.hibernate.sql.model.internal.TableDeleteCustomSql;
import org.hibernate.sql.model.internal.TableDeleteStandard;
import org.hibernate.sql.model.internal.TableInsertCustomSql;
import org.hibernate.sql.model.internal.TableInsertStandard;
import org.hibernate.sql.model.internal.TableUpdateCustomSql;
import org.hibernate.sql.model.internal.TableUpdateStandard;
import org.hibernate.type.BasicPluralType;
import org.hibernate.type.SqlTypes;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public class MilvusSqlAstTranslator<T extends JdbcOperation> extends AbstractSqlAstTranslator<T> {

	private static final String TRUE_CONSTANT = "1==1";

	private MilvusStatementDefinition milvusStatement;

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
	public void render(SqlAstNode sqlAstNode, SqlAstNodeRenderingMode renderingMode) {
		throw new UnsupportedOperationException();
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
	}

	@Override
	protected void visitUpdateStatementOnly(UpdateStatement statement) {
		renderUpdateClause( statement );
		renderSetClause( statement.getAssignments() );
		visitWhereClause( statement.getRestriction() );
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
		throw new UnsupportedOperationException();
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
					throw new UnsupportedOperationException( "Milvus only supports ordering by vector distance" );
				}
			}

			visitSelectClause( querySpec.getSelectClause() );
			visitFromClause( querySpec.getFromClause() );
			visitWhereClause( querySpec );
			visitGroupByClause( querySpec );
			visitOffsetFetchClause( querySpec );
			visitForUpdateClause( querySpec );
		}
		finally {
			getQueryPartStack().pop();
		}
	}

	protected void visitWhereClause(QuerySpec querySpec) {
		assert getSqlBuffer().isEmpty();

		final Predicate whereClauseRestrictions = querySpec.getWhereClauseRestrictions();
		if ( milvusStatement instanceof MilvusUpsert upsert ) {
			final Predicate predicate = Predicate.combinePredicates( whereClauseRestrictions, getAdditionalWherePredicate() );
			if ( predicate == null && predicate.isEmpty() ) {
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
				delete.setFilter( determineFilter() );
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
			addVectorSearchPredicate( rhs, operator );
			appendSql( TRUE_CONSTANT );
		}
		else if ( rhs instanceof FunctionExpression functionExpression
			&& (distanceFunctionKind = getDistanceFunctionKind( functionExpression )) != null ) {
			addVectorSearch( distanceFunctionKind, functionExpression );
			addVectorSearchPredicate( lhs, operator.invert() );
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
			final int position = getParameterBinders().size();
			final String oldBuffer = getSqlBuffer().toString();
			getSqlBuffer().setLength( 0 );

			expression.accept( this );

			getSqlBuffer().setLength( 0 );
			getSqlBuffer().append( oldBuffer );
			return new MilvusParameterValue( position );
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
		final int position = getParameterBinders().size();
		super.visitParameterAsParameter( jdbcParameter );
		Map<String, MilvusTypedValue> filterTemplateValues;
		if ( milvusStatement instanceof AbstractMilvusQuery query ) {
			filterTemplateValues = query.getFilterTemplateValues();
			if ( filterTemplateValues == null ) {
				query.setFilterTemplateValues( filterTemplateValues = new LinkedHashMap<>() );
			}
		}
		else if ( milvusStatement instanceof MilvusDelete delete ) {
			filterTemplateValues = delete.getFilterTemplateValues();
			if ( filterTemplateValues == null ) {
				delete.setFilterTemplateValues( filterTemplateValues = new LinkedHashMap<>() );
			}
		}
		else {
			throw new UnsupportedOperationException( "Unsupported statement: " + milvusStatement );
		}
		filterTemplateValues.put( "p" + getParameterBinders().size(), new MilvusParameterValue( position ) );
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
		appendSql( " in [" );
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
			if ( !(listExpression instanceof JdbcParameter || listExpression instanceof SqmParameterInterpretation || listExpression instanceof Literal ) ) {
				inExprLimit = 0;
				bindValueCountWithPadding = bindValueCount;
			}
		}

		appendSql( ']' );
		if ( parenthesis ) {
			appendSql( CLOSE_PARENTHESIS );
		}
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
			addVectorSearchPredicate( betweenPredicate.getLowerBound(), ComparisonOperator.GREATER_THAN_OR_EQUAL );
			addVectorSearchPredicate( betweenPredicate.getUpperBound(), ComparisonOperator.LESS_THAN_OR_EQUAL );
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
		if ( required && (!(lhs instanceof ColumnReference columnReference) || !isPrimaryKey( columnReference ) )) {
			throw new UnsupportedOperationException( "Unsupported LHS expression type for by id predicate: " + predicate );
		}
		return idsValues;
	}

	private boolean isPrimaryKey(ColumnReference columnReference) {
		final QuerySpec querySpec = getCurrentQueryPart().getFirstQuerySpec();
		final TableGroup tableGroup = querySpec.getFromClause().getRoots().get( 0 );
		return tableGroup.findTableReference( columnReference.getQualifier() ) != null
			&& getPrimaryKey().getSelectionExpression().equals( columnReference.getColumnExpression() );
	}

	private SelectableMapping getPrimaryKey() {
		final QuerySpec querySpec = getCurrentQueryPart().getFirstQuerySpec();
		final List<TableGroup> roots = querySpec.getFromClause().getRoots();
		assert roots.size() == 1;
		final TableGroup tableGroup = roots.get( 0 );
		final EntityIdentifierMapping identifierMapping = tableGroup.getModelPart().asEntityMappingType().getIdentifierMapping();
		assert identifierMapping.getJdbcTypeCount() == 1;
		return identifierMapping.getSelectable( 0 );
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
		if ( expression instanceof ColumnReference columnReference ) {
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
		final Number offset = getLiteralValue( fetchExpression );
		milvusQuery.setLimit( offset.longValue() );
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
		final DistanceFunctionKind distanceFunctionKind;
		if ( expression instanceof ColumnReference columnReference ) {
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
			milvusQuery.getOutputFields().add( MilvusHelper.DISTANCE_FIELD );
			addVectorSearch( distanceFunctionKind, functionExpression );
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
		final JdbcParameter existingJdbcParameter = (JdbcParameter) getParameterBinders().get( existingParameter.position() );
		final JdbcParameter newJdbcParameter = (JdbcParameter) getParameterBinders().get( newParameter.position() );
		return existingJdbcParameter.getParameterId() != null
			&& existingJdbcParameter.getParameterId().equals( newJdbcParameter.getParameterId() );
	}

	private void addVectorSearchPredicate(Expression expression, ComparisonOperator operator) {
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
		switch ( operator ) {
			case NOT_EQUAL, DISTINCT_FROM -> throw new UnsupportedOperationException("Milvus does not support unequal operator for distance");
			case EQUAL, NOT_DISTINCT_FROM -> {
				searchParams.put( "radius", renderValue( expression ) );
				searchParams.put( "range_filter", renderValue( expression ) );
			}
			case GREATER_THAN, GREATER_THAN_OR_EQUAL -> searchParams.put( "radius", renderValue( expression ) );
			case LESS_THAN, LESS_THAN_OR_EQUAL -> searchParams.put( "range_filter", renderValue( expression ) );
		}
	}

	private DistanceFunctionKind getDistanceFunctionKind(FunctionExpression functionExpression) {
		return switch ( functionExpression.getFunctionName() ) {
			case "cosine_distance" -> DistanceFunctionKind.COSINE;
			case "euclidean_distance" -> DistanceFunctionKind.L2;
			case "inner_product" -> DistanceFunctionKind.INNER_PRODUCT;
			case "hamming_distance" -> DistanceFunctionKind.HAMMING;
			default -> null;
		};
	}

	enum DistanceFunctionKind {
		COSINE,
		L2,
		INNER_PRODUCT,
		HAMMING;

		public SortDirection getDefaultOrder() {
			return switch ( this ) {
				case COSINE, INNER_PRODUCT -> SortDirection.DESCENDING;
				case L2, HAMMING -> SortDirection.ASCENDING;
			};
		}

		public IndexParam.MetricType getMetricType() {
			return switch ( this ) {
				case COSINE -> IndexParam.MetricType.COSINE;
				case L2 -> IndexParam.MetricType.L2;
				case HAMMING -> IndexParam.MetricType.HAMMING;
				case INNER_PRODUCT -> IndexParam.MetricType.IP;
			};
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
	public void visitStar(Star star) {
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
	public void visitSelfRenderingExpression(SelfRenderingExpression expression) {
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
	public void visitSelfRenderingPredicate(SelfRenderingPredicate selfRenderingPredicate) {
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
				if ( !hasVector( tableInsert.getMutationTarget().getTargetPart() ) ) {
					values.put( "embedding", new MilvusFloatVectorValue( new float[]{ 0, 0 } ) );
				}
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
	}

	private boolean hasVector(ModelPartContainer modelPartContainer) {
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
			case SqlTypes.VECTOR, SqlTypes.VECTOR_FLOAT32, SqlTypes.VECTOR_FLOAT64, SqlTypes.VECTOR_INT8 -> true;
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
				if ( !hasVector( tableUpdate.getMutationTarget().getTargetPart() ) ) {
					data.put( "embedding", new MilvusFloatVectorValue( new float[]{ 0, 0 } ) );
				}
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
