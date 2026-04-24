package com.kai.videoplatform.service;

import com.kai.videoplatform.model.dto.DanmuDTO;
import com.kai.videoplatform.model.vo.DanmuVO;

import java.util.List;

/**
 * 弹幕服务接口
 */
public interface DanmuService {

    void saveDanmu(DanmuDTO dto);

    List<DanmuVO> listByVideoId(Long videoId);
}