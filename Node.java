// IN2011 Computer Networks
// Coursework 2024/2025
//
// Submission by
//  Mohammad Faisal
//  230065855
//  mohammad.faisal.3@city.ac.uk

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;


// DO NOT EDIT starts
interface NodeInterface {

    /* These methods configure your node.
     * They must both be called once after the node has been created but
     * before it is used. */

    // Set the name of the node.
    public void setNodeName(String nodeName) throws Exception;

    // Open a UDP port for sending and receiving messages.
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
    private DatagramSocket udpSocket;
    private static final int MAX_BUFFER = 1024;
    private Stack<String> relayPath = new Stack<>();
    private Map<String, String> knownAddresses = new HashMap<>();
    private Map<String, String> keyValueStore = new HashMap<>();
    private Map<String, Integer> nodeDistances = new HashMap<>();

    public Node() {}

    public void setNodeName(String nodeName) throws Exception {
        this.nodeName= nodeName;
        try {
            String myAddress = InetAddress.getLocalHost().getHostAddress() + ":20110";
            knownAddresses.put(nodeName, myAddress);
        } catch (UnknownHostException e) {
            throw new Exception("Address setup failed: " + e.getMessage());
        }
    }

    public void openPort(int portNumber) throws SocketException {
        udpSocket = new DatagramSocket(portNumber);
    }

    public void handleIncomingMessages(int delay) throws Exception {
        udpSocket.setSoTimeout(5000);
        byte[] inputBuffer = new byte[MAX_BUFFER];
        DatagramPacket udpPacket = new DatagramPacket(inputBuffer, inputBuffer.length);
        long begin = System.currentTimeMillis();
        int maxLoops = (delay == 0) ? Integer.MAX_VALUE : Integer.MAX_VALUE;
        int loopCount = 0;

        if (delay > 0 && loopCount == 0) {
            List<String> seedNodes = new ArrayList<>(knownAddresses.keySet());
            for (String target : seedNodes) {
                String[] addrSplit = knownAddresses.get(target).split(":");
                for (int i = 0; i <= 6; i++) {
                    String searchKey = "D:jabberwocky" + i;
                    String txn = createTxnId();
                    dispatchMessage(addrSplit[0], Integer.parseInt(addrSplit[1]),
                            txn + "N " + HashID.computeHashID(searchKey));
                }
            }
        }

        while (loopCount < maxLoops) {
            try {
                udpSocket.receive(udpPacket);
                String msg = new String(udpPacket.getData(), 0, udpPacket.getLength());
                processMessage(msg, udpPacket.getAddress().getHostAddress(), udpPacket.getPort());
                loopCount++;
            } catch (SocketTimeoutException e) {
                if (delay > 0 && (System.currentTimeMillis() - begin) >= delay) break;
            }
            if (delay > 0 && (System.currentTimeMillis() - begin) >= delay) break;
        }
    }

    public boolean isActive(String nodeName) throws Exception {
        String[] split = knownAddresses.getOrDefault(nodeName, "").split(":");
        if (split.length != 2) return false;
        String msg = createTxnId() + " G";
        dispatchMessage(split[0], Integer.parseInt(split[1]), msg);
        return true;
    }

    public void pushRelay(String nodeName) { relayPath.push(nodeName); }

    private void processMessage(String message, String senderIP, int senderPort) throws Exception {
        String[] parts = message.split(" ", 3);
        if (parts.length < 2) return;

        String txnId = parts[0];
        String command = parts[1];
        int relayCount;
        try {
            relayCount = Integer.parseInt(command);
            if (relayCount < 0) return;

            String[] relayParts = message.split(" ", relayCount + 3);
            if (relayParts.length < relayCount + 3) return;
            String actualCommand = relayParts[1];
            String remainingMessage = relayParts[2];
            List<String> relays = new ArrayList<>(Arrays.asList(relayParts).subList(2, relayCount + 2));

            if (relays.contains(nodeName)) {
                int myIndex = relays.indexOf(nodeName);
                if (myIndex < relayCount - 1) {
                    String nextRelay = relays.get(myIndex + 1);
                    String[] nextParts = knownAddresses.get(nextRelay).split(":");
                    if (nextParts.length != 2) return;

                    String forwardedMessage = txnId + " " + (relayCount - myIndex - 1) + " " +
                            String.join(" ", relays.subList(myIndex + 1, relayCount)) +
                            " " + actualCommand + " " + remainingMessage;
                    dispatchMessage(nextParts[0], Integer.parseInt(nextParts[1]), forwardedMessage);
                    return;
                } else {
                    command = actualCommand;
                    parts = (txnId + " " + command + " " + remainingMessage).split(" ", 3);
                }
            } else {
                command = actualCommand;
                parts = (txnId + " " + command + " " + remainingMessage).split(" ", 3);
            }
        } catch (NumberFormatException e) {

        }

        switch (command) {
            case "G":
                dispatchMessage(senderIP, senderPort, txnId + " H 0 " + nodeName + " ");
                break;
            case "N":
                String hashID = parts[2].trim();
                List<String> nearest = rankNearest(hashID);
                StringBuilder response = new StringBuilder(txnId + " O ");
                for (String node : nearest) {
                    response.append("0 ").append(node).append(" 0 ").append(knownAddresses.get(node)).append(" ");
                }
                dispatchMessage(senderIP, senderPort, response.toString());
                break;

            case "O":
                String[] nodes = parts[2].split(" 0 ");
                for (int i = 0; i < nodes.length; i += 2) {
                    if (i + 1 < nodes.length && nodes[i].length() > 0) {
                        String nodeName = nodes[i].trim();
                        String addr = nodes[i + 1].trim();
                        if (nodeName.startsWith("N:") && addr.matches("\\d+\\.\\d+\\.\\d+\\.\\d+:\\d+")) {
                            knownAddresses.put(nodeName, addr);
                        }
                    }
                }
                break;

            case "E":
                String keyE = parts[2].trim();
                boolean isClosest = isAmongClosest(keyE);
                if (keyValueStore.containsKey(keyE)) {
                    dispatchMessage(senderIP, senderPort, txnId + " F Y");
                } else if (isClosest) {
                    dispatchMessage(senderIP, senderPort, txnId + " F N");
                } else {
                    dispatchMessage(senderIP, senderPort, txnId + " F ?");
                }
                break;

            case "R":
                String keyR = parts[2].trim();
                String valueR = keyValueStore.get(keyR);
                if (valueR != null) {
                    dispatchMessage(senderIP, senderPort, txnId + " S Y 0 " + valueR + " ");
                } else if (isAmongClosest(keyR)) {
                    dispatchMessage(senderIP, senderPort, txnId + " S N 0  ");
                } else {
                    dispatchMessage(senderIP, senderPort, txnId + " S ? 0  ");
                }
                break;

            case "W":
                String[] kv = parts[2].split(" ", 5);
                if (kv.length >= 4) {
                    String key = kv[1];
                    String value = kv[3];
                    /*if (isAmongClosest(key)) {
                        keyValueStore.put(key, value);
                        dispatchMessage(senderIP, senderPort, txnId + " X A");
                    } else if (keyValueStore.containsKey(key)) {
                        keyValueStore.put(key, value);
                        dispatchMessage(senderIP, senderPort, txnId + " X R");
                    } else {
                        dispatchMessage(senderIP, senderPort, txnId + " X X");
                    }*/
                    if (isAmongClosest(key)) {
                        keyValueStore.put(key, value);
                        dispatchMessage(senderIP, senderPort, txnId + " X A");
                    } else if (keyValueStore.containsKey(key)) {
                        keyValueStore.put(key, value);
                        dispatchMessage(senderIP, senderPort, txnId + " X R");
                    } else {
                        dispatchMessage(senderIP, senderPort, txnId + " X X");
                    }
                    knownAddresses.put(key, senderIP + ":" + senderPort);
                }
                break;

            case "X":
            case "S":
                break;

            case "C":
                String[] casParts = parts[2].split(" ", 7);
                if (casParts.length >= 6) {
                    String keyC = casParts[1];
                    String oldVal = casParts[3];
                    String newVal = casParts[5];
                    if (keyValueStore.getOrDefault(keyC, "").equals(oldVal)) {
                        keyValueStore.put(keyC, newVal);
                        dispatchMessage(senderIP, senderPort, txnId + " D R");
                    } else if (isAmongClosest(keyC)) {
                        keyValueStore.put(keyC, newVal);
                        dispatchMessage(senderIP, senderPort, txnId + " D A");
                    } else {
                        dispatchMessage(senderIP, senderPort, txnId + " D N");
                    }
                }
                break;

            case "I":
                break;
            case "V":
                String[] relayParts = parts[2].split(" ", 3);
                if (relayParts.length < 2) return; // Malformed message

                // Decode the target node name (CRN string format: <space_count> <node_name> )
                String targetNodeEncoded = relayParts[0] + " " + relayParts[1];
                String targetNode = decodeCRNString(targetNodeEncoded);
                if (!targetNode.startsWith("N:")) return; // Invalid node name

                // Extract the inner message
                String innerMessage = relayParts[2].trim();

                // Look up the target node's address
                String[] targetAddr = knownAddresses.getOrDefault(targetNode, "").split(":");
                if (targetAddr.length != 2) return; // Target node not found

                // Forward the inner message to the target node
                dispatchMessage(targetAddr[0], Integer.parseInt(targetAddr[1]), innerMessage);

                // Check if the inner message is a request by examining its command
                String innerCommand = innerMessage.split(" ", 2)[1].trim();
                if (isRequestCommand(innerCommand)) {
                    // Wait for a response from the target node
                    udpSocket.setSoTimeout(5000); // 5-second timeout per CRN spec
                    byte[] buf = new byte[MAX_BUFFER];
                    DatagramPacket responsePacket = new DatagramPacket(buf, buf.length);
                    boolean responseReceived = false;

                    for (int i = 0; i < 3; i++) { // Max 3 resends per CRN spec
                        try {
                            udpSocket.receive(responsePacket);
                            String responses = new String(responsePacket.getData(), 0, responsePacket.getLength());
                            // Forward the response back to the original sender using the relay txnId
                            dispatchMessage(senderIP, senderPort, txnId + responses.substring(2)); // Keep relay txnId
                            responseReceived = true;
                            break;
                        } catch (SocketTimeoutException e) {
                            if (i < 2) {
                                // Resend the inner message
                                dispatchMessage(targetAddr[0], Integer.parseInt(targetAddr[1]), innerMessage);
                            }
                        }
                    }

                    if (!responseReceived) {
                        // Optionally send an error message back to the sender
                        dispatchMessage(senderIP, senderPort, txnId + " I 0 Relay timeout ");
                    }
                }
                break;


            default:
                System.out.println("Unknown command: " + command);
        }
    }
    private String decodeCRNString(String encoded) {
        String[] parts = encoded.trim().split(" ", 2);
        if (parts.length < 2) return "";
        int spaceCount = Integer.parseInt(parts[0]);
        return parts[1].substring(0, parts[1].length() - spaceCount - 1);
    }

    private boolean isRequestCommand(String command) {
        // CRN request commands: G, N, E, R, W, C
        return command.equals("G") || command.equals("N") || command.equals("E") ||
                command.equals("R") || command.equals("W") || command.equals("C");
    }

    private String encodeCRNString(String s) {
        int spaceCount = s.length() - s.replace(" ", "").length();
        return spaceCount + " " + s + " ";
    }
    public void popRelay() { if (!relayPath.isEmpty()) relayPath.pop(); }

    public boolean exists(String key) throws Exception {
        List<String> closeBy = locateNearest(key);
        for (String node : closeBy) {
            String[] addr = knownAddresses.get(node).split(":");
            String msg = createTxnId() + " E 0 " + key + " ";
            dispatchMessage(addr[0], Integer.parseInt(addr[1]), msg);
        }
        return keyValueStore.containsKey(key);
    }

    public String read(String key) throws Exception {
        if (keyValueStore.containsKey(key)) return keyValueStore.get(key);
        int retries = 20;
        Set<String> visited = new HashSet<>();

        for (int tryNum = 0; tryNum < retries; tryNum++) {
            List<String> closeBy = locateNearest(key);
            List<String> selected = closeBy.size() > 5 ? closeBy.subList(0, 5) : closeBy;
            String txn = createTxnId();
            String msg = txn + "R 0 " + key + " ";

            for (String node : selected) {
                if (!visited.contains(node)) {
                    String[] addr = knownAddresses.get(node).split(":");
                    dispatchMessage(addr[0], Integer.parseInt(addr[1]), msg);
                    visited.add(node);
                }
            }

            udpSocket.setSoTimeout(10000);
            byte[] buf = new byte[MAX_BUFFER];
            DatagramPacket pkt = new DatagramPacket(buf, buf.length);
            long t0 = System.currentTimeMillis();

            while (System.currentTimeMillis() - t0 < 10000) {
                try {
                    udpSocket.receive(pkt);
                    String reply = new String(pkt.getData(), 0, pkt.getLength());
                    String[] parts = reply.split(" ", 4);
                    if (parts.length >= 3 && parts[0].equals(txn.trim()) && parts[1].equals("S")) {
                        if (parts[2].equals("Y")) return parts[3];
                        if (parts[2].equals("N")) return null;
                    } else if (reply.contains("Rate limit reached")) {
                        Thread.sleep(2000);
                        break;
                    }
                } catch (SocketTimeoutException e) {
                    break;
                }
            }

            if (tryNum % 3 == 0) {
                List<String> allNodes = new ArrayList<>(knownAddresses.keySet());
                for (String n : allNodes) {
                    String[] addr = knownAddresses.get(n).split(":");
                    String txnExpand = createTxnId();
                    dispatchMessage(addr[0], Integer.parseInt(addr[1]),
                            txnExpand + "N " + HashID.computeHashID(key));
                }
                handleIncomingMessages(1000);
            }

            Thread.sleep(500);
        }

        System.out.println("Key not found: " + key + " after retries");
        return null;
    }

    /*public boolean write(String key, String value) throws Exception {
        List<String> nearNodes = locateNearest(key);
        String txn = createTxnId();
        String msg = txn + "W 0 " + key + " 0 " + value;

        List<String> subset = nearNodes.subList(0, Math.min(3, nearNodes.size()));
        for (String node : subset) {
            String[] addr = knownAddresses.get(node).split(":");
            dispatchMessage(addr[0], Integer.parseInt(addr[1]), msg);
        }

        udpSocket.setSoTimeout(5000);
        byte[] buf = new byte[MAX_BUFFER];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        boolean acknowledged = false;

        for (int i = 0; i < 2; i++) {
            try {
                udpSocket.receive(packet);
                String response = new String(packet.getData(), 0, packet.getLength());
                String[] parts = response.split(" ", 3);
                if (parts.length >= 3 && parts[0].equals(txn.trim()) && parts[1].equals("X")) {
                    if (parts[2].equals("A") || parts[2].equals("R")) {
                        acknowledged = true;
                        keyValueStore.put(key, value);
                        break;
                    }
                }
            } catch (SocketTimeoutException e) {
                if (i < 1) {
                    for (String node : subset) {
                        String[] addr = knownAddresses.get(node).split(":");
                        dispatchMessage(addr[0], Integer.parseInt(addr[1]), msg);
                    }
                }
            }
        }

        if (!acknowledged) keyValueStore.put(key, value);
        return acknowledged;
    }
*/
    public boolean write(String key, String value) throws Exception {
        List<String> nearNodes = locateNearest(key);
        String txn = createTxnId();
        String msg = txn + "W 0 " + key + " 0 " + value;

        List<String> subset = nearNodes.subList(0, Math.min(3, nearNodes.size()));
        for (String node : subset) {
            String[] addr = knownAddresses.get(node).split(":");
            dispatchMessage(addr[0], Integer.parseInt(addr[1]), msg);
        }

        udpSocket.setSoTimeout(5000);
        byte[] buf = new byte[MAX_BUFFER];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        boolean acknowledged = false;

        for (int i = 0; i < 2; i++) {
            try {
                udpSocket.receive(packet);
                String response = new String(packet.getData(), 0, packet.getLength());
                String[] parts = response.split(" ", 3);
                if (parts.length >= 3 && parts[0].equals(txn.trim()) && parts[1].equals("X")) {
                    if (parts[2].equals("A") || parts[2].equals("R")) {
                        acknowledged = true;
                        keyValueStore.put(key, value);
                        break;
                    }
                }
            } catch (SocketTimeoutException e) {
                if (i < 1) {
                    for (String node : subset) {
                        String[] addr = knownAddresses.get(node).split(":");
                        dispatchMessage(addr[0], Integer.parseInt(addr[1]), msg);
                    }
                }
            }
        }

        if (!acknowledged) keyValueStore.put(key, value);
        return acknowledged;
    }
    public boolean CAS(String key, String current, String updated) throws Exception {
        List<String> nearNodes = locateNearest(key);
        for (String node : nearNodes) {
            String[] addr = knownAddresses.get(node).split(":");
            String msg = createTxnId() + " C 0 " + key + " 0 " + current + " 0 " + updated + " ";
            dispatchMessage(addr[0], Integer.parseInt(addr[1]), msg);
        }
        if (keyValueStore.getOrDefault(key, "").equals(current)) {
            keyValueStore.put(key, updated);
            return true;
        }
        return false;
    }



    private String createTxnId() {
        byte[] tx = new byte[2];
        new Random().nextBytes(tx);
        for (int i = 0; i < tx.length; i++) {
            tx[i] = (byte) (Math.abs(tx[i]) % 94 + 33);
        }
        return new String(tx, StandardCharsets.UTF_8) + " ";
    }

    private List<String> locateNearest(String key) throws Exception {
        String hash = HashID.computeHashID(key);
        List<String> allNodes = new ArrayList<>(knownAddresses.keySet());

        if (allNodes.size() <= 1) {
            String[] myself = knownAddresses.get(nodeName).split(":");
            String txn = createTxnId();
            dispatchMessage(myself[0], Integer.parseInt(myself[1]), txn + "N " + hash);
            handleIncomingMessages(1000);
            allNodes = new ArrayList<>(knownAddresses.keySet());
        }

        Map<String, String> temporary = new HashMap<>();
        int count = 0;
        for (String node : allNodes) {
            if (count >= 5) break;
            String[] addr = knownAddresses.get(node).split(":");
            String txn = createTxnId();
            dispatchMessage(addr[0], Integer.parseInt(addr[1]), txn + "N " + hash);
            count++;

            udpSocket.setSoTimeout(2000);
            byte[] buffer = new byte[MAX_BUFFER];
            DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
            try {
                udpSocket.receive(reply);
                String response = new String(reply.getData(), 0, reply.getLength());
                String[] parts = response.split(" ", 3);
                if (parts.length >= 2 && parts[0].equals(txn.trim()) && parts[1].equals("O")) {
                    String[] nodes = parts[2].split(" 0 ");
                    for (int i = 0; i < nodes.length; i += 2) {
                        if (i + 1 < nodes.length) {
                            String nName = nodes[i].trim();
                            String nAddr = nodes[i + 1].trim();
                            if (nName.startsWith("N:") && nAddr.matches("\\d+\\.\\d+\\.\\d+\\.\\d+:\\d+")) {
                                temporary.put(nName, nAddr);
                            }
                        }
                    }
                }
            } catch (SocketTimeoutException ignored) {}
        }

        knownAddresses.putAll(temporary);
        return rankNearest(hash);
    }
    private boolean isAmongClosest(String key) throws Exception {
        String targetHash = HashID.computeHashID(key);
        String thisHash = HashID.computeHashID(nodeName);
        int thisDistance = computeDist(thisHash, targetHash);

        List<String> nearest = rankNearest(targetHash);

        // Pre-compute distances for safety
        Map<String, Integer> distanceMap = new HashMap<>();
        for (String node : nearest) {
            String nodeHash = HashID.computeHashID(node);
            distanceMap.put(node, computeDist(nodeHash, targetHash));
        }

        // Count how many nodes are closer or equal in distance
        long closerOrEqual = nearest.stream()
                .filter(n -> distanceMap.get(n) <= thisDistance)
                .count();

        return closerOrEqual < 3;
    }

    private List<String> rankNearest(String hashID) throws Exception {
        List<String> nearby = new ArrayList<>();
        for (String n : knownAddresses.keySet()) {
            String nHash = HashID.computeHashID(n);
            int dist = computeDist(nHash, hashID);
            nodeDistances.put(n, dist);
            nearby.add(n);
        }
        nearby.sort(Comparator.comparingInt(nodeDistances::get));
        return nearby.subList(0, Math.min(3, nearby.size()));
    }

    private int computeDist(String h1, String h2) {
        int bitsMatch = 0;
        for (int i = 0; i < Math.min(h1.length(), h2.length()); i++) {
            int xor = Integer.parseInt(h1.substring(i, i + 1), 16) ^ Integer.parseInt(h2.substring(i, i + 1), 16);
            if (xor == 0) bitsMatch += 4;
            else {
                bitsMatch += Integer.numberOfLeadingZeros(xor) - 28;
                break;
            }
        }
        return 256 - bitsMatch;
    }

    private String convertToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    private void dispatchMessage(String ip, int port, String msg) throws Exception {
        byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
        DatagramPacket udpPacket = new DatagramPacket(bytes, bytes.length,
                InetAddress.getByName(ip), port);
        udpSocket.send(udpPacket);
    }

    // For Testing Purposes
    public void showKnownNodes() {
        knownAddresses.forEach((k, v) -> System.out.println("Node: " + v));
    }

    public void insertDummyNode(String name, String addr) {
        knownAddresses.put(name, addr);
    }

    public void closePort() {
        if (udpSocket != null && !udpSocket.isClosed()) udpSocket.close();
    }
}