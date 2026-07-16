/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.jdbc.internal;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Blob;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

public class ByteArrayBlob implements Blob {

	private final byte[] value;

	public ByteArrayBlob(byte[] value) {
		this.value = value;
	}

	@Override
	public long length() throws SQLException {
		return value.length;
	}

	@Override
	public byte[] getBytes(long pos, int length) throws SQLException {
		byte[] bytes = new byte[length];
		System.arraycopy(value, (int) (pos - 1), bytes, 0, length);
		return bytes;
	}

	@Override
	public InputStream getBinaryStream() throws SQLException {
		return new ByteArrayInputStream(value);
	}

	@Override
	public InputStream getBinaryStream(long pos, long length) throws SQLException {
		return new ByteArrayInputStream( getBytes( pos, (int) length ) );
	}

	@Override
	public long position(byte[] pattern, long start) throws SQLException {
		OUTER: for ( int i = 0; i < value.length; i++ ) {
			for ( int j = 0; j < pattern.length; j++ ) {
				if ( value[i + j] != pattern[j] ) {
					continue OUTER;
				}
			}
			return i;
		}
		return 0;
	}

	@Override
	public long position(Blob pattern, long start) throws SQLException {
		return position( pattern.getBytes( 1L, (int) pattern.length() ), start );
	}

	@Override
	public int setBytes(long pos, byte[] bytes) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public int setBytes(long pos, byte[] bytes, int offset, int len) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public OutputStream setBinaryStream(long pos) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void truncate(long len) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void free() throws SQLException {
	}
}
