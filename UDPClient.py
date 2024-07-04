from pathlib import Path
import pathlib
temp = pathlib.PosixPath
pathlib.WindowsPath = pathlib.PosixPath
import cv2
import torch
import socket

# Load YOLOv5 model
model = torch.hub.load('.', 'custom', path='note_detect.pt', source='local')

# Initialize video capture (0 is the default camera)
video_capture = cv2.VideoCapture(0)

if not video_capture.isOpened():
    print("Error: Could not open video stream.")
    exit()

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

        # Send results only if DataFrame is not empty
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

        # Display the resulting frame with bounding boxes and labels

        # Press 'q' to exit the loop
        if cv2.waitKey(1) & 0xFF == ord('q'):
            break

except KeyboardInterrupt:
    print("Interrupted by user.")

finally:
    # When everything is done, release the capture and close the socket
    video_capture.release()
    sock.close()