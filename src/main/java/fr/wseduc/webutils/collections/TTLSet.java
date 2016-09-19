/*
 * Copyright © WebServices pour l'Éducation, 2014
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.wseduc.webutils.collections;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;

import java.util.*;

public class TTLSet<T> implements Set<T> {

	private final HashMap<T, Long> map = new HashMap<>();
	private final long ttl;

	public TTLSet(long ttl) {
		this(ttl, null, -1);
	}

	public TTLSet(final long ttl, Vertx vertx, long clearPeriod) {
		this.ttl = ttl;
		if (vertx != null && clearPeriod > 0l) {
			vertx.setPeriodic(clearPeriod, new Handler<Long>() {
				@Override
				public void handle(Long aLong) {
					final long now = System.currentTimeMillis();
					final Map<T, Long> copyMap = (Map<T, Long>) map.clone();
					for (Map.Entry<T, Long> e: copyMap.entrySet()) {
						if (now > (e.getValue() + ttl)) {
							map.remove(e.getKey());
						}
					}
				}
			});
		}
	}

	@Override
	public int size() {
		return map.size();
	}

	@Override
	public boolean isEmpty() {
		return map.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		return map.containsKey(o);
	}

	@Override
	public Iterator<T> iterator() {
		return map.keySet().iterator();
	}

	@Override
	public Object[] toArray() {
		return map.keySet().toArray();
	}

	@Override
	public <T1> T1[] toArray(T1[] a) {
		return map.keySet().toArray(a);
	}

	@Override
	public boolean add(T t) {
		final Long insertTime = map.get(t);
		final long now = System.currentTimeMillis();
		if (insertTime == null || now > (insertTime + ttl)) {
			map.put(t, now);
			return true;
		}
		return false;
	}

	@Override
	public boolean remove(Object o) {
		return map.remove(o) != null;
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return map.keySet().containsAll(c);
	}

	@Override
	public boolean addAll(Collection<? extends T> c) {
		boolean changed = false;
		for (T t: c) {
			if (add(t)) {
				changed = true;
			}
		}
		return changed;
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		return false;
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		boolean changed = false;
		for (Object t: c) {
			if (remove(t)) {
				changed = true;
			}
		}
		return changed;
	}

	@Override
	public void clear() {
		map.clear();
	}

}
