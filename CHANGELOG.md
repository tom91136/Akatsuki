
0.2.0 *(2015/10/23)*
----------------------------

Major refactor, cleaned up most of the configuration annotations.

Additions

 - @AkatsukiConfig for global configuration
 - @RetainConfig is now a type annotation
 - Flexible compiler flags and optimisation flags 
 - Better verbose messages
 - Tests now covers the public facing API (such as the `Akatsuki` class)
 
Removed

 - `Optimisation` is removed in favour of flags, this allows more flexibility in configuring
 - Multidimentional arrays is no longer supported in ETS, it wasn't properly implemented anyway
 - Arrays of types that take generic arguments are no longer supported due to non existing use case
 
Changes

 - Over half of the tests are reviewed and refactored if required
 - All `Test` suffix changed to `IntegrationTest` to better reflect the fact that all of them are integration tests
 
 
If you want multidimentional arrays and arrays of generic type back, please open a issue (and send a PR if you want it very badly)





0.1.0 *(2015/10/18)*
----------------------------
Akatsuki now takes care of the entire IPC, that includes the already supported state restoration plus argument passing!

Additions

 - Argument passing for any object (Fragment, Activity, Service, etc)
 - Compile-time checked builders
 - Free `BundleBuilder` 
 - Proguard rules added (in README)
 
Changes

 - `TypeConverter` is moved into a new annotation `@With` to accommodate `@Arg`
 
Check out the Wiki for more information on how to pass arguments using `@Arg` and the builder
  

0.0.3 *(2015/08/27)*
----------------------------


Additions

 - Compiler arguments added (use `-Aakatsuki.<name>=value` where name is one of the methods in `@RetainConfig` and value is the name of the enum value)

Changes

 - Generic types are working now, see the [issue](https://github.com/tom91136/Akatsuki/issues/6)
 
This version fixes a serious bug that causes generics to fail in spectacular ways. In version 0.0.2 one of the following can happen

 * Compile error with an invalid cast (if the parameter name is not T)
 * Runtime error at strange places caused by casting fields to some random type in `BundleRetainer`(if the parameter is T)
 
Please update ASAP. 

----------------


0.0.2 *(2015/08/26)*
----------------------------


Additions

 - Added `@RetainConfig` for global configuration
 - Added new options such as `LoggingLevel` and `Optimisation` for `@RetainConfig`
 - Added `RestorePolicy`
 - Added `RetainerCache` to minimize reflection use
 - Partial ETS functional(List interfaces only)
 - Added `TypeFilter` for generic class matching

Changes

 - Serialize and Deserialize are now low level APIs that you should not use
 - Parceler support now extends to all types(including collections)
 - `TypeConstraint` now works with type parameters
 - Slightly better sample app

NOTE: major changes in README with new sections

----------------


0.0.1 *(2015/08/12)*
----------------------------

Initial release.