import java.io.*;
import java.net.*;

public class Client {
    private Socket socket;
    private ObjectOutputStream output;
    private ObjectInputStream input;

    public static final String BLUE = "\u001B[34m";
    public static final String GREEN = "\u001B[32m";
    public static final String BOLD = "\u001B[1m";
    public static final String RESET = "\u001B[0m";

    public Client(String address, int port) throws IOException {
        socket = new Socket(address, port);
        output = new ObjectOutputStream(socket.getOutputStream());
        input = new ObjectInputStream(socket.getInputStream());
        System.out.println("Connected to server at " + address + ":" + port);

        new Thread(this::listenForServerMessages).start();
    }

    private void listenForServerMessages() {
        try {
            while (true) {
                Object serverMessage = input.readObject();
                clearCurrentLine();
                System.out.println(BLUE + BOLD + "Server: " + RESET + serverMessage);
                System.out.print(GREEN + BOLD + "Client: " + RESET);

            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Lost connection to server: " + e.getMessage());
        }
    }

    public void sendCommand(String command, String key, String value) throws IOException {
        output.writeObject(command + ":" + key + (value != null ? ":" + value : ""));
    }


    public static void main(String[] args) throws IOException {
        Client client = new Client("localhost", 12345);
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String line;

        System.out.println(BOLD + "Available commands:\n-> ADD <key> <value>\n-> REMOVE <key>\n-> GET <key>" + RESET);
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split(" ");
            if (parts.length > 1) {
                String command = parts[0];
                String key = parts[1];
                String value = parts.length > 2 ? parts[2] : null;
                client.sendCommand(command, key, value);
                System.out.print(GREEN + BOLD + "Client: " + RESET);
            } else {
                System.out.println(BLUE + BOLD + "Server: " + RESET + "Invalid command. Please provide at least a command and a key.");
                System.out.print(GREEN + BOLD + "Client: " + RESET);
            }
        }
    }

    private void clearCurrentLine() {
        System.out.print("\r");
        System.out.print("\033[K");
    }
}
