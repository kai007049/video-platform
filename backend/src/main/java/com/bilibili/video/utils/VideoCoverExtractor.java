package com.bilibili.video.utils;

import com.bilibili.video.config.FfmpegConfig;
import com.bilibili.video.exception.BizException;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;

@Schema(description = "Video cover extraction helper")
@Component
@RequiredArgsConstructor
@Slf4j
public class VideoCoverExtractor {

    private final MinioUtils minioUtils;
    private final FfmpegConfig ffmpegConfig;

    public String extractAndUploadCover(String videoUrl) {
        File videoFile = null;
        File coverFile = null;
        try {
            videoFile = Files.createTempFile("video_", ".mp4").toFile();
            try (InputStream input = minioUtils.getVideoStream(videoUrl);
                 FileOutputStream output = new FileOutputStream(videoFile)) {
                input.transferTo(output);
            }

            coverFile = Files.createTempFile("cover_", ".jpg").toFile();
            runFfmpeg(videoFile, coverFile, 0.0);
            return minioUtils.uploadCoverFile(coverFile, "image/jpeg");
        } catch (Exception e) {
            log.warn("extractAndUploadCover failed: videoUrl={}", videoUrl, e);
            return null;
        } finally {
            if (videoFile != null) {
                videoFile.delete();
            }
            if (coverFile != null) {
                coverFile.delete();
            }
        }
    }

    public Integer extractDurationSeconds(String videoUrl) {
        File videoFile = null;
        try {
            videoFile = Files.createTempFile("video_", ".mp4").toFile();
            try (InputStream input = minioUtils.getVideoStream(videoUrl);
                 FileOutputStream output = new FileOutputStream(videoFile)) {
                input.transferTo(output);
            }
            return probeDurationSeconds(videoFile);
        } catch (Exception e) {
            log.warn("extractDurationSeconds failed: videoUrl={}", videoUrl, e);
            return null;
        } finally {
            if (videoFile != null) {
                videoFile.delete();
            }
        }
    }

    private Integer probeDurationSeconds(File videoFile) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(
                ffmpegConfig.getFfmpegPath(),
                "-i", videoFile.getAbsolutePath()
        );
        builder.redirectErrorStream(true);
        Process process = builder.start();

        Integer seconds = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("Duration:")) {
                    seconds = parseDurationSeconds(line);
                }
            }
        }

        int code = process.waitFor();
        if (code != 0 && seconds == null) {
            throw new BizException(500, "ffmpeg failed to parse duration");
        }
        return seconds;
    }

    private Integer parseDurationSeconds(String line) {
        int idx = line.indexOf("Duration:");
        if (idx < 0) {
            return null;
        }
        String part = line.substring(idx + 9);
        int comma = part.indexOf(',');
        if (comma > 0) {
            part = part.substring(0, comma);
        }
        part = part.trim();
        String[] seg = part.split(":");
        if (seg.length < 3) {
            return null;
        }
        try {
            int h = Integer.parseInt(seg[0].trim());
            int m = Integer.parseInt(seg[1].trim());
            double s = Double.parseDouble(seg[2].trim());
            return (int) Math.round(h * 3600 + m * 60 + s);
        } catch (Exception e) {
            return null;
        }
    }

    private void runFfmpeg(File videoFile, File coverFile, double timeSeconds) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(
                ffmpegConfig.getFfmpegPath(),
                "-y",
                "-ss", String.valueOf(timeSeconds),
                "-i", videoFile.getAbsolutePath(),
                "-frames:v", "1",
                "-q:v", "2",
                coverFile.getAbsolutePath()
        );
        builder.redirectErrorStream(true);
        Process process = builder.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            while (reader.readLine() != null) {
                // Intentionally consume ffmpeg output to avoid process buffer blocking.
            }
        }

        int code = process.waitFor();
        if (code != 0) {
            throw new BizException(500, "ffmpeg failed to capture cover");
        }
    }
}
