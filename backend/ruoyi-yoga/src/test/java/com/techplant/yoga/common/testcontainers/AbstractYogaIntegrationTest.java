package com.techplant.yoga.common.testcontainers;

import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import com.ruoyi.common.core.domain.model.LoginUser;

/** Testcontainers 集成测试基类：提供容器上的数据源与操作人身份。 */
@ExtendWith(SpringExtension.class)
public abstract class AbstractYogaIntegrationTest
{
    protected static final Long OPERATOR_ID = 1L;

    @Autowired
    protected DataSource dataSource;

    @BeforeEach
    void bindOperator()
    {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(OPERATOR_ID);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(loginUser, null));
    }

    @AfterEach
    void clearOperator()
    {
        SecurityContextHolder.clearContext();
    }
}
