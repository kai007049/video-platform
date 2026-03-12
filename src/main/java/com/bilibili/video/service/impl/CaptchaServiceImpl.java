package com.bilibili.video.service.impl;

import com.bilibili.video.common.Constants;
import com.bilibili.video.exception.BizException;
import com.bilibili.video.model.vo.CaptchaVO;
import com.bilibili.video.service.CaptchaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class CaptchaServiceImpl implements CaptchaService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public CaptchaVO createCaptcha() {
        String captchaValue = generateCaptchaText();
        String captchaKey = UUID.randomUUID().toString().replace("-", "");
        String imageBase64 = renderCaptchaImageBase64(captchaValue);
        redisTemplate.opsForValue().set(Constants.CAPTCHA_PREFIX + captchaKey, captchaValue, Constants.CAPTCHA_EXPIRE_SECONDS, TimeUnit.SECONDS);
        return new CaptchaVO(captchaKey, imageBase64);
    }

    @Override
    public void verifyCaptcha(String captchaKey, String captchaValue) {
        String redisKey = Constants.CAPTCHA_PREFIX + captchaKey;
        Object cached = redisTemplate.opsForValue().get(redisKey);
        if (cached == null) {
            throw new BizException(400, "验证码已过期");
        }
        String cachedValue = String.valueOf(cached).toLowerCase(Locale.ROOT);
        if (!cachedValue.equals(captchaValue.trim().toLowerCase(Locale.ROOT))) {
            throw new BizException(400, "验证码错误");
        }
        redisTemplate.delete(redisKey);
    }

    private String generateCaptchaText() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            builder.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return builder.toString();
    }

    private String renderCaptchaImageBase64(String text) {
        int width = 110;
        int height = 40;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setFont(new Font("Arial", Font.BOLD, 22));
        g.setColor(Color.BLACK);
        g.drawString(text, 18, 28);
        g.dispose();

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", outputStream);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            throw new BizException(500, "验证码生成失败");
        }
    }
}
