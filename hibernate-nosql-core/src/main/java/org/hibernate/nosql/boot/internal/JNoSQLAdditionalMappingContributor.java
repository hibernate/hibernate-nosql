/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.nosql.boot.internal;

import jakarta.nosql.Column;
import jakarta.nosql.Convert;
import jakarta.nosql.DiscriminatorColumn;
import jakarta.nosql.DiscriminatorValue;
import jakarta.nosql.Embeddable;
import jakarta.nosql.Entity;
import jakarta.nosql.Id;
import jakarta.nosql.Inheritance;
import jakarta.nosql.MappedSuperclass;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.InheritanceType;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.NamingStrategy;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.pool.TypePool;
import org.hibernate.AssertionFailure;
import org.hibernate.HibernateException;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Struct;
import org.hibernate.boot.ResourceStreamLocator;
import org.hibernate.boot.models.annotations.internal.ColumnJpaAnnotation;
import org.hibernate.boot.models.annotations.internal.ConvertJpaAnnotation;
import org.hibernate.boot.models.annotations.internal.DiscriminatorColumnJpaAnnotation;
import org.hibernate.boot.models.annotations.internal.DiscriminatorValueJpaAnnotation;
import org.hibernate.boot.models.annotations.internal.EmbeddableJpaAnnotation;
import org.hibernate.boot.models.annotations.internal.EntityJpaAnnotation;
import org.hibernate.boot.models.annotations.internal.IdJpaAnnotation;
import org.hibernate.boot.models.annotations.internal.InheritanceJpaAnnotation;
import org.hibernate.boot.models.annotations.internal.JdbcTypeCodeAnnotation;
import org.hibernate.boot.models.annotations.internal.MappedSuperclassJpaAnnotation;
import org.hibernate.boot.models.annotations.internal.StructAnnotation;
import org.hibernate.boot.spi.AdditionalMappingContributions;
import org.hibernate.boot.spi.AdditionalMappingContributor;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.internal.CoreMessageLogger;
import org.hibernate.models.internal.MutableClassDetailsRegistry;
import org.hibernate.models.spi.AnnotationTarget;
import org.hibernate.models.spi.FieldDetails;
import org.hibernate.models.spi.MemberDetails;
import org.hibernate.models.spi.MethodDetails;
import org.hibernate.models.spi.ModelsContext;
import org.hibernate.models.spi.MutableAnnotationTarget;
import org.hibernate.models.spi.MutableClassDetails;
import org.hibernate.type.SqlTypes;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

public class JNoSQLAdditionalMappingContributor implements AdditionalMappingContributor {

	private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
	private static final boolean DEBUG = false;

	@Override
	public void contribute(AdditionalMappingContributions contributions, InFlightMetadataCollector metadata, ResourceStreamLocator resourceStreamLocator, MetadataBuildingContext buildingContext) {
		final MutableClassDetailsRegistry classDetailsRegistry = metadata.getClassDetailsRegistry()
				.as( MutableClassDetailsRegistry.class );
		final List<String> groupingEmbeddableClassNames = new ArrayList<>();

		classDetailsRegistry.forEachClassDetails( classDetails -> {
			if ( classDetails.hasDirectAnnotationUsage( Entity.class )
				|| classDetails.hasDirectAnnotationUsage( MappedSuperclass.class )
				|| classDetails.hasDirectAnnotationUsage( Embeddable.class ) ) {
				assert classDetails instanceof MutableClassDetails;
				rewriteClassDetails(
						(MutableClassDetails) classDetails,
						metadata.getBootstrapContext().getModelsContext(),
						groupingEmbeddableClassNames
				);
				contributions.contributeManagedClass( classDetails );
				metadata.addClassType( classDetails );
			}
		} );

		if ( !groupingEmbeddableClassNames.isEmpty() ) {
			classDetailsRegistry.forEachClassDetails( classDetails -> {
				if ( classDetails.hasDirectAnnotationUsage( Entity.class )
					|| classDetails.hasDirectAnnotationUsage( MappedSuperclass.class )
					|| classDetails.hasDirectAnnotationUsage( Embeddable.class ) ) {
					rewriteGroupingEmbeddables(
							classDetails.getFields(),
							metadata.getBootstrapContext().getModelsContext(),
							groupingEmbeddableClassNames
					);
					rewriteGroupingEmbeddables(
							classDetails.getMethods(),
							metadata.getBootstrapContext().getModelsContext(),
							groupingEmbeddableClassNames
					);
				}
			} );
		}
	}

	private void rewriteGroupingEmbeddables(List<? extends MemberDetails> members, ModelsContext modelsContext, List<String> groupingEmbeddableClassNames) {
		for ( MemberDetails member : members ) {
			if ( groupingEmbeddableClassNames.contains( member.getType().getName() ) ) {
				// todo: might be nice to be able to control the grouping type
//				final StructAnnotation structAnnotation = findOrCreateAnnotation(
//						member,
//						Collections.emptyList(),
//						Struct.class,
//						StructAnnotation.class,
//						modelsContext
//				);
//				((MutableAnnotationTarget) member).addAnnotationUsage( structAnnotation );

				final JdbcTypeCodeAnnotation jdbcTypeCodeAnnotation = findOrCreateAnnotation(
						member,
						Collections.emptyList(),
						JdbcTypeCode.class,
						JdbcTypeCodeAnnotation.class,
						modelsContext
				);
				jdbcTypeCodeAnnotation.value( SqlTypes.JSON );
				((MutableAnnotationTarget) member).addAnnotationUsage( jdbcTypeCodeAnnotation );
			}
		}
	}

	private void rewriteClassDetails(MutableClassDetails classDetails, ModelsContext modelsContext, List<String> groupingEmbeddableClassNames) {
		if ( classDetails.getSuperClass() != null
			&& !classDetails.getSuperClass().getClassName().equals( "java.lang.Object" ) ) {
			rewriteClassDetails(
					(MutableClassDetails) classDetails.getSuperClass(),
					modelsContext,
					groupingEmbeddableClassNames
			);
		}
		processAnnotations( classDetails, modelsContext, groupingEmbeddableClassNames );
		for ( FieldDetails fieldDetails : classDetails.getFields() ) {
			processAnnotations( (MutableAnnotationTarget) fieldDetails, modelsContext, groupingEmbeddableClassNames );
		}
		for ( MethodDetails methodDetails : classDetails.getMethods() ) {
			processAnnotations( (MutableAnnotationTarget) methodDetails, modelsContext, groupingEmbeddableClassNames );
		}
	}

	private void processAnnotations(MutableAnnotationTarget mutableAnnotationTarget, ModelsContext modelsContext, List<String> groupingEmbeddableClassNames) {
		var newAnnotations = new ArrayList<Annotation>();
		for ( Annotation annotationUsage : mutableAnnotationTarget.getDirectAnnotationUsages() ) {
			processAnnotation( mutableAnnotationTarget, newAnnotations, annotationUsage, modelsContext, groupingEmbeddableClassNames );
		}
		for ( Annotation newAnnotation : newAnnotations ) {
			mutableAnnotationTarget.addAnnotationUsage( newAnnotation );
		}
	}

	private void processAnnotation(AnnotationTarget annotationTarget, List<Annotation> newAnnotations, Annotation annotation, ModelsContext modelsContext, List<String> groupingEmbeddableClassNames) {
		final Class<? extends Annotation> annotationType = annotation.annotationType();
		if ( annotationType == Entity.class ) {
			final Entity entity = (Entity) annotation;
			final EntityJpaAnnotation jpaEntity = new EntityJpaAnnotation( modelsContext );
			jpaEntity.name( entity.value() );
			newAnnotations.add( jpaEntity );
		}
		else if ( annotationType == MappedSuperclass.class ) {
			newAnnotations.add( new MappedSuperclassJpaAnnotation( modelsContext ) );
		}
		else if ( annotationType == Embeddable.class ) {
			final Embeddable embeddable = (Embeddable) annotation;
			newAnnotations.add( new EmbeddableJpaAnnotation( modelsContext ) );
			if ( embeddable.value() == Embeddable.EmbeddableType.GROUPING ) {
				groupingEmbeddableClassNames.add( annotationTarget.asClassDetails().getClassName() );
			}
		}
		else if ( annotationType == Inheritance.class ) {
			final InheritanceJpaAnnotation jpaInheritance = new InheritanceJpaAnnotation( modelsContext );
			jpaInheritance.strategy( InheritanceType.SINGLE_TABLE );
			newAnnotations.add( jpaInheritance );
		}
		else if ( annotationType == Id.class ) {
			final Id id = (Id) annotation;
			newAnnotations.add( new IdJpaAnnotation( modelsContext ) );
			final ColumnJpaAnnotation jpaColumn = findOrCreateAnnotation(
					annotationTarget,
					newAnnotations,
					jakarta.persistence.Column.class,
					ColumnJpaAnnotation.class,
					modelsContext
			);
			jpaColumn.name( id.value() );
			newAnnotations.add( jpaColumn );
		}
		else if ( annotationType == Column.class ) {
			final Column column = (Column) annotation;
			final ColumnJpaAnnotation jpaColumn = findOrCreateAnnotation(
					annotationTarget,
					newAnnotations,
					jakarta.persistence.Column.class,
					ColumnJpaAnnotation.class,
					modelsContext
			);
			jpaColumn.name( column.value() );

			if ( !column.udt().isEmpty() ) {
				final StructAnnotation ormStruct = findOrCreateAnnotation(
						annotationTarget,
						newAnnotations,
						Struct.class,
						StructAnnotation.class,
						modelsContext
				);
				ormStruct.name( column.udt() );
				newAnnotations.add( ormStruct );
			}
			newAnnotations.add( jpaColumn );
		}
		else if ( annotationType == DiscriminatorColumn.class ) {
			final DiscriminatorColumn discriminatorColumn = (DiscriminatorColumn) annotation;
			final DiscriminatorColumnJpaAnnotation jpaDiscriminatorColumn = findOrCreateAnnotation(
					annotationTarget,
					newAnnotations,
					jakarta.persistence.DiscriminatorColumn.class,
					DiscriminatorColumnJpaAnnotation.class,
					modelsContext
			);
			jpaDiscriminatorColumn.name( discriminatorColumn.value() );
			newAnnotations.add( jpaDiscriminatorColumn );
		}
		else if ( annotationType == DiscriminatorValue.class ) {
			final DiscriminatorValue discriminatorValue = (DiscriminatorValue) annotation;
			final DiscriminatorValueJpaAnnotation jpaDiscriminatorValue = findOrCreateAnnotation(
					annotationTarget,
					newAnnotations,
					jakarta.persistence.DiscriminatorValue.class,
					DiscriminatorValueJpaAnnotation.class,
					modelsContext
			);
			jpaDiscriminatorValue.value( discriminatorValue.value() );
			newAnnotations.add( jpaDiscriminatorValue );
		}
		else if ( annotationType == Convert.class ) {
			final Convert convert = (Convert) annotation;
			final ConvertJpaAnnotation jpaConvert = findOrCreateAnnotation(
					annotationTarget,
					newAnnotations,
					jakarta.persistence.Convert.class,
					ConvertJpaAnnotation.class,
					modelsContext
			);
			jpaConvert.converter( buildAdapterClass( convert.value() ) );
			newAnnotations.add( jpaConvert );
		}
	}

	private <T extends Annotation, I extends T> I findOrCreateAnnotation(AnnotationTarget annotationTarget, List<Annotation> newAnnotations, Class<T> annotationType, Class<I> implementationType, ModelsContext modelsContext) {
		final T existingAnnotation =
				findAnnotation( annotationTarget, newAnnotations, annotationType );
		if ( implementationType.isInstance( existingAnnotation ) ) {
			return implementationType.cast( existingAnnotation );
		}
		else {
			try {
				if ( existingAnnotation == null ) {
					return implementationType.getConstructor( ModelsContext.class )
							.newInstance( modelsContext );
				}
				else {
					return implementationType.getConstructor( annotationType, ModelsContext.class )
							.newInstance( existingAnnotation, modelsContext );
				}
			}
			catch (InvocationTargetException | InstantiationException | IllegalAccessException | NoSuchMethodException e) {
				throw new AssertionFailure( "Couldn't instantiate annotation implementation", e );
			}
		}
	}

	private <T extends Annotation> T findAnnotation(AnnotationTarget annotationTarget, List<Annotation> newAnnotations, Class<T> annotationType) {
		for ( Annotation newAnnotation : newAnnotations ) {
			if ( newAnnotation.annotationType() == annotationType ) {
				return annotationType.cast( newAnnotation );
			}
		}
		return annotationTarget.getDirectAnnotationUsage( annotationType );
	}

	@Override
	public String getContributorName() {
		return "jnosql";
	}


	private final ByteBuddy byteBuddy = new ByteBuddy();

	private Class<? extends AttributeConverter<?, ?>> buildAdapterClass(Class<? extends jakarta.nosql.AttributeConverter<?, ?>> converterClass) {
		//noinspection unchecked
		return (Class<? extends AttributeConverter<?, ?>>) load(
				converterClass,
				converterClass.getTypeName() + "$HibernateJpaAdapter",
				(byteBuddy1, namingStrategy) -> {
					return byteBuddy1.with( namingStrategy )
							.decorate( converterClass )
							.implement( AttributeConverter.class );
				});
	}


	/**
	 * Load a class generated by ByteBuddy.
	 *
	 * @param referenceClass The main class for which to create a class - might be an interface.
	 * @param className The name under which the class shall be created.
	 * @param makeClassFunction A function building the class.
	 * @return The loaded generated class.
	 */
	public Class<?> load(Class<?> referenceClass, String className, BiFunction<ByteBuddy, NamingStrategy, DynamicType.Builder<?>> makeClassFunction) {
		try {
			Class<?> result = referenceClass.getClassLoader().loadClass(className);
			if ( result.getClassLoader() == referenceClass.getClassLoader() ) {
				return result;
			}
		}
		catch (ClassNotFoundException e) {
			// Ignore
		}
		try {
			return make( makeClassFunction.apply( byteBuddy, new FixedNamingStrategy( className ) ) )
					.load(
							referenceClass.getClassLoader(),
							resolveClassLoadingStrategy( referenceClass )
					)
					.getLoaded();
		}
		catch (LinkageError e) {
			try {
				return referenceClass.getClassLoader().loadClass( className );
			}
			catch (ClassNotFoundException ex) {
				throw new RuntimeException( "Couldn't load or define class [" + className + "]", e );
			}
		}
	}

	public DynamicType.Unloaded<?> make(Function<ByteBuddy, DynamicType.Builder<?>> makeProxyFunction) {
		return make( makeProxyFunction.apply( byteBuddy ) );
	}

	public DynamicType.Unloaded<?> make(TypePool typePool, Function<ByteBuddy, DynamicType.Builder<?>> makeProxyFunction) {
		return make( typePool, makeProxyFunction.apply( byteBuddy ) );
	}

	private DynamicType.Unloaded<?> make(DynamicType.Builder<?> builder) {
		return make( null, builder );
	}

	private DynamicType.Unloaded<?> make(TypePool typePool, DynamicType.Builder<?> builder) {
		DynamicType.Unloaded<?> unloadedClass;
		if ( typePool != null ) {
			unloadedClass = builder.make( typePool );
		}
		else {
			unloadedClass = builder.make();
		}

		if ( DEBUG ) {
			try {
				unloadedClass.saveIn( new File( System.getProperty( "java.io.tmpdir" ) + "/bytebuddy/" ) );
			}
			catch (IOException e) {
				CoreMessageLogger.CORE_LOGGER.warn( "Unable to save generated class %1$s", unloadedClass.getTypeDescription().getName(), e );
			}
		}
		return unloadedClass;
	}

	private static ClassLoadingStrategy<ClassLoader> resolveClassLoadingStrategy(Class<?> originalClass) {
		try {
			return ClassLoadingStrategy.UsingLookup.of( MethodHandles.privateLookupIn( originalClass, LOOKUP ) );
		}
		catch (Throwable e) {
			throw new HibernateException( "Bytecode enhancement failed for class '" + originalClass.getName()
										+ "' (it might be due to the Java module system preventing Hibernate NoSQL from defining an enhanced class in the same package"
										+ " - in this case, the class should be opened and exported to Hibernate NoSQL)", e );
		}
	}

	private static class FixedNamingStrategy extends NamingStrategy.AbstractBase {
		private final String className;

		public FixedNamingStrategy(String className) {
			this.className = className;
		}

		@Override
		protected String name(TypeDescription typeDescription) {
			return className;
		}
	}
}
