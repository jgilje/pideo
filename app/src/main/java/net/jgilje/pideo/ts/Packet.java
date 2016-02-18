package net.jgilje.pideo.ts;

import java.nio.ByteBuffer;
import java.util.Map;

/**
 * Created by jgilje on 26.04.15.
 */
public class Packet {
    public static final byte SYNC_BYTE = 0x47;

    private ByteBuffer buffer;
    private int header;

    Packet(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    void parse(Map<Integer, Stream> streams) {
        header = buffer.getInt();
        if (! valid()) {
            System.err.println("INVALID Packet!");
        }

        if (hasAdaptation()) {
            int adaptation_size = (buffer.get() & 0xff);
            buffer.position(buffer.position() + adaptation_size);
        }

        if (isPsi(streams)) {
            parsePsi(streams);
        } else {
            if (payloadStart()) {
                parsePes(streams);
            }
        }
    }

    public boolean valid() {
        return ((header & 0xff000000) >> 24) == SYNC_BYTE;
    }

    public boolean transportError() {
        return (header & 0x800000) != 0;
    }

    public boolean payloadStart() {
        return (header & 0x400000) != 0;
    }

    public int pid() {
        return (header & 0x1fff00) >> 8;
    }

    public byte scamblingControl() {
        return (byte) ((header & 0xc0) >> 6);
    }

    public boolean hasAdaptation() {
        return (header & 0x20) != 0;
    }

    public boolean hasPayload() {
        return (header & 0x10) != 0;
    }

    public boolean isPsi(Map<Integer, Stream> streams) {
        return pid() == 0 || (streams.containsKey(pid()) && streams.get(pid()).pmt);
    }

    public int continuity() {
        return header & 0xf;
    }

    public void parsePsi(Map<Integer, Stream> streams) {
        byte pointer_field = buffer.get();
        for (byte i = 0; i < pointer_field; i++) {
            buffer.get();
        }

        byte table_id = buffer.get();
        short table_header = buffer.getShort();
        int section_length = table_header & 0x3ff;

        if (pid() == 0) {
            // PAT (Program association specific data)

            // skip table syntax
            buffer.position(buffer.position() + 5);

            int remaining = buffer.remaining() - 4;    // crc32 field at end
            int table_entries = remaining / 4;
            for (int i = 0; i < table_entries; i++) {
                int channel = buffer.getShort();
                int pid = (buffer.getShort() & 0x1fff);

                Stream stream;
                if (streams.containsKey(pid)) {
                    stream = streams.get(pid);
                } else {
                    stream = new Stream();
                    streams.put(pid, stream);
                }
                stream.channel = channel;
                stream.pmt = true;
            }
        } else if (table_id == 2) {
            // PMT (Program map specific data)

            // skip table syntax
            buffer.position(buffer.position() + 5);
            int pcr = (buffer.getShort() & 0x1fff);    // program clock reference - pid
            int info_length = (buffer.getShort() & 0x3ff);
            buffer.position(buffer.position() + info_length);

            int type = buffer.get() & 0xff;
            int pid = buffer.getShort() & 0x1fff;
            int es_length = buffer.getShort() & 0x3ff;
            if (! streams.containsKey(pid)) {
                Stream tsStream = new Stream();
                tsStream.type = type;
                streams.put(pid, tsStream);
            }

            buffer.position(buffer.position() + es_length);
        }
    }

    private long pts() {
        long pts = (buffer.get() & 0xe) << 29;
        pts |= (buffer.get() & 0xff) << 22;
        pts |= (buffer.get() & 0xfe) << 14;
        pts |= (buffer.get() & 0xff) << 7;
        pts |= (buffer.get() & 0xfe) >> 1;
        return pts / 90;
    }

    public void parsePes(Map<Integer, Stream> streams) {
        int start_code_stream_id = buffer.getInt();
        int start_code = (start_code_stream_id & 0xffffff00) >> 8;
        int stream_id = start_code_stream_id & 0xff;
        int packet_length = (buffer.getShort() & 0xffff);
        int pes_optional_header = (buffer.getShort() & 0xffff);
        int pes_optional_header_length = (buffer.get() & 0xff);
        int target_position = buffer.position() + pes_optional_header_length;

        // check for PTS and DTS
        switch (pes_optional_header & 0xc0) {
            case 0x80:
                // Only PTS
                if (streams.containsKey(pid())) {
                    long pts = pts();

                    Stream tsStream = streams.get(pid());
                    if (tsStream.first_pts < 0) {
                        tsStream.first_pts = pts;
                    }

                    if (tsStream.pts < pts) {
                        tsStream.last_pts = tsStream.pts;
                    }

                    tsStream.pts = pts;
                    if ((tsStream.pts - tsStream.last_pts) < 0) {
                        System.out.println("wat!");
                    }
                }
                break;
            case 0xc0:
                // PTS and DTS
                System.out.println("yarr!");
                break;
        }

        // System.out.println(target_position + "," + buffer.position());
        buffer.position(target_position);
    }
}
