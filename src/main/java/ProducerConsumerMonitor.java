import javax.swing.*;
import java.awt.*;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.ArrayList;
import java.util.List;

/**
 * ProducerConsumerGUI
 *
 * Updated version:
 * - No automatic producer/consumer threads.
 * - User clicks buttons to produce or consume N items.
 * - Buffer state is shown at the bottom.
 *
 * This still uses proper monitor-style synchronization on the bounded buffer
 * (wait/notifyAll), but now production/consumption is triggered by UI events.
 */
public class ProducerConsumerMonitor extends JFrame {

    // GUI components
    private final JLabel bufferLabel;
    private final JButton produce1Btn;
    private final JButton produce2Btn;
    private final JButton produce3Btn;
    private final JButton consume1Btn;
    private final JButton consume2Btn;
    private final JButton consume3Btn;

    // shared bounded buffer
    private final BoundedBuffer<Integer> buffer;

    private final Random rand = new Random();

    public ProducerConsumerMonitor() {
        super("Producer / Consumer Demo (Manual Mode)");

        this.buffer = new BoundedBuffer<>(5);

        // LEFT PANEL: Produce buttons
        produce1Btn = new JButton("Produce-1");
        produce2Btn = new JButton("Produce-2");
        produce3Btn = new JButton("Produce-3");

        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new GridLayout(3, 1, 8, 8));
        leftPanel.add(produce1Btn);
        leftPanel.add(produce2Btn);
        leftPanel.add(produce3Btn);

        // RIGHT PANEL: Consume buttons
        consume1Btn = new JButton("Consume-1");
        consume2Btn = new JButton("Consume-2");
        consume3Btn = new JButton("Consume-3");

        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new GridLayout(3, 1, 8, 8));
        rightPanel.add(consume1Btn);
        rightPanel.add(consume2Btn);
        rightPanel.add(consume3Btn);

        // CENTER PANEL: just a spacer / visual gap for now
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new GridBagLayout());
        JLabel title = new JLabel("Manual Producer / Consumer");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        centerPanel.add(title);

        // BOTTOM: buffer label
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
        this.setSize(500, 250);
        this.setLocationRelativeTo(null);

        // hook up button logic
        wireButtonActions();

        // refresh timer (keeps buffer label in sync ~10x/sec)
        startGuiRefreshTimer();
    }

    /**
     * Classic bounded buffer with wait/notifyAll.
     * Capacity is fixed.
     */
    private static class BoundedBuffer<T> {
        private final Queue<T> queue = new LinkedList<>();
        private final int capacity;

        public BoundedBuffer(int capacity) {
            this.capacity = capacity;
        }

        public synchronized void put(T item) throws InterruptedException {
            while (queue.size() == capacity) {
                wait();
            }
            queue.add(item);
            notifyAll();
        }

        public synchronized T take() throws InterruptedException {
            while (queue.isEmpty()) {
                wait();
            }
            T item = queue.remove();
            notifyAll();
            return item;
        }

        // helper: try to take up to n items
        public List<T> takeMany(int n) throws InterruptedException {
            List<T> result = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                result.add(take());
            }
            return result;
        }

        // helper: put n items
        public void putMany(List<T> items) throws InterruptedException {
            for (T item : items) {
                put(item);
            }
        }

        public synchronized List<T> snapshot() {
            return new ArrayList<>(queue);
        }

        public synchronized int size() {
            return queue.size();
        }
    }

    /**
     * Helper to generate N random integers.
     */
    private List<Integer> generateItems(int n) {
        List<Integer> items = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            items.add(rand.nextInt(1000)); // random 0..999
        }
        return items;
    }

    /**
     * Attach listeners to buttons.
     *
     * IMPORTANT:
     * We do the buffer work in background worker threads, not on the Swing
     * event dispatch thread, because put()/take() can block (wait()).
     * If we blocked the EDT, the UI would freeze.
     */
    private void wireButtonActions() {

        produce1Btn.addActionListener(e -> spawnWorkerProduce(1));
        produce2Btn.addActionListener(e -> spawnWorkerProduce(2));
        produce3Btn.addActionListener(e -> spawnWorkerProduce(3));

        consume1Btn.addActionListener(e -> spawnWorkerConsume(1));
        consume2Btn.addActionListener(e -> spawnWorkerConsume(2));
        consume3Btn.addActionListener(e -> spawnWorkerConsume(3));
    }

    /**
     * Run a short-lived background thread that produces N items.
     */
    private void spawnWorkerProduce(int n) {
        new Thread(() -> {
            try {
                List<Integer> items = generateItems(n);
                buffer.putMany(items);
                // you could log to console for debugging:
                System.out.println("Produced: " + items);
            } catch (InterruptedException ex) {
                // ignore for demo
            }
        }, "Produce-"+n).start();
    }

    /**
     * Run a short-lived background thread that consumes N items.
     */
    private void spawnWorkerConsume(int n) {
        new Thread(() -> {
            try {
                List<Integer> taken = buffer.takeMany(n);
                System.out.println("Consumed: " + taken);
            } catch (InterruptedException ex) {
                // ignore for demo
            }
        }, "Consume-"+n).start();
    }

    /**
     * Swing timer refresh: update bufferLabel to show current contents.
     * This runs on the EDT and reads snapshot() from the buffer.
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
            ProducerConsumerMonitor app = new ProducerConsumerMonitor();
            app.setVisible(true);
        });
    }
}