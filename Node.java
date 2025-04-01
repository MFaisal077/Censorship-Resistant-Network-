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
    private Map<String, InetSocketAddress> peerMap = new HashMap<>();
    private Map<String, String> vault = new HashMap<>();
    private Map<String, String> cacheNearest = new HashMap<>();
    private String fetchCache = null;
    private DatagramSocket socket;
    Stack<String> relayStack = new Stack<>();

    private String strip(String raw) {
        return raw.strip().replaceAll("[\\r\\n]+", "\n");
    }

    private String makeID() {
        return UUID.randomUUID().toString().substring(0, 4);
    }

    private void reply(InetAddress address, int port, String message) {
        try {
            byte[] data = message.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
            socket.send(packet);
        } catch (Exception e) {
            System.err.println("Reply error: " + e.getMessage());
        }
    }

    @Override
    public void handleIncomingMessages(int delay) throws Exception {
        socket.setSoTimeout(delay);
        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        try {
            socket.receive(packet);
            String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
            String[] parts = message.split(" ", 3);
            if (parts.length < 2) return;
            String transactionId = parts[0];
            String messageType = parts[1];

            switch (messageType) {
                case "G":
                    reply(packet.getAddress(), packet.getPort(), transactionId + " H 0 N:" + nodeName + " ");
                    break;
                case "N":
                    reply(packet.getAddress(), packet.getPort(), transactionId + " O 0 N:" + nodeName + " 0 " + packet.getAddress().getHostAddress() + ":" + packet.getPort() + " ");
                    break;
                case "O":
                    cacheNearest.put(transactionId, message);
                    break;
                case "R":
                    String[] readParts = message.split(" ", 4);
                    if (readParts.length >= 4) {
                        String key = readParts[3].trim();
                        if (vault.containsKey(key)) {
                            String value = vault.get(key);
                            reply(packet.getAddress(), packet.getPort(), transactionId + " S Y 0 " + value + " ");
                        } else {
                            reply(packet.getAddress(), packet.getPort(), transactionId + " S N 0 ");
                        }
                    }
                    break;
                case "W":
                    String[] writeParts = message.split(" ", 5);
                    if (writeParts.length >= 5) {
                        String key = writeParts[3];
                        String value = writeParts[4].trim();
                        vault.put(key, value);
                        reply(packet.getAddress(), packet.getPort(), transactionId + " X A ");
                    }
                    break;
                case "C":
                    String[] casParts = message.split(" ", 6);
                    if (casParts.length >= 6) {
                        String key = casParts[3];
                        String oldVal = casParts[4];
                        String newVal = casParts[5].trim();
                        if (vault.containsKey(key) && vault.get(key).equals(oldVal)) {
                            vault.put(key, newVal);
                            reply(packet.getAddress(), packet.getPort(), transactionId + " D R ");
                        } else {
                            reply(packet.getAddress(), packet.getPort(), transactionId + " D N ");
                        }
                    }
                    break;
                case "E":
                    String[] existsParts = message.split(" ", 4);
                    if (existsParts.length >= 4) {
                        String key = existsParts[3].trim();
                        if (vault.containsKey(key)) {
                            reply(packet.getAddress(), packet.getPort(), transactionId + " F Y ");
                        } else {
                            reply(packet.getAddress(), packet.getPort(), transactionId + " F N ");
                        }
                    }
                    break;
            }
        } catch (SocketTimeoutException e) {
            // Timeout reached, return control
        }
    }

    @Override
    public void setNodeName(String nodeName) throws Exception {
        this.nodeName = nodeName;
    }

    public void openPort(int portNumber) throws Exception {
        socket = new DatagramSocket(portNumber);
    }

    @Override
    public boolean isActive(String nodeName) throws Exception {
        return false; // Stub
    }

    @Override
    public void pushRelay(String nodeName) throws Exception {
        relayStack.push(nodeName);
    }

    @Override
    public void popRelay() throws Exception {
        if (!relayStack.isEmpty()) {
            relayStack.pop();
        }
    }

    public boolean exists(String key) throws Exception {
        return false; // Stub
    }

    public String read(String key) throws Exception {
        if (vault.containsKey(key)) return vault.get(key);

        String targetHash = HashID.computeHashID(key);
        Set<String> visited = new HashSet<>();
        Queue<String> toVisit = new LinkedList<>();

        if (peerMap.isEmpty()) {
            peerMap.put("N:azure", new InetSocketAddress("10.216.34.152", 20114));
        }

        toVisit.addAll(peerMap.keySet());

        while (!toVisit.isEmpty()) {
            String currentNode = toVisit.poll();
            if (visited.contains(currentNode) || !peerMap.containsKey(currentNode)) continue;
            visited.add(currentNode);

            InetSocketAddress address = peerMap.get(currentNode);
            String transactionId = makeID();
            fetchCache = null;

            reply(address.getAddress(), address.getPort(), transactionId + " R 0 " + key + " ");

            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < 1000) {
                handleIncomingMessages(100);
                if (fetchCache != null) return strip(fetchCache);
            }

            String nearestTx = makeID();
            reply(address.getAddress(), address.getPort(), nearestTx + " N 0 " + key + " ");

            long t2 = System.currentTimeMillis();
            while (!cacheNearest.containsKey(nearestTx) && System.currentTimeMillis() - t2 < 1000) {
                handleIncomingMessages(100);
            }

            String responseData = cacheNearest.get(nearestTx);
            if (responseData == null) continue;

            String[] parts = responseData.trim().split(" ");
            for (int i = 0; i + 3 < parts.length; i += 4) {
                String peerKey = parts[i + 1];
                String peerVal = parts[i + 3];
                if (peerKey.startsWith("N:") && peerVal.contains(":")) {
                    String[] ipPort = peerVal.split(":");
                    InetSocketAddress peerAddr = new InetSocketAddress(ipPort[0], Integer.parseInt(ipPort[1]));
                    peerMap.put(peerKey, peerAddr);
                    if (!visited.contains(peerKey)) toVisit.add(peerKey);
                }
            }
        }

        return null;
    }

    public boolean write(String key, String value) throws Exception {
        vault.put(key, value);
        for (InetSocketAddress addr : peerMap.values()) {
            String txID = makeID();
            reply(addr.getAddress(), addr.getPort(), txID + " W 0 " + key + " " + value + " ");
        }
        return true;
    }

    public boolean CAS(String key, String currentValue, String newValue) throws Exception {
        if (!vault.containsKey(key)) {
            vault.put(key, newValue);
            return true;
        } else if (vault.get(key).equals(currentValue)) {
            vault.put(key, newValue);
            return true;
        }
        return false;
    }
}
