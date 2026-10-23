# Sets Linux up for Flux Titan Hub's desktop: Xfce or KDE Plasma, TigerVNC's server, sound, a
# user called flux. Run inside Linux as root by flux-linux.sh: proot or chroot, debian or
# ubuntu, xfce or kde.
set -e
MODE=${1:-proot}
DISTRO=${2:-debian}
DESKTOP=${3:-xfce}
# Raised whenever this script changes what's installed: flux-linux.sh runs it again on start
FLUX_GUEST_VERSION=4
export DEBIAN_FRONTEND=noninteractive
# apt stays root throughout: PRoot can't switch users
echo 'APT::Sandbox::User "root";' > /etc/apt/apt.conf.d/01flux-sandbox
if [ "$MODE" = chroot ]; then
    # Android lets only these groups use the network
    groupadd -g 3003 aid_inet 2>/dev/null || true
    groupadd -g 3004 aid_net_raw 2>/dev/null || true
    usermod -a -G aid_inet,aid_net_raw root || true
fi
apt-get update
apt-get -y upgrade || true
# The desktop itself
if [ "$DESKTOP" = kde ]; then
    desktop="plasma-desktop plasma-workspace kwin-x11 konsole dolphin kate systemsettings breeze breeze-icon-theme plasma-pa"
else
    desktop="xfce4 xfce4-terminal thunar mousepad adwaita-icon-theme"
fi
# A browser that runs here: Ubuntu's Firefox is a Snap, which can't
if [ "$DISTRO" = ubuntu ]; then browser=falkon; else browser=firefox-esr; fi
apt-get install -y --no-install-recommends $desktop $browser \
    dbus-x11 tigervnc-standalone-server tigervnc-tools xfonts-base fonts-dejavu-core \
    sudo ca-certificates locales pulseaudio pulseaudio-utils gvfs libglib2.0-bin libgl1-mesa-dri
echo "$DESKTOP" > /etc/flux-desktop
id flux >/dev/null 2>&1 || useradd -m -s /bin/bash flux
usermod -a -G sudo flux || true
[ "$MODE" = chroot ] && usermod -a -G aid_inet,aid_net_raw flux
echo 'flux ALL=(ALL) NOPASSWD:ALL' > /etc/sudoers.d/flux
chmod 440 /etc/sudoers.d/flux
cat > /usr/local/bin/flux-session <<'SESSION'
#!/bin/sh
# The desktop: TigerVNC's X server on 127.0.0.1:5901 (with the app's password), its sound from
# PulseAudio as plain PCM on 127.0.0.1:5911 (the app plays it), the desktop on both
geometry=${FLUX_GEOMETRY:-1920x1080}
export XDG_RUNTIME_DIR="/tmp/runtime-$(id -u)"
mkdir -p "$XDG_RUNTIME_DIR"
chmod 700 "$XDG_RUNTIME_DIR"
mkdir -p "$HOME/.vnc"
printf '%s\n' "$FLUX_PASSWORD" | vncpasswd -f > "$HOME/.vnc/passwd"
chmod 600 "$HOME/.vnc/passwd"
rm -f /tmp/.X1-lock /tmp/.X11-unix/X1
mkdir -p /tmp/.X11-unix
chmod 1777 /tmp/.X11-unix 2>/dev/null
Xvnc :1 -geometry "$geometry" -depth 24 -rfbport 5901 -localhost -SecurityTypes VncAuth \
    -PasswordFile "$HOME/.vnc/passwd" -AlwaysShared -AcceptSetDesktopSize >/tmp/flux-xvnc.log 2>&1 &
echo $! > /tmp/flux-xvnc.pid
# Sound: everything plays into a silent sink whose monitor goes out as raw 48 kHz stereo
if command -v pulseaudio >/dev/null 2>&1; then
    pulseaudio --kill >/dev/null 2>&1
    pulseaudio --daemonize=no --exit-idle-time=-1 --disallow-exit -n --disable-shm=yes \
        -L "module-null-sink sink_name=flux rate=48000 channels=2" \
        -L "module-native-protocol-unix" \
        -L "module-simple-protocol-tcp listen=127.0.0.1 port=5911 record=true playback=false source=flux.monitor format=s16le rate=48000 channels=2" \
        >/tmp/flux-pulse.log 2>&1 &
    echo $! > /tmp/flux-pulse.pid
    sleep 1
    pactl set-default-sink flux >/dev/null 2>&1
fi
# PRoot can't give Firefox its sandbox: without it, rather than not at all
if [ "$FLUX_MODE" = proot ]; then
    export MOZ_DISABLE_CONTENT_SANDBOX=1 MOZ_DISABLE_GMP_SANDBOX=1 MOZ_DISABLE_RDD_SANDBOX=1 MOZ_DISABLE_SOCKET_PROCESS_SANDBOX=1
fi
sleep 1
export DISPLAY=:1
# The session's bus, started here so the phone's shortcuts can open programs in this session
eval "$(dbus-launch --sh-syntax)"
{
    echo "export DISPLAY=:1"
    echo "export XDG_RUNTIME_DIR='$XDG_RUNTIME_DIR'"
    echo "export DBUS_SESSION_BUS_ADDRESS='$DBUS_SESSION_BUS_ADDRESS'"
    [ "$FLUX_MODE" = proot ] && echo "export MOZ_DISABLE_CONTENT_SANDBOX=1 MOZ_DISABLE_GMP_SANDBOX=1 MOZ_DISABLE_RDD_SANDBOX=1 MOZ_DISABLE_SOCKET_PROCESS_SANDBOX=1"
} > /tmp/flux-env
# The desktop chosen at install: KDE Plasma without compositing (no GPU behind VNC), or Xfce
if [ "$(cat /etc/flux-desktop 2>/dev/null)" = kde ]; then
    KWIN_COMPOSE=N startplasma-x11 >/tmp/flux-session.log 2>&1 &
else
    startxfce4 >/tmp/flux-session.log 2>&1 &
fi
echo $! > /tmp/flux-session.pid
wait $!
kill "$DBUS_SESSION_BUS_PID" "$(cat /tmp/flux-xvnc.pid)" "$(cat /tmp/flux-pulse.pid 2>/dev/null)" 2>/dev/null
rm -f /tmp/flux-xvnc.pid /tmp/flux-session.pid /tmp/flux-pulse.pid /tmp/flux-env
SESSION
chmod 755 /usr/local/bin/flux-session
# One program in the running desktop, from the phone's shortcuts
cat > /usr/local/bin/flux-run <<'RUN'
#!/bin/sh
[ -f /tmp/flux-env ] && . /tmp/flux-env
cd "$HOME"
if [ "$FLUX_TERMINAL" = 1 ]; then exec xfce4-terminal -e "$FLUX_EXEC"; else exec sh -c "$FLUX_EXEC"; fi
RUN
chmod 755 /usr/local/bin/flux-run
# Each program's icon on the desktop (Xfce's or Plasma's), added once (one deleted stays deleted), as it signs in
cat > /usr/local/bin/flux-desktop-icons <<'ICONS'
#!/bin/sh
desk="$HOME/Desktop"
seen="$HOME/.config/flux-desktop-icons"
mkdir -p "$desk" "$HOME/.config"
touch "$seen"
for f in /usr/share/applications/*.desktop /usr/local/share/applications/*.desktop; do
    [ -f "$f" ] || continue
    name=${f##*/}
    grep -qxF "$name" "$seen" && continue
    echo "$name" >> "$seen"
    grep -q '^Type=Application' "$f" || continue
    grep -q '^NoDisplay=true' "$f" && continue
    grep -q '^OnlyShowIn=' "$f" && continue
    grep -q '^Categories=.*Settings' "$f" && continue
    cp "$f" "$desk/$name"
    chmod +x "$desk/$name"
    # Trusted, so the desktop opens it without asking first
    gio set -t string "$desk/$name" metadata::xfce-exe-checksum "$(sha256sum "$desk/$name" | cut -d' ' -f1)" 2>/dev/null
done
ICONS
chmod 755 /usr/local/bin/flux-desktop-icons
mkdir -p /etc/xdg/autostart
printf '[Desktop Entry]\nType=Application\nName=Flux desktop icons\nExec=/usr/local/bin/flux-desktop-icons\nNoDisplay=true\n' > /etc/xdg/autostart/flux-desktop-icons.desktop
echo "$FLUX_GUEST_VERSION" > /etc/flux-guest-version
echo "flux: guest ready"
