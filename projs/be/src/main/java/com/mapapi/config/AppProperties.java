package com.mapapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml의 app 프리픽스 아래 설정을 바인딩하는 프로퍼티 클래스.
 * - app.kakao.rest-api-key → kakao.restApiKey
 * - app.kakao.javascript-key → kakao.javascriptKey
 * - app.data-go-kr.service-key → dataGoKr.serviceKey
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Kakao kakao = new Kakao();
    private DataGoKr dataGoKr = new DataGoKr();

    public Kakao getKakao() {
        return kakao;
    }

    public void setKakao(Kakao kakao) {
        this.kakao = kakao;
    }

    public DataGoKr getDataGoKr() {
        return dataGoKr;
    }

    public void setDataGoKr(DataGoKr dataGoKr) {
        this.dataGoKr = dataGoKr;
    }

    public static class Kakao {
        private String restApiKey;
        private String javascriptKey;

        public String getRestApiKey() {
            return restApiKey;
        }

        public void setRestApiKey(String restApiKey) {
            this.restApiKey = restApiKey;
        }

        public String getJavascriptKey() {
            return javascriptKey;
        }

        public void setJavascriptKey(String javascriptKey) {
            this.javascriptKey = javascriptKey;
        }
    }

    public static class DataGoKr {
        private String serviceKey;

        public String getServiceKey() {
            return serviceKey;
        }

        public void setServiceKey(String serviceKey) {
            this.serviceKey = serviceKey;
        }
    }
}
