package com.czj.controller;

import com.alibaba.druid.util.StringUtils;
import com.czj.controller.viewobject.UserVO;
import com.czj.error.BusinessException;
import com.czj.error.EmBusinessError;
import com.czj.response.CommonReturnType;
import com.czj.service.UserService;
import com.czj.service.model.UserModel;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import sun.misc.BASE64Encoder;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

@Controller("user")
@RequestMapping("/user")
@CrossOrigin(origins = {"*"}, allowCredentials = "true")
public class UserController extends BaseController{

    @Autowired
    private UserService userService;

    /**
     * 单例
     * 内部 threadLocal (线程安全)
     */
    @Autowired
    private HttpServletRequest request;

    @RequestMapping("/get")
    @ResponseBody
    public CommonReturnType getUser(@RequestParam(name = "id") Integer id) throws BusinessException{
        UserModel userModel = userService.getUserById(id);
        if(userModel == null){
            throw new BusinessException(EmBusinessError.USER_NOT_EXIST);
        }
        return CommonReturnType.create(converFromModel(userModel));
    }

    /**
     * 用户注册接口
     */
    @RequestMapping(value = "/register", method = {RequestMethod.POST}, consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType register(@RequestParam(name = "telphone") String telphone,
                                     @RequestParam(name = "otpCode") String otpCode,
                                     @RequestParam(name = "name") String name,
                                     @RequestParam(name = "gender") Integer gender,
                                     @RequestParam(name = "age") Integer age,
                                     @RequestParam(name="password")String password) throws BusinessException, UnsupportedEncodingException, NoSuchAlgorithmException {
        //验证手机号和对应otpcode相符
        String inSessionOtpCode = (String) this.request.getSession().getAttribute(telphone);
        if (!StringUtils.equals(otpCode, inSessionOtpCode)) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "短信验证码不符合");
        }
        //用户注册流程
        UserModel userModel = new UserModel();
        userModel.setName(name);
        userModel.setGender(new Byte(String.valueOf(gender.intValue())));
        userModel.setAge(age);
        userModel.setTelphone(telphone);
        userModel.setRegisterMode("byphone");
        userModel.setEncrptPassword(this.enCodeByMD5(password));

        userService.register(userModel);
        return CommonReturnType.create(null);

    }

    /**
     * 用户登录接口
     *
     * @param telphone
     * @param password
     * @return
     * @throws BusinessException
     * @throws UnsupportedEncodingException
     * @throws NoSuchAlgorithmException
     */
    @RequestMapping(value = "/login",method = {RequestMethod.POST},consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType login(@RequestParam(name = "telphone")String telphone,@RequestParam(value = "password")String password) throws BusinessException, UnsupportedEncodingException, NoSuchAlgorithmException {

        // 输入参数校验
        if(org.apache.commons.lang3.StringUtils.isEmpty(telphone) || org.apache.commons.lang3.StringUtils.isEmpty(password)){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR);
        }

        //用户信息合法校验
        UserModel userModel = userService.validateLogin(telphone,this.enCodeByMD5(password));

        //将登录凭证加入到合法用户的session中
        this.request.getSession().setAttribute("IS_LOGIN",true);
        this.request.getSession().setAttribute("LOGIN_USER",userModel);

        return CommonReturnType.create(null);
    }


    /**
     * 使用 MD5 算法加密
     * @param str
     * @return
     * @throws NoSuchAlgorithmException
     * @throws UnsupportedEncodingException
     */
    public String enCodeByMD5(String str) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        // 确定计算方法
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        BASE64Encoder base64Encoder = new BASE64Encoder();
        // 加密字符串
        String newStr = base64Encoder.encode(md5.digest(str.getBytes("utf-8")));
        return newStr;
    }

    /**
     * @author czj
     * 用户获取otp短信接口
     */
    @RequestMapping(value = "/getotp", method = {RequestMethod.POST}, consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType getOtp(@RequestParam(name = "telphone") String telphone){

        //按照一定规则生成OTP验证码
        Random random = new Random();
        int randomInt = random.nextInt(99999);
        randomInt += 10000;
        String otpCode = String.valueOf(randomInt);

        //将OTP验证码同对应的用户的手机号关联，使用HTTP session的方式绑定(redis适用)
        request.getSession().setAttribute(telphone,otpCode);

        //将OtP验证码通过短信通道发送给用户（省略）
        System.out.println("telphone = " + telphone + "  &otpCode = " + otpCode);
        return CommonReturnType.create(null);
    }


    /**
     * 将核心领域模型用户对象转换为可供UI使用的viewobject
     * @param userModel
     * @return
     */
    private UserVO converFromModel(UserModel userModel){
        if(userModel == null){
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(userModel,userVO);
        return userVO;
    }


}
