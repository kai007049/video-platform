package com.bilibili.video.controller;

import com.bilibili.video.service.VideoService;
import com.bilibili.video.utils.MinioUtils;
import com.bilibili.video.utils.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VideoController.class)
@Import(com.bilibili.video.exception.GlobalExceptionHandler.class)
class VideoControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VideoService videoService;

    @MockBean
    private MinioUtils minioUtils;

    @MockBean
    private com.bilibili.video.service.WatchHistoryService watchHistoryService;

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    @Test
    void upload_shouldRejectTooLongTitleWithUnifiedResult() throws Exception {
        UserContext.set(100L);
        MockMultipartFile video = new MockMultipartFile(
                "video",
                "demo.mp4",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "video-content".getBytes()
        );

        mockMvc.perform(multipart("/video/upload")
                        .file(video)
                        .param("title", "a".repeat(257)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("标题长度不能超过256个字符"));

        verifyNoInteractions(videoService);
    }

    @Test
    void upload_shouldRejectTooLongDescriptionWithUnifiedResult() throws Exception {
        UserContext.set(100L);
        MockMultipartFile video = new MockMultipartFile(
                "video",
                "demo.mp4",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "video-content".getBytes()
        );

        mockMvc.perform(multipart("/video/upload")
                        .file(video)
                        .param("description", "b".repeat(2001)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("简介长度不能超过2000个字符"));

        verifyNoInteractions(videoService);
    }

    @Test
    void upload_shouldRejectTooManyTagIdsWithUnifiedResult() throws Exception {
        UserContext.set(100L);
        MockMultipartFile video = new MockMultipartFile(
                "video",
                "demo.mp4",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "video-content".getBytes()
        );

        var requestBuilder = multipart("/video/upload").file(video);
        for (int i = 1; i <= 11; i++) {
            requestBuilder.param("tagIds", String.valueOf(i));
        }

        mockMvc.perform(requestBuilder)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("标签数量不能超过10个"));

        verifyNoInteractions(videoService);
    }
}
