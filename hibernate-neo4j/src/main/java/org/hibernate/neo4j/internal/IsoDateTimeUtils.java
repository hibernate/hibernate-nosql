/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.neo4j.internal;

import org.hibernate.sql.spi.SqlAppender;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class IsoDateTimeUtils {
	private IsoDateTimeUtils() {
	}

	public static final String FORMAT_STRING_TIMESTAMP = "yyyy-MM-dd'T'HH:mm:ss";
	public static final String FORMAT_STRING_TIMESTAMP_WITH_MILLIS = FORMAT_STRING_TIMESTAMP + ".SSS";
	public static final String FORMAT_STRING_TIMESTAMP_WITH_NANOS = FORMAT_STRING_TIMESTAMP + ".SSSSSSSSS";
	public static final String FORMAT_STRING_TIMESTAMP_WITH_NANOS_AND_OFFSET = FORMAT_STRING_TIMESTAMP_WITH_NANOS + "XXX";
	public static final DateTimeFormatter DATE_TIME_FORMATTER_TIMESTAMP_WITH_NANOS = DateTimeFormatter.ofPattern(
			FORMAT_STRING_TIMESTAMP_WITH_NANOS,
			Locale.ENGLISH
	);
	public static final DateTimeFormatter DATE_TIME_FORMATTER_TIMESTAMP_WITH_NANOS_AND_OFFSET = DateTimeFormatter.ofPattern(
			FORMAT_STRING_TIMESTAMP_WITH_NANOS_AND_OFFSET,
			Locale.ENGLISH
	);

	private static final ThreadLocal<SimpleDateFormat> TIMESTAMP_WITH_MILLIS_FORMAT =
			ThreadLocal.withInitial( () -> new SimpleDateFormat( FORMAT_STRING_TIMESTAMP_WITH_MILLIS, Locale.ENGLISH ) );

	public static void appendAsTimestampWithNanos(
			SqlAppender appender,
			TemporalAccessor temporalAccessor,
			boolean supportsOffset,
			TimeZone jdbcTimeZone) {
		appendAsTimestamp(
				appender,
				temporalAccessor,
				supportsOffset,
				jdbcTimeZone,
				DATE_TIME_FORMATTER_TIMESTAMP_WITH_NANOS,
				DATE_TIME_FORMATTER_TIMESTAMP_WITH_NANOS_AND_OFFSET
		);
	}

	private static void appendAsTimestamp(
			SqlAppender appender,
			TemporalAccessor temporalAccessor,
			boolean supportsOffset,
			TimeZone jdbcTimeZone,
			DateTimeFormatter format,
			DateTimeFormatter formatWithOffset) {
		if ( temporalAccessor.isSupported( ChronoField.OFFSET_SECONDS ) ) {
			if ( supportsOffset ) {
				formatWithOffset.formatTo( temporalAccessor, appender );
			}
			else {
				format.formatTo(
						LocalDateTime.ofInstant(
								Instant.from( temporalAccessor ),
								jdbcTimeZone.toZoneId()
						),
						appender
				);
			}
		}
		else if ( temporalAccessor instanceof Instant instant ) {
			if ( supportsOffset ) {
				formatWithOffset.formatTo(
						instant.atZone( jdbcTimeZone.toZoneId() ),
						appender
				);
			}
			else {
				format.formatTo(
						LocalDateTime.ofInstant( instant, jdbcTimeZone.toZoneId() ),
						appender
				);
			}
		}
		else {
			format.formatTo( temporalAccessor, appender );
		}
	}

	public static void appendAsTimestampWithNanos(SqlAppender appender, Date date, TimeZone jdbcTimeZone) {
		if ( date instanceof Timestamp ) {
			// java.sql.Timestamp supports nano sec
			appendAsTimestamp(
					appender,
					date.toInstant().atZone( jdbcTimeZone.toZoneId() ),
					false,
					jdbcTimeZone,
					DATE_TIME_FORMATTER_TIMESTAMP_WITH_NANOS,
					DATE_TIME_FORMATTER_TIMESTAMP_WITH_NANOS_AND_OFFSET
			);
		}
		else {
			// java.util.Date supports only milli sec
			final SimpleDateFormat simpleDateFormat = TIMESTAMP_WITH_MILLIS_FORMAT.get();
			final TimeZone originalTimeZone = simpleDateFormat.getTimeZone();
			try {
				simpleDateFormat.setTimeZone( jdbcTimeZone );
				appender.appendSql( simpleDateFormat.format( date ) );
			}
			finally {
				simpleDateFormat.setTimeZone( originalTimeZone );
			}
		}
	}

	public static void appendAsTimestampWithMillis(
			SqlAppender appender,
			Calendar calendar,
			TimeZone jdbcTimeZone) {
		final SimpleDateFormat simpleDateFormat = TIMESTAMP_WITH_MILLIS_FORMAT.get();
		final TimeZone originalTimeZone = simpleDateFormat.getTimeZone();
		try {
			simpleDateFormat.setTimeZone( jdbcTimeZone );
			appender.appendSql( simpleDateFormat.format( calendar.getTime() ) );
		}
		finally {
			simpleDateFormat.setTimeZone( originalTimeZone );
		}
	}
}
