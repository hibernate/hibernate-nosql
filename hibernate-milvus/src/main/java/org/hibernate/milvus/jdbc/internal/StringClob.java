/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.jdbc.internal;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.Clob;
import java.sql.NClob;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

public class StringClob implements Clob, NClob {

	private final String value;

	public StringClob(String value) {
		this.value = value;
	}

	@Override
	public long length() throws SQLException {
		return value.length();
	}

	@Override
	public String getSubString(long pos, int length) throws SQLException {
		int index = (int) (pos - 1L);
		return value.substring( index, index + length );
	}

	@Override
	public Reader getCharacterStream() throws SQLException {
		return new StringReader( value );
	}

	@Override
	public InputStream getAsciiStream() throws SQLException {
		return new ByteArrayInputStream( value.getBytes( StandardCharsets.US_ASCII) );
	}

	@Override
	public long position(String searchstr, long start) throws SQLException {
		return value.indexOf( searchstr, (int) start ) + 1;
	}

	@Override
	public long position(Clob searchstr, long start) throws SQLException {
		return value.indexOf( searchstr.getSubString( 1L, (int) searchstr.length() ), (int) start ) + 1;
	}

	@Override
	public int setString(long pos, String str) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public int setString(long pos, String str, int offset, int len) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public OutputStream setAsciiStream(long pos) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public Writer setCharacterStream(long pos) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void truncate(long len) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void free() throws SQLException {
	}

	@Override
	public Reader getCharacterStream(long pos, long length) throws SQLException {
		int index = (int) (pos - 1L);
		return new StringReader( value.substring( index, (int) (index + length) ) );
	}
}
