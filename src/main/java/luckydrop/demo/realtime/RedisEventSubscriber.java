package luckydrop.demo.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import luckydrop.demo.draw.sse.DrawSseService;
import luckydrop.demo.realtime.event.DrawStatusChangedEvent;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class RedisEventSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final DrawSseService drawSseService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);

        try {
            if (RedisChannels.DRAW_STATUS.equals(channel)) {
                DrawStatusChangedEvent event =
                        objectMapper.readValue(payload, DrawStatusChangedEvent.class);

                drawSseService.sendToLocalEmitters(event);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Redis 이벤트 처리에 실패했습니다.", exception);
        }
    }
}
