/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.internal.mapping;

import org.hibernate.Internal;
import org.hibernate.MappingException;
import org.hibernate.annotations.CacheLayout;
import org.hibernate.annotations.SoftDeleteType;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.collection.spi.CollectionSemantics;
import org.hibernate.engine.FetchStyle;
import org.hibernate.jdbc.Expectation;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.FilterConfiguration;
import org.hibernate.mapping.IndexedCollection;
import org.hibernate.mapping.KeyValue;
import org.hibernate.mapping.Map;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.QualifiedColumnName;
import org.hibernate.mapping.Selectable;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.Value;
import org.hibernate.mapping.ValueVisitor;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.milvus.internal.collection.MilvusMapSemantics;
import org.hibernate.milvus.internal.collection.MilvusOrderedMapSemantics;
import org.hibernate.milvus.internal.collection.MilvusSortedMapSemantics;
import org.hibernate.persister.state.spi.StateManagement;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.CollectionType;
import org.hibernate.type.MappingContext;
import org.hibernate.type.Type;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public final class MilvusMap extends Map {

	private final Map original;

	public MilvusMap(Map original) {
		super( original.getBuildingContext(), original.getOwner() );
		this.original = original;
	}

	@Override
	public Map copy() {
		return new MilvusMap( original.copy() );
	}

	@Override
	public CollectionSemantics<?, ?> getCollectionSemantics() {
		if ( isSorted() ) {
			return MilvusSortedMapSemantics.INSTANCE;
		}
		else if ( hasOrder() ) {
			return MilvusOrderedMapSemantics.INSTANCE;
		}
		else {
			return MilvusMapSemantics.INSTANCE;
		}
	}

	@Override
	public Object accept(ValueVisitor visitor) {
		return visitor.accept( this );
	}

	// Delegate the rest of the methods

	@Override
	public void setFetchStyle(FetchStyle fetchStyle) {
		original.setFetchStyle( fetchStyle );
	}

	@Override
	public FetchStyle getFetchStyle() {
		return original.getFetchStyle();
	}

	@Override
	public boolean isMap() {
		return original.isMap();
	}

	@Override
	public String getMapKeyPropertyName() {
		return original.getMapKeyPropertyName();
	}

	@Override
	public void setMapKeyPropertyName(String mapKeyPropertyName) {
		original.setMapKeyPropertyName( mapKeyPropertyName );
	}

	@Override
	public CollectionType getDefaultCollectionType() {
		return original.getDefaultCollectionType();
	}

	@Override
	public void createAllKeys() throws MappingException {
		original.createAllKeys();
	}

	@Override
	public boolean hasMapKeyProperty() {
		return original.hasMapKeyProperty();
	}

	@Override
	public void setHasMapKeyProperty(boolean hasMapKeyProperty) {
		original.setHasMapKeyProperty( hasMapKeyProperty );
	}

	@Override
	public Value getIndex() {
		return original.getIndex();
	}

	@Override
	public void setIndex(Value index) {
		original.setIndex( index );
	}

	@Override
	public boolean isSame(Collection other) {
		return original.isSame( other );
	}

	@Override
	public boolean isSame(IndexedCollection other) {
		return original.isSame( other );
	}

	@Override
	public void validate(MappingContext mappingContext) throws MappingException {
		original.validate( mappingContext );
	}

	@Override
	public boolean isList() {
		return original.isList();
	}

	@Override
	public MetadataBuildingContext getBuildingContext() {
		return original.getBuildingContext();
	}

	@Override
	public MetadataImplementor getMetadata() {
		return original.getMetadata();
	}

	@Override
	public ServiceRegistry getServiceRegistry() {
		return original.getServiceRegistry();
	}

	@Override
	public boolean isSet() {
		return original.isSet();
	}

	@Override
	public KeyValue getKey() {
		return original.getKey();
	}

	@Override
	public Value getElement() {
		return original.getElement();
	}

	@Override
	public Table getCollectionTable() {
		return original.getCollectionTable();
	}

	@Override
	public void setCollectionTable(Table table) {
		original.setCollectionTable( table );
	}

	@Override
	public boolean isSorted() {
		return original.isSorted();
	}

	@Override
	public Comparator<?> getComparator() {
		return original.getComparator();
	}

	@Override
	public boolean isLazy() {
		return original.isLazy();
	}

	@Override
	public void setLazy(boolean lazy) {
		original.setLazy( lazy );
	}

	@Override
	public String getRole() {
		return original.getRole();
	}

	@Override
	public boolean isPrimitiveArray() {
		return original.isPrimitiveArray();
	}

	@Override
	public boolean isArray() {
		return original.isArray();
	}

	@Override
	public boolean hasFormula() {
		return original.hasFormula();
	}

	@Override
	public boolean isOneToMany() {
		return original.isOneToMany();
	}

	@Override
	public boolean isInverse() {
		return original.isInverse();
	}

	@Override
	public String getOwnerEntityName() {
		return original.getOwnerEntityName();
	}

	@Override
	public String getOrderBy() {
		return original.getOrderBy();
	}

	@Override
	public void setComparator(Comparator comparator) {
		original.setComparator( comparator );
	}

	@Override
	public void setElement(Value element) {
		original.setElement( element );
	}

	@Override
	public void setKey(KeyValue key) {
		original.setKey( key );
	}

	@Override
	public void setOrderBy(String orderBy) {
		original.setOrderBy( orderBy );
	}

	@Override
	public void setRole(String role) {
		original.setRole( role );
	}

	@Override
	public void setSorted(boolean sorted) {
		original.setSorted( sorted );
	}

	@Override
	public void setInverse(boolean inverse) {
		original.setInverse( inverse );
	}

	@Override
	public PersistentClass getOwner() {
		return original.getOwner();
	}

	@Override
	public String getWhere() {
		return original.getWhere();
	}

	@Override
	public void setWhere(String where) {
		original.setWhere( where );
	}

	@Override
	public String getManyToManyWhere() {
		return original.getManyToManyWhere();
	}

	@Override
	public void setManyToManyWhere(String manyToManyWhere) {
		original.setManyToManyWhere( manyToManyWhere );
	}

	@Override
	public String getManyToManyOrdering() {
		return original.getManyToManyOrdering();
	}

	@Override
	public void setManyToManyOrdering(String orderFragment) {
		original.setManyToManyOrdering( orderFragment );
	}

	@Override
	public boolean isIdentified() {
		return original.isIdentified();
	}

	@Override
	public boolean hasOrphanDelete() {
		return original.hasOrphanDelete();
	}

	@Override
	public void setOrphanDelete(boolean orphanDelete) {
		original.setOrphanDelete( orphanDelete );
	}

	@Override
	public int getBatchSize() {
		return original.getBatchSize();
	}

	@Override
	public void setBatchSize(int batchSize) {
		original.setBatchSize( batchSize );
	}

	@Override
	public List<Selectable> getSelectables() {
		return original.getSelectables();
	}

	@Override
	public List<Column> getColumns() {
		return original.getColumns();
	}

	@Override
	public int getColumnSpan() {
		return original.getColumnSpan();
	}

	@Override
	public boolean hasColumns() {
		return original.hasColumns();
	}

	@Override
	public Type getType() throws MappingException {
		return original.getType();
	}

	@Override
	public CollectionType getCollectionType() {
		return original.getCollectionType();
	}

	@Override
	public boolean isNullable() {
		return original.isNullable();
	}

	@Override
	public boolean isAlternateUniqueKey() {
		return original.isAlternateUniqueKey();
	}

	@Override
	public Table getTable() {
		return original.getTable();
	}

	@Override
	public void createForeignKey() {
		original.createForeignKey();
	}

	@Override
	public void createUniqueKey(MetadataBuildingContext context) {
		original.createUniqueKey( context );
	}

	@Override
	public boolean isSimpleValue() {
		return original.isSimpleValue();
	}

	@Override
	public boolean isValid(MappingContext mappingContext) {
		return original.isValid( mappingContext );
	}

	@Override
	public boolean isSame(Value other) {
		return original.isSame( other );
	}

	public static boolean isSame(Value v1, Value v2) {
		return Collection.isSame( v1, v2 );
	}

	@Override
	public boolean isPrimaryKeyDisabled() {
		return original.isPrimaryKeyDisabled();
	}

	@Override
	public void setPrimaryKeyDisabled(boolean disabled) {
		original.setPrimaryKeyDisabled( disabled );
	}

	@Override
	public void setAuxiliaryColumnInPrimaryKey(String key) {
		original.setAuxiliaryColumnInPrimaryKey( key );
	}

	@Override
	public boolean isAuxiliaryColumnInPrimaryKey() {
		return original.isAuxiliaryColumnInPrimaryKey();
	}

	@Override
	public String getCacheConcurrencyStrategy() {
		return original.getCacheConcurrencyStrategy();
	}

	@Override
	public void setCacheConcurrencyStrategy(String cacheConcurrencyStrategy) {
		original.setCacheConcurrencyStrategy( cacheConcurrencyStrategy );
	}

	@Override
	public void setTypeUsingReflection(String className, String propertyName) {
		original.setTypeUsingReflection( className, propertyName );
	}

	@Override
	public String getCacheRegionName() {
		return original.getCacheRegionName();
	}

	@Override
	public void setCacheRegionName(String cacheRegionName) {
		original.setCacheRegionName( cacheRegionName );
	}

	@Override
	public CacheLayout getQueryCacheLayout() {
		return original.getQueryCacheLayout();
	}

	@Override
	public void setQueryCacheLayout(CacheLayout queryCacheLayout) {
		original.setQueryCacheLayout( queryCacheLayout );
	}

	@Override
	public void setCustomSQLInsert(String customSQLInsert, boolean callable) {
		original.setCustomSQLInsert( customSQLInsert, callable );
	}

	@Override
	public String getCustomSQLInsert() {
		return original.getCustomSQLInsert();
	}

	@Override
	public boolean isCustomInsertCallable() {
		return original.isCustomInsertCallable();
	}

	@Override
	public void setCustomSQLUpdate(String customSQLUpdate, boolean callable) {
		original.setCustomSQLUpdate( customSQLUpdate, callable );
	}

	@Override
	public String getCustomSQLUpdate() {
		return original.getCustomSQLUpdate();
	}

	@Override
	public boolean isCustomUpdateCallable() {
		return original.isCustomUpdateCallable();
	}

	@Override
	public void setCustomSQLDelete(String customSQLDelete, boolean callable) {
		original.setCustomSQLDelete( customSQLDelete, callable );
	}

	@Override
	public String getCustomSQLDelete() {
		return original.getCustomSQLDelete();
	}

	@Override
	public boolean isCustomDeleteCallable() {
		return original.isCustomDeleteCallable();
	}

	@Override
	public void setCustomSQLDeleteAll(String customSQLDeleteAll, boolean callable) {
		original.setCustomSQLDeleteAll( customSQLDeleteAll, callable );
	}

	@Override
	public String getCustomSQLDeleteAll() {
		return original.getCustomSQLDeleteAll();
	}

	@Override
	public boolean isCustomDeleteAllCallable() {
		return original.isCustomDeleteAllCallable();
	}

	@Override
	public void addFilter(String name, String condition, boolean autoAliasInjection, java.util.Map<String, String> aliasTableMap, java.util.Map<String, String> aliasEntityMap) {
		original.addFilter( name, condition, autoAliasInjection, aliasTableMap, aliasEntityMap );
	}

	@Override
	public List<FilterConfiguration> getFilters() {
		return original.getFilters();
	}

	@Override
	public void addManyToManyFilter(String name, String condition, boolean autoAliasInjection, java.util.Map<String, String> aliasTableMap, java.util.Map<String, String> aliasEntityMap) {
		original.addManyToManyFilter( name, condition, autoAliasInjection, aliasTableMap, aliasEntityMap );
	}

	@Override
	public List<FilterConfiguration> getManyToManyFilters() {
		return original.getManyToManyFilters();
	}

	@Override
	public String toString() {
		return original.toString();
	}

	@Override
	public Set<String> getSynchronizedTables() {
		return original.getSynchronizedTables();
	}

	@Override
	public void addSynchronizedTable(String table) {
		original.addSynchronizedTable( table );
	}

	@Override
	public String getLoaderName() {
		return original.getLoaderName();
	}

	@Override
	public void setLoaderName(String name) {
		original.setLoaderName( name );
	}

	@Override
	public String getReferencedPropertyName() {
		return original.getReferencedPropertyName();
	}

	@Override
	public void setReferencedPropertyName(String propertyRef) {
		original.setReferencedPropertyName( propertyRef );
	}

	@Override
	public boolean isOptimisticLocked() {
		return original.isOptimisticLocked();
	}

	@Override
	public void setOptimisticLocked(boolean optimisticLocked) {
		original.setOptimisticLocked( optimisticLocked );
	}

	@Override
	public String getTypeName() {
		return original.getTypeName();
	}

	@Override
	public void setTypeName(String typeName) {
		original.setTypeName( typeName );
	}

	@Override
	public void setTypeParameters(java.util.Map<String, String> typeParameters) {
		original.setTypeParameters( typeParameters );
	}

	@Override
	public boolean[] getColumnInsertability() {
		return original.getColumnInsertability();
	}

	@Override
	public boolean hasAnyInsertableColumns() {
		return original.hasAnyInsertableColumns();
	}

	@Override
	public boolean[] getColumnUpdateability() {
		return original.getColumnUpdateability();
	}

	@Override
	public void setNonInsertable() {
		original.setNonInsertable();
	}

	@Override
	public void setNonUpdatable() {
		original.setNonUpdatable();
	}

	@Override
	public boolean hasAnyUpdatableColumns() {
		return original.hasAnyUpdatableColumns();
	}

	@Override
	public boolean isSubselectLoadable() {
		return original.isSubselectLoadable();
	}

	@Override
	public void setSubselectLoadable(boolean subqueryLoadable) {
		original.setSubselectLoadable( subqueryLoadable );
	}

	@Override
	public boolean isMutable() {
		return original.isMutable();
	}

	@Override
	public void setMutable(boolean mutable) {
		original.setMutable( mutable );
	}

	@Override
	public boolean isExtraLazy() {
		return original.isExtraLazy();
	}

	@Override
	public void setExtraLazy(boolean extraLazy) {
		original.setExtraLazy( extraLazy );
	}

	@Override
	public boolean hasOrder() {
		return original.hasOrder();
	}

	@Override
	public void setComparatorClassName(String comparatorClassName) {
		original.setComparatorClassName( comparatorClassName );
	}

	@Override
	public String getComparatorClassName() {
		return original.getComparatorClassName();
	}

	@Override
	public String getMappedByProperty() {
		return original.getMappedByProperty();
	}

	@Override
	public void setMappedByProperty(String mappedByProperty) {
		original.setMappedByProperty( mappedByProperty );
	}

	@Override
	public boolean isColumnInsertable(int index) {
		return original.isColumnInsertable( index );
	}

	@Override
	public boolean isColumnUpdateable(int index) {
		return original.isColumnUpdateable( index );
	}

	@Override
	public void enableSoftDelete(Column indicatorColumn, SoftDeleteType strategy) {
		original.enableSoftDelete( indicatorColumn, strategy );
	}

	@Override
	public SoftDeleteType getSoftDeleteStrategy() {
		return original.getSoftDeleteStrategy();
	}

	@Override
	public Table getMainTable() {
		return original.getMainTable();
	}

	@Override
	public boolean isMainTablePartitioned() {
		return original.isMainTablePartitioned();
	}

	@Override
	public void setMainTablePartitioned(boolean partitioned) {
		original.setMainTablePartitioned( partitioned );
	}

	@Override
	public Supplier<? extends Expectation> getInsertExpectation() {
		return original.getInsertExpectation();
	}

	@Override
	public void setInsertExpectation(Supplier<? extends Expectation> insertExpectation) {
		original.setInsertExpectation( insertExpectation );
	}

	@Override
	public Supplier<? extends Expectation> getUpdateExpectation() {
		return original.getUpdateExpectation();
	}

	@Override
	public void setUpdateExpectation(Supplier<? extends Expectation> updateExpectation) {
		original.setUpdateExpectation( updateExpectation );
	}

	@Override
	public Supplier<? extends Expectation> getDeleteExpectation() {
		return original.getDeleteExpectation();
	}

	@Override
	public void setDeleteExpectation(Supplier<? extends Expectation> deleteExpectation) {
		original.setDeleteExpectation( deleteExpectation );
	}

	@Override
	public Supplier<? extends Expectation> getDeleteAllExpectation() {
		return original.getDeleteAllExpectation();
	}

	@Override
	public void setDeleteAllExpectation(Supplier<? extends Expectation> deleteAllExpectation) {
		original.setDeleteAllExpectation( deleteAllExpectation );
	}

	@Override
	public boolean isPartitionKey() {
		return original.isPartitionKey();
	}

	@Override
	public void setStateManagementType(Class<? extends StateManagement> stateManagementType) {
		original.setStateManagementType( stateManagementType );
	}

	@Override
	public Class<? extends StateManagement> getStateManagementType() {
		return original.getStateManagementType();
	}

	@Override
	public Table getAuxiliaryTable() {
		return original.getAuxiliaryTable();
	}

	@Override
	public void setAuxiliaryTable(Table auxiliaryTable) {
		original.setAuxiliaryTable( auxiliaryTable );
	}

	@Override
	public Column getAuxiliaryColumn(String column) {
		return original.getAuxiliaryColumn( column );
	}

	@Override
	public void addAuxiliaryColumn(String name, Column column) {
		original.addAuxiliaryColumn( name, column );
	}

	@Override
	public List<Selectable> getVirtualSelectables() {
		return original.getVirtualSelectables();
	}

	@Override
	public List<Column> getConstraintColumns() {
		return original.getConstraintColumns();
	}

	@Override
	public JdbcMapping getSelectableType(MappingContext mappingContext, int index) throws MappingException {
		return original.getSelectableType( mappingContext, index );
	}

	@Override
	public String getExtraCreateTableInfo() {
		return original.getExtraCreateTableInfo();
	}

	@Internal
	@Override
	public void checkColumnDuplication(Set<QualifiedColumnName> distinctColumns, String owner) {
		original.checkColumnDuplication( distinctColumns, owner );
	}

	@Override
	public Column getSoftDeleteColumn() {
		return original.getSoftDeleteColumn();
	}

	@Override
	public StateManagement getStateManagement() {
		return original.getStateManagement();
	}
}
