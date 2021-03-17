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

package com.zeoflow.parcelled.demo.model;

import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zeoflow.parcelled.Parcelled;
import com.zeoflow.parcelled.ParcelledAdapter;
import com.zeoflow.parcelled.ParcelledVersion;

import java.util.Date;

@Parcelled(version = 1)
public abstract class Person implements Parcelable
{

    @Nullable
    public String name;

    @Nullable
    public String firstName;

    @ParcelledVersion(after = 1, before = 2)
    @Nullable
    public String lastName;

    @ParcelledAdapter(DateTypeAdapter.class)
    @ParcelledVersion(before = 1)
    public Date birthday;

    public int age;

    public Address address;

    public static Person create(@NonNull String name, @NonNull String firstName, @NonNull Date birthday, int age, Address address)
    {
        return new Parcelled_Person(name, firstName, "Doe", birthday, age, address);
    }

}
