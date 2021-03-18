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

import android.content.Intent;
import android.os.Bundle;

import com.zeoflow.app.Activity;

import com.zeoflow.parcelled.demo.model.Person;

public class MainActivity extends Activity
{

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.imvAdd).setOnClickListener(view ->
        {
            Person person = Person.create();

            Intent activityIntent = PersonActivity.createIntent(MainActivity.this, person);

            if (activityIntent != null)
            {
                MainActivity.this.startActivity(activityIntent);
            }
        });
    }

}
