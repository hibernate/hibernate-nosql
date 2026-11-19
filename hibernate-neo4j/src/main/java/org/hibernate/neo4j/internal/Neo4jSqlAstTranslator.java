/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.neo4j.internal;

import org.hibernate.dialect.sql.ast.spi.ReturningRenderingPlan;
import org.hibernate.dialect.sql.ast.spi.ReturningRenderingSupport;
import org.hibernate.dialect.sql.ast.spi.SelectItemReferenceStrategy;
import org.hibernate.dialect.sql.ast.spi.SetReturningFunctionRenderingSupport;
import org.hibernate.dialect.sql.ast.spi.SqlAstTranslationRequest;
import org.hibernate.dialect.sql.ast.spi.SubquerySupport;
import org.hibernate.sql.ast.spi.query.from.LazyTableGroup;
import org.hibernate.AssertionFailure;
import org.hibernate.LockMode;
import org.hibernate.internal.util.MathHelper;
import org.hibernate.spi.Stack;
import org.hibernate.metamodel.mapping.ModelPartContainer;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.query.sqm.sql.internal.SqmParameterInterpretation;
import org.hibernate.sql.ast.spi.translation.Clause;
import org.hibernate.sql.ast.spi.query.from.SqlAstJoinType;
import org.hibernate.sql.ast.internal.TableGroupHelper;
import org.hibernate.dialect.sql.ast.spi.AbstractSqlAstTranslator;
import org.hibernate.sql.ast.spi.Statement;
import org.hibernate.sql.ast.spi.query.delete.DeleteStatement;
import org.hibernate.sql.ast.spi.query.expression.ColumnReference;
import org.hibernate.sql.ast.spi.query.expression.Expression;
import org.hibernate.sql.ast.spi.query.expression.JdbcParameter;
import org.hibernate.sql.ast.spi.query.expression.Literal;
import org.hibernate.sql.ast.spi.query.expression.QueryLiteral;
import org.hibernate.sql.ast.spi.query.expression.SqlTuple;
import org.hibernate.sql.ast.spi.query.expression.SqlTupleContainer;
import org.hibernate.sql.ast.spi.query.from.FromClause;
import org.hibernate.sql.ast.spi.query.from.NamedTableReference;
import org.hibernate.sql.ast.spi.query.from.TableGroup;
import org.hibernate.sql.ast.spi.query.from.TableGroupJoin;
import org.hibernate.sql.ast.spi.query.from.TableReference;
import org.hibernate.sql.ast.spi.query.from.TableReferenceJoin;
import org.hibernate.sql.ast.spi.query.predicate.BetweenPredicate;
import org.hibernate.sql.ast.spi.query.predicate.BooleanExpressionPredicate;
import org.hibernate.sql.ast.spi.query.predicate.InListPredicate;
import org.hibernate.sql.ast.spi.query.predicate.Predicate;
import org.hibernate.sql.ast.spi.query.select.QuerySpec;
import org.hibernate.sql.ast.spi.query.select.SelectClause;
import org.hibernate.sql.ast.spi.query.select.SelectStatement;
import org.hibernate.sql.ast.spi.query.update.Assignment;
import org.hibernate.sql.ast.spi.query.update.UpdateStatement;
import org.hibernate.sql.exec.spi.JdbcOperation;
import org.hibernate.sql.ast.spi.model.TableDeleteStandard;
import org.hibernate.sql.ast.spi.model.TableInsertCustomSql;
import org.hibernate.sql.ast.spi.model.TableInsertStandard;
import org.hibernate.sql.ast.spi.model.TableUpdateStandard;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import static org.hibernate.internal.util.StringHelper.isBlank;
import static org.hibernate.sql.ast.spi.query.expression.SqlTupleContainer.getSqlTuple;

public class Neo4jSqlAstTranslator<T extends JdbcOperation> extends AbstractSqlAstTranslator<T> {

	/**
	 * Alias used to qualify the columns of the returning clause. The base translator renders the
	 * returning clause as {@code returning <columns>} once the mutation itself was rendered. Cypher
	 * requires {@code return <alias>.<column>} instead, which {@link #appendSql(String)} adapts.
	 */
	private String returningAlias;

	public Neo4jSqlAstTranslator(SqlAstTranslationRequest<? extends Statement, T> request) {
		super( request );
	}

	@Override
	public void appendSql(String fragment) {
		if ( returningAlias != null ) {
			if ( " returning ".equals( fragment ) ) {
				super.appendSql( " return " );
				return;
			}
			else if ( !fragment.isEmpty() && !COMMA_SEPARATOR.equals( fragment ) ) {
				super.appendSql( returningAlias + "." + fragment );
				return;
			}
		}
		super.appendSql( fragment );
	}

	@Override
	protected ReturningRenderingSupport getReturningRenderingSupport() {
		return request -> request.returningColumns().isEmpty()
				? new ReturningRenderingPlan.None()
				: new ReturningRenderingPlan.Clause();
	}

	@Override
	protected SetReturningFunctionRenderingSupport getSetReturningFunctionRenderingSupport() {
		return request -> {
			throw new UnsupportedOperationException();
		};
	}

	@Override
	protected SelectItemReferenceStrategy getGroupBySelectItemReferenceStrategy() {
		return SelectItemReferenceStrategy.ALIAS;
	}



	@Override
	public void visitQueryClauses(QuerySpec querySpec) {
		renderMatchClause( querySpec.getFromClause() ); // match
		if ( hasWhere( querySpec.getWhereClauseRestrictions() ) && querySpec.getFromClause().hasJoins() ) {
			// A previous join might have scoped a where clause to that match, so we need to reset the scope
			appendSql( " with *" );
		}
		visitWhereClause( querySpec.getWhereClauseRestrictions() );
		super.visitForUpdateClause( querySpec );
		visitSelectClause( querySpec.getSelectClause() ); // return
		visitGroupByClause( querySpec, getGroupBySelectItemReferenceStrategy() );
		visitHavingClause( querySpec.getHavingClauseRestrictions() );
		visitOrderBy( querySpec.getSortSpecifications() );
		visitOffsetFetchClause( querySpec );
	}


	private void renderMatchClause(FromClause fromClause) {
		if ( hasFrom( fromClause ) ) {
			appendSql( "match " );
			renderMatchSpaces( fromClause );
		}
	}

	private void renderMatchSpaces(FromClause fromClause) {
		try {
			getCurrentClauseStack().push( Clause.FROM );
			String separator = NO_SEPARATOR;
			for ( TableGroup root : fromClause.getRoots() ) {
				separator = renderFromClauseRoot( root, separator );
			}
		}
		finally {
			getCurrentClauseStack().pop();
		}
	}

	private String renderFromClauseRoot(TableGroup root, String separator) {
		if ( root.isVirtual() ) {
			for ( TableGroupJoin tableGroupJoin : root.getTableGroupJoins() ) {
				addAdditionalWherePredicate( tableGroupJoin.getPredicate() );
				separator = renderFromClauseRoot( tableGroupJoin.getJoinedGroup(), separator );
			}
			for ( TableGroupJoin tableGroupJoin : root.getNestedTableGroupJoins() ) {
				addAdditionalWherePredicate( tableGroupJoin.getPredicate() );
				separator = renderFromClauseRoot( tableGroupJoin.getJoinedGroup(), separator );
			}
		}
		else if ( root.isInitialized() ) {
			appendSql( separator );
			renderRootTableGroup( root, null );
			separator = COMMA_SEPARATOR;
		}
		return separator;
	}

	protected boolean renderNamedTableReference(NamedTableReference tableReference, LockMode lockMode) {
		appendSql( '(' );
		appendSql( getTableAlias( tableReference ) );
		appendSql( ':' );
		appendSql( tableReference.getTableExpression() );
		appendSql( ')' );
		registerAffectedTable( tableReference );
		return false;
	}

	@Override
	protected void visitForUpdateClause(QuerySpec querySpec) {
		// No-op, handled in visitQueryClauses
	}


	@Override
	protected void renderSelectClause(SelectClause selectClause) {
		appendSql( " return " );
		if ( selectClause.isDistinct() ) {
			appendSql( "distinct " );
		}
		renderSelectItems( selectClause );
		renderVirtualSelections( selectClause );
	}

	private void renderTableGroupJoin(TableGroupJoin tableGroupJoin, List<TableGroupJoin> tableGroupJoinCollector) {
		appendSql( WHITESPACE );
		if ( tableGroupJoin.getJoinType() != SqlAstJoinType.INNER ) {
			appendSql( "optional " );
		}
		appendSql( "match " );

		final Predicate predicate;
		if ( tableGroupJoin.getPredicate() == null ) {
			if ( tableGroupJoin.getJoinType() == SqlAstJoinType.CROSS ) {
				predicate = null;
			}
			else {
				predicate = new BooleanExpressionPredicate( new QueryLiteral<>( true, getBooleanType() ) );
			}
		}
		else {
			predicate = tableGroupJoin.getPredicate();
		}
		if ( predicate != null && !predicate.isEmpty() ) {
			renderJoinedTableGroup( tableGroupJoin, predicate, tableGroupJoinCollector );
		}
		else {
			renderJoinedTableGroup( tableGroupJoin, null, tableGroupJoinCollector );
		}
	}

	private void renderJoinedTableGroup(TableGroupJoin tableGroupJoin, Predicate predicate, List<TableGroupJoin> tableGroupJoinCollector) {
		final LockMode lockModeToApply = determineJoinedTableGroupLockMode( tableGroupJoin );
		var tableGroup = tableGroupJoin.getJoinedGroup();

		final boolean realTableGroup;
		int swappedJoinIndex = -1;
		boolean forceLeftJoin = false;
		if ( tableGroup.isRealTableGroup() ) {
			if ( hasNestedTableGroupsToRender( tableGroup.getNestedTableGroupJoins() ) ) {
				// If there are nested table groups, we need to render a real table group
				realTableGroup = true;
			}
			else {
				// Determine the reference join indexes of the table reference used in the predicate
				final int referenceJoinIndexForPredicateSwap = TableGroupHelper.findReferenceJoinForPredicateSwap(
						tableGroup,
						predicate
				);
				if ( referenceJoinIndexForPredicateSwap == TableGroupHelper.REAL_TABLE_GROUP_REQUIRED ) {
					// Means that real table group rendering is necessary
					realTableGroup = true;
				}
				else if ( referenceJoinIndexForPredicateSwap == TableGroupHelper.NO_TABLE_GROUP_REQUIRED ) {
					// Means that no swap is necessary to avoid the table group rendering
					realTableGroup = false;
					forceLeftJoin = !tableGroup.canUseInnerJoins();
				}
				else {
					// Means that real table group rendering can be avoided if the primary table reference is swapped
					// with the table reference join at the given index
					realTableGroup = false;
					forceLeftJoin = !tableGroup.canUseInnerJoins();
					swappedJoinIndex = referenceJoinIndexForPredicateSwap;

					// Render the table reference of the table reference join first
					final TableReferenceJoin tableReferenceJoin = tableGroup.getTableReferenceJoins()
							.get( swappedJoinIndex );
					renderNamedTableReference( tableReferenceJoin.getJoinedTableReference(), LockMode.NONE );
					// along with the predicate for the table group
					renderJoinPredicate( predicate );

					// Then render the join syntax and fall through to rendering the primary table reference
					appendSql( WHITESPACE );
					if ( !tableGroup.canUseInnerJoins() || tableReferenceJoin.getJoinType() != SqlAstJoinType.LEFT ) {
						appendSql( "optional " );
					}
					appendSql( "match " );
				}
			}
		}
		else {
			realTableGroup = false;
		}
		if ( realTableGroup ) {
			appendSql( OPEN_PARENTHESIS );
		}

		renderPrimaryTableReference( tableGroup, lockModeToApply );
		final List<TableGroupJoin> tableGroupJoins;

		if ( realTableGroup ) {
			// For real table groups, we collect all normal table group joins within that table group
			// The purpose of that is to render them in-order outside of the group/parenthesis
			// This is necessary for at least Derby but is also a lot easier to read
			renderTableReferenceJoins( tableGroup, lockModeToApply );
			if ( tableGroupJoinCollector == null ) {
				tableGroupJoins = new ArrayList<>();
				processNestedTableGroupJoins( tableGroup, tableGroupJoins );
			}
			else {
				tableGroupJoins = null;
				processNestedTableGroupJoins( tableGroup, tableGroupJoinCollector );
			}
			appendSql( CLOSE_PARENTHESIS );
		}
		else {
			tableGroupJoins = null;
		}

		// Predicate was already rendered when swappedJoinIndex is not equal to -1
		if ( predicate != null && swappedJoinIndex == -1 ) {
			renderJoinPredicate( predicate );
		}
		if ( tableGroup.isLateral() && !getDialect().getSubquerySupport().supports( SubquerySupport.Feature.LATERAL ) ) {
			final Predicate lateralEmulationPredicate = determineLateralEmulationPredicate( tableGroup );
			if ( lateralEmulationPredicate != null ) {
				if ( predicate == null ) {
					renderJoinPredicate( lateralEmulationPredicate );
				}
				else {
					appendSql( " and " );
					lateralEmulationPredicate.accept( this );
				}
			}
		}

		if ( !realTableGroup ) {
			renderTableReferenceJoins( tableGroup, lockModeToApply, swappedJoinIndex, forceLeftJoin );
			processNestedTableGroupJoins( tableGroup, tableGroupJoinCollector );
		}
		if ( tableGroupJoinCollector != null ) {
			tableGroupJoinCollector.addAll( tableGroup.getTableGroupJoins() );
		}
		else {
			if ( tableGroupJoins != null ) {
				for ( TableGroupJoin join : tableGroupJoins ) {
					processTableGroupJoin( join, null );
				}
			}
			processTableGroupJoins( tableGroup );
		}

		ModelPartContainer modelPart = tableGroup.getModelPart();
		if ( modelPart instanceof EntityPersister persister ) {
			final String[] querySpaces = (String[]) persister.getQuerySpaces();
			for ( String querySpace : querySpaces ) {
				registerAffectedTable( querySpace );
			}
		}
	}

	private void renderRootTableGroup(TableGroup tableGroup, List<TableGroupJoin> tableGroupJoinCollector) {
		final LockMode effectiveLockMode = determineRootTableGroupLockMode( tableGroup );
		renderPrimaryTableReference( tableGroup, effectiveLockMode );

		if ( tableGroup.isLateral() && !getDialect().getSubquerySupport().supports( SubquerySupport.Feature.LATERAL ) ) {
			addAdditionalWherePredicate( determineLateralEmulationPredicate( tableGroup ) );
		}

		final LockMode lockMode = getEffectiveLockMode();
		renderTableReferenceJoins( tableGroup, lockMode );
		processNestedTableGroupJoins( tableGroup, tableGroupJoinCollector );
		if ( tableGroupJoinCollector != null ) {
			tableGroupJoinCollector.addAll( tableGroup.getTableGroupJoins() );
		}
		else {
			processTableGroupJoins( tableGroup );
		}
		if ( tableGroup.getModelPart() instanceof EntityPersister persister ) {
			final String[] querySpaces = (String[]) persister.getQuerySpaces();
			for ( int i = 0; i < querySpaces.length; i++ ) {
				registerAffectedTable( querySpaces[i] );
			}
		}
	}

	private LockMode determineRootTableGroupLockMode(TableGroup tableGroup) {
		if ( getLockingClauseStrategy() != null ) {
			if ( getCurrentQueryPart() == getLockingTarget() ) {
				if ( getLockingClauseStrategy().registerRoot( tableGroup ) ) {
					return getEffectiveLockMode( tableGroup.getSourceAlias() );
				}
			}
		}
		return LockMode.NONE;
	}

	private void renderPrimaryTableReference(TableGroup tableGroup, LockMode lockMode) {
		final TableReference tableReference = tableGroup.getPrimaryTableReference();
		if ( tableReference instanceof NamedTableReference namedTableReference ) {
			renderNamedTableReference( namedTableReference, lockMode );
		}
		else {
			throw new UnsupportedOperationException( "Neo4j does not support table references of type " + tableReference.getClass().getSimpleName() );
		}
	}

	private boolean hasNestedTableGroupsToRender(List<TableGroupJoin> nestedTableGroupJoins) {
		for ( TableGroupJoin nestedTableGroupJoin : nestedTableGroupJoins ) {
			final TableGroup joinedGroup = nestedTableGroupJoin.getJoinedGroup();
			if ( !joinedGroup.isInitialized() ) {
				continue;
			}
			if ( joinedGroup.isVirtual() ) {
				if ( hasNestedTableGroupsToRender( joinedGroup.getNestedTableGroupJoins() ) ) {
					return true;
				}
			}
			else {
				return true;
			}
		}
		return false;
	}

	private void processTableGroupJoins(TableGroup source) {
		source.visitTableGroupJoins( tableGroupJoin -> processTableGroupJoin( tableGroupJoin, null ) );
	}

	private void processNestedTableGroupJoins(TableGroup source, List<TableGroupJoin> tableGroupJoinCollector) {
		source.visitNestedTableGroupJoins( tableGroupJoin -> processTableGroupJoin( tableGroupJoin, tableGroupJoinCollector ) );
	}

	private void processTableGroupJoin(TableGroupJoin tableGroupJoin, List<TableGroupJoin> tableGroupJoinCollector) {
		final TableGroup joinedGroup = tableGroupJoin.getJoinedGroup();

		if ( joinedGroup.isVirtual() ) {
			processNestedTableGroupJoins( joinedGroup, tableGroupJoinCollector );
			if ( tableGroupJoinCollector != null ) {
				tableGroupJoinCollector.addAll( joinedGroup.getTableGroupJoins() );
			}
			else {
				processTableGroupJoins( joinedGroup );
			}
		}
		else if ( joinedGroup.isInitialized() ) {
			renderTableGroupJoin( tableGroupJoin, tableGroupJoinCollector );
		}
		// A lazy table group, even if uninitialized, might contain table group joins
		else if ( joinedGroup instanceof LazyTableGroup ) {
			processNestedTableGroupJoins( joinedGroup, tableGroupJoinCollector );
			if ( tableGroupJoinCollector != null ) {
				tableGroupJoinCollector.addAll( joinedGroup.getTableGroupJoins() );
			}
			else {
				processTableGroupJoins( joinedGroup );
			}
		}
	}

	private QuerySpec getLockingTarget() {
		// todo: temporary, until we add the getter to ORM
		try {
			Field lockingTarget = AbstractSqlAstTranslator.class.getDeclaredField( "lockingTarget" );
			lockingTarget.setAccessible( true );
			return (QuerySpec) lockingTarget.get( this );
		}
		catch (Exception e) {
			throw new RuntimeException( e );
		}
	}

	private LockMode determineJoinedTableGroupLockMode(TableGroupJoin join) {
		if ( getLockingClauseStrategy() != null ) {
			if ( getCurrentQueryPart() == getLockingTarget() ) {
				if ( getLockingClauseStrategy().registerJoin( join ) ) {
					return getEffectiveLockMode( join.getJoinedGroup().getSourceAlias() );
				}
			}
		}

		return LockMode.NONE;
	}

	private void renderTableReferenceJoins(TableGroup tableGroup, LockMode lockMode) {
		renderTableReferenceJoins( tableGroup, lockMode, -1, false );
	}

	private void renderTableReferenceJoins(TableGroup tableGroup, LockMode lockMode, int swappedJoinIndex, boolean forceLeftJoin) {
		final List<TableReferenceJoin> joins = tableGroup.getTableReferenceJoins();
		if ( joins == null || joins.isEmpty() ) {
			return;
		}

		if ( swappedJoinIndex != -1 ) {
			// Finish the join against the primary table reference after the swap
			final TableReferenceJoin swappedJoin = joins.get( swappedJoinIndex );
			renderJoinPredicate( swappedJoin.getPredicate() );
		}

		for ( int i = 0; i < joins.size(); i++ ) {
			// Skip the swapped join since it was already rendered
			if ( swappedJoinIndex != i ) {
				final TableReferenceJoin tableJoin = joins.get( i );
				appendSql( WHITESPACE );
				if ( forceLeftJoin || tableJoin.getJoinType() != SqlAstJoinType.INNER ) {
					append( "optional " );
				}
				appendSql( "match " );

				renderNamedTableReference( tableJoin.getJoinedTableReference(), lockMode );

				renderJoinPredicate( tableJoin.getPredicate() );
			}
		}
	}

	private void renderJoinPredicate(Predicate predicate) {
		if ( predicate != null && !predicate.isEmpty() ) {
			appendSql( " where " );
			predicate.accept( this );
		}
	}

	protected void visitHavingClause(Predicate havingClauseRestrictions) {
		if ( havingClauseRestrictions != null && !havingClauseRestrictions.isEmpty() ) {
			appendSql( " where " );

			getClauseStack().push( Clause.HAVING );
			try {
				havingClauseRestrictions.accept( this );
			}
			finally {
				getClauseStack().pop();
			}
		}
	}

	// ~ MUTATION OPERATIONS

	@Override
	protected void renderStandardTableInsert(TableInsertStandard tableInsert) {
		appendSql( "create " );
		appendSql( OPEN_PARENTHESIS );

		appendSql( "n:" );
		appendSql( tableInsert.getMutatingTable().getTableName() );
		appendSql( ' ' );

		getCurrentClauseStack().push( Clause.VALUES );
		tableInsert.forEachValueBinding( (columnPosition, columnValueBinding) -> {
			if ( columnPosition == 0 ) {
				appendSql( '{' );
			}
			else {
				appendSql( ',' );
			}
			appendSql( columnValueBinding.getColumnReference().getColumnExpression() );
			appendSql( ':' );
			columnValueBinding.getValueExpression().accept( this );
		} );
		appendSql( "})" );

		getCurrentClauseStack().pop();
		returningAlias = "n";
	}


	@Override
	public void visitCustomTableInsert(TableInsertCustomSql tableInsert) {
		throw new UnsupportedOperationException();
	}

	@Override
	protected void renderStandardTableUpdate(TableUpdateStandard tableUpdate) {
		visitTableUpdate( tableUpdate, tableUpdate.getWhereFragment() );
		returningAlias = "n";
	}

	private void visitTableUpdate(TableUpdateStandard tableUpdate, String whereFragment) {
		applySqlComment( tableUpdate.getMutationComment() );

		appendSql( "match(n:" );
		appendSql( tableUpdate.getMutatingTable().getTableName() );
		registerAffectedTable( tableUpdate.getMutatingTable().getTableName() );
		appendSql( ')' );

		getCurrentClauseStack().push( Clause.WHERE );
		try {
			appendSql( " where" );
			tableUpdate.forEachKeyBinding( (position, columnValueBinding) -> {
				if ( position == 0 ) {
					appendSql( ' ' );
				}
				else {
					appendSql( " and " );
				}
				appendSql( "n." );
				appendSql( columnValueBinding.getColumnReference().getColumnExpression() );
				appendSql( '=' );
				columnValueBinding.getValueExpression().accept( this );
			} );

			if ( tableUpdate.getNumberOfOptimisticLockBindings() > 0 ) {
				tableUpdate.forEachOptimisticLockBinding( (position, columnValueBinding) -> {
					appendSql( " and n." );
					appendSql( columnValueBinding.getColumnReference().getColumnExpression() );
					if ( columnValueBinding.getValueExpression() == null
						|| columnValueBinding.getValueExpression().getFragment() == null ) {
						appendSql( " is null" );
					}
					else {
						appendSql( "=" );
						columnValueBinding.getValueExpression().accept( this );
					}
				} );
			}

			if ( !isBlank( whereFragment ) ) {
				appendSql( " and " + whereFragment );
			}
		}
		finally {
			getCurrentClauseStack().pop();
		}

		getCurrentClauseStack().push( Clause.SET );
		try {
			appendSql( " set" );
			tableUpdate.forEachValueBinding( (columnPosition, columnValueBinding) -> {
				if ( columnPosition == 0 ) {
					appendSql( ' ' );
				}
				else {
					appendSql( ',' );
				}
				appendSql( "n." );
				appendSql( columnValueBinding.getColumnReference().getColumnExpression() );
				appendSql( '=' );
				columnValueBinding.getValueExpression().accept( this );
			} );
		}
		finally {
			getCurrentClauseStack().pop();
		}
	}

	@Override
	protected void visitUpdateStatementOnly(UpdateStatement statement) {
		renderUpdateClause( statement );
		visitWhereClause( statement.getRestriction() );
		renderSetClause( statement.getAssignments() );
		renderFromClauseAfterUpdateSet( statement );
		returningAlias = getTableAlias( statement.getTargetTable() );
	}

	static String getTableAlias(TableReference tableReference) {
		return tableReference.getIdentificationVariable() != null
				? tableReference.getIdentificationVariable()
				: tableReference.getTableId();
	}

	@Override
	protected void renderUpdateClause(UpdateStatement updateStatement) {
		appendSql( "match (" );
		final Stack<Clause> clauseStack = getClauseStack();
		try {
			clauseStack.push( Clause.UPDATE );
			renderDmlTargetTableExpression( updateStatement.getTargetTable() );
			appendSql( ')' );
		}
		finally {
			clauseStack.pop();
		}
	}

	@Override
	protected void renderDmlTargetTableExpression(NamedTableReference tableReference) {
		appendSql( getTableAlias( tableReference ) );
		appendSql( ':' );
		appendSql( tableReference.getTableExpression() );
		registerAffectedTable( tableReference );
	}


	@Override
	protected void renderSetAssignment(Assignment assignment) {
		final List<ColumnReference> columnReferences = assignment.getAssignable().getColumnReferences();
		final Expression assignedValue = assignment.getAssignedValue();
		if ( columnReferences.size() == 1 ) {
			columnReferences.get( 0 ).appendColumnForWrite( this );
			appendSql( '=' );
			final SqlTuple sqlTuple = getSqlTuple( assignedValue );
			if ( sqlTuple != null ) {
				assert sqlTuple.getExpressions().size() == 1;
				sqlTuple.getExpressions().get( 0 ).accept( this );
			}
			else {
				assignedValue.accept( this );
			}
		}
		else if ( assignedValue instanceof SelectStatement ) {
			// todo neo4j : we should render a separate match clause before the set
			throw new UnsupportedOperationException(
					"Neo4j does not support subquery assignments in update statements" );
		}
		else if ( assignedValue instanceof SqlTupleContainer ) {
			throw new UnsupportedOperationException( "Not yet implemented" );
		}
		else {
			throw new AssertionFailure( "Unexpected assigned value" );
		}
	}

	@Override
	protected void renderFromClauseAfterUpdateSet(UpdateStatement statement) {
		renderFromClauseJoiningDmlTargetReference( statement );
	}


	/**
	 * Renders a standard table delete. The base translator does not offer a hook for this, which is
	 * why {@link Neo4jTableDelete} dispatches directly to this method.
	 */
	void renderStandardTableDelete(TableDeleteStandard tableDelete) {
		getCurrentClauseStack().push( Clause.DELETE );
		try {
			applySqlComment( tableDelete.getMutationComment() );

			appendSql( "match(n:" );
			appendSql( tableDelete.getMutatingTable().getTableName() );
			appendSql( ')' );
			registerAffectedTable( tableDelete.getMutatingTable().getTableName() );

			getCurrentClauseStack().push( Clause.WHERE );
			try {
				appendSql( " where " );

				tableDelete.forEachKeyBinding( (columnPosition, columnValueBinding) -> {
					appendSql( "n." );
					appendSql( columnValueBinding.getColumnReference().getColumnExpression() );
					appendSql( "=" );
					columnValueBinding.getValueExpression().accept( this );

					if ( columnPosition < tableDelete.getNumberOfKeyBindings() - 1 ) {
						appendSql( " and " );
					}
				} );

				if ( tableDelete.getNumberOfOptimisticLockBindings() > 0 ) {
					appendSql( " and " );

					tableDelete.forEachOptimisticLockBinding( (columnPosition, columnValueBinding) -> {
						appendSql( "n." );
						appendSql( columnValueBinding.getColumnReference().getColumnExpression() );
						if ( columnValueBinding.getValueExpression() == null ) {
							appendSql( " is null" );
						}
						else {
							appendSql( "=" );
							columnValueBinding.getValueExpression().accept( this );
						}

						if ( columnPosition < tableDelete.getNumberOfOptimisticLockBindings() - 1 ) {
							appendSql( " and " );
						}
					} );
				}

				if ( tableDelete.getWhereFragment() != null ) {
					appendSql( " and (" );
					appendSql( tableDelete.getWhereFragment() );
					appendSql( ")" );
				}
			}
			finally {
				getCurrentClauseStack().pop();
			}

			appendSql( " delete n" );
		}
		finally {
			getCurrentClauseStack().pop();
		}
	}

	@Override
	protected void visitDeleteStatementOnly(DeleteStatement statement) {
		renderDeleteClause( statement );
		visitWhereClause( statement.getRestriction() );
		final String tableAlias = getTableAlias( statement.getTargetTable() );
		if ( !statement.getReturningColumns().isEmpty() ) {
			appendSql( String.format( " with %s, properties(%s) as returning_properties", tableAlias, tableAlias ) );
		}
		appendSql( " delete " + tableAlias );
		returningAlias = "returning_properties";
	}

	@Override
	protected void renderDeleteClause(DeleteStatement statement) {
		appendSql( "match (" );
		final Stack<Clause> clauseStack = getClauseStack();
		try {
			clauseStack.push( Clause.DELETE );
			renderDmlTargetTableExpression( statement.getTargetTable() );
			appendSql( ')' );
		}
		finally {
			clauseStack.pop();
		}
	}



	@Override
	public void visitBetweenPredicate(BetweenPredicate betweenPredicate) {
		appendSql( '(' );
		betweenPredicate.getUpperBound().accept( this );
		appendSql( betweenPredicate.isNegated() ? " < " : " >= " );
		betweenPredicate.getExpression().accept( this );
		if ( betweenPredicate.isNegated() ) {
			appendSql( " or " );
			betweenPredicate.getExpression().accept( this );
		}
		appendSql( betweenPredicate.isNegated() ? " < " : " >= " );
		betweenPredicate.getLowerBound().accept( this );
		appendSql( ')' );
	}

	@Override
	public void visitTuple(SqlTuple tuple) {
		// A tuple in a values clause of an insert-select statement must be unwrapped,
		// since the assignment target is also unwrapped to the individual column references
		final boolean wrap = getClauseStack().getCurrent() != Clause.VALUES;
		if ( wrap ) {
			appendSql( '[' );
		}

		renderCommaSeparated( tuple.getExpressions() );

		if ( wrap ) {
			appendSql( ']' );
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
		if ( ( lhsTuple = getSqlTuple( inListPredicate.getTestExpression() ) ) != null ) {
			if ( lhsTuple.getExpressions().size() == 1 ) {
				// Special case for tuples with arity 1 as any DBMS supports scalar IN predicates
				if ( getSqlTuple( listExpressions.get( 0 ) ) != null ) {
					itemAccessor = listExpression ->
							getSqlTuple( listExpression )
									.getExpressions().get( 0 );
				}
			}
		}

		int bindValueCount = listExpressions.size();
		int bindValueCountWithPadding = bindValueCount;

		int inExprLimit = getDialect().getParameterLimits().inExpressionCountLimit();

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
			if ( !(listExpression instanceof JdbcParameter || listExpression instanceof SqmParameterInterpretation || listExpression instanceof Literal) ) {
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
}
