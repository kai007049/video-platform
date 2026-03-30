package com.bilibili.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bilibili.video.entity.Danmu;
import com.bilibili.video.entity.User;
import com.bilibili.video.mapper.DanmuMapper;
import com.bilibili.video.mapper.UserMapper;
import com.bilibili.video.model.dto.DanmuDTO;
import com.bilibili.video.model.vo.DanmuVO;
import com.bilibili.video.model.mq.DanmuMessage;
import com.bilibili.video.model.mq.NotifyMessage;
import com.bilibili.video.model.mq.SearchSyncMessage;
import com.bilibili.video.service.DanmuService;
import com.bilibili.video.service.MQService;
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
    private final MQService mqService;

    @Override
    public void saveDanmu(DanmuDTO dto) {
        Danmu danmu = new Danmu();
        danmu.setVideoId(dto.getVideoId());
        danmu.setUserId(dto.getUserId());
        danmu.setContent(dto.getContent());
        danmu.setTimePoint(dto.getTimePoint() != null ? dto.getTimePoint() : 0);
        danmuMapper.insert(danmu);

        mqService.sendDanmu(new DanmuMessage(danmu.getVideoId(), danmu.getUserId(), danmu.getContent(), danmu.getTimePoint()));
        mqService.sendNotify(new NotifyMessage("danmu", danmu.getUserId(), danmu.getVideoId(), danmu.getContent()));
        mqService.sendSearchSync(new SearchSyncMessage("danmu", danmu.getId(), "create"));
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
