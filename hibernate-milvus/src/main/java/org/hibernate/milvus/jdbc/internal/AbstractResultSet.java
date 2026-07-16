/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.jdbc.internal;

import org.hibernate.internal.build.AllowReflection;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

public abstract class AbstractResultSet<T extends Statement> implements ResultSet {

	protected final T statement;

	private int fetchDirection;
	private int fetchSize;
	private int resultSetType;

	private boolean closed = false;
	protected boolean wasNull = false;
	protected int position = -1;

	public AbstractResultSet(T statement) {
		this.statement = statement;
	}

	protected abstract int resultSize();

	@Override
	public boolean next() throws SQLException {
		wasNull = false;
		if (position + 1 < resultSize()) {
			position++;
			return true;
		}
		position = resultSize();
		return false;
	}

	@Override
	public void close() throws SQLException {
		closed = true;
	}

	protected void checkClosed() throws SQLException {
		if (closed) {
			throw new SQLException("ResultSet is closed");
		}
	}

	protected void checkIndex(int index, int size) throws SQLException {
		checkClosed();
		if ( index <= 0 || index > size ) {
			throw new SQLException( "Column index out of bounds: " + index );
		}
	}

	@Override
	public SQLWarning getWarnings() throws SQLException {
		checkClosed();
		return null;
	}

	@Override
	public void clearWarnings() throws SQLException {
		checkClosed();
	}

	@Override
	public String getCursorName() throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public boolean isClosed() throws SQLException {
		return closed;
	}

	@Override
	public boolean isBeforeFirst() throws SQLException {
		checkClosed();
		return position == -1;
	}

	@Override
	public boolean isAfterLast() throws SQLException {
		checkClosed();
		return position == resultSize();
	}

	@Override
	public boolean isFirst() throws SQLException {
		checkClosed();
		return position == 0;
	}

	@Override
	public boolean isLast() throws SQLException {
		checkClosed();
		return position == resultSize() - 1;
	}

	@Override
	public void beforeFirst() throws SQLException {
		checkClosed();
		position = -1;
		wasNull = false;
	}

	@Override
	public void afterLast() throws SQLException {
		checkClosed();
		position = resultSize();
		wasNull = false;
	}

	@Override
	public boolean first() throws SQLException {
		checkClosed();
		position = 0;
		wasNull = false;
		return resultSize() != 0;
	}

	@Override
	public boolean last() throws SQLException {
		checkClosed();
		position = resultSize() - 1;
		wasNull = false;
		return resultSize() != 0;
	}

	@Override
	public int getRow() throws SQLException {
		checkClosed();
		return position + 1;
	}

	@Override
	public boolean absolute(int row) throws SQLException {
		checkClosed();
		position = row - 1;
		wasNull = false;
		return position >= 0 && resultSize() != 0
			&& position < resultSize();
	}

	@Override
	public boolean relative(int rows) throws SQLException {
		checkClosed();
		return absolute( position + rows );
	}

	@Override
	public boolean previous() throws SQLException {
		checkClosed();
		wasNull = false;
		if (position - 1 >= 0) {
			position--;
			return true;
		}
		position = -1;
		return false;
	}

	@Override
	public void setFetchDirection(int direction) throws SQLException {
		checkClosed();
		this.fetchDirection = direction;
	}

	@Override
	public int getFetchDirection() throws SQLException {
		checkClosed();
		return fetchDirection;
	}

	@Override
	public void setFetchSize(int rows) throws SQLException {
		checkClosed();
		this.fetchSize = rows;
	}

	@Override
	public int getFetchSize() throws SQLException {
		checkClosed();
		return fetchSize;
	}

	@Override
	public int getType() throws SQLException {
		checkClosed();
		return resultSetType;
	}

	@Override
	public int getConcurrency() throws SQLException {
		checkClosed();
		return ResultSet.CONCUR_READ_ONLY;
	}

	@Override
	public int getHoldability() throws SQLException {
		checkClosed();
		return ResultSet.HOLD_CURSORS_OVER_COMMIT;
	}

	@Override
	public Statement getStatement() throws SQLException {
		checkClosed();
		return statement;
	}

	@Override
	public <T> T unwrap(Class<T> iface) throws SQLException {
		throw new SQLException();
	}

	@Override
	public boolean isWrapperFor(Class<?> iface) throws SQLException {
		return false;
	}

	// ------------- Read APIs ----------------

	@Override
	public boolean wasNull() throws SQLException {
		checkClosed();
		return wasNull;
	}

	protected abstract Object getValue(int columnIndex) throws SQLException;

	protected abstract Object getValue(String columnLabel) throws SQLException;

	private String getString(Object value) throws SQLException {
		if ( value == null ) {
			return null;
		}
		else if ( value instanceof String ) {
			return (String) value;
		}
		else {
			throw new SQLException( "Unsupported type: " + value.getClass().getName() );
		}
	}

	private boolean getBoolean(Object value) throws SQLException {
		if ( value == null ) {
			return false;
		}
		else if ( value instanceof Boolean ) {
			return (Boolean) value;
		}
		else {
			throw new SQLException( "Unsupported type: " + value.getClass().getName() );
		}
	}

	private byte getByte(Object value) throws SQLException {
		if ( value == null ) {
			return 0;
		}
		else if ( value instanceof Byte ) {
			return (Byte) value;
		}
		else if ( value instanceof Number ) {
			return ((Number) value).byteValue();
		}
		else {
			throw new SQLException( "Unsupported type: " + value.getClass().getName() );
		}
	}

	private short getShort(Object value) throws SQLException {
		if ( value == null ) {
			return 0;
		}
		else if ( value instanceof Short ) {
			return (Short) value;
		}
		else if ( value instanceof Number ) {
			return ((Number) value).shortValue();
		}
		else {
			throw new SQLException( "Unsupported type: " + value.getClass().getName() );
		}
	}

	private int getInt(Object value) throws SQLException {
		if ( value == null ) {
			return 0;
		}
		else if ( value instanceof Integer ) {
			return (Integer) value;
		}
		else if ( value instanceof Number ) {
			return ((Number) value).intValue();
		}
		else {
			throw new SQLException( "Unsupported type: " + value.getClass().getName() );
		}
	}

	private long getLong(Object value) throws SQLException {
		if ( value == null ) {
			return 0;
		}
		else if ( value instanceof Long ) {
			return (Long) value;
		}
		else if ( value instanceof Number ) {
			return ((Number) value).longValue();
		}
		else {
			throw new SQLException( "Unsupported type: " + value.getClass().getName() );
		}
	}

	private float getFloat(Object value) throws SQLException {
		if ( value == null ) {
			return 0;
		}
		else if ( value instanceof Float ) {
			return (Float) value;
		}
		else if ( value instanceof Number ) {
			return ((Number) value).floatValue();
		}
		else {
			throw new SQLException( "Unsupported type: " + value.getClass().getName() );
		}
	}

	private double getDouble(Object value) throws SQLException {
		if ( value == null ) {
			return 0;
		}
		else if ( value instanceof Double ) {
			return (Double) value;
		}
		else if ( value instanceof Number ) {
			return ((Number) value).doubleValue();
		}
		else {
			throw new SQLException( "Unsupported type: " + value.getClass().getName() );
		}
	}

	private BigDecimal getBigDecimal(Object value, int scale) throws SQLException {
		if ( value == null ) {
			return null;
		}
		else {
			return getBigDecimal( value ).setScale( scale, RoundingMode.DOWN );
		}
	}

	private byte[] getBytes(Object value) throws SQLException {
		if ( value == null ) {
			return null;
		}
		else if ( value instanceof byte[] ) {
			return (byte[]) value;
		}
		else {
			throw new SQLException( "Unsupported type: " + value.getClass().getName() );
		}
	}

	private Date getDate(Object value) throws SQLException {
		if ( value == null ) {
			return null;
		}
		else if ( value instanceof Date ) {
			return (Date) value;
		}
		// todo (milvus): emulate?
		else {
			throw new SQLException( "Unsupported type: " + value.getClass().getName() );
		}
	}

	private Time getTime(Object value) throws SQLException {
		if ( value == null ) {
			return null;
		}
		else if ( value instanceof Time ) {
			return (Time) value;
		}
		// todo (milvus): emulate?
		else {
			throw new SQLException( "Unsupported type: " + value.getClass().getName() );
		}
	}

	private Timestamp getTimestamp(Object value) throws SQLException {
		if ( value == null ) {
			return null;
		}
		else if ( value instanceof Timestamp ) {
			return (Timestamp) value;
		}
		// todo (milvus): emulate?
		else {
			throw new SQLException( "Unsupported type: " + value.getClass().getName() );
		}
	}

	private InputStream getAsciiStream(Object value) throws SQLException {
		if ( value == null ) {
			return null;
		}
		else if ( value instanceof InputStream ) {
			return (InputStream) value;
		}
		else if ( value instanceof String ) {
			return new ByteArrayInputStream( ((String) value).getBytes( StandardCharsets.US_ASCII) );
		}
		else {
			throw new SQLException( "Unsupported type: " + value.getClass().getName() );
		}
	}

	private InputStream getUnicodeStream(Object value) throws SQLException {
		if ( value == null ) {
			return null;
		}
		else if ( value instanceof InputStream ) {
			return (InputStream) value;
		}
		else if ( value instanceof String ) {
			return new ByteArrayInputStream( ((String) value).getBytes( StandardCharsets.UTF_8) );
		}
		else {
			throw new SQLException( "Unsupported type: " + value.getClass().getName() );
		}
	}

	private InputStream getBinaryStream(Object value) throws SQLException {
		if ( value == null ) {
			return null;
		}
		else if ( value instanceof InputStream ) {
			return (InputStream) value;
		}
		else if ( value instanceof byte[] ) {
			return new ByteArrayInputStream( (byte[]) value );
		}
		else {
			throw new SQLException( "Unsupported type: " + value.getClass().getName() );
		}
	}

	private Object getObject(Object value) throws SQLException {
		return value;
	}

	private Reader getCharacterStream(Object value) throws SQLException {
		if ( value == null ) {
			return null;
		}
		else if ( value instanceof Reader ) {
			return (Reader) value;
		}
		else if ( value instanceof String ) {
			return new StringReader( (String) value );
		}
		else {
			throw new SQLException( "Unsupported type: " + value.getClass().getName() );
		}
	}

	private BigDecimal getBigDecimal(Object value) throws SQLException {
		if ( value == null ) {
			return null;
		}
		else if ( value instanceof BigDecimal ) {
			return (BigDecimal) value;
		}
		else if ( value instanceof Double ) {
			return BigDecimal.valueOf( ((Double) value) );
		}
		else if ( value instanceof Float ) {
			return BigDecimal.valueOf( ((Float) value) );
		}
		else if ( value instanceof Long ) {
			return BigDecimal.valueOf( ((Long) value) );
		}
		else if ( value instanceof Integer ) {
			return BigDecimal.valueOf( ((Integer) value) );
		}
		else if ( value instanceof Short ) {
			return BigDecimal.valueOf( ((Short) value) );
		}
		else if ( value instanceof Byte ) {
			return BigDecimal.valueOf( ((Byte) value) );
		}
		else if ( value instanceof Number ) {
			return new BigDecimal( value.toString() );
		}
		else {
			throw new SQLException( "Unsupported type: " + value.getClass().getName() );
		}
	}

	private Object getObject(Object value, Map<String, Class<?>> map) throws SQLException {
		return value;
	}

	private Blob getBlob(Object value) throws SQLException {
		if ( value == null ) {
			return null;
		}
		else if ( value instanceof Blob ) {
			return (Blob) value;
		}
		else if ( value instanceof byte[] ) {
			return new ByteArrayBlob( (byte[]) value );
		}
		else {
			throw new SQLException( "Unsupported type: " + value.getClass().getName() );
		}
	}

	private Clob getClob(Object value) throws SQLException {
		if ( value == null ) {
			return null;
		}
		else if ( value instanceof Clob ) {
			return (Clob) value;
		}
		else if ( value instanceof String ) {
			return new StringClob( (String) value );
		}
		else {
			throw new SQLException( "Unsupported type: " + value.getClass().getName() );
		}
	}

	private Array getArray(Object value) throws SQLException {
		if ( value == null ) {
			return null;
		}
		else if ( value.getClass().isArray() ) {
			return (Array) value;
		}
		// todo (milvus): emulate?
		else {
			throw new SQLException( "Unsupported type: " + value.getClass().getName() );
		}
	}

	private Date getDate(Object value, Calendar cal) throws SQLException {
		if ( value == null ) {
			return null;
		}
		else if ( value instanceof Date ) {
			return (Date) value;
		}
		// todo (milvus): emulate?
		else {
			throw new SQLException( "Unsupported type: " + value.getClass().getName() );
		}
	}

	private Time getTime(Object value, Calendar cal) throws SQLException {
		if ( value == null ) {
			return null;
		}
		else if ( value instanceof Time ) {
			return (Time) value;
		}
		// todo (milvus): emulate?
		else {
			throw new SQLException( "Unsupported type: " + value.getClass().getName() );
		}
	}

	private Timestamp getTimestamp(Object value, Calendar cal) throws SQLException {
		if ( value == null ) {
			return null;
		}
		else if ( value instanceof Timestamp ) {
			return (Timestamp) value;
		}
		// todo (milvus): emulate?
		else {
			throw new SQLException( "Unsupported type: " + value.getClass().getName() );
		}
	}

	private URL getURL(Object value) throws SQLException {
		if ( value == null ) {
			return null;
		}
		else if ( value instanceof URL ) {
			return (URL) value;
		}
		else if ( value instanceof String ) {
			try {
				return URI.create( (String) value ).toURL();
			}
			catch (MalformedURLException e) {
				throw new SQLException( "Invalid URL", e );
			}
		}
		else {
			throw new SQLException( "Unsupported type: " + value.getClass().getName() );
		}
	}

	private RowId getRowId(Object value) throws SQLException {
		if ( value == null ) {
			return null;
		}
		else if ( value instanceof RowId ) {
			return (RowId) value;
		}
		// todo (milvus): emulate?
		else {
			throw new SQLException( "Unsupported type: " + value.getClass().getName() );
		}
	}

	private NClob getNClob(Object value) throws SQLException {
		if ( value == null ) {
			return null;
		}
		else if ( value instanceof NClob ) {
			return (NClob) value;
		}
		else if ( value instanceof String ) {
			return new StringClob( (String) value );
		}
		else {
			throw new SQLException( "Unsupported type: " + value.getClass().getName() );
		}
	}

	// TODO Christian make sure reflection is correctly registered for the relevant types.
	//   Typically this is done by exposing the array types in here:
	//   https://github.com/hibernate/hibernate-nosql/blob/018b8eeda3627e114ec25bd48407ccb9c47564ce/hibernate-graalvm/src/main/java/org/hibernate/graalvm/internal/StaticClassLists.java#L42
	@AllowReflection
	private <T> T getObject(Object value, Class<T> type) throws SQLException {
		if ( value == null ) {
			return null;
		}
		else if ( type.isInstance( value  ) ) {
			return type.cast( value );
		}
		else if ( type == byte[].class && value instanceof ByteBuffer byteBuffer ) {
			return (T) byteBuffer.array();
		}
		else if ( type.isArray() && value instanceof List<?> list ) {
			final Object array = java.lang.reflect.Array.newInstance( type.componentType(), list.size() );
			for ( int i = 0; i < list.size(); i++ ) {
				java.lang.reflect.Array.set( array, i, list.get( i ) );
			}
			return type.cast( array );
		}
		else {
			throw new SQLException( "Unsupported type: " + value.getClass().getName() );
		}
	}

	// -------------- Index-based Read APIs

	@Override
	public String getString(int columnIndex) throws SQLException {
		return getString( getValue( columnIndex ) );
	}

	@Override
	public boolean getBoolean(int columnIndex) throws SQLException {
		return getBoolean( getValue( columnIndex ) );
	}

	@Override
	public byte getByte(int columnIndex) throws SQLException {
		return getByte( getValue( columnIndex ) );
	}

	@Override
	public short getShort(int columnIndex) throws SQLException {
		return getShort( getValue( columnIndex ) );
	}

	@Override
	public int getInt(int columnIndex) throws SQLException {
		return getInt( getValue( columnIndex ) );
	}

	@Override
	public long getLong(int columnIndex) throws SQLException {
		return getLong( getValue( columnIndex ) );
	}

	@Override
	public float getFloat(int columnIndex) throws SQLException {
		return getFloat( getValue( columnIndex ) );
	}

	@Override
	public double getDouble(int columnIndex) throws SQLException {
		return getDouble( getValue( columnIndex ) );
	}

	@Override
	public BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException {
		return getBigDecimal( getValue( columnIndex ), scale );
	}

	@Override
	public byte[] getBytes(int columnIndex) throws SQLException {
		return getBytes( getValue( columnIndex ) );
	}

	@Override
	public Date getDate(int columnIndex) throws SQLException {
		return getDate( getValue( columnIndex ) );
	}

	@Override
	public Time getTime(int columnIndex) throws SQLException {
		return getTime( getValue( columnIndex ) );
	}

	@Override
	public Timestamp getTimestamp(int columnIndex) throws SQLException {
		return getTimestamp( getValue( columnIndex ) );
	}

	@Override
	public InputStream getAsciiStream(int columnIndex) throws SQLException {
		return getAsciiStream( getValue( columnIndex ) );
	}

	@Override
	public InputStream getUnicodeStream(int columnIndex) throws SQLException {
		return getUnicodeStream( getValue( columnIndex ) );
	}

	@Override
	public InputStream getBinaryStream(int columnIndex) throws SQLException {
		return getBinaryStream( getValue( columnIndex ) );
	}

	@Override
	public Object getObject(int columnIndex) throws SQLException {
		return getValue( columnIndex );
	}

	@Override
	public Reader getCharacterStream(int columnIndex) throws SQLException {
		return getCharacterStream( getValue( columnIndex ) );
	}

	@Override
	public BigDecimal getBigDecimal(int columnIndex) throws SQLException {
		return getBigDecimal( getValue( columnIndex ) );
	}

	@Override
	public Object getObject(int columnIndex, Map<String, Class<?>> map) throws SQLException {
		return getObject( getValue( columnIndex ), map );
	}

	@Override
	public Ref getRef(int columnIndex) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public Blob getBlob(int columnIndex) throws SQLException {
		return getBlob( getValue( columnIndex ) );
	}

	@Override
	public Clob getClob(int columnIndex) throws SQLException {
		return getClob( getValue( columnIndex ) );
	}

	@Override
	public Array getArray(int columnIndex) throws SQLException {
		return getArray( getValue( columnIndex ) );
	}

	@Override
	public Date getDate(int columnIndex, Calendar cal) throws SQLException {
		return getDate( getValue( columnIndex ), cal );
	}

	@Override
	public Time getTime(int columnIndex, Calendar cal) throws SQLException {
		return getTime( getValue( columnIndex ), cal );
	}

	@Override
	public Timestamp getTimestamp(int columnIndex, Calendar cal) throws SQLException {
		return getTimestamp( getValue( columnIndex ), cal );
	}

	@Override
	public URL getURL(int columnIndex) throws SQLException {
		return getURL( getValue( columnIndex ) );
	}

	@Override
	public RowId getRowId(int columnIndex) throws SQLException {
		return getRowId( getValue( columnIndex ) );
	}

	@Override
	public NClob getNClob(int columnIndex) throws SQLException {
		return getNClob( getValue( columnIndex ) );
	}

	@Override
	public SQLXML getSQLXML(int columnIndex) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public String getNString(int columnIndex) throws SQLException {
		return getString( columnIndex );
	}

	@Override
	public Reader getNCharacterStream(int columnIndex) throws SQLException {
		return getCharacterStream( getValue( columnIndex ) );
	}

	@Override
	public <T> T getObject(int columnIndex, Class<T> type) throws SQLException {
		return getObject( getValue( columnIndex ), type );
	}

	// ------ String-based API -------

	@Override
	public String getString(String columnLabel) throws SQLException {
		return getString( getValue( columnLabel ) );
	}

	@Override
	public boolean getBoolean(String columnLabel) throws SQLException {
		return getBoolean( getValue( columnLabel ) );
	}

	@Override
	public byte getByte(String columnLabel) throws SQLException {
		return getByte( getValue( columnLabel ) );
	}

	@Override
	public short getShort(String columnLabel) throws SQLException {
		return getShort( getValue( columnLabel ) );
	}

	@Override
	public int getInt(String columnLabel) throws SQLException {
		return getInt( getValue( columnLabel ) );
	}

	@Override
	public long getLong(String columnLabel) throws SQLException {
		return getLong( getValue( columnLabel ) );
	}

	@Override
	public float getFloat(String columnLabel) throws SQLException {
		return getFloat( getValue( columnLabel ) );
	}

	@Override
	public double getDouble(String columnLabel) throws SQLException {
		return getDouble( getValue( columnLabel ) );
	}

	@Override
	public BigDecimal getBigDecimal(String columnLabel, int scale) throws SQLException {
		return getBigDecimal( getValue( columnLabel ), scale );
	}

	@Override
	public byte[] getBytes(String columnLabel) throws SQLException {
		return getBytes( getValue( columnLabel ) );
	}

	@Override
	public Date getDate(String columnLabel) throws SQLException {
		return getDate( getValue( columnLabel ) );
	}

	@Override
	public Time getTime(String columnLabel) throws SQLException {
		return getTime( getValue( columnLabel ) );
	}

	@Override
	public Timestamp getTimestamp(String columnLabel) throws SQLException {
		return getTimestamp( getValue( columnLabel ) );
	}

	@Override
	public InputStream getAsciiStream(String columnLabel) throws SQLException {
		return getAsciiStream( getValue( columnLabel ) );
	}

	@Override
	public InputStream getUnicodeStream(String columnLabel) throws SQLException {
		return getUnicodeStream( getValue( columnLabel ) );
	}

	@Override
	public InputStream getBinaryStream(String columnLabel) throws SQLException {
		return getBinaryStream( getValue( columnLabel ) );
	}

	@Override
	public Object getObject(String columnLabel) throws SQLException {
		return getValue( columnLabel );
	}

	@Override
	public Reader getCharacterStream(String columnLabel) throws SQLException {
		return getCharacterStream( getValue( columnLabel ) );
	}

	@Override
	public BigDecimal getBigDecimal(String columnLabel) throws SQLException {
		return getBigDecimal( getValue( columnLabel ) );
	}

	@Override
	public Object getObject(String columnLabel, Map<String, Class<?>> map) throws SQLException {
		return getObject( getValue( columnLabel ), map );
	}

	@Override
	public Ref getRef(String columnLabel) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public Blob getBlob(String columnLabel) throws SQLException {
		return getBlob( getValue( columnLabel ) );
	}

	@Override
	public Clob getClob(String columnLabel) throws SQLException {
		return getClob( getValue( columnLabel ) );
	}

	@Override
	public Array getArray(String columnLabel) throws SQLException {
		return getArray( getValue( columnLabel ) );
	}

	@Override
	public Date getDate(String columnLabel, Calendar cal) throws SQLException {
		return getDate( getValue( columnLabel ), cal );
	}

	@Override
	public Time getTime(String columnLabel, Calendar cal) throws SQLException {
		return getTime( getValue( columnLabel ), cal );
	}

	@Override
	public Timestamp getTimestamp(String columnLabel, Calendar cal) throws SQLException {
		return getTimestamp( getValue( columnLabel ), cal );
	}

	@Override
	public URL getURL(String columnLabel) throws SQLException {
		return getURL( getValue( columnLabel ) );
	}

	@Override
	public RowId getRowId(String columnLabel) throws SQLException {
		return getRowId( getValue( columnLabel ) );
	}

	@Override
	public NClob getNClob(String columnLabel) throws SQLException {
		return getNClob( getValue( columnLabel ) );
	}

	@Override
	public SQLXML getSQLXML(String columnLabel) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public String getNString(String columnLabel) throws SQLException {
		return getString( columnLabel );
	}

	@Override
	public Reader getNCharacterStream(String columnLabel) throws SQLException {
		return getCharacterStream( getValue( columnLabel ) );
	}

	@Override
	public <T> T getObject(String columnLabel, Class<T> type) throws SQLException {
		return getObject( getValue( columnLabel ), type );
	}

	// -------------- Update APIs ---------------

	@Override
	public boolean rowUpdated() throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public boolean rowInserted() throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public boolean rowDeleted() throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateNull(int columnIndex) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateBoolean(int columnIndex, boolean x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateByte(int columnIndex, byte x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateShort(int columnIndex, short x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateInt(int columnIndex, int x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateLong(int columnIndex, long x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateFloat(int columnIndex, float x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateDouble(int columnIndex, double x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateBigDecimal(int columnIndex, BigDecimal x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateString(int columnIndex, String x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateBytes(int columnIndex, byte[] x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateDate(int columnIndex, Date x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateTime(int columnIndex, Time x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateTimestamp(int columnIndex, Timestamp x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateAsciiStream(int columnIndex, InputStream x, int length) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateBinaryStream(int columnIndex, InputStream x, int length) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateCharacterStream(int columnIndex, Reader x, int length) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateObject(int columnIndex, Object x, int scaleOrLength) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateObject(int columnIndex, Object x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateNull(String columnLabel) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateBoolean(String columnLabel, boolean x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateByte(String columnLabel, byte x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateShort(String columnLabel, short x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateInt(String columnLabel, int x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateLong(String columnLabel, long x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateFloat(String columnLabel, float x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateDouble(String columnLabel, double x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateBigDecimal(String columnLabel, BigDecimal x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateString(String columnLabel, String x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateBytes(String columnLabel, byte[] x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateDate(String columnLabel, Date x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateTime(String columnLabel, Time x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateTimestamp(String columnLabel, Timestamp x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateAsciiStream(String columnLabel, InputStream x, int length) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateBinaryStream(String columnLabel, InputStream x, int length) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateCharacterStream(String columnLabel, Reader reader, int length) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateObject(String columnLabel, Object x, int scaleOrLength) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateObject(String columnLabel, Object x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void insertRow() throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateRow() throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void deleteRow() throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void refreshRow() throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void cancelRowUpdates() throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void moveToInsertRow() throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void moveToCurrentRow() throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateRef(int columnIndex, Ref x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateRef(String columnLabel, Ref x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateBlob(int columnIndex, Blob x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateBlob(String columnLabel, Blob x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateClob(int columnIndex, Clob x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateClob(String columnLabel, Clob x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateArray(int columnIndex, Array x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateArray(String columnLabel, Array x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateRowId(int columnIndex, RowId x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateRowId(String columnLabel, RowId x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateNString(int columnIndex, String nString) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateNString(String columnLabel, String nString) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateNClob(int columnIndex, NClob nClob) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateNClob(String columnLabel, NClob nClob) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateSQLXML(int columnIndex, SQLXML xmlObject) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateSQLXML(String columnLabel, SQLXML xmlObject) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateNCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateNCharacterStream(String columnLabel, Reader reader, long length) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateAsciiStream(int columnIndex, InputStream x, long length) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateBinaryStream(int columnIndex, InputStream x, long length) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateAsciiStream(String columnLabel, InputStream x, long length) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateBinaryStream(String columnLabel, InputStream x, long length) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateCharacterStream(String columnLabel, Reader reader, long length) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateBlob(int columnIndex, InputStream inputStream, long length) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateBlob(String columnLabel, InputStream inputStream, long length) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateClob(int columnIndex, Reader reader, long length) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateClob(String columnLabel, Reader reader, long length) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateNClob(int columnIndex, Reader reader, long length) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateNClob(String columnLabel, Reader reader, long length) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateNCharacterStream(int columnIndex, Reader x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateNCharacterStream(String columnLabel, Reader reader) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateAsciiStream(int columnIndex, InputStream x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateBinaryStream(int columnIndex, InputStream x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateCharacterStream(int columnIndex, Reader x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateAsciiStream(String columnLabel, InputStream x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateBinaryStream(String columnLabel, InputStream x) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateCharacterStream(String columnLabel, Reader reader) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateBlob(int columnIndex, InputStream inputStream) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateBlob(String columnLabel, InputStream inputStream) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateClob(int columnIndex, Reader reader) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateClob(String columnLabel, Reader reader) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateNClob(int columnIndex, Reader reader) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void updateNClob(String columnLabel, Reader reader) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}
}
