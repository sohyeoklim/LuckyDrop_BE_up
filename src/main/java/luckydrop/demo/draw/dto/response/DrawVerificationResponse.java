package luckydrop.demo.draw.dto.response;

import lombok.Builder;
import lombok.Getter;
import luckydrop.demo.draw.entity.Draw;
import luckydrop.demo.draw.enums.DrawStatus;

import java.time.LocalDateTime;

@Getter
@Builder
public class DrawVerificationResponse {

    private String status;
    private String algorithm;
    private String commitment;
    private String revealedCode;
    private String participantSnapshotHash;
    private LocalDateTime drawnAt;

    public static DrawVerificationResponse from(Draw draw) {
        if (!draw.hasVerificationCommitment()) {
            return DrawVerificationResponse.builder()
                    .status("LEGACY")
                    .build();
        }

        boolean revealed = draw.getStatus() == DrawStatus.CLOSE && draw.getDrawnAt() != null;
        return DrawVerificationResponse.builder()
                .status(revealed ? "VERIFIED" : "PENDING")
                .algorithm(draw.getVerificationAlgorithm())
                .commitment(draw.getServerSeedHash())
                .revealedCode(revealed ? draw.getServerSeed() : null)
                .participantSnapshotHash(revealed ? draw.getParticipantSnapshotHash() : null)
                .drawnAt(draw.getDrawnAt())
                .build();
    }
}
