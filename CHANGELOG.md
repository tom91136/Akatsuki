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