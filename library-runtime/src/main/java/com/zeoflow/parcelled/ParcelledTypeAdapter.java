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

import android.os.Parcel;

/**
 * Converts Java objects to and from Parcels.
 *
 * <p>By default Parcelled can parcel and unparcel data types known by
 * the {@link Parcel} class.  To support other types, like custom classes or other objects,
 * like {@link java.util.Date} objects or booleans, you can create a custom ParcelledTypeAdapter to
 * tell the Parcel extension how to parcel the object.
 *
 * <p>Here's an example ParcelledTypeAdapter for a Date object:
 *
 * <pre>
 * <code>
 * public class DateTypeAdapter implements ParcelledTypeAdapter<Date> {
 *   public Date fromParcel(Parcel in) {
 *     return new Date(in.readLong());
 *   }
 *
 *   public void toParcel(Date value, Parcel dest) {
 *     dest.writeLong(value.getTime());
 *   }
 * }
 * </code>
 * </pre>
 * <p>
 * You can tell the Parcel Extension to use this ParcelledTypeAdapter by using the {@link ParcelledAdapter}
 * annotation on any Date properties.
 *
 * <pre>
 * <code>
 * {@literal @}Parcelled public abstract class Foo {
 *   {@literal @}ParcelledAdapter(DateTypeAdapter.class) public abstract Date date;
 * }
 * </code>
 * </pre>
 */
public interface ParcelledTypeAdapter<T>
{

    /**
     * Creates a new object based on the values in the provided {@link Parcel}.
     *
     * @param in The {@link Parcel} which contains the values of {@code T}.
     *
     * @return A new object based on the values in {@code in}.
     */
    T fromParcel(Parcel in);

    /**
     * Writes {@code value} into {@code dest}.
     *
     * @param value The object to be written.
     * @param dest  The {@link Parcel} in which to write {@code value}.
     */
    void toParcel(T value, Parcel dest);

}