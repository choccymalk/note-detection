import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class UDPServer {

    private static volatile boolean running = true;

    public static void main(String[] args) {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket(5800);
            byte[] buffer = new byte[1024];
            System.out.println("UDP server up and listening on port 5800");

            final DatagramSocket finalSocket = socket;  // Copy the socket into a final variable

            // Start thread for receiving messages
            Thread receiveThread = new Thread(() -> {
                try {
                    while (running) {
                        byte[] receiveBuffer = new byte[1024];
                        DatagramPacket request = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                        finalSocket.receive(request);
                        String message = new String(request.getData(), 0, request.getLength());
                        System.out.println(/* "Received: " + */message/*+ " from " + request.getAddress()*/);
                    }
                } catch (Exception e) {
                    if (running) {
                        e.printStackTrace();
                    }
                }
            });
            receiveThread.start();

            // Wait for the server to be terminated (press Enter to terminate)
            System.out.println("Press Enter to stop the server...");
            System.in.read();
            running = false;

            // Wait for the receive thread to finish
            receiveThread.join();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }
}
