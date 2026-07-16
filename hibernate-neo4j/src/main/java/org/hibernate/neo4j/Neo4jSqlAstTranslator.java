/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.neo4j;

import org.hibernate.AssertionFailure;
import org.hibernate.LockMode;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.internal.util.collections.Stack;
import org.hibernate.metamodel.mapping.ModelPartContainer;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.query.sqm.sql.internal.SqmPathInterpretation;
import org.hibernate.query.sqm.tuple.internal.AnonymousTupleTableGroupProducer;
import org.hibernate.sql.ast.Clause;
import org.hibernate.sql.ast.SqlAstJoinType;
import org.hibernate.sql.ast.SqlAstNodeRenderingMode;
import org.hibernate.sql.ast.internal.TableGroupHelper;
import org.hibernate.sql.ast.spi.AbstractSqlAstTranslator;
import org.hibernate.sql.ast.tree.SqlAstNode;
import org.hibernate.sql.ast.tree.Statement;
import org.hibernate.sql.ast.tree.delete.DeleteStatement;
import org.hibernate.sql.ast.tree.expression.ColumnReference;
import org.hibernate.sql.ast.tree.expression.Expression;
import org.hibernate.sql.ast.tree.expression.QueryLiteral;
import org.hibernate.sql.ast.tree.expression.SqlTuple;
import org.hibernate.sql.ast.tree.expression.SqlTupleContainer;
import org.hibernate.sql.ast.tree.from.FromClause;
import org.hibernate.sql.ast.tree.from.NamedTableReference;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.ast.tree.from.TableGroupJoin;
import org.hibernate.sql.ast.tree.from.TableReference;
import org.hibernate.sql.ast.tree.from.TableReferenceJoin;
import org.hibernate.sql.ast.tree.insert.InsertSelectStatement;
import org.hibernate.sql.ast.tree.predicate.BetweenPredicate;
import org.hibernate.sql.ast.tree.predicate.BooleanExpressionPredicate;
import org.hibernate.sql.ast.tree.predicate.Predicate;
import org.hibernate.sql.ast.tree.select.QuerySpec;
import org.hibernate.sql.ast.tree.select.SelectClause;
import org.hibernate.sql.ast.tree.select.SelectStatement;
import org.hibernate.sql.ast.tree.update.Assignment;
import org.hibernate.sql.ast.tree.update.UpdateStatement;
import org.hibernate.sql.exec.spi.JdbcOperation;
import org.hibernate.sql.model.MutationOperation;
import org.hibernate.sql.model.ast.RestrictedTableMutation;
import org.hibernate.sql.model.internal.TableDeleteStandard;
import org.hibernate.sql.model.internal.TableInsertCustomSql;
import org.hibernate.sql.model.internal.TableInsertStandard;
import org.hibernate.sql.model.internal.TableUpdateStandard;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.hibernate.internal.util.StringHelper.isBlank;

public class Neo4jSqlAstTranslator<T extends JdbcOperation> extends AbstractSqlAstTranslator<T> {

	protected Neo4jSqlAstTranslator(SessionFactoryImplementor sessionFactory, Statement statement) {
		super( sessionFactory, statement );
	}

	@Override
	public void visitQuerySpec(QuerySpec querySpec) {
		getQueryPartStack().push( querySpec );

		try {
			visitFromClause( querySpec.getFromClause() ); // match
			visitWhereClause( querySpec.getWhereClauseRestrictions() );
			visitSelectClause( querySpec.getSelectClause() ); // return
			visitOrderBy( querySpec.getSortSpecifications() );
			visitOffsetFetchClause( querySpec );
			visitForUpdateClause( querySpec );
		}
		finally {
			getQueryPartStack().pop();
		}
	}

	@Override
	public void visitFromClause(FromClause fromClause) {
		if ( fromClause == null || fromClause.getRoots().isEmpty() ) {
//			throw new UnsupportedOperationException( "Neo4j doesn't support an empty from clause" );
		}
		else {
			appendSql( "match " );
			renderFromClauseSpaces( fromClause );
		}
	}

	@Override
	protected void renderFromClauseSpaces(FromClause fromClause) {
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
				renderFromClauseRoot( tableGroupJoin.getJoinedGroup(), separator );
			}
			for ( TableGroupJoin tableGroupJoin : root.getNestedTableGroupJoins() ) {
				addAdditionalWherePredicate( tableGroupJoin.getPredicate() );
				renderFromClauseRoot( tableGroupJoin.getJoinedGroup(), separator );
			}
		}
		else if ( root.isInitialized() ) {
			appendSql( separator );
			renderRootTableGroup( root, null );
		}
		return ",";
	}

	@Override
	protected boolean renderNamedTableReference(NamedTableReference tableReference, LockMode lockMode) {
		appendSql( '(' );
		renderTableReferenceIdentificationVariable( tableReference );
		appendSql( ':' );
		appendSql( tableReference.getTableExpression() );
		appendSql( ')' );
		registerAffectedTable( tableReference );
		return false;
	}

	@Override
	public void visitSelectClause(SelectClause selectClause) {
		getCurrentClauseStack().push( Clause.SELECT );

		try {
			appendSql( " return " );
			if ( selectClause.isDistinct() ) {
				appendSql( "distinct " );
			}
			visitSqlSelections( selectClause );
			renderVirtualSelections( selectClause );
		}
		finally {
			getCurrentClauseStack().pop();
		}
	}

	@Override
	protected void renderTableGroupJoin(TableGroupJoin tableGroupJoin, List<TableGroupJoin> tableGroupJoinCollector) {
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

	@Override
	protected void renderJoinedTableGroup(TableGroupJoin tableGroupJoin, Predicate predicate, List<TableGroupJoin> tableGroupJoinCollector) {
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
		if ( tableGroup.isLateral() && !getDialect().supportsLateral() ) {
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

	private QuerySpec getLockingTarget() {
		// todo: temporary, until we add the getter to ORM
		try {
			Field lockingTarget = AbstractSqlAstTranslator.class.getField( "lockingTarget" );
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

	@Override
	protected void renderTableReferenceJoins(TableGroup tableGroup, LockMode lockMode, int swappedJoinIndex, boolean forceLeftJoin) {
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
			if ( getCurrentQueryPart().isRoot() ) {
				addAdditionalWherePredicate( predicate );
			}
			else {
				appendSql( " where " );
				predicate.accept( this );
			}
		}
	}

	// ~ MUTATION OPERATIONS

	@Override
	public void visitStandardTableInsert(TableInsertStandard tableInsert) {
		getCurrentClauseStack().push( Clause.INSERT );
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

		if ( tableInsert.getNumberOfReturningColumns() > 0 ) {
			visitReturningColumns( tableInsert.getReturningColumns(), "n" );
		}

		getCurrentClauseStack().pop();
	}

	@Override
	public void visitInsertStatement(InsertSelectStatement statement) {
		throw new UnsupportedOperationException( "Neo4j does not support insert-select statements" );
	}

	@Override
	public void visitCustomTableInsert(TableInsertCustomSql tableInsert) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitStandardTableUpdate(TableUpdateStandard tableUpdate) {
		getCurrentClauseStack().push( Clause.UPDATE );
		try {
			visitTableUpdate( tableUpdate, tableUpdate.getWhereFragment() );
			if ( tableUpdate.getNumberOfReturningColumns() > 0 ) {
				visitReturningColumns( tableUpdate.getReturningColumns(), "n" );
			}
		}
		finally {
			getCurrentClauseStack().pop();
		}
	}

	private void visitTableUpdate(RestrictedTableMutation<? extends MutationOperation> tableUpdate, String whereFragment) {
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
		visitReturningColumns( statement.getReturningColumns(), getTableAlias( statement.getTargetTable() ) );
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
		renderTableReferenceIdentificationVariable( tableReference );
		appendSql( ':' );
		super.renderDmlTargetTableExpression( tableReference );
	}

	@Override
	protected void renderTableReferenceIdentificationVariable(TableReference tableReference) {
		appendSql( getTableAlias( tableReference ) );
	}

	@Override
	protected void visitSetAssignment(Assignment assignment) {
		if ( assignment.getAssignable() instanceof SqmPathInterpretation<?> sqmPathInterpretation ) {
			final String affectedTableName = sqmPathInterpretation.getAffectedTableName();
			if ( affectedTableName != null ) {
				addAffectedTableName( affectedTableName );
			}
		}
		final List<ColumnReference> columnReferences = assignment.getAssignable().getColumnReferences();
		final Expression assignedValue = assignment.getAssignedValue();
		if ( columnReferences.size() == 1 ) {
			columnReferences.get( 0 ).appendColumnForWrite( this );
			appendSql( '=' );
			final SqlTuple sqlTuple = SqlTupleContainer.getSqlTuple( assignedValue );
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

	private void applySqlComment(String comment) {
		if ( getSessionFactory().getSessionFactoryOptions().isCommentsEnabled() ) {
			if ( comment != null ) {
				appendSql( "/* " );
				appendSql( Dialect.escapeComment( comment ) );
				appendSql( " */" );
			}
		}
	}

	@Override
	public void visitStandardTableDelete(TableDeleteStandard tableDelete) {
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
		visitReturningColumns( statement.getReturningColumns(), "returning_properties" );
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

	protected void visitReturningColumns(List<ColumnReference> returningColumns, String tableAlias) {
		if ( !returningColumns.isEmpty() ) {
			appendSql( " return " );
			String separator = "";
			for ( ColumnReference columnReference : returningColumns ) {
				appendSql( separator );
				appendSql( tableAlias + "." + columnReference.getColumnExpression() );
				separator = COMMA_SEPARATOR;
			}
		}
	}

	@Override
	public void renderNamedSetReturningFunction(String functionName, List<? extends SqlAstNode> sqlAstArguments, AnonymousTupleTableGroupProducer tupleType, String tableIdentifierVariable, SqlAstNodeRenderingMode argumentRenderingMode) {
		throw new UnsupportedOperationException();
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
}
