package com.kai.videoplatform.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class FfmpegConfig {
    @Value("${ffmpeg.path}")
    private String ffmpegPath;
}