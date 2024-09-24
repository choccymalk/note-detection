from flask import Flask, render_template, request, redirect, url_for
import json
import os
from threading import Thread
#import pathlib
import cv2
import torch
import socket
from pygrabber.dshow_graph import FilterGraph  # Importing for camera detection
from flask_socketio import SocketIO, emit

app = Flask(__name__)
socketio = SocketIO(app)

CONFIG_FILE = 'config.json'

if not os.path.exists(CONFIG_FILE):
    default_config = {"setting1": "option1", "setting2": "value2", "camera_index": 0}
    with open(CONFIG_FILE, 'w') as file:
        json.dump(default_config, file, indent=4)

# Available options for setting1
SETTING1_OPTIONS = ["option1", "option2", "option3", "option4"]

# Get available camera devices
def get_available_cameras():
    devices = FilterGraph().get_input_devices()
    available_cameras = [(index, name) for index, name in enumerate(devices)]  # Create a list of tuples (index, name)
    return available_cameras  # Return list of tuples

# Load the configuration from the file
def load_config():
    with open(CONFIG_FILE, 'r') as file:
        return json.load(file)

# Save the updated configuration to the file
def save_config(data):
    with open(CONFIG_FILE, 'w') as file:
        json.dump(data, file, indent=4)

# Flask route to render the main configuration page
@app.route('/')
def home():
    config = load_config()  # Load current configuration
    available_cameras = get_available_cameras()  # Get the list of available cameras
    return render_template('config.html', config=config, setting1_options=SETTING1_OPTIONS, available_cameras=available_cameras)

# Flask route to handle configuration updates
@app.route('/update_config', methods=['POST'])
def update_config():
    setting1 = request.form.get('setting1')  # Get selected value from dropdown
    setting2 = request.form.get('setting2')  # Get value from text input
    camera_index = int(request.form.get('camera_index'))  # Get selected camera index

    # Update configuration file with new settings
    config = load_config()
    config['setting1'] = setting1
    config['setting2'] = setting2
    config['camera_index'] = camera_index
    save_config(config)

    return redirect(url_for('home'))

# Function to run YOLOv5 detection and streaming
def run_yolo_detection():
    #pathlib.WindowsPath = pathlib.PosixPath  # Fixing path issue for Windows/Posix compatibility

    # Load YOLOv5 model
    model = torch.hub.load('.', 'custom', path='note_detect_v2.pt', source='local')

    # Load configuration and get selected camera index
    config = load_config()
    camera_index = config.get('camera_index', 0)

    # Initialize video capture with the selected camera index
    video_capture = cv2.VideoCapture(camera_index)

    if not video_capture.isOpened():
        print(f"Error: Could not open video stream on camera index {camera_index}.")
        return

    # UDP server address
    server_ip = "192.168.1.114"
    server_port = 5806
    server_address = (server_ip, server_port)

    # Initialize UDP socket
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)

    try:
        while True:
            # Capture frame-by-frame
            ret, frame = video_capture.read()
            if not ret:
                print("Error: Failed to capture image.")
                break

            # Perform detection
            results = model(frame)

            # Convert detection results to pandas DataFrame
            detections_df = results.pandas().xyxy[0]

            # Convert frame to JPEG format
            _, jpeg = cv2.imencode('.jpg', frame)
            # Convert to bytes
            frame_bytes = jpeg.tobytes()

            # Send frame to the client via WebSocket
            socketio.emit('video_frame', {'data': frame_bytes})

            # Send detection results over UDP if DataFrame is not empty
            if not detections_df.empty:
                # Convert DataFrame to CSV string
                csv_string = detections_df.to_csv(index=False)

                # Ensure the string is within the UDP packet size limit (usually 65,507 bytes)
                if len(csv_string.encode()) <= 65507:
                    sock.sendto(csv_string.encode(), server_address)
                    print("Sent detections to server:")
                    print(detections_df)
                else:
                    print("DataFrame too large to send over UDP")

    except Exception as e:
        print(f"Error: {e}")

    finally:
        video_capture.release()
        sock.close()

# Start the Flask server
if __name__ == '__main__':
    yolo_thread = Thread(target=run_yolo_detection)
    yolo_thread.start()
    socketio.run(app, debug=True, use_reloader=False)
