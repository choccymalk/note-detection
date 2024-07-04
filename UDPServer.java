import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;

public class UDPServer {
    public static void main(String[] args) {
        int port = 5800;
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

                        // Here you can store or process the variables as needed
                    } else {
                        System.out.println("Row " + i + " has an incorrect number of columns.");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
