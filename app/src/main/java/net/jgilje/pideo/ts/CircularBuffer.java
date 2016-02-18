package net.jgilje.pideo.ts;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

/**
 * Created by jgilje on 02.05.15.
 */
public class CircularBuffer {
    private int PACKET_SIZE;

    private static final int DEMUX_BUFFER_PACKETS = 128;
    private ByteBuffer writerBuffer;
    private ByteBuffer readerBuffer;

    private Thread channelReaderThread;
    private final Object readerWriterMonitor = new Object();

    private static final int WRAP_MARKER = 0x8000_0000;
    private static final int COUNTER_MASK = ~WRAP_MARKER;
    private volatile int demuxWriterPacket = 0;
    private volatile int demuxReaderPacket = 0;

    CircularBuffer(int packetSize) {
        PACKET_SIZE = packetSize;

        writerBuffer = ByteBuffer.allocate(PACKET_SIZE * DEMUX_BUFFER_PACKETS);
        readerBuffer = writerBuffer.asReadOnlyBuffer();
    }

    public static boolean FillBuffer(ReadableByteChannel channel, ByteBuffer buffer) throws IOException {
        int bytes;
        do {
            bytes = channel.read(buffer);
        } while (bytes != -1 && buffer.hasRemaining());

        return bytes > 0;
    }

    /**
     * The buffer is empty if the packet pointers is equal
     * @return
     */
    private boolean isEmpty() {
        if (demuxReaderPacket == demuxWriterPacket) {
            return true;
        }

        return false;
    }

    /**
     * The buffer is full if the packet pointers only differs by WRAP_MARKER (MSB)
     * @return
     */
    private boolean isFull() {
        if ((demuxWriterPacket ^ demuxReaderPacket) == WRAP_MARKER) {
            return true;
        }

        return false;
    }
    /**
     * Configures the readerBuffer for the current demuxReaderPacket, and parses the packet
     * Waits indefinitely if the buffer is empty
     */
    public void nextReader() {
        boolean packet_available = false;
        do {
            if (isEmpty()) {
                synchronized (readerWriterMonitor) {
                    try {
                        readerWriterMonitor.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            } else {
                int packet = demuxReaderPacket & COUNTER_MASK;
                readerBuffer.position(packet * PACKET_SIZE);
                readerBuffer.limit((packet + 1) * PACKET_SIZE);
                packet_available = true;
            }
        } while (! packet_available);
    }

    /**
     * Advances demuxReaderPacket position by one
     */
    public void advanceReader() {
        int packet = demuxReaderPacket & COUNTER_MASK;

        packet++;
        if (packet >= DEMUX_BUFFER_PACKETS) {
            demuxReaderPacket = (demuxReaderPacket ^ WRAP_MARKER) & WRAP_MARKER;
        } else {
            demuxReaderPacket++;
        }

        synchronized (readerWriterMonitor) {
            readerWriterMonitor.notifyAll();
        }
    }

    public ByteBuffer readerBuffer() {
        return readerBuffer;
    }

    /**
     * Reads from Channel and adds to writerBuffer
     * (this class could just as well be named BufferWriter)
     */
    private class ChannelReader implements Runnable {
        ReadableByteChannel channel;
        ChannelReader(ReadableByteChannel channel) {
            this.channel = channel;
        }

        private void nextWriter() {
            boolean packet_available = false;
            do {
                if (isFull()) {
                    synchronized (readerWriterMonitor) {
                        try {
                            readerWriterMonitor.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                } else {
                    int packet = (demuxWriterPacket & COUNTER_MASK);
                    writerBuffer.position(packet * PACKET_SIZE);
                    writerBuffer.limit((packet + 1) * PACKET_SIZE);
                    packet_available = true;
                }
            } while (! packet_available);
        }

        private void advanceWriter() {
            int packet = demuxWriterPacket & COUNTER_MASK;

            packet++;
            if (packet >= DEMUX_BUFFER_PACKETS) {
                demuxWriterPacket = (demuxWriterPacket ^ WRAP_MARKER) & WRAP_MARKER;
            } else {
                demuxWriterPacket++;
            }

            synchronized (readerWriterMonitor) {
                readerWriterMonitor.notifyAll();
            }
        }

        @Override
        public void run() {
            while (! Thread.interrupted()) {
                nextWriter();

                try {
                    if (! FillBuffer(channel, writerBuffer)) {
                        Thread.currentThread().interrupt();
                        continue;
                    }
                } catch (IOException e) {
                    Thread.currentThread().interrupt();
                    continue;
                }

                advanceWriter();
            }
        }
    }

    public void stopChannelReader() throws InterruptedException {
        if (channelReaderThread != null) {
            channelReaderThread.interrupt();
            channelReaderThread.join();
        }
    }

    public void startChannelReader(ReadableByteChannel channel) {
        channelReaderThread = new Thread(new ChannelReader(channel), "CircularBuffer.ChannelReader");
        channelReaderThread.start();
    }
}
