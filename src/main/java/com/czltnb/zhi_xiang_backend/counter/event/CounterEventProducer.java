package com.czltnb.zhi_xiang_backend.counter.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * 通过异步线程将事件写入Kafka，不阻塞同步路径
 */
@Service
public class CounterEventProducer {

    private final KafkaTemplate<String,String> kafka;
    private final ObjectMapper objectMapper;

    public CounterEventProducer(KafkaTemplate<String, String> kafka, ObjectMapper objectMapper) {
        this.kafka        = kafka;
        this.objectMapper = objectMapper; //ObjectMapper：Jackson 的核心工具类，用来做 对象 ↔ JSON 的序列化/反序列化。
    }

    public void publish(CounterEvent event) {
        try{
            String payload = objectMapper.writeValueAsString(event);
            kafka.send(CounterTopics.EVENTS,payload);
        } catch (JsonProcessingException e) {
            // 生产异常不影响主流程
        }
    }
}
