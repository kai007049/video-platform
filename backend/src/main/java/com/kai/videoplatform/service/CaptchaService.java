package com.kai.videoplatform.service;

import com.kai.videoplatform.model.vo.CaptchaVO;

public interface CaptchaService {

    CaptchaVO createCaptcha();

    void verifyCaptcha(String captchaKey, String captchaValue);
}