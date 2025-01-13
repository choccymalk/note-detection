echo "deb https://packages.cloud.google.com/apt coral-edgetpu-stable main" | sudo tee /etc/apt/sources.list.d/coral-edgetpu.list
curl https://packages.cloud.google.com/apt/doc/apt-key.gpg | sudo apt-key add -
sudo apt-get update
sudo apt-get install gasket-dkms libedgetpu1-std
sudo sh -c "echo 'SUBSYSTEM==\"apex\", MODE=\"0660\", GROUP=\"apex\"' >> /etc/udev/rules.d/65-apex.rules"
sudo groupadd apex
sudo adduser $USER apex
read -p "To finish installation, your device needs to reboot. Reboot now? [y/N]: " response
  if [[ $response == [yY] || $response == [yY][eE][sS] ]]; then
    sudo reboot now
  fi