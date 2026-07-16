/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.nosql.test.junit;

import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.nosql.spi.TemplateImplementor;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.hibernate.testing.jdbc.SQLStatementInspector;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author Steve Ebersole
 */
public interface TemplateScope {
	SessionFactoryImplementor getSessionFactory();
	MetadataImplementor getMetadataImplementor();
	StatementInspector getStatementInspector();
	<T extends StatementInspector> T getStatementInspector(Class<T> type);
	SQLStatementInspector getCollectingStatementInspector();

	void inSession(Consumer<TemplateImplementor> action);
	void inTransaction(Consumer<TemplateImplementor> action);
	void inTransaction(TemplateImplementor session, Consumer<TemplateImplementor> action);

	<T> T fromSession(Function<TemplateImplementor, T> action);
	<T> T fromTransaction(Function<TemplateImplementor, T> action);
	<T> T fromTransaction(TemplateImplementor session, Function<TemplateImplementor, T> action);

	void dropData();
}
