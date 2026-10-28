/*
 * ETA workspace bridge (protocol v1).
 * Privileged local service for capability handshake ONLY.
 * No app launches, display operations, generic shell or credential access.
 * Build: aarch64-linux-android33-clang -std=c11 -O2 -Wall -Wextra -Werror
 */
#define _GNU_SOURCE
#include <errno.h>
#include <signal.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/un.h>
#include <sys/time.h>
#include <unistd.h>

#ifndef SO_PEERCRED
#error "SO_PEERCRED is required for authorization"
#endif
#define ETA_SOCKET_NAME "eta.workspace.bridge.v1"
#define MAX_REQUEST_BYTES 95u
static volatile sig_atomic_t running = 1;
static void stop_server(int sig) { (void)sig; running = 0; }

static void send_response(int fd, const char *s) {
    size_t total = strlen(s), off = 0;
    while (off < total) {
        ssize_t n = send(fd, s + off, total - off, MSG_NOSIGNAL);
        if (n <= 0) return;
        off += (size_t)n;
    }
}

static void handle_client(int fd, uid_t expected) {
    struct ucred identity;
    socklen_t len = (socklen_t)sizeof(identity);
    if (getsockopt(fd, SOL_SOCKET, SO_PEERCRED, &identity, &len) != 0 ||
        len != sizeof(identity) || identity.uid != expected) return;

    const struct timeval timeout = { .tv_sec = 3, .tv_usec = 0 };
    (void)setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
    (void)setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));
    char request[MAX_REQUEST_BYTES + 1];
    size_t used = 0;
    while (used < MAX_REQUEST_BYTES) {
        char ch;
        if (recv(fd, &ch, 1, 0) != 1) return;
        if (ch == '\n') break;
        if (ch == '\r' || ch == '\0' || (unsigned char)ch < 32 ||
            (unsigned char)ch > 126) {
            send_response(fd, "{\"ok\":false,\"code\":\"MALFORMED_REQUEST\"}\n");
            return;
        }
        request[used++] = ch;
    }
    if (used == MAX_REQUEST_BYTES) {
        send_response(fd, "{\"ok\":false,\"code\":\"REQUEST_TOO_LARGE\"}\n");
        return;
    }
    request[used] = '\0';
    if (strcmp(request, "HELLO 1") == 0)
        send_response(fd, "{\"ok\":true,\"protocol\":1,\"service\":\"eta-workspace-bridge\",\"capabilities\":\"probe-only\"}\n");
    else if (strcmp(request, "CAPS") == 0)
        send_response(fd, "{\"ok\":true,\"protocol\":1,\"display_backend\":false,\"max_displays\":0,\"app_launch\":false,\"targeted_input\":false,\"screen_capture\":false}\n");
    else if (strcmp(request, "PING") == 0)
        send_response(fd, "{\"ok\":true,\"protocol\":1,\"status\":\"ready\"}\n");
    else if (strncmp(request, "HELLO ", 6) == 0)
        send_response(fd, "{\"ok\":false,\"code\":\"VERSION_UNSUPPORTED\"}\n");
    else
        send_response(fd, "{\"ok\":false,\"code\":\"OPERATION_UNSUPPORTED\"}\n");
}

static int parse_app_uid(const char *s, uid_t *uid) {
    if (s == NULL || *s == '\0' || *s == '-') return -1;
    char *end = NULL;
    errno = 0;
    unsigned long u = strtoul(s, &end, 10);
    if (errno || end == s || *end != '\0' || u < 10000 || u > 1999999) return -1;
    *uid = (uid_t)u;
    return 0;
}

int main(int argc, char **argv) {
    uid_t app_uid = 0;
    if (argc != 3 || strcmp(argv[1], "--app-uid") != 0 ||
        parse_app_uid(argv[2], &app_uid) != 0) {
        fprintf(stderr, "usage: eta-workspace-bridge --app-uid <Android-app-UID>\n");
        return 2;
    }
    int listener = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (listener < 0) { perror("socket"); return 1; }
    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    addr.sun_path[0] = '\0';
    const size_t name_len = strlen(ETA_SOCKET_NAME);
    memcpy(addr.sun_path + 1, ETA_SOCKET_NAME, name_len);
    const socklen_t len = (socklen_t)(offsetof(struct sockaddr_un, sun_path) + name_len + 1);
    if (bind(listener, (const struct sockaddr *)&addr, len) != 0 ||
        listen(listener, 4) != 0) {
        perror("bind/listen");
        close(listener);
        return 1;
    }
    struct sigaction handler;
    memset(&handler, 0, sizeof(handler));
    handler.sa_handler = stop_server;
    sigemptyset(&handler.sa_mask);
    (void)sigaction(SIGTERM, &handler, NULL);
    (void)sigaction(SIGINT, &handler, NULL);
    while (running) {
        int client = accept4(listener, NULL, NULL, SOCK_CLOEXEC);
        if (client < 0) {
            if (errno == EINTR) continue;
            break;
        }
        handle_client(client, app_uid);
        close(client);
    }
    close(listener);
    return 0;
}
