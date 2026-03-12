package com.bilibili.video.service;

import com.bilibili.video.model.dto.DanmuDTO;
import com.bilibili.video.model.vo.DanmuVO;

import java.util.List;

/**
 * 弹幕服务接口
 */
public interface DanmuService {

    void saveDanmu(DanmuDTO dto);

    List<DanmuVO> listByVideoId(Long videoId);
}
