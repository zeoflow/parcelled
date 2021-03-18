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

package com.zeoflow.parcelled.demo;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.zeoflow.parcelled.demo.model.Person;

public class PersonActivity extends AppCompatActivity
{

    private static final String EXTRA_PERSON = "EXTRA_PERSON";

    @Nullable
    public static Intent createIntent(@NonNull Context context, Person person)
    {
        //noinspection ConstantConditions
        if (context == null)
        {
            return null;
        }
        Intent intent = new Intent(context, PersonActivity.class);
        // we need to cast it to Parcelable because Person does not itself implement parcelable
        intent.putExtra(EXTRA_PERSON, person);
        return intent;
    }
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_person);

        TextView fullName = (TextView) findViewById(R.id.fullName);
        TextView date = (TextView) findViewById(R.id.dateOfBirth);
        TextView age = (TextView) findViewById(R.id.age);
        TextView fullAddress = (TextView) findViewById(R.id.fullAddress);

        // get the passed intent
        Intent intent = getIntent();
        if (intent != null)
        {
            Person person = intent.getParcelableExtra(EXTRA_PERSON);
            fullName.setText(getString(R.string.formatName, person.name));
            date.setText(getString(R.string.format_date, person.birthday.toString()));
            age.setText(getString(R.string.format_age, person.age));
            fullAddress.setText(getString(R.string.full_address,
                    TextUtils.isEmpty(person.address.street) ? "street: " : person.address.street,
                    TextUtils.isEmpty(person.address.postCode) ? "\npost code: " : person.address.postCode,
                    TextUtils.isEmpty(person.address.city) ? "\ncity: " : person.address.city,
                    TextUtils.isEmpty(person.address.country) ? "\ncountry: " : person.address.country));
        }
    }

}
