import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
// IN2011 Computer Networks
// Coursework 2024/2025
//
// Submission by
//  MOHAMMAD FAISAL
//  230065855
//  mohammad.faisal.3@city.ac.uk


// DO NOT EDIT starts
// This gives the interface that your code must implement.
// These descriptions are intended to help you understand how the interface
// will be used. See the RFC for how the protocol works.

interface NodeInterface {
    /* These methods configure your node.
     * They must both be called once after the node has been created but
     * before it is used. */
    //Set the name of the node.
    public void setNodeName(String nodeName) throws Exception;
    //Open a UDP Port for sending and receiving message.
    public void openPort(int portNumber) throws Exception;
    /*
     * These methods query and change how the network is used.
     */

    // Handle all incoming messages.
    // If you wait for more than delay miliseconds and
    // there are no new incoming messages return.
    // If delay is zero then wait for an unlimited amount of time.
    public void handleIncomingMessages(int delay) throws Exception;

    // Determines if a node can be contacted and is responding correctly.
    // Handles any messages that have arrived.
    public boolean isActive(String nodeName) throws Exception;
    // You need to keep a stack of nodes that are used to relay messages.
    // The base of the stack is the first node to be used as a relay.
    // The first node must relay to the second node and so on.

    // Adds a node name to a stack of nodes used to relay all future messages.
    public void pushRelay(String nodeName) throws Exception;
    // Pops the top entry from the stack of nodes used for relaying.
    // No effect if the stack is empty
    public void popRelay() throws Exception;
    /*
     * These methods provide access to the basic functionality of
     * CRN-25 network.
     */

    // Checks if there is an entry in the network with the given key.
    // Handles any messages that have arrived.
    public boolean exists(String key) throws Exception;

    // Reads the entry stored in the network for key.
    // If there is a value, return it.
    // If there isn't a value, return null.
    // Handles any messages that have arrived.
    public String read(String key) throws Exception;

    // Sets key to be value.
    // Returns true if it worked, false if it didn't.
    // Handles any messages that have arrived.
    public boolean write(String key, String value) throws Exception;

    // If key is set to currentValue change it to newValue.
    // Returns true if it worked, false if it didn't.
    // Handles any messages that have arrived.
    public boolean CAS(String key, String currentValue, String newValue) throws Exception;
}

// DO NOT EDIT ends

// Complete this!
public class Node implements NodeInterface {
    private String nodeName;
    private DatagramSocket socket;
    private Map<String, String> keyValueStore = new HashMap<>();
    private Stack<String> relayStack = new Stack<>();

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public void openPort(int portNumber) throws Exception {
        socket = new DatagramSocket(portNumber);
    }

    public void handleIncomingMessages(int delay) throws Exception {
        socket.setSoTimeout(delay);
        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        try {
            socket.receive(packet);
            String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
            processMessage(message, packet.getAddress(), packet.getPort());
        } catch (SocketTimeoutException e) {
            // Timeout reached, return control
        }
    }

    public boolean isActive(String nodeName) throws Exception {
        sendMessage("G", nodeName);
        return true;
    }

    public void pushRelay(String nodeName) {
        relayStack.push(nodeName);
    }

    public void popRelay() {
        if (!relayStack.isEmpty()) {
            relayStack.pop();
        }
    }

    public boolean exists(String key) throws Exception {
        return keyValueStore.containsKey(key);
    }

    public String read(String key) throws Exception {
        return keyValueStore.getOrDefault(key, null);
    }

    public boolean write(String key, String value) throws Exception {
        keyValueStore.put(key, value);
        return true;
    }

    public boolean CAS(String key, String currentValue, String newValue) throws Exception {
        if (keyValueStore.containsKey(key) && keyValueStore.get(key).equals(currentValue)) {
            keyValueStore.put(key, newValue);
            return true;
        }
        return false;
    }

    private void processMessage(String message, InetAddress senderAddress, int senderPort) throws IOException {
        if (message.startsWith("G")) {
            sendResponse("H " + nodeName, senderAddress, senderPort);
        } else if (message.startsWith("E")) {
            handleKeyExistenceRequest(message, senderAddress, senderPort);
        } else if (message.startsWith("R")) {
            handleReadRequest(message, senderAddress, senderPort);
        } else if (message.startsWith("W")) {
            handleWriteRequest(message, senderAddress, senderPort);
        } else if (message.startsWith("C")) {
            handleCompareAndSwapRequest(message, senderAddress, senderPort);
        }
    }

    private void handleKeyExistenceRequest(String message, InetAddress senderAddress, int senderPort) {
        String key = message.substring(2);
        String response = keyValueStore.containsKey(key) ? "F Y" : "F N";
        sendResponse(response, senderAddress, senderPort);
    }

    private void handleReadRequest(String message, InetAddress senderAddress, int senderPort) {
        String key = message.substring(2);
        String value = keyValueStore.getOrDefault(key, "");
        String response = keyValueStore.containsKey(key) ? "S Y " + value : "S N ";
        sendResponse(response, senderAddress, senderPort);
    }

    private void handleWriteRequest(String message, InetAddress senderAddress, int senderPort) {
        String[] parts = message.split(" ", 3);
        if (parts.length < 3) return;
        keyValueStore.put(parts[1], parts[2]);
        sendResponse("X A", senderAddress, senderPort);
    }

    private void handleCompareAndSwapRequest(String message, InetAddress senderAddress, int senderPort) {
        String[] parts = message.split(" ", 4);
        if (parts.length < 4) return;
        String key = parts[1], oldValue = parts[2], newValue = parts[3];
        if (keyValueStore.containsKey(key) && keyValueStore.get(key).equals(oldValue)) {
            keyValueStore.put(key, newValue);
            sendResponse("D R", senderAddress, senderPort);
        } else {
            sendResponse("D N", senderAddress, senderPort);
        }
    }
    private boolean sendMessage(String message, String nodeName) {
        try {
            InetAddress address = InetAddress.getByName("127.0.0.1"); // Defaulting to local address
            byte[] messageData = message.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(messageData, messageData.length, address, 20110);
            socket.send(packet);
            return true;
        } catch (IOException e) {
            System.err.println("Error sending message: " + e.getMessage());
            return false;
        }
    }

    private void sendResponse(String response, InetAddress recipient, int port) {
        try {
            byte[] responseData = response.getBytes(StandardCharsets.UTF_8);
            DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length, recipient, port);
            socket.send(responsePacket);
        } catch (IOException e) {
            System.err.println("Error sending response: " + e.getMessage());
        }
    }
}

