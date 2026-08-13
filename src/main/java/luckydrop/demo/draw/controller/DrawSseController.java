package luckydrop.demo.draw.controller;

import lombok.RequiredArgsConstructor;
import luckydrop.demo.draw.sse.DrawSseService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/draws")
@RequiredArgsConstructor
public class DrawSseController {

    private final DrawSseService drawSseService;

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        return drawSseService.subscribe();
    }
}
