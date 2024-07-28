# photonvision has this built in
### deprecated \
how to put on robot\
on coprocessor:\
run these commands\
git clone https://github.com/choccymalk/note-detection.git \
cd note-detection\
pip install -r requirements.txt\
sudo ufw allow 5806\
python UDPClient.py\
the data is sent and received as a string\
on rio:\
_i don't know_\
how to run it on startup ‼️ ‼️ (ubuntu only(i think)) \
crontab -e -u .the username that you use to login. \
once youre inside the crontab file, add this. \
@reboot python /home/username/note-detection/UDPClient.py 
