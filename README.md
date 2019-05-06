# SpringBoot构建电商基础秒杀项目
标签（空格分隔）： 未分类

---
## #项目简介

通过SpringBoot快速搭建的前后端分离的电商基础秒杀项目。项目通过应用领域驱动型的分层模型设计方式去完成：用户otp注册、登陆、查看、商品列表、进入商品详情以及倒计时秒杀开始后下单购买的基本流程。

## #项目要点
* 使用配置mybatis自动生成器来生成文件，在mybatis-generator.xml配置文件中在对应生成表类名配置中加入 enableCountByExample="false"enableUpdateByExample="false" enableDeleteByExample="false" enableSelectByExample="false"selectByExampleQueryId="false" 避免生成不常用方法
 
* 使用LocalDatTime、joda-time类库代替java.util.date

* 前端 ajax 调用接口获取验证码 html/getotp.html，出现跨域请求问题 解决方法：@CrossOrigin(origins = {"*"}, allowCredentials = "true") allowedHeaders 允许前端将 token 放入 header 做 session 共享的跨域请求。 allowCredentials 授信后，需前端也设置 xhfFields 授信才能实现跨域 session 共享。 xhrFields: {withCredentials: true},
 

* 统一前端返回格式CommonReturnType {status: xx ,object:xx} dataobject -> 与数据库对应的映射对象 model -> 用于业务逻辑service的领域模型对象 viewobject -> 用于前端交互的模型对象
 
* 使用 hibernate-validator 通过注解来完成模型参数校验
 
* insertSelective 中设置 keyProperty="id" useGeneratedKeys="true" 使得插入完后的 DO 生成自增 id 。 insertSelective与insert区别： insertSelective对应的sql语句加入了NULL校验，即只会插入数据不为null的字段值（null的字段依赖于数据库字段默认值）insert则会插入所有字段，会插入null。
 

* 数据库设计规范，设计时字段要设置为not null，并设置默认值，避免唯一索引在null情况下失效等类似场景

* 解决如果事务createorder下单如果回滚，该下单方法中获得流水号id回滚，使等到的id号可能再一次被使用 在generatorOrderNo方法前加注解： @Transactional(propagation = Propagation.REQUIRES_NEW)
 
* 使用聚合模型在itemModel加入PromoModel promoModel，若不为空表示其有未结束的秒杀活动；在orderModel中加入promoId，若不为空，则以秒杀方式下单

##1、应用SpringBoot完成基础项目搭建
###1-1 使用IDEA创建maven项目

> * 新建项目，选择maven->maven-archetype-quickstart(以jar包的方式提供对外的统一输出)
> * 设置项目的和目录结构sources root以及resources root等

###1-2 引入SpringBoot依赖包实现简单的Web项目
查看[SpringBoot官方文档][1]为项目添加maven依赖
 
     <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>2.0.5.RELEASE</version>
      </parent>
      <dependencies>
        <dependency>
          <groupId>org.springframework.boot</groupId>
          <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
在项目的启动类上添加@EnableAutoConfiguration开启自动化配置（后面需做修改）
###1-3 mybatis接入SpringBoot项目
添加mysql依赖、数据库连接池依赖、SpringBoot对mybatis的支持依赖

    <!--mysql驱动-->
    <dependency>
        <groupId>mysql</groupId>
        <artifactId>mysql-connector-java</artifactId>
        <version>5.1.37</version>
    </dependency>
    <!--数据库连接池-->
    
    <dependency>
      <groupId>com.alibaba</groupId>
      <artifactId>druid</artifactId>
      <version>1.1.3</version>
    </dependency>
    
    <!--Springboot对mybatis的支持-->
    <dependency>
      <groupId>org.mybatis.spring.boot</groupId>
      <artifactId>mybatis-spring-boot-starter</artifactId>
      <version>1.3.1</version>
    </dependency>
  
在resources目录下建立application.properties文件和mapping目录，在application.properties文件里添加

    mybatis.mapperLocations=classpath:mapping/*.xml

pom文件里引入mybatis自动生成器的插件
 

            <!--配置mybatis自动生成器-->
            <plugin>
              <groupId>org.mybatis.generator</groupId>
              <artifactId>mybatis-generator-maven-plugin</artifactId>
              <version>1.3.5</version>
              <dependencies>
                <dependency>
                  <groupId>org.mybatis.generator</groupId>
                  <artifactId>mybatis-generator-core</artifactId>
                  <version>1.3.5</version>
                </dependency>
                <dependency>
                  <groupId>mysql</groupId>
                  <artifactId>mysql-connector-java</artifactId>
                  <version>5.1.37</version>
                </dependency>
              </dependencies>
              <executions>
                <execution>
                  <id>mybatis.generator</id>
                  <phase>package</phase>
                  <goals>
                    <goal>generate</goal>
                  </goals>
                </execution>
              </executions>
              <configuration>
                <!--允许移动生成的文件-->
                <verbose>true</verbose>
                <!--允许自动覆盖文件-->
                <!--慎用-->
                <overwrite>false</overwrite>
                <configurationFile>
                  src/main/resources/mybatis-generator.xml
                </configurationFile>
              </configuration>
            </plugin>

###1-4 mybatis自动生成器的使用
1、创建数据库（miaosha）和user_info表、user_password表（密码应当分开存储，防止在查询用户信息时，把密码也直接查询出来，会容易被攻击和进行恶意操作）

    CREATE TABLE `user_info` (
      `id` int(11) NOT NULL AUTO_INCREMENT,
      `name` varchar(64) COLLATE utf8_unicode_ci NOT NULL DEFAULT '',
      `gender` tinyint(4) NOT NULL DEFAULT '-1' COMMENT '1为男性，2为女性',
      `age` int(11) NOT NULL DEFAULT '0',
      `telphone` varchar(255) COLLATE utf8_unicode_ci NOT NULL DEFAULT '',
      `register_mode` varchar(255) COLLATE utf8_unicode_ci NOT NULL DEFAULT '' COMMENT 'byphone,bywechat,byalipay',
      `third_party_id` varchar(64) COLLATE utf8_unicode_ci NOT NULL DEFAULT '',
      PRIMARY KEY (`id`),
      UNIQUE KEY `telphone_unique_index` (`telphone`)
    ) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci
    
    CREATE TABLE `user_password` (
      `id` int(11) NOT NULL AUTO_INCREMENT,
      `encrpt_password` varchar(128) COLLATE utf8_unicode_ci NOT NULL DEFAULT '',
      `user_id` int(11) NOT NULL DEFAULT '0',
      PRIMARY KEY (`id`)
    ) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci

2、在resources目录下创建mybatis-generator.xml(内容可从[mybatis官网][2]上面拷贝），并修改里面的内容

    <?xml version="1.0" encoding="UTF-8"?>
    <!DOCTYPE generatorConfiguration
        PUBLIC "-//mybatis.org//DTD MyBatis Generator Configuration 1.0//EN"
            "http://mybatis.org/dtd/mybatis-generator-config_1_0.dtd">
    
    <generatorConfiguration>
    
        <context id="DB2Tables" targetRuntime="MyBatis3">
            <jdbcConnection driverClass="com.mysql.jdbc.Driver"
                            connectionURL="jdbc:mysql://127.0.0.1:3306/miaosha"
                            userId="root"
                            password="root">
            </jdbcConnection>
    
            <!--生成Model/DataObject类的存放位置-->
            <javaModelGenerator targetPackage="com.czj.dataobject" targetProject="src/main/java">
                <property name="enableSubPackages" value="true"/>
                <property name="trimStrings" value="true"/>
            </javaModelGenerator>
    
            <!--生成映射文件存放位置-->
            <sqlMapGenerator targetPackage="mapping" targetProject="src/main/resources">
                <property name="enableSubPackages" value="true"/>
            </sqlMapGenerator>
    
            <!--生成Dao类存放位置-->
            <javaClientGenerator type="XMLMAPPER" targetPackage="com.czj.dao" targetProject="src/main/java">
                <property name="enableSubPackages" value="true"/>
            </javaClientGenerator>
    
            <!--生成对应表及类名-->
            <table tableName="user_info" domainObjectName="UserDO" enableCountByExample="false"
                   enableUpdateByExample="false" enableDeleteByExample="false" enableSelectByExample="false"
                   selectByExampleQueryId="false"></table>
            <table tableName="user_password" domainObjectName="UserPasswordDO" enableCountByExample="false"
                   enableUpdateByExample="false" enableDeleteByExample="false" enableSelectByExample="false"
                   selectByExampleQueryId="false"></table>

        </context>
    </generatorConfiguration>

3、在Run -> Edit Configurations添加选中maven，进行如下配置

    Name：mybatis-generator
    Command line：mybatis-generator:generate
    
运行mybatis-generator，自动生成相应文件。
同时把主启动类的@EnableAutoConfiguration注解换成@SpringBootApplication(scanBasePackages = {"com.czj"})并添加注解@MapperScan("com.czj.dao")

###1-5 配置数据库连接
在application.properties文件中添加

    spring.datasource.name=miaosha
    spring.datasource.url=jdbc:mysql://127.0.0.1:3306/miaosha
    spring.datasource.username=root
    spring.datasource.password=root
    #使用druid数据源
    spring.datasource.type=com.alibaba.druid.pool.DruidDataSource
    spring.datasource.driverClassName=com.mysql.jdbc.Driver

##2、用户模块开发
###2-1 使用SpringMVC方式开发用户信息
项目中数据对象分为三层

> * dataobject：与数据库的映射 
> * model：领域模型，业务逻辑交互的数据（位于service包）
> * viewobject：视图模型,定义返回给前端的信息（位于controller包）
```java
public class UserModel {
    private Integer id;
    private String name;
    private Byte gender;
    private Integer age;
    private String telphone;
    private String registerMode;
    private String thirdPartyId;
    private String encrptPassword;
}

public class UserVO {
    private Integer id;
    private String name;
    private Byte gender;
    private Integer age;
    private String telphone;
}
```
###2-2 定义通用的返回对象
1、新建response包，添加CommonReturnType.java,定义返回前端的通用数据格式

    public class CommonReturnType {
       
        //表明对应请求的返回处理结果“success”或“fail”
        private String status;
        //若status = success，则data内返回前端需要的json数据
        //若status = fail，则data内使用通用的错误码格式
        private Object data;
    
        public String getStatus() {
            return status;
        }
    
        public void setStatus(String status) {
            this.status = status;
        }
    
        public Object getData() {
            return data;
        }
    
        public void setData(Object data) {
            this.data = data;
        }
    
        //定义一个通用的创建方法
        public static CommonReturnType create(Object result){
            return CommonReturnType.create(result,"success");
        }
    
        public static CommonReturnType create(Object result,String status){
            CommonReturnType commonReturnType = new CommonReturnType();
            commonReturnType.setData(result);
            commonReturnType.setStatus(status);
            return commonReturnType;
        }
    }
        
2、新建error包，用于封装错误信息和异常类。添加CommonError接口、实现CommonError接口的枚举类型EmBusinessError、实现CommonError和继承Exception类的异常类BusinessException

    public interface CommonError {
    
        public int getErrCode();
    
        public String getErrMsg();
    
        public CommonError setErrMsg(String errMsg);
    }
    
    public enum  EmBusinessError implements CommonError{
    
        //通用错误类型10001
        PARAMETER_VALIDATION_ERROR(10001,"参数不合法"),
        UNKNOW_ERROR(10002,"未知错误"),
    
       //20000开头为用户相关错误
        USER_NOT_EXIST(20001,"用户不存在"),
        USER_LOGIN_FAIL(20002,"手机或密码不存在"),
        USER_NOT_LOGIN(20003,"用户还未登陆"),
    
        //30000开头为交易型错误
        STOCK_NOT_ENOUGH(30001,"库存不足")
        ;
    
        private int errCode;
        private String errMsg;
    
        private EmBusinessError(int errCode, String errMsg) {
            this.errCode = errCode;
            this.errMsg = errMsg;
        }
    
        @Override
        public int getErrCode() {
            return this.errCode;
        }
    
        @Override
        public String getErrMsg() {
            return this.errMsg;
        }
    
        @Override
        public CommonError setErrMsg(String errMsg) {
            this.errMsg = errMsg;
            return this;
        }
    }
    
    public class BusinessException extends Exception implements CommonError {
    
        private CommonError commonError;
    
        //直接接收EmBusinessError的传参用于构造业务异常
        public BusinessException(CommonError commonError) {
            super();
            this.commonError = commonError;
        }
    
        //接收自定义errMsg的方式构造业务异常（通过覆盖原本errMsg）
        public BusinessException(CommonError commonError,String errMsg){
            super();
            this.commonError = commonError;
            this.commonError.setErrMsg(errMsg);
        }
    
        @Override
        public int getErrCode() {
            return this.commonError.getErrCode();
        }
    
        @Override
        public String getErrMsg() {
            return this.commonError.getErrMsg();
        }
    
        @Override
        public CommonError setErrMsg(String errMsg) {
            this.commonError.setErrMsg(errMsg);
            return this;
        }
    }

3、在controller包下创建统一异常处理类，用于处理controller层抛出的异常（dao层和service层异常会传递到controller层），并是它被其他controller类继承。

    public class BaseController {
    
        public static final String CONTENT_TYPE_FORMED="application/x-www-form-urlencoded";
    
        @ExceptionHandler(Exception.class)
        @ResponseStatus(HttpStatus.OK)
        @ResponseBody
        public Object handlerException(HttpServletRequest request,Exception ex){
    
            /**
             * 不可以直接把 exception 对象交给 data ，否则 data 为exception反序列化的对象，也不能把EmBusinessError对象直接交给data（枚举对象序列化问题）
             */
            Map<String,Object> responseData = new HashMap<>();
    
    
            if(ex instanceof BusinessException){
                BusinessException businessException = (BusinessException) ex;
                responseData.put("errCode",businessException.getErrCode());
                responseData.put("errMsg",businessException.getErrMsg());
            }else {
                responseData.put("errCode", EmBusinessError.UNKNOW_ERROR.getErrCode());
                responseData.put("errMsg",EmBusinessError.UNKNOW_ERROR.getErrMsg());
            }
            return CommonReturnType.create(responseData,"fail");
        }
    }

###2-3 otp验证码获取
* 按照一规则生成otp验证码
* 将OTP验证码同对应的用户的手机号关联，使用HTTP session的方式绑定(redis最适用)
* 将otp验证码通过短信通道发送给用户（省略）

     @Autowired
            private HttpServletRequest request;
            
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

###2-4 getotp页面实现

    <script>
        // 页面渲染成功才可以操作
        jQuery(document).ready(function(){
    
            //绑定otp的click事件用于像后端发送获取手机验证码请求
            $("#getotp").on("click",function(){
                var telphone = $("#telphone").val();
                
                if(telphone == null || telphone == ""){
                    alert("手机号不能为空");
                    return false;
                }
                $.ajax({
                    type:"POST",
                    contentType:"application/x-www-form-urlencoded",
                    url:"http://localhost:8080/user/getotp",
                    data:{
                        "telphone":$("#telphone").val(),
                    },
                    xhrFields:{withCredentials:true},
                    success:function(data){
                        if(data.status == "success"){
                            alert("otp已经发送到了手机，请注意查收");
                            window.location.href="register.html";
                        } else {
                            alert("otp发送失败，原因为" + data.data.errMsg);
                        }
                    },
                    error:function(data){
                        alert("otp发送失败，原因为," + data.responseText);
                    }
                });
                return false;
            });
        });
    </script>

- [ ]  js函数里的return false 是因为html里的button类型是submit，避免传递给submit进行提交表单处理，也就是return false可以阻止执行默认事件行为（[参考][3]）
- [ ]  [四种常见的post提交数据对应的context-type的值][4]
- [ ]  跨域请求问题：若ajax请求于本地文件中的html，而请求的服务器为localhost上的域名，ajax的回调函数无法获取服务器返回的信息，解决方法是在controller类上加上@CrossOrigin。若要做session共享的跨域请求，需修改为@CrossOrigin(origins = {"*"}, allowCredentials = "true")，并在ajax里添加 xhrFields:{withCredentials:true}

###2-5 用户注册功能实现
* controller层
- [x] com.alibaba.druid.util.StringUtils的使用
- [x] MD5加密 
```java
    @RequestMapping(value = "/register", method = {RequestMethod.POST},         consumes = {CONTENT_TYPE_FORMED})
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
 
 
 public String enCodeByMD5(String str) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        // 确定计算方法
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        BASE64Encoder base64Encoder = new BASE64Encoder();
        // 加密字符串
        String newStr = base64Encoder.encode(md5.digest(str.getBytes("utf-8")));
        return newStr;
    }
```
* service层
- [x]  org.apache.commons.lang3.StringUtils.isEmpty的使用
- [x]  org.springframework.beans.BeanUtils.copyProperties的使用
```java
 @Transactional
    @Override
    public void register(UserModel userModel) throws BusinessException {
        if (userModel == null) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR);
        }

        if(StringUtils.isEmpty(userModel.getName())
                || userModel.getGender() == null
                || userModel.getAge() == null
                || StringUtils.isEmpty(userModel.getTelphone())){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR);
        }

        //实现model->dataobject方法
        UserDO userDO = convertFromModel(userModel);
        //使用insertSelective而不是insert,insertSelective会将对象为null的字段不进行插入，使这个字段依赖数据库的默认值。
        try {
            /**
             * 需在对应的mapper文件里声明 keyProperty="id" useGeneratedKeys="true
             * 把插入数据库后 id 的字段值赋给userDO的 id 值
             */
            userDOMapper.insertSelective(userDO);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"手机号已被注册！");
        }
        userModel.setId(userDO.getId());

        UserPasswordDO userPasswordDO = convertPasswordFromModel(userModel);
        userPasswordDOMapper.insertSelective(userPasswordDO);

    }
    
    
    private UserDO convertFromModel(UserModel userModel){
        if(userModel == null) {
            return null;
        }
        UserDO userDO = new UserDO();
        BeanUtils.copyProperties(userModel,userDO);
        return userDO;
    }
```
* dao层

>  int insertSelective(UserDO record);

     <insert id="insertSelective" parameterType="com.czj.dataobject.UserDO" keyProperty="id" useGeneratedKeys="true">
       
        insert into user_info
        <trim prefix="(" suffix=")" suffixOverrides=",">
          <if test="id != null">
            id,
          </if>
          <if test="name != null">
            name,
          </if>
          <if test="gender != null">
            gender,
          </if>
          <if test="age != null">
            age,
          </if>
          <if test="telphone != null">
            telphone,
          </if>
          <if test="registerMode != null">
            register_mode,
          </if>
          <if test="thirdPartyId != null">
            third_party_id,
          </if>
        </trim>
        <trim prefix="values (" suffix=")" suffixOverrides=",">
          <if test="id != null">
            #{id,jdbcType=INTEGER},
          </if>
          <if test="name != null">
            #{name,jdbcType=VARCHAR},
          </if>
          <if test="gender != null">
            #{gender,jdbcType=TINYINT},
          </if>
          <if test="age != null">
            #{age,jdbcType=INTEGER},
          </if>
          <if test="telphone != null">
            #{telphone,jdbcType=VARCHAR},
          </if>
          <if test="registerMode != null">
            #{registerMode,jdbcType=VARCHAR},
          </if>
          <if test="thirdPartyId != null">
            #{thirdPartyId,jdbcType=VARCHAR},
          </if>
        </trim>
      </insert>

###2-6 用户登录功能实现
* controller层
```java
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
```
* service层
```java
    @Override
    public UserModel validateLogin(String telphone, String encrptPassword) throws BusinessException {

        UserDO userDO = userDOMapper.selectByTelphone(telphone);
        if(userDO == null){
            throw new BusinessException(EmBusinessError.USER_LOGIN_FAIL);
        }
        UserPasswordDO userPasswordDO = userPasswordDOMapper.selectByUserId(userDO.getId());
        UserModel userModel = converFromDataObject(userDO,userPasswordDO);
        if(!StringUtils.equals(encrptPassword,userModel.getEncrptPassword())){
            throw new BusinessException(EmBusinessError.USER_LOGIN_FAIL);
        }
        return userModel;
    }
```
* dao层
 
> UserDOMapper.java： UserDO selectByTelphone(String telphone); 
  UserPasswordDOMapper.java： UserPasswordDO selectByUserId(Integer id);

     <select id="selectByTelphone"  resultMap="BaseResultMap">
        select
        <include refid="Base_Column_List" />
        from user_info
        where telphone = #{telphone,jdbcType=VARCHAR}
      </select>
      
      <select id="selectByUserId" parameterType="java.lang.Integer" resultMap="BaseResultMap">
      select
      <include refid="Base_Column_List" />
      from user_password
      where user_id = #{userId,jdbcType=INTEGER}
      </select>

###2-7 优化校验规则
1、引入hibernate-validator依赖
  
        <dependency>
          <groupId>org.hibernate.validator</groupId>
          <artifactId>hibernate-validator</artifactId>
          <version>6.0.14.Final</version>
        </dependency>

2、新建validator包，添加ValidationResult包装类和ValidatorImpl
```java
public class ValidationResult {

    //校验结果是否有错
    private boolean hasErrors = false;

    //存放错误信息的 map
    private Map<String,String> errorMsgMap = new HashMap<>();

    //实现通用的通过格式化字符串信息获取错误结果的msg方法
    public String getErrMsg(){
        return StringUtils.join(errorMsgMap.values().toArray(),",");
    }

    public boolean isHasErrors() {
        return hasErrors;
    }

    public void setHasErrors(boolean hasErrors) {
        this.hasErrors = hasErrors;
    }

    public Map<String, String> getErrorMsgMap() {
        return errorMsgMap;
    }

    public void setErrorMsgMap(Map<String, String> errorMsgMap) {
        this.errorMsgMap = errorMsgMap;
    }
}
```
```java
@Component
public class ValidatorImpl implements InitializingBean {

    private Validator validator;

    public ValidationResult validate(Object bean){
        ValidationResult validationResult = new ValidationResult();
        Set<ConstraintViolation<Object>> constraintViolationSet = validator.validate(bean);
        if(constraintViolationSet.size() > 0){
            validationResult.setHasErrors(true);
            constraintViolationSet.forEach(constraintViolation -> {
                String propertyName = constraintViolation.getPropertyPath().toString();
                String errMsg = constraintViolation.getMessage();
                validationResult.getErrorMsgMap().put(propertyName,errMsg);
            });
        }
        return validationResult;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        //将hiberbate validation 通过工厂的初始化方式使其实例化
        this.validator = Validation.buildDefaultValidatorFactory().getValidator();
    }
}
```
3、在UserModel类添加校验信息相关注解以及修改UserServiceImpl使其使用校验器做校验
```java
public class UserModel {

    private Integer id;

    @NotBlank(message = "姓名不能为空")
    private String name;

    @NotNull(message = "性别不能为空")
    private Byte gender;

    @NotNull(message = "年龄不能为空")
    @Min(value = 0,message = "年龄不能小于0岁")
    @Max(value = 150,message = "年龄不能大于150")
    private Integer age;

    @NotBlank(message = "手机号不能为空")
    private String telphone;

    private String registerMode;

    private String thirdPartyId;

    private String encrptPassword;
```
```java
    /* if(StringUtils.isEmpty(userModel.getName())
                || userModel.getGender() == null
                || userModel.getAge() == null
                || StringUtils.isEmpty(userModel.getTelphone())){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR);
    }*/

    ValidationResult validationResult = validator.validate(userModel);
        if(validationResult.isHasErrors()){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,validationResult.getErrMsg());
        }
```
##3、商品模块开发
###3-1 商品领域模型的创建
```java
public class ItemModel {

    private Integer id;

    @NotBlank(message = "商品名称不能为空")
    private String title;

    @NotNull(message = "商品价格不能为空")
    @Min(value = 0,message = "商品价格要大于0")
    private BigDecimal price;

    @NotNull(message = "商品库存不能为空")
    private Integer stock;

    @NotBlank(message = "商品描述不能为空")
    private String description;

    private Integer sales;

    @NotBlank(message = "商品图片不能为空")
    private String imgUrl;

    //使用聚合模型,如果promoModel不为空，则表示其拥有还未结束的秒杀活动
    private PromoModel promoModel;
```
###3-2 数据库表的创建
为了性能上的考虑，把库存信息放在另外一张表

    CREATE TABLE `item` (
      `id` int(11) NOT NULL AUTO_INCREMENT,
      `title` varchar(64) COLLATE utf8_unicode_ci NOT NULL DEFAULT '',
      `price` decimal(10,2) NOT NULL DEFAULT '0.00',
      `description` varchar(500) COLLATE utf8_unicode_ci NOT NULL DEFAULT '',
      `sales` int(11) NOT NULL DEFAULT '0',
      `img_url` varchar(255) COLLATE utf8_unicode_ci NOT NULL DEFAULT '',
      PRIMARY KEY (`id`)
    ) ENGINE=InnoDB AUTO_INCREMENT=49 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci
    
    CREATE TABLE `item_stock` (
      `id` int(11) NOT NULL AUTO_INCREMENT,
      `stock` int(11) NOT NULL DEFAULT '0',
      `item_id` int(11) NOT NULL DEFAULT '0',
      PRIMARY KEY (`id`)
    ) ENGINE=InnoDB AUTO_INCREMENT=33 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci

###3-3 利用mybatis-generator自动生成文件
在mybatis-generator.xml文件里注释其他table标签，添加新的table标签并运行mybatis-generator

            <table tableName="item" domainObjectName="ItemDO" enableCountByExample="false"
                   enableUpdateByExample="false" enableDeleteByExample="false" enableSelectByExample="false"
                   selectByExampleQueryId="false">
            </table>
            <table tableName="item_stock" domainObjectName="ItemStockDO" enableCountByExample="false"
                   enableUpdateByExample="false" enableDeleteByExample="false" enableSelectByExample="false"
                   selectByExampleQueryId="false">
            </table>
###3-4 商品创建功能实现
* controller层
```java
    @RequestMapping(value = "/create", method = {RequestMethod.POST}, consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType createItem(@RequestParam(name = "title") String title,
                                       @RequestParam(name = "price") BigDecimal price,
                                       @RequestParam(name = "stock") Integer stock,
                                       @RequestParam(name = "imgUrl") String imgUrl,
                                       @RequestParam(name = "description") String description) throws BusinessException {
        // 封装service请求用来创建商品
        ItemModel itemModel = new ItemModel();
        itemModel.setTitle(title);
        itemModel.setPrice(price);
        itemModel.setStock(stock);
        itemModel.setImgUrl(imgUrl);
        itemModel.setDescription(description);

        ItemModel itemModelForReturn = itemService.createItem(itemModel);
        ItemVO itemVO = this.convertVOFromModel(itemModelForReturn);

        return CommonReturnType.create(itemVO);
    }
```
```java
public class ItemVO {

    private Integer id;
    private String title;
    private BigDecimal price;
    private Integer stock;
    private String description;
    private Integer sales;
    private String imgUrl;

    //记录商品是否在秒杀活动中，以及对应的status = 0 没有秒杀活动，为1 待抢购，为2 进行中
    private Integer promoStatus;

    //秒杀活动价格
    private BigDecimal promoPrice;
    //秒杀活动ID
    private Integer promoId;

    /**
     * 因为DateTime类型传到前端数据格式有问题，
     * 所以转化为String类型再传到前端
     */
    private String startDate;
```
* service层
```java
    @Override
    @Transactional
    public ItemModel createItem(ItemModel itemModel) throws BusinessException {
        //入参校验
        ValidationResult result = validator.validate(itemModel);
        if (result.isHasErrors()) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, result.getErrMsg());
        }
        //把itemModel转化为dataObject
        ItemDO itemDO = this.convertItemDOFromItemModel(itemModel);

        //写入数据库
        itemDOMapper.insertSelective(itemDO);
        itemModel.setId(itemDO.getId());

        ItemStockDO itemStockDO = this.convertItemStockDOFromItemModel(itemModel);
        itemStockDOMapper.insertSelective(itemStockDO);

        //返回创建完成后数据库里的对象
        return this.getItemById(itemModel.getId());
    }
```
* dao层

> ItemDOMapper.java: int insertSelective(ItemDO record);
> ItemStockDOMapper.java: int insertSelective(ItemStockDO record);

    //ItemDOMapper.xml
    <insert id="insertSelective" parameterType="com.czj.dataobject.ItemDO" useGeneratedKeys="true" keyProperty="id">
        insert into item
        <trim prefix="(" suffix=")" suffixOverrides=",">
          <if test="id != null">
            id,
          </if>
          <if test="title != null">
            title,
          </if>
          <if test="price != null">
            price,
          </if>
          <if test="description != null">
            description,
          </if>
          <if test="sales != null">
            sales,
          </if>
          <if test="imgUrl != null">
            img_url,
          </if>
        </trim>
        <trim prefix="values (" suffix=")" suffixOverrides=",">
          <if test="id != null">
            #{id,jdbcType=INTEGER},
          </if>
          <if test="title != null">
            #{title,jdbcType=VARCHAR},
          </if>
          <if test="price != null">
            #{price,jdbcType=DECIMAL},
          </if>
          <if test="description != null">
            #{description,jdbcType=VARCHAR},
          </if>
          <if test="sales != null">
            #{sales,jdbcType=INTEGER},
          </if>
          <if test="imgUrl != null">
            #{imgUrl,jdbcType=VARCHAR},
          </if>
        </trim>
     </insert>
      
     //ItemStockDOMapper.xml
    <insert id="insertSelective" parameterType="com.czj.dataobject.ItemStockDO" useGeneratedKeys="true" keyProperty="id">
        insert into item_stock
        <trim prefix="(" suffix=")" suffixOverrides=",">
          <if test="id != null">
            id,
          </if>
          <if test="stock != null">
            stock,
          </if>
          <if test="itemId != null">
            item_id,
          </if>
        </trim>
        <trim prefix="values (" suffix=")" suffixOverrides=",">
          <if test="id != null">
            #{id,jdbcType=INTEGER},
          </if>
          <if test="stock != null">
            #{stock,jdbcType=INTEGER},
          </if>
          <if test="itemId != null">
            #{itemId,jdbcType=INTEGER},
          </if>
        </trim>
      </insert>
###3-5 商品列表功能实现
* controller层
- [x]  Lambda表达式的使用
- [x]  Stream API的使用
```java
 @RequestMapping(value = "/list", method = {RequestMethod.GET})
    @ResponseBody
    public CommonReturnType listItem(){

        List<ItemModel> itemModelList = itemService.listItem();
        List<ItemVO> itemVOList = itemModelList.stream().map(itemModel -> {
            ItemVO itemVO = convertVOFromModel(itemModel);
            return itemVO;
        }).collect(Collectors.toList());
        return CommonReturnType.create(itemVOList);
    }
```
* service层
- [x]  Lambda表达式的使用
- [x]  Stream API的使用
```java
    @Override
    public List<ItemModel> listItem() {
        List<ItemDO> itemDOList = itemDOMapper.listItem();
        List<ItemModel> itemModels = itemDOList.stream().map(itemDO -> {
            ItemStockDO itemStockDO = itemStockDOMapper.selectByItemId(itemDO.getId());
            ItemModel itemModel = this.convertModelFromDataObject(itemDO,itemStockDO);
            return itemModel;
        }).collect(Collectors.toList());
        return itemModels;
    }
```
* dao层

> ItemDOMapper.java: List<ItemDO> listItem(); 
ItemStockDOMapper.java: ItemStockDO selectByItemId(Integer itemId);

 
    //ItemDOMapper.xml
     <select id="listItem"  resultMap="BaseResultMap">
        select
        <include refid="Base_Column_List" />
        from item
        order by sales DESC ;
      </select>
    
    //ItemStockDOMapper.xml
     <select id="selectByItemId" parameterType="java.lang.Integer" resultMap="BaseResultMap">
        select
        <include refid="Base_Column_List" />
        from item_stock
        where item_id = #{itemId,jdbcType=INTEGER}
     </select>

###3-6 商品详情功能实现
* controller层
```java
    @RequestMapping(value = "/get", method = {RequestMethod.GET})
    @ResponseBody
    public CommonReturnType getItem(@RequestParam(name = "id")Integer id){
        ItemModel itemModel = itemService.getItemById(id);
        ItemVO itemVO = this.convertVOFromModel(itemModel);

        return CommonReturnType.create(itemVO);
    }
```
* service层
```java
    @Override
    public ItemModel getItemById(Integer id) {
        ItemDO itemDO = itemDOMapper.selectByPrimaryKey(id);
        if(itemDO == null){
            return null;
        }

        //获取该商品库存数量
        ItemStockDO itemStockDO = itemStockDOMapper.selectByItemId(itemDO.getId());

        //将dataobject转化为model
        ItemModel itemModel = this.convertModelFromDataObject(itemDO,itemStockDO);

        return itemModel;
    }
```
* dao层

> ItemDOMapper.java: ItemDO selectByPrimaryKey(Integer id);
> ItemStockDOMapper.java: ItemStockDO selectByItemId(Integer itemId);

    //ItemDOMapper.xml
      <select id="selectByPrimaryKey" parameterType="java.lang.Integer" resultMap="BaseResultMap">
        select 
        <include refid="Base_Column_List" />
        from item
        where id = #{id,jdbcType=INTEGER}
      </select>
     
      //ItemStockDOMapper.xml
      <select id="selectByItemId" parameterType="java.lang.Integer" resultMap="BaseResultMap">
        select
        <include refid="Base_Column_List" />
        from item_stock
        where item_id = #{itemId,jdbcType=INTEGER}
      </select>

##4、交易模块开发
###4-1 交易领域模型的创建
```java
public class OrderModel {

    //订单号，String类型（有固定的生成规则）
    private String id;

    private Integer userId;

    private Integer itemId;

    private BigDecimal itemPrice;

    private Integer amount;

    private BigDecimal orderPrice;

    //若非空，表示以秒杀方式下单
    private Integer promoId;
```
###4-2 数据库表的创建

    CREATE TABLE `order_info` (
      `id` varchar(32) COLLATE utf8_unicode_ci NOT NULL DEFAULT '',
      `user_id` int(11) NOT NULL DEFAULT '0',
      `item_id` int(11) NOT NULL DEFAULT '0',
      `item_price` decimal(10,2) NOT NULL DEFAULT '0.00',
      `amount` int(11) NOT NULL DEFAULT '0',
      `order_price` decimal(10,2) NOT NULL DEFAULT '0.00',
      `promo_id` int(11) NOT NULL DEFAULT '0',
      PRIMARY KEY (`id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci
    
    CREATE TABLE `sequence_info` (
      `name` varchar(255) COLLATE utf8_unicode_ci NOT NULL DEFAULT '',
      `current_value` int(11) NOT NULL DEFAULT '0',
      `step` int(11) NOT NULL DEFAULT '0',
      PRIMARY KEY (`name`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci

###4-3 利用mybatis-generator自动生成文件
在mybatis-generator.xml文件里注释其他table标签，添加新的table标签并运行mybatis-generator

            <table tableName="order_info" domainObjectName="OrderDO" enableCountByExample="false"
                   enableUpdateByExample="false" enableDeleteByExample="false" enableSelectByExample="false"
                   selectByExampleQueryId="false">
            </table>
            <table tableName="sequence_info" domainObjectName="SequenceDO" enableCountByExample="false"
                   enableUpdateByExample="false" enableDeleteByExample="false" enableSelectByExample="false"
                   selectByExampleQueryId="false">
            </table>
###4-4 交易下单功能实现
* controller层
```java
    @RequestMapping(value = "/createorder",method = {RequestMethod.POST},consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType createOrder(@RequestParam(name = "itemId")Integer itemId,
                                        @RequestParam(name = "amount")Integer amount) throws BusinessException {
        //获取用户登录信息
        Boolean isLogin = (Boolean) this.httpServletRequest.getSession().getAttribute("IS_LOGIN");
        if(isLogin == null || !isLogin.booleanValue()){
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN,"用户还未登录，不能下单");
        }
        UserModel userModel = (UserModel)this.httpServletRequest.getSession().getAttribute("LOGIN_USER");
        OrderModel orderModel = orderService.createOrder(userModel.getId(),itemId,amount);
        return CommonReturnType.create(null);
    }
```
* service层
- [x]  [LocalDateTime][5]的使用
- [ ] OrderServiceImpl
```java
    @Override
    @Transactional
    public OrderModel createOrder(Integer userId, Integer itemId, Integer amount) throws BusinessException {
        /**
         * 1、校验下单状态（用户是否合法、下单商品是否存在、购买数量是否正确）
         */
        UserModel userModel = userService.getUserById(userId);
        if(userModel == null){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"用户不存在");
        }

        ItemModel itemModel = itemService.getItemById(itemId);
        if(itemModel == null) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"商品不存在");
        }

        if(amount <= 0 || amount > 99){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"数量信息不正确");
        }

        /**
         * 2、落单减库存
         */
        boolean result = itemService.decreaseStock(itemId,amount);
        if(!result){
            throw new BusinessException(EmBusinessError.STOCK_NOT_ENOUGH);
        }

        /**
         * 3、订单入库
         */
        OrderModel orderModel = new OrderModel();
        orderModel.setUserId(userId);
        orderModel.setItemId(itemId);
        orderModel.setAmount(amount);
        orderModel.setItemPrice(itemModel.getPrice());
        orderModel.setOrderPrice(orderModel.getItemPrice().multiply(BigDecimal.valueOf(amount)));
        //生成交易流水号，订单号
        orderModel.setId(generatorOrderNo());
        OrderDO orderDO = convertFromOrderModel(orderModel);
        orderDOMapper.insertSelective(orderDO);
        //商品销量增加
        itemService.increaseSales(itemId,amount);

        /**
         * 4、返回前端
         */
        return orderModel;
    }
    
    
    /**
     * 生成 16 位订单号
     * @return
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    String generatorOrderNo(){
        StringBuilder stringBuilder = new StringBuilder();

        //1、前 8 位为时间信息（年月日）
        LocalDateTime now = LocalDateTime.now();
        String nowDate = now.format(DateTimeFormatter.ISO_DATE).replace("-","");
        stringBuilder.append(nowDate);

        //2、中间 6 位为自增序列
        int sequence = 0;
        SequenceDO sequenceDO = sequenceDOMapper.selectByPrimaryKey("order_info");
        sequence = sequenceDO.getCurrentValue();
        sequenceDO.setCurrentValue(sequenceDO.getCurrentValue() + sequenceDO.getStep());
        sequenceDOMapper.updateByPrimaryKeySelective(sequenceDO);
        //凑足 6 位
        String sequenceStr = String.valueOf(sequence);
        for(int i = 0;i < 6 - sequenceStr.length();i++){
            stringBuilder.append(0);
        }
        stringBuilder.append(sequenceStr);

        //3、最后两位分库分表位
        stringBuilder.append("00");
        return stringBuilder.toString();
    }
```
- [ ] ItemServiceImpl
```java
    @Override
    @Transactional
    public boolean decreaseStock(Integer itemId, Integer amount) {
        int affectedRow = itemStockDOMapper.decreaseStock(itemId,amount);
        if(affectedRow > 0){
            return true;
        }else {
            return false;
        }
    }

    @Override
    @Transactional
    public void increaseSales(Integer itemId, Integer amount) {
        itemDOMapper.increaseSales(itemId,amount);
    }
```
* dao层
- [ ] getSequenceByName通过名称获取序列号需上锁（xml文件加上for update）
> OrderDOMapper.java: int insertSelective(OrderDO record);

> ItemStockDOMapper.java: int decreaseStock(@Param("itemId") Integer
> itemId,@Param("amount") Integer amount); 
> 
>   ItemDOMapper.java:  int increaseSales(@Param("id")Integer   
> id,@Param("amount")Integer amount);

> SequenceDOMapper.java: SequenceDO getSequenceByName(String name); int updateByPrimaryKeySelective(SequenceDO record);

    <update id="decreaseStock">
        update item_stock
        set stock = stock - #{amount}
        where item_id = #{itemId} and stock >= #{amount}
    </update>
      
    <insert id="insertSelective" parameterType="com.czj.dataobject.OrderDO">
        insert into order_info
        <trim prefix="(" suffix=")" suffixOverrides=",">
          <if test="id != null">
            id,
          </if>
          <if test="userId != null">
            user_id,
          </if>
          <if test="itemId != null">
            item_id,
          </if>
          <if test="itemPrice != null">
            item_price,
          </if>
          <if test="amount != null">
            amount,
          </if>
          <if test="orderPrice != null">
            order_price,
          </if>
          <if test="promoId != null">
            promo_id,
          </if>
        </trim>
        <trim prefix="values (" suffix=")" suffixOverrides=",">
          <if test="id != null">
            #{id,jdbcType=VARCHAR},
          </if>
          <if test="userId != null">
            #{userId,jdbcType=INTEGER},
          </if>
          <if test="itemId != null">
            #{itemId,jdbcType=INTEGER},
          </if>
          <if test="itemPrice != null">
            #{itemPrice,jdbcType=DECIMAL},
          </if>
          <if test="amount != null">
            #{amount,jdbcType=INTEGER},
          </if>
          <if test="orderPrice != null">
            #{orderPrice,jdbcType=DECIMAL},
          </if>
          <if test="promoId != null">
            #{promoId,jdbcType=INTEGER},
          </if>
        </trim>
    </insert>
      
    <update id="increaseSales">
        update item
        set sales = sales + #{amount}
        where id = #{id,jdbcType=INTEGER}
    </update>
        
    <select id="getSequenceByName" parameterType="java.lang.String" resultMap="BaseResultMap">
    select
    <include refid="Base_Column_List" />
    from sequence_info
    where name = #{name,jdbcType=VARCHAR} for update 
  </select>
      
    <update id="updateByPrimaryKeySelective" parameterType="com.czj.dataobject.SequenceDO">
        update sequence_info
        <set>
          <if test="currentValue != null">
            current_value = #{currentValue,jdbcType=INTEGER},
          </if>
          <if test="step != null">
            step = #{step,jdbcType=INTEGER},
          </if>
        </set>
        where name = #{name,jdbcType=VARCHAR}
    </update>

##5、秒杀模块开发
###5-1 秒杀领域模型的创建
- [x]  joda-time类库的使用
```java
public class PromoModel {

    private Integer id;

    //秒杀活动状态，1代表还没开始，2代表进行中，3代表已结束
    private Integer status;

    //秒杀活动名称
    private String promoName;

    //秒杀开始时间
    private DateTime startDate;

    //秒杀结束时间
    private DateTime endDate;

    //秒杀活动使用商品
    private Integer itemId;

    //秒杀获得的商品价格
    private BigDecimal promoItemPrice;
```
###5-2 数据库表的创建

    CREATE TABLE `promo` (
      `id` int(11) NOT NULL AUTO_INCREMENT,
      `promo_name` varchar(255) COLLATE utf8_unicode_ci NOT NULL DEFAULT '',
      `start_date` datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
      `end_date` datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
      `item_id` int(11) NOT NULL DEFAULT '0',
      `promo_item_price` decimal(10,2) NOT NULL DEFAULT '0.00',
      PRIMARY KEY (`id`)
    ) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci
    
###5-3 利用mybatis-generator自动生成文件
在mybatis-generator.xml文件里注释其他table标签，添加新的table标签并运行mybatis-generator

        <table tableName="promo" domainObjectName="PromoDO" enableCountByExample="false"
                   enableUpdateByExample="false" enableDeleteByExample="false" enableSelectByExample="false"
                   selectByExampleQueryId="false">
        </table>
###5-4 秒杀功能实现
- [ ] 活动模型与商品模型结合
```java
public class ItemModel {

    private Integer id;

    @NotBlank(message = "商品名称不能为空")
    private String title;

    @NotNull(message = "商品价格不能为空")
    @Min(value = 0,message = "商品价格要大于0")
    private BigDecimal price;

    @NotNull(message = "商品库存不能为空")
    private Integer stock;

    @NotBlank(message = "商品描述不能为空")
    private String description;

    private Integer sales;

    @NotBlank(message = "商品图片不能为空")
    private String imgUrl;

    //使用聚合模型,如果promoModel不为空，则表示其拥有还未结束的秒杀活动
    private PromoModel promoModel;
```
* controller层
- [ ] ItemController.java
```java
    //商品详情页浏览
    @RequestMapping(value = "/get", method = {RequestMethod.GET})
    @ResponseBody
    public CommonReturnType getItem(@RequestParam(name = "id")Integer id){
        ItemModel itemModel = itemService.getItemById(id);
        ItemVO itemVO = this.convertVOFromModel(itemModel);

        return CommonReturnType.create(itemVO);
    }
    
    //把领域模型转化为视图模型
    private ItemVO convertVOFromModel(ItemModel itemModel) {
        if(itemModel == null) {
            return null;
        }
        ItemVO itemVO = new ItemVO();
        BeanUtils.copyProperties(itemModel,itemVO);
        if(itemModel.getPromoModel() != null){
            //有正在进行或即将进行的秒杀活动
            itemVO.setPromoStatus(itemModel.getPromoModel().getStatus());
            itemVO.setPromoId(itemModel.getPromoModel().getId());
            //把DateTime转化为String类型传到前端，避免数据格式问题(json序列化问题)
            itemVO.setStartDate(itemModel.getPromoModel().getStartDate().toString(DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")));
            itemVO.setPromoPrice(itemModel.getPromoModel().getPromoItemPrice());
        } else {
            itemVO.setPromoStatus(0);
        }
        return itemVO;
    }
```
- [ ] OrderController.java
```java
    //下单
    @RequestMapping(value = "/createorder",method = {RequestMethod.POST},consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType createOrder(@RequestParam(name = "itemId")Integer itemId,
                                        @RequestParam(name="promoId",required = false)Integer promoId,
                                        @RequestParam(name = "amount")Integer amount) throws BusinessException {
        //获取用户登录信息
        Boolean isLogin = (Boolean) this.httpServletRequest.getSession().getAttribute("IS_LOGIN");
        if(isLogin == null || !isLogin.booleanValue()){
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN,"用户还未登录，不能下单");
        }
        UserModel userModel = (UserModel)this.httpServletRequest.getSession().getAttribute("LOGIN_USER");
        OrderModel orderModel = orderService.createOrder(userModel.getId(),itemId,promoId,amount);
        return CommonReturnType.create(null);
    }
```
- [ ] ItemVO.java
```java
public class ItemVO {

    private Integer id;
    private String title;
    private BigDecimal price;
    private Integer stock;
    private String description;
    private Integer sales;
    private String imgUrl;

    //记录商品是否在秒杀活动中，以及对应的status = 0 没有秒杀活动，为1 待抢购，为2 进行中
    private Integer promoStatus;

    //秒杀活动价格
    private BigDecimal promoPrice;
    //秒杀活动ID
    private Integer promoId;

    /**
     * 因为DateTime类型传到前端数据格式有问题，
     * 所以转化为String类型再传到前端
     */
    private String startDate;
```
* service层
- [ ] ItemServiceImpl.java
```java
    //获取某商品信息
    @Override
    public ItemModel getItemById(Integer id) {
        ItemDO itemDO = itemDOMapper.selectByPrimaryKey(id);
        if(itemDO == null){
            return null;
        }

        //获取该商品库存数量
        ItemStockDO itemStockDO = itemStockDOMapper.selectByItemId(itemDO.getId());

        //将dataobject转化为model
        ItemModel itemModel = this.convertModelFromDataObject(itemDO,itemStockDO);

        //获取商品秒杀活动信息
        PromoModel promoModel = promoService.getPromoById(itemModel.getId());
        if(promoModel != null && promoModel.getStatus().intValue() != 3) {
            itemModel.setPromoModel(promoModel);
        }

        return itemModel;
    }
```
- [ ] PromoServiceImpl.java
```java
    //获取某商品秒杀活动信息
    @Override
    public PromoModel getPromoById(Integer itemId) {

        //获取对应商品的秒杀信息
        PromoDO promoDO = promoDOMapper.selectByItemId(itemId);
        PromoModel promoModel = convertFromDataObject(promoDO);
        if(promoModel == null){
            return null;
        }

        //判断秒杀活动的当前状态
        if(promoModel.getStartDate().isAfterNow()){
            promoModel.setStatus(1);
        }else if(promoModel.getEndDate().isBeforeNow()){
            promoModel.setStatus(3);
        }else {
            promoModel.setStatus(2);
        }

        return promoModel;
    }

    private PromoModel convertFromDataObject(PromoDO promoDO){
        if(promoDO == null) {
            return null;
        }
        PromoModel promoModel = new PromoModel();
        BeanUtils.copyProperties(promoDO,promoModel);
        promoModel.setStartDate(new DateTime(promoDO.getStartDate()));
        promoModel.setEndDate(new DateTime(promoDO.getEndDate()));

        return promoModel;
    }
```
- [ ] OrderServiceImpl.java
```java
    //下单
    @Override
    @Transactional
    public OrderModel createOrder(Integer userId, Integer itemId,Integer promoId, Integer amount) throws BusinessException {
        /**
         * 1、校验下单状态（用户是否合法、下单商品是否存在、购买数量是否正确）
         */
        UserModel userModel = userService.getUserById(userId);
        if(userModel == null){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"用户不存在");
        }

        ItemModel itemModel = itemService.getItemById(itemId);
        if(itemModel == null) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"商品不存在");
        }

        if(amount <= 0 || amount > 99){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"数量信息不正确");
        }

        //校验活动信息
        if(promoId != null){
            // 1. 校验对应活动是否存在对应商品
            if(promoId.intValue() != itemModel.getPromoModel().getId()) {
                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"活动信息不正确");
            } else if(itemModel.getPromoModel().getStatus().intValue() != 2){
                // 2、校验活动是否在进行中
                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"活动未开始");
            }
        }
        /**
         * 2、落单减库存
         */
        boolean result = itemService.decreaseStock(itemId,amount);
        if(!result){
            throw new BusinessException(EmBusinessError.STOCK_NOT_ENOUGH);
        }

        /**
         * 3、订单入库
         */
        OrderModel orderModel = new OrderModel();
        orderModel.setUserId(userId);
        orderModel.setItemId(itemId);
        orderModel.setAmount(amount);
        if(promoId != null) {
            orderModel.setItemPrice(itemModel.getPromoModel().getPromoItemPrice());
        } else {
            orderModel.setItemPrice(itemModel.getPrice());
        }
        orderModel.setPromoId(promoId);
        orderModel.setOrderPrice(orderModel.getItemPrice().multiply(BigDecimal.valueOf(amount)));
        //生成交易流水号，订单号
        orderModel.setId(generatorOrderNo());
        OrderDO orderDO = convertFromOrderModel(orderModel);
        orderDOMapper.insertSelective(orderDO);
        //商品销量增加
        itemService.increaseSales(itemId,amount);

        /**
         * 4、返回前端
         */
        return orderModel;
    }
```
* dao层

> PromoDOMapper：PromoDO selectByItemId(Integer itemId);

    <select id="selectByItemId" parameterType="java.lang.Integer" resultMap="BaseResultMap">
        select
        <include refid="Base_Column_List" />
        from promo
        where item_id = #{itemId,jdbcType=INTEGER}
    </select>
##6、遗留问题
- [ ] 如何支撑亿级秒杀流量
- [ ] 如何发现容量问题
- [ ] 如何使得系统水平扩展
- [ ] 查询效率低下
- [ ] 活动开始前页面被疯狂刷新
- [ ] 库存行锁问题
- [ ] 下单操作多，缓慢
- [ ] 浪涌流量如何解决
  [1]: https://spring.io/guides/gs/rest-service/
  [2]: http://www.mybatis.org/generator/configreference/xmlconfig.html
  [3]: https://www.cnblogs.com/camikehuihui/p/7999537.html
  [4]: https://www.cnblogs.com/wushifeng/p/6707248.html
  [5]: https://blog.csdn.net/kingboyworld/article/details/75808108