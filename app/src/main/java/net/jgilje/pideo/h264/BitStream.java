package net.jgilje.pideo.h264;

import net.jgilje.pideo.ts.CircularBuffer;
import net.jgilje.pideo.ts.Demuxer;
import net.jgilje.pideo.ts.Packet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

/**
 * Created by jgilje on 02.02.16.
 */
public class BitStream {
    private final ByteBuffer buffer;
    int bit_position = -1;
    byte byte_buffer;

    public BitStream(ByteBuffer buffer) {
        this.buffer = buffer;
        buffer.position(0);
    }

    private void check_and_advance_buffer() {
        if (bit_position < 0) {
            byte_buffer = buffer.get();
            bit_position = 7;
        }
    }

    private byte get_bit() {
        check_and_advance_buffer();
        byte b = (byte) ((byte_buffer >> bit_position) & 0x1);
        bit_position--;
        return b;
    }

    public byte get_byte() {
        byte b = 0;
        byte current_bit = 7;
        while (current_bit >= 0) {
            b |= get_bit() << current_bit;
            current_bit--;
        }
        return b;
    }

    public int get_ue() {
        check_and_advance_buffer();
        int ret = 0;

        int i = 0;
        while (get_bit() == 0) {
            i++;
        }

        // because we consume bits in while loop above
        // the first iteration is manual
        ret |= (1 << i);
        i--;

        while (i >= 0) {
            ret |= get_bit() << i;
            i--;
        }

        return ret - 1;
    }

    public static Object parse(Demuxer demuxer) {
        ByteBuffer nal_buffer = ByteBuffer.allocate(4096);
        demuxer.peekPid(nal_buffer);

        int len = nal_buffer.position();
        byte[] array = nal_buffer.array();
        int type = ((array[4]) & 0xff) & 0x1f;

        ByteBuffer rbsp_buffer = ByteBuffer.allocate(len);
        for (int i = 5; i < len; i++) {
            if ((i + 2 < len)
                    && (array[i    ] == 0x0)
                    && (array[i + 1] == 0x0)
                    && (array[i + 2] == 0x3)) {
                rbsp_buffer.put((byte) 0x0);
                rbsp_buffer.put((byte) 0x0);
                i += 2;
            } else {
                rbsp_buffer.put(array[i]);
            }
        }

        BitStream bs = new BitStream(rbsp_buffer);
        switch (type) {
            case 7:
                return parseSPS(bs);
            default:
                return null;
        }
    }

    // http://stackoverflow.com/questions/6394874/fetching-the-dimensions-of-a-h264video-stream
    private static SPS parseSPS(BitStream bs) {
        SPS sps = new SPS();

        sps.profile_idc = bs.get_byte();
        sps.constraint_set = bs.get_byte();
        sps.level_idc = bs.get_byte();
        sps.seq_parameter_set_id = bs.get_ue();

        if (sps.profile_idc == 100 || sps.profile_idc == 110 || sps.profile_idc == 122 || sps.profile_idc == 244 ||
            sps.profile_idc ==  44 || sps.profile_idc ==  83 || sps.profile_idc ==  86 || sps.profile_idc == 118) {
            sps.chroma_format_idc = bs.get_ue();
            if (sps.chroma_format_idc == 3) {
                byte seperate_colour_plane = bs.get_bit();
            }

            sps.bit_depth_luma_minus8 = bs.get_ue();
            sps.bit_depth_chroma_minus8 = bs.get_ue();
            sps.qpprime_y_zero_transform_bypass_flag = bs.get_bit();
            sps.seq_scaling_matrix_present_flag = bs.get_bit();
            if (sps.seq_scaling_matrix_present_flag == 1) {
                for (int i = 0; i < ((sps.chroma_format_idc != 3) ? 8 : 12); i++) {
                    byte seq_scaling_list_present = bs.get_bit();
                    // don't care
                }
            }
        }

        sps.log2_max_frame_num_minus4 = bs.get_ue();
        sps.pic_order_cnt_type = bs.get_ue();
        if (sps.pic_order_cnt_type == 0) {
            int log2_max_pic_order_cnt_lsb_minus4 = bs.get_ue();
        } else if (sps.pic_order_cnt_type == 1) {
            sps.delta_pic_order_always_zero_flag = bs.get_bit();
            sps.offset_for_non_ref_pic = bs.get_ue();           // actually _se, but value is ignored
            sps.offset_for_top_to_bottom_field = bs.get_ue();   // _se, what's important is to consume bits
            sps.num_ref_frames_in_pic_order_cnt_cycle = bs.get_ue();
            for (int i = 0; i < sps.num_ref_frames_in_pic_order_cnt_cycle; i++) {
                sps.offset_for_ref_frame = bs.get_ue();         // _se
            }
        }
        sps.max_num_ref_frames = bs.get_ue();
        sps.gaps_in_frame_num_value_allowed_flag = bs.get_bit();
        sps.pic_width_in_mbs_minus1 = bs.get_ue();
        sps.pic_height_in_map_units_minus1 = bs.get_ue();

        sps.frame_mbs_only_flag = bs.get_bit();
        if (sps.frame_mbs_only_flag == 0) {
            sps.mb_adaptive_frame_field_flag = bs.get_bit();
        }

        sps.direct_8x8_inference_flag = bs.get_bit();
        sps.frame_cropping_flag = bs.get_bit();
        if (sps.frame_cropping_flag == 1) {
            sps.frame_crop_left_offset = bs.get_ue();
            sps.frame_crop_right_offset = bs.get_ue();
            sps.frame_crop_top_offset = bs.get_ue();
            sps.frame_crop_bottom_offset = bs.get_ue();
        }
        sps.vui_parameters_present_flag = bs.get_bit();

        return sps;
    }

    public static boolean syncStream(Demuxer demuxer, ReadableByteChannel channel) throws IOException {
        final int PACKET_SIZE = demuxer.packetSize();
        int retries = 0;
        ByteBuffer syncBuffer = ByteBuffer.allocate(10 * PACKET_SIZE);

        CircularBuffer.FillBuffer(channel, syncBuffer);
        syncBuffer.flip();

        while (retries < 100) {
            byte b = 0;
            while (b != Packet.SYNC_BYTE && syncBuffer.hasRemaining()) {
                b = syncBuffer.get();
            }
            int position = syncBuffer.position() - 1;
            int remaining = syncBuffer.remaining() + 1;

            if (remaining < (4 * PACKET_SIZE)) {
                syncBuffer.clear();

                CircularBuffer.FillBuffer(channel, syncBuffer);
                syncBuffer.flip();

                retries++;
                continue;
            }

            byte[] array = syncBuffer.array();
            if (array[position                     ] == Packet.SYNC_BYTE &&
                    array[position +     PACKET_SIZE] == Packet.SYNC_BYTE &&
                    array[position + 2 * PACKET_SIZE] == Packet.SYNC_BYTE &&
                    array[position + 3 * PACKET_SIZE] == Packet.SYNC_BYTE &&
                    array[position + 4 * PACKET_SIZE] == Packet.SYNC_BYTE) {

                syncBuffer.position(position);
                while (syncBuffer.remaining() > PACKET_SIZE) {
                    syncBuffer.position(syncBuffer.position() + PACKET_SIZE);
                }
                int missingBytes = PACKET_SIZE - syncBuffer.remaining();
                syncBuffer.clear();
                syncBuffer.limit(missingBytes);
                CircularBuffer.FillBuffer(channel, syncBuffer);

                demuxer.start(channel);
                ByteBuffer readerBuffer = demuxer.readBuffer();

                while (true) {
                    Packet demuxPacket = demuxer.next();

                    if (demuxPacket.hasPayload() && readerBuffer.remaining() >= 5) {
                        int sync = readerBuffer.getInt(readerBuffer.position());

                        // when we reach an SPS packet, we're good
                        // although we should verify PPS (== 8) comes next
                        if (sync == 0x1) {
                            int type = (readerBuffer.get(readerBuffer.position() + 4) & 0xff) & 0x1f;

                            while (readerBuffer.remaining() > 4 && type != 7) {
                                sync = 0;

                                while (readerBuffer.remaining() > 4 && sync != 0x1) {
                                    readerBuffer.position(readerBuffer.position() + 1);
                                    sync = readerBuffer.getInt(readerBuffer.position());
                                }

                                if (readerBuffer.remaining() > 4) {
                                    type = (readerBuffer.get(readerBuffer.position() + 4) & 0xff) & 0x1f;
                                }
                            }

                            if (sync == 0x1 && type == 7) {
                                break;
                            }
                        }
                    }

                    demuxer.advance();
                }

                return true;
            }
        }

        return false;
    }
}
