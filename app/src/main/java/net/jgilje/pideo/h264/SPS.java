package net.jgilje.pideo.h264;

/**
 * Created by jgilje on 03.02.16.
 */
public class SPS {
    public int profile_idc;
    public byte constraint_set;
    public byte level_idc;
    public int seq_parameter_set_id;

    public int chroma_format_idc;
    public int bit_depth_luma_minus8;
    public int bit_depth_chroma_minus8;
    public byte qpprime_y_zero_transform_bypass_flag;
    public byte seq_scaling_matrix_present_flag;

    public int log2_max_frame_num_minus4;
    public int pic_order_cnt_type;
    public byte delta_pic_order_always_zero_flag;
    public int offset_for_non_ref_pic;
    public int offset_for_top_to_bottom_field;
    public int num_ref_frames_in_pic_order_cnt_cycle;
    public int offset_for_ref_frame; // actually array of num_ref_frames_in_pic_order_cnt_cycle

    public int max_num_ref_frames;
    public byte gaps_in_frame_num_value_allowed_flag;
    public int pic_width_in_mbs_minus1;
    public int pic_height_in_map_units_minus1;
    public byte frame_mbs_only_flag;
    public byte mb_adaptive_frame_field_flag;
    public byte direct_8x8_inference_flag;

    public byte frame_cropping_flag;
    public int frame_crop_left_offset;
    public int frame_crop_right_offset;
    public int frame_crop_top_offset;
    public int frame_crop_bottom_offset;

    public byte vui_parameters_present_flag;

    public int width() {
        return ((pic_width_in_mbs_minus1 + 1) * 16) - frame_crop_left_offset * 2 - frame_crop_right_offset * 2;
    }

    public int height() {
        return ((2 - frame_mbs_only_flag) * (pic_height_in_map_units_minus1 +1) * 16) - (frame_crop_top_offset * 2) - (frame_crop_bottom_offset * 2);
    }
}
