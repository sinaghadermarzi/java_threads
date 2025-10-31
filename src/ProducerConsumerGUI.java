import javax.swing.*;
import java.awt.*;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

/**
 * ProducerConsumerGUI
 *
 * This program demonstrates:
 * - A bounded buffer shared between a Producer thread and a Consumer thread.
 * - Proper synchronization with wait()/notifyAll().
 * - A Swing GUI that live-updates to show buffer state.
 *
 * The buffer holds integers. The producer keeps generating numbers.
 * The consumer keeps removing them.
 */
public class ProducerConsumerGUI extends JFrame {

    // --- GUI components ---
    private final JLabel bufferLabel;
    private final JLabel lastProducedLabel;
    private final JLabel lastConsumedLabel;

    // Shared state that GUI will display
    private final BoundedBuffer<Integer> buffer;
    private volatile Integer lastProduced = null;
    private volatile Integer lastConsumed = null;

    public ProducerConsumerGUI() {
        super("Producer / Consumer Demo");

        // buffer of size 5
        this.buffer = new BoundedBuffer<>(5);

        // GUI labels
        bufferLabel = new JLabel("Buffer: []");
        lastProducedLabel = new JLabel("Last produced: -");
        lastConsumedLabel = new JLabel("Last consumed: -");

        // layout the UI
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(3, 1, 8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));
        panel.add(bufferLabel);
        panel.add(lastProducedLabel);
        panel.add(lastConsumedLabel);

        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setContentPane(panel);
        this.setSize(400, 200);
        this.setLocationRelativeTo(null);

        // start background threads AFTER UI is up
        startThreads();
        // start a Swing timer to refresh the GUI ~10x/sec
        startGuiRefreshTimer();
    }

    /**
     * BoundedBuffer<T> is a classic monitor:
     * - put() waits if buffer is full
     * - take() waits if buffer is empty
     */
    private static class BoundedBuffer<T> {
        private final Queue<T> queue = new LinkedList<>();
        private final int capacity;

        public BoundedBuffer(int capacity) {
            this.capacity = capacity;
        }

        public synchronized void put(T item) throws InterruptedException {
            while (queue.size() == capacity) {
                wait(); // wait for space
            }
            queue.add(item);
            notifyAll(); // wake any waiting consumer
        }

        public synchronized T take() throws InterruptedException {
            while (queue.isEmpty()) {
                wait(); // wait for data
            }
            T item = queue.remove();
            notifyAll(); // wake any waiting producer
            return item;
        }

        public synchronized java.util.List<T> snapshot() {
            // return a shallow copy for display
            return new java.util.ArrayList<>(queue);
        }

        public synchronized int size() {
            return queue.size();
        }
    }

    // Thread that keeps producing integers
    private class Producer implements Runnable {
        private final Random rand = new Random();
        private int counter = 0;

        @Override
        public void run() {
            try {
                while (true) {
                    // create a new "item"
                    int item = counter++;
                    // pretend work: random sleep
                    Thread.sleep(200 + rand.nextInt(400));

                    // put item into the buffer
                    buffer.put(item);

                    // remember last produced for GUI
                    lastProduced = item;
                }
            } catch (InterruptedException e) {
                // thread ending
            }
        }
    }

    // Thread that keeps consuming integers
    private class Consumer implements Runnable {
        private final Random rand = new Random();

        @Override
        public void run() {
            try {
                while (true) {
                    // pretend work: random sleep
                    Thread.sleep(300 + rand.nextInt(500));

                    // take item from buffer
                    Integer item = buffer.take();

                    // remember last consumed for GUI
                    lastConsumed = item;
                }
            } catch (InterruptedException e) {
                // thread ending
            }
        }
    }

    /**
     * Launch producer and consumer threads.
     */
    private void startThreads() {
        Thread producerThread = new Thread(new Producer(), "ProducerThread");
        Thread consumerThread = new Thread(new Consumer(), "ConsumerThread");

        // mark them as daemons so program exits when window closes
        producerThread.setDaemon(true);
        consumerThread.setDaemon(true);

        producerThread.start();
        consumerThread.start();
    }

    /**
     * Swing Timer: pull the latest state from shared fields
     * and reflect it in the labels.
     *
     * We do NOT touch Swing components from worker threads directly.
     * Instead, we cache state in lastProduced/lastConsumed and snapshot()
     * the buffer here in the EDT (Event Dispatch Thread).
     */
    private void startGuiRefreshTimer() {
        Timer timer = new Timer(100, e -> {
            // safely snapshot buffer state for display
            java.util.List<Integer> snapshot = buffer.snapshot();
            bufferLabel.setText("Buffer (" + snapshot.size() + "): " + snapshot);
            lastProducedLabel.setText("Last produced: " + (lastProduced == null ? "-" : lastProduced));
            lastConsumedLabel.setText("Last consumed: " + (lastConsumed == null ? "-" : lastConsumed));
        });
        timer.start();
    }

    public static void main(String[] args) {
        // All Swing UI creation should happen on the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            ProducerConsumerGUI app = new ProducerConsumerGUI();
            app.setVisible(true);
        });
    }
}