package org.hibernate.nosql.testing;

import org.hibernate.dialect.Dialect;
import org.hibernate.milvus.MilvusDialect;
import org.hibernate.sql.spi.StringBuilderSqlAppender;
import org.hibernate.testing.orm.junit.DialectFeatureCheck;

import java.util.UUID;

public final class BasicDialectFeatureChecks {
	private BasicDialectFeatureChecks() {
	}

	public static class SupportsArbitraryNumerics implements DialectFeatureCheck {
		@Override
		public boolean apply(Dialect dialect) {
			// Milvus only supports float/double
			return !(dialect instanceof MilvusDialect);
		}
	}

	public static class SupportsCompositePrimaryKey implements DialectFeatureCheck {
		@Override
		public boolean apply(Dialect dialect) {
			// Milvus only supports a single primary key
			return !(dialect instanceof MilvusDialect);
		}
	}

	public static class SupportsJoins implements DialectFeatureCheck {
		@Override
		public boolean apply(Dialect dialect) {
			// Milvus does not support joins
			return !(dialect instanceof MilvusDialect);
		}
	}

	public static class SupportsPartialUpdate implements DialectFeatureCheck {
		@Override
		public boolean apply(Dialect dialect) {
			// Milvus can only replace the whole data, never update the data partially
			return !(dialect instanceof MilvusDialect);
		}
	}

	public static class SupportsJoinTableCollections implements DialectFeatureCheck {
		@Override
		public boolean apply(Dialect dialect) {
			// Milvus does not support joins, but MilvusMetadataContributor takes care of making fetches of collection
			// parts through separate selects. This expects that the MilvusSettings#JOIN_TABLE_COMPATIBILITY is true.
			return true;
		}
	}

	public static class SupportsTableSequences implements DialectFeatureCheck {
		@Override
		public boolean apply(Dialect dialect) {
			// TableGenerator requires SQL support to function, so no NoSQL database can support this ATM
			return false;
		}
	}

	public static class SupportsUnion implements DialectFeatureCheck {
		@Override
		public boolean apply(Dialect dialect) {
			// UnionSubclassEntityPersister requires SQL support to function, so no NoSQL database can support this ATM
			return false;
		}
	}

	public static class SupportsOptimisticLocking implements DialectFeatureCheck {
		@Override
		public boolean apply(Dialect dialect) {
			// Milvus does not allow a predicate in the UPSERT statement
			return !(dialect instanceof MilvusDialect);
		}
	}

	public static class SupportsSelectingNonColumns implements DialectFeatureCheck {
		@Override
		public boolean apply(Dialect dialect) {
			// Milvus does not allow selection of anything but columns
			return !(dialect instanceof MilvusDialect);
		}
	}

	public static class SupportsUUIDConstant implements DialectFeatureCheck {
		@Override
		public boolean apply(Dialect dialect) {
			try {
				dialect.appendUUIDLiteral( new StringBuilderSqlAppender(), UUID.randomUUID() );
				return true;
			}
			catch (RuntimeException e) {
				return false;
			}
		}
	}

	public static class SupportsBinaryConstant implements DialectFeatureCheck {
		@Override
		public boolean apply(Dialect dialect) {
			try {
				dialect.appendBinaryLiteral( new StringBuilderSqlAppender(), new byte[0] );
				return true;
			}
			catch (RuntimeException e) {
				return false;
			}
		}
	}
}
