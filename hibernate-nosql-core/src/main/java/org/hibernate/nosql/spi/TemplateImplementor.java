/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.nosql.spi;

import jakarta.nosql.Template;

public interface TemplateImplementor extends Template {

	<T> T unwrap(Class<T> type);
}
