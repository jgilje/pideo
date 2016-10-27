package net.jgilje.pideo.ts;

/**
 * Created by jgilje on 26.04.15.
 */
class Stream {
    int channel;
    int type;
    long pts = -1;
    long last_pts = -1;
    long first_pts = -1;
    boolean pmt;     // if this pid has been added from PAT
}
