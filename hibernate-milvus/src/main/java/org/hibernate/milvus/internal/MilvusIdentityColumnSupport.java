/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.internal;

import org.hibernate.MappingException;
import org.hibernate.dialect.identity.spi.IdentityColumnSupportBase;


public class MilvusIdentityColumnSupport extends IdentityColumnSupportBase {

	public static final MilvusIdentityColumnSupport INSTANCE = new MilvusIdentityColumnSupport();

	@Override
	public boolean supportsIdentityColumns() {
		return true;
	}

	@Override
	public String getIdentitySelectString(String table, String column, int type) throws MappingException {
		throw new MappingException(
				getClass().getName() + " does not support selecting the last generated identity value");
	}

	@Override
	public String getIdentityColumnString(int type) {
		return "autoid";
	}
}
