import java.net.InetAddress;

public class RelayTest {
    public static void main(String[] args) throws Exception {
        // Step 1: Create four nodes (for multiple relays)
        Node[] nodes = new Node[4];
        String[] nodeNames = {"N:nodeA", "N:nodeB", "N:nodeC", "N:nodeD"};
        int[] ports = {20110, 20111, 20112, 20113};

        for (int i = 0; i < 4; i++) {
            nodes[i] = new Node();
            nodes[i].setNodeName(nodeNames[i]);
            nodes[i].openPort(ports[i]);
        }

        // Step 2: Bootstrap the nodes
        String localIP = InetAddress.getLocalHost().getHostAddress();
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                nodes[i].insertDummyNode(nodeNames[j], localIP + ":" + ports[j]);
            }
        }

        // Step 3: Start N:nodeB, N:nodeC, and N:nodeD in separate threads
        Thread[] nodeThreads = new Thread[3];
        for (int i = 0; i < 3; i++) {
            final int index = i + 1; // N:nodeB, N:nodeC, N:nodeD
            nodeThreads[i] = new Thread(() -> {
                try {
                    nodes[index].handleIncomingMessages(0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            nodeThreads[i].start();
        }

        // Allow threads to start
        Thread.sleep(1000);

        // Test 1: Single Relay (N:nodeA -> N:nodeB -> N:nodeC)
        System.out.println("=== Test 1: Single Relay (N:nodeA -> N:nodeB -> N:nodeC) ===");
        System.out.println("Setting N:nodeB as relay for N:nodeA...");
        nodes[0].pushRelay("N:nodeB");

        String key = "D:testKey";
        String value = "Hello, Relay!";
        System.out.println("Writing " + key + " from N:nodeA (via N:nodeB) to N:nodeC...");
        boolean writeSuccess = nodes[0].write(key, value);
        if (writeSuccess) {
            System.out.println("Write successful!");
        } else {
            System.out.println("Write failed!");
            return;
        }

        System.out.println("Reading " + key + " from N:nodeA (via N:nodeB) from N:nodeC...");
        String readValue = nodes[0].read(key);
        if (readValue != null && readValue.equals(value)) {
            System.out.println("Read successful! Value: " + readValue);
        } else {
            System.out.println("Read failed! Expected: " + value + ", Got: " + readValue);
            return;
        }

        // Pop the relay for the next test
        nodes[0].popRelay();

        // Test 2: Multiple Relays (N:nodeA -> N:nodeB -> N:nodeC -> N:nodeD)
        System.out.println("\n=== Test 2: Multiple Relays (N:nodeA -> N:nodeB -> N:nodeC -> N:nodeD) ===");
        System.out.println("Setting N:nodeB and N:nodeC as relays for N:nodeA...");
        nodes[0].pushRelay("N:nodeB");
        nodes[0].pushRelay("N:nodeC");

        String key2 = "D:testKey2";
        String value2 = "Multiple Relays!";
        System.out.println("Writing " + key2 + " from N:nodeA (via N:nodeB -> N:nodeC) to N:nodeD...");
        writeSuccess = nodes[0].write(key2, value2);
        if (writeSuccess) {
            System.out.println("Write successful!");
        } else {
            System.out.println("Write failed!");
            return;
        }

        System.out.println("Reading " + key2 + " from N:nodeA (via N:nodeB -> N:nodeC) from N:nodeD...");
        readValue = nodes[0].read(key2);
        if (readValue != null && readValue.equals(value2)) {
            System.out.println("Read successful! Value: " + readValue);
        } else {
            System.out.println("Read failed! Expected: " + value2 + ", Got: " + readValue);
            return;
        }

        // Pop the relays
        nodes[0].popRelay();
        nodes[0].popRelay();

        // Test 3: Non-Responsive Relay (N:nodeA -> N:nodeB -> N:nodeC, but N:nodeB is down)
        System.out.println("\n=== Test 3: Non-Responsive Relay (N:nodeB down) ===");
        System.out.println("Shutting down N:nodeB...");
        nodeThreads[0].interrupt();
        nodes[1].closePort();
        Thread.sleep(1000); // Ensure N:nodeB is down

        System.out.println("Setting N:nodeB as relay for N:nodeA...");
        nodes[0].pushRelay("N:nodeB");

        System.out.println("Attempting to read " + key + " from N:nodeA (via N:nodeB) from N:nodeC...");
        readValue = nodes[0].read(key);
        if (readValue == null) {
            System.out.println("Read failed as expected (N:nodeB is down).");
        } else {
            System.out.println("Read succeeded unexpectedly! Value: " + readValue);
            return;
        }

        // Clean up
        for (Thread thread : nodeThreads) {
            thread.interrupt();
        }
        for (Node node : nodes) {
            node.closePort();
        }

        System.out.println("\nALL relay tests passed!");
    }
}