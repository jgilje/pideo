package net.jgilje.pideo;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import net.jgilje.pideo.h264.BitStream;
import net.jgilje.pideo.h264.SPS;
import net.jgilje.pideo.ts.Demuxer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * Created by jgilje on 12/27/15.
 */
class Player extends Thread {
    /**
     * Log tag
     */
    private static final String TAG = "Player";

    private static final String MIMETYPE_VIDEO_AVC = "video/avc";

    private Demuxer demuxer = new Demuxer();
    private final Surface surface;

    private MediaCodec codec;
    private ByteBuffer[] inputBuffers;
    private MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

    private String serverHost;
    private int serverPort;
    private EventListener eventListener;

    Player(Surface surface, String serverHost, int serverPort) {
        super("Pideo.PlayerThread");
        this.surface = surface;
        this.serverHost = serverHost;
        this.serverPort = serverPort;
    }

    void setEventListener(EventListener eventListener) {
        this.eventListener = eventListener;
    }

    interface EventListener {
        void onSync();
        void onFailedToConnect();
        void onConnectionFailed();
    }

    private void queueInputBuffer(int flags) throws IOException {
        int inputBufferIndex = codec.dequeueInputBuffer(10000);
        if (inputBufferIndex >= 0) {
            ByteBuffer buffer = inputBuffers[inputBufferIndex];
            buffer.clear();

            demuxer.fetchPid(buffer);
            long presentationTime = demuxer.sampleTime();

            codec.queueInputBuffer(inputBufferIndex, 0, buffer.position(), presentationTime, flags);
        }
    }

    @Override
    public void run() {
        SocketChannel socketChannel;
        try {
            socketChannel = SocketChannel.open(new InetSocketAddress(serverHost, serverPort));
        } catch (Throwable e) {
            if (eventListener != null) {
                eventListener.onFailedToConnect();
            }

            return;
        }

        try {
            demuxer.selectPid(65);
            BitStream.syncStream(demuxer, socketChannel);
            Log.d(TAG, "Stream is sync'd");
            if (eventListener != null) {
                eventListener.onSync();
            }

            int width = 1280;
            int height = 720;
            Object o = BitStream.parse(demuxer);
            if (o != null) {
                if (o instanceof SPS) {
                    SPS sps = (SPS) o;
                    width = sps.width();
                    height = sps.height();
                } else {
                    Log.w(TAG, "Expected SPS packet from BitStream parser");
                }
            } else {
                Log.w(TAG, "BitStream parser failed to parse");
            }

            MediaFormat format = new MediaFormat();
            format.setString(MediaFormat.KEY_MIME, MIMETYPE_VIDEO_AVC); // MediaFormat.MIMETYPE_VIDEO_AVC is not available before API 21
            format.setInteger(MediaFormat.KEY_WIDTH, width);
            format.setInteger(MediaFormat.KEY_HEIGHT, height);

            try {
                codec = MediaCodec.createDecoderByType(MIMETYPE_VIDEO_AVC);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            codec.configure(format, surface, null, 0);
            codec.start();

            inputBuffers = codec.getInputBuffers();
            queueInputBuffer(MediaCodec.BUFFER_FLAG_CODEC_CONFIG);

            while (! Thread.interrupted()) {
                queueInputBuffer(0);

                int outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 1000);
                switch (outputBufferIndex) {
                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                        break;
                    case MediaCodec.INFO_TRY_AGAIN_LATER:
                        break;
                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                        break;
                    default:
                        codec.releaseOutputBuffer(outputBufferIndex, true);
                        break;
                }
            }

            demuxer.stop();
            socketChannel.close();

            while (! socketChannel.socket().isClosed()) {
                socketChannel.socket().close();
            }
        } catch (IOException | InterruptedException e) {
            if (eventListener != null) {
                eventListener.onConnectionFailed();
            }
        }
    }
}
