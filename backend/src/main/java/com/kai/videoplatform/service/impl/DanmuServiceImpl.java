package com.kai.videoplatform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kai.videoplatform.entity.Danmu;
import com.kai.videoplatform.entity.User;
import com.kai.videoplatform.mapper.DanmuMapper;
import com.kai.videoplatform.mapper.UserMapper;
import com.kai.videoplatform.mapper.VideoMapper;
import com.kai.videoplatform.model.dto.DanmuDTO;
import com.kai.videoplatform.model.mq.DanmuMessage;
import com.kai.videoplatform.model.mq.NotifyMessage;
import com.kai.videoplatform.model.mq.SearchSyncMessage;
import com.kai.videoplatform.model.vo.DanmuVO;
import com.kai.videoplatform.service.DanmuService;
import com.kai.videoplatform.service.MQService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DanmuServiceImpl implements DanmuService {

    private final DanmuMapper danmuMapper;
    private final UserMapper userMapper;
    private final VideoMapper videoMapper;
    private final MQService mqService;

    @Override
    public void saveDanmu(DanmuDTO dto) {
        Danmu danmu = new Danmu();
        danmu.setVideoId(dto.getVideoId());
        danmu.setUserId(dto.getUserId());
        danmu.setContent(dto.getContent());
        danmu.setTimePoint(dto.getTimePoint() != null ? dto.getTimePoint() : 0);
        danmuMapper.insert(danmu);
        dto.setId(danmu.getId());

        DanmuMessage danmuMessage = new DanmuMessage(danmu.getVideoId(), danmu.getUserId(), danmu.getContent(), danmu.getTimePoint());
        danmuMessage.setBizKey("danmu:video:" + danmu.getVideoId() + ":user:" + danmu.getUserId() + ":" + danmu.getTimePoint());
        mqService.sendDanmu(danmuMessage);
        var video = dto.getVideoId() == null ? null : videoMapper.selectById(dto.getVideoId());
        if (video != null && video.getAuthorId() != null && !video.getAuthorId().equals(danmu.getUserId())) {
            NotifyMessage notifyMessage = new NotifyMessage("danmu", video.getAuthorId(), danmu.getVideoId(), danmu.getContent());
            notifyMessage.setBizKey("notify:user:" + video.getAuthorId() + ":danmu:" + danmu.getId());
            mqService.sendNotify(notifyMessage);
        }
        SearchSyncMessage searchSyncMessage = new SearchSyncMessage("danmu", danmu.getId(), "create");
        searchSyncMessage.setBizKey("search:danmu:" + danmu.getId() + ":create");
        mqService.sendSearchSync(searchSyncMessage);
    }

    @Override
    public List<DanmuVO> listByVideoId(Long videoId) {
        List<Danmu> list = danmuMapper.selectList(
                new LambdaQueryWrapper<Danmu>()
                        .eq(Danmu::getVideoId, videoId)
                        .orderByAsc(Danmu::getTimePoint)
                        .orderByAsc(Danmu::getCreateTime));
        if (list.isEmpty()) return new ArrayList<>();
        List<Long> userIds = list.stream().map(Danmu::getUserId).distinct().collect(Collectors.toList());
        Map<Long, String> userNames = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u.getUsername() != null ? u.getUsername() : "用户"));
        List<DanmuVO> result = new ArrayList<>();
        for (Danmu d : list) {
            DanmuVO vo = new DanmuVO();
            BeanUtils.copyProperties(d, vo);
            vo.setUsername(userNames.getOrDefault(d.getUserId(), "用户"));
            result.add(vo);
        }
        return result;
    }
}