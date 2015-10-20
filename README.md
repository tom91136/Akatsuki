# Akatsuki
[![Join the chat at https://gitter.im/tom91136/Akatsuki](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/tom91136/Akatsuki?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

[![Android Arsenal](https://img.shields.io/badge/Android%20Arsenal-Akatsuki-green.svg?style=flat)](https://android-arsenal.com/details/1/2230)
[![Build Status](https://travis-ci.org/tom91136/Akatsuki.svg)](https://travis-ci.org/tom91136/Akatsuki)
[![Download](https://api.bintray.com/packages/tom91136/maven/Akatsuki/images/download.svg)](https://bintray.com/tom91136/maven/Akatsuki/_latestVersion)

Akatsuki is an light weight Android library that handles [state restoration](http://developer.android.com/training/basics/activity-lifecycle/recreating.html) and argument passing via annotations.
The library automatically generates source files through JSR269 to ensure almost<sup>1</sup> zero performance impact.


State restoration

 - Retain state with `@Retained`
 - All types supported by `Bundle` can be `@Retained`
 - Inheritance is supported
 - Generic parameters are supported
 - `TypeConverter` and `@TransformationTemplate` for custom types
 - Compatible with other parcel and binding libraries
 
 
Argument passing

 - Pass arguments with `@Arg`
 - Supports ANY class, including `Activity`, `Fragment`, `Service`.  You name it!
 - Support for type safe builders
 
 
Why Akatsuki?

This library handles most Android IPC boilerplate that would otherwise be tedious to write and maintain. 

While AndroidAnnotations is awesome, I'm looking for a lightweight drop in solution for only eliminating IPC boilerplate, and writing my own seems to be the only solution. 
Dart, Icepick and FragmentArg is awesome on their own, but it gets tedious when used together because you have to call their static helper everywhere and some of them don't play nice with each other.  

Example usage:


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
        
        // you want to start AnotherActivity and pass some stuff:
        Builders.AnotherActivity().theAnswer("42").startActivity(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Akatsuki.save(this, outState);
    }
}

// in another activity class
public class AnotherActivity extends Activity {

    // @Args and @Retained can be used together
    @Arg @Retained String theAnswer; 
  
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Akatsuki.restore(this, savedInstanceState);
        // theAnswer is retrieved from the intent and persisted in case of any change
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

**For the time being, there seems to be several bugs with version `0.1.0` due to the addition of `@Arg`. For stability, you may want to use `0.3.0` for now while I work on `0.1.1`**

Things that are broken in `0.1.0`:

 * Builder does not work on inherited fragments
 * Builder looses type information after certain chained calls
 * `@Arg` doesn't seems to survive very well on it's own, need a `@Retained` field otherwise something breaks
 
Some of them are showstoppers which I apologise for, I was rushing the 0.1.0 release because it's taking forever.


Gradle dependencies:
```groovy
dependencies {
	compile 'com.sora.util.akatsuki:akatsuki-api:0.1.0'
	apt 'com.sora.util.akatsuki:akatsuki-compiler:0.1.0'
}
```
Optional parceler support:
```groovy
compile 'com.sora.util.akatsuki:akatsuki-parceler:0.1.0@aar'
```


Please pay special attention to the build script:

```groovy
// your source/target compatibility remains 1_7, do NOT change it to 1_8
compileOptions {
	sourceCompatibility JavaVersion.VERSION_1_7
	targetCompatibility JavaVersion.VERSION_1_7
}
// exception: do keep 1_8 if you happen to be using retrolambda
```

##### [Sample app(.apk)](http://jcenter.bintray.com/com/sora/util/akatsuki/sample/0.0.3/)
Showcasing (`Fragment` + `NumberPicker`/`EditText`)

## Proguard
Please use the following rules if you have proguard enabled in your build script:

```groovy
-dontwarn com.sora.util.akatsuki.**
-keep class com.sora.util.akatsuki.** { *; }
-keep class **$$BundleRetainer { *; }
-keepclasseswithmembernames class * {
    @com.sora.util.akatsuki.* <fields>;
}
```

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




