# Sets Debian up for Flux Titan Hub's desktop: Xfce, TigerVNC's server, a user called flux.
# Run inside Debian as root by flux-linux.sh; $1 is proot or chroot.
set -e
MODE=${1:-proot}
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
apt-get install -y --no-install-recommends \
    xfce4 xfce4-terminal thunar mousepad dbus-x11 \
    tigervnc-standalone-server tigervnc-tools xfonts-base \
    fonts-dejavu-core adwaita-icon-theme sudo firefox-esr ca-certificates
id flux >/dev/null 2>&1 || useradd -m -s /bin/bash flux
usermod -a -G sudo flux || true
[ "$MODE" = chroot ] && usermod -a -G aid_inet,aid_net_raw flux
echo 'flux ALL=(ALL) NOPASSWD:ALL' > /etc/sudoers.d/flux
chmod 440 /etc/sudoers.d/flux
cat > /usr/local/bin/flux-session <<'SESSION'
#!/bin/sh
# The desktop: TigerVNC's X server on 127.0.0.1:5901 (with the app's password), Xfce on it
geometry=${FLUX_GEOMETRY:-1920x1080}
mkdir -p "$HOME/.vnc"
printf '%s\n' "$FLUX_PASSWORD" | vncpasswd -f > "$HOME/.vnc/passwd"
chmod 600 "$HOME/.vnc/passwd"
rm -f /tmp/.X1-lock /tmp/.X11-unix/X1
mkdir -p /tmp/.X11-unix
chmod 1777 /tmp/.X11-unix 2>/dev/null
Xvnc :1 -geometry "$geometry" -depth 24 -rfbport 5901 -localhost -SecurityTypes VncAuth \
    -PasswordFile "$HOME/.vnc/passwd" -AlwaysShared -AcceptSetDesktopSize >/tmp/flux-xvnc.log 2>&1 &
echo $! > /tmp/flux-xvnc.pid
sleep 2
export DISPLAY=:1 XDG_RUNTIME_DIR="/tmp/runtime-$(id -u)"
mkdir -p "$XDG_RUNTIME_DIR"
chmod 700 "$XDG_RUNTIME_DIR"
dbus-launch --exit-with-session startxfce4 >/tmp/flux-session.log 2>&1 &
echo $! > /tmp/flux-session.pid
wait $!
kill "$(cat /tmp/flux-xvnc.pid)" 2>/dev/null
rm -f /tmp/flux-xvnc.pid /tmp/flux-session.pid
SESSION
chmod 755 /usr/local/bin/flux-session
echo "flux: guest ready"
