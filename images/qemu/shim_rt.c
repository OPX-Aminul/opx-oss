/* Minimal librt shim for Android NDK API 29 where POSIX shm_open/shm_unlink
 * are absent from bionic libc (added in API 30). Provides file-backed
 * implementations via a temporary directory so QEMU's meson shm_open probe
 * and the actual runtime code (which assign the result to an int fd) succeed.
 *
 * Note: This file is compiled standalone (not with -include), so it can safely
 * include system headers. The matching shim_rt.h used via -include in the
 * cross file is a minimal version that avoids including system headers.
 */
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/mman.h>
#include <sys/types.h>
#include <unistd.h>
#include <string.h>
#include <stdlib.h>
#include <stdarg.h>
#include <signal.h>
#include <errno.h>
#include <time.h>
#include <stdio.h>

#define SHM_DIR "/dev/shm"

static char *shm_path(const char *name) {
    if (name[0] == '/') name++;
    size_t len = strlen(name);
    char *path = malloc(strlen(SHM_DIR) + 1 + len + 1);
    if (!path) return NULL;
    sprintf(path, "%s/%s", SHM_DIR, name);
    return path;
}

int shm_open(const char *name, int oflag, mode_t mode) {
    char *path = shm_path(name);
    if (!path) return -1;
    int fd = open(path, oflag, mode);
    free(path);
    return fd;
}

int shm_unlink(const char *name) {
    char *path = shm_path(name);
    if (!path) return -1;
    int r = unlink(path);
    free(path);
    return r;
}

/* timer_create/timer_delete minimal stubs (QEMU may probe these) */
int timer_create(clockid_t clockid, struct sigevent *sevp, timer_t *timerid) {
    errno = ENOSYS;
    return -1;
}
int timer_delete(timer_t timerid) { return 0; }
int timer_settime(timer_t timerid, int flags, const struct itimerspec *newval,
                  struct itimerspec *oldval) { errno = ENOSYS; return -1; }
int timer_gettime(timer_t timerid, struct itimerspec *curr) { errno = ENOSYS; return -1; }

/* fallocate: declared in NDK headers but not linkable on API < 30.
 * Provide a no-op stub so QEMU's cc.has_function("fallocate") succeeds
 * and CONFIG_FALLOCATE gets defined, matching the FALLOC_FL_* header checks. */
int fallocate(int fd, int mode, off_t offset, off_t len) {
    errno = ENOSYS;
    return -1;
}
int fallocate64(int fd, int mode, off64_t offset, off64_t len) {
    errno = ENOSYS;
    return -1;
}
