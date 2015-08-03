/*
 * Copyright 2015 WEI CHEN LIN
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sora.util.akatsuki;

import android.os.Bundle;

public final class InvalidTypeConverter<T> implements TypeConverter<T> {

	private final Exception e;

	public InvalidTypeConverter(Exception e) {
		this.e = e;
	}

	@Override
	public void save(Bundle bundle, T t, String key) {
		throw new RuntimeException("Unable to find a TypeConverter for field  " + key + ", type="
				+ (t == null ? "null" : t.getClass()), e);
	}

	@Override
	public T restore(Bundle bundle, String key) {
		throw new RuntimeException("Unable to find a TypeConverter for field  " + key, e);
	}
}
