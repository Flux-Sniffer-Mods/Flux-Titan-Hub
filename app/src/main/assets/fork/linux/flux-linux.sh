# Flux Titan Hub's Linux desktop: Debian with Xfce, seen through VNC.
# Run by Android's sh, as the built-in shell (proot: no root) or as root through su (chroot).
#   flux-linux.sh install | start | stop | status | remove
# From the app, in the environment:
#   MODE     proot or chroot
#   BASE     where it lives (/data/local/tmp/flux-linux, or /data/local/flux-linux with root)
#   TARBALL  Debian's root file system (rootfs.tar.gz), for install
#   LIBDIR   the app's native libraries: libproot.so and libproot-loader.so (proot)
#   GUEST    the script set up inside Debian (flux-guest.sh), for install
#   GEOMETRY the desktop's size (1920x1080), PASSWORD its VNC password, for start

set -u
ROOTFS="$BASE/rootfs"
PIDS="$BASE/run"
mkdir -p "$BASE" "$PIDS"

say() { echo "flux: $*"; }

# PRoot copied beside the system (the shell may run what's here, not from the app's folder)
proot_ready() {
    mkdir -p "$BASE/bin" "$BASE/tmp"
    cp "$LIBDIR/libproot.so" "$BASE/bin/proot" && cp "$LIBDIR/libproot-loader.so" "$BASE/bin/loader" || return 1
    chmod 755 "$BASE/bin/proot" "$BASE/bin/loader"
    export PROOT_LOADER="$BASE/bin/loader" PROOT_TMP_DIR="$BASE/tmp"
}

# A command inside Debian: as root, or with ID (uid:gid) as that user
guest() {
    if [ "$MODE" = proot ]; then
        "$BASE/bin/proot" --kill-on-exit --link2symlink -0 -r "$ROOTFS" \
            -b /dev -b /proc -b /sys -b /sdcard -b /storage -b "$ROOTFS/tmp:/dev/shm" \
            -w /root /usr/bin/env -i HOME=/root USER=root TERM=xterm-256color LANG=C.UTF-8 \
            PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin "$@"
    else
        chroot "$ROOTFS" /usr/bin/env -i HOME=/root USER=root TERM=xterm-256color LANG=C.UTF-8 \
            PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin "$@"
    fi
}

# What a chroot needs from Android: devices, processes, storage, shared memory
chroot_mounts() {
    for d in dev proc sys sdcard dev/pts dev/shm; do mkdir -p "$ROOTFS/$d"; done
    grep -q " $ROOTFS/dev " /proc/mounts || mount -o bind /dev "$ROOTFS/dev"
    grep -q " $ROOTFS/dev/pts " /proc/mounts || mount -t devpts devpts "$ROOTFS/dev/pts"
    grep -q " $ROOTFS/dev/shm " /proc/mounts || mount -t tmpfs -o size=256M tmpfs "$ROOTFS/dev/shm"
    grep -q " $ROOTFS/proc " /proc/mounts || mount -t proc proc "$ROOTFS/proc"
    grep -q " $ROOTFS/sys " /proc/mounts || mount -t sysfs sysfs "$ROOTFS/sys"
    grep -q " $ROOTFS/sdcard " /proc/mounts || mount -o bind /data/media/0 "$ROOTFS/sdcard"
}

chroot_unmount() {
    for d in sdcard sys proc dev/shm dev/pts dev; do
        grep -q " $ROOTFS/$d " /proc/mounts && umount -l "$ROOTFS/$d"
    done
    return 0
}

install() {
    if [ ! -x "$ROOTFS/usr/bin/env" ]; then
        say "unpacking Debian"
        rm -rf "$ROOTFS"
        mkdir -p "$ROOTFS"
        if [ "$MODE" = proot ]; then
            proot_ready || { say "proot missing"; exit 2; }
            # Under PRoot, so links Android won't make are kept as PRoot's own
            "$BASE/bin/proot" --link2symlink -0 /system/bin/tar -xzf "$TARBALL" -C "$ROOTFS" || { say "unpack failed"; exit 3; }
        else
            tar -xzf "$TARBALL" -C "$ROOTFS" || { say "unpack failed"; exit 3; }
        fi
    fi
    [ "$MODE" = proot ] && { proot_ready || exit 2; }
    # Names resolved through public DNS: Android has no resolv.conf to share
    rm -f "$ROOTFS/etc/resolv.conf"
    printf 'nameserver 1.1.1.1\nnameserver 8.8.8.8\n' > "$ROOTFS/etc/resolv.conf"
    printf '127.0.0.1 localhost\n::1 localhost\n127.0.1.1 flux\n' > "$ROOTFS/etc/hosts"
    echo flux > "$ROOTFS/etc/hostname"
    cp "$GUEST" "$ROOTFS/root/flux-guest.sh"
    [ "$MODE" = chroot ] && chroot_mounts
    say "installing the desktop (a few minutes)"
    guest /bin/sh /root/flux-guest.sh "$MODE"
    result=$?
    [ "$MODE" = chroot ] && chroot_unmount
    [ $result -eq 0 ] || { say "install failed"; exit 4; }
    touch "$BASE/installed"
    say "installed"
}

start() {
    [ -f "$BASE/installed" ] || { say "not installed"; exit 5; }
    stop >/dev/null 2>&1
    [ "$MODE" = proot ] && { proot_ready || exit 2; }
    [ "$MODE" = chroot ] && chroot_mounts
    echo $$ > "$PIDS/start.pid"
    say "starting"
    if [ "$MODE" = proot ]; then
        guest /usr/bin/env FLUX_GEOMETRY="$GEOMETRY" FLUX_PASSWORD="$PASSWORD" /usr/local/bin/flux-session
    else
        guest /usr/bin/env FLUX_GEOMETRY="$GEOMETRY" FLUX_PASSWORD="$PASSWORD" /bin/su - flux -w FLUX_GEOMETRY,FLUX_PASSWORD -c /usr/local/bin/flux-session
        chroot_unmount
    fi
    rm -f "$PIDS/start.pid"
    say "stopped"
}

# Ends the desktop: the VNC server first (the session follows it), then whatever's left
stop() {
    for f in "$ROOTFS/tmp/flux-xvnc.pid" "$ROOTFS/tmp/flux-session.pid"; do
        [ -f "$f" ] && kill "$(cat "$f")" 2>/dev/null
    done
    sleep 1
    [ -f "$PIDS/start.pid" ] && kill "$(cat "$PIDS/start.pid")" 2>/dev/null
    rm -f "$ROOTFS/tmp/flux-xvnc.pid" "$ROOTFS/tmp/flux-session.pid" "$PIDS/start.pid"
    [ "$MODE" = chroot ] && chroot_unmount
    say "stopped"
}

status() {
    [ -f "$BASE/installed" ] && echo "installed" || echo "not-installed"
    if [ -f "$ROOTFS/tmp/flux-xvnc.pid" ] && kill -0 "$(cat "$ROOTFS/tmp/flux-xvnc.pid")" 2>/dev/null; then
        echo running
    else
        echo stopped
    fi
}

remove() {
    stop >/dev/null 2>&1
    [ "$MODE" = chroot ] && chroot_unmount
    # Never through a mount still in place: the phone's own files would go with it
    if grep -q " $ROOTFS/" /proc/mounts; then say "still mounted, not removed"; exit 6; fi
    rm -rf "$BASE"
    say "removed"
}

case "${1:-}" in
    install) install ;;
    start) start ;;
    stop) stop ;;
    status) status ;;
    remove) remove ;;
    *) say "install | start | stop | status | remove"; exit 1 ;;
esac
