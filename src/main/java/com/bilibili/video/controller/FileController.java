package com.bilibili.video.controller;

import com.bilibili.video.utils.MinioUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.util.Optional;

@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
@Tag(name = "文件转发")
@Slf4j
public class FileController {

    private final MinioUtils minioUtils;

    @GetMapping("/cover")
    @Operation(summary = "封面转发")
    public ResponseEntity<byte[]> cover(@RequestParam("url") String objectName) {
        try (InputStream in = minioUtils.getCoverStreamByObjectName(objectName)) {
            byte[] bytes = in.readAllBytes();
            MediaType mediaType = resolveMediaType(objectName).orElse(MediaType.IMAGE_JPEG);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=86400")
                    .contentType(mediaType)
                    .body(bytes);
        } catch (Exception e) {
            log.warn("cover proxy failed: {}", objectName, e);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/avatar")
    @Operation(summary = "头像转发")
    public ResponseEntity<byte[]> avatar(@RequestParam("url") String objectName) {
        try (InputStream in = minioUtils.getAvatarStreamByObjectName(objectName)) {
            byte[] bytes = in.readAllBytes();
            MediaType mediaType = resolveMediaType(objectName).orElse(MediaType.IMAGE_JPEG);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=86400")
                    .contentType(mediaType)
                    .body(bytes);
        } catch (Exception e) {
            log.warn("avatar proxy failed: {}", objectName, e);
            return ResponseEntity.notFound().build();
        }
    }

    private Optional<MediaType> resolveMediaType(String url) {
        try {
            return MediaTypeFactory.getMediaType(url);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
