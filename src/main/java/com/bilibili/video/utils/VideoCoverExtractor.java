package com.bilibili.video.utils;

import com.bilibili.video.config.FfmpegConfig;
import com.bilibili.video.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class VideoCoverExtractor {

    private final MinioUtils minioUtils;
    private final FfmpegConfig ffmpegConfig;
    /**
     * 截取视频封面
     * @param videoUrl
     * @return
     */
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
            double captureTime = 0.0;
            runFfmpeg(videoFile, coverFile, captureTime);

            return minioUtils.uploadCoverFile(coverFile, "image/jpeg");
        } catch (Exception e) {
            return null;
        } finally {
            if (videoFile != null) videoFile.delete();
            if (coverFile != null) coverFile.delete();
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
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[ffmpeg] " + line);
            }
        }

        int code = process.waitFor();
        if (code != 0) {
            throw new BizException(500, "ffmpeg 截图失败");
        }
    }
}
