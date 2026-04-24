package com.kai.videoplatform.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MybatisPlusConfigTest {

    @Test
    void shouldRegisterPaginationInterceptorForSelectPageQueries() throws Exception {
        MybatisPlusInterceptor interceptor = new MybatisPlusConfig().mybatisPlusInterceptor();

        Field interceptorsField = MybatisPlusInterceptor.class.getDeclaredField("interceptors");
        interceptorsField.setAccessible(true);
        List<?> innerInterceptors = (List<?>) interceptorsField.get(interceptor);

        assertThat(innerInterceptors)
                .anyMatch(PaginationInnerInterceptor.class::isInstance);
    }
}