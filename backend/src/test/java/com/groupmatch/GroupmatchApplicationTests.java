package com.groupmatch;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;

@SpringBootTest
@ActiveProfiles("test")
@MockitoBean(types = DataSource.class)
class GroupmatchApplicationTests {

    @Test
    void contextLoads() {}
}
