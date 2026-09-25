// Placeholder replaced after linking by a real native executable.
//
// The Android Gradle Plugin packages shared-library CMake targets into the APK
// but ignores executable targets. A carrier is declared as a shared library so
// AGP registers the APK entry, then a POST_BUILD step overwrites it with the
// executable. The installer extracts it to the native library directory, which
// is the only location this app may execute from (runtime/bootstrap/README.md).
void icode_executable_carrier(void) {}
