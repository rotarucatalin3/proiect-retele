import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    private ServerSocket serverSocket;
    private final ConcurrentHashMap<String, String> objectMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ObjectOutputStream> keyClientMap = new ConcurrentHashMap<>();
    private final List<ObjectOutputStream> clientStreams = Collections.synchronizedList(new ArrayList<>());

    public Server(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("Server running on port: " + port);
    }

    public void start() {
        while (true) {
            try {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getRemoteSocketAddress());
                ObjectOutputStream output = new ObjectOutputStream(clientSocket.getOutputStream());
                ObjectInputStream input = new ObjectInputStream(clientSocket.getInputStream());
                clientStreams.add(output);


                output.writeObject("List of available keys: " + new ArrayList<>(objectMap.keySet()));

                Thread clientThread = new Thread(() -> handleClient(clientSocket, input, output));
                clientThread.start();
            } catch (IOException e) {
                System.out.println("Error accepting client connection: " + e.getMessage());
            }
        }
    }

    private void handleClient(Socket clientSocket, ObjectInputStream input, ObjectOutputStream output) {
        try {
            while (true) {
                Object message = input.readObject();
                if (message instanceof String) {
                    String[] parts = ((String) message).split(":", 3);
                    String command = parts[0];
                    String key = parts.length > 1 ? parts[1] : null;
                    String value = parts.length > 2 ? parts[2] : null;


                    switch (command.toLowerCase()) {
                        case "add" -> {
                            if (key != null && value != null) {
                                addObject(key, value, output);
                            } else {
                                output.writeObject("Error: ADD command requires both key and value.");
                            }
                        }
                        case "remove" -> {
                            if (key != null) {
                                removeObject(key, output);
                            } else {
                                output.writeObject("Error: REMOVE command requires a key.");
                            }
                        }
                        case "get" -> {
                            if (key != null) {
                                getObject(key, output);
                            } else {
                                output.writeObject("Error: GET command requires a key.");
                            }
                        }
                        case "request" -> {
                            if(key != null && ("approve".equalsIgnoreCase(value) || "deny".equalsIgnoreCase(value))){
                                processResponse(key, value);
                            } else{
                                processResponse(key, "deny");
                                output.writeObject("Error: REQUEST command requires both key and response.");
                            }
                        }
                        default ->
                                output.writeObject("Error: Unknown command. Valid commands are ADD, REMOVE, SEARCH.");
                    }
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Client disconnected: " + e.getMessage());
            clientStreams.remove(output);
            try {
                clientSocket.close();
            } catch (IOException ex) {
                System.out.println("Error closing client socket: " + ex.getMessage());
            }
        }
    }

    public void getObject(String key, ObjectOutputStream output) throws IOException {
        if (objectMap.containsKey(key)) {
            if (keyClientMap.get(key) == output) {
                String value = objectMap.get(key);
                output.writeObject("Object found: " + value);
            } else {
                ObjectOutputStream owner = keyClientMap.get(key);
                keyClientMap.put(key + "_requester", output);
                owner.writeObject("Client requests to view object with key: " + key + "\nApprove? [REQUEST <key> <approve/deny>] ");
            }
        } else {
            output.writeObject("Key not found.");
        }
    }

    private void processResponse(String key, String value) throws IOException {
        ObjectOutputStream requester = keyClientMap.get(key + "_requester");
        keyClientMap.remove(key + "_requester");

        if (requester != null) {
            if ("approve".equalsIgnoreCase(value)) {
                String objectValue = objectMap.get(key);
                requester.writeObject("Approval received. Object value: " + objectValue);
            } else {
                requester.writeObject("Approval denied.");
            }
        }
    }

    public void handleApproval(String key, String response, ObjectOutputStream output) throws IOException {
        ObjectOutputStream requester = keyClientMap.get(key + "_requester");
        if ("yes".equals(response)) {
            String value = objectMap.get(key);
            requester.writeObject("Request approved. Object value: " + value);
        } else {
            requester.writeObject("Request denied.");
        }
        keyClientMap.remove(key + "_requester");  // Curățăm referința la solicitant
    }


    public synchronized void addObject(String key, String value, ObjectOutputStream output) throws IOException {
        String existingValue = objectMap.putIfAbsent(key, value);
        if (existingValue == null) {
            keyClientMap.put(key, output);
            notifyAllClients("New object added: Key=" + key);
        } else {
            output.writeObject("Error: Key already exists.");
        }
    }


    public synchronized void removeObject(String key, ObjectOutputStream output) throws IOException {
        if (objectMap.containsKey(key) && keyClientMap.get(key) == output) {
            objectMap.remove(key);
            notifyAllClients("Object removed: Key=" + key);
        } else {
            output.writeObject("Error: Key does not exist or you are not the owner of the key.");
        }
    }

    private void notifyAllClients(String message) {
        Iterator<ObjectOutputStream> it = clientStreams.iterator();
        while (it.hasNext()) {
            ObjectOutputStream stream = it.next();
            try {
                stream.writeObject(message);
            } catch (IOException e) {
                System.out.println("Error notifying client: " + e.getMessage());
                it.remove();
            }
        }
    }


    public static void main(String[] args) throws IOException {
        int port = 12345;
        Server server = new Server(port);
        server.start();
    }
}
