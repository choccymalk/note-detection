import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;

public class UDPServerRotationOnly {
    public static void main(String[] args) {
        final double IMAGE_WIDTH = 640.0; // Example image width in pixels
        final double IMAGE_HEIGHT = 480.0; // Example image height in pixels
        final double FOV_X = Math.toRadians(60.0); // Horizontal field of view in radians
        final double FOV_Y = Math.toRadians(45.0); // Vertical field of view in radians

        // Camera coordinates and rotation
        final double CAMERA_X = 8.0;
        final double CAMERA_Y = 10.5;
        final double CAMERA_Z = 24.0;
        final double CAMERA_ROTATION_DOWN = Math.toRadians(-35.0); // Camera rotated down by -35 degrees

        int port = 5806;
        byte[] buffer = new byte[65507];

        try (DatagramSocket socket = new DatagramSocket(port)) {
            System.out.println("UDP server up and listening on port " + port);

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String received = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                System.out.println("Received detections from client:");
                System.out.println(received);

                // Parse the received string into individual rows and columns
                String[] rows = received.split("\n");
                String header = rows[0];
                String[] columns = header.split(",");

                for (int i = 1; i < rows.length; i++) {
                    String[] values = rows[i].split(",");

                    if (values.length == columns.length) {
                        // Assuming the data format is known and consistent
                        double xmin = Double.parseDouble(values[0]);
                        double ymin = Double.parseDouble(values[1]);
                        double xmax = Double.parseDouble(values[2]);
                        double ymax = Double.parseDouble(values[3]);
                        double confidence = Double.parseDouble(values[4]);
                        int cls = Integer.parseInt(values[5]);
                        String name = values[6];

                        // Print the values
                        System.out.println("Row " + i + ":");
                        System.out.println("xmin: " + xmin);
                        System.out.println("ymin: " + ymin);
                        System.out.println("xmax: " + xmax);
                        System.out.println("ymax: " + ymax);
                        System.out.println("confidence: " + confidence);
                        System.out.println("class: " + cls);
                        System.out.println("name: " + name);

                        // Calculate the center of the bounding box
                        double centerX = (xmin + xmax) / 2.0;
                        double centerY = (ymin + ymax) / 2.0;

                        // Normalize the coordinates to [-1, 1] range
                        double normCenterX = (centerX / IMAGE_WIDTH) * 2 - 1;
                        double normCenterY = (centerY / IMAGE_HEIGHT) * 2 - 1;

                        // Calculate the direction vector from the camera to the object in 3D space
                        double directionX = Math.tan(normCenterX * FOV_X / 2.0);
                        double directionY = Math.tan(normCenterY * FOV_Y / 2.0);
                        double directionZ = 1.0; // Assuming the camera is looking straight along the Z-axis

                        // Apply the camera's downward rotation
                        double cosTheta = Math.cos(CAMERA_ROTATION_DOWN);
                        double sinTheta = Math.sin(CAMERA_ROTATION_DOWN);

                        // Rotate the direction vector by the camera's pitch (downward rotation)
                        double rotatedDirectionY = cosTheta * directionY - sinTheta * directionZ;
                        double rotatedDirectionZ = sinTheta * directionY + cosTheta * directionZ;

                        // Apply the camera's position
                        double objectX = CAMERA_X + directionX;
                        double objectY = CAMERA_Y + rotatedDirectionY;
                        double objectZ = CAMERA_Z + rotatedDirectionZ;

                        // Calculate the vector from the camera to the object
                        double relativeX = objectX - CAMERA_X;
                        double relativeY = objectY - CAMERA_Y;
                        double relativeZ = objectZ - CAMERA_Z;

                        // Calculate the magnitude of the relative vector
                        double magnitude = Math.sqrt(relativeX * relativeX + relativeY * relativeY + relativeZ * relativeZ);

                        // Normalize the relative vector
                        relativeX /= magnitude;
                        relativeY /= magnitude;
                        relativeZ /= magnitude;

                        // Calculate the rotation angle in radians (angle with the Z-axis)
                        double rotationAngle = Math.acos(relativeZ);

                        // Output the calculated rotation angle in radians
                        System.out.println("Calculated Rotation Angle (in radians): " + rotationAngle);

                        // Here you can send the rotation commands to the robot if needed
                    } else {
                        System.out.println("Row " + i + " has an incorrect number of columns.");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error! " + e);
        }
    }
}
