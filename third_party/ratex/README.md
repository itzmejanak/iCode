# RaTeX

`jniLibs/arm64-v8a/libratex_ffi.so` is RaTeX's native library: it parses LaTeX and lays it out as
KaTeX does, and iCode draws what it returns (docs/markdown-rendering-plan.md §2.4, §3.2). It is
copied unchanged from

- artifact: `io.github.darriousliu:ratex-android:0.1.15` on Maven Central (2026-10-16), its
  `jni/arm64-v8a/libratex_ffi.so`; the AAR's SHA-1 `54476b7a2e9cc4852a70f9d10e71db72daa43957`
  matches Maven Central's
- built from RaTeX-CMP tag `v0.1.15` (commit `d7bf13a`), whose RaTeX core is commit `5f7dc48` of
  https://github.com/erweixin/RaTeX (`VERSION` 0.1.15)
- file: 3,195,976 bytes, SHA-256 `1f89089504c96e0fc098e165f0419af14ec3fbd07956835c6b033b5ddfcde84a`

Only arm64-v8a ships, as for the rest of iCode's native code (`ndk.abiFilters` in
`app/build.gradle.kts`). The library is 16 KB page aligned (the build's `checkPageAlignment`
checks it), built for API 21 with NDK r27d, and links only `libc` and `libdl`.

## How iCode calls it

The library binds by JNI name to one class, so `app/src/main/java/io/ratex/RaTeXEngine.kt` keeps
RaTeX-CMP's package, class and method names exactly:

- `io.ratex.RaTeXEngine.nativeParseAndLayout(String latex, boolean displayMode, float[] rgba)`
  returns the formula's display list as JSON, or null; then
- `io.ratex.RaTeXEngine.nativeGetLastError()` returns why, for the calling thread.

The display list is read by `domain/markdown/MathLayout.kt` and drawn by
`ui/markdown/MarkdownMath.kt`, with the KaTeX fonts in `app/src/main/assets/ratex/fonts` (KaTeX
0.19.0's, byte for byte).

## Licence

RaTeX and RaTeX-CMP are MIT; the Rust crates linked into the library are MIT, Apache-2.0,
Unlicense, Zlib or Unicode-3.0, each used under MIT (with Unicode-3.0 where it is required). Their
texts ship in the APK: `app/src/main/resources/licenses/RaTeX.txt`. The KaTeX fonts are under the SIL
Open Font License 1.1: `app/src/main/resources/licenses/OFL-KaTeX.txt`.

## Upgrading

The release is built with `panic = "abort"`: a panic in RaTeX ends iCode's process. An upgrade
therefore repeats the checks of docs/markdown-rendering-plan.md §2.4 before it lands:

1. the new AAR's checksum against Maven Central's, and the library's alignment, API level and
   linked libraries;
2. the owner's formulas and the hostile inputs through the same release's desktop build, and drawn
   beside KaTeX;
3. on a phone, `MarkdownMathTest` (androidTest) through the real library;
4. the display-list fixtures in `app/src/test/resources/ratex` captured again, and
   `RaTeX.txt` regenerated from `cargo tree` of the new core.
