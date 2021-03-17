// Copyright 2021 ZeoFlow SRL
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.zeoflow.parcelled;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * An annotation that indicates the auto-parcel {@link ParcelledTypeAdapter} to use to
 * parcel and unparcel the field.  The value must be set to a valid {@link ParcelledTypeAdapter}
 * class.
 *
 * <pre>
 * <code>
 * {@literal @}Parcelled public abstract class Foo {
 *   {@literal @}ParcelledAdapter(DateTypeAdapter.class) public abstract Date date;
 * }
 * </code>
 * </pre>
 * <p>
 * The generated code will instantiate and use the {@code DateTypeAdapter} class to parcel and
 * unparcel the {@code date()} property. In order for the generated code to instantiate the
 * {@link ParcelledTypeAdapter}, it needs a public, no-arg constructor.
 */
@Target(FIELD)
@Retention(SOURCE)
@Documented
public @interface ParcelledAdapter
{

    Class<? extends ParcelledTypeAdapter<?>> value();

}