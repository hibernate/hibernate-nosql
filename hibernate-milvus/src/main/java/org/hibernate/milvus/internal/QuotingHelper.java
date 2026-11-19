/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.milvus.internal;

import org.hibernate.sql.spi.SqlAppender;

/**
 * Helper to append JSON strings.
 */
public class QuotingHelper {

	private static final char[] HEX = "0123456789abcdef".toCharArray();

	public static void appendJsonString(SqlAppender sqlAppender, String text) {
		sqlAppender.append( '"' );
		for ( int i = 0; i < text.length(); i++ ) {
			final char c = text.charAt( i );
			switch ( c ) {
				case '"':
					sqlAppender.append( "\\\"" );
					break;
				case '\\':
					sqlAppender.append( "\\\\" );
					break;
				case '\b':
					sqlAppender.append( "\\b" );
					break;
				case '\f':
					sqlAppender.append( "\\f" );
					break;
				case '\n':
					sqlAppender.append( "\\n" );
					break;
				case '\r':
					sqlAppender.append( "\\r" );
					break;
				case '\t':
					sqlAppender.append( "\\t" );
					break;
				default:
					if ( c < 0x20 ) {
						sqlAppender.append( "\\u00" );
						sqlAppender.append( HEX[c >> 4] );
						sqlAppender.append( HEX[c & 0xF] );
					}
					else {
						sqlAppender.append( c );
					}
					break;
			}
		}
		sqlAppender.append( '"' );
	}
}
