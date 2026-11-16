// Process bridge for the Linux runtime.
//
// Every non-interactive runtime process arrives here: git, ripgrep, language
// servers and the installer (docs/runtime-and-terminal.md). Each gets pipes for
// its input and output. Interactive shells do not: a terminal session gets its
// pseudo-terminal from the terminal library (see TerminalSessionManager).

#define _GNU_SOURCE

#include <jni.h>

#include <errno.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/resource.h>
#include <sys/syscall.h>
#include <sys/wait.h>
#include <unistd.h>

static char **copy_string_array(JNIEnv *env, jobjectArray array, jsize *out_count) {
    jsize count = (*env)->GetArrayLength(env, array);
    char **copy = calloc((size_t)count + 1, sizeof(char *));
    if (copy == NULL) {
        return NULL;
    }
    for (jsize index = 0; index < count; index++) {
        jstring element = (jstring)(*env)->GetObjectArrayElement(env, array, index);
        const char *chars = (*env)->GetStringUTFChars(env, element, NULL);
        copy[index] = strdup(chars);
        (*env)->ReleaseStringUTFChars(env, element, chars);
        (*env)->DeleteLocalRef(env, element);
    }
    if (out_count != NULL) {
        *out_count = count;
    }
    return copy;
}

static void free_string_array(char **array) {
    if (array == NULL) {
        return;
    }
    for (char **entry = array; *entry != NULL; entry++) {
        free(*entry);
    }
    free(array);
}

// Closes every descriptor above stderr in the child.
//
// An app process holds dozens of them — its APK, binder, sockets, open
// DataStore files — and without this each one was inherited by every runtime
// process (measured: 31 open in a plain `ls`). Runs between fork and exec of a
// multi-threaded process, so it must not allocate.
static void close_inherited_descriptors(void) {
#ifdef __NR_close_range
    if (syscall(__NR_close_range, 3, ~0U, 0) == 0) {
        return;
    }
#endif
    // Kernels before 5.9 lack close_range.
    struct rlimit limit;
    int highest = 1024;
    if (getrlimit(RLIMIT_NOFILE, &limit) == 0 && limit.rlim_cur != RLIM_INFINITY) {
        highest = limit.rlim_cur < 65536 ? (int)limit.rlim_cur : 65536;
    }
    for (int fd = 3; fd < highest; fd++) {
        close(fd);
    }
}

static jintArray result_array(JNIEnv *env, jint pid, jint read_fd, jint write_fd) {
    jintArray result = (*env)->NewIntArray(env, 3);
    if (result == NULL) {
        return NULL;
    }
    jint values[3] = {pid, read_fd, write_fd};
    (*env)->SetIntArrayRegion(env, result, 0, 3, values);
    return result;
}

JNIEXPORT jintArray JNICALL
Java_dev_icode_mobile_data_runtime_NativeProcess_nativeSpawn(
        JNIEnv *env, jclass clazz,
        jobjectArray java_argv, jobjectArray java_env, jstring java_cwd) {
    (void)clazz;

    char **argv = copy_string_array(env, java_argv, NULL);
    char **envp = copy_string_array(env, java_env, NULL);
    const char *cwd_chars = (*env)->GetStringUTFChars(env, java_cwd, NULL);
    char *cwd = strdup(cwd_chars);
    (*env)->ReleaseStringUTFChars(env, java_cwd, cwd_chars);

    if (argv == NULL || envp == NULL || cwd == NULL || argv[0] == NULL) {
        free_string_array(argv);
        free_string_array(envp);
        free(cwd);
        return NULL;
    }

    int stdin_pipe[2] = {-1, -1};
    int stdout_pipe[2] = {-1, -1};
    if (pipe(stdin_pipe) != 0 || pipe(stdout_pipe) != 0) {
        goto failure;
    }

    pid_t pid = fork();
    if (pid < 0) {
        goto failure;
    }

    if (pid == 0) {
        // Child. Nothing here may allocate through the JNI environment.
        if (setsid() < 0) {
            _exit(70);
        }
        dup2(stdin_pipe[0], STDIN_FILENO);
        dup2(stdout_pipe[1], STDOUT_FILENO);
        dup2(stdout_pipe[1], STDERR_FILENO);
        close(stdin_pipe[0]);
        close(stdin_pipe[1]);
        close(stdout_pipe[0]);
        close(stdout_pipe[1]);

        close_inherited_descriptors();

        // PRoot ptraces the processes it launches; without this the tracee is
        // not dumpable and attaching fails.
        prctl(PR_SET_DUMPABLE, 1, 0, 0, 0);

        if (chdir(cwd) != 0) {
            _exit(72);
        }
        execve(argv[0], argv, envp);
        _exit(errno != 0 ? errno : 73);
    }

    // Parent.
    free_string_array(argv);
    free_string_array(envp);
    free(cwd);
    close(stdin_pipe[0]);
    close(stdout_pipe[1]);
    return result_array(env, (jint)pid, (jint)stdout_pipe[0], (jint)stdin_pipe[1]);

failure:
    if (stdin_pipe[0] >= 0) close(stdin_pipe[0]);
    if (stdin_pipe[1] >= 0) close(stdin_pipe[1]);
    if (stdout_pipe[0] >= 0) close(stdout_pipe[0]);
    if (stdout_pipe[1] >= 0) close(stdout_pipe[1]);
    free_string_array(argv);
    free_string_array(envp);
    free(cwd);
    return NULL;
}

/** Blocks until the process ends. Returns the exit code, or 128+signal. */
JNIEXPORT jint JNICALL
Java_dev_icode_mobile_data_runtime_NativeProcess_nativeWaitFor(
        JNIEnv *env, jclass clazz, jint pid) {
    (void)env;
    (void)clazz;
    int status = 0;
    while (waitpid((pid_t)pid, &status, 0) < 0) {
        if (errno != EINTR) {
            return -1;
        }
    }
    if (WIFEXITED(status)) {
        return (jint)WEXITSTATUS(status);
    }
    if (WIFSIGNALED(status)) {
        return (jint)(128 + WTERMSIG(status));
    }
    return -1;
}

/** Signals the whole process group, so a shell's children stop too. */
JNIEXPORT jint JNICALL
Java_dev_icode_mobile_data_runtime_NativeProcess_nativeSendSignal(
        JNIEnv *env, jclass clazz, jint pid, jint signal_number) {
    (void)env;
    (void)clazz;
    if (killpg((pid_t)pid, (int)signal_number) == 0) {
        return 0;
    }
    return kill((pid_t)pid, (int)signal_number) == 0 ? 0 : errno;
}

JNIEXPORT void JNICALL
Java_dev_icode_mobile_data_runtime_NativeProcess_nativeClose(
        JNIEnv *env, jclass clazz, jint fd) {
    (void)env;
    (void)clazz;
    if (fd >= 0) {
        close(fd);
    }
}
