/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.nosql.test.junit;

import org.hibernate.StatelessSession;
import org.hibernate.Transaction;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.nosql.internal.TemplateImpl;
import org.hibernate.nosql.spi.TemplateImplementor;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.hibernate.testing.jdbc.SQLStatementInspector;
import org.hibernate.testing.orm.junit.DomainModelExtension;
import org.hibernate.testing.orm.junit.JUnitHelper;
import org.hibernate.testing.orm.junit.SessionFactoryExtension;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.junit.platform.commons.support.AnnotationSupport;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * hibernate-testing implementation of a few JUnit5 contracts to support SessionFactory-based testing,
 * including argument injection (or see {@link TemplateScopeAware})
 *
 * @see TemplateScope
 * @see DomainModelExtension
 *
 * @author Steve Ebersole
 */
public class TemplateExtension
		implements TestInstancePostProcessor, BeforeEachCallback, TestExecutionExceptionHandler {

	private static final Logger log = Logger.getLogger( TemplateExtension.class );
	private static final String TEMPLATE_KEY = TemplateScope.class.getName();

	/**
	 * Intended for use from external consumers.  Will never create a scope, just
	 * attempt to consume an already created and stored one
	 */
	public static TemplateScope findTemplateScope(Object testInstance, ExtensionContext context) {
		final ExtensionContext.Store store = locateExtensionStore( testInstance, context );
		final TemplateScope existing = (TemplateScope) store.get( TEMPLATE_KEY );
		if ( existing != null ) {
			return existing;
		}

		throw new RuntimeException( "Could not locate TemplateScope : " + context.getDisplayName() );
	}

	@Override
	public void postProcessTestInstance(Object testInstance, ExtensionContext context) {
		log.tracef( "#postProcessTestInstance(%s, %s)", testInstance, context.getDisplayName() );

		final Optional<Template> templateAnnRef = AnnotationSupport.findAnnotation(
				context.getRequiredTestClass(),
				Template.class
		);

		if ( templateAnnRef.isPresent() ) {
			final SessionFactoryScope sfScope = SessionFactoryExtension.findSessionFactoryScope( testInstance, context );
			final TemplateScope created = createTemplateScope( testInstance, sfScope, context );
			locateExtensionStore( testInstance, context ).put( TEMPLATE_KEY, created );
		}
	}

	@Override
	public void beforeEach(ExtensionContext context) {
		final Optional<Template> templateAnnRef = AnnotationSupport.findAnnotation(
				context.getRequiredTestMethod(),
				Template.class
		);

		if ( templateAnnRef.isEmpty() ) {
			// assume the annotations are defined on the class-level...
			// will be validated by the parameter-resolver or SFS-extension
			return;
		}

		final SessionFactoryScope sfScope = SessionFactoryExtension.findSessionFactoryScope( context.getTestInstance(), context );
		final TemplateScope created = createTemplateScope( context.getRequiredTestInstance(), sfScope, context );
		final ExtensionContext.Store extensionStore = locateExtensionStore( context.getRequiredTestInstance(), context );
		extensionStore.put( TEMPLATE_KEY, created );
	}

	private static ExtensionContext.Store locateExtensionStore(Object testInstance, ExtensionContext context) {
		return JUnitHelper.locateExtensionStore( TemplateExtension.class, context, testInstance );
	}

	private static TemplateScopeImpl createTemplateScope(
			Object testInstance,
			SessionFactoryScope sessionFactoryScope,
			ExtensionContext context) {
		final TemplateScopeImpl templateScope = new TemplateScopeImpl( sessionFactoryScope );

		if ( testInstance instanceof TemplateScopeAware ) {
			( (TemplateScopeAware) testInstance ).injectTemplateScope( templateScope );
		}

		return templateScope;
	}

	@Override
	public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
		log.tracef( "#handleTestExecutionException(%s, %s)", context.getDisplayName(), throwable );
		throw throwable;
	}

	private static class TemplateScopeImpl implements TemplateScope {
		private final SessionFactoryScope sessionFactoryScope;

		private TemplateScopeImpl(SessionFactoryScope sessionFactoryScope) {
			this.sessionFactoryScope = sessionFactoryScope;
		}

		@Override
		public SessionFactoryImplementor getSessionFactory() {
			return sessionFactoryScope.getSessionFactory();
		}

		@Override
		public MetadataImplementor getMetadataImplementor() {
			return sessionFactoryScope.getMetadataImplementor();
		}

		@Override
		public StatementInspector getStatementInspector() {
			return getSessionFactory().getSessionFactoryOptions().getStatementInspector();
		}

		@Override
		public <T extends StatementInspector> T getStatementInspector(Class<T> type) {
			//noinspection unchecked
			return (T) getStatementInspector();
		}

		@Override
		public SQLStatementInspector getCollectingStatementInspector() {
			return getStatementInspector( SQLStatementInspector.class );
		}

		public void inSession(Consumer<TemplateImplementor> action) {
			log.trace( "#inSession(Consumer)" );

			try (StatelessSession session = getSessionFactory().openStatelessSession()) {
				log.trace( "Session opened, calling action" );
				action.accept( new TemplateImpl( session ) );
			}
			finally {
				log.trace( "Session close - auto-close block" );
			}
		}

		@Override
		public <T> T fromSession(Function<TemplateImplementor, T> action) {
			log.trace( "#fromSession(Function)" );

			try (StatelessSession session = getSessionFactory().openStatelessSession()) {
				log.trace( "Session opened, calling action" );
				return action.apply( new TemplateImpl( session ) );
			}
			finally {
				log.trace( "Session close - auto-close block" );
			}
		}

		@Override
		public void inTransaction(Consumer<TemplateImplementor> action) {
			log.trace( "#inTransaction(Consumer)" );

			try (StatelessSession session = getSessionFactory().openStatelessSession()) {
				log.trace( "Session opened, calling action" );
				inTransaction( new TemplateImpl( session ), action );
			}
			finally {
				log.trace( "Session close - auto-close block" );
			}
		}

		@Override
		public <T> T fromTransaction(Function<TemplateImplementor, T> action) {
			log.trace( "#fromTransaction(Function)" );

			try (StatelessSession session = getSessionFactory().openStatelessSession()) {
				log.trace( "Session opened, calling action" );
				return fromTransaction( new TemplateImpl( session ), action );
			}
			finally {
				log.trace( "Session close - auto-close block" );
			}
		}

		@Override
		public void inTransaction(TemplateImplementor template, Consumer<TemplateImplementor> action) {
			log.trace( "inTransaction(Session,Consumer)" );
			final Transaction txn = template.unwrap( StatelessSession.class ).beginTransaction();
			log.trace( "Started transaction" );

			try {
				log.trace( "Calling action in txn" );
				action.accept( template );
				log.trace( "Called action - in txn" );

				if ( !txn.getRollbackOnly() ) {
					log.trace( "Committing transaction" );
					txn.commit();
					log.trace( "Committed transaction" );
				}
				else {
					try {
						log.trace( "Rollback transaction marked for rollback only" );
						txn.rollback();
					}
					catch (Exception e) {
						log.error( "Rollback failure", e );
					}
				}
			}
			catch (Exception e) {
				log.tracef(
						"Error calling action: %s (%s) - rolling back",
						e.getClass().getName(),
						e.getMessage()
				);
				try {
					txn.rollback();
				}
				catch (Exception ignore) {
					log.trace( "Was unable to roll back transaction" );
					// really nothing else we can do here - the attempt to
					//		rollback already failed and there is nothing else
					// 		to clean up.
				}

				throw e;
			}
			catch (AssertionError t) {
				try {
					txn.rollback();
				}
				catch (Exception ignore) {
					log.trace( "Was unable to roll back transaction" );
					// really nothing else we can do here - the attempt to
					//		rollback already failed and there is nothing else
					// 		to clean up.
				}
				throw t;
			}
		}

		@Override
		public <T> T fromTransaction(TemplateImplementor template, Function<TemplateImplementor, T> action) {
			log.trace( "fromTransaction(Session,Function)" );
			final Transaction txn = template.unwrap( StatelessSession.class ).beginTransaction();
			log.trace( "Started transaction" );
			try {
				log.trace( "Calling action in txn" );
				final T result = action.apply( template );
				log.trace( "Called action - in txn" );

				log.trace( "Committing transaction" );
				txn.commit();
				log.trace( "Committed transaction" );

				return result;
			}
			catch (Exception e) {
				log.tracef(
						"Error calling action: %s (%s) - rolling back",
						e.getClass().getName(),
						e.getMessage()
				);
				try {
					txn.rollback();
				}
				catch (Exception ignore) {
					log.trace( "Was unable to roll back transaction" );
					// really nothing else we can do here - the attempt to
					//		rollback already failed and there is nothing else
					// 		to clean up.
				}

				throw e;
			}
			catch (AssertionError t) {
				try {
					txn.rollback();
				}
				catch (Exception ignore) {
					log.trace( "Was unable to roll back transaction" );
					// really nothing else we can do here - the attempt to
					//		rollback already failed and there is nothing else
					// 		to clean up.
				}
				throw t;
			}
		}

		@Override
		public void dropData() {
			sessionFactoryScope.dropData();
		}


	}
}
