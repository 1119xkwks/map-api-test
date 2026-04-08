package com.mapapi.util;

import com.mapapi.dto.TradeItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 국토교통부 아파트 매매 실거래가 상세 자료 API의 XML 응답을 파싱하는 유틸리티.
 *
 * 정상 응답 예시:
 * <response>
 *   <header>
 *     <resultCode>000</resultCode>
 *     <resultMsg>OK</resultMsg>
 *   </header>
 *   <body>
 *     <items>
 *       <item>...</item>
 *     </items>
 *   </body>
 * </response>
 *
 * 에러 코드 참조: .claude/docs/apt_trade_api.md
 * - 000: 정상
 * - 01: Application Error, 02: DB Error, 03: No Data, 04: HTTP Error
 * - 10: 잘못된 요청, 11: 필수 파라미터 없음, 20: 접근 거부, 30: 잘못된 키 등
 */
public class XmlParserUtil {

    private static final Logger log = LoggerFactory.getLogger(XmlParserUtil.class);

    /** 정상 응답으로 간주하는 resultCode 목록 */
    private static final Set<String> SUCCESS_CODES = Set.of("00", "000");

    private XmlParserUtil() {
    }

    /**
     * 공공API XML 응답 문자열을 파싱하여 TradeItem 리스트로 변환한다.
     *
     * @param xml XML 응답 문자열
     * @return TradeItem 리스트
     * @throws ApiResponseException 공공API가 에러 코드를 반환한 경우
     * @throws XmlParseException    XML 파싱 자체가 실패한 경우
     */
    public static List<TradeItem> parseTradeXml(String xml) {
        List<TradeItem> items = new ArrayList<>();

        // 1단계: XML → DOM 파싱
        Document doc;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // XXE 공격 방지
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            doc = builder.parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            log.error("[XML 파싱 오류] XML 문서 자체를 파싱할 수 없습니다: {}", e.getMessage());
            log.debug("[XML 파싱 오류] 원본 XML (처음 300자): {}",
                    xml.substring(0, Math.min(300, xml.length())));
            throw new XmlParseException("XML 문서 파싱 실패: " + e.getMessage(), e);
        }

        // 2단계: 응답 코드 확인
        String resultCode = getFirstTagValue(doc, "resultCode");
        String resultMsg = getFirstTagValue(doc, "resultMsg");

        if (resultCode != null) {
            resultCode = resultCode.trim();
            resultMsg = resultMsg != null ? resultMsg.trim() : "";
            log.info("[공공API 응답] resultCode={}, resultMsg={}", resultCode, resultMsg);

            if (!SUCCESS_CODES.contains(resultCode)) {
                log.error("[공공API 에러 응답] resultCode={}, resultMsg={}", resultCode, resultMsg);
                throw new ApiResponseException(resultCode, resultMsg);
            }
        } else {
            log.warn("[공공API 응답] resultCode 태그를 찾을 수 없음. XML 구조가 예상과 다릅니다.");
            log.debug("[공공API 응답] 원본 XML (처음 500자): {}",
                    xml.substring(0, Math.min(500, xml.length())));
        }

        // 3단계: item 요소 파싱
        try {
            NodeList itemNodes = doc.getElementsByTagName("item");
            log.info("[XML 파싱] item 노드 개수: {}", itemNodes.getLength());

            for (int i = 0; i < itemNodes.getLength(); i++) {
                Node node = itemNodes.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;
                    TradeItem item = parseItem(element);
                    if (item != null) {
                        items.add(item);
                    }
                }
            }
        } catch (Exception e) {
            log.error("[XML 파싱 오류] item 요소 파싱 중 오류: {}", e.getMessage(), e);
            throw new XmlParseException("거래 데이터 파싱 실패: " + e.getMessage(), e);
        }

        log.info("[XML 파싱 완료] 유효한 거래 건수: {}", items.size());
        return items;
    }

    /**
     * 개별 item 요소를 TradeItem으로 변환한다.
     */
    private static TradeItem parseItem(Element element) {
        try {
            TradeItem item = new TradeItem();

            item.setAptNm(getTagValue(element, "aptNm"));
            item.setDealAmount(getTagValue(element, "dealAmount"));
            item.setDealYear(getTagValue(element, "dealYear"));
            item.setDealMonth(getTagValue(element, "dealMonth"));
            item.setDealDay(getTagValue(element, "dealDay"));
            item.setUmdNm(getTagValue(element, "umdNm"));
            item.setJibun(getTagValue(element, "jibun"));
            item.setRoadNm(getTagValue(element, "roadNm"));
            item.setRoadNmBonbun(getTagValue(element, "roadNmBonbun"));
            item.setRoadNmBubun(getTagValue(element, "roadNmBubun"));
            item.setRoadNmSggCd(getTagValue(element, "roadNmSggCd"));
            item.setSggCd(getTagValue(element, "sggCd"));
            item.setUmdCd(getTagValue(element, "umdCd"));
            item.setCdealType(getTagValue(element, "cdealType"));
            item.setDealingGbn(getTagValue(element, "dealingGbn"));

            // 숫자 필드 파싱
            item.setExcluUseAr(parseDouble(getTagValue(element, "excluUseAr")));
            item.setFloor(parseInt(getTagValue(element, "floor")));
            item.setBuildYear(parseInt(getTagValue(element, "buildYear")));

            // dealAmount 공백 제거
            if (item.getDealAmount() != null) {
                item.setDealAmount(item.getDealAmount().trim());
            }

            // null → 빈 문자열
            if (item.getCdealType() == null) {
                item.setCdealType("");
            } else {
                item.setCdealType(item.getCdealType().trim());
            }
            if (item.getDealingGbn() == null) {
                item.setDealingGbn("");
            } else {
                item.setDealingGbn(item.getDealingGbn().trim());
            }

            // aptNm이 없으면 스킵
            if (item.getAptNm() == null || item.getAptNm().trim().isEmpty()) {
                return null;
            }

            return item;

        } catch (Exception e) {
            log.warn("[item 파싱 오류] {}", e.getMessage());
            return null;
        }
    }

    /** Document 레벨에서 태그의 첫 번째 텍스트를 가져온다 */
    private static String getFirstTagValue(Document doc, String tagName) {
        NodeList nodes = doc.getElementsByTagName(tagName);
        if (nodes.getLength() > 0 && nodes.item(0).getTextContent() != null) {
            return nodes.item(0).getTextContent();
        }
        return null;
    }

    /** Element 내 특정 태그의 텍스트 값을 추출한다 */
    private static String getTagValue(Element element, String tagName) {
        NodeList nodeList = element.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            Node node = nodeList.item(0);
            if (node.getTextContent() != null) {
                return node.getTextContent();
            }
        }
        return null;
    }

    private static double parseDouble(String value) {
        if (value == null || value.trim().isEmpty()) return 0;
        try { return Double.parseDouble(value.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    private static int parseInt(String value) {
        if (value == null || value.trim().isEmpty()) return 0;
        try { return Integer.parseInt(value.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    // ========== 커스텀 예외 클래스 ==========

    /**
     * 공공API가 에러 응답코드를 반환한 경우.
     * (API 호출 자체는 성공했으나, 비즈니스 레벨에서 에러)
     */
    public static class ApiResponseException extends RuntimeException {
        private final String resultCode;
        private final String resultMsg;

        public ApiResponseException(String resultCode, String resultMsg) {
            super("공공API 에러 응답 [" + resultCode + "]: " + resultMsg);
            this.resultCode = resultCode;
            this.resultMsg = resultMsg;
        }

        public String getResultCode() { return resultCode; }
        public String getResultMsg() { return resultMsg; }
    }

    /**
     * XML 파싱 자체가 실패한 경우.
     * (응답 XML 구조가 깨졌거나, 파서 오류 등)
     */
    public static class XmlParseException extends RuntimeException {
        public XmlParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
