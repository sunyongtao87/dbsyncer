package org.dbsyncer.parser.enums;

import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.parser.ParserException;
import org.dbsyncer.parser.convert.Handler;
import org.dbsyncer.parser.convert.handler.*;

/**
 * 支持的转换类型
 *
 * @author AE86
 * @version 1.0.0
 * @date 2019/9/19 23:56
 */
public enum ConvertEnum {

    /**
     * 默认值
     */
    DEFAULT("DEFAULT", "默认值", 1, new DefaultHandler()),
    /**
     * 系统时间戳
     */
    SYSTEM_TIMESTAMP("SYSTEM_TIMESTAMP", "系统时间戳", 0, new TimestampHandler()),
    /**
     * 系统日期date
     */
    SYSTEM_DATE("SYSTEM_DATE", "系统日期", 0, new DateHandler()),
    /**
     * Timestamp转date
     */
    TIMESTAMP_TO_DATE("TIMESTAMP_TO_DATE", "Timestamp转date", 0, new TimestampToDateHandler()),
    /**
     * Timestamp转Long
     */
    TIMESTAMP_TO_LONG("TIMESTAMP_TO_LONG", "Timestamp转Long", 0, new TimestampToLongHandler()),
    /**
     * Long转Timestamp
     */
    LONG_TO_TIMESTAMP("LONG_TO_TIMESTAMP", "Long转Timestamp", 0, new LongToTimestampHandler()),
    /**
     * 替换
     */
    REPLACE("REPLACE", "替换", 2, new ReplaceHandler()),
    /**
     * 追加在前面,例如“张三”追加123 => 123张三
     */
    PREPEND("PREPEND", "前面追加", 1, new PrependHandler()),
    /**
     * 追加在后面,例如“张三”追加123 => 张三123
     */
    APPEND("APPEND", "后面追加", 1, new AppendHandler()),
    /**
     * AES加密
     */
    AES_ENCRYPT("AES_ENCRYPT", "AES加密", 1, new AesEncryptHandler()),
    /**
     * AES解密
     */
    AES_DECRYPT("AES_DECRYPT", "AES解密", 1, new AesDecryptHandler()),
    /**
     * SHA1加密
     */
    SHA1("SHA1", "SHA1加密", 0, new Sha1Handler()),
    /**
     * UUID
     */
    UUID("UUID", "UUID", 0, new UUIDHandler()),
    /**
     * 去掉首字符
     */
    REM_STR_FIRST("REM_STR_FIRST", "去掉首字符", 0, new RemStrFirstHandler()),
    /**
     * 去掉尾字符
     */
    REM_STR_LAST("REM_STR_LAST", "去掉尾字符", 0, new RemStrLastHandler()),
    /**
     * 从前面截取N个字符
     */
    SUB_STR_FIRST("SUB_STR_FIRST", "从前面截取N个字符", 1, new SubStrFirstHandler()),
    /**
     * 从后面截取N个字符
     */
    SUB_STR_LAST("SUB_STR_LAST", "从后面截取N个字符", 1, new SubStrLastHandler()),
    /**
     * 清空
     */
    CLEAR("CLEAR", "清空", 0, new ClearHandler());

    // 转换编码
    private String code;
    // 转换名称
    private String name;
    // 参数个数
    private int argNum;
    // 转换实现
    private Handler handler;

    ConvertEnum(String code, String name, int argNum, Handler handler) {
        this.code = code;
        this.name = name;
        this.argNum = argNum;
        this.handler = handler;
    }

    public static Handler getHandler(String code) throws ParserException {
        for (ConvertEnum e : ConvertEnum.values()) {
            if (StringUtil.equals(code, e.getCode())) {
                return e.getHandler();
            }
        }
        throw new ParserException(String.format("Handler code \"%s\" does not exist.", code));
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getArgNum() {
        return argNum;
    }

    public Handler getHandler() {
        return handler;
    }

}