import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;

public class UDPServerDistance {
    // Camera parameters
    private static final double IMAGE_WIDTH = 640.0;
    private static final double IMAGE_HEIGHT = 480.0;
    private static final double FOV_X = Math.toRadians(60.0);
    private static final double FOV_Y = Math.toRadians(45.0);
    private static final double CAMERA_X = 8.0;
    private static final double CAMERA_Y = 10.5;
    private static final double CAMERA_Z = 24.0;
    private static final double CAMERA_ROTATION_DOWN = Math.toRadians(-35.0);

    // Known torus dimensions
    private static final double TORUS_MAJOR_RADIUS = 5.0;
    private static final double TORUS_MINOR_RADIUS = 1.0;

    public static void main(String[] args) {
        int port = 5806;
        byte[] buffer = new byte[65507];

        try (DatagramSocket socket = new DatagramSocket(port)) {
            System.out.println("UDP server listening on port " + port);

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String received = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                processDetections(received);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error: " + e);
        }
    }

    private static void processDetections(String detectionData) {
        try {
            String[] rows = detectionData.split("\n");
            String[] columns = rows[0].split(",");

            for (int i = 1; i < rows.length; i++) {
                String[] values = rows[i].split(",");
                if (values.length == columns.length) {
                    double xmin = Double.parseDouble(values[0]);
                    double ymin = Double.parseDouble(values[1]);
                    double xmax = Double.parseDouble(values[2]);
                    double ymax = Double.parseDouble(values[3]);
                    
                    double apparentWidth = xmax - xmin;
                    double apparentHeight = ymax - ymin;
                    double aspectRatio = apparentWidth / apparentHeight;
                    
                    // Calculate center point
                    double centerX = (xmin + xmax) / 2.0;
                    double centerY = (ymin + ymax) / 2.0;
                    
                    // Calculate viewing angle
                    double[] viewingAngles = calculateViewingAngles(centerX, centerY);
                    double viewingAngle = viewingAngles[0]; // horizontal angle
                    double verticalAngle = viewingAngles[1]; // vertical angle
                    
                    // Calculate distance considering viewing angle
                    TorusEstimate estimate = calculateTorusDistanceAndOrientation(
                        apparentWidth, apparentHeight, aspectRatio, viewingAngle, verticalAngle);
                    
                    // Calculate 3D position using corrected distance
                    double[] position = calculate3DPosition(centerX, centerY, estimate.distance);
                    
                    System.out.println("\nTorus Detection Analysis:");
                    System.out.println("Apparent Width: " + apparentWidth + " pixels");
                    System.out.println("Apparent Height: " + apparentHeight + " pixels");
                    System.out.println("Aspect Ratio: " + String.format("%.2f", aspectRatio));
                    System.out.println("Viewing Angle: " + String.format("%.2f", Math.toDegrees(viewingAngle)) + "°");
                    System.out.println("Vertical Angle: " + String.format("%.2f", Math.toDegrees(verticalAngle)) + "°");
                    System.out.println("Estimated Distance: " + String.format("%.2f", estimate.distance) + " units");
                    System.out.println("Estimated Orientation: " + String.format("%.2f", Math.toDegrees(estimate.orientation)) + "°");
                    System.out.println("Confidence: " + String.format("%.2f", estimate.confidence));
                    System.out.println("3D Position: X=" + String.format("%.2f", position[0]) + 
                                     ", Y=" + String.format("%.2f", position[1]) + 
                                     ", Z=" + String.format("%.2f", position[2]));
                }
            }
        } catch (Exception e) {
            System.out.println("Error processing detection: " + e.getMessage());
        }
    }

    private static class TorusEstimate {
        double distance;
        double orientation;
        double confidence;
        
        TorusEstimate(double distance, double orientation, double confidence) {
            this.distance = distance;
            this.orientation = orientation;
            this.confidence = confidence;
        }
    }

    private static double[] calculateViewingAngles(double centerX, double centerY) {
        // Convert to normalized coordinates (-1 to 1)
        double normX = (centerX / IMAGE_WIDTH) * 2 - 1;
        double normY = (centerY / IMAGE_HEIGHT) * 2 - 1;
        
        // Calculate horizontal angle
        double horizontalAngle = Math.atan(normX * Math.tan(FOV_X / 2.0));
        
        // Calculate vertical angle including camera rotation
        double baseVerticalAngle = Math.atan(normY * Math.tan(FOV_Y / 2.0));
        double verticalAngle = baseVerticalAngle + CAMERA_ROTATION_DOWN;
        
        return new double[]{horizontalAngle, verticalAngle};
    }

    private static TorusEstimate calculateTorusDistanceAndOrientation(
            double apparentWidth, double apparentHeight, double aspectRatio,
            double viewingAngle, double verticalAngle) {
        
        // Calculate the expected aspect ratio at different orientations
        double minAspectRatio = 2 * TORUS_MINOR_RADIUS / (2 * (TORUS_MAJOR_RADIUS + TORUS_MINOR_RADIUS));
        double maxAspectRatio = 1.0; // When viewed head-on
        
        // Normalize the observed aspect ratio between expected min and max
        double normalizedRatio = (aspectRatio - minAspectRatio) / (maxAspectRatio - minAspectRatio);
        normalizedRatio = Math.max(0.0, Math.min(1.0, normalizedRatio));
        
        // Estimate orientation angle from aspect ratio
        double orientationAngle = Math.acos(normalizedRatio);
        
        // Calculate apparent size considering orientation
        double effectiveDiameter = 2 * TORUS_MAJOR_RADIUS * Math.cos(orientationAngle) +
                                 2 * TORUS_MINOR_RADIUS * Math.sin(orientationAngle);
        
        // Calculate base distance using apparent width
        double widthAngle = (apparentWidth / IMAGE_WIDTH) * FOV_X;
        double baseDistance = effectiveDiameter / (2 * Math.tan(widthAngle / 2));
        
        // Correct distance for viewing angle
        double correctedDistance = baseDistance / Math.cos(viewingAngle);
        
        // Calculate confidence based on how well the measurements match expected values
        double expectedHeight = 2 * (TORUS_MAJOR_RADIUS + TORUS_MINOR_RADIUS) * Math.sin(orientationAngle) +
                              2 * TORUS_MINOR_RADIUS * Math.cos(orientationAngle);
        double heightAngle = (apparentHeight / IMAGE_HEIGHT) * FOV_Y;
        double heightBasedDistance = expectedHeight / (2 * Math.tan(heightAngle / 2));
        
        // Compare the two distance estimates for confidence
        double confidence = 1.0 - Math.min(1.0, 
            Math.abs(correctedDistance - heightBasedDistance) / correctedDistance);
        
        return new TorusEstimate(correctedDistance, orientationAngle, confidence);
    }

    private static double[] calculate3DPosition(double centerX, double centerY, double distance) {
        // Normalize coordinates
        double normCenterX = (centerX / IMAGE_WIDTH) * 2 - 1;
        double normCenterY = (centerY / IMAGE_HEIGHT) * 2 - 1;

        // Calculate direction vector
        double directionX = Math.tan(normCenterX * FOV_X / 2.0);
        double directionY = Math.tan(normCenterY * FOV_Y / 2.0);
        double directionZ = 1.0;

        // Apply camera rotation
        double cosTheta = Math.cos(CAMERA_ROTATION_DOWN);
        double sinTheta = Math.sin(CAMERA_ROTATION_DOWN);
        double rotatedDirectionY = cosTheta * directionY - sinTheta * directionZ;
        double rotatedDirectionZ = sinTheta * directionY + cosTheta * directionZ;

        // Normalize direction vector
        double magnitude = Math.sqrt(directionX * directionX + 
                                   rotatedDirectionY * rotatedDirectionY + 
                                   rotatedDirectionZ * rotatedDirectionZ);
        
        // Calculate position
        double[] position = new double[3];
        position[0] = CAMERA_X + (directionX / magnitude) * distance;
        position[1] = CAMERA_Y + (rotatedDirectionY / magnitude) * distance;
        position[2] = CAMERA_Z + (rotatedDirectionZ / magnitude) * distance;

        return position;
    }
}