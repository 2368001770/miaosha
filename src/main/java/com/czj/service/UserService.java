package com.czj.service;

import com.czj.error.BusinessException;
import com.czj.service.model.UserModel;

/**
 * @author czj
 */
public interface UserService {

    /**
     * 通过用户id获取对象
     */
    UserModel getUserById(Integer id);

    /**
     * 用户注册
     * @param userModel
     * @throws BusinessException
     */
    void register(UserModel userModel) throws BusinessException;

    UserModel validateLogin(String telphone,String encrptPassword) throws BusinessException;

}
