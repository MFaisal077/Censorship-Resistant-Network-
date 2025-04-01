// IN2011 Computer Networks
// Coursework 2024/2025
//
// Submission by
//  YOUR_NAME_GOES_HERE
//  YOUR_STUDENT_ID_NUMBER_GOES_HERE
//  YOUR_EMAIL_GOES_HERE

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;


// DO NOT EDIT starts
interface NodeInterface {
    public void setNodeName(String nodeName) throws Exception;
    public void openPort(int portNumber) throws Exception;
    public void handleIncomingMessages(int delay) throws Exception;
    public boolean isActive(String nodeName) throws Exception;
    public void pushRelay(String nodeName) throws Exception;
    public void popRelay() throws Exception;
    public boolean exists(String key) throws Exception;
    public String read(String key) throws Exception;
    public boolean write(String key, String value) throws Exception;
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
                case "G": handleNameRequest(transactionId, packet); break;
                case "N": handleNearestRequest(transactionId, message, packet); break;
                case "O": handleNearestResponse(transactionId, message); break;
                case "R": handleReadRequest(transactionId, message, packet); break;
                case "W": handleWriteRequest(transactionId, message, packet); break;
                case "C": handleCASRequest(transactionId, message, packet); break;
                case "E": handleExistsRequest(transactionId, message, packet); break;
            }
        } catch (SocketTimeoutException e) {
            // Timeout reached
        }}


    private void handleNameRequest(String tx, DatagramPacket packet) {
        reply(packet.getAddress(), packet.getPort(), tx + " H 0 N:" + nodeName + " ");
    }

    private void handleNearestRequest(String tx, String msg, DatagramPacket packet) {
        String[] parts = msg.split(" ", 4);
        if (parts.length < 4) return;
        String response = tx + " O 0 N:" + nodeName + " 0 " + packet.getAddress().getHostAddress() + ":" + packet.getPort() + " ";
        reply(packet.getAddress(), packet.getPort(), response);
    }

    private void handleNearestResponse(String tx, String msg) {
        cacheNearest.put(tx, msg);
    }

    private void handleReadRequest(String tx, String msg, DatagramPacket packet) {
        String[] parts = msg.split(" ", 5);
        if (parts.length < 5) return;
        String key = parts[4].trim();
        if (vault.containsKey(key)) {
            String value = vault.get(key);
            reply(packet.getAddress(), packet.getPort(), tx + " S Y 0 " + value + " ");
        } else {
            reply(packet.getAddress(), packet.getPort(), tx + " S N 0 ");
        }
    }

    private void handleWriteRequest(String tx, String msg, DatagramPacket packet) {
        String[] parts = msg.split(" ", 6);
        if (parts.length < 6) return;
        String key = parts[4];
        String value = parts[5].trim();
        vault.put(key, value);
        reply(packet.getAddress(), packet.getPort(), tx + " X A ");
    }

    private void handleCASRequest(String tx, String msg, DatagramPacket packet) {
        String[] parts = msg.split(" ", 7);
        if (parts.length < 7) return;
        String key = parts[4];
        String oldVal = parts[5];
        String newVal = parts[6].trim();
        if (vault.containsKey(key) && vault.get(key).equals(oldVal)) {
            vault.put(key, newVal);
            reply(packet.getAddress(), packet.getPort(), tx + " D R ");
        } else {
            reply(packet.getAddress(), packet.getPort(), tx + " D N ");
        }
    }

    private void handleExistsRequest(String tx, String msg, DatagramPacket packet) {
        String[] parts = msg.split(" ", 5);
        if (parts.length < 5) return;
        String key = parts[4].trim();
        if (vault.containsKey(key)) {
            reply(packet.getAddress(), packet.getPort(), tx + " F Y ");
        } else {
            reply(packet.getAddress(), packet.getPort(), tx + " F N ");
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
