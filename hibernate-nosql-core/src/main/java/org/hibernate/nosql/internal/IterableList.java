/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.nosql.internal;

import java.util.AbstractList;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class IterableList<E> extends AbstractList<E> {

	private final Iterable<E> iterable;

	public IterableList(Iterable<E> iterable) {
		this.iterable = iterable;
	}

	@Override
	public E get(int index) {
		if (index < 0) {
			throw new IndexOutOfBoundsException();
		}
		final Iterator<E> iterator = iterable.iterator();
		E element;
		try {
			element = iterator.next();
			for ( int i = 0; i < index; i++ ) {
				element = iterator.next();
			}
		}
		catch (NoSuchElementException e) {
			throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size());
		}
		return element;
	}

	@Override
	public int size() {
		int size = 0;
		for ( E e : iterable ) {
			size++;
		}
		return size;
	}

	@Override
	public Iterator<E> iterator() {
		return iterable.iterator();
	}
}
