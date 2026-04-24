package com.kai.videoplatform.model.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class VideoUploadDTOValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void shouldRequireVideoFile() {
        VideoUploadDTO dto = validDto();
        dto.setVideo(null);

        assertThat(validateMessages(dto)).contains("视频文件不能为空");
    }

    @Test
    void shouldRequireTitle() {
        VideoUploadDTO dto = validDto();
        dto.setTitle("   ");

        assertThat(validateMessages(dto)).contains("标题不能为空");
    }

    @Test
    void shouldRequireDescription() {
        VideoUploadDTO dto = validDto();
        dto.setDescription("   ");

        assertThat(validateMessages(dto)).contains("简介不能为空");
    }

    @Test
    void shouldRequireCategory() {
        VideoUploadDTO dto = validDto();
        dto.setCategoryId(null);

        assertThat(validateMessages(dto)).contains("分类不能为空");
    }

    @Test
    void shouldRequireAtLeastOneTag() {
        VideoUploadDTO dto = validDto();
        dto.setTagIds(new ArrayList<>());

        assertThat(validateMessages(dto)).contains("请至少选择一个标签");
    }

    private Set<String> validateMessages(VideoUploadDTO dto) {
        return validator.validate(dto).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    private VideoUploadDTO validDto() {
        VideoUploadDTO dto = new VideoUploadDTO();
        dto.setVideo(new MockMultipartFile("video", "demo.mp4", "video/mp4", new byte[]{1, 2, 3}));
        dto.setTitle("测试标题");
        dto.setDescription("测试简介");
        dto.setCategoryId(1L);
        dto.setTagIds(new ArrayList<>(List.of(1L)));
        return dto;
    }
}