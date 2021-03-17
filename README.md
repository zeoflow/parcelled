# Parcelled - Android Library

## Intro
A fast annotation processor that auto-generates the Parcelable methods without writing them.

## Getting Started
For information on how to get started Parcelled, take a look at our [Getting Started](docs/getting-started.md) guide.

## Submitting Bugs or Feature Requests
Bugs or feature requests should be submitted at our [GitHub Issues section](https://github.com/zeoflow/parcelled/issues).

## How does it work?
### 1. Depend on our library

Parcelled for Android is available through Google's Maven Repository.
To use it:

1.  Open the `build.gradle` file for your application.
2.  Make sure that the `repositories` section includes Google's Maven Repository
    `google()`. For example:
```groovy
    allprojects {
    repositories {
        google()
        jcenter()
    }
    }
```

3.  Add the library to the `dependencies` section:
```groovy
    dependencies {
    // ...
    def parcelled_version = "1.0.0"

    implementation("com.zeoflow:parcelled-runtime:$parcelled_version")
    annotationProcessor("com.zeoflow:parcelled-compiler:$parcelled_version")
    // ...
    }
```

### 2. Usage
  #### 2.1 Import
  ```java
  import com.zeoflow.parcelled.Parcelled;
  import com.zeoflow.parcelled.ParcelledAdapter;
  import com.zeoflow.parcelled.ParcelledVersion;
  ```

  #### 2.2 Class Declaration
  ```java
  @Parcelled(version = 1)
  public abstract class CustomBean implements Parcelable {

      @Nullable
      public String firstName;

      @ParcelledVersion(after = 1, before = 2)
      @Nullable
      public String lastName;

      @ParcelledAdapter(DateTypeAdapter.class)
      @ParcelledVersion(before = 1)
      public Date birthday;

      public static CustomBean create(
        @NonNull String firstName,
        @NonNull String lastName,
        @NonNull Date birthday
      ) {
          return new Parcelled_Person(firstName, lastName, birthday);
      }
  }
  ```
    
## License
    Copyright 2020 ZeoFlow
    
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    
      http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

## 🏆 Contributors 🏆

<!-- ZEOBOT-LIST:START - Do not remove or modify this section -->
<!-- prettier-ignore-start -->
<!-- markdownlint-disable -->
<p float="left">
<a href="docs/contributors.md#pushpin-teodor-g-teodorhmx1"><img width="100" src="https://avatars.githubusercontent.com/u/22307006?v=4" hspace=5 title='Teodor G. (@TeodorHMX1) - click for details about the contributions'></a>
</p>

<!-- markdownlint-enable -->
<!-- prettier-ignore-end -->
<!-- ZEOBOT-LIST:END -->