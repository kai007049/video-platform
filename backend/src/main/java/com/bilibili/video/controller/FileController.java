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
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
@Tag(name = "文件转发")
@Slf4j
public class FileController {

    private static final String DEFAULT_COVER_OBJECT = "default/default-video-cover.png";
    private static final byte[] DEFAULT_COVER_BYTES = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 960 540">
              <defs>
                <linearGradient id="bg" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stop-color="#0f172a"/>
                  <stop offset="55%" stop-color="#1d4ed8"/>
                  <stop offset="100%" stop-color="#38bdf8"/>
                </linearGradient>
              </defs>
              <rect width="960" height="540" rx="32" fill="url(#bg)"/>
              <circle cx="760" cy="136" r="88" fill="rgba(255,255,255,0.12)"/>
              <circle cx="180" cy="428" r="120" fill="rgba(255,255,255,0.08)"/>
              <rect x="146" y="138" width="668" height="264" rx="28" fill="rgba(15,23,42,0.30)" stroke="rgba(255,255,255,0.18)" stroke-width="4"/>
              <polygon points="430,208 430,332 560,270" fill="#ffffff"/>
              <text x="146" y="462" fill="#ffffff" font-size="42" font-family="Segoe UI, Arial, sans-serif" font-weight="700">Video Cover</text>
              <text x="146" y="500" fill="rgba(255,255,255,0.82)" font-size="24" font-family="Segoe UI, Arial, sans-serif">Default placeholder image</text>
            </svg>
            """.getBytes(StandardCharsets.UTF_8);

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
            if (DEFAULT_COVER_OBJECT.equals(objectName)) {
                return ResponseEntity.ok()
                        .header(HttpHeaders.CACHE_CONTROL, "max-age=86400")
                        .contentType(MediaType.parseMediaType("image/svg+xml"))
                        .body(DEFAULT_COVER_BYTES);
            }
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

    @GetMapping("/message")
    @Operation(summary = "私信图片转发")
    public ResponseEntity<byte[]> message(@RequestParam("url") String objectName) {
        try (InputStream in = minioUtils.getMessageStreamByObjectName(objectName)) {
            byte[] bytes = in.readAllBytes();
            MediaType mediaType = resolveMediaType(objectName).orElse(MediaType.IMAGE_JPEG);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=86400")
                    .contentType(mediaType)
                    .body(bytes);
        } catch (Exception e) {
            log.warn("message image proxy failed: {}", objectName, e);
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
