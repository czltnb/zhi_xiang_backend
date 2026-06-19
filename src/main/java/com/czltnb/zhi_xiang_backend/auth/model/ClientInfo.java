package com.czltnb.zhi_xiang_backend.auth.model;

/**
 * 客户端信息 — 用于登录审计。
 *
 * @param ip        客户端 IP
 * @param userAgent User-Agent 头
 */
public record ClientInfo(String ip, String userAgent) {
}

/**
 * Java record — 自动生成构造器、getter、equals/hashCode/toString，适合不可变数据载体。
 */
