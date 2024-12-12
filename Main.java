//Shayan Saberi-Nikou, SXS220123
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.Map.Entry;

public class Main {

    // Constants for the B-Tree structure and file format
    private static final byte[] MAGIC_NUMBER = "4337PRJ3".getBytes();
    private static final int BLOCK_SIZE = 512;
    private static final int MIN_DEGREE = 10;
    private static final int MAX_KEYS = 2 * MIN_DEGREE - 1;
    private static final int MAX_CHILDREN = 2 * MIN_DEGREE;

    // Convert a long to an 8-byte array
    private static byte[] toBytes(long n) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(n);
        return buffer.array();
    }

    // Convert an 8-byte array to a long
    private static long fromBytes(byte[] b) {
        ByteBuffer buffer = ByteBuffer.wrap(b);
        return buffer.getLong();
    }

    /**
     * Represents a single node in the B-Tree.
     * Each node stores keys, values, and pointers to child nodes.
     */
    static class BTreeNode {
        long blockId;
        long parentId;
        int numKeys;
        long[] keys;
        long[] values;
        long[] children;

        BTreeNode(long blockId) {
            this(blockId, 0, 0, new long[MAX_KEYS], new long[MAX_KEYS], new long[MAX_CHILDREN]);
        }

        BTreeNode(long blockId, long parentId, int numKeys, long[] keys, long[] values, long[] children) {
            this.blockId = blockId;
            this.parentId = parentId;
            this.numKeys = numKeys;
            this.keys = keys;
            this.values = values;
            this.children = children;
        }

        boolean isLeaf() {
            for (long c : children) {
                if (c != 0) return false;
            }
            return true;
        }
    }

    /**
     * A Least Recently Used (LRU) cache for B-Tree nodes.
     * Ensures that not all nodes stay in memory at once, and nodes are saved back
     * to the file before being evicted from the cache.
     */
    static class LRUNodeCache {
        private int capacity;
        private LinkedHashMap<Long, BTreeNode> cache;
        private BTree btree;

        LRUNodeCache(int capacity, BTree btree) {
            this.capacity = capacity;
            this.btree = btree;
            // accessOrder=true makes LinkedHashMap order entries by access
            this.cache = new LinkedHashMap<Long, BTreeNode>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long,BTreeNode> eldest) {
                    if (size() > LRUNodeCache.this.capacity) {
                        try {
                            btree.writeNodeToFile(eldest.getValue());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        return true;
                    }
                    return false;
                }
            };
        }

        BTreeNode get(long blockId) throws IOException {
            if (cache.containsKey(blockId)) {
                return cache.get(blockId);
            }
            // If not in cache, read from file and insert into the cache
            BTreeNode node = btree.readNodeFromFile(blockId);
            cache.put(blockId, node);
            return node;
        }

        void put(long blockId, BTreeNode node) {
            cache.put(blockId, node);
        }

        void clear() {
            // Write all cached nodes back to the file before clearing
            for (Entry<Long,BTreeNode> entry : cache.entrySet()) {
                try {
                    btree.writeNodeToFile(entry.getValue());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            cache.clear();
        }
    }

    /**
     * BTree class handles all operations on the B-Tree index such as
     * creating, opening, inserting, searching, loading, extracting, and printing.
     */
    static class BTree {
        private String filePath;
        private RandomAccessFile file;
        long rootId = 0;
        long nextBlockId = 1;
        LRUNodeCache cache;

        BTree(String filePath) {
            this.filePath = filePath;
            this.cache = new LRUNodeCache(3, this);
        }

        // ====== Public interface methods (reordered for clarity) ======

        void openIndexFile(String mode) throws FileNotFoundException {
            this.file = new RandomAccessFile(this.filePath, mode);
        }

        void closeIndexFile() {
            if (this.file != null) {
                this.cache.clear();
                try {
                    this.file.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                this.file = null;
            }
        }

        void loadBTree() throws IOException {
            if (this.file == null) {
                throw new IOException("File is not open.");
            }
            this.cache.clear();
            this.readHeader();
        }

        void insert(long key, long value) throws IOException {
            if (this.rootId == 0) {
                // Create a new root if tree is empty
                BTreeNode root = new BTreeNode(1);
                root.numKeys = 1;
                root.keys[0] = key;
                root.values[0] = value;
                this.rootId = root.blockId;
                this.cache.put(root.blockId, root);
                this.writeHeader();
            } else {
                BTreeNode root = this.cache.get(this.rootId);
                // If root is full, split it first
                if (root.numKeys == MAX_KEYS) {
                    BTreeNode newRoot = new BTreeNode(this.nextBlockId);
                    this.nextBlockId++;
                    newRoot.children[0] = root.blockId;
                    this.rootId = newRoot.blockId;
                    this.splitChild(newRoot, 0, root);
                    this.writeHeader();
                    this.insertNonFull(newRoot, key, value);
                } else {
                    // Insert into root if not full
                    this.insertNonFull(root, key, value);
                }
            }
        }

        Long searchKey(long key) throws IOException {
            if (this.rootId == 0) {
                return null;
            }
            BTreeNode root = this.cache.get(this.rootId);
            return searchNode(root, key);
        }

        void printTree() throws IOException {
            List<long[]> result = new ArrayList<>();
            this.inorderTraverse(result);
            for (long[] kv : result) {
                System.out.println(kv[0] + ": " + kv[1]);
            }
        }

        void extractData(String filename) throws IOException {
            File f = new File(filename);
            if (f.exists()) {
                Scanner s = new Scanner(System.in);
                System.out.print("File " + filename + " exists. Overwrite? (yes/no): ");
                String overwrite = s.nextLine().trim().toLowerCase();
                if (!overwrite.equals("yes")) {
                    System.out.println("Operation aborted.");
                    return;
                }
            }
            List<long[]> result = new ArrayList<>();
            this.inorderTraverse(result);
            BufferedWriter bw = new BufferedWriter(new FileWriter(filename));
            for (long[] kv : result) {
                bw.write(kv[0] + "," + kv[1]);
                bw.newLine();
            }
            bw.close();
            System.out.println("Data extracted to " + filename + ".");
        }

        void loadData(String filename) throws IOException {
            File f = new File(filename);
            if (!f.exists()) {
                System.out.println("File " + filename + " does not exist.");
                return;
            }
            BufferedReader br = new BufferedReader(new FileReader(filename));
            String line;
            int lineNumber = 0;
            while ((line = br.readLine()) != null) {
                lineNumber++;
                String[] parts = line.trim().split(",");
                if (parts.length != 2) {
                    System.out.println("Skipping invalid line " + lineNumber + ": " + line.trim());
                    continue;
                }
                try {
                    long k = Long.parseLong(parts[0]);
                    long v = Long.parseLong(parts[1]);
                    this.insert(k, v);
                } catch (NumberFormatException e) {
                    System.out.println("Skipping invalid line " + lineNumber + ": " + line.trim());
                }
            }
            br.close();
        }

        // ====== Internal B-Tree operations (reordered, renamed methods) ======

        // Insert into a node that is not full
        void insertNonFull(BTreeNode node, long key, long value) throws IOException {
            while (true) {
                int i = node.numKeys - 1;
                if (node.isLeaf()) {
                    // Insert into leaf node
                    while (i >= 0 && key < node.keys[i]) {
                        node.keys[i+1] = node.keys[i];
                        node.values[i+1] = node.values[i];
                        i--;
                    }
                    node.keys[i+1] = key;
                    node.values[i+1] = value;
                    node.numKeys++;
                    this.cache.put(node.blockId, node);
                    return;
                } else {
                    // Insert into internal node
                    while (i >= 0 && key < node.keys[i]) {
                        i--;
                    }
                    i++;
                    if (node.children[i] == 0) {
                        // Create a new child if it doesn't exist
                        System.out.println("Creating new child node for key " + key + " at position " + i);
                        BTreeNode newChild = new BTreeNode(this.nextBlockId);
                        newChild.parentId = node.blockId;
                        this.nextBlockId++;
                        this.cache.put(newChild.blockId, newChild);
                        node.children[i] = newChild.blockId;
                        this.cache.put(node.blockId, node);

                        // After creating a new leaf child, the loop continues and inserts into it
                        node = newChild;
                    } else {
                        // If the chosen child is full, split it first
                        BTreeNode child = this.cache.get(node.children[i]);
                        if (child.numKeys == MAX_KEYS) {
                            splitChild(node, i, child);
                            if (key > node.keys[i]) {
                                i++;
                            }
                        }
                        node = this.cache.get(node.children[i]);
                    }
                }
            }
        }

        // Split a full child node
        void splitChild(BTreeNode parent, int index, BTreeNode child) throws IOException {
            BTreeNode newChild = new BTreeNode(this.nextBlockId);
            this.nextBlockId++;

            newChild.numKeys = MIN_DEGREE - 1;
            System.arraycopy(child.keys, MIN_DEGREE, newChild.keys, 0, MIN_DEGREE - 1);
            System.arraycopy(child.values, MIN_DEGREE, newChild.values, 0, MIN_DEGREE - 1);

            if (!child.isLeaf()) {
                System.arraycopy(child.children, MIN_DEGREE, newChild.children, 0, MIN_DEGREE);
            }

            child.numKeys = MIN_DEGREE - 1;

            // Shift keys and children in the parent to make room
            System.arraycopy(parent.children, index+1, parent.children, index+2, parent.numKeys - index);
            parent.children[index+1] = newChild.blockId;

            System.arraycopy(parent.keys, index, parent.keys, index+1, parent.numKeys - index);
            System.arraycopy(parent.values, index, parent.values, index+1, parent.numKeys - index);

            parent.keys[index] = child.keys[MIN_DEGREE - 1];
            parent.values[index] = child.values[MIN_DEGREE - 1];
            parent.numKeys++;

            this.cache.put(parent.blockId, parent);
            this.cache.put(child.blockId, child);
            this.cache.put(newChild.blockId, newChild);
        }

        // In-order traversal of the B-Tree to get all key-value pairs sorted by key
        void inorderTraverse(List<long[]> result) throws IOException {
            if (this.rootId == 0) {
                return;
            }
            BTreeNode root = this.cache.get(this.rootId);
            depthFirstSearch(root, result);
        }

        // Depth-first search to collect all key-value pairs in ascending order
        void depthFirstSearch(BTreeNode node, List<long[]> result) throws IOException {
            for (int i = 0; i < node.numKeys; i++) {
                if (node.children[i] != 0) {
                    depthFirstSearch(this.cache.get(node.children[i]), result);
                }
                result.add(new long[]{node.keys[i], node.values[i]});
            }
            if (node.children[node.numKeys] != 0) {
                depthFirstSearch(this.cache.get(node.children[node.numKeys]), result);
            }
        }

        // Search for a key in the subtree rooted at 'node'
        Long searchNode(BTreeNode node, long key) throws IOException {
            int i = 0;
            while (i < node.numKeys && key > node.keys[i]) {
                i++;
            }
            if (i < node.numKeys && key == node.keys[i]) {
                return node.values[i];
            } else if (node.isLeaf()) {
                return null;
            } else {
                return searchNode(this.cache.get(node.children[i]), key);
            }
        }

        // ====== File header operations ======

        void readHeader() throws IOException {
            this.file.seek(0);
            byte[] data = new byte[BLOCK_SIZE];
            int read = this.file.read(data);
            if (read < BLOCK_SIZE) {
                throw new IOException("Invalid header size.");
            }
            for (int i = 0; i < 8; i++) {
                if (data[i] != MAGIC_NUMBER[i]) {
                    throw new IOException("Invalid magic number.");
                }
            }
            this.rootId = fromBytes(Arrays.copyOfRange(data, 8, 16));
            this.nextBlockId = fromBytes(Arrays.copyOfRange(data, 16, 24));
        }

        void writeHeader() throws IOException {
            this.file.seek(0);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bos.write(MAGIC_NUMBER);
            bos.write(toBytes(this.rootId));
            bos.write(toBytes(this.nextBlockId));

            byte[] header = bos.toByteArray();
            if (header.length < BLOCK_SIZE) {
                header = Arrays.copyOf(header, BLOCK_SIZE);
            }
            this.file.write(header);
            this.file.getChannel().force(true);
        }

        // ====== Node I/O operations ======

        BTreeNode readNodeFromFile(long blockId) throws IOException {
            this.file.seek(BLOCK_SIZE * blockId);
            byte[] data = new byte[BLOCK_SIZE];
            int read = this.file.read(data);
            if (read < BLOCK_SIZE) {
                throw new IOException("Block " + blockId + " does not exist.");
            }

            int offset = 0;
            long nodeBlockId = fromBytes(Arrays.copyOfRange(data, offset, offset+8));
            offset += 8;
            long parentId = fromBytes(Arrays.copyOfRange(data, offset, offset+8));
            offset += 8;
            int numKeys = (int)fromBytes(Arrays.copyOfRange(data, offset, offset+8));
            offset += 8;

            long[] keys = new long[MAX_KEYS];
            for (int i = 0; i < MAX_KEYS; i++) {
                keys[i] = fromBytes(Arrays.copyOfRange(data, offset, offset+8));
                offset += 8;
            }

            long[] values = new long[MAX_KEYS];
            for (int i = 0; i < MAX_KEYS; i++) {
                values[i] = fromBytes(Arrays.copyOfRange(data, offset, offset+8));
                offset += 8;
            }

            long[] children = new long[MAX_CHILDREN];
            for (int i = 0; i < MAX_CHILDREN; i++) {
                children[i] = fromBytes(Arrays.copyOfRange(data, offset, offset+8));
                offset += 8;
            }

            return new BTreeNode(nodeBlockId, parentId, numKeys, keys, values, children);
        }

        void writeNodeToFile(BTreeNode node) throws IOException {
            this.file.seek(BLOCK_SIZE * node.blockId);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bos.write(toBytes(node.blockId));
            bos.write(toBytes(node.parentId));
            bos.write(toBytes(node.numKeys));
            for (long k : node.keys) {
                bos.write(toBytes(k));
            }
            for (long v : node.values) {
                bos.write(toBytes(v));
            }
            for (long c : node.children) {
                bos.write(toBytes(c));
            }
            byte[] data = bos.toByteArray();
            if (data.length < BLOCK_SIZE) {
                data = Arrays.copyOf(data, BLOCK_SIZE);
            }
            this.file.write(data);
            this.file.getChannel().force(true);
        }
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        BTree index_file = null;

        while (true) {
            System.out.println("\nAvailable commands: create, open, insert, search, load, print, extract, quit");
            System.out.print("Enter a command: ");
            String command = scanner.nextLine().trim().toLowerCase();

            try {
                switch (command) {
                    case "create": {
                        System.out.print("Enter index file name: ");
                        String filename = scanner.nextLine().trim();
                        File f = new File(filename);
                        if (f.exists()) {
                            System.out.print("File " + filename + " exists. Overwrite? (yes/no): ");
                            String overwrite = scanner.nextLine().trim().toLowerCase();
                            if (!overwrite.equals("yes")) {
                                System.out.println("Operation aborted.");
                                continue;
                            }
                        }
                        FileOutputStream fos = new FileOutputStream(filename);
                        byte[] zeroes = new byte[BLOCK_SIZE];
                        fos.write(zeroes);
                        fos.close();
                        index_file = new BTree(filename);
                        index_file.openIndexFile("rw");
                        index_file.writeHeader();
                        System.out.println("Created and opened index file " + filename + ".");
                        break;
                    }

                    case "open": {
                        System.out.print("Enter index file name: ");
                        String filename = scanner.nextLine().trim();
                        if (index_file != null) {
                            index_file.closeIndexFile();
                        }
                        index_file = new BTree(filename);
                        index_file.openIndexFile("rw");
                        index_file.loadBTree();
                        System.out.println("Opened and loaded index file " + filename + ".");
                        break;
                    }

                    case "insert": {
                        if (index_file == null) {
                            System.out.println("No index file is open.");
                            continue;
                        }
                        try {
                            System.out.print("Enter key (unsigned integer): ");
                            long key = Long.parseLong(scanner.nextLine().trim());
                            System.out.print("Enter value (unsigned integer): ");
                            long value = Long.parseLong(scanner.nextLine().trim());
                            if (key < 0 || value < 0) {
                                throw new NumberFormatException();
                            }
                            index_file.insert(key, value);
                        } catch (NumberFormatException e) {
                            System.out.println("Invalid input. Please enter unsigned integers.");
                        }
                        break;
                    }

                    case "search": {
                        if (index_file == null) {
                            System.out.println("No index file is open.");
                            continue;
                        }
                        try {
                            System.out.print("Enter key (unsigned integer): ");
                            long key = Long.parseLong(scanner.nextLine().trim());
                            if (key < 0) {
                                throw new NumberFormatException();
                            }
                            Long val = index_file.searchKey(key);
                            if (val != null) {
                                System.out.println("Found key " + key + " with value " + val + ".");
                            } else {
                                System.out.println("Key not found.");
                            }
                        } catch (NumberFormatException e) {
                            System.out.println("Invalid input. Please enter an unsigned integer.");
                        }
                        break;
                    }

                    case "load": {
                        if (index_file == null) {
                            System.out.println("No index file is open.");
                            continue;
                        }
                        System.out.print("Enter filename to load data from: ");
                        String fname = scanner.nextLine().trim();
                        index_file.loadData(fname);
                        break;
                    }

                    case "print": {
                        if (index_file == null) {
                            System.out.println("No index file is open.");
                            continue;
                        }
                        index_file.printTree();
                        break;
                    }

                    case "extract": {
                        if (index_file == null) {
                            System.out.println("No index file is open.");
                            continue;
                        }
                        System.out.print("Enter filename to extract data to: ");
                        String fname = scanner.nextLine().trim();
                        index_file.extractData(fname);
                        break;
                    }

                    case "quit": {
                        System.out.println("Exiting program.");
                        if (index_file != null) {
                            index_file.closeIndexFile();
                        }
                        scanner.close();
                        System.exit(0);
                        break;
                    }

                    default:
                        System.out.println("Invalid command.");
                        break;
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
