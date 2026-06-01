package com.tripcraft.plan.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TMapClient {

    private final ObjectMapper objectMapper;

    @Value("${tmap.api-key}")
    private String apiKey;

    @Value("${tmap.base-url}")
    private String baseUrl;

    public record TMapDrivingResult(int durationMinutes, int taxiFare, String routeCoords) {}

    public TMapDrivingResult fetchTaxiRoute(BigDecimal fromLat, BigDecimal fromLng,
                                             BigDecimal toLat, BigDecimal toLng) {
        String url = baseUrl + "/routes?version=1";
        String body = String.format(
            "{\"startX\":\"%s\",\"startY\":\"%s\",\"endX\":\"%s\",\"endY\":\"%s\"," +
            "\"reqCoordType\":\"WGS84GEO\",\"resCoordType\":\"WGS84GEO\"," +
            "\"startName\":\"출발\",\"endName\":\"도착\"}",
            fromLng.toPlainString(), fromLat.toPlainString(),
            toLng.toPlainString(), toLat.toPlainString()
        );
        try {
            String response = RestClient.create()
                .post().uri(url)
                .header("appKey", apiKey)
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve().body(String.class);

            JsonNode root = objectMapper.readTree(response);
            int durationSeconds = 0;
            int taxiFare = 0;
            List<double[]> coords = new ArrayList<>();

            for (JsonNode feature : root.path("features")) {
                String geomType = feature.path("geometry").path("type").asText();
                JsonNode props = feature.path("properties");

                if ("Point".equals(geomType) && props.has("totalTime")) {
                    durationSeconds = props.path("totalTime").asInt(0);
                    taxiFare = props.path("taxiFare").asInt(0);
                }
                if ("LineString".equals(geomType)) {
                    for (JsonNode coord : feature.path("geometry").path("coordinates")) {
                        coords.add(new double[]{coord.get(0).asDouble(), coord.get(1).asDouble()});
                    }
                }
            }

            int durationMinutes = Math.max(1, (int) Math.ceil(durationSeconds / 60.0));
            String routeJson = objectMapper.writeValueAsString(coords);
            return new TMapDrivingResult(durationMinutes, taxiFare, routeJson);
        } catch (Exception e) {
            log.warn("T Map 택시 경로 조회 실패 from=({},{}) to=({},{}): {}",
                fromLat, fromLng, toLat, toLng, e.getMessage());
            return null;
        }
    }

    public TMapDrivingResult fetchWalkingRoute(BigDecimal fromLat, BigDecimal fromLng,
                                                BigDecimal toLat, BigDecimal toLng) {
        String url = baseUrl + "/routes/pedestrian?version=1";
        String body = String.format(
            "{\"startX\":\"%s\",\"startY\":\"%s\",\"endX\":\"%s\",\"endY\":\"%s\"," +
            "\"reqCoordType\":\"WGS84GEO\",\"resCoordType\":\"WGS84GEO\"," +
            "\"startName\":\"출발\",\"endName\":\"도착\",\"speed\":4}",
            fromLng.toPlainString(), fromLat.toPlainString(),
            toLng.toPlainString(), toLat.toPlainString()
        );
        try {
            String response = RestClient.create()
                .post().uri(url)
                .header("appKey", apiKey)
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve().body(String.class);

            JsonNode root = objectMapper.readTree(response);
            int durationSeconds = 0;
            List<double[]> coords = new ArrayList<>();

            for (JsonNode feature : root.path("features")) {
                String geomType = feature.path("geometry").path("type").asText();
                JsonNode props = feature.path("properties");
                if ("Point".equals(geomType) && props.has("totalTime")) {
                    durationSeconds = props.path("totalTime").asInt(0);
                }
                if ("LineString".equals(geomType)) {
                    for (JsonNode coord : feature.path("geometry").path("coordinates")) {
                        coords.add(new double[]{coord.get(0).asDouble(), coord.get(1).asDouble()});
                    }
                }
            }

            int durationMinutes = Math.max(1, (int) Math.ceil(durationSeconds / 60.0));
            String routeJson = objectMapper.writeValueAsString(coords);
            return new TMapDrivingResult(durationMinutes, 0, routeJson);
        } catch (Exception e) {
            log.warn("T Map 도보 경로 조회 실패: {}", e.getMessage());
            return null;
        }
    }
}
