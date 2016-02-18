package net.jgilje.pideo.ts;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by jgilje on 01.04.15.
 */
public class Demuxer {
    public static final int TS_PACKET_SIZE = 188;

    private Map<Integer, Stream> streams = new HashMap<>();
    private int selectedPid = -1;

    private CircularBuffer circularBuffer = new CircularBuffer(TS_PACKET_SIZE);
    private ByteBuffer readerBuffer = circularBuffer.readerBuffer();
    private Packet demuxPacket = new Packet(readerBuffer);

    public int packetSize() {
        return TS_PACKET_SIZE;
    }

    public void selectPid(int pid) {
        selectedPid = pid;
    }

    public ByteBuffer readBuffer() {
        return readerBuffer;
    }

    public void start(ReadableByteChannel channel) {
        circularBuffer.startChannelReader(channel);
    }

    public Packet next() {
        circularBuffer.nextReader();
        demuxPacket.parse(streams);
        return demuxPacket;
    }

    public void advance() {
        circularBuffer.advanceReader();
    }

    public void peekPid(ByteBuffer dest) {
        if (! demuxPacket.payloadStart()) {
            System.out.println("Out of sync! fetchPid without payloadStart");
        }

        if (demuxPacket.pid() == selectedPid && demuxPacket.hasPayload()) {
            int pos = readerBuffer.position();
            dest.put(readerBuffer);
            readerBuffer.position(pos);
        }
    }

    public void fetchPid(ByteBuffer dest) throws IOException {
        if (! demuxPacket.payloadStart()) {
            System.out.println("Out of sync! fetchPid without payloadStart");
        }

        if (demuxPacket.pid() == selectedPid && demuxPacket.hasPayload()) {
            dest.put(readerBuffer);
        }
        circularBuffer.advanceReader();

        while (true) {
            circularBuffer.nextReader();
            demuxPacket.parse(streams);

            if (demuxPacket.payloadStart()) {
                break;
            }

            if (demuxPacket.pid() == selectedPid && demuxPacket.hasPayload()) {
                dest.put(readerBuffer);
            }

            circularBuffer.advanceReader();
        }
    }

    public long presentationTime() {
        if (streams.containsKey(selectedPid)) {
            Stream tsStream = streams.get(selectedPid);
            return tsStream.pts;
        }

        return -1;
    }

    public long sampleTime() {
        if (streams.containsKey(selectedPid)) {
            Stream tsStream = streams.get(selectedPid);

            if (tsStream.last_pts > 0) {
                return tsStream.pts - tsStream.last_pts;
            }

            return -1;
        }

        return -1;
    }

    public void stop() throws InterruptedException {
        circularBuffer.stopChannelReader();
    }
}
