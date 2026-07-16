/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.nosql.test.junit;

import org.hibernate.testing.orm.junit.DomainModelExtension;
import org.hibernate.testing.orm.junit.DomainModelParameterResolver;
import org.hibernate.testing.orm.junit.FailureExpectedExtension;
import org.hibernate.testing.orm.junit.ServiceRegistryExtension;
import org.hibernate.testing.orm.junit.ServiceRegistryParameterResolver;
import org.hibernate.testing.orm.junit.SessionFactoryExtension;
import org.hibernate.testing.orm.junit.SessionFactoryParameterResolver;
import org.hibernate.testing.orm.junit.SessionFactoryScopeParameterResolver;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Inherited
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention( RetentionPolicy.RUNTIME )

@TestInstance( TestInstance.Lifecycle.PER_CLASS )

@ExtendWith( FailureExpectedExtension.class )
@ExtendWith( ServiceRegistryExtension.class )
@ExtendWith( ServiceRegistryParameterResolver.class )

@ExtendWith( DomainModelExtension.class )
@ExtendWith( DomainModelParameterResolver.class )

@ExtendWith( SessionFactoryExtension.class )
@ExtendWith( SessionFactoryParameterResolver.class )
@ExtendWith( SessionFactoryScopeParameterResolver.class )

@ExtendWith( TemplateExtension.class )
@ExtendWith( TemplateScopeParameterResolver.class )
public @interface Template {
}
