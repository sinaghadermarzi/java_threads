import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

public class ProducerConsumerSemaphore extends JFrame {

    // GUI components
    private final JLabel bufferLabel;
    private final JButton produce1Btn;
    private final JButton produce2Btn;
    private final JButton produce3Btn;
    private final JButton consume1Btn;
    private final JButton consume2Btn;
    private final JButton consume3Btn;

    private final Random rand = new Random();

    // Shared bounded buffer implemented with semaphores
    private final SemaphoreBuffer<Integer> buffer;

    public ProducerConsumerSemaphore() {
        super("Producer / Consumer Demo (Semaphore)");

        // buffer capacity 5, like before
        this.buffer = new SemaphoreBuffer<>(5);

        // LEFT PANEL: Produce buttons
        produce1Btn = new JButton("Produce-1");
        produce2Btn = new JButton("Produce-2");
        produce3Btn = new JButton("Produce-3");
        JPanel leftPanel = new JPanel(new GridLayout(3, 1, 8, 8));
        leftPanel.add(produce1Btn);
        leftPanel.add(produce2Btn);
        leftPanel.add(produce3Btn);

        // RIGHT PANEL: Consume buttons
        consume1Btn = new JButton("Consume-1");
        consume2Btn = new JButton("Consume-2");
        consume3Btn = new JButton("Consume-3");
        JPanel rightPanel = new JPanel(new GridLayout(3, 1, 8, 8));
        rightPanel.add(consume1Btn);
        rightPanel.add(consume2Btn);
        rightPanel.add(consume3Btn);

        // CENTER PANEL: Title
        JPanel centerPanel = new JPanel(new GridBagLayout());
        JLabel title = new JLabel("Manual Producer / Consumer (Semaphore Version)");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        centerPanel.add(title);

        // BOTTOM PANEL: buffer label
        bufferLabel = new JLabel("Buffer: []");
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(8,16,8,16));
        bottomPanel.add(bufferLabel);

        // MAIN LAYOUT
        JPanel mainPanel = new JPanel(new BorderLayout(16,16));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));
        mainPanel.add(leftPanel, BorderLayout.WEST);
        mainPanel.add(rightPanel, BorderLayout.EAST);
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setContentPane(mainPanel);
        this.setSize(600, 260);
        this.setLocationRelativeTo(null);

        wireButtonActions();
        startGuiRefreshTimer();
    }

    /**
     * SemaphoreBuffer<T>:
     *
     * This is the classic semaphore form of bounded buffer:
     * - emptySlots: how many spaces left in buffer
     * - fullSlots: how many items available
     * - lock: protect queue modifications
     *
     * put():
     *   1. acquire an empty slot (wait if full)
     *   2. lock, add item, unlock
     *   3. release a full slot (signal consumer there's data)
     *
     * take():
     *   1. acquire a full slot (wait if empty)
     *   2. lock, remove item, unlock
     *   3. release an empty slot (signal producer there's room)
     */
    private static class SemaphoreBuffer<T> {

        private final Queue<T> queue = new LinkedList<>();

        // counts how many "free cells" are left
        private final Semaphore emptySlots;
        // counts how many "used cells" are ready to consume
        private final Semaphore fullSlots;
        // protect actual queue operations
        private final ReentrantLock lock = new ReentrantLock();

        public SemaphoreBuffer(int capacity) {
            this.emptySlots = new Semaphore(capacity); // start with N empty slots
            this.fullSlots = new Semaphore(0);         // start with 0 full slots
        }

        public void put(T item) throws InterruptedException {
            // wait until there is space
            emptySlots.acquire();

            // now we "reserve" one slot for ourselves
            lock.lock();
            try {
                queue.add(item);
            } finally {
                lock.unlock();
            }

            // tell consumers "there is an additional item to consume"
            fullSlots.release();
        }

        public T take() throws InterruptedException {
            // wait until there is something available
            fullSlots.acquire();

            T item;
            lock.lock();
            try {
                item = queue.remove();
            } finally {
                lock.unlock();
            }

            // we just freed one slot in the buffer
            emptySlots.release();
            return item;
        }

        // putMany and takeMany are helpers like before
        public void putMany(List<T> items) throws InterruptedException {
            for (T item : items) {
                put(item);
            }
        }

        public List<T> takeMany(int n) throws InterruptedException {
            List<T> out = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                out.add(take());
            }
            return out;
        }

        public List<T> snapshot() {
            // We want a consistent copy for the GUI.
            // We'll lock just long enough to copy.
            lock.lock();
            try {
                return new ArrayList<>(queue);
            } finally {
                lock.unlock();
            }
        }

        public int size() {
            lock.lock();
            try {
                return queue.size();
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Generate n random integers
     */
    private List<Integer> generateItems(int n) {
        List<Integer> items = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            items.add(rand.nextInt(1000));
        }
        return items;
    }

    /**
     * Attach logic to buttons.
     * We spawn background threads for the same reason as before:
     * put()/take() can block.
     */
    private void wireButtonActions() {
        produce1Btn.addActionListener(e -> spawnWorkerProduce(1));
        produce2Btn.addActionListener(e -> spawnWorkerProduce(2));
        produce3Btn.addActionListener(e -> spawnWorkerProduce(3));

        consume1Btn.addActionListener(e -> spawnWorkerConsume(1));
        consume2Btn.addActionListener(e -> spawnWorkerConsume(2));
        consume3Btn.addActionListener(e -> spawnWorkerConsume(3));
    }

    private void spawnWorkerProduce(int n) {
        new Thread(() -> {
            try {
                List<Integer> items = generateItems(n);
                buffer.putMany(items);
                System.out.println("Produced (semaphore): " + items);
            } catch (InterruptedException ex) {
                // ignore in demo
            }
        }, "Produce-" + n).start();
    }

    private void spawnWorkerConsume(int n) {
        new Thread(() -> {
            try {
                List<Integer> taken = buffer.takeMany(n);
                System.out.println("Consumed (semaphore): " + taken);
            } catch (InterruptedException ex) {
                // ignore in demo
            }
        }, "Consume-" + n).start();
    }

    /**
     * Swing timer to refresh the buffer display.
     */
    private void startGuiRefreshTimer() {
        Timer timer = new Timer(100, e -> {
            List<Integer> snap = buffer.snapshot();
            bufferLabel.setText("Buffer (" + snap.size() + "): " + snap);
        });
        timer.start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ProducerConsumerSemaphore app = new ProducerConsumerSemaphore();
            app.setVisible(true);
        });
    }
}