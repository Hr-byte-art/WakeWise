package com.wjh.wakewise.domain.travel;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** 高德响应解析与缓存键：纯函数，字面 JSON 夹具直接断言。 */
class AmapResponseParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static com.fasterxml.jackson.databind.JsonNode json(String raw) {
        try {
            return MAPPER.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void geocodeExtractsFirstLocation() {
        var root = json("""
                {"status":"1","info":"OK","geocodes":[
                  {"location":"116.481488,39.990464","level":"门址"},
                  {"location":"116.55,39.91"}]}
                """);
        assertThat(AmapResponseParser.extractGeocode(root)).contains("116.481488,39.990464");
    }

    @Test
    void geocodeEmptyOrFailedReturnsEmpty() {
        assertThat(AmapResponseParser.extractGeocode(json("{\"status\":\"0\",\"info\":\"INVALID_USER_KEY\",\"geocodes\":[]}")))
                .isEmpty();
        assertThat(AmapResponseParser.extractGeocode(json("{\"status\":\"1\",\"geocodes\":[]}"))).isEmpty();
        assertThat(AmapResponseParser.extractGeocode(null)).isEmpty();
    }

    @Test
    void walkingAndDrivingReadPathsDuration() {
        var walking = json("{\"status\":\"1\",\"route\":{\"origin\":\"0,0\",\"paths\":[{\"distance\":1500,\"duration\":1080}]}}");
        assertThat(AmapResponseParser.extractPathsDuration(walking)).contains(1080);

        var driving = json("{\"status\":\"1\",\"route\":{\"paths\":[{\"distance\":9800,\"duration\":1450}]}}");
        assertThat(AmapResponseParser.extractPathsDuration(driving)).contains(1450);

        // 高德真实响应：duration/distance 均为字符串数字（2026-10-30 真 key 冒烟发现）
        var realShape = json("{\"status\":\"1\",\"info\":\"OK\",\"count\":\"1\",\"route\":{\"origin\":\"116.316,39.984\","
                + "\"destination\":\"116.322,39.895\",\"taxi_cost\":\"38\","
                + "\"paths\":[{\"distance\":\"12169\",\"duration\":\"1343\",\"strategy\":\"速度最快\"}]}}");
        assertThat(AmapResponseParser.extractPathsDuration(realShape)).contains(1343);
    }

    @Test
    void transitReadsTransitsDuration() {
        var transit = json("""
                {"status":"1","route":{"origin":"0,0","distance":8000,
                 "transits":[{"cost":"4.0","duration":1920,"nightflag":"0","walking_distance":600}]}}
                """);
        assertThat(AmapResponseParser.extractTransitDuration(transit)).contains(1920);
    }

    @Test
    void bicyclingV4ReadsDataPathsDuration() {
        var v4 = json("""
                {"errcode":0,"errmsg":"OK","data":{"origin":"0,0","paths":[{"distance":3100,"duration":960}]}}
                """);
        assertThat(AmapResponseParser.extractBicyclingDuration(v4)).contains(960);

        var v4Failed = json("{\"errcode\":10001,\"errmsg\":\"INVALID_USER_KEY\"}");
        assertThat(AmapResponseParser.extractBicyclingDuration(v4Failed)).isEmpty();
    }

    @Test
    void malformedOrZeroDurationsAreRejected() {
        // v3 失败 / 路径缺失 / 0 秒一律拒绝，调用方降级
        assertThat(AmapResponseParser.extractPathsDuration(json("{\"status\":\"0\"}"))).isEmpty();
        assertThat(AmapResponseParser.extractPathsDuration(json("{\"status\":\"1\",\"route\":{}}"))).isEmpty();
        assertThat(AmapResponseParser.extractPathsDuration(
                json("{\"status\":\"1\",\"route\":{\"paths\":[{\"duration\":0}]}}"))).isEmpty();
    }

    @Test
    void parseLocationSplitsLngLat() {
        BigDecimal[] ll = AmapClient.parseLocation("116.481488,39.990464");
        assertThat(ll).isNotNull();
        assertThat(ll[0]).isEqualByComparingTo("116.481488");
        assertThat(ll[1]).isEqualByComparingTo("39.990464");

        assertThat(AmapClient.parseLocation("bad")).isNull();
        assertThat(AmapClient.parseLocation("116.4,39.9,0")).isNull();
        assertThat(AmapClient.parseLocation(null)).isNull();
    }

    @Test
    void routeKeyCompactsTo4dpAndStaysWithinColumnWidth() {
        String key = TravelEtaService.routeKey(
                "116.481488,39.990464", "116.588031,39.931101", "TRANSIT");
        assertThat(key).isEqualTo("o116.4815,39.9905|d116.5880,39.9311|mTRANSIT");
        assertThat(key.length()).isLessThanOrEqualTo(64);
    }
}
