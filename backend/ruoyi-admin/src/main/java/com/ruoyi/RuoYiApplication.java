package com.ruoyi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * 启动程序
 *
 * <p>扫描范围包含业务包 {@code com.techplant.yoga}：业务代码放在独立模块 ruoyi-yoga 里，
 * 包名按详细设计 §1.1.1 的口径是 {@code com.techplant.yoga.<模块>}，不在 {@code com.ruoyi} 之下，
 * 因此必须显式加进扫描范围，否则业务 controller / service / 配置类都不会被注册。</p>
 *
 * @author ruoyi
 */
@SpringBootApplication(scanBasePackages = { "com.ruoyi", "com.techplant.yoga" }, exclude = {
        DataSourceAutoConfiguration.class })
public class RuoYiApplication
{
    public static void main(String[] args)
    {
        // System.setProperty("spring.devtools.restart.enabled", "false");
        SpringApplication.run(RuoYiApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  若依启动成功   ლ(´ڡ`ლ)ﾞ  \n" +
                " .-------.       ____     __        \n" +
                " |  _ _   \\      \\   \\   /  /    \n" +
                " | ( ' )  |       \\  _. /  '       \n" +
                " |(_ o _) /        _( )_ .'         \n" +
                " | (_,_).' __  ___(_ o _)'          \n" +
                " |  |\\ \\  |  ||   |(_,_)'         \n" +
                " |  | \\ `'   /|   `-'  /           \n" +
                " |  |  \\    /  \\      /           \n" +
                " ''-'   `'-'    `-..-'              ");
    }
}
