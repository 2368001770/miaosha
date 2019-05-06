package com.czj;

import com.czj.dao.UserDOMapper;
import com.czj.dataobject.UserDO;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hello world!
 *
 */
//@EnableAutoConfiguration
@SpringBootApplication(scanBasePackages = {"com.czj"})
@MapperScan("com.czj.dao")
@RestController
public class App 
{

    @Autowired
    private UserDOMapper userDOMapper;

    @RequestMapping("/")
    public String home(){
        UserDO userDO = userDOMapper.selectByPrimaryKey(1);
        if(userDO == null){
            return "用户对象不存在";
        }else {
            return userDO.getName();
        }
    }

    @RequestMapping(value = "/testparam1" ,method = {RequestMethod.GET})
    public String home(@RequestParam(name = "id",required = true)Integer id){
        return String.valueOf(id);
    }

    @RequestMapping(value = "/testparam2" ,method = {RequestMethod.GET})
    public String home(@RequestParam(name = "id",required = true)String id){
        return id;
    }

    public static void main( String[] args ) {
        System.out.println("Hello World!");
        SpringApplication.run(App.class, args);
    }
}
