package net.jgilje.pideo.ts;

/**
 * Created by jgilje on 26.04.15.
 */
public class Stream {
    public int channel;
    public int type;
    public long pts = -1;
    public long last_pts = -1;
    public long first_pts = -1;
    public boolean pmt;     // if this pid has been added from PAT
}
