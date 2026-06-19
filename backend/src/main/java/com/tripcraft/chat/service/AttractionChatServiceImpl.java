package com.tripcraft.chat.service;

import com.tripcraft.attraction.dto.AttractionDetailDto;
import com.tripcraft.attraction.dto.NearbyAttraction;
import com.tripcraft.attraction.service.AttractionService;
import com.tripcraft.chat.dto.AttractionChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttractionChatServiceImpl implements AttractionChatService {

    private final ChatClient attractionChatClient;
    private final AttractionService attractionService;

    private static final double NEARBY_RADIUS_KM = 3.0;
    private static final int NEARBY_LIMIT = 8;

    private static final Map<Integer, String> TYPE_LABEL = Map.of(
            12, "관광지", 14, "문화시설", 15, "축제·공연·행사", 25, "여행코스",
            28, "레포츠", 32, "숙박", 38, "쇼핑", 39, "음식점");

    @Override
    public AttractionChatResponse chat(Long attractionId, String message, String conversationId) {
        AttractionDetailDto detail = attractionService.getById(attractionId);
        List<NearbyAttraction> nearby = attractionService.findNearby(
                detail.getLatitude(), detail.getLongitude(), detail.getId(), NEARBY_RADIUS_KM, NEARBY_LIMIT);

        String convId = StringUtils.hasText(conversationId) ? conversationId : UUID.randomUUID().toString();
        String systemPrompt = buildSystemPrompt(detail, nearby);

        String reply = attractionChatClient.prompt()
                .system(systemPrompt)
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, convId))
                .call()
                .content();

        log.debug("관광지 챗봇 응답: attractionId={}, convId={}", attractionId, convId);
        return new AttractionChatResponse(reply, convId, nearby);
    }

    /** AttractionDetailDto와 주변 장소를 한국어 시스템 프롬프트로 직렬화한다. */
    private String buildSystemPrompt(AttractionDetailDto d, List<NearbyAttraction> nearby) {
        StringBuilder sb = new StringBuilder();
        sb.append("당신은 한국 여행을 돕는 친절한 관광 안내 도우미입니다.\n")
          .append("아래 [관광지 정보]와 [주변 장소] 데이터를 근거로 사용자의 질문에 한국어로 친근하고 간결하게 답하세요.\n")
          .append("주변에 가볼 곳·먹을 곳을 물으면 [주변 장소] 목록을 활용해 추천하되, 목록에 없는 곳은 지어내지 마세요.\n")
          .append("제공된 데이터로 알 수 없는 내용은 솔직히 모른다고 답하세요.\n")
          .append("이 관광지·여행과 무관한 질문에는 정중히 관광 관련 질문을 유도하세요.\n\n");

        sb.append("[관광지 정보]\n");
        appendField(sb, "이름", d.getTitle());
        appendField(sb, "분류", d.getCategory());
        appendField(sb, "지역", joinNonBlank(d.getRegion(), d.getSigunguName()));
        appendField(sb, "주소", joinNonBlank(d.getAddr1(), d.getAddr2()));
        appendField(sb, "전화", d.getTel());
        appendField(sb, "홈페이지", stripHtml(d.getHomepage()));

        Map<String, String> intro = d.getIntro();
        if (intro != null && !intro.isEmpty()) {
            sb.append("- 이용/운영 정보:\n");
            intro.forEach((k, v) -> {
                String val = stripHtml(v);
                if (StringUtils.hasText(val)) sb.append("    · ").append(k).append(": ").append(val).append('\n');
            });
        }

        if (d.getInfoList() != null && !d.getInfoList().isEmpty()) {
            sb.append("- 추가 정보:\n");
            d.getInfoList().forEach(info -> {
                String text = stripHtml(info.getInfotext());
                if (StringUtils.hasText(text)) {
                    sb.append("    · ").append(info.getInfoname()).append(": ").append(text).append('\n');
                }
            });
        }

        appendField(sb, "소개", stripHtml(d.getOverview()));

        if (nearby != null && !nearby.isEmpty()) {
            sb.append('\n').append("[주변 장소 (가까운 순, 반경 약 ").append((int) NEARBY_RADIUS_KM).append("km)]\n");
            for (NearbyAttraction n : nearby) {
                sb.append("- ").append(n.getTitle());
                String label = TYPE_LABEL.get(n.getContentTypeId());
                if (label != null) sb.append(" (").append(label).append(')');
                if (n.getDistanceM() != null) sb.append(" · 약 ").append(formatDistance(n.getDistanceM()));
                if (StringUtils.hasText(n.getAddr1())) sb.append(" · ").append(n.getAddr1().trim());
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private String formatDistance(double meters) {
        if (meters < 1000) return Math.round(meters) + "m";
        return String.format("%.1fkm", meters / 1000.0);
    }

    private void appendField(StringBuilder sb, String label, String value) {
        if (StringUtils.hasText(value)) {
            sb.append("- ").append(label).append(": ").append(value.trim()).append('\n');
        }
    }

    private String joinNonBlank(String a, String b) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(a)) sb.append(a.trim());
        if (StringUtils.hasText(b)) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(b.trim());
        }
        return sb.toString();
    }

    /** TourAPI 텍스트의 HTML 태그/엔티티를 제거해 프롬프트를 정리한다. */
    private String stripHtml(String html) {
        if (html == null) return null;
        return html.replaceAll("<[^>]*>", " ")
                   .replace("&nbsp;", " ")
                   .replace("&amp;", "&")
                   .replaceAll("\\s+", " ")
                   .trim();
    }
}
