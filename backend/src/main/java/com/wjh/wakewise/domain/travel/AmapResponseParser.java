package com.wjh.wakewise.domain.travel;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

/**
 * 高德 Web 服务响应解析（纯函数、确定性）：只做形状提取与防御，不做任何业务判断。
 *
 * v3（geocode/walking/driving/transit）成功标志 status=="1"；
 * v4（bicycling）成功标志 errcode==0，路径在 data.paths 下。
 */
public final class AmapResponseParser {

    private AmapResponseParser() {
    }

    /** 地理编码：取首个 geocodes[].location（"lng,lat" 文本）。 */
    public static Optional<String> extractGeocode(JsonNode root) {
        if (root == null || !"1".equals(root.path("status").asText())) {
            return Optional.empty();
        }
        JsonNode location = root.path("geocodes").path(0).path("location");
        return location.isMissingNode() || location.asText().isBlank()
                ? Optional.empty() : Optional.of(location.asText());
    }

    /** v3 步行/驾车：route.paths[0].duration（秒）。 */
    public static Optional<Integer> extractPathsDuration(JsonNode root) {
        if (root == null || !"1".equals(root.path("status").asText())) {
            return Optional.empty();
        }
        return positiveInt(root.path("route").path("paths").path(0).path("duration"));
    }

    /** v3 公交：route.transits[0].duration（秒）。 */
    public static Optional<Integer> extractTransitDuration(JsonNode root) {
        if (root == null || !"1".equals(root.path("status").asText())) {
            return Optional.empty();
        }
        return positiveInt(root.path("route").path("transits").path(0).path("duration"));
    }

    /** v4 骑行：data.paths[0].duration（秒），errcode==0 为成功。 */
    public static Optional<Integer> extractBicyclingDuration(JsonNode root) {
        if (root == null || root.path("errcode").asInt(-1) != 0) {
            return Optional.empty();
        }
        return positiveInt(root.path("data").path("paths").path(0).path("duration"));
    }

    private static Optional<Integer> positiveInt(JsonNode node) {
        if (node.isMissingNode()) {
            return Optional.empty();
        }
        // 高德真实响应里 duration 等字段是字符串数字（"1343"），测试 fixture 是数字——两种都要接
        int value;
        if (node.isTextual()) {
            try {
                value = Integer.parseInt(node.asText().trim());
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        } else if (node.canConvertToInt()) {
            value = node.asInt();
        } else {
            return Optional.empty();
        }
        return value > 0 ? Optional.of(value) : Optional.empty();
    }
}
