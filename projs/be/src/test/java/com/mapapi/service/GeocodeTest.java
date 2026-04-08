package com.mapapi.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Kakao geocode API 직접 호출 테스트.
 * 실행: java -cp build/classes/java/test com.mapapi.service.GeocodeTest
 * 또는 IDE에서 main 실행
 */
public class GeocodeTest {

    // .env의 KAKAO_REST_API_KEY
    private static final String KAKAO_REST_API_KEY = System.getenv("KAKAO_REST_API_KEY");

    public static void main(String[] args) throws Exception {
        String address = "서울특별시 송파구 송이로31길 56";

        if (KAKAO_REST_API_KEY == null || KAKAO_REST_API_KEY.isEmpty()) {
            System.out.println("ERROR: KAKAO_REST_API_KEY 환경변수를 설정하세요.");
            System.out.println("예: set KAKAO_REST_API_KEY=ddd51e4da3f7e47883aa6b960abdf485");
            return;
        }

        System.out.println("=== Kakao Geocode API 테스트 ===");
        System.out.println("API Key: " + KAKAO_REST_API_KEY.substring(0, 8) + "...");
        System.out.println("주소: " + address);
        System.out.println();

        // 방법 1: URLEncoder 사용 (현재 GeocodeService 방식)
        System.out.println("--- 방법 1: URLEncoder.encode ---");
        testWithURLEncoder(address);

        System.out.println();

        // 방법 2: URI 클래스 사용
        System.out.println("--- 방법 2: URI 클래스 ---");
        testWithURI(address);

        System.out.println();

        // 방법 3: 직접 URL 문자열 (인코딩 없이)
        System.out.println("--- 방법 3: 인코딩 없이 직접 ---");
        testDirect(address);
    }

    /** 방법 1: URLEncoder.encode (현재 코드 방식) */
    static void testWithURLEncoder(String address) throws Exception {
        String encoded = URLEncoder.encode(address, StandardCharsets.UTF_8);
        String urlStr = "https://dapi.kakao.com/v2/local/search/address?query=" + encoded;
        System.out.println("URL: " + urlStr);
        System.out.println("Encoded query: " + encoded);
        callApi(urlStr);
    }

    /** 방법 2: URI 클래스 */
    static void testWithURI(String address) throws Exception {
        URI uri = new URI("https", "dapi.kakao.com", "/v2/local/search/address", "query=" + address, null);
        String urlStr = uri.toASCIIString();
        System.out.println("URL: " + urlStr);
        callApi(urlStr);
    }

    /** 방법 3: 인코딩 없이 */
    static void testDirect(String address) throws Exception {
        String urlStr = "https://dapi.kakao.com/v2/local/search/address?query=" + address;
        System.out.println("URL: " + urlStr);
        try {
            callApi(urlStr);
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
        }
    }

    /** HTTP GET 호출 */
    static void callApi(String urlStr) throws Exception {
        URL url = new URI(urlStr).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "KakaoAK " + KAKAO_REST_API_KEY);
        conn.setRequestProperty("Accept-Charset", "UTF-8");

        int status = conn.getResponseCode();
        System.out.println("HTTP Status: " + status);

        BufferedReader reader;
        if (status >= 200 && status < 300) {
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        } else {
            reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
        }

        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();

        String body = sb.toString();
        System.out.println("Response: " + body.substring(0, Math.min(500, body.length())));

        // documents 개수 확인
        if (body.contains("\"documents\":[]")) {
            System.out.println(">>> 결과: documents 비어있음!");
        } else if (body.contains("\"documents\":[")) {
            System.out.println(">>> 결과: documents 있음 ✓");
        } else if (body.contains("errorType")) {
            System.out.println(">>> 결과: 에러 응답!");
        }
    }
}
