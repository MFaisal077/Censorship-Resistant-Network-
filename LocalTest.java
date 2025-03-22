import java.net.*;
import java.util.*;

class LocalTest {
	public static void main(String[] args) {
		try {
			int numberOfNodes = 2;

			// If you want to test with more nodes,
			// set the number as a command line argument
			if (args.length > 0) {
				int n = Integer.parseInt(args[0]);
				if (n >= 2 && n <= 10) {
					numberOfNodes = n;
				}
			}

			// Create an array of nodes and initialise them
			Node[] nodes = new Node[numberOfNodes];
			for (int i = 0; i < numberOfNodes; ++i) {
				nodes[i] = new Node();
				nodes[i].setNodeName("N:test" + i);
				nodes[i].openPort(20110 + i);
			}

			// Bootstrapping so that nodes know the addresses of some of the others
			bootstrap(nodes);

			// Start each of the nodes running in a thread
			// nodes[0] is handled by this program rather than a thread
			for (int i = 1; i < numberOfNodes; ++i) {
				int nodeIndex = i;
				new Thread(() -> {
					try {
						nodes[nodeIndex].handleIncomingMessages(0);
					} catch (Exception e) {
						System.err.println("Node " + nodeIndex + " encountered an error.");
						e.printStackTrace();
					}
				}).start();
			}

			// Now we can test some of the functionality of node[0]
			testStorage(nodes[0]);
		} catch (Exception e) {
			System.err.println("Exception during localTest");
			e.printStackTrace();
		}
	}

	private static void testStorage(Node node) throws Exception {
		List<String> lines = Arrays.asList(
				"O Romeo, Romeo! wherefore art thou Romeo?",
				"Deny thy father and refuse thy name;",
				"Or, if thou wilt not, be but sworn my love,",
				"And I'll no longer be a Capulet."
		);

		int successfulTests = 0;

		// Write test data
		for (int i = 0; i < lines.size(); i++) {
			String key = "D:Juliet-" + i;
			System.out.print("Writing " + key + "...");
			boolean success = node.write(key, lines.get(i));
			System.out.println(success ? " Success!" : " Failed!");
			if (success) successfulTests++;
		}

		// Read test data
		for (int i = 0; i < lines.size(); i++) {
			String key = "D:Juliet-" + i;
			System.out.print("Reading " + key + "...");
			String value = node.read(key);
			if (value != null && value.equals(lines.get(i))) {
				System.out.println(" Success!");
				successfulTests++;
			} else {
				System.out.println(" Failed (Expected: " + lines.get(i) + ", Got: " + value + ")");
			}
		}

		if (successfulTests == 2 * lines.size()) {
			System.out.println("All tests worked -- that's a good start!");
		} else {
			System.out.println("Some tests failed.");
		}
	}

	// This sends gives each node some initial address key/value pairs
	// You don't need to know how this works
	public static void bootstrap(Node[] nodes) throws Exception {
		int seed = 23; // Change for different initial network topologies
		Random r = new Random(seed);
		int n = nodes.length;
		double p = Math.log((double) n + 5) / (double) n;

		DatagramSocket ds = new DatagramSocket();
		byte[] contents = "0 W 0 N:test1 0 127.0.0.1:20111 ".getBytes();

		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				if (i != j && r.nextDouble() <= p) {
					DatagramPacket packet = new DatagramPacket(contents, contents.length, InetAddress.getLocalHost(), 20110 + i);
					ds.send(packet);
				}
			}
		}
		ds.close();
	}
}
