/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.nosql.test.junit;

/**
 * @author Steve Ebersole
 */
public interface TemplateScopeAware {
	/**
	 * Callback to inject the TemplateScope into the container
	 */
	void injectTemplateScope(TemplateScope scope);
}
