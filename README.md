![Logo](https://github.com/tom91136/Akatsuki/blob/master/akatsuki_logo.png)
# Akatsuki
[![Join the chat at https://gitter.im/tom91136/Akatsuki](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/tom91136/Akatsuki?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

[![Android Arsenal](https://img.shields.io/badge/Android%20Arsenal-Akatsuki-green.svg?style=flat)](https://android-arsenal.com/details/1/2230)
[![Build Status](https://travis-ci.org/tom91136/Akatsuki.svg)](https://travis-ci.org/tom91136/Akatsuki)

Akatsuki is an Android library that handles [state restoration](http://developer.android.com/training/basics/activity-lifecycle/recreating.html) via annotations.
The library automatically generates source files through JSR269 to ensure almost<sup>1</sup> zero performance impact.
- Retain state with `@Retained`
- All types supported by `Bundle` can be `@Retained`
- Inheritance is supported
- Generic parameters are supported
- `TypeConverter` for custom types
- Compatible with other parcel and binding libraries

```java
public class MainActivity extends Activity {

    @Retained String myString;
    @Retained int myInt;
    @Retained android.accounts.Account account; // implements Parcelable

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Akatsuki.restore(this, savedInstanceState);
        //everything restored!   
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Akatsuki.save(this, outState);
    }
}
```
<sup>1</sup>Reflection is used only once to locate the generated classes.

For documentation and additional information see [the wiki](https://github.com/tom91136/Akatsuki/wiki)

## Download
**The compiler is written in Java 8 so make sure you have JDK8 or higher installed(use `java -version` to check)**
Gradle dependencies:
```groovy
dependencies {
	compile 'com.sora.util.akatsuki:akatsuki-api:0.0.2'
	apt 'com.sora.util.akatsuki:akatsuki-compiler:0.0.2'
}
```
Optional parceler support:
```groovy
compile 'com.sora.util.akatsuki:akatsuki-parceler:0.0.2@aar'
```

##### [Sample app(.apk)](http://jcenter.bintray.com/com/sora/util/akatsuki/sample/0.0.2/)
Showcasing (`Fragment` + `NumberPicker`/`EditText`)

## License

    Copyright 2015 WEI CHEN LIN

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.




