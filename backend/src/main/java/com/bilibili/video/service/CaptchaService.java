package com.bilibili.video.service;

import com.bilibili.video.model.vo.CaptchaVO;

public interface CaptchaService {

    CaptchaVO createCaptcha();

    void verifyCaptcha(String captchaKey, String captchaValue);
}
