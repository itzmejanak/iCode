cmake_minimum_required(VERSION 3.22.1)
project(icode_runtime_native C ASM)

# PRoot's syscall translation is architecture specific and the runtime rootfs is
# ARM64. Fail loudly rather than producing an APK that cannot start a runtime.
if(NOT ANDROID_ABI STREQUAL "arm64-v8a")
    message(FATAL_ERROR "iCode's Linux runtime currently supports only arm64-v8a")
endif()

set(PROJECT_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../..")
set(PROOT_UPSTREAM "${PROJECT_ROOT}/third_party/proot")
set(SHM_ROOT "${PROJECT_ROOT}/third_party/libandroid-shmem")
set(TALLOC_ROOT "${PROJECT_ROOT}/third_party/talloc")

foreach(required "${PROOT_UPSTREAM}/src/cli/proot.c" "${SHM_ROOT}/shmem.c" "${TALLOC_ROOT}/talloc.c")
    if(NOT EXISTS "${required}")
        message(FATAL_ERROR "Missing ${required}. Run: git submodule update --init --recursive")
    endif()
endforeach()

# ---------------------------------------------------------------------------
# Upstream sources, with iCode's fixes applied
# ---------------------------------------------------------------------------

# Submodules stay on their upstream tags. iCode's fixes are patch files in
# third_party/patches/<name>, applied to a copy here, so a clone never depends
# on a commit that exists only in one checkout. Each patch explains itself in
# its header.
find_program(PATCH_EXECUTABLE patch REQUIRED)

# Copies <upstream> to the build tree, applies third_party/patches/<name>/*.patch
# in order, and sets <out_var> to the copy.
function(icode_patched_copy name upstream out_var)
    set(patch_dir "${PROJECT_ROOT}/third_party/patches/${name}")
    # Re-run this step whenever a patch is added, removed or edited, or an
    # upstream file changes: CONFIGURE_DEPENDS re-checks the globs on every build.
    file(GLOB patches CONFIGURE_DEPENDS "${patch_dir}/*.patch")
    list(SORT patches)
    file(GLOB_RECURSE upstream_files CONFIGURE_DEPENDS "${upstream}/*")
    set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS ${patches})
    set(patched "${CMAKE_CURRENT_BINARY_DIR}/${name}")
    file(REMOVE_RECURSE "${patched}")
    file(COPY "${upstream}/" DESTINATION "${patched}" PATTERN ".git" EXCLUDE)
    foreach(patch_file IN LISTS patches)
        execute_process(
            COMMAND "${PATCH_EXECUTABLE}" -p1 --batch --forward --quiet
                -d "${patched}" -i "${patch_file}"
            RESULT_VARIABLE patch_result
            OUTPUT_VARIABLE patch_output
            ERROR_VARIABLE patch_output
        )
        if(NOT patch_result EQUAL 0)
            message(FATAL_ERROR "Could not apply ${patch_file}:\n${patch_output}")
        endif()
    endforeach()
    set(${out_var} "${patched}" PARENT_SCOPE)
endfunction()

icode_patched_copy(proot "${PROOT_UPSTREAM}" PROOT_PATCHED)
set(PROOT_ROOT "${PROOT_PATCHED}/src")
icode_patched_copy(libandroid-shmem "${SHM_ROOT}" SHM_PATCHED)

# ---------------------------------------------------------------------------
# Process bridge (JNI)
# ---------------------------------------------------------------------------

add_library(icodespawn SHARED icode_spawn.c)
target_link_libraries(icodespawn PRIVATE log)

# ---------------------------------------------------------------------------
# Terminal JNI: Termux's, built here for 16 KB pages (third_party/termux-terminal-jni)
# ---------------------------------------------------------------------------

add_library(termux SHARED "${PROJECT_ROOT}/third_party/termux-terminal-jni/termux.c")
target_compile_options(termux PRIVATE -std=c11 -Wall -Wextra -Os)

# ---------------------------------------------------------------------------
# PRoot dependencies
# ---------------------------------------------------------------------------

# The NDK toolchain sets CMAKE_PLATFORM_NO_VERSIONED_SONAME, so this produces
# libtalloc.so with a matching SONAME. That is why building from source avoids
# the libtalloc.so.2 naming problem that prebuilt Termux binaries would create:
# Android only extracts lib*.so entries from an APK.
add_library(talloc SHARED "${TALLOC_ROOT}/talloc.c")
target_include_directories(talloc PUBLIC "${TALLOC_ROOT}")
target_compile_definitions(talloc PRIVATE
    _GNU_SOURCE
    __STDC_WANT_LIB_EXT1__=1
    TALLOC_BUILD_VERSION_MAJOR=2
    TALLOC_BUILD_VERSION_MINOR=4
    TALLOC_BUILD_VERSION_RELEASE=3
)
set_target_properties(talloc PROPERTIES OUTPUT_NAME talloc SOVERSION 2)

add_library(android-shmem SHARED "${SHM_PATCHED}/shmem.c")
target_include_directories(android-shmem PUBLIC "${SHM_PATCHED}")
target_link_libraries(android-shmem PRIVATE log android)

# ---------------------------------------------------------------------------
# PRoot loader
# ---------------------------------------------------------------------------

add_executable(prootloader
    "${PROOT_ROOT}/loader/loader.c"
    "${PROOT_ROOT}/loader/assembly.S"
)
target_include_directories(prootloader PRIVATE "${PROOT_ROOT}")
target_compile_options(prootloader PRIVATE -fPIC -ffreestanding)
target_link_options(prootloader PRIVATE
    -static -nostdlib
    "-Wl,--build-id=none,--rosegment,-Ttext=0x2000000000,-z,noexecstack,-u,pokedata_workaround"
)
set_target_properties(prootloader PROPERTIES OUTPUT_NAME "prootloader-bin" SUFFIX "")

add_custom_command(
    OUTPUT "${CMAKE_CURRENT_BINARY_DIR}/loader-info.c"
    COMMAND "${CMAKE_COMMAND}"
        "-DREADELF=${CMAKE_READELF}"
        "-DLOADER=$<TARGET_FILE:prootloader>"
        "-DOUTPUT=${CMAKE_CURRENT_BINARY_DIR}/loader-info.c"
        -P "${CMAKE_CURRENT_SOURCE_DIR}/GenerateLoaderInfo.cmake"
    DEPENDS prootloader "${CMAKE_CURRENT_SOURCE_DIR}/GenerateLoaderInfo.cmake"
    VERBATIM
)

# ---------------------------------------------------------------------------
# PRoot
# ---------------------------------------------------------------------------

set(PROOT_SOURCES
    cli/cli.c cli/proot.c cli/note.c
    execve/enter.c execve/exit.c execve/shebang.c execve/elf.c
    execve/ldso.c execve/auxv.c execve/aoxp.c
    path/binding.c path/glue.c path/canon.c path/f2fs-bug.c
    path/path.c path/proc.c path/temp.c
    syscall/seccomp.c syscall/syscall.c syscall/chain.c syscall/enter.c
    syscall/exit.c syscall/sysnum.c syscall/socket.c syscall/heap.c
    syscall/rlimit.c syscall/pipe_shadow.c
    tracee/tracee.c tracee/mem.c tracee/reg.c tracee/event.c
    tracee/seccomp.c tracee/statx.c
    ptrace/ptrace.c ptrace/user.c ptrace/wait.c
    extension/extension.c extension/ashmem_memfd/ashmem_memfd.c
    extension/kompat/kompat.c
    extension/fake_id0/chown.c extension/fake_id0/chroot.c
    extension/fake_id0/getsockopt.c extension/fake_id0/sendmsg.c
    extension/fake_id0/socket.c extension/fake_id0/open.c
    extension/fake_id0/unlink.c extension/fake_id0/rename.c
    extension/fake_id0/chmod.c extension/fake_id0/utimensat.c
    extension/fake_id0/access.c extension/fake_id0/exec.c
    extension/fake_id0/link.c extension/fake_id0/symlink.c
    extension/fake_id0/mk.c extension/fake_id0/stat.c
    extension/fake_id0/helper_functions.c extension/fake_id0/fake_id0.c
    extension/hidden_files/hidden_files.c extension/mountinfo/mountinfo.c
    extension/port_switch/port_switch.c extension/sysvipc/sysvipc.c
    extension/sysvipc/sysvipc_msg.c extension/sysvipc/sysvipc_sem.c
    extension/sysvipc/sysvipc_shm.c extension/link2symlink/link2symlink.c
    extension/fix_symlink_size/fix_symlink_size.c
)
list(TRANSFORM PROOT_SOURCES PREPEND "${PROOT_ROOT}/")

file(WRITE "${CMAKE_CURRENT_BINARY_DIR}/build.h" [=[
#ifndef BUILD_H
#define BUILD_H
#define VERSION "5.1.107.92"
#define HAVE_PROCESS_VM 1
#define HAVE_SECCOMP_FILTER 1
#endif
]=])

add_executable(proot ${PROOT_SOURCES} "${CMAKE_CURRENT_BINARY_DIR}/loader-info.c")
target_include_directories(proot PRIVATE
    "${PROOT_ROOT}"
    "${CMAKE_CURRENT_BINARY_DIR}"
    "${TALLOC_ROOT}"
    "${SHM_PATCHED}"
)
# PROOT_UNBUNDLE_LOADER makes PRoot expect an external loader instead of
# writing one into a temporary directory, which Android would refuse to execute.
target_compile_definitions(proot PRIVATE
    _FILE_OFFSET_BITS=64
    _GNU_SOURCE
    ARG_MAX=131072
    WITH_LIBANDROID_SHMEM
    PROOT_UNBUNDLE_LOADER="."
)
target_compile_options(proot PRIVATE
    -Wall -Wextra -O2
    "SHELL:-include string.h"
    "SHELL:-include ${SHM_PATCHED}/shm.h"
)
target_link_options(proot PRIVATE "-Wl,-z,noexecstack")
target_link_libraries(proot PRIVATE talloc android-shmem)
set_target_properties(proot PROPERTIES OUTPUT_NAME "proot-bin" SUFFIX "")

# ---------------------------------------------------------------------------
# Carriers: see runtime/bootstrap/README.md section 3.
# ---------------------------------------------------------------------------

function(add_executable_carrier carrier_name executable_target)
    add_library(${carrier_name} SHARED executable_carrier.c)
    set_target_properties(${carrier_name} PROPERTIES OUTPUT_NAME ${executable_target})
    add_dependencies(${carrier_name} ${executable_target})
    # add_dependencies only orders the builds. Without this the carrier is not
    # relinked when the executable changes, its POST_BUILD copy never runs, and
    # the APK keeps shipping the previous executable.
    set_property(TARGET ${carrier_name} APPEND PROPERTY
        LINK_DEPENDS "$<TARGET_FILE:${executable_target}>")
    add_custom_command(TARGET ${carrier_name} POST_BUILD
        COMMAND "${CMAKE_COMMAND}" -E copy
            "$<TARGET_FILE:${executable_target}>" "$<TARGET_FILE:${carrier_name}>"
    )
endfunction()

add_executable_carrier(proot-carrier proot)
add_executable_carrier(prootloader-carrier prootloader)

# 16 KB memory pages are required on recent Android releases.
# The loader is an executable, not a library, but the kernel maps it too.
foreach(target icodespawn termux talloc android-shmem proot prootloader proot-carrier prootloader-carrier)
    target_link_options(${target} PRIVATE "-Wl,-z,max-page-size=16384")
endforeach()
