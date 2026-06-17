package com.czltnb.zhi_xiang_backend.user.mapper;

import com.czltnb.zhi_xiang_backend.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@MybatisTest
class UserMapperTest {

    @Autowired
    private UserMapper userMapper;

    @Test
    @DisplayName("插入一条用户记录并可由 ID 查询")
    void shouldInsertAndFindById() {
        // given
        User user = User.builder()
                .phone("13800138000")
                .email("test@example.com")
                .passwordHash("hashed_password")
                .nickname("测试用户")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // when
        userMapper.insert(user);

        // then
        assertNotNull(user.getId(), "插入后 id 应由数据库生成");

        User found = userMapper.findById(user.getId());
        assertNotNull(found);
        assertEquals("13800138000", found.getPhone());
        assertEquals("测试用户", found.getNickname());
    }

    @Test
    @DisplayName("根据手机号查询用户")
    void shouldFindByPhone() {
        // given
        User user = User.builder()
                .phone("13900139000")
                .nickname("手机号用户")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        userMapper.insert(user);

        // when
        User found = userMapper.findByPhone("13900139000");

        // then
        assertNotNull(found);
        assertEquals("手机号用户", found.getNickname());
    }

    @Test
    @DisplayName("查询不存在的手机号返回 null")
    void shouldReturnNullForUnknownPhone() {
        User found = userMapper.findByPhone("00000000000");
        assertNull(found);
    }

    @Test
    @DisplayName("检查手机号是否存在")
    void shouldCheckPhoneExistence() {
        User user = User.builder()
                .phone("13600136000")
                .nickname("存在性测试")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        userMapper.insert(user);

        assertTrue(userMapper.existsByPhone("13600136000"));
        assertFalse(userMapper.existsByPhone("00000000000"));
    }

    @Test
    @DisplayName("根据邮箱查询用户")
    void shouldFindByEmail() {
        User user = User.builder()
                .email("findme@example.com")
                .nickname("邮箱查询用户")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        userMapper.insert(user);

        User found = userMapper.findByEmail("findme@example.com");
        assertNotNull(found);
        assertEquals("邮箱查询用户", found.getNickname());
    }

    @Test
    @DisplayName("更新用户密码")
    void shouldUpdatePassword() {
        User user = User.builder()
                .phone("13700137000")
                .nickname("密码测试")
                .passwordHash("old_hash")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        userMapper.insert(user);

        userMapper.updatePassword(user.getId(), "new_hash");

        User updated = userMapper.findById(user.getId());
        assertNotNull(updated);
        assertEquals("new_hash", updated.getPasswordHash());
    }

    @Test
    @DisplayName("更新用户资料")
    void shouldUpdateProfile() {
        User user = User.builder()
                .phone("13500135000")
                .nickname("原始昵称")
                .bio("原始简介")
                .gender("male")
                .birthday(LocalDate.of(2000, 1, 1))
                .school("原始学校")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        userMapper.insert(user);

        user.setNickname("新昵称");
        user.setBio("新简介");
        user.setSchool("新学校");
        userMapper.updateProfile(user);

        User updated = userMapper.findById(user.getId());
        assertEquals("新昵称", updated.getNickname());
        assertEquals("新简介", updated.getBio());
        assertEquals("新学校", updated.getSchool());
        // 未更新的字段应保持原值
        assertEquals("male", updated.getGender());
    }

    @Test
    @DisplayName("通过 ID 列表批量查询用户")
    void shouldListByIds() {
        User user1 = User.builder().phone("13100131001").nickname("用户1").createdAt(Instant.now()).updatedAt(Instant.now()).build();
        User user2 = User.builder().phone("13100131002").nickname("用户2").createdAt(Instant.now()).updatedAt(Instant.now()).build();
        userMapper.insert(user1);
        userMapper.insert(user2);

        var users = userMapper.listByIds(java.util.List.of(user1.getId(), user2.getId()));
        assertEquals(2, users.size());
    }
}
