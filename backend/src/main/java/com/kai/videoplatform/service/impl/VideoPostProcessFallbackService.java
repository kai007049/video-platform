package com.kai.videoplatform.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoPostProcessFallbackService {

    private final VideoCoverProcessService videoCoverProcessService;

    @Value("${video.cover.fallback-delay-ms:12000}")
    private long coverFallbackDelayMs;

    @Async("postProcessExecutor")
    public void triggerCoverProcessFallback(Long videoId) {
        try {
            if (coverFallbackDelayMs > 0) {
                Thread.sleep(coverFallbackDelayMs);
            }
            videoCoverProcessService.processByVideoId(videoId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[Async] cover fallback interrupted: videoId={}", videoId);
        } catch (Exception e) {
            log.warn("[Async] cover fallback failed: videoId={}", videoId, e);
        }
    }
}