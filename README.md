Akatsuki
============

[![Join the chat at https://gitter.im/tom91136/Akatsuki](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/tom91136/Akatsuki?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

[![Android Arsenal](https://img.shields.io/badge/Android%20Arsenal-Akatsuki-green.svg?style=flat)](https://android-arsenal.com/details/1/2230)
[![Build Status](https://travis-ci.org/tom91136/Akatsuki.svg)](https://travis-ci.org/tom91136/Akatsuki)


Akatsuki is an Android library that handles [state restoration](http://developer.android.com/training/basics/activity-lifecycle/recreating.html) via annotations.
The library automatically generates source files through JSR269 to ensure almost<sup>1</sup> zero performance impact.

Typical usage looks like:
```java
public class MainActivity extends Activity {

    @Retained String myString;
    @Retained int myInt;
    @Retained android.accounts.Account account; // Account implements Parcelable

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
Annotate the fields you want to persist, Akatsuki takes care of the rest.

If you are new to Android development, retaining fields are painful and error prone. You have to create string keys and then manually call the `set<Type>` and `get<Type>`of the `Bundle` object.
To demonstrate, here is the same Activity **without using Akatsuki:**

```java
public class MainActivity extends Activity {

    private static final String MY_STRING = "myString";
    private static final String MY_INT = "myInt";
    private static final String ACCOUNT = "account";
	
	String myString;
	int myInt;
    android.accounts.Account account;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		myString = savedInstanceState.getString(MY_STRING);
		myInt = savedInstanceState.getInt(MY_INT);
		account = savedInstanceState.getParcelable(ACCOUNT);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString(MY_STRING, myString);
		outState.putInt(MY_INT, myInt);
		outState.putParcelable(ACCOUNT, account);
	}
}
```
<sup>1</sup>Reflection is used only once to locate the generated classes.

----------

Advanced usage
---------
Akatsuki supports any kind of objects(Fragments, Services, and Views), not just Activity. As long as the persisting medium is a `Bundle`, the code generation will work.

For `View` states, Akatsuki provides several utility methods to make view state restoration possible. To demonstrate: 

```java
public static class MyView extends View {

	@Retained int myInt;

	public MyView(Context context) {
		super(context);
	}

	@Override
	protected Parcelable onSaveInstanceState() {
		return Akatsuki.save(this, super.onSaveInstanceState());
	}

	@Override
	protected void onRestoreInstanceState(Parcelable state) {
		super.onRestoreInstanceState(Akatsuki.restore(this, state));
	}
}
```

For debug purposes, `@Retain` has a method called `skip()`, when set to true, the field will not be retained. Adding the `transient` modifier also has the same effect.

If you need more control on how Akatsuki saves and restores your fields, you can use the `@RetainConfig` annotation. Annotate it anywhere you like as it's just a compile time annotation. 


Supported types
---------------
All data types supported by `Bundle` is supported by Akatsuki, that includes:
```java
IBinder
boolean
boolean[]
Bundle
byte
byte[]
char
char[]
CharSequence
CharSequence[]
ArrayList<CharSequence>
double
double[]
float
float[]
IBinder
int
int[]
ArrayList<Integer>
long
long[]
String
Parcelable
Parcelable[]
ArrayList<T>
Serializable
short
short[]
Size
SizeF
SparseArray<T>
String
String[]
ArrayList<String>
```
ETS (Extended Type Support) allows you to retain even more types:

 - Experimental support for multidimensional arrays of all supported types (not encouraged though as an unique key will have to be generated for EVERY element)
 - `LinkedList`, `CopyOnWriteArrayList` or simply `List` for all `ArrayList` supported types

More types will be added, submit an issue or pull request if you feel something is missing from the list.



##Generic parameters
Generic parameters are supported if and only if the type can be computed at compile time. This means that a type `<T>` must have known bounds.  
The following examples will work
```java
<T extends Parcelable> 
<T extends Serializable>
<T extends Parcelable & Serializable> // intersection type
```
When a intersection type is found, a search for supported type is executed from left to right. If a supported type is found, that type will be used for serialization.

Inheritance
-----------
**Inheritance is fully supported**, annotated fields will continue to persist in subclasses.

You can do something like this:
```java
public abstract class BaseRetainedFragment extends Fragment {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Akatsuki.restore(this, savedInstanceState);
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		Akatsuki.save(this, outState);
	}
}

public class FooFragment extends BaseRetainedFragment {

	@Retained int foo;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		final View view = inflater.inflate(R.layout.fragment_main, container, false);
		//Akatsuki.restore(this, savedInstanceState); thanks to inheritance support, you don't have to do this
		return view;
	}

}
```
NOTE: Even if you accidentally call restore more than once,  nothing would break as the last call will overwrite the result from previous calls.  

One interesting aspect of Akatsuki is that it give you control on how the object is retained, for example: you can add additional null checks while restoring by using `RestorePolicy.IF_NULL` in`@Retained` (or @RetainConfig if you want to do this globally)



That being said, a null check will be performed before an Object is restored so that we don't overwrite our previous restorations. Y




##Field hiding
[Field hiding](https://docs.oracle.com/javase/tutorial/java/IandI/hidevariables.html) is supported through traversing the class hierarchy to avoid field name collisions.
NOTE: The use of field hiding is discouraged as it makes code hard to follow.

Compatibility with other libraries
------

 - ParcelablePlease - working as intended
 - Parceler - plugin avaliable
 - FragmentArgs - not sure, have to test
 - Butterknife - working as intended



Parceler support
----------------
<a name="parceler></a>Akatsuki supports [Parceler](https://github.com/johncarl81/parceler) annotated beans
Simply add `@IncludeClasses(ParcelerSupport.class)` to any class in your project.(MainActivity or your custom Application class perhaps).
And don't forget to import:
```groovy
compile 'com.sora.util.akatsuki:akatsuki-parceler:0.0.1@aar'
```


TypeConverters
--------------
Akatsuki supports pluggable `TypeConverter`s , this allows you to support any type by writing a `TypeConverter` for it. 

Here's a custom type:
```java
public class Foo {
	String name;
	int id;
	Date timestamp;
	// more stuff
}
```
The `TypeConverter` would look something like this:
```java
public class FooConverter implements TypeConverter<Foo> {
	@Override
	public void save(Bundle bundle, Foo foo, String key) {
		bundle.putString(key + "name", foo.name);
		bundle.putInt(key + "id", foo.id);
		// Date.toString() is for illustration purpose only, do not use!
		bundle.putString(key + "timestamp", foo.timestamp.toString());
	}
	@Override
	public Foo restore(Bundle bundle, Foo initializer, String key) {
		Foo foo = new Foo();
		foo.name = bundle.getString(key + "name");
		foo.id = bundle.getInt(key + "id");
		// new Date(String) is for illustration purpose only, do not use!
		foo.timestamp = new Date(bundle.getString(key + "timestamp"));
		return foo;
	}
}
```
The example above can be simpler:
```java
public class BetterFooConverter extends MultiKeyTypeConverter<Foo> {
	@Override
	protected String[] saveMultiple(Bundle bundle, Foo foo) {
		String[] keys = generateKey(3);
		bundle.putString(keys[0], foo.name);
		bundle.putInt(keys[1], foo.id);
		bundle.putString(keys[2], foo.timestamp.toString());
		return keys;
	}
	@Override
	protected Foo restoreMultiple(Bundle bundle, Foo initializer, String[] keys) {
		Foo foo = new Foo();
		foo.name = bundle.getString(keys[0]);
		foo.id = bundle.getInt(keys[1]);
		foo.timestamp = new Date(bundle.getString(keys[2]));
		return foo;
	}
}
```
To let Akatsuki know about the converter, simply use:

```java
@Retained(converter = BetterFooConverter.class) Foo myFoo;
```
or if you use `Foo` everywhere, register the converter so that `Foo` automatically uses the converter:
```java
@DeclaredConverter(@TypeFilter(type=@TypeConstraint(type = Foo.class)))
public class BetterFooConverter extends MultiKeyTypeConverter<Foo> {
	// ...
}
```
Using converters do incur a tiny performance impact as the converter has to be instantiated before the type can be properly serialized(Only once through reflection). If you want something faster, take a look at `@TransformationTemplate`

@TransformationTemplate
----------------------
`@TransformationTemplate` allows you to support arbitrary types. This is the zero performance impact alternative to `TypeConverter`. To understand how this works, here's the what the class `ParcelerSupport` actually looks like:

```java
@SuppressWarnings("unused")
@TransformationTemplate(
		save = @StatementTemplate("{{bundle}}.putParcelable({{keyName}}, org.parceler.Parcels.wrap(" +
				                          "com.sora.util.akatsuki.parceler.ParcelerSupport.resolveInterfaceClass(" +
				                          "{{fieldName}}), {{fieldName}}))"),
		restore = @StatementTemplate(type = Type.ASSIGNMENT,
				                     value = "org.parceler.Parcels.unwrap({{bundle}}.getParcelable({{keyName}}))",
				                     variable = "{{fieldName}}"),
		filters = {@TypeFilter(type = @TypeConstraint(type = Parcel.class)),
				   @TypeFilter(type = @TypeConstraint(type = List.class, bound = Bound.EXTENDS),
						       parameters = @TypeConstraint(type = Parcel.class)),
				   @TypeFilter(type = @TypeConstraint(type = Set.class, bound = Bound.EXTENDS),
				               parameters = @TypeConstraint(type = Parcel.class)),
				   @TypeFilter(type = @TypeConstraint(type = Map.class, bound = Bound.EXTENDS),
				               parameters = @TypeConstraint(type = Parcel.class))
		          }
		)
//@formatter:on
public class ParcelerSupport {

	public static Class<?> resolveInterfaceClass(Object input) {
		if (input instanceof List) {
			return List.class;
		} else if (input instanceof Set) {
			return Set.class;
		} else if (input instanceof Map) {
			return Map.class;
		}
		return input.getClass();
	}

}

```
 [Parceler](https://github.com/johncarl81/parceler) annotated beans need `Parcels.wrap()` and `Parcels.unwrap()` before the object becomes usable. In the example above, we simply create a code template that includes the custom logic for wrapping and unwrapping Parceler objects.
Akatsuki uses [mustache](https://mustache.github.io/) to convert the template into code that gets emitted into the generated source file. Everything is done in compile time so there will be no runtime cost. The `@TransformationTemplate` annotation has a retention of `RetentionPolicy.CLASS` so that it has no effect in runtime while other libraries using the annotation could still retain the template. For more information on how to write Transformation templates, the [javadoc]() contains examples and docs for all methods in the annotation.

Due to a limitation of APT, if the template is located in another library, you have to include the class that is annotated with `@TransformationTemplate`  with `@IncludeClasses(TheClass.class)`, see the [Parceler support section ](#parceler) for more info.

**Warning: `@TransformationTemplate` does not do any validation on the templates, however,  syntax errors does prevent your project from compiling which can save you from debugging heedlessly.**

Low level APIs
----------
<a name="nested-retain"></a>Because `Bundle` is just a type safe container for `Parcel`,  Akatsuki also exposes a few low level APIs that allow you to work with Bundles and instances directly:

```java
// below are just examples, not recommended for general use
// you can have some bean
static class MyStuff{
	@Retained int foo;
	@Retained String bar;
	// your getters/setters etc
}

// serialize your bean into a Bundle
MyStuff stuff = new MyStuff();
Bundle bundle = Akatsuki.serialize(stuff);


// restore your bean from the Bundle
MyStuff stuff = Akatsuki.deserialize(new MyStuff(), bundle);
```
**Though this kind of usage is discouraged**. For general serialization, please make your Type implement Parcelable(either manually or through 3rd libraries such as Parceler and ParcelablePlease)
Now you might wonder if you can annotate `MyStuff` with `@Retained`. The answer is sort of, take a look at this example:
    
```java
@Retained MyStuff foo; 
@Retained int bar;

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    // manually instantiate your custom type
	foo = new MyStuff(/* some arguments*/); 
	// bar is a supported type so no need to instantiate
    Akatsuki.restore(this, savedInstanceState); 
}
```
Because Akatsuki does not know how `MyStuff` is created, you will have to instantiate it manually **BEFORE** `Akatsuki.restore(Object, Bundle)` is called. 
**Since annotating a java bean is discouraged, retaining a Type containing `@Retained` is also not recommended due to restrictions mentioned in the example.** 

Opt-out
-------
Akatsuki is designed to be flexible. You can still retain your fields the old way in conjunction with Akatsuki and nothing will break. There is only one thing to look out for: key collisions (Akatsuki uses the field name as key). 

Abuse
-----
While Akatsuki makes retaining state effortless, it is still constrained by Android's IPC limit (around 1MB, varies between different vendor). What this means is that the `Bundle` used to store your states can still blow up if you annotate too many fields or if the fields contain large amounts of data. When your bundle blows up, [strange things](http://stackoverflow.com/questions/11451393/what-to-do-on-transactiontoolargeexception) happen. 


Here are some tips on using Akatsuki:

 - Stuff like TextView text should be retained as Android does it for you (given your `View` has a valid id). 
 - Huge/Complex data structures should not saved as it might block the main thread while the state is saved
 - saving `Bitmap`s are not recommended since they are huge
 - Depending on circumstances, Android might decide that your state can be persisted without actual serialization. Your restored object could be the same object you saved, or a new one with the same value. 

The tips above applies to general Android programming as well.

Why another library?
--------
Currently, we have [Icepick](https://github.com/frankiesardo/icepick) and possibly some other libraries that I'm not aware of. The main motivation for this library is due to the inflexibility of Icepick. Icepick does not support [Parceler](https://github.com/johncarl81/parceler) which is a deal breaker for me (there's a [discussion](https://github.com/frankiesardo/icepick/pull/20) on why that's the case). Incompatibility between Parceler and Icepick does not justify the creation of a another library, I could have forked Icepick and added the support myself and be happy. But no, I want to see how APT works so I did a [clean room implementation](https://en.wikipedia.org/wiki/Clean_room_design) of Icepick and added some other features.

Download
--------

**IMPORTANT**

The compiler is written in Java 8 so make sure you have JDK8 or higher installed(use `java -version` to check)

Pay special attention to the build script:
```groovy
// your source/target compatibility remains 1_7, do NOT change it to 1_8
compileOptions {
	sourceCompatibility JavaVersion.VERSION_1_7
	targetCompatibility JavaVersion.VERSION_1_7
}
// exception: do keep 1_8 if you happen to be using retrolambda

```
The dependencies:
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
You can download the sample app [here](http://jcenter.bintray.com/com/sora/util/akatsuki/sample/0.0.2/) if you want to test it out (nothing surprising though, just a very simple demo with a `Fragment` + `NumberPicker`/`EditText`).


License
-------

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




