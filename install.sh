#!/bin/bash
# borrowed from photonvision
# i love photonvision
package_is_installed(){
    dpkg-query -W -f='${Status}' "$1" 2>/dev/null | grep -q "ok installed"
}

help() {
  echo "This script installs the note-detection utility."
  echo "It must be run as root."
  echo
  echo "Syntax: sudo ./install.sh [-h|m|n|q]"
  echo "  options:"
  echo "  -h        Display this help message."
  echo "  -m        Install and configure NetworkManager (Ubuntu only)."
  echo "  -q        Silent install, automatically accepts all defaults. For non-interactive use."
  echo
}

INSTALL_NETWORK_MANAGER="false"

while getopts ":hmnq" name; do
  case "$name" in
    h)
      help
      exit 0
      ;;
    m) INSTALL_NETWORK_MANAGER="true"
      ;;
    q) QUIET="true"
      ;;
    \?)
      echo "Error: Invalid option -- '$OPTARG'"
      echo "Try './install.sh -h' for more information."
      exit 1
  esac
done

shift $(($OPTIND -1))

if [ "$(id -u)" != "0" ]; then
   echo "This script must be run as root" 1>&2
   exit 1
fi

ARCH=$(uname -m)
ARCH_NAME=""
if [ "$ARCH" = "aarch64" ]; then
  ARCH_NAME="linuxarm64"
elif [ "$ARCH" = "armv7l" ]; then
  echo "ARM32 is not supported. Exiting."
  exit 1
elif [ "$ARCH" = "x86_64" ]; then
  ARCH_NAME="linuxx64"
else
  if [ "$#" -ne 1 ]; then
      echo "Can't determine current arch; please provide it (one of):"
      echo ""
      echo "- linuxarm64 (64-bit Linux ARM)"
      echo "- linuxx64   (64-bit Linux)"
      exit 1
  else
    echo "Can't detect arch (got $ARCH) -- using user-provided $1"
    ARCH_NAME=$1
  fi
fi

echo "This is the installation script for the note detection utility."
echo "Installing for platform $ARCH_NAME"

DISTRO=$(lsb_release -is)
if [[ "$DISTRO" = "Ubuntu" && "$INSTALL_NETWORK_MANAGER" != "true" && -z "$QUIET" && -z "$DISABLE_NETWORKING" ]]; then
  echo ""
  echo "This uses NetworkManager to control networking on your device."
  read -p "Do you want this script to install and configure NetworkManager? [y/N]: " response
  if [[ $response == [yY] || $response == [yY][eE][sS] ]]; then
    INSTALL_NETWORK_MANAGER="true"
  fi
fi

echo "Update package list"
apt-get update

echo "Installing curl..."
apt-get install --yes curl
echo "curl installation complete."

echo "Installing avahi-daemon..."
apt-get install --yes avahi-daemon
echo "avahi-daemon installation complete."

echo "installing python3..."
apt-get install --yes python3
echo "python3 installation complete."

echo "installing pip3..."
apt-get install --yes pip3
echo "pip3 installation complete."

echo "Installing cpufrequtils..."
apt-get install --yes cpufrequtils
echo "cpufrequtils installation complete."

echo "Installing ufw..."
apt-get install --yes ufw
echo "ufw installation complete."

echo "Creating firewall rules"
ufw allow 5806
echo "Updated firewall rules"

echo "Setting cpufrequtils to performance mode"
if [ -f /etc/default/cpufrequtils ]; then
    sed -i -e 's/^#\?GOVERNOR=.*$/GOVERNOR=performance/' /etc/default/cpufrequtils
else
    echo 'GOVERNOR=performance' > /etc/default/cpufrequtils
fi

echo "Installing libatomic"
apt-get install --yes libatomic1
echo "libatomic installation complete."

if [[ "$INSTALL_NETWORK_MANAGER" == "true" ]]; then
  echo "Installing network-manager..."
  apt-get install --yes network-manager net-tools
  systemctl disable systemd-networkd-wait-online.service
  cat > /etc/netplan/00-default-nm-renderer.yaml <<EOF
network:
  renderer: NetworkManager
EOF
  echo "network-manager installation complete."
fi

echo "Installing the JRE..."
if ! package_is_installed openjdk-17-jre-headless
then
   apt-get update
   apt-get install --yes openjdk-17-jre-headless
fi
echo "JRE installation complete."

echo "Installing additional math packages"
if [[ "$DISTRO" = "Ubuntu" && -z $(apt-cache search libcholmod3) ]]; then
  echo "Adding jammy to list of apt sources"
  add-apt-repository -y -S 'deb http://ports.ubuntu.com/ubuntu-ports jammy main universe'
fi
apt-get install --yes libcholmod3 liblapack3 libsuitesparseconfig5

echo "Installing v4l-utils..."
apt-get install --yes v4l-utils
echo "v4l-utils installation complete."

echo "Installing sqlite3"
apt-get install --yes sqlite3

echo "Downloading latest stable release..."
mkdir $HOME/note-detection-temp
cd $HOME/note-detection-temp
apt-get install --yes unzip
wget https://github.com/choccymalk/note-detection/archive/refs/heads/main.zip
mkdir -p /opt/note-detection
unzip main.zip -d /opt/note-detection
/usr/bin/pip3 install -r /opt/note-detection/requirements.txt -t /opt/note-detection/
rm -rf $HOME/note-detection-temp
echo "Downloaded latest stable release."

echo "Creating systemd service..."

if systemctl --quiet is-active note-detection; then
  echo "The service is already running! Stopping it."
  systemctl stop note-detection
  systemctl disable note-detection
  rm /lib/systemd/system/note-detection.service
  rm /etc/systemd/system/note-detection.service
  systemctl daemon-reload
  systemctl reset-failed
fi

cat > /lib/systemd/system/note-detection.service <<EOF
[Unit]
Description=Service that runs the note-detection utility

[Service]
WorkingDirectory=/opt/note-detection
# Run at "nice" -10, which is higher priority than standard
Nice=-10
# for non-uniform CPUs, like big.LITTLE, you want to select the big cores
# look up the right values for your CPU
# AllowedCPUs=4-7

ExecStart=/usr/bin/python3 /opt/note-detection/UDPClient.py
ExecStop=/bin/systemctl kill note-detection
Type=simple
Restart=on-failure
RestartSec=1

[Install]
WantedBy=multi-user.target
EOF

if [[ -n $(cat /proc/cpuinfo | grep "RK3588") ]]; then
  echo "This has a Rockchip RK3588, enabling all cores"
  sed -i 's/# AllowedCPUs=4-7/AllowedCPUs=0-7/g' /lib/systemd/system/note-detection.service
fi

cp /lib/systemd/system/note-detection.service /etc/systemd/system/note-detection.service
chmod 644 /etc/systemd/system/note-detection.service
systemctl daemon-reload
systemctl enable note-detection.service

echo "Created systemd service."

echo "Installation successful!"
