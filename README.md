## Installation Guide
### On coprocessor:
#### Using the Installation Script (Do Not Use, Broken)
```
wget https://raw.githubusercontent.com/choccymalk/note-detection/refs/heads/main/install.sh
```
```
sudo chmod +x ./install.sh
```
```
sudo ./install.sh
```
If this fails, try using "dos2unix" on the install script.
```
sudo apt-get install --yes dos2unix
```
```
sudo dos2unix install.sh
```
#### Manual Installation
```
git clone https://github.com/choccymalk/note-detection.git
```
```
cd note-detection
```
```
pip3 install -r requirements.txt
```
```
sudo ufw allow 5806
```
```
python3 UDPClient.py
```
the data is sent and received as a pandas dataframe\
how to run it on startup (ubuntu only(i think)) 
```
crontab -e -u username 
```
once youre inside the crontab file, add this
```
@reboot python /home/username/note-detection/UDPClient.py 
```
### On roborio:
look in the "roborio" directory 
