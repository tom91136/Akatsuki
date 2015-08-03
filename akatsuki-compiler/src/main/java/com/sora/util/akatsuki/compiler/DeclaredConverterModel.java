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

package com.sora.util.akatsuki.compiler;

import com.sora.util.akatsuki.TypeConstraint;

import javax.lang.model.type.DeclaredType;

/**
 * Project: Akatsuki
 * Created by Tom on 7/29/2015.
 */
public class DeclaredConverterModel {

	public final DeclaredType converter;
	public final TypeConstraint[] constraint;

	public DeclaredConverterModel(DeclaredType converter, TypeConstraint[] constraint) {
		this.converter = converter;
		this.constraint = constraint;
	}
}
