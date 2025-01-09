from flask import Flask, render_template, request, redirect, url_for
import json
import os
from threading import Thread
import cv2
import numpy as np
from tflite_runtime.interpreter import Interpreter
from tflite_runtime.interpreter import load_delegate
from flask_socketio import SocketIO, emit
import socket

app = Flask(__name__)
socketio = SocketIO(app)

CONFIG_FILE = 'config.json'

if not os.path.exists(CONFIG_FILE):
    default_config = {"ipOfRio": "10.0.0.2", "camera_index": 0}
    with open(CONFIG_FILE, 'w') as file:
        json.dump(default_config, file, indent=4)

SETTING1_OPTIONS = ["option1", "option2", "option3", "option4"]

# Model settings
MODEL_PATH = 'note_detect_v2-fp16_edgetpu.tflite'
LABELS_PATH = 'labels.txt'
INPUT_SIZE = 320  # Must match your model's input size

def load_labels(path):
    with open(path, 'r') as f:
        return {i: line.strip() for i, line in enumerate(f.readlines())}

def get_available_cameras():
    """Get available camera devices on Linux."""
    available_cameras = []
    # Check the first 10 camera device nodes
    for i in range(10):
        cap = cv2.VideoCapture(i)
        if cap.isOpened():
            # Test reading a frame
            ret, _ = cap.read()
            if ret:
                # On Linux, we can try to get the camera name from v4l2
                # but for simplicity, we'll just use indices
                available_cameras.append((i, f"Camera {i}"))
            cap.release()
    return available_cameras


def load_config():
    with open(CONFIG_FILE, 'r') as file:
        return json.load(file)

def save_config(data):
    with open(CONFIG_FILE, 'w') as file:
        json.dump(data, file, indent=4)

@app.route('/')
def home():
    config = load_config()
    available_cameras = get_available_cameras()
    return render_template('config.html', config=config, 
                         setting1_options=SETTING1_OPTIONS, 
                         available_cameras=available_cameras)

@app.route('/update_config', methods=['POST'])
def update_config():
    ipOfRio = request.form.get('ipOfRio')
    camera_index = int(request.form.get('camera_index'))
    config = load_config()
    config['ipOfRio'] = ipOfRio
    config['camera_index'] = camera_index
    save_config(config)
    return redirect(url_for('home'))

def run_detection():
    # Initialize Edge TPU with delegate
    possible_paths = [
            '/usr/lib/libedgetpu.so.1.0',
            '/usr/lib/aarch64-linux-gnu/libedgetpu.so.1.0',  # Common path on ARM64
            'libedgetpu.so.1.0'
        ]
        
    delegate = None
    for path in possible_paths:
        try:
            print(f"Attempting to load Edge TPU delegate from: {path}")
            delegate = load_delegate(path)
            print(f"Successfully loaded delegate from {path}")
            break
        except ValueError as e:
            print(f"Failed to load from {path}: {e}")
            continue
                
    if delegate is None:
        raise ValueError("Could not load EdgeTPU delegate from any known path")
    
    interpreter = Interpreter(
        model_path=MODEL_PATH,
        experimental_delegates=[delegate]
    )
    interpreter.allocate_tensors()

    # Get model details
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    
    # Load labels
    labels = load_labels(LABELS_PATH)

    # Load configuration
    config = load_config()
    camera_index = config.get('camera_index', 0)
    rioIp = config.get('ipOfRio', '')

    # Initialize video capture
    video_capture = cv2.VideoCapture(camera_index)
    if not video_capture.isOpened():
        print(f"Error: Could not open video stream on camera index {camera_index}.")
        return

    # UDP setup
    server_address = (rioIp, 5806)
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)

    try:
        while True:
            ret, frame = video_capture.read()
            if not ret:
                print("Error: Failed to capture image.")
                break

            # Prepare input image
            input_shape = input_details[0]['shape']
            input_data = cv2.resize(frame, (input_shape[1], input_shape[2]))
            input_data = np.expand_dims(input_data, axis=0)
            input_data = (np.float32(input_data) - 127.5) / 127.5

            # Run inference
            interpreter.set_tensor(input_details[0]['index'], input_data)
            interpreter.invoke()

            # Get detection results
            boxes = interpreter.get_tensor(output_details[0]['index'])[0]
            classes = interpreter.get_tensor(output_details[1]['index'])[0]
            scores = interpreter.get_tensor(output_details[2]['index'])[0]

            # Create detection results in similar format to original
            detections = []
            for i in range(len(scores)):
                if scores[i] > 0.5:  # Detection threshold
                    ymin, xmin, ymax, xmax = boxes[i]
                    detection = {
                        'xmin': xmin * frame.shape[1],
                        'ymin': ymin * frame.shape[0],
                        'xmax': xmax * frame.shape[1],
                        'ymax': ymax * frame.shape[0],
                        'confidence': float(scores[i]),
                        'class': labels[int(classes[i])]
                    }
                    detections.append(detection)

            # Convert frame for streaming
            _, jpeg = cv2.imencode('.jpg', frame)
            frame_bytes = jpeg.tobytes()
            socketio.emit('video_frame', {'data': frame_bytes})

            # Send detections over UDP
            if detections:
                detection_str = json.dumps(detections)
                if len(detection_str.encode()) <= 65507:
                    sock.sendto(detection_str.encode(), server_address)
                    print("Sent detections:", detections)
                else:
                    print("Detection data too large for UDP packet")

    except Exception as e:
        print(f"Error: {e}")
    finally:
        video_capture.release()
        sock.close()

if __name__ == '__main__':
    detection_thread = Thread(target=run_detection)
    detection_thread.start()
    socketio.run(app, debug=True, use_reloader=False)