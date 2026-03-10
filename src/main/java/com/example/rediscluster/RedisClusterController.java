package com.example.rediscluster;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/redis")
@RestController
public class RedisClusterController {

    private final RedisClusterService redisClusterService;

    @GetMapping("/start")
    public String startTest() {
        redisClusterService.startTest();
        return "Redis Cluster 테스트가 시작되었습니다. 애플리케이션 로그를 확인하세요.";
    }

    @GetMapping("/stop")
    public String stopTest() {
        redisClusterService.stopTest();
        return "테스트 중단 요청을 보냈습니다.";
    }
}
