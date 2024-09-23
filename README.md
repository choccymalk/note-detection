how to put on robot\
on coprocessor:\
run these commands
```
git clone https://github.com/choccymalk/note-detection.git
```
```
cd note-detection
```
```
pip install -r requirements.txt
```
```
sudo ufw allow 5806
```
```
python UDPClient.py
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
on rio:\
look in the "roborio" directory 
