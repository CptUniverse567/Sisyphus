# Keep the pure core untouched by R8. It is reflected at runtime by no one, but
# its data classes are (de)serialized through the KeyValueStore contract.
-keep class org.sisyphus.core.** { *; }
