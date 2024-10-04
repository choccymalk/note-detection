#!/bin/bash
# based on photonvision's installation script
package_is_installed(){
    dpkg-query -W -f='${Status}' "$1" 2>/dev/null | grep -q "ok installed"
}

help() {
  echo "This script installs the note-detection utility."
  echo "It must be run as root."
  echo
  echo "Syntax: sudo ./install.sh [-h|m|n|q|u|p|s]"
  echo "  options:"
  echo "  -h        Display this help message."
  echo "  -m        Install and configure NetworkManager (Ubuntu only)."
  echo "  -q        Silent install, automatically accepts all defaults. For non-interactive use."
  echo "  -u        Upgrade to most recent version."
  echo "  -p        Do not install Python. Only use if you already have Python version 3.12 installed."
  echo "  -s        Build Python from source. Helpful if Launchpad Librarian is unreachable."
  echo
}

INSTALL_NETWORK_MANAGER="false"
INSTALLPYTHON="true"
BUILDPYTHONFROMSOURCE="false"

while getopts ":hmnqups" name; do
  case "$name" in
    h)
      help
      exit 0
      ;;
    m) INSTALL_NETWORK_MANAGER="true"
      ;;
    q) QUIET="true"
      ;;
    u) UPGRADE="true"
      ;;
    p) INSTALLPYTHON="false"
      ;;
    s) BUILDPYTHONFROMSOURCE="true"
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

if [ "$UPGRADE" = "true" ]; then
  cp /opt/note-detection/config.json $HOME/note-detection-config.json
  rm -rf /opt/note-detection
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
mkdir /opt/note-detection
echo "Update package list"
apt-get update

echo "Installing build-essential..."
apt-get install --yes build-essential
echo "build-essential installation complete."

echo "Installing wget..."
apt-get install --yes wget
echo "wget installation complete."

echo "Installing curl..."
apt-get install --yes curl
echo "curl installation complete."

echo "Installing avahi-daemon..."
apt-get install --yes avahi-daemon
echo "avahi-daemon installation complete."

echo "installing python3..."
apt-get install --yes python3-distutils
apt-get install --yes libssl-dev openssl
if [ "$INSTALLPYTHON" = "false" ]; then
  echo "Not installing python3."
else
  if [ "$BUILDPYTHONFROMSOURCE" = "false"]; then
    #mkdir $HOME/note-detection-temp
    apt-get install --yes python3.12
  else
    cd $HOME/note-detection-temp
    wget https://www.python.org/ftp/python/3.12.3/Python-3.12.3.tgz
    tar xzvf Python-3.12.3.tgz
    cd Python-3.12.3
    ./configure
    make
    make install
    cd $HOME/
    rm -rf $HOME/note-detection-temp
  fi
  apt-get install --yes python3-pip
  apt-get install --yes python3-pip-whl
  apt-get install --yes python3-venv
  #wget http://ftp.us.debian.org/debian/pool/main/p/python3-stdlib-extensions/python3-distutils_3.9.2-1_all.deb
  #dpkg -i python3-distutils_3.9.2-1_all.deb
  #if [ "$ARCH" = "aarch64" ]; then
  #    cd $HOME/note-detection-temp
  #    wget http://ftp.us.debian.org/debian/pool/main/p/python-pip/python-pip-whl_20.3.4-4+deb11u1_all.deb
  #    dpkg -i python-pip-whl_20.3.4-4+deb11u1_all.deb
  #    wget http://ftp.us.debian.org/debian/pool/main/p/python3.9/python3.9-venv_3.9.2-1_arm64.deb
  #    dpkg -i python3.9-venv_3.9.2-1_amd64.deb
  #    cd /opt/note-detection/
  #    echo "python3 installation complete."
  #  else
  #    cd $HOME/note-detection-temp
  #    wget http://ftp.us.debian.org/debian/pool/main/p/python-pip/python-pip-whl_20.3.4-4+deb11u1_all.deb
  #    dpkg -i python-pip-whl_20.3.4-4+deb11u1_all.deb
  #    wget http://ftp.us.debian.org/debian/pool/main/p/python3.9/python3.9-venv_3.9.2-1_amd64.deb
  #    dpkg -i python3.9-venv_3.9.2-1_amd64.deb
  #    cd /opt/note-detection/
  #    echo "python3 installation complete."
  #fi
fi

echo "Installing cpufrequtils..."
apt-get install --yes cpufrequtils
echo "cpufrequtils installation complete."

echo "Installing ufw..."
apt-get install --yes ufw
echo "ufw installation complete."

echo "Installing pypi2deb..."
apt-get install --yes pypi2deb
echo "pypi2deb installation complete."

echo "Creating firewall rules"
ufw allow 5000
ufw allow 5800
ufw allow 5801
ufw allow 5802
ufw allow 5803
ufw allow 5804
ufw allow 5805
ufw allow 5806
ufw allow 5807
ufw allow 5808
ufw allow 5809
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
mv /opt/note-detection/note-detection-main/* /opt/note-detection
if [ "$UPGRADE" = "true" ]; then
  rm /opt/note-detection/config.json
  cp $HOME/note-detection-config.json /opt/note-detection/config.json
  rm $HOME/note-detection-config.json
fi
cd $HOME/
cd /opt/note-detection/
echo "Downloaded latest stable release."
#http://launchpadlibrarian.net/592777863/python3.9-venv_3.9.12-1_amd64.deb

echo "Installing python packages..."
cd $HOME/note-detection-temp
apt-get install --yes python3-pip-whl
apt-get install --yes python3-setuptools-whl
#wget http://launchpadlibrarian.net/590522018/python3-distutils_3.9.10-2_all.deb
#dpkg -i python3-distutils_3.9.10-2_all.deb
#if [ "$ARCH" = "aarch64" ]; then
#  cd $HOME/note-detection-temp
#  wget http://launchpadlibrarian.net/592814498/python3.9-venv_3.9.12-1_arm64.deb
#  dpkg -i python3.9-venv_3.9.12-1_arm64.deb
#  cd /opt/note-detection/
#else
#  cd $HOME/note-detection-temp
#  wget http://launchpadlibrarian.net/592777863/python3.9-venv_3.9.12-1_amd64.deb
#  dpkg -i python3.9-venv_3.9.12-1_amd64.deb
#  cd /opt/note-detection/
#fi
cd /opt/note-detection/
su - $SUDO_USER -c "source /opt/note-detection/note-detection/bin/activate"
su - $SUDO_USER -c "python3 -m venv note-detection"
su - $SUDO_USER -c "/opt/note-detection/note-detection/bin/pip3 install -r /opt/note-detection/requirements.txt"
su - $SUDO_USER -c "deactivate"
#cat > /opt/note-detection/installpypackages.sh <<EOF
##!/opt/note-detection/bin/python3
#python3 -m pip install -r /opt/note-detection/requirements.txt # --break-system-packages
##apt-get install --yes python3-gitpython python3-matplotlib python3-numpy python3-opencv-python python3-pillow python3-psutil python3-PyYAML python3-requests python3-scipy python3-thop python3-torch python3-torchvision python3-tqdm python3-ultralytics python3-pandas python3-seaborn python3-setuptools python3-flask-socketio python3-socketio python3-flask python3-pygrabber python3-dill 
#EOF
#source /opt/note-detection/bin/activate
#/usr/bin/pip3 install -r /opt/note-detection/requirements.txt # --break-system-packages
#apt-get install --yes python3-gitpython python3-matplotlib python3-numpy python3-opencv-python python3-pillow python3-psutil python3-PyYAML python3-requests python3-scipy python3-thop python3-torch python3-torchvision python3-tqdm python3-ultralytics python3-pandas python3-seaborn python3-setuptools python3-flask-socketio python3-flask python3-pygrabber python3-dill python3-pickle 
#deactivate
#chmod 777 /opt/note-detection/installpypackages.sh
#/opt/note-detection/installpypackages.sh
echo "Finished installing packages."

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

#cat > /opt/note-detection/startup.sh <<EOF
##!/bin/bash
#/opt/note-detection/bin/python3 /opt/note-detection/UDPClient.py
#EOF
#chmod 777 /opt/note-detection/startup.sh

#/opt/note-detection/note-detection/bin/python3 /opt/note-detection/UDPClient.py
cat > /lib/systemd/system/note-detection.service <<EOF
[Unit]
Description=Service that runs the note-detection utility

[Service]
WorkingDirectory=/opt/note-detection/
# Run at "nice" -10, which is higher priority than standard
Nice=-10
# for non-uniform CPUs, like big.LITTLE, you want to select the big cores
# look up the right values for your CPU
# AllowedCPUs=4-7

ExecStart=/bin/bash -c 'source /opt/note-detection/note-detection/bin/activate && python3 /opt/note-detection/UDPClient.py'
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
chmod 777 /etc/systemd/system/note-detection.service
systemctl daemon-reload
systemctl enable note-detection.service

echo "Created systemd service."
rm -rf $HOME/note-detection-temp
echo "Installation successful!"
read -p "Would you like to restart your device now? [y/N]: " response
if [[ $response == [yY] || $response == [yY][eE][sS] ]]; then
  reboot now
fi
