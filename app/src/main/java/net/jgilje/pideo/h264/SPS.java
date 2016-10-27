package net.jgilje.pideo.h264;

/**
 * Created by jgilje on 03.02.16.
 */
public class SPS {
    int profile_idc;
    byte constraint_set;
    byte level_idc;
    int seq_parameter_set_id;

    int chroma_format_idc;
    int bit_depth_luma_minus8;
    int bit_depth_chroma_minus8;
    byte qpprime_y_zero_transform_bypass_flag;
    byte seq_scaling_matrix_present_flag;

    int log2_max_frame_num_minus4;
    int pic_order_cnt_type;
    byte delta_pic_order_always_zero_flag;
    int offset_for_non_ref_pic;
    int offset_for_top_to_bottom_field;
    int num_ref_frames_in_pic_order_cnt_cycle;
    int offset_for_ref_frame; // actually array of num_ref_frames_in_pic_order_cnt_cycle

    int max_num_ref_frames;
    byte gaps_in_frame_num_value_allowed_flag;
    int pic_width_in_mbs_minus1;
    int pic_height_in_map_units_minus1;
    byte frame_mbs_only_flag;
    byte mb_adaptive_frame_field_flag;
    byte direct_8x8_inference_flag;

    byte frame_cropping_flag;
    int frame_crop_left_offset;
    int frame_crop_right_offset;
    int frame_crop_top_offset;
    int frame_crop_bottom_offset;

    byte vui_parameters_present_flag;

    public int width() {
        return ((pic_width_in_mbs_minus1 + 1) * 16) - frame_crop_left_offset * 2 - frame_crop_right_offset * 2;
    }

    public int height() {
        return ((2 - frame_mbs_only_flag) * (pic_height_in_map_units_minus1 +1) * 16) - (frame_crop_top_offset * 2) - (frame_crop_bottom_offset * 2);
    }
}
