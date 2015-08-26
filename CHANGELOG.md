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