package com.bilibili.video.controller;

import com.bilibili.video.model.vo.VideoVO;
import com.bilibili.video.service.VideoService;
import com.bilibili.video.service.WatchHistoryService;
import com.bilibili.video.utils.MinioUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoControllerStreamRangeTest {

    @Mock
    private VideoService videoService;
    @Mock
    private MinioUtils minioUtils;
    @Mock
    private WatchHistoryService watchHistoryService;

    private VideoController controller;

    @BeforeEach
    void setUp() {
        controller = new VideoController(videoService, minioUtils, watchHistoryService);
    }

    @Test
    void shouldReturnPartialContentWhenRangeHeaderPresent() throws Exception {
        VideoVO video = new VideoVO();
        video.setId(1L);
        video.setVideoUrl("http://minio.test/video/demo.mp4");
        when(videoService.getById(1L, null)).thenReturn(video);
        when(minioUtils.getVideoSize(video.getVideoUrl())).thenReturn(10L);
        when(minioUtils.getVideoStream(eq(video.getVideoUrl()), eq(2L), eq(4L)))
                .thenReturn(new ByteArrayInputStream("2345".getBytes(StandardCharsets.UTF_8)));

        ResponseEntity<StreamingResponseBody> response = controller.stream(1L, "bytes=2-5");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(response.getHeaders().getFirst("Accept-Ranges")).isEqualTo("bytes");
        assertThat(response.getHeaders().getFirst("Content-Range")).isEqualTo("bytes 2-5/10");
        assertThat(response.getHeaders().getFirst("Content-Length")).isEqualTo("4");
        assertThat(writeBody(response.getBody())).isEqualTo("2345".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void shouldReturnFullContentWhenRangeHeaderMissing() throws Exception {
        VideoVO video = new VideoVO();
        video.setId(1L);
        video.setVideoUrl("http://minio.test/video/demo.mp4");
        when(videoService.getById(1L, null)).thenReturn(video);
        when(minioUtils.getVideoSize(video.getVideoUrl())).thenReturn(10L);
        when(minioUtils.getVideoStream(video.getVideoUrl()))
                .thenReturn(new ByteArrayInputStream("0123456789".getBytes(StandardCharsets.UTF_8)));

        ResponseEntity<StreamingResponseBody> response = controller.stream(1L, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("Accept-Ranges")).isEqualTo("bytes");
        assertThat(response.getHeaders().getFirst("Content-Length")).isEqualTo("10");
        assertThat(writeBody(response.getBody())).isEqualTo("0123456789".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void shouldIgnoreWindowsClientAbortDuringStreaming() throws Exception {
        VideoVO video = new VideoVO();
        video.setId(1L);
        video.setVideoUrl("http://minio.test/video/demo.mp4");
        when(videoService.getById(1L, null)).thenReturn(video);
        when(minioUtils.getVideoSize(video.getVideoUrl())).thenReturn(10L);
        when(minioUtils.getVideoStream(video.getVideoUrl()))
                .thenReturn(new ClientAbortInputStream("你的主机中的软件中止了一个已建立的连接。"));

        ResponseEntity<StreamingResponseBody> response = controller.stream(1L, null);

        assertThatCode(() -> writeBody(response.getBody())).doesNotThrowAnyException();
    }

    private byte[] writeBody(StreamingResponseBody body) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        body.writeTo(outputStream);
        return outputStream.toByteArray();
    }

    private static final class ClientAbortInputStream extends ByteArrayInputStream {
        private final String message;

        private ClientAbortInputStream(String message) {
            super("0123456789".getBytes(StandardCharsets.UTF_8));
            this.message = message;
        }

        @Override
        public long transferTo(java.io.OutputStream out) throws IOException {
            throw new IOException(message);
        }
    }
}
