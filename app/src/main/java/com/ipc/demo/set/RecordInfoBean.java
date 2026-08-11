package com.ipc.demo.set;

import com.thingclips.smart.camera.middleware.cloud.bean.TimePieceBean;

import java.util.List;

/**
 * Playback day record payload parsed from queryRecordTimeSliceByDay.
 */
public class RecordInfoBean {

    private int count;
    private List<TimePieceBean> items;

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public List<TimePieceBean> getItems() {
        return items;
    }

    public void setItems(List<TimePieceBean> items) {
        this.items = items;
    }
}
