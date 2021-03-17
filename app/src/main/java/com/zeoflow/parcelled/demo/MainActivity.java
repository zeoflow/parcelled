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

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.zeoflow.parcelled.demo.model.Address;
import com.zeoflow.parcelled.demo.model.Person;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity
{

    private static final String TAG = MainActivity.class.getSimpleName();
    private EditText mAgeEditText;
    private EditText mNameEditText;
    private EditText mBdayEditText;
    private EditText mStreetEditText;
    private EditText mPostcodeEditText;
    private EditText mCityEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mNameEditText = findViewById(R.id.fullName);
        mBdayEditText = findViewById(R.id.dateOfBirth);
        mAgeEditText = findViewById(R.id.age);
        mStreetEditText = findViewById(R.id.street);
        mPostcodeEditText = findViewById(R.id.postCode);
        mCityEditText = findViewById(R.id.city);

        findViewById(R.id.imvAdd).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                @SuppressLint("SimpleDateFormat") SimpleDateFormat df = new SimpleDateFormat("dd-MM-yyyy");
                // Create the parcelable object using the creator
                try
                {
                    int age = TextUtils.isEmpty(mAgeEditText.getText()) ? 0 : Integer.parseInt(mAgeEditText.getText().toString());
                    Date date = TextUtils.isEmpty(mBdayEditText.getText()) ? new Date(System.currentTimeMillis()) : df.parse(mBdayEditText.getText().toString());
                    Address address = Address.create(
                            mStreetEditText.getText().toString(),
                            mPostcodeEditText.getText().toString(),
                            mCityEditText.getText().toString(),
                            /* Country */ null);
                    Person person = Person.create(
                            mNameEditText.getText().toString(),
                            "last",
                            date, age, address);

                    Intent activityIntent = PersonActivity.createIntent(MainActivity.this, person);

                    if (activityIntent != null)
                    {
                        MainActivity.this.startActivity(activityIntent);
                    }
                } catch (ParseException e)
                {
                    Log.e(TAG, "onClick: Error parsing date", e);
                }
            }
        });
    }

}
