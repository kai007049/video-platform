package com.bilibili.video.seed;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SeedCommandRunnerTest {

    @Test
    void shouldSkipWhenSeedDisabled() throws Exception {
        SeedProperties properties = new SeedProperties();
        properties.setEnabled(false);
        SeedOrchestrator orchestrator = mock(SeedOrchestrator.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);

        new SeedCommandRunner(properties, orchestrator, context).run(new DefaultApplicationArguments(new String[0]));

        verifyNoInteractions(orchestrator);
        verifyNoInteractions(context);
    }

    @Test
    void shouldRunLogSummaryAndCloseContextWhenSeedEnabled() throws Exception {
        SeedProperties properties = new SeedProperties();
        properties.setEnabled(true);
        properties.setAppend(true);
        SeedOrchestrator orchestrator = mock(SeedOrchestrator.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        SeedGenerationSummary summary = new SeedGenerationSummary(2, 5, 9, 20, 7, 4, 3, true);
        when(orchestrator.run()).thenReturn(summary);

        Logger logger = (Logger) LoggerFactory.getLogger(SeedCommandRunner.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            new SeedCommandRunner(properties, orchestrator, context).run(new DefaultApplicationArguments(new String[0]));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        verify(orchestrator).run();
        verify(context).close();
        List<ILoggingEvent> events = appender.list;
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(events.get(0).getFormattedMessage())
                .isEqualTo("Seed generation complete: " + summary.toLogLine());
    }
}